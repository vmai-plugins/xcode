#!/usr/bin/env python3
"""X-Codes background agent runner.

Runs an OmniRoute (OpenAI- or Anthropic-compatible) agent loop on the server, so a
task keeps going when the phone is locked or the app is closed. Installed and
driven by the X-Codes app over SSH; standard library only.

Layout:  ~/.xcodes/runs/<id>/{task.json, key, meta.json, events.jsonl}

Commands:
  run <run_dir>        execute a task (started detached by the app)
  list [cwd]           JSON array of runs, newest first
  logs <id>            the run's transcript as plain text
  stop <id>            stop a running task
  rm <id>              delete a finished task's files
"""
import ipaddress
import json
import os
import re
import shlex
import signal
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

VERSION = "3"
HOME = os.path.expanduser("~/.xcodes")
RUNS = os.path.join(HOME, "runs")

MAX_ITERATIONS = 40
MAX_READ_BYTES = 256 * 1024
MAX_TOOL_OUTPUT = 30_000
MAX_HISTORY_CHARS = 100_000
TRIMMED_TOOL_OUTPUT = 1_500
COMMAND_TIMEOUT = 600
REQUEST_TIMEOUT = 600
MAX_TOKENS = 4096
FETCH_MAX_BYTES = 1024 * 1024
FETCH_MAX_CHARS = 20_000

# Commands refused even in full-auto mode: unrecoverable with no one watching.
DENIED = [
    r"\brm\s+-[a-z]*r[a-z]*f?[a-z]*\s+(/|~|\$HOME)(\s|$)",
    r"\bmkfs(\.|\s)",
    r"\bdd\s+.*\bof=/dev/",
    r"\b(shutdown|reboot|halt|poweroff)\b",
    r":\(\)\s*\{\s*:\|:&\s*\};:",
    r">\s*/dev/sd[a-z]",
    r"\bch(mod|own)\s+-R\s+\S+\s+/(\s|$)",
    r"\bfind\s+(/|~|\$HOME)(\s|$).*(-delete|-exec\s+rm)",
]
WRAPPERS = {"sudo", "env", "nohup", "time", "command", "exec"}
SYSTEM_DIR = re.compile(r"/(bin|boot|dev|etc|home|lib\w*|opt|proc|root|run|sbin|srv|sys|usr|var)/?\*?")
HOME_LIKE = {"/", "/*", "~", "~/", "~/*", "$HOME", "$HOME/", "$HOME/*", "${HOME}", "${HOME}/"}

# Files the agent may not read or change: a fetched page can tell it to leak these.
SECRET_DIRS = {".ssh", ".gnupg", ".aws", ".kube", ".docker"}
SECRET_SUFFIXES = (".pem", ".key", ".p12", ".pfx", ".kdbx")
TEMPLATE_SUFFIXES = (".example", ".sample", ".template", ".dist")

TOOLS = {
    "read_file": ("Read a text file's contents.", {"path": "Absolute or working-directory-relative path."}, ["path"]),
    "list_directory": ("List files and subdirectories at a path.", {"path": "Directory path."}, ["path"]),
    "grep_search": ("Search files for a pattern; returns matching lines.",
                    {"query": "Text or regular expression.", "path": "Where to search; defaults to the working directory."},
                    ["query"]),
    "web_fetch": ("Fetch a public web page (http/https) and return its readable text.", {"url": "Full URL."}, ["url"]),
    "write_file": ("Create or overwrite a text file with the complete content.",
                   {"path": "File path.", "content": "Complete new contents."}, ["path", "content"]),
    "edit_file": ("Replace target_content (must occur exactly once) with replacement_content in a file.",
                  {"path": "File path.", "target_content": "Exact existing text.",
                   "replacement_content": "New text."}, ["path", "target_content", "replacement_content"]),
    "run_command": ("Run a shell command in the working directory and return its output.",
                    {"command": "Shell command."}, ["command"]),
}
MODE_TOOLS = {
    "plan": ["read_file", "list_directory", "grep_search", "web_fetch"],
    "edit": ["read_file", "list_directory", "grep_search", "web_fetch", "write_file", "edit_file"],
    "full": list(TOOLS),
}


# --- files ----------------------------------------------------------------------

def run_dir(run_id):
    if not re.fullmatch(r"xo-[0-9a-f]{8}", run_id or ""):
        raise SystemExit("bad run id")
    return os.path.join(RUNS, run_id)


def read_json(path, default=None):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return default


def write_json(path, data):
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f)
    os.replace(tmp, path)


class Run:
    def __init__(self, directory):
        self.dir = directory
        self.meta_path = os.path.join(directory, "meta.json")
        self.events_path = os.path.join(directory, "events.jsonl")
        self.meta = read_json(self.meta_path, {})

    def update(self, **fields):
        self.meta.update(fields)
        write_json(self.meta_path, self.meta)

    def event(self, kind, **fields):
        fields["type"] = kind
        fields["at"] = int(time.time() * 1000)
        with open(self.events_path, "a", encoding="utf-8") as f:
            f.write(json.dumps(fields) + "\n")


# --- model requests ---------------------------------------------------------------

def tool_schema(name):
    description, props, required = TOOLS[name]
    return description, {
        "type": "object",
        "properties": {k: {"type": "string", "description": v} for k, v in props.items()},
        "required": required,
    }


def size_of(message):
    return sum(len(json.dumps(v)) for v in message.values())


def trim_history(history, budget=MAX_HISTORY_CHARS):
    """Keeps the conversation inside the model's context window."""
    if sum(size_of(m) for m in history) <= budget:
        return history
    keep_from = max(len(history) - 6, 0)
    trimmed = []
    for index, message in enumerate(history):
        if index < keep_from and message["role"] == "tool" and len(message["content"]) > TRIMMED_TOOL_OUTPUT:
            message = dict(message, content=message["content"][:TRIMMED_TOOL_OUTPUT] + "\n[trimmed]")
        trimmed.append(message)
    while sum(size_of(m) for m in trimmed) > budget:
        next_user = next((i for i, m in enumerate(trimmed) if i > 0 and m["role"] == "user"), None)
        if next_user is None:
            break
        trimmed = trimmed[next_user:]
    return trimmed


def build_request(task, history, tools):
    """History uses a neutral shape: {role: user|assistant|tool, content, tool_calls?, tool_call_id?}."""
    if task["dialect"] == "anthropic":
        messages = []
        for m in history:
            if m["role"] == "user":
                messages.append({"role": "user", "content": [{"type": "text", "text": m["content"]}]})
            elif m["role"] == "assistant":
                blocks = [{"type": "text", "text": m["content"]}] if m.get("content") else []
                for call in m.get("tool_calls", []):
                    blocks.append({"type": "tool_use", "id": call["id"], "name": call["name"],
                                   "input": json.loads(call["arguments"] or "{}")})
                messages.append({"role": "assistant", "content": blocks or [{"type": "text", "text": "(no reply)"}]})
            else:
                block = {"type": "tool_result", "tool_use_id": m["tool_call_id"], "content": m["content"]}
                if messages and messages[-1]["role"] == "user" and messages[-1].get("_results"):
                    messages[-1]["content"].append(block)
                else:
                    messages.append({"role": "user", "content": [block], "_results": True})
        for m in messages:
            m.pop("_results", None)
        body = {"model": task["model"], "max_tokens": MAX_TOKENS, "system": task["system_prompt"],
                "messages": messages,
                "tools": [{"name": n, "description": tool_schema(n)[0], "input_schema": tool_schema(n)[1]} for n in tools]}
        return "v1/messages", body
    messages = [{"role": "system", "content": task["system_prompt"]}]
    for m in history:
        if m["role"] == "tool":
            messages.append({"role": "tool", "tool_call_id": m["tool_call_id"], "content": m["content"]})
        elif m["role"] == "assistant":
            entry = {"role": "assistant", "content": m.get("content")}
            if m.get("tool_calls"):
                entry["tool_calls"] = [{"id": c["id"], "type": "function",
                                        "function": {"name": c["name"], "arguments": c["arguments"]}}
                                       for c in m["tool_calls"]]
            messages.append(entry)
        else:
            messages.append({"role": "user", "content": m["content"]})
    body = {"model": task["model"], "max_tokens": MAX_TOKENS, "stream": False, "messages": messages,
            "tools": [{"type": "function", "function": {"name": n, "description": tool_schema(n)[0],
                                                        "parameters": tool_schema(n)[1]}} for n in tools]}
    return "v1/chat/completions", body


def call_model(task, key, history, tools):
    budget = int(task.get("context_chars") or MAX_HISTORY_CHARS)
    path, body = build_request(task, trim_history(history, budget), tools)
    headers = {"Content-Type": "application/json"}
    if task["dialect"] == "anthropic":
        headers.update({"x-api-key": key, "anthropic-version": "2023-06-01"})
    else:
        headers["Authorization"] = "Bearer " + key
    request = urllib.request.Request(task["base_url"].rstrip("/") + "/" + path,
                                     data=json.dumps(body).encode(), headers=headers, method="POST")
    last_error = None
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT) as response:
                return parse_response(task["dialect"], json.loads(response.read().decode("utf-8", "replace")))
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", "replace")[:500]
            last_error = "HTTP %s from the gateway: %s" % (error.code, detail)
            if error.code not in (408, 429, 500, 502, 503, 504):
                break
        except (urllib.error.URLError, socket.timeout, ConnectionError) as error:
            last_error = "Could not reach the gateway: %s" % error
        time.sleep(2 * (attempt + 1))
    raise RuntimeError(last_error)


def parse_response(dialect, root):
    """Returns (text, tool_calls[{id,name,arguments}], (input_tokens, output_tokens))."""
    if dialect == "anthropic":
        text, calls = "", []
        for block in root.get("content") or []:
            if block.get("type") == "text":
                text += block.get("text", "")
            elif block.get("type") == "tool_use":
                calls.append({"id": block["id"], "name": block["name"],
                              "arguments": json.dumps(block.get("input") or {})})
        usage = root.get("usage") or {}
        return text, calls, (usage.get("input_tokens", 0), usage.get("output_tokens", 0))
    choice = (root.get("choices") or [{}])[0]
    message = choice.get("message") or {}
    calls = [{"id": c.get("id") or "call_%d" % i, "name": c["function"]["name"],
              "arguments": c["function"].get("arguments") or "{}"}
             for i, c in enumerate(message.get("tool_calls") or []) if c.get("function")]
    usage = root.get("usage") or {}
    return message.get("content") or "", calls, (usage.get("prompt_tokens", 0), usage.get("completion_tokens", 0))


# --- tools ----------------------------------------------------------------------

def is_secret(path):
    parts = [p for p in path.split(os.sep) if p]
    name = parts[-1].lower() if parts else ""
    return (any(p in SECRET_DIRS for p in parts)
            or (name.startswith(".env") and not name.endswith(TEMPLATE_SUFFIXES))
            or name.startswith(("id_rsa", "id_ed25519", "id_ecdsa"))
            or name.endswith(SECRET_SUFFIXES))


def resolve(cwd, path):
    """Resolves symlinks, and refuses anything outside the project folder or holding credentials."""
    if path.startswith("~"):
        raise PermissionError("Home-relative paths are not available. Use a path inside %s." % cwd)
    root = os.path.realpath(cwd)
    target = os.path.realpath(os.path.join(root, path))
    if root != os.sep and target != root and not target.startswith(root + os.sep):
        raise PermissionError("That path is outside the project folder (%s). File tools only work inside it." % root)
    if is_secret(target):
        raise PermissionError("That path holds credentials, so the agent may not read or change it.")
    return target


def cap(text, limit=MAX_TOOL_OUTPUT):
    return text if len(text) <= limit else text[:limit] + "\n\n[output truncated: %d more characters]" % (len(text) - limit)


def tool_read_file(cwd, args):
    path = resolve(cwd, args["path"])
    if os.path.getsize(path) > MAX_READ_BYTES:
        return "File is larger than %d KB. Use grep_search or run_command with head/sed." % (MAX_READ_BYTES // 1024), True
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read(), False


def tool_list_directory(cwd, args):
    path = resolve(cwd, args.get("path") or ".")
    entries = sorted(os.listdir(path))[:500]
    return "\n".join(e + ("/" if os.path.isdir(os.path.join(path, e)) else "") for e in entries) or "(empty)", False


def tool_grep_search(cwd, args):
    path = resolve(cwd, args.get("path") or ".")
    result = subprocess.run(
        ["grep", "-rIn", "--exclude-dir=.git", "--exclude-dir=node_modules", "--exclude-dir=build",
         "--exclude-dir=.gradle", "--exclude-dir=.ssh", "--exclude-dir=.aws", "--exclude=.env*",
         "--exclude=*.pem", "--exclude=*.key", "--exclude=id_rsa*", "--exclude=id_ed25519*",
         "-e", args["query"], path],
        capture_output=True, text=True, timeout=120)
    lines = result.stdout.splitlines()[:200]
    return "\n".join(lines) or "No matches.", False


def tool_write_file(cwd, args):
    path = resolve(cwd, args["path"])
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(args["content"])
    return "Wrote %d characters to %s." % (len(args["content"]), path), False


def tool_edit_file(cwd, args):
    path = resolve(cwd, args["path"])
    with open(path, encoding="utf-8") as f:
        content = f.read()
    count = content.count(args["target_content"])
    if count != 1:
        return ("target_content occurs %d times in %s; it must occur exactly once. "
                "Include more surrounding lines." % (count, path)), True
    with open(path, "w", encoding="utf-8") as f:
        f.write(content.replace(args["target_content"], args["replacement_content"], 1))
    return "Edited %s." % path, False


def blocked(command):
    """Best-effort refusal of commands nobody would want run unattended. A guard rail, not a sandbox."""
    if any(re.search(pattern, command) for pattern in DENIED):
        return True
    for segment in re.split(r"[;&|\n]+", command):
        try:
            words = shlex.split(segment)
        except ValueError:
            words = segment.split()
        while words and (words[0] in WRAPPERS or re.fullmatch(r"\w+=.*", words[0])):
            words = words[1:]
        if not words or os.path.basename(words[0]) != "rm":
            continue
        recursive = any(w == "--recursive" or re.fullmatch(r"-[a-zA-Z]*[rR][a-zA-Z]*", w) for w in words[1:])
        targets = [w for w in words[1:] if not w.startswith("-")]
        if recursive and any(t in HOME_LIKE or SYSTEM_DIR.fullmatch(t) for t in targets):
            return True
    return False


def tool_run_command(cwd, args):
    command = args["command"]
    if blocked(command):
        return "Refused: this command is blocked for unattended runs.", True
    result = subprocess.run(["bash", "-lc", command], cwd=cwd, capture_output=True, text=True,
                            timeout=COMMAND_TIMEOUT, stdin=subprocess.DEVNULL)
    output = (result.stdout or "") + (("\n" + result.stderr) if result.stderr else "")
    return "%s\n[exit code %d]" % (output.strip() or "(no output)", result.returncode), result.returncode != 0


def is_private_ip(address):
    ip = ipaddress.ip_address(address)
    return ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_reserved or ip.is_multicast \
        or ip.is_unspecified or ip in ipaddress.ip_network("100.64.0.0/10")


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def tool_web_fetch(cwd, args):
    url = args["url"].strip()
    opener = urllib.request.build_opener(_NoRedirect)
    for _ in range(6):
        parts = urllib.parse.urlsplit(url)
        if parts.scheme not in ("http", "https") or not parts.hostname:
            return "Only http and https URLs can be fetched.", True
        try:
            addresses = {info[4][0] for info in socket.getaddrinfo(parts.hostname, None)}
        except socket.gaierror as error:
            return "Could not resolve %s: %s" % (parts.hostname, error), True
        if any(is_private_ip(a.split("%")[0]) for a in addresses):
            return "Private and local addresses cannot be fetched.", True
        request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (X11; Linux) XCodes/1.0"})
        try:
            response = opener.open(request, timeout=30)
        except urllib.error.HTTPError as error:
            if error.code in (301, 302, 303, 307, 308) and error.headers.get("Location"):
                url = urllib.parse.urljoin(url, error.headers["Location"])
                continue
            return "The server answered HTTP %d." % error.code, True
        with response:
            body = response.read(FETCH_MAX_BYTES).decode("utf-8", "replace")
        return cap(readable(body), FETCH_MAX_CHARS), False
    return "Too many redirects.", True


def readable(body):
    if "<html" not in body.lower() and "<body" not in body.lower():
        return body.strip()
    title = re.search(r"<title[^>]*>(.*?)</title>", body, re.I | re.S)
    text = re.sub(r"<(script|style|noscript|svg|head)[^>]*>.*?</\1>", " ", body, flags=re.I | re.S)
    text = re.sub(r"<(br|/p|/div|/li|/h[1-6]|/tr|/pre)[^>]*>", "\n", text, flags=re.I)
    text = re.sub(r"<[^>]+>", " ", text)
    for entity, char in (("&nbsp;", " "), ("&lt;", "<"), ("&gt;", ">"), ("&quot;", '"'), ("&#39;", "'"), ("&amp;", "&")):
        text = text.replace(entity, char)
    lines = [re.sub(r"[ \t]+", " ", line).strip() for line in text.splitlines()]
    text = "\n".join(line for line in lines if line)
    return ("# %s\n\n%s" % (title.group(1).strip(), text)) if title else text


TOOL_FUNCS = {
    "read_file": tool_read_file, "list_directory": tool_list_directory, "grep_search": tool_grep_search,
    "write_file": tool_write_file, "edit_file": tool_edit_file, "run_command": tool_run_command,
    "web_fetch": tool_web_fetch,
}


def summarize(name, args):
    target = args.get("path") or args.get("url") or args.get("command") or args.get("query") or ""
    labels = {"read_file": "Read", "list_directory": "List", "grep_search": "Search", "write_file": "Write",
              "edit_file": "Edit", "run_command": "Run", "web_fetch": "Fetch"}
    return ("%s %s" % (labels.get(name, name), target)).strip()[:200]


def run_tool(cwd, allowed, call):
    name = call["name"]
    if name not in allowed:
        return "Tool \"%s\" is not available in this run's mode." % name, True
    try:
        args = json.loads(call["arguments"] or "{}")
        output, is_error = TOOL_FUNCS[name](cwd, args)
    except subprocess.TimeoutExpired:
        return "Timed out.", True
    except KeyError as error:
        return "Missing argument %s." % error, True
    except (OSError, ValueError) as error:
        return str(error), True
    return cap(output), is_error


# --- the loop -------------------------------------------------------------------

def run(directory):
    task = read_json(os.path.join(directory, "task.json"))
    key_path = os.path.join(directory, "key")
    with open(key_path, encoding="utf-8") as f:
        key = f.read().strip()
    os.remove(key_path)  # Only ever in memory from here on.

    state = Run(directory)
    started = int(time.time() * 1000)
    state.update(id=os.path.basename(directory), cwd=task["cwd"], name=task["prompt"][:80],
                 kind="background", status="running", state="working", startedAt=started,
                 pid=os.getpid(), model=task["model"], version=VERSION)

    def on_stop(*_):
        state.event("stopped")
        state.update(status="done", state="exited", result="stopped")
        sys.exit(0)

    signal.signal(signal.SIGTERM, on_stop)

    allowed = MODE_TOOLS.get(task.get("mode"), MODE_TOOLS["plan"])
    history = list(task.get("history") or [])
    history.append({"role": "user", "content": task["prompt"]})
    state.event("user", text=task["prompt"])
    tokens_in = tokens_out = 0
    os.chdir(task["cwd"])

    try:
        for _ in range(MAX_ITERATIONS):
            text, calls, (used_in, used_out) = call_model(task, key, history, allowed)
            tokens_in += used_in or 0
            tokens_out += used_out or 0
            if text:
                state.event("text", text=text)
            history.append({"role": "assistant", "content": text, "tool_calls": calls})
            if not calls:
                state.event("done", input_tokens=tokens_in, output_tokens=tokens_out,
                            duration_ms=int(time.time() * 1000) - started)
                state.update(status="done", state="exited", result="completed",
                             finishedAt=int(time.time() * 1000))
                return
            for call in calls:
                args = {}
                try:
                    args = json.loads(call["arguments"] or "{}")
                except ValueError:
                    pass
                state.event("tool_start", id=call["id"], name=call["name"], summary=summarize(call["name"], args),
                            path=args.get("path"))
                output, is_error = run_tool(task["cwd"], allowed, call)
                state.event("tool_end", id=call["id"], is_error=is_error, output=output[:4000])
                history.append({"role": "tool", "tool_call_id": call["id"], "content": output})
        raise RuntimeError("Stopped after %d model turns to avoid a runaway loop." % MAX_ITERATIONS)
    except Exception as error:  # noqa: BLE001 - the run must always end with a status.
        state.event("failed", message=str(error))
        state.update(status="done", state="exited", result="failed", error=str(error),
                     finishedAt=int(time.time() * 1000))


# --- management commands ----------------------------------------------------------

def alive(pid):
    try:
        os.kill(int(pid), 0)
        return True
    except (OSError, TypeError, ValueError):
        return False


def list_runs(cwd=None):
    runs = []
    if os.path.isdir(RUNS):
        for name in os.listdir(RUNS):
            meta = read_json(os.path.join(RUNS, name, "meta.json"))
            if not meta:
                continue
            if meta.get("status") == "running" and not alive(meta.get("pid")):
                meta.update(status="done", state="exited", result="crashed")
            if cwd and os.path.normpath(meta.get("cwd", "")) != os.path.normpath(cwd):
                continue
            runs.append(meta)
    runs.sort(key=lambda m: m.get("startedAt", 0), reverse=True)
    print(json.dumps(runs[:50]))


def show_logs(run_id):
    lines = []
    path = os.path.join(run_dir(run_id), "events.jsonl")
    if not os.path.exists(path):
        print("No output yet.")
        return
    with open(path, encoding="utf-8") as f:
        for raw in f:
            try:
                e = json.loads(raw)
            except ValueError:
                continue
            kind = e.get("type")
            if kind == "user":
                lines.append("You: " + e.get("text", ""))
            elif kind == "text":
                lines.append(e.get("text", ""))
            elif kind == "tool_start":
                lines.append("› " + e.get("summary", e.get("name", "")))
            elif kind == "tool_end" and e.get("is_error"):
                lines.append("  ✗ " + e.get("output", "")[:300])
            elif kind == "done":
                lines.append("✓ Done (%s in / %s out tokens)" % (e.get("input_tokens"), e.get("output_tokens")))
            elif kind == "failed":
                lines.append("✗ Failed: " + e.get("message", ""))
            elif kind == "stopped":
                lines.append("■ Stopped")
    print("\n\n".join(lines)[-20000:])


def stop(run_id):
    meta = read_json(os.path.join(run_dir(run_id), "meta.json"), {})
    if meta.get("status") == "running" and alive(meta.get("pid")):
        os.kill(int(meta["pid"]), signal.SIGTERM)
    print("stopped")


def remove(run_id):
    directory = run_dir(run_id)
    meta = read_json(os.path.join(directory, "meta.json"), {})
    if meta.get("status") == "running" and alive(meta.get("pid")):
        print("still running")
        return
    for name in os.listdir(directory):
        os.remove(os.path.join(directory, name))
    os.rmdir(directory)
    print("removed")


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return
    command = argv[1]
    if command == "version":
        print(VERSION)
    elif command == "run":
        run(argv[2])
    elif command == "list":
        list_runs(argv[2] if len(argv) > 2 else None)
    elif command == "logs":
        show_logs(argv[2])
    elif command == "stop":
        stop(argv[2])
    elif command == "rm":
        remove(argv[2])
    else:
        print("unknown command: " + command)


if __name__ == "__main__":
    main(sys.argv)

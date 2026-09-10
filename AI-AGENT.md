# AI agent

## Two providers, two jobs

| | Claude Code over SSH | OmniRoute gateway |
|---|---|---|
| Capability | **Full agent** — reads and edits files, runs commands and tests | **Chat only** — no tools, cannot touch files |
| Needs | An SSH server with Claude Code installed and signed in | A base URL and an API key |
| Credential lives | On the server | Keystore-backed store on the device |
| Cost | The server's own plan or key | Per token at the gateway |

They are **not interchangeable**, so the registry never substitutes one for the other
silently. An agentic request quietly answered by a chat-only backend would look like
it ran and did nothing. The user's choice decides; the UI states each backend's
capability where the choice is made.

### Do not point Claude Code at the gateway when signed in with a plan

A Pro or Max login authenticates by OAuth, which is bound to Anthropic's first-party
endpoint — the CLI treats OAuth and `ANTHROPIC_API_KEY` as separate paths. Setting
`ANTHROPIC_BASE_URL` on a subscription login will at best break authentication, and at
worst send Anthropic OAuth credentials to a third-party host.

Pick one per server:

- **Subscription login** (`claude auth login`) — agent work draws on the plan, no
  per-token API billing. Leave `ANTHROPIC_BASE_URL` unset.
- **Gateway** — set `ANTHROPIC_BASE_URL` and `ANTHROPIC_AUTH_TOKEN` in the server's
  environment and do *not* sign in with a plan.

Use the OmniRoute provider from the app for gateway access instead; that keeps the two
credentials in their own lanes.

## The decision

The agent is **Claude Code, running on the development machine, driven over SSH.**

The specification describes building an agent: a tool registry, a planner, a context
engine, an execution loop with iteration limits (§15–§18, §44). All of that already
exists in the Claude Code CLI, and it runs where the code is. Reimplementing it on a
phone would be worse in every dimension that matters:

| Concern | Reimplemented in-app | Delegated to the CLI |
|---|---|---|
| Context selection | The hardest part of §18, built from scratch | Handled, and tuned against real repos |
| Tool execution | ~24 tools over SFTP round trips | Native filesystem and process access |
| Repo data over mobile | Context must cross the link | Only the prompt out, events back |
| Long tasks | Die when the app is backgrounded | Survive on the server |
| API credentials | App must store a key | The server owns its own |

`AiProvider` keeps this a choice rather than a lock-in. A direct HTTP provider is a
sibling implementation for cases the CLI cannot serve — local projects, and any
context with no SSH host.

## How it works

```
App  ──SSH exec──>  claude --print --output-format stream-json --verbose
                         │
                    line-delimited JSON on stdout
                         │
     <───────────  ClaudeCodeStreamParser  ──>  AgentEvent  ──>  UI + Room
```

`SshSession.executeStreaming()` provides line-oriented output while the process is
still alive. No PTY is allocated, which is what makes the CLI emit machine output
rather than a rendered terminal UI.

### Session continuity

The `system/init` envelope carries a `session_id`. It is stored on the conversation
row and passed back as `--resume` on the next prompt, so a follow-up continues the
same conversation on the server instead of starting cold. This is why the transcript
is persisted rather than held in memory.

### Parsing is deliberately lenient

Two properties of the real stream rule out strict `@Serializable` models:

1. **The CLI writes non-JSON to stdout among the JSON.** A captured run produced
   `[claude-code:unrecognized_model] {...}` between two valid envelopes. A strict
   parser would abort the run on a diagnostic line.
2. **Envelopes gain fields between releases.** The CLI updates independently of the
   app, so an unknown field must be ignored rather than fatal.

Unrecognised lines become `AgentEvent.Diagnostic` rather than being dropped. An
unauthenticated CLI reports in plain text and exits; swallowing that would present as
an agent that silently does nothing — which is precisely the failure a first-time user
hits.

## Security

### The app's command guard does not extend inside a run

This is the central trade-off and it is stated plainly in the UI as well as here.
Tools the CLI invokes are its own subprocesses on the server. `CommandGuard` sees the
command that *starts* the agent; it cannot see the agent's own `Bash` calls.

Constraint comes from the CLI's controls instead, set per run:

| Control | Effect |
|---|---|
| `--permission-mode plan` | Proposes; changes nothing. The default for new runs. |
| `--permission-mode acceptEdits` | Applies edits, asks for the rest |
| `--restricted` | Removes Bash and the other code-running tools entirely |
| `--allowedTools` / `--disallowedTools` | Explicit allow and deny lists |
| working directory | The agent's filesystem scope |

Every tool call is surfaced as an event so the run is auditable, but the app cannot
gate individual calls. There is no `--permission-prompt-tool` in CLI 2.1.x that would
let approvals route back to the phone.

### Two things the command builder gets right on purpose

**The prompt is shell-quoted.** It is arbitrary user text placed into a command that
runs on the developer's server; concatenating it unquoted turns a prompt containing
`$(...)` or a backtick into remote code execution. Every interpolated value goes
through `shellQuote`, and six tests cover the constructs that would otherwise escape.

**No credential ever reaches the command line.** Pointing the CLI at a gateway needs
`ANTHROPIC_BASE_URL` and a token, but `TOKEN=… claude …` would expose the key in the
server's process list to every user on the box. The environment is left to the
server's own configuration, so the app never holds or transmits the AI credential. A
test asserts the built command contains no `ANTHROPIC_*`.

## Setting up the server

```bash
claude --version        # 2.1.x expected
claude auth status      # must report loggedIn: true
```

To use a gateway rather than first-party Anthropic, set in the server's environment
(not in the app):

```bash
export ANTHROPIC_BASE_URL="https://your-gateway.example.com"
export ANTHROPIC_AUTH_TOKEN="…"
```

**The gateway must implement the Anthropic Messages API** — `POST /v1/messages` with
SSE streaming, `tool_use`/`tool_result` content blocks and system prompts. A gateway
that is only OpenAI-compatible (`/v1/chat/completions`) will not work with Claude
Code, and no configuration changes that. Verify with:

```bash
curl -sS "$ANTHROPIC_BASE_URL/v1/messages" -H "content-type: application/json" -H "x-api-key: $KEY" -H "anthropic-version: 2023-06-01" -d '{"model":"claude-sonnet-4-5","max_tokens":16,"messages":[{"role":"user","content":"ping"}]}'
```

An Anthropic-shaped `{"content":[{"type":"text",…}]}` means it will work. A 404 or an
OpenAI-shaped `{"choices":[…]}` means it will not.

The health check distinguishes *not installed* from *installed but not signed in* and
says which, because both otherwise present identically: a prompt that produces nothing.

## Not yet built

- **Diff review (§21).** `Edit` and `Write` events carry the affected path, but the
  before/after viewer needs the editor from Phase 3.
- **Background runs.** `claude --bg` plus `claude agents/logs/stop` maps onto Tasks
  (§28) and would let a long run survive the app being closed.
- **The OmniRoute HTTP provider.** The abstraction is in place; the implementation is
  not, and it requires the app to own the agent loop.
- **Approval routing.** If a future CLI exposes a permission-prompt hook, individual
  tool calls could be gated by the app's existing approval gate.

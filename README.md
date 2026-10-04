# VMStudio Code

A professional AI-powered mobile development environment for Android. Connect to
remote development machines over SSH, browse and edit projects, run commands, use
Git, and delegate coding work to an AI agent that operates inside a real workspace
rather than guessing from a chat window.

> **Status: in development.** Foundation, security, SSH, SFTP, the terminal and the
> AI agent are built: you can add a server, connect, verify its host key, browse and
> manage its files, run an interactive shell, and give a coding task to Claude Code
> running on that machine. The editor, Git and the direct HTTP AI provider are not yet
> implemented. See [Current status](#current-status) for exactly what does and does
> not work.

---

## Requirements

| Requirement | Version | Notes |
|---|---|---|
| JDK | **17 or 21** | **Not 25.** See [Toolchain](#toolchain) below. |
| Android SDK | 36 | `compileSdk` / `targetSdk` |
| Gradle | 8.14.3 | Via the wrapper; no local install needed |
| Android Studio | Ladybug or newer | Set the Gradle JDK manually — see below |
| Device / emulator | Android 8.0 (API 26)+ | |

### Toolchain

Recent Android Studio releases bundle **JBR 25**, which Gradle 8.14.3's embedded
Kotlin DSL compiler cannot parse — the build fails with a bare `IllegalArgumentException: 25.0.2`.
The project therefore needs a JDK 17 or 21 as the *Gradle JVM*:

- **Android Studio:** Settings → Build, Execution, Deployment → Build Tools →
  Gradle → **Gradle JDK** → select a JDK 17 or 21 (use *Download JDK…* if you have none).
- **Command line:** set `JAVA_HOME` to a JDK 17/21, or use the bundled helper which
  does it for you:

```bash
./build.sh :app:assembleDebug
```

Override the JDK the helper uses with `VMSTUDIO_JDK=/path/to/jdk ./build.sh …`.

## Build

```bash
./build.sh :app:assembleDebug
```

Run every unit test:

```bash
./build.sh testDebugUnitTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk` and installs
alongside a release build (`applicationId` suffix `.debug`).

## Current status

### Working

- **Build system** — 29 Gradle modules driven by seven convention plugins, with a
  resolution-verified version catalog.
- **Structured errors** — every failure carries what happened, the likely reason, a
  suggested action and whether retrying can help. `VmErrorPanel` renders them; there
  is no code path that renders a bare "Unknown error".
- **Secret handling** — AES-256-GCM envelope encryption with a non-exportable
  Android Keystore master key (StrongBox when the device offers it), a `Secret` type
  that masks itself in logs and zeroes its buffer, and a redaction layer that scrubs
  keys, tokens, passwords, `Authorization` headers and PEM bodies from every log line
  at capture time.
- **Database** — 19 Room tables covering servers, projects, workspaces, AI
  conversations, agent tasks, tool executions, Git, connectors, transfers and the
  activity timeline. Schemas are exported and checked in. No secrets are stored here,
  only opaque credential references.
- **Design system** — Material 3 with a custom dark-first palette, semantic colour
  tokens for connection state, Git status and diffs, a monospace type scale, and
  reusable components.
- **Server management** — add, edit and delete SSH servers with full field
  validation; credentials are written to the Keystore-backed store and are removed
  with the server. Secrets are cleared from UI state as soon as they are stored.
- **App shell** — adaptive navigation (bottom bar on phones, rail from medium width),
  theme and font-size preferences, offline detection, diagnostics export, and
  startup reconciliation of work interrupted by process death.
- **SSH** — sshj-based connection engine with one multiplexed transport per server,
  password and key authentication, keepalive, concurrent stdout/stderr command
  execution, and a POSIX-only environment probe that never assumes bash exists.
- **Host key verification** — trust on first use with OpenSSH-comparable SHA-256
  fingerprints. An unknown or changed key fails the handshake and is surfaced to the
  user as a decision rather than being resolved on the transport thread.
- **Command safety** — every command is classified *and authorised* before it runs.
  Dangerous commands are refused outright; destructive ones suspend on a process-wide
  approval prompt. `rm` is analysed by parsing its flags rather than matching text, and
  the "always confirm" setting can never be used to skip an agent's proposal. The
  interactive terminal is guarded on a best-effort basis — see
  [SECURITY.md](SECURITY.md#the-interactive-terminal-is-best-effort) for exactly where
  that guard does and does not hold.
- **SFTP** — browse, create, rename, delete, chmod and stream files, with resumable
  transfers, bounded memory use, and status codes mapped to actionable errors.
- **Terminal** — a hand-written VT100/xterm-subset emulator (256-colour and
  truecolour, scroll regions, the alternate screen used by vim and top, OSC titles),
  multiple concurrent sessions that survive navigation, and a Canvas renderer.
- **Server telemetry** — a live health sample (CPU, memory, disk, load average,
  uptime, top processes) collected in one SSH round trip, parsed defensively
  against procps-ng, old procps and BusyBox output, and rendered as a bottom
  sheet on the server detail screen. Polls only while the sheet is visible.

- **Apps Hub** — scan a server's apps root for running processes, frameworks
  (Node.js, Python, Docker, Go, Rust, static HTML), git branches and ports. View
  logs, start/stop/restart processes, and clone from GitHub. Every action is a
  routed through the same command-safety engine as the terminal and agent, so the
  approval policy applies uniformly.

- **Server Recipes** — one-click provisioning of development stacks on a server.
  The WordPress sandbox recipe (ported from the old app) installs PHP, WP-CLI, and
  SQLite-backed WordPress with zero MySQL setup. Every recipe step is routed through
  the same command-safety engine as the terminal, so the approval policy applies
  uniformly. The system is generic — new recipes (Node.js, Docker, etc.) can be added
  to [RecipeRegistry](core/ssh/src/main/kotlin/digital/vmstudio/code/core/ssh/recipes/RecipeRegistry.kt)
  without touching the executor.

- **Web Preview** — a browser-chrome status surface for running apps. Shows the
  app's URL, port, framework and process state, with an "Open in browser" action
  that launches the system browser. Ported from the old app's `WebPreviewSheet`.

- **Code Editor** — syntax-highlighted remote file editor with line numbers,
  undo/redo, and save. Files are loaded from and saved to the server via SFTP.
  Supports Kotlin, Java, JavaScript, TypeScript, Python, Shell, SQL, and more.
  Opened from the Files screen via the edit button on any file.

- **Git** — version control operations over SSH: status, diff, stage, unstage,
  commit, log, fetch, pull, and push. Parses unified diff output with line
  numbers and change statistics. Requires Git installed on the server.

- **Diff Review** — unified diff viewer for examining file changes. Shows line
  numbers, additions, and deletions with accept/reject actions. Integrated with
  the agent workflow: tool calls that modify files show a "Review" button that
  opens the diff viewer.

- **AI model sync** — connection testing fetches the provider's live `/models`
  list, which is persisted and shown as selectable chips. A custom model-id field
  accepts any model the gateway supports beyond the synced list.

- **AI agent** — Dual-engine architecture:
  1. **Claude Code CLI** driven over SSH on the remote machine with resumable sessions surviving app restart.
  2. **OmniRoute Autonomous Agent Loop** — 13-tool native agent loop (`read_file`, `write_file`, `edit_file`, `list_directory`, `grep_search`, `delete_path`, `git_inspect`, `git_change`, `update_plan`, `web_fetch`, `web_search`, `browse_page`, `run_command`).
  - **Live Plan Checklist** — dynamic progress card rendered in the chat stream showing task breakdown and execution status.
  - **Ask / Plan / Agent Modes** — quick segmented selector to toggle between strict review (`MANUAL`), read-only planning (`PLAN`), and autonomous edits (`ACCEPT_EDITS`).
  - **Run-Level Rollback** — one-tap checkpoint undo that automatically identifies all files modified in a run and cleanly reverts them via Git.
  - **Model Context Protocol (MCP)** — extensible JSON-RPC 2.0 client to connect external MCP tool servers dynamically.
  - **Cloud Connectors** — Google Drive client with resumable uploads, Docs/Sheets exports, shared folders, and pagination.

### Documentation

Sections whose backing subsystem does not exist yet say so on screen. The app does
not render fabricated servers, metrics, file trees or AI responses.

### Using the AI agent

Claude Code must be installed **and signed in** on the server:

```bash
claude --version && claude auth status
```

The app's health check reports which of those is missing rather than failing at the
prompt. To route through a gateway instead of first-party Anthropic, set
`ANTHROPIC_BASE_URL` and `ANTHROPIC_AUTH_TOKEN` in the server's own environment — the
app never stores or transmits the AI credential. The gateway must implement the
Anthropic Messages API; see [AI-AGENT.md](AI-AGENT.md#setting-up-the-server).

## Documentation

| Document | Contents |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Module graph, layering rules, key decisions |
| [SECURITY.md](SECURITY.md) | Threat model, key management, redaction, safety boundaries |
| [SSH.md](SSH.md) | Library choice, connection model, host key verification, command safety |
| [AI-AGENT.md](AI-AGENT.md) | Why the agent is Claude Code over SSH, the stream protocol, security trade-offs |
| [SFTP.md](SFTP.md) | Channel model, path handling, memory limits, resume |
| [DEVELOPMENT.md](DEVELOPMENT.md) | Setup, phase plan, conventions, dependency choices |
| [TESTING.md](TESTING.md) | Test strategy and what is currently covered |

## Licence

Not yet determined.

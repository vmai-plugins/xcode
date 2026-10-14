# PROJECT X: THE DEFINITIVE ARCHITECTURE, DEVELOPMENT & GAP SPECIFICATION

> **System**: X-Codes (Mobile Cloud IDE & Autonomous Agent for Android)  
> **Repository**: `https://github.com/vmai-plugins/xcode.git`  
> **Status**: Production Release Grade  
> **Target Platform**: Android 8.0+ (API 26+)  
> **Engine**: Jetpack Compose + Kotlin Coroutines/Flow + Hilt + OmniRoute Multi-Dialect AI Agent Loop  

---

## 1. Executive Overview & System Vision

**X-Codes** is an Antigravity-grade mobile IDE and autonomous agentic coding environment built natively for Android. It bridges the gap between phone ergonomics and full-stack software development by treating the mobile device as an intelligent command deck capable of orchestrating code across three environments:
1. **Local Phone Storage**: Direct on-device development via Android Storage Access Framework (SAF) and DocumentsProvider.
2. **Remote Cloud VPS**: Seamless SSH/SFTP connection to Linux servers, cloud VMs, and remote development containers with full bash terminal emulation.
3. **GitHub Zero-Clone Virtual Repositories**: Instant browsing, editing, and committing to remote repositories via the GitHub REST/Trees API without cloning gigabytes of source code.
4. **VM Project Hub (Universal Workspace)**: Remote persistent workspace plugin for automated project sync.

### The Antigravity Philosophy
X-Codes incorporates the core tenets of Google Antigravity:
- **Autonomous Tool-Using Loop**: The AI is not a conversational chatbot; it is a coding agent equipped with file system, command execution, and code manipulation tools (`read_file`, `edit_file`, `write_file`, `grep_search`, `list_directory`, `run_command`, `run_build`, `list_apks`, `web_fetch`).
- **Reactive Streaming & Tool Batching**: Continuous streaming reasoning, draft rendering, and collapsible tool execution batches with micro-latency.
- **Safety Approval Gate**: Granular autonomy levels (`MANUAL`, `ACCEPT_EDITS`, `BYPASS`) paired with interactive diff review before writes touch disks.
- **Steerability**: Antigravity-style message queueing and immediate task steering ("Steer Now") to redirect runaway tool rounds without losing context.

```
+-----------------------------------------------------------------------------------+
|                                  USER INTERFACE                                   |
|   Agent Chat (Compose)  |  Code Editor (Monaco/Ace)  |  Terminal (Xterm.js / SSH)  |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|                               FEATURE LAYER (:feature:*)                          |
|  :feature:ai (ChatTranscript, AgentChatViewModel, DiffReview, ToolBatchCards)     |
|  :feature:projects  |  :feature:editor  |  :feature:terminal  |  :feature:files   |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|                                 CORE AI ENGINE (:core:ai)                         |
|  OmniRouteAgentLoop  <--->  OmniRouteToolCodec  <--->  OmniRouteStreamAssembler   |
|         |                           |                               |             |
|         v                           v                               v             |
|  Tool Dispatcher              Wire Dialects:                  SSE Streaming       |
|  (edit/write/grep/run)    OpenAI Chat / Anthropic Messages    & Reasoning Parser  |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|                        INFRASTRUCTURE & ENVIRONMENT ACCESS                        |
|  :core:sftp (SAF & SFTP) | :core:ssh (CommandGuard) | :core:git (GitHub / JGit)   |
|         |                            |                           |                |
|  Local Phone (SAF)             Remote Linux VPS              GitHub REST API      |
+-----------------------------------------------------------------------------------+
```

---

## 2. Complete Module Map & Responsibilities

The codebase follows strict modular architecture and unidirectional data flow (UDF):

| Module | Namespace | Role & Responsibilities |
| :--- | :--- | :--- |
| `:app` | `digital.vmstudio.code` | Application entry point, Hilt dependency graph container, Navigation host (`VmNavHost`), deep link routing, App theme initialization. |
| `:core:ai` | `digital.vmstudio.code.core.ai` | Autonomous agent runtime (`OmniRouteAgentLoop`), Stream assembler, Tool definition and JSON schemas (`OmniRouteTool`), Wire protocol codecs (OpenAI & Anthropic), Model pricing & catalog, Background runner and WakeLock manager. |
| `:core:ui` | `digital.vmstudio.code.core.ui` | Antigravity design system tokens (`VmTheme`, `VmColors`, `VmSpacing`), UI components (`VmButton`, `VmCard`, `VmStatusBadge`), and high-performance Markdown renderer (`MarkdownText`). |
| `:core:ssh` | `digital.vmstudio.code.core.ssh` | SSH client implementation via SSHJ, command execution guard (`CommandGuard`), safety limits (`CommandLimits`), key pair and server repository. |
| `:core:sftp` | `digital.vmstudio.code.core.sftp` | Unified virtual filesystem abstraction (`RemoteFileSystem`): SFTP over SSH, local phone SAF storage (`LocalFileSystemDelegate`), transfer progress flows. |
| `:core:git` | `digital.vmstudio.code.core.git` | Git repository scanning, JGit local operations, and zero-clone GitHub client (`GitHubRepositoryClient`) for cloud operations. |
| `:core:database` | `digital.vmstudio.code.core.database` | Room database schema: projects, servers, conversations, agent tasks, git repositories, and audit activity entities. |
| `:core:network` | `digital.vmstudio.code.core.network` | OkHttp client abstraction (`VmHttpClient`), SSE stream parser, retry policies, certificate pinning, and network monitors. |
| `:core:security` | `digital.vmstudio.code.core.security` | EncryptedSharedPreferences, Keystore integration, `Secret` wrappers for API keys and SSH passwords/keys. |
| `:core:connectors` | `digital.vmstudio.code.core.connectors`| External cloud storage connectors: Google Drive automated project backup manager (`GoogleDriveManager`), OAuth tokens. |
| `:core:common` | `digital.vmstudio.code.core.common` | `VmResult<T>`, `VmError` hierarchy (Network, Ssh, FileSystem, Git, Ai), log categories (`VmLog`), coroutine dispatchers. |
| `:feature:ai` | `digital.vmstudio.code.feature.ai` | Antigravity Chat UI (`AgentChatScreen`), transcript row grouping (`ChatTranscript`), `AgentChatViewModel`, approval gate sheet (`FileEditApprovalSheet`), task drawer. |
| `:feature:projects` | `digital.vmstudio.code.feature.projects` | Workspace management, project creation wizard (GitHub, Local Phone, VPS, Linked), project detail & APK build dashboard. |
| `:feature:editor` | `digital.vmstudio.code.feature.editor` | Code editor surface with syntax highlighting, line numbers, tabs, and file tree synchronization. |
| `:feature:terminal` | `digital.vmstudio.code.feature.terminal` | Interactive terminal emulator, pseudo-terminal (PTY) session handling, ANSI escape sequence rendering. |
| `:feature:files` | `digital.vmstudio.code.feature.files` | Remote & local directory tree explorer, file operations (create, rename, delete, upload, download). |
| `:feature:servers` | `digital.vmstudio.code.feature.servers` | VPS credential manager, server health monitor, SSH key generation, and environment tagging. |
| `:feature:tasks` | `digital.vmstudio.code.feature.tasks` | Kanban & list task management linked to AI conversations and agent action logs. |
| `:feature:settings` | `digital.vmstudio.code.feature.settings`| App preferences, autonomy level settings, API key management, theme toggle, and credential purge. |

---

## 3. Core Engine Workflows & Lifecycles

### 3.1 The OmniRoute Agent Loop (`OmniRouteAgentLoop.kt`)
The heart of X-Codes is `OmniRouteAgentLoop`. It executes an autonomous multi-turn tool-use loop:

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant VM as AgentChatViewModel
    participant Loop as OmniRouteAgentLoop
    participant Assembler as OmniRouteStreamAssembler
    participant Gate as FileEditApprovalGate
    participant FS as RemoteFileSystem / GitHub
    participant Gateway as OmniRoute Gateway

    User->>VM: Sends prompt / attaches images
    VM->>Loop: run(config, autonomyLevel)
    Loop->>Loop: Build System Prompt & Tool Schemas
    
    rect rgb(240, 248, 255)
    Note over Loop, Gateway: Model Turn Execution
    Loop->>Gateway: POST /chat/completions or /messages (SSE)
    Gateway-->>Assembler: SSE Stream (deltas, thinking, tool calls)
    Assembler-->>VM: Emit AgentEvent.Reasoning & AssistantDelta
    Assembler-->>Loop: OmniRouteTurn (Text or ToolCalls)
    end

    alt Turn is Text
        Loop->>VM: Emit AgentEvent.AssistantMessage & Completed
    else Turn is ToolCalls
        Loop->>VM: Emit AgentEvent.ToolStarted (for each call)
        opt Tool is File Edit / Write
            Loop->>Gate: Check canAutoApply(env, mode, autonomy)
            alt Manual Approval Required
                Gate-->>User: Present Diff Review Modal
                User-->>Gate: Approve / Reject
            end
        end
        Loop->>FS: Execute Tool (ReadFile / EditFile / WriteFile / RunCommand)
        FS-->>Loop: Tool Outcome (Output + Error flag)
        Loop->>VM: Emit AgentEvent.ToolFinished
        Loop->>Loop: Append ToolResult to Conversation History
        Note over Loop, Gateway: Re-enter turn loop with tool feedback
    end
```

### 3.2 Workspace Delegation
X-Codes decouples storage backends via `RemoteFileSystem`:
- **Local Device**: Handled by `LocalFileSystemDelegate` using Android Storage Access Framework (SAF). All file reads and writes use `ContentResolver.openInputStream` / `openOutputStream`.
- **Remote Cloud VPS**: Handled by `SftpFileSystemDelegate` using an authenticated SSHJ SFTP channel.
- **GitHub Zero-Clone**: Handled by `GitHubRepositoryClient`. Reads fetch base64 blob trees; edits create atomic GitHub commits using the Git Data API.

---

## 4. Antigravity Gap Analysis (What Antigravity Has vs What X-Codes Needs)

To match Google DeepMind Antigravity's depth and polish, the following gaps have been analyzed:

| Antigravity Capability | Current X-Codes State | Technical Gap / Path to Parity |
| :--- | :--- | :--- |
| **Hierarchical Subagents** | Single linear agent loop (`OmniRouteAgentLoop`). | Needs `invoke_subagent` and `define_subagent` infrastructure to fork child background loops for isolated research tasks, code audits, or unit testing without polluting parent context. |
| **Generative UI & Visual Artifacts** | Standard markdown rendering; text-only tool results. | Needs Generative UI rendering for structured artifacts (e.g. interactive diff sliders, HTML mockups, execution timelines, mermaid charts inline). |
| **Two OmniRoute Integration Methods** | Currently connects only via HTTP API endpoint (`/v1/chat/completions` or `/v1/messages`). | OmniRoute also supports direct socket/plugin integration via the **VM Project Hub Plugin**. X-Codes should support both: (1) Direct Gateway API, and (2) Project Hub Local Sidecar Daemon. |
| **Fuzzy Semantic Search** | Regex-based `grep_search` and Git tree string match. | Lacks AST-based code symbol search (ctags / tree-sitter) and semantic embeddings for codebase understanding across 100+ files. |
| **Interactive Terminal PTY Streaming** | Commands run synchronously via `commandGuard.run` and return output after completion. | Needs continuous real-time PTY stdout/stderr streaming in tool cards so long builds (`./gradlew assemble`) show live progress bars. |
| **Sliding Window Memory & Context Compaction** | Basic char-budget string trimming (`trimHistory`). | Needs hierarchical conversation summarization (`/compact`) that condenses earlier turns into concise markdown summaries while preserving tool outcomes. |

---

## 5. Comprehensive Bug Catalog & Resolutions (Deep Audit)

During our deep audit, the following critical bugs across AI, UI, Wire Protocol, and Storage were diagnosed and permanently resolved:

### Bug 1: Live Chat Streaming UI Jank & Heavy Recomposition
- **Severity**: HIGH (Frame drops down to 10 FPS during streaming)
- **Root Cause**: In [`MarkdownText.kt`](file:///d:/Dev/x-codes/core/ui/src/main/kotlin/digital/vmstudio/code/core/ui/markdown/MarkdownText.kt), the markdown tree was wrapped in Jetpack Compose's `SelectionContainer`. Every 80ms chunk emitted by `OmniRouteStreamAssembler` invalidated text layout bounds, forcing recalculation of selectable drag handles and text pointers. In [`ChatTranscript.kt`](file:///d:/Dev/x-codes/feature/ai/src/main/kotlin/digital/vmstudio/code/feature/ai/ChatTranscript.kt), follow-tail scrolling was clamped with `scrollToItem(last, scrollOffset = 100_000)`, causing violent viewport jumps.
- **Fix**:
  1. Added `selectable: Boolean = true` to `MarkdownText`. During live generation (`TranscriptItem.StreamingText`), `selectable = false` is enforced.
  2. Replaced abrupt `scrollToItem` with smooth `animateScrollToItem(last)` triggered only when the viewport is at the tail.

### Bug 2: Missing Tool Name in OpenAI Wire Codec
- **Severity**: HIGH (Immediate 400 Bad Request from LiteLLM / vLLM / Groq)
- **File**: [`OmniRouteToolCodec.kt:189-194`](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/omniroute/agent/OmniRouteToolCodec.kt#L189-L194)
- **Root Cause**: In `OmniRouteMessage.ToolResult.toOpenAiJson()`, the payload was serialized as:
  ```json
  { "role": "tool", "tool_call_id": "call_123", "content": "..." }
  ```
  OpenAI's official API specification and strict routers require `"name"` in addition to `"tool_call_id"`.
- **Fix**: Added `put("name", toolName)` to `toOpenAiJson()`.

### Bug 3: Line Endings Mismatch (CRLF vs LF) in `edit_file` Tool
- **Severity**: HIGH (Tool execution failure on Windows & Android git repos)
- **File**: [`OmniRouteAgentLoop.kt:708-725`](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/omniroute/agent/OmniRouteAgentLoop.kt#L708-L725)
- **Root Cause**: `uniqueMatchProblem` executed `content.split(target).size - 1`. If a file had CRLF (`\r\n`) line endings but the model sent LF (`\n`) in `target_content`, `split` returned 0 occurrences, causing the agent to fail with *"Could not find target_content in..."*.
- **Fix**: Implemented `findMatchingTarget()` with bidirectional line ending normalization (`\r\n` <-> `\n`). When a file uses CRLF, replacement content is automatically adapted to CRLF.

### Bug 4: Markdown-Fenced JSON & String-Encoded Tool Arguments
- **Severity**: HIGH (Model tool calls crash or fail to execute)
- **File**: [`OmniRouteAgentLoop.kt`](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/omniroute/agent/OmniRouteAgentLoop.kt)
- **Root Cause**: Open-source models (e.g. Qwen, DeepSeek, Mistral) often output tool arguments wrapped in markdown fences ````json ... ```` or doubly encode arguments as strings (`"{\"path\": \"main.kt\"}"`).
- **Fix**: Added `sanitizeJson()` to strip code fences and `parseArgumentsObject()` to unwrap nested string and array payloads into valid JSON objects.

### Bug 5: Phone Workspace SAF Auto-Appending `.txt`
- **Severity**: MEDIUM (Created files like `App.kt.txt`)
- **File**: [`LocalFileSystemDelegate.kt:285`](file:///d:/Dev/x-codes/core/sftp/src/main/kotlin/digital/vmstudio/code/core/sftp/fs/LocalFileSystemDelegate.kt#L285)
- **Root Cause**: `createFile("text/plain", name)` told Android's `DocumentsProvider` that the file was plain text, prompting the OS to append `.txt`.
- **Fix**: Switched MIME type to `"application/octet-stream"` for code files.

### Bug 6: Duplicate `signOut()` in SettingsViewModel
- **Severity**: BLOCKER (Release compilation failure in `:feature:settings`)
- **File**: [`SettingsViewModel.kt:153-163`](file:///d:/Dev/x-codes/feature/settings/src/main/kotlin/digital/vmstudio/code/feature/settings/SettingsViewModel.kt#L153-L163)
- **Root Cause**: Accidental duplicated definition of `fun signOut(): Unit`.
- **Fix**: Removed redundant overload.

### Bug 7: Corrupted Catch/Finally & Ambiguous Import in GoogleDriveManager
- **Severity**: BLOCKER (Release compilation failure in `:core:connectors`)
- **File**: [`GoogleDriveManager.kt:19, 168-176`](file:///d:/Dev/x-codes/core/connectors/src/main/kotlin/digital/vmstudio/code/core/connectors/drive/GoogleDriveManager.kt)
- **Root Cause**: Duplicate `import java.io.File` and a dangling unclosed `try-catch` block after `cleanupBackup`.
- **Fix**: Removed duplicate import and cleaned up the redundant syntax.

### Bug 8: Missing ViewModel Signatures in ProjectCreate & ProjectDetail
- **Severity**: BLOCKER (Compilation failure in `:feature:projects`)
- **Files**: `ProjectCreateViewModel.kt`, `ProjectDetailViewModel.kt`
- **Root Cause**: `selectLocalFolder`, `reportFolderAccessError`, and `showTerminalNotice` were called in UI screens but missing in ViewModels.
- **Fix**: Added implementations with proper error wrapping using `VmError.FileSystem`.

### Bug 9: Free Model Pricing & TPM Rate Limit Heuristics
- **Severity**: MEDIUM (Model failover prematurely aborted)
- **Files**: `GatewayModelPricing.kt`, `OmniRouteToolCodec.kt`
- **Root Cause**: Free tier detection didn't recognize bare model IDs (`gpt-oss-safeguard-20b`, `llama-3*`, `mixtral-*`, `qwen*`); error parser missed `"tokens per minute"` and `"tpm"` rate limits.
- **Fix**: Expanded `isFreeModelId` and updated `RETRYABLE_ERROR_TYPES`.

### Bug 10: SFTP Remote File System Tilde (`~`) Path Resolution Failure
- **Severity**: HIGH (SFTP runtime failure for default VPS projects)
- **File**: [`SftpRemoteFileSystem.kt`](file:///d:/Dev/x-codes/core/sftp/src/main/kotlin/digital/vmstudio/code/core/sftp/fs/SftpRemoteFileSystem.kt)
- **Root Cause**: SFTP RFC 9116 does not expand shell tildes (`~`). Project paths created as `"~/projects/..."` failed with `SSH_FX_NO_SUCH_FILE` on all SFTP file operations.
- **Fix**: Implemented `expandPath(sftp, path)` resolving `~` via `sftp.canonicalize(".")` across all SFTP operations (`stat`, `exists`, `canonicalize`, `createDirectory`, `createFile`, `rename`, `delete`, `setPermissions`, `readText`, `writeText`, `download`, `upload`).

### Bug 11: Editor Zero-Clone GitHub Virtual Path Storage Exception
- **Severity**: HIGH (Editor crash / read error when opening GitHub files)
- **Files**: [`feature/editor/build.gradle.kts`](file:///d:/Dev/x-codes/feature/editor/build.gradle.kts), [`EditorViewModel.kt`](file:///d:/Dev/x-codes/feature/editor/src/main/kotlin/digital/vmstudio/code/feature/editor/EditorViewModel.kt)
- **Root Cause**: `/github/owner/repo/...` virtual zero-clone paths were routed directly to `RemoteFileSystem`, which threw a local storage failure.
- **Fix**: Injected `GitHubRepositoryClient` into `EditorViewModel` and intercepted `/github/` paths in both `loadFile()` and `save()`.

### Bug 12: Terminal Multiline Paste CommandGuard Bypass
- **Severity**: HIGH (Security bypass on pasted terminal commands)
- **File**: [`TerminalViewModel.kt`](file:///d:/Dev/x-codes/feature/terminal/src/main/kotlin/digital/vmstudio/code/feature/terminal/TerminalViewModel.kt)
- **Root Cause**: Pasted text containing newlines bypassed `submitLine()`, evading `CommandGuard.authorize()` and executing unvetted commands.
- **Fix**: Refactored `send(text)` to split on line breaks and channel every command through `submitLine()`.

### Bug 13: Local Workspace Grep Tool Omission & Traversal
- **Severity**: MEDIUM (Agent incapable of code search on phone/local projects)
- **File**: [`OmniRouteAgentLoop.kt`](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/omniroute/agent/OmniRouteAgentLoop.kt)
- **Root Cause**: `GREP_SEARCH` tool was omitted for local workspaces because `commandGuard.run` requires an SSH connection.
- **Fix**: Implemented `localGrep()` using recursive filesystem exploration via `RemoteFileSystem`, enabling `GREP_SEARCH` unconditionally across VPS, GitHub, and local workspaces.

### Bug 14: OmniRoute Token Limit Case-Sensitivity & Multimodel Truncation Miss
- **Severity**: HIGH (Silent tool call corruption when output is truncated)
- **File**: [`OmniRouteToolCodec.kt:78`](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/omniroute/agent/OmniRouteToolCodec.kt)
- **Root Cause**: `isLengthStop()` used strict exact string comparisons `== "length" || == "max_tokens"`. Gemini returns uppercase `"MAX_TOKENS"`, Vertex returns `"max_output_tokens"`, Mistral returns `"model_length"`, Groq returns `"token_limit"`. All returned `false`, causing the agent loop to mistake cut-off JSON for complete output and error or corrupt code.
- **Fix**: Normalized reason via `lowercase()` and recognized all major gateway stop reason dialects (`length`, `max_tokens`, `max_output_tokens`, `model_length`, `token_limit`).

### Bug 15: GitHub REST Zero-Clone Space/Special Character URL Failure
- **Severity**: MEDIUM (OkHttp IllegalArgumentException when reading or saving files with spaces)
- **File**: [`GitHubRepositoryClient.kt`](file:///d:/Dev/x-codes/core/git/src/main/kotlin/digital/vmstudio/code/core/git/GitHubRepositoryClient.kt)
- **Root Cause**: `contents/$cleanPath` and `trees/$ref` concatenated raw file paths without URL encoding. Any file path with spaces (e.g. `docs/Release Notes.md`) or branch names with `#` or `/` crashed OkHttp's URL builder.
- **Fix**: Added `encodePath()` and `encodeParam()` using `URLEncoder.encode` with `%20` replacement across `getFileContent()`, `getRepositoryTree()`, and `putFileContent()`.

### Bug 16: Terminal Session Manager Server Disconnect Tab Leak & Ghost Sessions
- **Severity**: MEDIUM (Ghost terminal tabs remaining active after SSH drops)
- **File**: [`TerminalSessionManager.kt`](file:///d:/Dev/x-codes/core/terminal/src/main/kotlin/digital/vmstudio/code/core/terminal/session/TerminalSessionManager.kt)
- **Root Cause**: An orphaned doc comment documented closing all sessions on disconnect, but `closeAllForServer(serverId)` was completely omitted from the class implementation.
- **Fix**: Implemented `closeAllForServer(serverId)` to cleanly iterate and terminate all active sessions tied to the dropped server.

### Bug 17: SSH KeepAlive Heartbeat Initialization Race
- **Severity**: MEDIUM (SSH connections silently dying on idle mobile connections)
- **File**: [`DefaultSshConnectionManager.kt`](file:///d:/Dev/x-codes/core/ssh/src/main/kotlin/digital/vmstudio/code/core/ssh/connection/DefaultSshConnectionManager.kt)
- **Root Cause**: Keepalive interval was set prior to socket connection, but SSHJ heartbeat runner thread can fail to spawn if socket handshake occurs after configuration.
- **Fix**: Reaffirmed `configureKeepAlive(created, server)` post-authentication to ensure the heartbeat daemon is actively running.

### Bug 18: PathGuard Tilde Path Invalidation & Normalization Traversal Block
- **Severity**: HIGH (Agent unable to read or write files on default VPS project paths)
- **Files**: [`PathGuard.kt`](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/omniroute/agent/PathGuard.kt), [`OmniRouteAgentLoop.kt:1148`](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/omniroute/agent/OmniRouteAgentLoop.kt)
- **Root Cause**: `PathGuard.problem()` unconditionally rejected any path beginning with `~`, even when the project's working directory itself was `"~/projects/..."`. Furthermore, `normalize()` prepended `/` to tildes (converting `"~/projects/app"` to `"/~/projects/app"`). When `PathGuard.resolve()` failed, `resolvePath()` fell back to the root folder, causing the agent to attempt reading the project directory as a file.
- **Fix**: Allowed paths matching or nested inside tilde working directories, preserved tildes in `normalize()`, and updated `resolvePath()` in `OmniRouteAgentLoop`.

### Bug 19: Anthropic SSE Stream Socket Hang on `message_stop`
- **Severity**: MEDIUM (SSE stream hanging on Anthropic models until timeout)
- **File**: [`StreamDecoding.kt:67`](file:///d:/Dev/x-codes/core/network/src/main/kotlin/digital/vmstudio/code/core/network/http/StreamDecoding.kt)
- **Root Cause**: `SseDecoder` only evaluated `isDone` against OpenAI's `[DONE]` terminator. Anthropic and OpenRouter Anthropic bridges finish via `event: message_stop`. Proxies using HTTP keep-alive kept the SSE connection waiting for further lines until read timeout.
- **Fix**: Added `name == "message_stop"` and payload JSON type checking to terminate the stream as soon as Anthropic finishes.

### Bug 20: Confidential Android & Git Credentials Leak in Context Scanners
- **Severity**: HIGH (Security & secret leakage to LLM context)
- **Files**: [`RemotePath.kt:130`](file:///d:/Dev/x-codes/core/sftp/src/main/kotlin/digital/vmstudio/code/core/sftp/model/RemotePath.kt), [`PathGuard.kt:127`](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/omniroute/agent/PathGuard.kt)
- **Root Cause**: `looksSensitive()` and `PathGuard.isSecret()` withheld SSH keys and `.env`, but omitted `local.properties` (Android signing keys and keystore passwords), `google-services.json` (OAuth and Firebase client tokens), and `.git/config` (containing embedded GitHub PATs).
- **Fix**: Expanded sensitivity filters across both `RemotePath` and `PathGuard` to block access to these files.

### Bug 21: POSIX Shell Single-Quoting Tilde Expansion Failure Across Remote Commands & Backups
- **Severity**: HIGH (Commands and project setups fail on default VPS project paths)
- **Files**: [`CommandResult.kt`](file:///d:/Dev/x-codes/core/ssh/src/main/kotlin/digital/vmstudio/code/core/ssh/command/CommandResult.kt), [`OmniRouteAgentLoop.kt`](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/omniroute/agent/OmniRouteAgentLoop.kt), [`ClaudeCodeCommandBuilder.kt`](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/claudecode/ClaudeCodeCommandBuilder.kt), [`OmniRouteBackgroundRunner.kt`](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/background/OmniRouteBackgroundRunner.kt), [`GoogleDriveManager.kt`](file:///d:/Dev/x-codes/core/connectors/src/main/kotlin/digital/vmstudio/code/core/connectors/drive/GoogleDriveManager.kt), [`CommandGitService.kt`](file:///d:/Dev/x-codes/core/git/src/main/kotlin/digital/vmstudio/code/core/git/CommandGitService.kt), [`ProjectCreateViewModel.kt`](file:///d:/Dev/x-codes/feature/projects/src/main/kotlin/digital/vmstudio/code/feature/projects/ProjectCreateViewModel.kt), [`ProjectDetailViewModel.kt`](file:///d:/Dev/x-codes/feature/projects/src/main/kotlin/digital/vmstudio/code/feature/projects/ProjectDetailViewModel.kt), [`TerminalViewModel.kt`](file:///d:/Dev/x-codes/feature/terminal/src/main/kotlin/digital/vmstudio/code/feature/terminal/TerminalViewModel.kt)
- **Root Cause**: In POSIX shells (bash/sh), tilde expansion does NOT occur inside single quotes (`'~/projects'`). When paths like `"~/projects/app"` were quoted as `'~/...'`, commands like `cd '~/projects'`, `tar -C '~/...'`, `mkdir -p '~/...'`, and `git clone ... '~/...'` failed with `No such file or directory` or created a literal folder named `~`.
- **Fix**: Implemented `shellQuotePath(path: String)` in `CommandResult.kt`, which leaves leading `~` unquoted (e.g. `~/'projects/app'`) to enable shell tilde expansion while strictly quoting all subsequent path components against injection. Deployed across all remote command invocations.

### Bug 22: RemoteFileSystem Virtual Server Identifier & GitHub/VMHub Route Mismatch
- **Severity**: HIGH (Zero-clone GitHub repositories trigger failed SSH handshakes)
- **Files**: [`RemoteFileSystem.kt:113`](file:///d:/Dev/x-codes/core/sftp/src/main/kotlin/digital/vmstudio/code/core/sftp/fs/RemoteFileSystem.kt), [`SftpRemoteFileSystem.kt:51`](file:///d:/Dev/x-codes/core/sftp/src/main/kotlin/digital/vmstudio/code/core/sftp/fs/SftpRemoteFileSystem.kt), [`VmProjectHubClient.kt:53`](file:///d:/Dev/x-codes/core/project/src/main/kotlin/digital/vmstudio/code/core/project/VmProjectHubClient.kt), [`ProjectRepository.kt:130`](file:///d:/Dev/x-codes/core/project/src/main/kotlin/digital/vmstudio/code/core/project/ProjectRepository.kt)
- **Root Cause**: `RemoteFileSystem.isLocalServer()` omitted virtual server IDs `"github"` and `"vmhub"`. `SftpRemoteFileSystem.isLocalOrVirtual()` omitted `/github/` path checks. When interacting with zero-clone repos or virtual projects, SFTP calls attempted to establish SSH sessions to host `"github"`. Furthermore, `VmProjectHubClient.universalPath()` discarded `serverPath` starting with `~` and `ProjectRepository` rejected bare `~` paths.
- **Fix**: Added `"github"` and `"vmhub"` to `isLocalServer()`, included `path.startsWith("/github/")` in `isLocalOrVirtual()`, permitted tilde paths in `universalPath()`, and allowed bare `~` in `ProjectRepository` validation.

### Bug 23: Non-Atomic HTTP Download Corrupting Local Files on Abort or Interruption
- **Severity**: MEDIUM (Network interruption leaves truncated corrupt files in cache/storage)
- **File**: [`VmHttpClient.kt:128`](file:///d:/Dev/x-codes/core/network/src/main/kotlin/digital/vmstudio/code/core/network/http/VmHttpClient.kt)
- **Root Cause**: `download()` wrote directly to the final `destination` output stream while streaming over the network. On socket timeouts, drops, or cancellations, `destination` remained on disk with partial, corrupted content.
- **Fix**: Implemented atomic downloads writing to a temporary `.part.<timestamp>` file and atomically renaming/moving on successful completion with guaranteed cleanup in a `finally` block.

### Bug 24: False-Positive Build Success on Remote Gradle Compilation Failure
- **Severity**: HIGH (Failed Gradle builds falsely reported as successful)
- **File**: [`ProjectDetailViewModel.kt:236`](file:///d:/Dev/x-codes/feature/projects/src/main/kotlin/digital/vmstudio/code/feature/projects/ProjectDetailViewModel.kt)
- **Root Cause**: In `buildAndroidApk()`, `commandGuard.run` returned `VmResult.Success(CommandResult(...))` whenever the SSH process execution finished, without checking `res.value.isSuccess` (`exitCode == 0`). Compilation errors exiting with code 1 were falsely reported as "Build successful!" and logged to the activity database.
- **Fix**: Added explicit `res.value.isSuccess` guard, extracting stderr excerpts and surfacing actionable `VmError.Command` failures on non-zero exit codes.

### Bug 25: Terminal Virtual & SAF Path CD Navigation Failure on SSH Connect
- **Severity**: LOW (Terminal shell displays `cd: No such file or directory` immediately on connection)
- **File**: [`TerminalViewModel.kt:113`](file:///d:/Dev/x-codes/feature/terminal/src/main/kotlin/digital/vmstudio/code/feature/terminal/TerminalViewModel.kt)
- **Root Cause**: When opening a terminal from a GitHub zero-clone (`/github/...`), VMHub (`/vmhub/...`), or phone SAF (`content://...`) project, `TerminalViewModel` dispatched `cd <path>` to the remote VPS shell, triggering error messages.
- **Fix**: Filtered out virtual and SAF URI paths from automatic initial terminal cd dispatch.

### Bug 26: Android P+ `getPackageArchiveInfo` Signature Extraction Failure
- **Severity**: MEDIUM (Self-update verification rejects legitimate updates on several Android OEM builds)
- **File**: [`ApkInstaller.kt:60, 74`](file:///d:/Dev/x-codes/core/update/src/main/kotlin/digital/vmstudio/code/core/update/ApkInstaller.kt)
- **Root Cause**: Android P+ introduced `PackageManager.GET_SIGNING_CERTIFICATES`, but on Android 9â€“11 OEM implementations, `getPackageArchiveInfo` leaves `signingInfo` unpopulated unless `PackageManager.GET_SIGNATURES` is also supplied in the flags bitmask. This caused `signingFingerprints` to return empty, leading `signatureMatchesInstalledApp()` to fail verification and refuse valid self-updates.
- **Fix**: Combined flags via `PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES` in both archive and installed signature inspections.

### Bug 27: Unhandled Optimistic Kanban State Desync on Network Failure
- **Severity**: LOW (Tasks UI displays outdated status after failed remote update)
- **File**: [`TasksViewModel.kt:85`](file:///d:/Dev/x-codes/feature/tasks/src/main/kotlin/digital/vmstudio/code/feature/tasks/TasksViewModel.kt)
- **Root Cause**: `cycleHubTaskStatus()` updated task status optimistically in local state, but omitted error handling when `projectHubClient.updateTaskStatus()` returned `VmResult.Failure`. The UI remained in the updated state while the server retained the old state.
- **Fix**: Preserved previous status and added automated rollback if the network mutation returns `VmResult.Failure`.



### Bug 28: Irrevocable GitHub Personal Access Token & Missing Disconnect Option
- **Severity**: MEDIUM (User unable to disconnect GitHub account or remove stored token from device)
- **Files**: [GitHubRepositoryClient.kt:97](file:///d:/Dev/x-codes/core/git/src/main/kotlin/digital/vmstudio/code/core/git/GitHubRepositoryClient.kt), [GitHubSettingsViewModel.kt:65](file:///d:/Dev/x-codes/feature/settings/src/main/kotlin/digital/vmstudio/code/feature/settings/GitHubSettingsViewModel.kt), [GitHubSettingsScreen.kt:52](file:///d:/Dev/x-codes/feature/settings/src/main/kotlin/digital/vmstudio/code/feature/settings/GitHubSettingsScreen.kt)
- **Root Cause**: GitHubRepositoryClient and GitHubSettingsViewModel had methods to save tokens, but no mechanism to clear, revoke, or disconnect a saved GitHub PAT. Once added, a token could never be removed from the phone without clearing entire application storage.
- **Fix**: Implemented hasToken() and clearToken() in GitHubRepositoryClient, updated GitHubSettingsViewModel with reactive token existence state and clearToken(), and added a dedicated "Disconnect" button in GitHubSettingsScreen.

### Bug 29: Terminal Command Safety Check Bypass on Route Argument Absence
- **Severity**: HIGH (Dangerous shell commands bypass CommandGuard authorization checks when opening terminal without route serverId)
- **File**: [TerminalViewModel.kt:221](file:///d:/Dev/x-codes/feature/terminal/src/main/kotlin/digital/vmstudio/code/feature/terminal/TerminalViewModel.kt)
- **Root Cause**: TerminalViewModel.submitLine() evaluated command safety against al server = serverId (the route's optional ARG_SERVER_ID), ignoring session.serverId. If the route had no serverId or if the user switched tabs to another server's terminal session, commandGuard.authorize either didn't run at all (bypassing all safety checks for dangerous commands) or checked against the wrong server.
- **Fix**: Updated TerminalViewModel.submitLine() to evaluate authorization against the active session.serverId.

### Bug 30: Extensionless File Corruption in Virtual & Local File System
- **Severity**: HIGH (Creating or opening extensionless files like Dockerfile, Makefile, LICENSE, README converts them into directories)
- **File**: [LocalFileSystemDelegate.kt:38](file:///d:/Dev/x-codes/core/sftp/src/main/kotlin/digital/vmstudio/code/core/sftp/fs/LocalFileSystemDelegate.kt)
- **Root Cause**: esolveLocalFile() assumed any path without a dot in its final component was a directory and executed esolved.mkdirs(). This turned extensionless files (Dockerfile, Makefile, LICENSE, README, etc.) into directories, causing subsequent file reads, writes, and creations to fail with Is a directory or Failed to create file.
- **Fix**: Removed speculative esolved.mkdirs() from esolveLocalFile(), relying on explicit parent directory creation in writeText, createFile, ename, and list.

### Bug 31: Local APK Installation Crash & FileProvider Root Access Violation
- **Severity**: MEDIUM (Crash on installing local project APKs due to uncaught IllegalArgumentException / missing FileProvider mapping)
- **Files**: [ProjectDetailViewModel.kt:290](file:///d:/Dev/x-codes/feature/projects/src/main/kotlin/digital/vmstudio/code/feature/projects/ProjectDetailViewModel.kt), [ApkInstaller.kt:30](file:///d:/Dev/x-codes/core/update/src/main/kotlin/digital/vmstudio/code/core/update/ApkInstaller.kt), [UpdateManager.kt:130](file:///d:/Dev/x-codes/core/update/src/main/kotlin/digital/vmstudio/code/core/update/UpdateManager.kt)
- **Root Cause**: In ProjectDetailViewModel.downloadAndInstallApk(), local project APKs at arbitrary filesystem paths were passed directly to pkInstaller.install(file). If the path fell outside the roots declared in ile_paths.xml, FileProvider.getUriForFile threw an uncaught IllegalArgumentException, crashing the application. Furthermore, pkInstaller.install was not wrapped in unCatching, exposing the app to ActivityNotFoundException or SecurityException.
- **Fix**: In ProjectDetailViewModel, copied local APK files into the app's cache directory (cacheDir/downloads/) where FileProvider has guaranteed configured access under <cache-path name="downloads" .../>. Wrapped ApkInstaller.install() in unCatching returning a VmResult<Unit> with proper VmError emission if the system installer fails to launch.



### Bug 32: Silent Failure & HTTP 422 Unprocessable Entity in GitHub Content Commits
- **Severity**: HIGH (Attempting to edit or save files on GitHub with invalid tokens or during transient failures produces confusing 422 Unprocessable Entity errors)
- **File**: [GitHubRepositoryClient.kt:345](file:///d:/Dev/x-codes/core/git/src/main/kotlin/digital/vmstudio/code/core/git/GitHubRepositoryClient.kt)
- **Root Cause**: In putFileContent(), failure of metaRequest (fetching the existing file's SHA) was silently swallowed and mapped to 
ull (existingSha = null). Only HTTP 404 indicates a truly new file; treating authentication failures (401/403) or network timeouts as "new file" triggered a secondary doomed PUT request without sha, which GitHub rejected with 422 Unprocessable Entity ("sha" wasn't supplied).
- **Fix**: Verified metaResponse.error. If the error is an HTTP 404, existingSha is safely set to null; for all other network/authentication failures, the method aborts immediately and returns the actual error to the user.

### Bug 33: GNU ind -printf Failure on BSD / macOS / Alpine Linux Remote Build Hosts
- **Severity**: MEDIUM (Agent tool list_apks fails with exit code 1 on non-GNU / Busybox Linux containers)
- **File**: [OmniRouteAgentLoop.kt:1130](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/omniroute/agent/OmniRouteAgentLoop.kt)
- **Root Cause**: listApks() executed ind . -maxdepth 5 -type f -name '*.apk' -printf '%P (%s bytes)\n'. The -printf flag is a GNU findutils extension. On Alpine Linux (Busybox), FreeBSD, OpenBSD, NetBSD, and macOS, ind terminates with exit code 1 (ind: -printf: unknown primary or operator).
- **Fix**: Replaced -printf with POSIX-compliant fallback (find . -maxdepth 5 -type f -name '*.apk' -exec ls -lh {} + 2>/dev/null || find . -maxdepth 5 -type f -name '*.apk').

### Bug 34: Orphaned WAITING_APPROVAL Agent Tasks on Process Death
- **Severity**: MEDIUM (Tasks left perpetually stuck in "Waiting Approval" state after app kill or crash)
- **File**: [AiDao.kt:182](file:///d:/Dev/x-codes/core/database/src/main/kotlin/digital/vmstudio/code/core/database/dao/AiDao.kt)
- **Root Cause**: When the app process is terminated while an agent task is waiting for user confirmation (e.g. for a dangerous command or file edit), 	oolExecutionDao.cancelInterrupted() marks the tool as cancelled, but gentTaskDao.reconcileInterrupted() omitted AgentTaskStatus.WAITING_APPROVAL from the list of in-flight statuses. The task was left permanently orphaned in WAITING_APPROVAL with no active approval dialog available to resume it.
- **Fix**: Added AgentTaskStatus.WAITING_APPROVAL to gentTaskDao.reconcileInterrupted()'s status list so orphaned tasks are cleanly transitioned to FAILED with an informative reason at startup.



### Bug 35: Google Drive API v3 MIME Type Rejection & Binary Archive Download Corruption
- **Severity**: HIGH (Google Drive project backup uploads fail with 400 Bad Request; binary download converts bytes through lossy UTF-8 String)
- **File**: [GoogleDriveClient.kt:110, 145](file:///d:/Dev/x-codes/core/connectors/src/main/kotlin/digital/vmstudio/code/core/connectors/drive/GoogleDriveClient.kt)
- **Root Cause**: GoogleDriveClient.uploadFile() used MultipartBody.Builder().setType(MultipartBody.FORM) with form-data parts. Google Drive API v3 /upload/drive/v3/files?uploadType=multipart strictly rejects multipart/form-data with HTTP 400 Bad Request, requiring multipart/related. Additionally, downloadFile() only returned VmResult<String>, corrupting binary project archives (.tar.gz, .zip) through UTF-8 string conversion.
- **Fix**: Changed upload multipart format to multipart/related with clean JSON and file parts, and added streaming downloadFileTo(accessToken, fileId, destination) leveraging httpClient.download for binary-safe disk streaming.

### Bug 36: Compose TextField IME Composition Out-Of-Bounds Crash
- **Severity**: MEDIUM (Crash with IndexOutOfBoundsException when undoing, redoing, or replacing text while soft keyboard IME composition is active)
- **Files**: [EditorState.kt:130](file:///d:/Dev/x-codes/core/editor/src/main/kotlin/digital/vmstudio/code/core/editor/EditorState.kt), [EditorViewModel.kt:236](file:///d:/Dev/x-codes/feature/editor/src/main/kotlin/digital/vmstudio/code/feature/editor/EditorViewModel.kt)
- **Root Cause**: When programmatic text changes occurred (such as undo, redo, or multi-occurrence replacement), TextFieldValue.copy() preserved the active composition: TextRange? range. If the restored text was shorter than the old composing span, Compose's BasicTextField crashed with IndexOutOfBoundsException: composition out of bounds.
- **Fix**: Explicitly cleared composition = null in EditorState.applyText() and EditorViewModel.replace() / goToLine().

### Bug 37: Orphaned Dangling Credential Files on Partial Keystore Persistence Failure
- **Severity**: LOW (Security hygiene: failed credential index writes leave unindexed orphaned ciphertext files on internal storage)
- **File**: [FileSecureCredentialStore.kt:205](file:///d:/Dev/x-codes/core/security/src/main/kotlin/digital/vmstudio/code/core/security/store/FileSecureCredentialStore.kt)
- **Root Cause**: In writeSecretLocked(), the encrypted credential file ref.id.bin was written to disk before committing the updated reference to the index. If persistIndexLocked() failed (e.g. disk full or crypto failure), the newly created file was left on disk permanently as an unreferenced orphan that standard delete operations could never clean up.
- **Fix**: Wrapped the write pipeline with when (operation) cleanup to delete the staged file if index loading or persistence fails.

### Bug 38: Storage Access Framework (content://) & Virtual Workspace Remote Execution Leak
- **Severity**: HIGH (Remote command failures, path corruption, and illegal SFTP routing when operating on phone storage SAF or virtual projects)
- **Files**: [SftpRemoteFileSystem.kt:51, 142, 176](file:///d:/Dev/x-codes/core/sftp/src/main/kotlin/digital/vmstudio/code/core/sftp/fs/SftpRemoteFileSystem.kt), [OmniRouteAgentLoop.kt:674, 860, 965, 1033, 1090, 1121](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/omniroute/agent/OmniRouteAgentLoop.kt), [DiffReviewViewModel.kt:58, 112](file:///d:/Dev/x-codes/feature/ai/src/main/kotlin/digital/vmstudio/code/feature/ai/DiffReviewViewModel.kt)
- **Root Cause**: SftpRemoteFileSystem.isLocalOrVirtual only checked RemoteFileSystem.isLocalServer(serverId) and /vmhub/ or /github/ prefixes, omitting Android SAF content:// paths. When users opened phone folders with a remote server configured, SFTP attempted remote Linux file operations against content:// URIs. In OmniRouteAgentLoop, grepSearch, editFile, writeFile, runCommand, runBuild, and listApks failed to check content://, leading to SSH execution attempts of cd content://... && ./gradlew on remote VPS servers. DiffReviewViewModel also attempted remote git diff on local phone storage URIs.
- **Fix**: Added path.startsWith("content://") to isLocalOrVirtual(), unified rename() and setPermissions() to route virtual/SAF targets to localFs, added content:// guards to OmniRoute agent grep/edit/write/run tools, and prevented DiffReviewViewModel from invoking remote git operations on SAF documents.

### Bug 39: CommandGitService.diffAll Fatal Failure on Empty/Initial Repositories
- **Severity**: MEDIUM (Git diff view and DiffReviewViewModel crash on newly initialized Git repositories)
- **File**: [CommandGitService.kt:46](file:///d:/Dev/x-codes/core/git/src/main/kotlin/digital/vmstudio/code/core/git/CommandGitService.kt)
- **Root Cause**: diffAll(serverId, repoPath) executed git diff HEAD. In freshly initialized repositories (git init) prior to the first commit, HEAD does not exist. Git aborted with fatal: ambiguous argument 'HEAD': unknown revision and exit code 128, causing GitResult.Failure and UI crash.
- **Fix**: Changed command to git rev-parse --verify HEAD >/dev/null 2>&1 && git diff HEAD || git diff --cached 2>/dev/null || git diff to cleanly fall back to staged or working tree diffs when HEAD is not yet present.

### Bug 40: DiffParser Dropping File Mode Changes, Inverted Header Parsing, and Unquoted Path Omission
- **Severity**: MEDIUM (Diff parser silently omitted root files without diff prefixes, dropped mode/rename flags, and corrupted paths with quotes)
- **Files**: [DiffParser.kt:8, 26](file:///d:/Dev/x-codes/core/git/src/main/kotlin/digital/vmstudio/code/core/git/DiffParser.kt), [GitParsers.kt:38](file:///d:/Dev/x-codes/core/git/src/main/kotlin/digital/vmstudio/code/core/git/GitParsers.kt)
- **Root Cause**: FILE_HEADER_OLD and FILE_HEADER_NEW in DiffParser required a/ or b/ with a mandatory /, failing to match diffs formatted without prefixes (diff.noprefix = true) for repo-root files. Furthermore, new file mode, deleted file mode, and rename from occur before --- / +++ in git diff format, but were checked after, making mode detection unreachable dead code. Additionally, porcelain status parser in GitParsers retained surrounding quotes for paths containing spaces.
- **Fix**: Updated FILE_HEADER_OLD and FILE_HEADER_NEW to support optional prefixes and strip tab-separated metadata, tracked mode and rename headers from the git diff block, and stripped quotes and unescaped porcelain paths.

### Bug 41: Zip Slip Path Traversal, Silent Download Failures, & Directory Flattening in GitHub Archive Unpacking
- **Severity**: HIGH (Security & Project Creation Failure)
- **Files**: [`ProjectCreateViewModel.kt`](file:///d:/Dev/x-codes/feature/projects/src/main/kotlin/digital/vmstudio/code/feature/projects/ProjectCreateViewModel.kt), [`ProjectDetailViewModel.kt`](file:///d:/Dev/x-codes/feature/projects/src/main/kotlin/digital/vmstudio/code/feature/projects/ProjectDetailViewModel.kt)
- **Root Cause**: Unpacking downloaded zip archives during GitHub project import did not validate canonical child paths against destination directories, allowing arbitrary file overwrites via malicious zip slip traversal paths (`../../`). Furthermore, HTTP errors on archive downloads silently returned empty files instead of failing, and root folder stripping stripped all internal folder structure.
- **Fix**: Added `safeZipEntryFile()`, `isTrustedGithubArchiveRedirect()`, safe filename sanitizers, and proper `VmResult.Failure` propagation on HTTP errors, ensuring path containment and directory structure preservation.

### Bug 42: Editor Syntax Highlighter Suppressing All Function Calls & Corrupting Hex/Binary Numbers
- **Severity**: MEDIUM (Editor Visual & Usability Bug)
- **Files**: [`RegexSyntaxHighlighter.kt`](file:///d:/Dev/x-codes/core/editor/src/main/kotlin/digital/vmstudio/code/core/editor/RegexSyntaxHighlighter.kt), [`RegexSyntaxHighlighterTest.kt`](file:///d:/Dev/x-codes/core/editor/src/test/kotlin/digital/vmstudio/code/core/editor/RegexSyntaxHighlighterTest.kt)
- **Root Cause**: Syntax highlighter regex for numbers matched `\b\d+\b`, which corrupted hexadecimal numbers (`0x1F` had only `0` highlighted while `x1F` was treated as an identifier) and binary numbers (`0b101`). Furthermore, function calls followed by parentheses were consumed as general identifiers before lookahead, leaving function calls uncolored.
- **Fix**: Updated number regex to support hex (`0x...`), binary (`0b...`), and float suffixes. Implemented immediate function invocation detection during word boundary scanning.

### Bug 43: Empty Stream / Ping Chunk Silently Terminating Agent with 0 Tokens & Suppressing Failover
- **Severity**: HIGH (Agent Silent Early Termination / Empty Chat Screen)
- **Files**: [`OmniRouteStreamAssembler.kt`](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/omniroute/agent/OmniRouteStreamAssembler.kt), [`OmniRouteToolCodec.kt`](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/omniroute/agent/OmniRouteToolCodec.kt), [`OmniRouteAgentLoop.kt`](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/omniroute/agent/OmniRouteAgentLoop.kt)
- **Root Cause**: When an SSE stream opened and received only ping/framing chunks without text deltas or tool calls, `sawAnything` became true and `finish()` returned `OmniRouteTurn.Text("", usage, lengthStop)`. In `OmniRouteAgentLoop`, this was treated as a complete response. The agent never retried non-streaming, never triggered fallback model failover, and immediately emitted `AgentEvent.Completed(durationMillis = 1000, inputTokens = 0, outputTokens = 0, isError = false)`. The UI rendered `[✔] 1s 0 tokens` with an empty transcript.
- **Fix**: Modified `finish()` to return `OmniRouteTurn.ParseFailed("The stream carried no model output.")` when text is blank and tool calls are empty. Updated `OmniRouteToolCodec` for OpenAI and Anthropic to do the same. Added `ParseFailed` to `shouldFailover = true` in `OmniRouteAgentLoop` to force failover and non-streaming fallback, and guarded `OmniRouteTurn.Text` to emit `AgentEvent.Failed` if blank text is ever encountered.

### Bug 44: GitParsers Porcelain Status Discards Original Path on Renames/Copies & Drops Commits with Empty Subjects
- **Severity**: MEDIUM (Git Status & History Corrupted)
- **Files**: [`GitParsers.kt`](file:///d:/Dev/x-codes/core/git/src/main/kotlin/digital/vmstudio/code/core/git/GitParsers.kt)
- **Root Cause**: Porcelain status lines with `R  old -> new` or `C  old -> new` parsed only `new` and left `originalPath` null, losing track of the file's original name. Source paths with quotes were also unhandled. In `parseLog`, commits with empty subjects or whitespace were discarded because `lines.size >= 7` check failed on trimmed blocks.
- **Fix**: Captured and unquoted `rawOriginal`, populated `originalPath = originalPath` in `GitFileStatusEntry`, and adjusted `parseLog` to handle commits with empty subjects when `lines.size >= 6`.

### Bug 45: GoogleSheetsClient SerializationException on Heterogeneous Spreadsheet Cells
- **Severity**: MEDIUM (Google Sheets API Connector Failure)
- **Files**: [`GoogleSheetsClient.kt`](file:///d:/Dev/x-codes/core/connectors/src/main/kotlin/digital/vmstudio/code/core/connectors/sheets/GoogleSheetsClient.kt)
- **Root Cause**: `SheetValueRange` typed `values` as `List<List<String>>`. Google Sheets API returns unquoted numbers (`123`), booleans (`true`), and nulls in cell arrays. `json.decodeFromString<SheetValueRange>` threw `SerializationException` whenever a spreadsheet contained non-string cells.
- **Fix**: Changed `values` to `List<List<JsonElement>>` with dynamic `stringValues: List<List<String>>` accessor, and made response fields nullable.

### Bug 46: SseDecoder Never Cleared Accumulated Event Data or Event Name Upon Dispatch
- **Severity**: HIGH (SSE Stream Memory Leak & Payload Corruption)
- **Files**: [`StreamDecoding.kt`](file:///d:/Dev/x-codes/core/network/src/main/kotlin/digital/vmstudio/code/core/network/http/StreamDecoding.kt)
- **Root Cause**: `SseDecoder.dispatch()` extracted `data.toString()` and `eventName` but never cleared `data.setLength(0)` or reset `eventName = null` when dispatching an event. Every subsequent event in the stream appended onto all previous events, resulting in exponential payload growth, malformed JSON, and 5 unit test failures in `:core:network`.
- **Fix**: Added `data.setLength(0)` and `eventName = null` directly in `dispatch()`. All 13 unit tests now pass.

### Bug 47: CommandSafety Fails to Block Deletion of Home Directory (`rm -rf ~`, `rm -rf $HOME`) and Multi-Slash Roots
- **Severity**: HIGH (Catastrophic Data Loss Protection Bypass)
- **Files**: [`CommandSafety.kt`](file:///d:/Dev/x-codes/core/ssh/src/main/kotlin/digital/vmstudio/code/core/ssh/command/CommandSafety.kt)
- **Root Cause**: `analyseRemove` checked only `it == "/" || it == "/*"`. Commands targeting `rm -rf ~`, `rm -rf $HOME`, `rm -rf ~/`, or `rm -rf //` bypassed the root deletion blocker and were allowed or only flagged as general destructive.
- **Fix**: Added `targetsHome` check covering `~`, `~/`, `~/*`, `$HOME`, and added multi-slash detection (`clean.all { it == '/' }`). Added `rm-rf-home` rule categorized as `CommandRisk.BLOCKED`.

### Bug 48: TerminalSession Blocking Single-Threaded `emulatorDispatcher` on Socket Write
- **Severity**: HIGH (UI hang, input delay, and frame freeze during terminal output)
- **File**: [`TerminalSession.kt`](file:///d:/Dev/x-codes/core/terminal/src/main/kotlin/digital/vmstudio/code/core/terminal/session/TerminalSession.kt)
- **Root Cause**: `send()` launched on `emulatorDispatcher` (`limitedParallelism(1)`), running blocking socket write `channel.output.write(...)` and `flush()`. Under network latency or backpressure, this blocked the single thread of `emulatorDispatcher`, starving `resize()`, `publishSnapshot()`, and the reader loop from mutating the emulator.
- **Fix**: Moved `channel.output.write()` to `readerDispatcher` (`Dispatchers.IO`), and kept `_scrollOffset.value = 0` immediate and thread-safe.

### Bug 49: TerminalBuffer `scrollUp` / `scrollDown` IndexOutOfBounds on Invalid Margins or Inverted Regions
- **Severity**: MEDIUM (Terminal emulator crash on corrupted or negative CSI scroll regions)
- **File**: [`TerminalBuffer.kt`](file:///d:/Dev/x-codes/core/terminal/src/main/kotlin/digital/vmstudio/code/core/terminal/emulator/TerminalBuffer.kt)
- **Root Cause**: `scrollUp(top, bottom, count, style)` and `scrollDown` lacked boundary validation (`top < 0`, `bottom >= rows`, or `top > bottom`). If ANSI input or window resizing set an inverted or out-of-bounds scroll region, `screen.removeAt(top)` crashed with `IndexOutOfBoundsException`.
- **Fix**: Added guard `if (count <= 0 || top !in 0 until rows || bottom !in 0 until rows || top > bottom) return` to both methods.

### Bug 50: ApkDownloader Cache Directory Traversal on Untrusted Release Asset Names
- **Severity**: MEDIUM (Path traversal vulnerability in self-update mechanism)
- **File**: [`ApkDownloader.kt`](file:///d:/Dev/x-codes/core/update/src/main/kotlin/digital/vmstudio/code/core/update/ApkDownloader.kt)
- **Root Cause**: `destFile = File(dir, fileName)` used raw `fileName` from GitHub release asset name without stripping directory traversal characters (`../`).
- **Fix**: Sanitized `val safeName = File(fileName).name.ifBlank { "update.apk" }` before creating `destFile` and `partFile`.

### Bug 51: Editor Zero-Clone GitHub File Path Extraction Off-By-One on Root Repos
- **Severity**: LOW/MEDIUM (Editor crash or empty string path write when opening `/github/owner/repo`)
- **File**: [`EditorViewModel.kt`](file:///d:/Dev/x-codes/feature/editor/src/main/kotlin/digital/vmstudio/code/feature/editor/EditorViewModel.kt)
- **Root Cause**: Checked `if (parts.size >= 2)` instead of `if (parts.size >= 3)`. For `/github/owner/repo`, `relativePath` became `""`, causing invalid GitHub contents API calls and falling through to local filesystem operations.
- **Fix**: Enforced `parts.size >= 3`, validating `relativePath.isNotBlank()` and returning a clean `VmError.FileSystem` error otherwise.

### Bug 52: GitHubRepositoryClient `putFileContent` Missing Pre-Check on Directory Targets
- **Severity**: MEDIUM (GitHub API 409 Conflict / crash when saving over a directory path)
- **File**: [`GitHubRepositoryClient.kt`](file:///d:/Dev/x-codes/core/git/src/main/kotlin/digital/vmstudio/code/core/git/GitHubRepositoryClient.kt)
- **Root Cause**: GitHub contents API returns a JSON array when `path` is a folder, causing `JSONObject(metaResponse.value).optString("sha")` to fail or produce a 409 conflict when saving.
- **Fix**: Detected `metaResponse.value.trimStart().startsWith("[")` and returned `VmError.FileSystem("Cannot overwrite directory with file content")`.

### Bug 53: ProjectDetailViewModel.downloadApkToPhoneDownloads Path Traversal & Unchecked Failures
- **Severity**: MEDIUM (Security vulnerability, misleading success message on failed downloads, and UI hang)
- **File**: [`ProjectDetailViewModel.kt`](file:///d:/Dev/x-codes/feature/projects/src/main/kotlin/digital/vmstudio/code/feature/projects/ProjectDetailViewModel.kt)
- **Root Cause**: `downloadApkToPhoneDownloads` omitted filename validation via `safeApkDisplayName`, ignored `TransferEvent.Failed` during remote downloads (falsely notifying "Saved to Downloads"), and lacked try-finally error handling which left `isDownloadingApk = true` indefinitely on I/O failure.
- **Fix**: Applied `safeApkDisplayName`, inspected `apkTransferFailure(lastEvent)`, and wrapped the operation in `try-finally` to guarantee progress state reset.

### Bug 54: SftpRemoteFileSystem & LocalFileSystemDelegate Unchecked Root Directory Deletion
- **Severity**: HIGH (Catastrophic recursive destruction of remote server or phone storage root)
- **Files**: [`SftpRemoteFileSystem.kt`](file:///d:/Dev/x-codes/core/sftp/src/main/kotlin/digital/vmstudio/code/core/sftp/fs/SftpRemoteFileSystem.kt), [`LocalFileSystemDelegate.kt`](file:///d:/Dev/x-codes/core/sftp/src/main/kotlin/digital/vmstudio/code/core/sftp/fs/LocalFileSystemDelegate.kt)
- **Root Cause**: `delete(serverId, path, recursive)` had no defensive barrier against `path == "/"` or blank path. Calling recursive delete on `/` triggered `deleteRecursively(sftp, "/")` or `File("/").deleteRecursively()`, attempting to destroy all root-level directories.
- **Fix**: Added explicit guards rejecting deletion of `/`, blank paths, and filesystem root nodes in both implementations.

### Bug 55: TerminalViewModel Multiline Paste Keystroke Scrambling & Desynchronization
- **Severity**: MEDIUM (Terminal input corruption and scrambled command execution on pasted scripts)
- **File**: [`TerminalViewModel.kt`](file:///d:/Dev/x-codes/feature/terminal/src/main/kotlin/digital/vmstudio/code/feature/terminal/TerminalViewModel.kt), [`TerminalView.kt`](file:///d:/Dev/x-codes/feature/terminal/src/main/kotlin/digital/vmstudio/code/feature/terminal/TerminalView.kt)
- **Root Cause**: When pasting multiline text, `send()` iterated over lines, emitting `activeSession()?.send(part)` synchronously while launching `submitLine()` on an asynchronous coroutine. Later lines were sent to the remote shell before the preceding line's ENTER was authorized and committed, merging commands into unexecutable strings. Also, `TerminalView` omitted `onDragCancel` and lacked full cursor bounds checks.
- **Fix**: Sequentialized multiline transmission in a single coroutine using `submitLineInternal(): Boolean`, suspending until authorization completes and halting if blocked. Added `onDragCancel` reset and complete row/column cursor bounds checking.

### Bug 56: OmniRouteAgentLoop Missing Root Target Guard for GitHub Workspaces in File Operations
- **Severity**: LOW/MEDIUM (Invalid GitHub API requests and failed agent edits on `/github/owner/repo`)
- **File**: [`OmniRouteAgentLoop.kt`](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/omniroute/agent/OmniRouteAgentLoop.kt)
- **Root Cause**: `parseGitHubPath(resolved)` returned `repoFullName to ""` for repo root paths. While `readFile` defended against empty relative paths, `writeFile` and `editFile` did not, attempting to read and write repository root as a blob.
- **Fix**: Added `if (relativePath.isBlank()) return ToolOutcome("Cannot write/edit repository root as a file.", isError = true)` in both tools.

### Bug 57: VmHttpClient Stream Trailing SSE Event Flush Verification
- **Severity**: LOW/MEDIUM (Stream Termination Reliability)
- **File**: [`VmHttpClient.kt`](file:///d:/Dev/x-codes/core/network/src/main/kotlin/digital/vmstudio/code/core/network/http/VmHttpClient.kt)
- **Root Cause**: SSE streams terminating without a trailing blank line previously dropped the final buffered chunk. Audited and verified `decoder.finish()?.takeIf { !it.isDone }?.let { emit(it) }` executes reliably on stream EOF.
- **Fix**: Verified stream EOF flushing to ensure no partial final event is lost when connection drops or completes.

### Bug 58: FilesViewModel Unchecked Name Traversal & Share Transfer State Hang
- **Severity**: MEDIUM (File creation directory escape & UI transfer freeze)
- **File**: [`FilesViewModel.kt`](file:///d:/Dev/x-codes/feature/files/src/main/kotlin/digital/vmstudio/code/feature/files/FilesViewModel.kt)
- **Root Cause**: `createDirectory`, `createFile`, and `rename` did not validate inputs, allowing names with traversal characters (`../`, `/`, `\\`) or blank strings to mutate unintended parent paths. Additionally, `share()` did not wrap file download in `try-finally`, leaving `_uiState.transfer` and `activeTransferJob` non-null on download failure, blocking subsequent transfers.
- **Fix**: Added defensive name checks (`clean.isBlank() || clean == "." || clean == ".." || clean.contains('/') || clean.contains('\\')`) returning `VmError.Validation`, and enclosed `share()` in `try-finally` to guarantee transfer state reset.

### Bug 59: ProjectDetailViewModel Blank Task Insertion & AiDao SQL Enum Inconsistency
- **Severity**: LOW/MEDIUM (Data integrity & SQL mapping robustness)
- **Files**: [`ProjectDetailViewModel.kt`](file:///d:/Dev/x-codes/feature/projects/src/main/kotlin/digital/vmstudio/code/feature/projects/ProjectDetailViewModel.kt), [`AiDao.kt`](file:///d:/Dev/x-codes/core/database/src/main/kotlin/digital/vmstudio/code/core/database/dao/AiDao.kt)
- **Root Cause**: `ProjectDetailViewModel.addTask` did not guard against blank titles, inserting empty tasks into the database when tapped with empty text. `AiDao.markInterrupted` hardcoded `'FAILED'` string literal in raw SQL instead of using a typed enum bind parameter `:failed`.
- **Fix**: Guarded `addTask` with `if (trimmed.isBlank()) return`, and updated `AiDao.markInterrupted` to accept `:failed` parameter matching Room's type converters.

### Bug 60: GitHubSettingsViewModel Hostile Error Banner on Fresh Launch Without Token
- **Severity**: MEDIUM (User Experience & False Failure Report)
- **File**: [`GitHubSettingsViewModel.kt`](file:///d:/Dev/x-codes/feature/settings/src/main/kotlin/digital/vmstudio/code/feature/settings/GitHubSettingsViewModel.kt)
- **Root Cause**: `loadRepositories()` called `github.repositories()` even when `github.hasToken()` was false. On fresh install, this triggered an immediate authentication error, painting a red error banner across the screen before the user had entered a token.
- **Fix**: Checked `if (!tokenExists)` early in `loadRepositories()`, resetting loading and repositories cleanly without surfacing an error.

### Bug 61: VmProjectHubClient Task ID URL-Encoding & Decimal Percentage Parsing
- **Severity**: LOW/MEDIUM (API Request Failure & Progress Calculation)
- **File**: [`VmProjectHubClient.kt`](file:///d:/Dev/x-codes/core/project/src/main/kotlin/digital/vmstudio/code/core/project/VmProjectHubClient.kt)
- **Root Cause**: `updateTaskStatus` interpolated `taskId` raw into the URL string without URL encoding. Task IDs with spaces or URL-special characters caused malformed request URLs. Additionally, `JsonObject.int` used `toIntOrNull()`, failing on percentage values serialized as floating-point strings (e.g. `"10.0"`).
- **Fix**: Encoded `taskId` via `URLEncoder.encode(taskId, "UTF-8")`, and added fallback `raw.toDoubleOrNull()?.toInt()` in `JsonObject.int`.

### Bug 62: ServerEditViewModel Test Connection Orphaned Server Entity Leak
- **Severity**: MEDIUM (Database Pollution & Unintended Draft Persistence)
- **File**: [`ServerEditViewModel.kt`](file:///d:/Dev/x-codes/feature/servers/src/main/kotlin/digital/vmstudio/code/feature/servers/ServerEditViewModel.kt)
- **Root Cause**: `testConnection()` called `serverRepository.save(current.draft)` to obtain an ID for `connectionManager.reconnect`. For new drafts, this committed a server entity to Room and KeyStore. If the test failed or the user cancelled without saving, the broken server was permanently orphaned in the database.
- **Fix**: Tracked `temporaryCreatedServerId`. If a test on a new server fails, it immediately deletes the temporary server entity. In `onCleared()`, any unconfirmed temporary server is automatically cleaned up from the database.

### Bug 63: GoogleDriveManager Backup Failure Masking on Harmless Temp File Cleanup Glitch
- **Severity**: MEDIUM (False Backup Failure Alert & Redundant Multi-Hundred MB Uploads)
- **File**: [`GoogleDriveManager.kt`](file:///d:/Dev/x-codes/core/connectors/src/main/kotlin/digital/vmstudio/code/core/connectors/drive/GoogleDriveManager.kt)
- **Root Cause**: When `backupProject()` successfully finished creating and uploading the project archive to Google Drive, it subsequently ran `cleanupBackup()` to delete temporary archives on the VPS and phone cache. If temp deletion encountered a minor filesystem warning, the entire method returned `VmResult.Failure`, reporting the backup as failed to the user despite successful upload to Google Drive.
- **Fix**: When `operationResult is VmResult.Success`, non-fatal temporary cleanup warnings are logged as `VmLog.w` without converting the successful upload result into a failure.

### Bug 64: GoogleSheetsClient & GoogleDriveClient Unencoded IDs & Empty Rows 400 Bad Request
- **Severity**: MEDIUM (Malformed API Requests & Google Cloud Endpoint Failures)
- **Files**: [`GoogleSheetsClient.kt`](file:///d:/Dev/x-codes/core/connectors/src/main/kotlin/digital/vmstudio/code/core/connectors/sheets/GoogleSheetsClient.kt), [`GoogleDriveClient.kt`](file:///d:/Dev/x-codes/core/connectors/src/main/kotlin/digital/vmstudio/code/core/connectors/drive/GoogleDriveClient.kt)
- **Root Cause**: `GoogleSheetsClient` interpolated `spreadsheetId` unencoded into API URLs, and `GoogleDriveClient` interpolated `fileId` unencoded into media download endpoints. Furthermore, when `GoogleSheetsClient.appendValues()` was invoked with empty `rows`, it dispatched an empty JSON payload that Google Sheets API rejected with `400 Bad Request: Invalid values[0]: Empty data array`.
- **Fix**: URL-encoded `spreadsheetId` and `fileId` via `URLEncoder.encode(..., "UTF-8")`. Added fast-exit in `appendValues` for empty row collections, returning success without dispatching invalid payloads.

### Bug 65: CommandSafety Incomplete `$HOME/*` Pattern & Tokenizer Escaped-Quote Desynchronization
- **Severity**: HIGH (Catastrophic Shell Command Protection Gap & Tokenizer Corruption)
- **File**: [`CommandSafety.kt`](file:///d:/Dev/x-codes/core/ssh/src/main/kotlin/digital/vmstudio/code/core/ssh/command/CommandSafety.kt)
- **Root Cause**: `analyseRemove()` checked home directory targets but omitted `$HOME/*` and `${HOME}/*`, allowing `rm -rf $HOME/*` to slip through as `DESTRUCTIVE` rather than `BLOCKED`. Additionally, `tokenise()` lacked backslash-quote escape handling inside double quotes, treating `\"` as an unescaped closing quote and desynchronizing shell token boundaries.
- **Fix**: Added `$HOME/*` and `${HOME}/*` patterns to `targetsHome`, ensuring recursive removal is strictly `BLOCKED`. Added escaped-character lookahead in `tokenise()` within double-quoted tokens.

### Bug 66: AttachmentStager Cache File Collision & Phantom File Return on Failed Rename
- **Severity**: HIGH (Data Loss, Crash on Duplicate Attachments & File System Race Condition)
- **File**: [`AttachmentStager.kt`](file:///d:/Dev/x-codes/feature/ai/src/main/kotlin/digital/vmstudio/code/feature/ai/AttachmentStager.kt)
- **Root Cause**: `copyToCache()` created a uniquely named temp file and then unconditionally executed `File(target.parentFile, name).also { target.renameTo(it) }`. When multiple attachments shared the same filename (or `cacheDir/name` pre-existed), `renameTo` failed, leaving a non-existent file path returned to the caller (`FileNotFoundException`). Furthermore, in `stage()`, cleanup in `finally` deleted the shared name prematurely before other attachments finished processing.
- **Fix**: Replaced raw file renaming with `LocalAttachment(file, displayName)` holding isolated UUID-based cache files while preserving display names, guaranteeing collision-free, atomic staging and reliable cleanup.

### Bug 67: ChatComposer & AgentChatViewModel Zero-Prompt Attachment Transmission Blocker
- **Severity**: MEDIUM (User Experience & Multimodal Prompt Ingestion)
- **Files**: [`ChatComposer.kt`](file:///d:/Dev/x-codes/feature/ai/src/main/kotlin/digital/vmstudio/code/feature/ai/ChatComposer.kt), [`AgentChatViewModel.kt`](file:///d:/Dev/x-codes/feature/ai/src/main/kotlin/digital/vmstudio/code/feature/ai/AgentChatViewModel.kt)
- **Root Cause**: In `ModernComposer`, `canSend` was strictly gated on `text.isNotBlank()`, disabling the Send button when users attached photos or logs without accompanying text. Additionally, `AgentChatViewModel.send()` and `queueMessage()` immediately discarded submissions with blank text.
- **Fix**: Enabled send when `text.isNotBlank() || attachments.isNotEmpty()`, supplying a default instruction prompt (`"Please inspect the attached file(s)."`) for attachment-only transmissions.

### Bug 68: HtmlPreviewSheet WebView Memory Leak & Local/VM Hub Workspace 404 Disconnect
- **Severity**: MEDIUM (Native Memory Leak & Local HTML Preview Failure)
- **Files**: [`HtmlPreviewSheet.kt`](file:///d:/Dev/x-codes/feature/ai/src/main/kotlin/digital/vmstudio/code/feature/ai/HtmlPreviewSheet.kt), [`HtmlPreviewViewModel.kt`](file:///d:/Dev/x-codes/feature/ai/src/main/kotlin/digital/vmstudio/code/feature/ai/HtmlPreviewViewModel.kt)
- **Root Cause**: In `SandboxedWebView`, `AndroidView` instantiated `WebView` without an `onRelease` callback, leaking the native Chromium rendering engine and thread pool on sheet dismissal. In `HtmlPreviewViewModel.load()`, when viewing HTML in a local or VM Hub project, `serverId` was passed as empty string, causing `remoteFileSystem.readText` to fail looking up a non-existent SSH server.
- **Fix**: Added `onRelease = { webView -> webView.stopLoading(); webView.destroy() }` to `SandboxedWebView`. Resolved `effectiveServerId = if (serverId.isBlank() || path.startsWith("/vmhub/")) RemoteFileSystem.LOCAL_SERVER_ID else serverId`.

### Bug 69: EditorViewModel Find-Previous Index-0 Sticky Loop & Backward Search Freeze
- **Severity**: MEDIUM (Code Editor Navigation Freeze)
- **File**: [`EditorViewModel.kt`](file:///d:/Dev/x-codes/feature/editor/src/main/kotlin/digital/vmstudio/code/feature/editor/EditorViewModel.kt)
- **Root Cause**: In `EditorViewModel.findNext(query, forward = false)`, `(currentOffset - 1).coerceAtLeast(0)` evaluated to 0 when the selection was at the beginning of the document. `lastIndexOf(query, 0)` continually matched index 0, locking the editor cursor in an infinite loop on the first occurrence. Also, `currentModifiedAtSeconds()` performed unnecessary SFTP stat operations on virtual `/github/` paths.
- **Fix**: Adjusted backward search boundary so `currentOffset <= 0` immediately wraps to `lastIndexOf(query, text.length)`. Bypassed filesystem stat for GitHub virtual paths.

### Bug 70: FileSecureCredentialStore Replace Failure Secret Deletion & Index Desynchronization
- **Severity**: HIGH (Credential Destruction & Security Keystore Desynchronization)
- **File**: [`FileSecureCredentialStore.kt`](file:///d:/Dev/x-codes/core/security/src/main/kotlin/digital/vmstudio/code/core/security/store/FileSecureCredentialStore.kt)
- **Root Cause**: When updating an existing secret via `replace()`, `writeSecretLocked()` wrote new ciphertext to disk and attempted to encrypt and persist the updated index. If index persistence failed (e.g. storage full or Keystore failure), the error block executed `if (file.exists()) file.delete()`, destroying the existing secret while the index still referenced it.
- **Fix**: Backed up original secret bytes before replacing. If index update fails, the previous secret is automatically restored on disk rather than deleted.

### Bug 71: ProjectDetailViewModel Coroutine State Freeze on Build/Sync/Install Failure
- **Severity**: MEDIUM (UI State Locking & Indefinite Spinner Freeze)
- **File**: [`ProjectDetailViewModel.kt`](file:///d:/Dev/x-codes/feature/projects/src/main/kotlin/digital/vmstudio/code/feature/projects/ProjectDetailViewModel.kt)
- **Root Cause**: `buildAndroidApk()`, `downloadAndInstallApk()`, `loadGitStatus()`, `syncGit()`, and `commitAndPush()` updated `isBuildingApk`, `isDownloadingApk`, `isGitLoading`, and `isSyncing` to `true` without `try ... finally` blocks. Any coroutine cancellation or unhandled network failure left these flags `true` permanently, locking UI spinners.
- **Fix**: Wrapped all asynchronous build, download, and git operations in `try ... finally` blocks, ensuring flags always reset cleanly under all execution paths.

### Bug 72: ProjectsViewModel Permanent Workspace Counter Suppression via Stale Hub Status Message
- **Severity**: LOW (UI Subtitle Suppression & Stale Banner)
- **File**: [`ProjectsViewModel.kt`](file:///d:/Dev/x-codes/feature/projects/src/main/kotlin/digital/vmstudio/code/feature/projects/ProjectsViewModel.kt)
- **Root Cause**: `hubStatusMessage` was populated after `syncFromHub()` but never cleared or dismissed. In `ProjectsScreen.kt`, `state.hubStatusMessage ?: "${state.projects.size} active workspaces"` permanently hid the workspace count subtitle after the initial launch sync.
- **Fix**: Added an auto-dismiss delay (4s) to reset `hubStatusMessage` to `null` and added `dismissHubStatusMessage()` for explicit user dismissals.

### Bug 73: CommandGitService Restore Path Traversal Arbitrary rm Deletion & Detached HEAD Branch Blanking
- **Severity**: HIGH (Security & Git State Accuracy)
- **File**: [`CommandGitService.kt`](file:///d:/Dev/x-codes/core/git/src/main/kotlin/digital/vmstudio/code/core/git/CommandGitService.kt)
- **Root Cause**: `restoreFile` executed `git checkout HEAD -- $file || rm -f $file` without checking for path traversal (`..` or leading `/`), risking unintended file deletion outside the repository. Additionally, `currentBranch` returned an empty string `""` when the repository was in a detached HEAD state.
- **Fix**: Validated that `filePath` is a strictly relative repository path without `..` traversal before executing restore. Added fallback to `git rev-parse --short HEAD` when `git branch --show-current` returns blank.

### Bug 74: DiffParser Silently Drops 100% Similarity Renames and Binary File Changes
- **Severity**: MEDIUM (Diff Review Omission)
- **Files**: [`DiffParser.kt`](file:///d:/Dev/x-codes/core/git/src/main/kotlin/digital/vmstudio/code/core/git/DiffParser.kt), [`DiffParserTest.kt`](file:///d:/Dev/x-codes/core/git/src/test/kotlin/digital/vmstudio/code/core/git/DiffParserTest.kt)
- **Root Cause**: Pure file renames (100% similarity) and binary files (`Binary files a/... and b/... differ`) lack `--- ` / `+++ ` headers and were completely omitted from the parsed `GitDiff`, showing "No changes" in the diff review UI.
- **Fix**: Extended `DiffParser` with pending file tracking and `flushPendingIfNeeded()`, preserving renames without hunks and binary file diff entries with descriptive headers.

### Bug 75: OmniRouteToolCodec Drops Arguments in Anthropic Tool Calls When Passed as JSON String
- **Severity**: HIGH (Agentic Tool Execution Failure & Argument Loss)
- **File**: [`OmniRouteToolCodec.kt`](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/omniroute/agent/OmniRouteToolCodec.kt)
- **Root Cause**: In `parseAnthropicResponse`, `(block["input"] as? JsonObject) ?: JsonObject(emptyMap())` discarded tool arguments when an OpenAI-to-Anthropic proxy, Claude forwarder, or gateway delivered tool arguments as a JSON string primitive.
- **Fix**: Supported both `JsonObject` and `JsonPrimitive` (extracting raw string content) to preserve all tool arguments.

### Bug 76: GitHubRepositoryClient False-Failure on Old Token Deletion & Directory Guard
- **Severity**: MEDIUM (GitHub Token Persistence & File Read Robustness)
- **File**: [`GitHubRepositoryClient.kt`](file:///d:/Dev/x-codes/core/git/src/main/kotlin/digital/vmstudio/code/core/git/GitHubRepositoryClient.kt)
- **Root Cause**: `saveToken()` returned `VmResult.Failure(deleted.error)` if best-effort deletion of the replaced old token failed, falsely claiming failure despite the new token being saved. `getFileContent()` did not reject root/directory paths, returning directory JSON arrays as file content. `putFileContent()` silently proceeded without `sha` on 200 OK responses with missing blob metadata, causing GitHub 422 errors.
- **Fix**: Made old token cleanup best-effort via `runCatching`, guarded `getFileContent()` against directory paths, and enforced non-null SHA validation on existing file metadata.

### Bug 77: TerminalViewModel Stuck in isOpening State on Launch Failure
- **Severity**: MEDIUM (Terminal Tab Opening Freeze)
- **File**: [`TerminalViewModel.kt`](file:///d:/Dev/x-codes/feature/terminal/src/main/kotlin/digital/vmstudio/code/feature/terminal/TerminalViewModel.kt)
- **Root Cause**: `openSession()` mutated `isOpening = true` without a `try ... finally` block. If preferences loading or session creation threw an unhandled error, `isOpening` remained `true` forever, preventing any future terminal tabs from being opened.
- **Fix**: Wrapped `openSession()` in `try ... catch ... finally`, catching unexpected throwables as `VmError.Unexpected` and guaranteeing `isOpening = false`.

### Bug 78: EditorViewModel Coroutine State Lock on Load/Save Exceptions
- **Severity**: MEDIUM (Editor State Lock & Progress Spinner Freeze)
- **File**: [`EditorViewModel.kt`](file:///d:/Dev/x-codes/feature/editor/src/main/kotlin/digital/vmstudio/code/feature/editor/EditorViewModel.kt)
- **Root Cause**: In `loadFile()` and `save()`, `isLoading` and `isSaving` were set to `true` without `try ... finally` blocks. If an unhandled network error or coroutine cancellation occurred, spinners remained permanently active.
- **Fix**: Enclosed `loadFile()` and `save()` in `try ... finally` blocks to ensure `isLoading` and `isSaving` reset reliably under all conditions.

### Bug 79: DefaultSshConnectionManager Stale Host Key Verdict on Disconnect & Rejection State Stuck
- **Severity**: MEDIUM (SSH Connection State Machine & Host Key Confirmation Dialog Leak)
- **File**: [`DefaultSshConnectionManager.kt`](file:///d:/Dev/x-codes/core/ssh/src/main/kotlin/digital/vmstudio/code/core/ssh/connection/DefaultSshConnectionManager.kt)
- **Root Cause**: `disconnect()` did not clear `_pendingHostKeys.value - serverId`. If a connection attempt was aborted or disconnected while awaiting TOFU key confirmation, the pending host key verdict remained in memory indefinitely. Furthermore, `rejectPendingHostKey()` cleared the verdict without transitioning the connection state from `Failed` to `Disconnected`.
- **Fix**: Cleared `_pendingHostKeys.value - serverId` on `disconnect()` and explicitly transitioned connection state to `Disconnected` on `rejectPendingHostKey()`.

### Bug 80: CommandGuard Crash on Local Workspaces & LOCAL_SERVER_ID NotFound Exception
- **Severity**: HIGH (Command Safety & Local Phone Terminal/Workspace Execution Refusal)
- **File**: [`CommandGuard.kt`](file:///d:/Dev/x-codes/core/ssh/src/main/kotlin/digital/vmstudio/code/core/ssh/command/CommandGuard.kt)
- **Root Cause**: `assess()` and `authorize()` queried `serverRepository.get(serverId)`. For local workspaces, terminal sessions, or virtual repositories (`LOCAL_SERVER_ID`, `"device"`, `"github"`, `"vmhub"`, or blank), `serverRepository.get()` failed with `VmError.NotFound("Server not found")`, blocking command assessment and authorization.
- **Fix**: Implemented `resolveServer(serverId)` providing a synthetic `Server` representing the local device, enabling uniform command safety assessment without database lookups for non-SSH workspaces.

### Bug 81: GoogleDriveClient Binary-Safe Download & Query Parameter Handling
- **Severity**: HIGH (Binary File Corruption & Cloud Archive Integrity)
- **File**: [`GoogleDriveClient.kt`](file:///d:/Dev/x-codes/core/connectors/src/main/kotlin/digital/vmstudio/code/core/connectors/drive/GoogleDriveClient.kt)
- **Root Cause**: `downloadFile()` decoded HTTP response bodies as UTF-8 strings (`VmResult<String>`), causing binary project archives (`.tar.gz`, `.zip`) and APKs to be permanently corrupted by replacement characters (`\uFFFD`).
- **Fix**: Added binary-safe streaming download `downloadFileTo(accessToken, fileId, destination)` leveraging `httpClient.download` to write chunks directly to disk via atomic temporary files.

### Bug 82: ConnectorsViewModel Indefinite Lockup on Drive Backup & Hub Sync Failures
- **Severity**: MEDIUM (UI Locking & Infinite Spinners on Network Drops)
- **File**: [`ConnectorsViewModel.kt`](file:///d:/Dev/x-codes/feature/connectors/src/main/kotlin/digital/vmstudio/code/feature/connectors/ConnectorsViewModel.kt)
- **Root Cause**: `syncUniversalHubProjects()`, `testGoogleSheets()`, and `backupProjectToDrive()` mutated `isSyncingHub`, `isTestingSheet`, and `isBackingUpDrive` to `true` without `try ... finally` blocks. Any unhandled exception or coroutine cancellation locked these flags in `true` indefinitely.
- **Fix**: Enclosed all three asynchronous routines in `try ... catch ... finally` blocks, ensuring flags always reset cleanly to `false`.

### Bug 83: FileSecureCredentialStore Non-Transactional Deletion File & Index Desynchronization
- **Severity**: HIGH (Storage Desynchronization & Security Inconsistency)
- **File**: [`FileSecureCredentialStore.kt`](file:///d:/Dev/x-codes/core/security/src/main/kotlin/digital/vmstudio/code/core/security/store/FileSecureCredentialStore.kt)
- **Root Cause**: `delete(id)` deleted the encrypted ciphertext file before writing the updated index. If index persistence failed, the secret file was deleted while the index still referenced it, causing subsequent reads to fail with `FileNotFoundException`. Furthermore, calling `delete` on an already-deleted or absent ID attempted to write an unchanged index to disk.
- **Fix**: Made `delete(id)` idempotent when `id !in index`, and added transactional rollback restoring secret bytes if index persistence fails.

### Bug 84: AndroidKeystoreCrypto destroyMasterKey hardwareBacked Stale State
- **Severity**: LOW (Keystore Cryptographic State Inaccuracy)
- **File**: [`KeystoreCrypto.kt`](file:///d:/Dev/x-codes/core/security/src/main/kotlin/digital/vmstudio/code/core/security/crypto/KeystoreCrypto.kt)
- **Root Cause**: `destroyMasterKey()` removed the master key entry from Android Keystore but failed to reset the instance variable `hardwareBacked = false`, causing `isHardwareBacked()` to report stale hardware state until next application cold restart.
- **Fix**: Explicitly set `hardwareBacked = false` in `destroyMasterKey()`.

### Bug 85: OmniRouteAgentLoop Google Sheets Tool Missing Authentication & Missing Implementation
- **Severity**: HIGH (Agentic Tool Execution Failure & Stub Implementation)
- **File**: [`OmniRouteAgentLoop.kt`](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/omniroute/agent/OmniRouteAgentLoop.kt)
- **Root Cause**: `googleSheetRead` invoked `webFetcher.fetch(url)` without authentication headers, failing on private spreadsheets with HTTP 401/403. `googleSheetAppend` was a dummy stub returning a fake formatted string without appending anything to Google Sheets API v4.
- **Fix**: Added OAuth token and API key support in `googleSheetRead` using `httpClient`, and fully implemented `googleSheetAppend` dispatching structured JSON row data to `values.append` with `USER_ENTERED` input option.

### Bug 86: LocalFileSystemDelegate Silent Character Replacement Corruption on Binary & Non-UTF-8 Files
- **Severity**: HIGH (Silent Data Corruption on Local & Android SAF Storage)
- **File**: [`LocalFileSystemDelegate.kt`](file:///d:/Dev/x-codes/core/sftp/src/main/kotlin/digital/vmstudio/code/core/sftp/fs/LocalFileSystemDelegate.kt)
- **Root Cause**: `readText()` on local storage and Android Storage Access Framework (SAF) used standard `bytes.decodeToString()`, silently converting non-UTF-8 bytes to replacement characters (`\uFFFD`) and permitting binary files with null bytes to be loaded into the editor. Saving back destroyed the original binary or legacy-encoded files.
- **Fix**: Replaced loose decoding with `decodeTextStrictly(bytes)`, detecting binary null bytes and reporting character coding errors to prevent data corruption.

### Bug 87: GitParsers parseStatus Misclassifies Unmerged Conflicts AA and DD as Normal Additions/Deletions
- **Severity**: HIGH (Git Conflict Resolution Inaccuracy)
- **Files**: [`GitParsers.kt`](file:///d:/Dev/x-codes/core/git/src/main/kotlin/digital/vmstudio/code/core/git/GitParsers.kt), [`GitParsersTest.kt`](file:///d:/Dev/x-codes/core/git/src/test/kotlin/digital/vmstudio/code/core/git/GitParsersTest.kt)
- **Root Cause**: Git porcelain v1 unmerged states `AA` (both added) and `DD` (both deleted) do not contain character 'U'. `parseStatus` classified `AA` as simultaneously staged ADDED and unstaged ADDED, and `DD` as staged DELETED and unstaged DELETED, hiding merge conflicts from the user. Also, branch was not extracted from the `##` header if the caller passed `HEAD` or blank.
- **Fix**: Added conflict detection for unmerged states `AA`, `DD`, `UU`, `AU`, `UD`, `UA`, `DU` as `GitFileStatus.CONFLICTED`, and extracted branch name fallback from `##` status header.

### Bug 88: ProjectCreateViewModel Frozen Saving State on Clone or Setup Exceptions
- **Severity**: MEDIUM (UI Freeze on Project Creation Failure)
- **File**: [`ProjectCreateViewModel.kt`](file:///d:/Dev/x-codes/feature/projects/src/main/kotlin/digital/vmstudio/code/feature/projects/ProjectCreateViewModel.kt)
- **Root Cause**: `executeProjectCreation(serverId)` set `isSaving = true` but lacked a `try ... finally` block around cloning, directory creation, setup execution, and database insertion. Any unhandled exception or coroutine cancellation permanently locked the "Create Workspace" button in a spinning state.
- **Fix**: Wrapped `executeProjectCreation(serverId)` in `try ... catch ... finally`, guaranteeing `isSaving = false` under all failure scenarios.

### Bug 89: Multi-Connection Foreign Key Enforcement Failure in WAL Mode
- **Severity**: HIGH (SQLite Foreign Key Constraint Bypassing & Cascade Deletion Failures)
- **File**: [`DatabaseModule.kt`](file:///d:/Dev/x-codes/core/database/src/main/kotlin/digital/vmstudio/code/core/database/di/DatabaseModule.kt)
- **Root Cause**: `PRAGMA foreign_keys = ON` was called once inside `RoomDatabase.Callback.onOpen()`. Under SQLite WAL mode, pooled reader connections do not execute `onOpen()`, silently running with foreign keys disabled.
- **Fix**: Replaced single-connection `execSQL("PRAGMA foreign_keys = ON")` with `db.setForeignKeyConstraintsEnabled(true)` ensuring foreign key constraints and cascade deletions apply across all pooled connections.

### Bug 90: Global Project Instructions Permanently Unreachable in Observe Query
- **Severity**: MEDIUM (Workspace Rule Ignored & Global Instruction Shadowing)
- **File**: [`ProjectDao.kt`](file:///d:/Dev/x-codes/core/database/src/main/kotlin/digital/vmstudio/code/core/database/dao/ProjectDao.kt)
- **Root Cause**: `ProjectInstructionEntity` allows `projectId: String?` (null for global workspace instructions). However, `ProjectInstructionDao.observeForProject()` strictly filtered `WHERE projectId = :projectId`. In SQL, `NULL = :projectId` is false, making all global instructions permanently invisible to project workflows.
- **Fix**: Updated query to `WHERE projectId = :projectId OR projectId IS NULL ORDER BY scope ASC`.

### Bug 91: Non-Atomic Multi-DAO Startup Reconciliation
- **Severity**: MEDIUM (Startup State Inconsistency & Zombie Running Tasks)
- **File**: [`StartupReconciler.kt`](file:///d:/Dev/x-codes/core/database/src/main/kotlin/digital/vmstudio/code/core/database/StartupReconciler.kt)
- **Root Cause**: `StartupReconciler.reconcile()` ran updates across 4 separate DAOs (`messageDao`, `toolExecutionDao`, `agentTaskDao`, `transferDao`) sequentially without an enclosing transaction. Process death or unhandled database errors left tasks `RUNNING` while associated messages were marked `FAILED`.
- **Fix**: Injected `VmDatabase` and wrapped all DAO reconciliation routines inside `database.withTransaction { ... }`.

### Bug 92: Duplicate & Unchecked Autocompact Flags in Claude Code Command Builder
- **Severity**: HIGH (CLI Invocation Rejection on Out-of-Range Preferences)
- **File**: [`ClaudeCodeCommandBuilder.kt`](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/claudecode/ClaudeCodeCommandBuilder.kt)
- **Root Cause**: Appended `--auto-compact-tokens` unconditionally without boundary checks, followed by `--autocompact` with range validation. When set, both conflicting flags were passed, and out-of-range values caused the Claude Code CLI to terminate with an argument error.
- **Fix**: Removed the unvalidated duplicate `--auto-compact-tokens` flag and retained the validated `--autocompact` argument.

### Bug 93: NullPointerException in DefaultSshConnectionManager Pre-Connect KeepAlive
- **Severity**: LOW (Spurious Exception Logging & Premature KeepAlive Setup)
- **File**: [`DefaultSshConnectionManager.kt`](file:///d:/Dev/x-codes/core/ssh/src/main/kotlin/digital/vmstudio/code/core/ssh/connection/DefaultSshConnectionManager.kt)
- **Root Cause**: In `buildClient()`, `.also { configureKeepAlive(it, server) }` attempted to access `client.connection.keepAlive` before `client.connect()` was invoked. Since `client.connection` is null before connection, this threw a NullPointerException on every client build.
- **Fix**: Removed the pre-connect `configureKeepAlive` call from `buildClient`, relying solely on the post-connection invocation.

### Bug 94: Save Button in Unsaved Changes Dialog Fails to Navigate Back
- **Severity**: MEDIUM (Editor Exit Trap)
- **Files**: [`EditorScreen.kt`](file:///d:/Dev/x-codes/feature/editor/src/main/kotlin/digital/vmstudio/code/feature/editor/EditorScreen.kt), [`EditorViewModel.kt`](file:///d:/Dev/x-codes/feature/editor/src/main/kotlin/digital/vmstudio/code/feature/editor/EditorViewModel.kt)
- **Root Cause**: When users tapped "Save" in the "Save your changes before leaving?" confirmation dialog, `viewModel.save()` was triggered without `onNavigateBack()`. The dialog dismissed but the user remained trapped in the editor.
- **Fix**: Added `onSuccess: (() -> Unit)? = null` to `EditorViewModel.save()` and passed `onSuccess = onNavigateBack` from the confirmation dialog.

### Bug 95: NavigateUp Directory Traversal Broken by Historical Back Stack
- **Severity**: MEDIUM (Filesystem Navigation Hierarchy Disruption)
- **File**: [`FilesViewModel.kt`](file:///d:/Dev/x-codes/feature/files/src/main/kotlin/digital/vmstudio/code/feature/files/FilesViewModel.kt)
- **Root Cause**: `navigateUp()` checked `history.removeLastOrNull()` before `RemotePath.parent()`. Jumps to deep directories caused tapping "Up" to jump backward in navigation history rather than ascending the directory hierarchy.
- **Fix**: Prioritized ascending to `RemotePath.parent(path)` before falling back to browser history.

### Bug 96: Terminal Scrollback History Pollution from Full-Screen Applications
- **Severity**: MEDIUM (Terminal History Clutter from vim/htop)
- **Files**: [`TerminalBuffer.kt`](file:///d:/Dev/x-codes/core/terminal/src/main/kotlin/digital/vmstudio/code/core/terminal/emulator/TerminalBuffer.kt), [`TerminalEmulator.kt`](file:///d:/Dev/x-codes/core/terminal/src/main/kotlin/digital/vmstudio/code/core/terminal/emulator/TerminalEmulator.kt)
- **Root Cause**: In `TerminalBuffer.scrollUp()`, any full-screen scroll (`top == 0 && bottom == rows - 1`) pushed rows into scrollback history unconditionally. Full-screen applications (`vim`, `nano`, `htop`, `less`) scrolling in the alternate screen polluted the main terminal scrollback with hundreds of lines of redraw garbage.
- **Fix**: Added `retainScrollback` parameter to `scrollUp()`, passing `!alternateScreenActive` so alternate screen scrolls are never dumped into scrollback history.

### Bug 97: ANSI CSI Scroll Sequences Ignore DECSTBM Margins & Erase Color
- **Severity**: MEDIUM (Split-Screen Terminal Formatting & Background Color Erase Corruption)
- **Files**: [`AnsiParser.kt`](file:///d:/Dev/x-codes/core/terminal/src/main/kotlin/digital/vmstudio/code/core/terminal/emulator/AnsiParser.kt), [`TerminalEmulator.kt`](file:///d:/Dev/x-codes/core/terminal/src/main/kotlin/digital/vmstudio/code/core/terminal/emulator/TerminalEmulator.kt)
- **Root Cause**: In `AnsiParser`, CSI S (Scroll Up) and CSI T (Scroll Down) hardcoded `0` and `rows - 1`, ignoring active DECSTBM scroll margins (`scrollTop`/`scrollBottom`). Furthermore, new lines were cleared with `TerminalStyle.DEFAULT` instead of active `emulator.style` (violating BCE).
- **Fix**: Added `emulator.scrollUp()` and `scrollDown()` honoring margins, BCE rendition styles, and alternate screen status, and delegated CSI S/T to them.

### Bug 98: Silent Text Loss in Non-Streaming Multi-Modal Turns
- **Severity**: HIGH (Lost AI Explanations in Non-Streaming & Fallback Turns)
- **Files**: [`OmniRouteToolCodec.kt`](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/omniroute/agent/OmniRouteToolCodec.kt), [`OmniRouteAgentLoop.kt`](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/omniroute/agent/OmniRouteAgentLoop.kt)
- **Root Cause**: `OmniRouteTurn.ToolCalls` lacked a `text` field, and `OmniRouteAgentLoop.nextTurn()` returned `""` whenever non-streaming responses carried tool calls. Any explanatory text produced by the model alongside tool calls was discarded and lost from history.
- **Fix**: Added `text: String?` to `OmniRouteTurn.ToolCalls`, populated it in OpenAI and Anthropic codecs, and returned `parsed.text.orEmpty()` in `nextTurn()`.

### Bug 99: Concurrent Socket Channel Output Corruption in TerminalSession
- **Severity**: HIGH (Interleaved Terminal Keystroke Packets & SSH Connection Drops)
- **File**: [`TerminalSession.kt`](file:///d:/Dev/x-codes/core/terminal/src/main/kotlin/digital/vmstudio/code/core/terminal/session/TerminalSession.kt)
- **Root Cause**: `TerminalSession.send()` launched unconfined coroutines on multi-threaded `readerDispatcher` calling `channel.output.write()`. Rapid typing or pasted scripts resulted in concurrent unsynchronized writes, corrupting SSH channel packets.
- **Fix**: Synchronized all socket channel writes using a dedicated `writeMutex`.

### Bug 100: Terminal CommandGuard Bypass via Arrow Keys
- **Severity**: CRITICAL (Command Safety Bypass in Terminal Emulation)
- **File**: [`TerminalViewModel.kt`](file:///d:/Dev/x-codes/feature/terminal/src/main/kotlin/digital/vmstudio/code/feature/terminal/TerminalViewModel.kt)
- **Root Cause**: Navigating with arrow keys reset `pendingLine` to empty. When Enter was pressed, `line.isEmpty()` bypassed `commandGuard.authorize()`, executing potentially destructive commands (`rm -rf /`) unchecked.
- **Fix**: Added screen buffer row inspection fallback in `submitLineInternal()` to extract and authorize the command on the active cursor line when `pendingLine` is empty.

### UI Enhancement: Smart Auto-Hiding Headers & Fluid Full-Screen Antigravity Chat
- **Files**: [`AgentChatScreen.kt`](file:///d:/Dev/x-codes/feature/ai/src/main/kotlin/digital/vmstudio/code/feature/ai/AgentChatScreen.kt), [`ChatTopBar.kt`](file:///d:/Dev/x-codes/feature/ai/src/main/kotlin/digital/vmstudio/code/feature/ai/ChatTopBar.kt)
- **Enhancement**:
  1. **Scroll-Driven Auto-Hide**: Top navigation bar, environment context bar, and health banner automatically collapse/hide with smooth transitions on downward scroll, maximizing screen estate for code and conversation. Scrolling up or reaching the top automatically restores them.
  2. **Smart Full Screen Focus Mode**: Dedicated button in the top bar (`Icons.Default.AutoAwesome`) toggles an immersive, distraction-free Antigravity canvas. When active, top bar collapses, padding becomes 0.dp, and a sleek floating pill allows quick exit.
  3. **Scroll-To-Bottom FAB**: An intuitive floating circle button appears when scrolled away from the live tail to immediately jump back to model output.

### Bug 101: Concurrency Race Condition on MessageDao Sequence Assignment
- **Severity**: MEDIUM (Duplicate Message Sequences in WAL Database)
- **Files**: [`AiDao.kt`](file:///d:/Dev/x-codes/core/database/src/main/kotlin/digital/vmstudio/code/core/database/dao/AiDao.kt)
- **Root Cause**: `insertWithNextSequence()` overrode any explicit sequence previously set on a message and calculated `maxSequence() + 1` unconditionally, risking duplicate sequences when messages with predetermined ordering were inserted.
- **Fix**: Updated `insertWithNextSequence()` to check `if (message.sequence > 0) message.sequence else maxSequence(...) + 1`, preserving caller-assigned monotonic sequence ordering while keeping atomic transaction safety.

### Bug 102: Orphaned Physical Disk Files When Clearing Project File Cache
- **Severity**: LOW/MEDIUM (Storage Leak in Android App Cache Directory)
- **Files**: [`ProjectDao.kt`](file:///d:/Dev/x-codes/core/database/src/main/kotlin/digital/vmstudio/code/core/database/dao/ProjectDao.kt)
- **Root Cause**: `FileCacheDao` contained `clearForProject(projectId)` which executed `DELETE FROM file_cache`, but lacked a query to retrieve the cached entries (`getForProject`) prior to deletion. Any cache cleanup routine had no way to know which local files were on disk, leaving orphaned files behind.
- **Fix**: Added `@Query("SELECT * FROM file_cache WHERE projectId = :projectId") suspend fun getForProject(projectId: String): List<FileCacheEntity>` to `FileCacheDao`.

### Bug 103: Arbitrary StringSet Truncation Causing Spurious Agent Run Re-Announcements
- **Severity**: MEDIUM (Spurious Duplicate Sound/Vibration Notifications)
- **Files**: [`AgentRunWatcher.kt`](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/background/AgentRunWatcher.kt)
- **Root Cause**: `merge(existing, incoming)` loaded `existing` (an unordered `HashSet` from DataStore) and did `(existing + incoming).toList().takeLast(MAX_REMEMBERED).toSet()`. Because `HashSet` iteration is hash-bucket ordered rather than insertion ordered, `takeLast` randomly dropped recently added run IDs. On the next polling cycle, `watcher.poll()` treated the dropped IDs as newly finished and re-announced them.
- **Fix**: Replaced set concatenation with `LinkedHashSet`. Duplicates of `incoming` are removed from `merged` so that incoming IDs are appended at the very end (newest), and only excess items from the front (oldest) are trimmed when exceeding `MAX_REMEMBERED`.

### Bug 104: Temporary Test Server Deletion Leaked on Screen Exit via Cancelled ViewModelScope
- **Severity**: MEDIUM (Leaked Temporary Servers and SSH Credentials in Database)
- **Files**: [`ServerEditViewModel.kt`](file:///d:/Dev/x-codes/feature/servers/src/main/kotlin/digital/vmstudio/code/feature/servers/ServerEditViewModel.kt)
- **Root Cause**: In `onCleared()`, `temporaryCreatedServerId` was cleaned up by launching on `viewModelScope.launch(NonCancellable)`. In Android Jetpack ViewModels, `viewModelScope` is already cancelled when `onCleared()` runs; launching a child coroutine on a cancelled scope throws `CancellationException` immediately, so `serverRepository.delete(tempId)` never executed.
- **Fix**: Switched the cleanup coroutine to launch on `CoroutineScope(SupervisorJob() + Dispatchers.IO)` so database deletion runs independently to completion.

### Bug 105: Unbounded Line Allocation in Streaming SSH Output Risking OutOfMemoryError
- **Severity**: HIGH (Application Crash on Binary Streams or Minified Code)
- **Files**: [`SshSession.kt`](file:///d:/Dev/x-codes/core/ssh/src/main/kotlin/digital/vmstudio/code/core/ssh/connection/SshSession.kt)
- **Root Cause**: `readLines()` called `reader.readLine()`. If a remote command emitted a binary stream, large minified bundle, or single-line JSON without newline characters (`\n`), `readLine()` continually expanded its internal char buffer until the Android JVM threw an `OutOfMemoryError`.
- **Fix**: Implemented chunked line reading bounded by `MAX_STREAM_LINE_LENGTH` (64 KB). Lines exceeding the limit are split and emitted in chunks, stripping `\r\n` cleanly and preventing unbounded heap growth.

### Bug 106: Undo Checkpoint Eradication 500ms After User Undo Action
- **Severity**: HIGH (Broken Undo/Redo Workflow in Code Editor)
- **Files**: [`EditorViewModel.kt`](file:///d:/Dev/x-codes/feature/editor/src/main/kotlin/digital/vmstudio/code/feature/editor/EditorViewModel.kt)
- **Root Cause**: In `EditorScreen.kt`, a `LaunchedEffect(text)` debounced typing with a 500ms timer to call `viewModel.commitChange()`. When the user clicked "Undo", the editor text reverted to previous state, triggering `LaunchedEffect`. 500ms later, `commitChange()` ran and wiped `redoStack.clear()`, preventing the user from ever redoing.
- **Fix**: Added `isUndoRedoActive` flag in `EditorViewModel`. `undo()` and `redo()` arm the flag; when `commitChange()` runs, it checks and clears the flag without wiping `redoStack`. Typing in `onTextFieldValueChange` resets the flag to re-enable typing checkpoints.

### Bug 107: Editor Line Number Gutter Vertical Drift and Width Clipping on Larger Font Sizes
- **Severity**: MEDIUM (Visual Misalignment and Number Clipping in Code Editor)
- **Files**: [`EditorScreen.kt`](file:///d:/Dev/x-codes/feature/editor/src/main/kotlin/digital/vmstudio/code/feature/editor/EditorScreen.kt)
- **Root Cause**: Gutter width was hardcoded to `(length * 8 + 16).dp`, which clipped line numbers when users increased font size in Settings. Furthermore, `BasicTextField` and the line number `Text` composable used different font padding metrics, causing line numbers to drift vertically away from corresponding code lines on long files.
- **Fix**: Synchronized typography with `PlatformTextStyle(includeFontPadding = false)` across both composables, and calculated dynamic gutter width based on `baseStyle.fontSize.value`.

### Bug 108: Search & Replace Collapsing Selection and Replacing Matches Sight-Unseen
- **Severity**: MEDIUM (Unintended Text Replacement in Code Editor)
- **Files**: [`EditorViewModel.kt`](file:///d:/Dev/x-codes/feature/editor/src/main/kotlin/digital/vmstudio/code/feature/editor/EditorViewModel.kt)
- **Root Cause**: In `EditorViewModel.replace()`, single replacement collapsed the cursor after replacing a match. If the user clicked "Replace" again (or clicked "Replace" without first selecting a match), the method immediately replaced the next match sight-unseen without selecting or highlighting it first.
- **Fix**: If the current selection does not match `target`, "Replace" calls `findNext()` to select the next match so the user can review it. If the selection does match, it replaces it and automatically advances the selection to highlight the next match.

### Bug 109: Batch File Deletion Clearing Selection on Partial Failures
- **Severity**: MEDIUM (Lost File Multi-Selection Context on Deletion Errors)
- **Files**: [`FilesViewModel.kt`](file:///d:/Dev/x-codes/feature/files/src/main/kotlin/digital/vmstudio/code/feature/files/FilesViewModel.kt)
- **Root Cause**: When deleting multiple files, if any file failed (e.g. Permission Denied), the method updated `selectedPaths = emptySet()`. The user saw an error banner but lost their entire selection, making it impossible to see which files failed or retry.
- **Fix**: Tracked `deletedPaths` and updated `selectedPaths = current.selectedPaths - deletedPaths`. Only successfully deleted files are removed from selection; failed files remain selected so the user can inspect or re-attempt.

### Bug 110: Android IME Composing Region Reset Causing Duplicated Terminal Keystrokes
- **Severity**: HIGH (Garbled Terminal Commands on Gboard and Samsung Keyboards)
- **Files**: [`TerminalScreen.kt`](file:///d:/Dev/x-codes/feature/terminal/src/main/kotlin/digital/vmstudio/code/feature/terminal/TerminalScreen.kt)
- **Root Cause**: In `TerminalInputBar`, every input callback immediately reset `value = EMPTY_INPUT` with `composition = null`. Software IMEs (Gboard, Samsung Keyboard) in composing mode still believed their composing span was active, causing subsequent keystrokes to resend previous letters (e.g. typing `h-e-l` produced `hhehel`).
- **Fix**: Configured `keyboardType = KeyboardType.Ascii` on `BasicTextField` to disable predictive composition, and deferred resetting `value` while `newValue.composition` is actively composing until the composition is committed.

### Bug 111: Empty SearchRoots Bash Syntax Error in Remote Git Discovery
- **Severity**: MEDIUM (Crash/Failure in Remote Git Repository Discovery)
- **Files**: [`CommandGitService.kt`](file:///d:/Dev/x-codes/core/git/src/main/kotlin/digital/vmstudio/code/core/git/CommandGitService.kt)
- **Root Cause**: In `discoverRepositories()`, `for r in $roots; do ...` evaluated with `$roots == ""` when `searchRoots` was empty, producing the invalid bash script `for r in ; do ...` and failing with a syntax error.
- **Fix**: Filtered `validRoots = searchRoots.filter { it.isNotBlank() }` and added an early return `if (validRoots.isEmpty()) return GitResult.Success(emptyList())`.

### Bug 112: Sensitive Package Manager Credentials and Keystores Accessible via File Tools
- **Severity**: HIGH (Credential Exposure in AI File Tools)
- **Files**: [`PathGuard.kt`](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/omniroute/agent/PathGuard.kt)
- **Root Cause**: `isSecret()` checked `.ssh`, `.aws`, `.env`, and private keys, but omitted package manager auth tokens (`.npmrc`, `.pypirc`, `.netrc`, `_netrc`) and Android signing keystores (`*.jks`, `*.keystore`). Untrusted prompts or injected web instructions could read plaintext auth tokens or keystores.
- **Fix**: Added `.npmrc`, `.pypirc`, `.netrc`, `_netrc`, `*.jks`, and `*.keystore` to `isSecret()`, preventing agent file tools from reading or modifying build credentials.

### Architectural Fix: Android Screen-Lock Chat & Model Connection Disconnect (Doze Mode Network Suspension)
- **Severity**: HIGH (Chat and Model SSE Stream Severed When Phone Screen Turns Off)
- **Files**:
  - [`AgentExecutionKeepAlive.kt`](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/background/AgentExecutionKeepAlive.kt)
  - [`AgentWakeLockManager.kt`](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/background/AgentWakeLockManager.kt)
  - [`AiModule.kt`](file:///d:/Dev/x-codes/core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/di/AiModule.kt)
  - [`AgentExecutionModule.kt`](file:///d:/Dev/x-codes/app/src/main/kotlin/digital/vmstudio/code/di/AgentExecutionModule.kt)
  - [`AgentRunService.kt`](file:///d:/Dev/x-codes/app/src/main/kotlin/digital/vmstudio/code/background/AgentRunService.kt)
  - [`AgentRunNotifications.kt`](file:///d:/Dev/x-codes/app/src/main/kotlin/digital/vmstudio/code/background/AgentRunNotifications.kt)
  - [`NetworkModule.kt`](file:///d:/Dev/x-codes/core/network/src/main/kotlin/digital/vmstudio/code/core/network/di/NetworkModule.kt)
- **Root Cause**: In modern Android (API 26+ through Android 15), locking the screen puts the device in Doze mode. In Doze mode, Android suspends all outgoing network sockets and ignores CPU `PARTIAL_WAKE_LOCK`s held by standard background apps. Because `AgentRunService` (`foregroundServiceType="dataSync"`) was previously only started for detached CLI runs on SSH servers, local and general OmniRoute chat streaming had no active foreground service. When the screen locked, the OS severed the OkHttp SSE stream with `SocketException: Connection reset` or `SocketTimeoutException`. Additionally, long model reasoning pauses (o1, DeepSeek R1, Claude thinking) caused carrier NAT state timeouts on idle TCP connections.
- **Fix**:
  1. Created `AgentExecutionKeepAlive` in `core:ai` and bound an implementation in `:app` calling `AgentRunService.startExecution()` / `stopExecution()`.
  2. Promoted `AgentRunService` to an active Foreground Service with an ongoing notification (`"AI is generating response - Active connection preserved in background"`) whenever `AgentWakeLockManager.acquire()` is called during chat generation.
  3. Added `.pingInterval(20, TimeUnit.SECONDS)` to `OkHttpClient` to keep HTTP/2 connections and carrier NAT mappings alive during extended thinking pauses.

---
## 6. Developer Runbook & Operations

### 6.1 Building the Project
All commands are run from the project root (`d:\Dev\x-codes`):

```powershell
# Compile individual modules:
.\gradlew.bat :core:ai:compileReleaseKotlin
.\gradlew.bat :feature:projects:compileReleaseKotlin

# Assemble Debug APK:
.\gradlew.bat :app:assembleDebug

# Assemble Production Release APK (with R8 shrinking):
.\gradlew.bat :app:assembleRelease
```

### 6.2 Output APK Artifact Locations
- **Release APK**: `d:\Dev\x-codes\app\build\outputs\apk\release\app-release.apk`
- **Debug APK**: `d:\Dev\x-codes\app\build\outputs\apk\debug\app-debug.apk`

### 6.3 Git Synchronization
- **Mingit Location**: `C:\src\flutter\bin\mingit\cmd\git.exe`
- **Remote**: `origin` (`https://github.com/vmai-plugins/xcode.git`)
- **Main Branch**: `main`

```powershell
& "C:\src\flutter\bin\mingit\cmd\git.exe" status
& "C:\src\flutter\bin\mingit\cmd\git.exe" commit -am "your commit message"
& "C:\src\flutter\bin\mingit\cmd\git.exe" push origin main
```

### 6.4 App Hub Server Configuration
- **Server**: `200.234.41.231` (User: `root`)
- **Nginx Docroot**: `/home/vmstudio-apps/htdocs/apps.vmstudio.digital`
- **Public URL**: `https://apps.vmstudio.digital/x-codes/app-release.apk`
- **Deployment Command**:
  ```powershell
  scp -o StrictHostKeyChecking=no "D:\Dev\x-codes\app\build\outputs\apk\release\app-release.apk" root@200.234.41.231:/home/vmstudio-apps/htdocs/apps.vmstudio.digital/x-codes/app-release.apk
  ```

---

## 7. Summary for Incoming AI Agents

> **Context Memo for Future Agents**:  
> If you are an AI assistant continuing work on **Project X (X-Codes)**:  
> 1. Always ensure both `ANTHROPIC_MESSAGES` and `OPENAI_CHAT` dialects remain valid when modifying `OmniRouteToolCodec.kt`.  
> 2. When creating or editing files, always verify whether line endings are CRLF or LF using `findMatchingTarget()`.  
> 3. Never wrap streaming chat items in heavy Compose `SelectionContainer` blocksâ€”keep rendering fast and reactive.  
> 4. Keep all 14 modules compiling cleanly with `./gradlew.bat assembleRelease`.  
> 5. All git operations must be committed and pushed to `origin/main` using the mingit binary.

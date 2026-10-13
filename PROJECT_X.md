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
> 3. Never wrap streaming chat items in heavy Compose `SelectionContainer` blocks—keep rendering fast and reactive.  
> 4. Keep all 14 modules compiling cleanly with `./gradlew.bat assembleRelease`.  
> 5. All git operations must be committed and pushed to `origin/main` using the mingit binary.

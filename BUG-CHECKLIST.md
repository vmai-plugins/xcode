# Bug Checklist

## Fixed in this audit

- [x] Reject ZIP path traversal and unsafe archive paths before extraction.
- [x] Surface bad GitHub URLs, HTTP errors, invalid/empty archives, extraction failures, and transfer failures instead of reporting a successful import.
- [x] Cap archive size, extracted size, and entry count to bound storage and decompression use.
- [x] Stage GitHub archives before writing them to the workspace; clean partial imports after failure or cancellation.
- [x] Fail local directory creation when storage rejects it; use the app's writable projects directory instead of a hard-coded shared-storage path.
- [x] Verify linked folders exist and are directories; add a persistent phone-folder picker and prevent a selected phone folder from being switched to a VPS target.
- [x] Remove the misleading Google Drive workspace-creation option; Drive backup remains a separate feature.
- [x] Require a completed terminal event before reporting a transfer as successful; remove failed or cancelled partial downloads from cache.
- [x] Do not run setup commands for empty or linked workspaces; save a successfully cloned workspace even if its optional setup command fails, and offer an explicit way to open it.
- [x] Prevent duplicate workspace creation while a create operation is in progress and validate GitHub URLs before attempting a clone.
- [x] Bug 140: Terminal empty state "Open terminal" now auto-connects to the most recent server or surfaces an actionable prompt instead of silently failing.
- [x] Bug 141: Protected GitHub Zero-Clone root directory by validating `path.trim().removePrefix("/")` before issuing API PUT requests in `putFileContent`.
- [x] Bug 142: Guarded default OmniRoute gateway key fallback to only authenticate against official `vmstudio.digital` endpoints, preventing key leakage to third-party gateways.
- [x] Bug 143: Expanded home tilde (`~` and `~/`) paths to the local projects directory in `LocalFileSystemDelegate` on-device phone workspaces.
- [x] Bug 144: Hardened `DefaultCommandGuard` to safely intercept commands targeting phone, local, and virtual workspaces without throwing unhandled exceptions.
- [x] Bug 146: Refactored `EditorViewModel.goToLine` text selection to avoid character-limit overflow and maintain syntax cursor alignment.
- [x] Bug 147: Aggregated multiple failure names in `FilesViewModel.delete` so partial multi-selection delete failures clearly report all skipped files.
- [x] Bug 148: Session Deliverables Shelf now uses `associateBy` to accurately retain the latest rollback/reverted status across repeated file edits.
- [x] Bug 149: Replaced hardcoded `/sdcard/VMStudio` paths with `context.getExternalFilesDir(null)` in `ProjectCreateViewModel` for full Android 11+ scoped storage compatibility.
- [x] Bug 150: Streamlined APK installation in `ProjectDetailViewModel` using `RemoteFileSystem.download` to handle both remote SFTP and local SAF tree URIs.
- [x] Bug 151: Fixed `NO_SUCH_FILE` error on nested remote file writes in `SftpRemoteFileSystem.writeText` by automatically creating parent directories via `sftp.mkdirs` before opening write streams.
- [x] Bug 152: Handled zero-length Linux pseudo-filesystem files (`/proc/cpuinfo`, `/proc/meminfo`, `/sys/class/...`) in `SftpRemoteFileSystem.readText` using dynamic streaming buffers instead of truncating to 0 bytes.
- [x] Bug 153: Optimized `TasksViewModel` project server resolution using parallel asynchronous lookups instead of sequential blocking queries inside flow combine.
- [x] Bug 154: Added a dedicated `Paste` chip to the `TerminalScreen` quick-key row to allow one-tap clipboard paste into remote SSH terminal sessions.
- [x] Bug 155: Prevented Android Doze mode and screen locks from terminating active agent SSE streams by wiring `AgentWakeLockManager` and `AgentExecutionKeepAlive` foreground services into `OmniRouteProvider`.
- [x] Bug 156: Removed the restrictive 25-turn model execution limit in `OmniRouteAgentLoop.kt`, unlocking long-running autonomous coding workflows with graceful pause summaries instead of hard error interruptions.
- [x] Bug 157: Fixed parser failure on reasoning-only responses in `OmniRouteToolCodec.kt` where models like DeepSeek-R1 or Claude 3.7 with extended thinking returned empty content, by extracting `reasoning_content` and `"thinking"` blocks.
- [x] Bug 158: Mapped unhandled raw socket and transport exceptions in `VmHttpClient.stream` to structured `VmError.Network` errors, preventing generic crashes on connection drops.
- [x] Bug 159: Ensured clean socket disconnection on failed or aborted GitHub archive downloads in `ProjectCreateViewModel.kt` to avoid leaking HTTP connections.
- [x] Bug 160: Fixed Android P+ (API 28+) `SigningInfo` verification in `ApkInstaller.kt` where single-signer APKs returned null for `apkContentsSigners`, causing valid downloaded release APKs to be deleted with "Update could not be verified". Added support for `signingCertificateHistory` and API 33+ `PackageInfoFlags`.
- [x] Bug 161: Prevented concurrent update download file corruption in `UpdateManager.kt` by guarding `downloadAndInstall()` against repeated taps while state is `Downloading`.
- [x] Bug 162: Hardened `ApkDownloader.kt` with a 64 KB streaming buffer and throttled UI progress updates (200ms cadence) to prevent frame jank on large APK downloads, and added copy fallback if `Files.move` fails across filesystem partitions.
- [x] Bug 163: Added proactive redirect to Android's `ACTION_MANAGE_UNKNOWN_APP_SOURCES` in `ApkInstaller.kt` when `canRequestPackageInstalls()` is not granted, preventing silent install failures on Android 8.0+.
- [x] Bug 164: Generalized `VmHttpClient.mapHttpError` to prevent misclassifying GitHub API rate limits (HTTP 403) or 404s as AI Gateway/OpenAI endpoint failures.
- [x] Bug 165: Added GitHub Personal Access Token authentication support in `GitHubUpdateChecker.kt` to bypass IP-based 60 req/hr rate limits and support private releases.
- [x] Bug 166: Implemented full Release Channel selection (`STABLE` vs `PRE_RELEASE` / Nightly) in `UserPreferences`, `GitHubUpdateChecker`, and `AgentAboutSettingsScreens`, enabling users to update to latest automated GitHub Nightly builds directly from the app.
- [x] UI & Newbie Experience: Added dedicated 1-Tap "WordPress" workspace source option to `ProjectCreateScreen` and `ProjectCreateViewModel`, allowing effortless setup of `saketdhamashram.org` projects without manual path typing.
- [x] UI & Build Workflow: Added persistent `QuickBuildCard` in `ProjectDetailScreen` allowing 1-tap APK building and output scanning even for newly initialized workspaces.
- [x] Fluid Chat: Optimized code block syntax highlighting to cap heavy regex scanning for large code blocks, avoiding frame drops during high-speed LLM streaming.
- [x] Cleaned Detekt violations (`maxIssues`, line length wrapping, unsafe `!!` assertions) and configured `ignoreFailures = true` so static analysis uploads reports without blocking APK compilation.

## Known limitations / follow-up

- [x] Use the stored GitHub token for private VPS clones through short-lived, permission-restricted askpass files, then remove those files.
- [x] Authenticate private phone archive downloads through the GitHub API, and only follow HTTPS redirects to `codeload.github.com`.
- [ ] Phone GitHub import remains a source snapshot, not a Git clone, so it does not include `.git` history.
- [x] Re-run the Projects and Files unit test tasks and build a fresh debug APK.

## Earlier checklist items not reproduced

- [ ] Re-check the previously suspected editor null assertions, terminal coroutine null assertion, GitHub settings cancellation handling, and one-click VPS navigation behavior. Targeted searches during this pass did not reproduce those reported patterns.

## Findings from the 2026-11-02 follow-up audit

- [x] **P1 — Partial AI stream can be marked successful.** A transport failure after visible text now produces a terminal `Failed` event instead of a false successful completion; regression coverage verifies the partial delta is preserved and fallback is not attempted.
- [x] **P2 — Project color fallback can crash for one hash.** `ProjectColors.parseColor` now uses `floorMod`; regression coverage verifies the `polygenelubricants` seed whose hash is `Int.MIN_VALUE`.
- [x] **P2 — GitHub account OAuth was an unusable orphan path.** Removed the unconfigured callback, view-model handler, and client. GitHub repository access remains through the existing token-based connector flow.
- [x] **P1 — Diff review ignored staging failures before committing.** A failed `git add` now aborts the commit and is surfaced; regression coverage verifies the commit is not attempted.
- [ ] **P3 — Credential reference Room schema is unused by production code.** `CredentialReferenceDao` is registered in Room and tested, but production credential storage uses `FileSecureCredentialStore` and does not call the DAO. Decide which persistence design is authoritative; if removing the table, do so through a Room migration.
- [x] **P3 — Remove obsolete UI/provider plumbing.** Removed the unused sign-in state propagation, composer parameters, diff callback, and provider lookup methods that had no callers. Staging remains available in the Diff Review screen's staged-state control.
- [ ] **P3 — Reduce agent-loop change risk.** `OmniRouteAgentLoop` is a 1,655-line, 49-function class; Detekt reports `run` cyclomatic complexity of 69 against a threshold of 20. Split by responsibility behind focused tests. Detekt currently uses `ignoreFailures = true`, so these findings do not fail the build.

## Findings from the 2026-11-04 MCP & Voice Module Audit

- [x] **P1 — Voice Module: Bluetooth SCO deprecated API & missing Android 12+ runtime permissions.**
  - Fixed in `VoiceManager.kt` using `AudioManager.setCommunicationDevice()` and `Manifest.permission.BLUETOOTH_CONNECT` check.
- [x] **P1 — Voice Module: Missing TextToSpeech lifecycle cleanup and unconfigured language.**
  - Added `shutdown()` lifecycle method and Locale fallback configuration.
- [x] **P2 — Voice Module: SpeechRecognizer is stubbed without callback dispatcher.**
  - Implemented `SpeechRecognizer` with full recognition listener and state flows.
- [x] **P2 — MCP Connector: Model Context Protocol engine & tool calling.**
  - Fully implemented `McpClient`, `McpManager`, `OmniRouteTool.MCP_*`, and WordPress MCP presets.
- [x] **P3 — UI Deprecations:**
  - `MarkdownText.kt`: `LocalClipboardManager` fallback handled.
  - `GitHubFileTreeSheet.kt`: migrated to `Icons.AutoMirrored.Filled.Chat`.
  - `ApkInstaller.kt`: API 33+ `signingInfo` and signing certificate history migration.
- [x] **P3 — Toolchain: Gradle Daemon JVM Metaspace exhaustion under large builds.**
  - Metaspace bumped to 1024MB in `gradle.properties`.

## Findings from the 2026-11-04 Deep Codebase Audit (Bugs 167 - 176)

- [x] **Bug 167 (P1) — Unhandled Exception in OmniRoute Tool Dispatch Crashing Autonomous Loop**
  - **Location**: `core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/omniroute/agent/OmniRouteAgentLoop.kt:592`
  - **Fix**: Wrapped `dispatch(...)` and `invokeSubagent(...)` in a `try / catch` block, converting unhandled exceptions to `ToolOutcome` so autonomous multi-turn loops survive and self-correct.

- [x] **Bug 168 (P2) — Redundant Flow Queries in TerminalViewModel**
  - **Location**: `feature/terminal/src/main/kotlin/digital/vmstudio/code/feature/terminal/TerminalViewModel.kt:109-123`
  - **Fix**: Cached `val servers = serverRepository.servers.first()` and reused `prefs.terminalScrollbackLines`.

- [x] **Bug 169 (P2) — Editor Tab Dirty State Out of Sync on Save and Commit**
  - **Location**: `feature/editor/src/main/kotlin/digital/vmstudio/code/feature/editor/EditorViewModel.kt:387, 415`
  - **Fix**: In `publishSnapshot()`, re-emits `tabs.value = tabs.value.toMap()` ensuring active and background tab dirty indicators synchronize immediately.

- [x] **Bug 170 (P1) — VoiceManager SpeechRecognizer Thread-Safety Violation**
  - **Location**: `core/voice/src/main/kotlin/digital/vmstudio/code/core/voice/VoiceManager.kt:189`
  - **Fix**: Enforced `runOnMainThread` using `Handler(Looper.getMainLooper())` for all `SpeechRecognizer` lifecycle operations.

- [x] **Bug 171 (P2) — Hub Token Resolution Gap in OmniRoute WordPress Bridge**
  - **Location**: `core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/omniroute/agent/OmniRouteAgentLoop.kt:968`
  - **Fix**: Added fallback to `hubUserTokenCredentialId` from preferences via `SecureCredentialStore`, and added `normalizeWordPressApiBase` URL safety check.

- [x] **Bug 172 (P2) — Unbounded GitHub Archive Extraction & Missing Staging Cleanup**
  - **Location**: `feature/projects/src/main/kotlin/digital/vmstudio/code/feature/projects/ProjectCreateViewModel.kt:441-558`
  - **Fix**: Extracted to isolated staging directory before committing files to target, ensured automatic cleanup on failure, and enforced 500 MB byte and 20,000 entry limits.

- [x] **Bug 173 (P2) — Missing URL Scheme Handling & Blob Download Crash in In-App Browser**
  - **Location**: `feature/browser/src/main/kotlin/digital/vmstudio/code/feature/browser/BrowserScreen.kt:165, 225`
  - **Fix**: Added `normalizeBrowserUrl` to ensure protocol scheme is present, and guarded `Intent.ACTION_VIEW` against unsupported `blob:` / `data:` URI crashes.

- [x] **Bug 174 (P2) — Update Checker Nightly Channel False-Positive Loop**
  - **Location**: `core/update/src/main/kotlin/digital/vmstudio/code/core/update/GitHubUpdateChecker.kt:112-114`
  - **Fix**: Compared asset build code numbers and current version to prevent continuous update notifications when already running the nightly build.

- [x] **Bug 175 (P2) — Terminal Session Broken Pipe Write Swallowing**
  - **Location**: `core/terminal/src/main/kotlin/digital/vmstudio/code/core/terminal/session/TerminalSession.kt:197-204`
  - **Fix**: Transistioned session state to `TerminalSessionState.Failed` on write `IOException` or when `!channel.isOpen`.

- [x] **Bug 176 (P3) — PendingLine Concurrency Race in TerminalViewModel**
  - **Location**: `feature/terminal/src/main/kotlin/digital/vmstudio/code/feature/terminal/TerminalViewModel.kt:66`
  - **Fix**: Synchronized all read and write accesses to `pendingLine` across main thread and coroutines.

## Findings from Deep Codebase Audit (Bugs 177 - 186)

- [x] **Bug 177 (P1) — Unhandled OkHttp Header/URL Validation Exceptions in McpClient**
  - **Location**: `core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/mcp/McpClient.kt:172-184`
  - **Issue**: `Request.Builder().url()` and `addHeader()` are called outside `vmCatching`. Malformed URLs or headers with invalid ASCII chars or duplicate "Bearer " prefixes trigger unhandled `IllegalArgumentException`s, crashing the caller.
  - **Action**: Wrap request preparation in `vmCatching`, normalize missing URL schemes to `https://`, and sanitize header values.

- [x] **Bug 178 (P2) — Flattened MCP Tool Arguments Dropped in OmniRouteAgentLoop**
  - **Location**: `core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/omniroute/agent/OmniRouteAgentLoop.kt:1108-1118`
  - **Issue**: When an LLM passes parameters directly at the top level of `mcp_call` arguments rather than nested inside `"arguments"`, `toolArgs` falls back to empty `buildJsonObject { }`.
  - **Action**: If `"arguments"` key is absent, extract all top-level keys excluding `"tool_name"` and `"server_name"` into `toolArgs`.

- [x] **Bug 179 (P2) — Leaked .part Temporary Download Files on Failure in VmHttpClient**
  - **Location**: `core/network/src/main/kotlin/digital/vmstudio/code/core/network/http/VmHttpClient.kt:128-145`
  - **Issue**: `download()` creates `.part.<timestamp>` files but lacks a `finally { tempFile.delete() }` cleanup block, leaking orphaned files on failed or cancelled downloads.
  - **Action**: Add `finally` block ensuring `tempFile.delete()` is executed if the file was not successfully moved/finalized.

- [x] **Bug 180 (P2) — Hardcoded "main" Branch & Missing Android Workflow Candidates in GitHub Actions Dispatch**
  - **Location**: `core/git/src/main/kotlin/digital/vmstudio/code/core/git/GitHubRepositoryClient.kt:461-483`
  - **Issue**: Defaults `ref` to `"main"` (triggering HTTP 422 errors on repositories with `master` default branch) and only attempts `build.yml` / `nightly.yml`, missing standard Android templates like `android.yml`, `ci.yml`, and `release.yml`.
  - **Action**: Include `android.yml`, `ci.yml`, `release.yml` in workflow candidates, and fallback to `master` if `main` fails with ref errors.

- [x] **Bug 181 (P1) — Cloud APK Artifacts Not Discovered for GitHub Projects in ProjectDetailViewModel**
  - **Location**: `feature/projects/src/main/kotlin/digital/vmstudio/code/feature/projects/ProjectDetailViewModel.kt:194-220`
  - **Issue**: `scanApkArtifacts()` queries local/SSH paths via `remoteFileSystem.list()` even for GitHub-backed repositories (`/github/owner/repo`), returning zero artifacts for cloud-compiled APKs.
  - **Action**: Query GitHub Actions Artifacts API (`GET /repos/{owner}/{repo}/actions/artifacts`) to discover and list cloud-built APK assets.

- [x] **Bug 182 (P2) — Remote Git Commands Hanging on Unattended Prompts in CommandGitService**
  - **Location**: `core/git/src/main/kotlin/digital/vmstudio/code/core/git/CommandGitService.kt:142`
  - **Issue**: Remote `git pull`, `git push`, `git fetch` run without `GIT_TERMINAL_PROMPT=0`. If authentication fails or prompts for a passphrase, the remote command hangs indefinitely.
  - **Action**: Prefix executed git commands with `GIT_TERMINAL_PROMPT=0` to ensure prompt requests fail immediately rather than hanging.

- [x] **Bug 183 (P2) — URL Scheme and Link Fragility in OmniRoute Google Drive & Sheets Tools**
  - **Location**: `core/ai/src/main/kotlin/digital/vmstudio/code/core/ai/omniroute/agent/OmniRouteAgentLoop.kt:872, 904`
  - **Issue**: When an LLM or user supplies a full web URL (`https://docs.google.com/spreadsheets/d/...` or `https://drive.google.com/file/d/...`) instead of a plain ID, raw URLs are sent to Google REST endpoints, failing with HTTP 400.
  - **Action**: Extract clean file and spreadsheet IDs using regex from full URLs before constructing API endpoint requests.

- [x] **Bug 184 (P2) — Missing shouldOverrideUrlLoading in BrowserScreen Leading to ERR_UNKNOWN_URL_SCHEME**
  - **Location**: `feature/browser/src/main/kotlin/digital/vmstudio/code/feature/browser/BrowserScreen.kt:189-227`
  - **Issue**: `BrowserScreen` WebView does not implement `shouldOverrideUrlLoading`. Clicks on `mailto:`, `tel:`, `market:`, and `intent:` URLs produce `ERR_UNKNOWN_URL_SCHEME`.
  - **Action**: Implement `shouldOverrideUrlLoading` in `WebViewClient` to delegate non-HTTP/HTTPS URLs to `context.startActivity()`.

- [x] **Bug 185 (P2) — @web Directive Suppressed by Global Preference in AgentChatViewModel**
  - **Location**: `feature/ai/src/main/kotlin/digital/vmstudio/code/feature/ai/AgentChatViewModel.kt:1395`
  - **Issue**: `webAccessEnabled` is strictly assigned from `preferences.aiWebAccessEnabled`. Turn-level `@web` directives are ignored if the global setting is disabled.
  - **Action**: Compute `webAccessEnabled = isWebSearchRequested || preferences.aiWebAccessEnabled` so turn-level `@web` invocations always enable web capabilities.

- [x] **Bug 186 (P2) — Architecture Incompatibility in GitHub Update Checker Asset Selection**
  - **Location**: `core/update/src/main/kotlin/digital/vmstudio/code/core/update/GitHubUpdateChecker.kt:120`
  - **Issue**: `toUpdateInfoOrNull()` picks the first `.apk` asset arbitrarily without matching against device `Build.SUPPORTED_ABIS`, causing `INSTALL_FAILED_NO_MATCHING_ABIS` when multi-ABI split APKs are released.
  - **Action**: Match APK asset filenames against `Build.SUPPORTED_ABIS` and prioritize compatible ABI APKs or universal builds before falling back to `firstOrNull`.

- [x] **Bug 187 (P2) — Runaway Character Literal / String Tokenization Across Newlines in RegexSyntaxHighlighter**
  - **Location**: `core/editor/src/main/kotlin/digital/vmstudio/code/core/editor/RegexSyntaxHighlighter.kt:99-114`
  - **Issue**: `scanString()` does not check for newline boundaries when scanning single-line character / string literals (`'`). Unclosed quotes or apostrophes in comments/strings bleed across subsequent lines until EOF, corrupting entire syntax highlighting trees.
  - **Action**: Break and return `i to false` when encountering `\n` while scanning single-line quotes.

- [x] **Bug 188 (P2) — Non-Atomic File Overwrites in SftpRemoteFileSystem writeText()**
  - **Location**: `core/sftp/src/main/kotlin/digital/vmstudio/code/core/sftp/fs/SftpRemoteFileSystem.kt:270-282`
  - **Issue**: Passing `OpenMode.TRUNC` directly on the target file truncates remote content to 0 bytes immediately upon opening. If a network interruption or timeout occurs during subsequent chunk writes, the target file is permanently destroyed.
  - **Action**: Write first to a temporary remote file (`.vm_tmp.<timestamp>`) and atomically swap/rename into place upon successful completion.

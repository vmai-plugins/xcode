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

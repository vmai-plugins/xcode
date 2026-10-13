# App audit — checklist (orphans · unfinished features · bugs · improvements)

Audit of this Android app (Kotlin/Jetpack Compose, Hilt, Room, sshj, Material 3).

## Comparison with remote `https://github.com/vmai-plugins/xcode`

- `origin/main` == local `main` == `47f43321bc02157ef00bf6016219e70fab46770a` — **in sync**.
- `git log origin/main..HEAD` is empty → **no local commits ahead**.
- Working tree is **not** clean. Relative to `origin/main`:
  - **27 modified files** (uncommitted "index on main" / checkpoint changes), e.g. feature/connectors/ConnectorsScreen.kt (~583 lines), OmniRouteProvider.kt (~271), feature/ai/AgentChatViewModel.kt (~253), app/ui/VmApp.kt (~257), core/ai/omniroute/agent/OmniRouteAgentLoop.kt, core/project/ProjectRepository.kt, core/common/UserPreferencesRepository.kt, feature/projects/*, feature/settings/*, core/ai/*.
  - **10 untracked files**: `core/ai/.../background/AgentWakeLockManager.kt`, `core/connectors/.../github/{GitHubClient,GitHubManager,GitHubModels,Test}`, `feature/ai/.../DeltaBatcherTest.kt`, and the `scratch/` dir (dev scratch, several MB of HTML/script/APK).
- Remote also carries 3 other branches: `ccr-1d15ec5d-bb3wz8`, `claude/app-visibility-check-8dvfof` (small diffs ahead of main), `feat/port-agent-layer` (already merged into main history).

---

## 1. Orphans

### 1.1 Empty directories — zero Kotlin source, not Gradle modules (6)

| Directory | Contents | Confirmation |
|---|---|---|
| `core/agent/` | 0 `.kt` | not in `settings.gradle.kts` |
| `core/workspace/` | 0 `.kt` | not in `settings.gradle.kts` (spec §7 lists a future "workspace" concept) |
| `feature/activity/` | 0 `.kt` | not in `settings.gradle.kts` |
| `feature/dashboard/` | 0 `.kt` | not in `settings.gradle.kts` (dashboard UI deleted) |
| `feature/git/` | 0 `.kt` | not in `settings.gradle.kts` (core:git exists, no UI) |
| `feature/onboarding/` | 0 `.kt` | not in `settings.gradle.kts` (spec §4 onboarding) |

None of these contain any `.kt`/`.kts`/build files. They are dead directories and should either be **implemented or deleted**.

### 1.2 Documentation says "29 modules" but there are 24

- `README.md` and `ARCHITECTURE.md` repeatedly say "29 Gradle modules".
- Actual `settings.gradle.kts` includes: `app` + **14 core** (common, ui, security, database, network, ssh, sftp, terminal, editor, git, project, ai, connectors, update) + **9 feature** (projects, servers, files, terminal, editor, ai, tasks, connectors, settings) = **24 modules**.
- `ARCHITECTURE.md`'s module table lists `core:agent` and `core:workspace` as real modules — they are **not** in `settings.gradle.kts`.
- Fix: update counts and the module table in both docs; list real modules only.
## 2. Unfinished features

Compared against the spec §1–30 and the remote's feature set.

1. **Full code editor (spec §11)**
   - `feature:editor` = `EditorScreen.kt` + `EditorViewModel.kt` + `core:editor` (5 files: syntax-highlighter abstraction + `EditorState`).
   - **Missing:** tabs, search/replace, go-to-line, code folding, bracket matching, auto-indent, file tree, recent files, modified/unsaved indicators, encoding handling, real LSP.
   - A constrained mobile editor is arguably fine for now, but the spec's "no plain-text-only editor" requirement is only partially met.

2. **Git UI (spec §12, §111)**
   - `core:git` (7 files) implements status/diff/commit/log/pull/push over sshj, but **no `feature:git` module** and **no Git screen** in the shell. Diff review exists only as a tool for the AI agent. Git status is absent from project rows.

3. **Project management (spec §5–7)**
   - `feature:projects` has `ProjectsScreen`/`ProjectDetailScreen`/`ProjectCreateScreen` + ViewModels (6 files) — partial. No project tree explorer, no project-configuration screen, no project type detection, no `VMSTUDIO.md`/project instruction file, no workspace state (spec §7's "workspace" concept has no module at all).

4. **Onboarding (spec §4)**
   - No `feature:onboarding` module. `hasCompletedOnboarding` is stored but never read.

5. **AI agent dependability (spec §15–19)**
   - No background runs (`claude --bg` would survive app close; today leaving the app kills the run).
   - Agent conversations persist to Room with a resume id, and a **list + reopen** already exists (`AgentConversationsScreen` / `AgentConversationRepository` / navigation). *Remaining gaps:* resume across app restarts, run-watching while the app is backgrounded, and context carrying.
   - Context survives restarts only via in-memory LRU (16). No resume-from-database path.
   - `agentMaxIterations`, `agentMaxCommandsPerRun`, `agentMaxContextTokens` are stored but **never read** (spec §44 enforces ceilings).
   - Working directory **unvalidated**: typed agent paths are passed straight to the CLI; `RemotePath.confine` exists and is tested but is not applied → typo can scope the agent to the wrong tree or `/`.
   - No diff review of agent edits (see below).

6. **Model picker & health**
   - "Allow tools" switch in Settings **does nothing**: `OmniRouteProvider.run` enables the tool loop whenever any server is set, ignoring the switch.
   - Every health check sends **two billed chat requests** (dialect probes with model `probe`). Cache the detected dialect per base URL instead of re-probing.
   - **No context trimming**: long tool-heavy conversations grow until the gateway rejects.
   - **Cost always $0**: the gateway bills upstream; nothing shows tokens or cost.

7. **Tasks (spec §28)**
   - `feature:tasks` = 2 files. No task records beyond the chat; no run tracker, no progress, no retry UI separate from the chat.

8. **Activity timeline (spec §29)**
   - No `feature:activity` module; nothing writes the timeline. The dashboard monitors Room, but the timeline is never populated.

9. **Transfer manager (spec §26)**
   - SFTP has resumable transfers and bounded memory, but no **queue / progress / retry / cancellation UI**. Multi-select, copy, move, search are incomplete.

10. **Connectors / Drive (spec §23–25)**
    - `core:connectors` has a framework + `github/` experimental code (untracked). `feature:connectors` has 2 screens. Drive backup, MCP tool list (partially), and global drive search are not shipped.

11. **Notifications (spec §30)**
    - None. Long agent runs die when the app is backgrounded. `FOREGROUND_SERVICE` / `POST_NOTIFICATIONS` were removed as unused; a foreground service + notifications are needed before re-enabling.

12. **Global search (spec §19)**
    - None.

13. **Terminal polish (spec §9)**
    - No text selection / copy / paste. No multiple sessions or tabs. (ANSI codes are spec, not a bug — see "Not gaps".)



### 1.3 Spec is larger than what ships

`VMStudio Code — Master Android Development Specification.md` §4–30 describe far more than is implemented (e.g. §7 projects, §13 omniroute gateway, §23–25 connectors/drive, §26 transfer, §29 activity timeline, §30 notifications, §19 global search, §4 onboarding). Numerous spec blocks are marked "pending"/"not yet implemented" and have no backing module.

### 1.4 Build artifacts sitting on disk (ignored, not committed)

Root `ls` shows:
- `x-codes-latest.apk` (25 MB)
- `xcodes-v2.apk` (26 MB)
- `release/app-release.apk` (6 MB)

`*.apk` is in `.gitignore`, so these are untracked build leftovers. Not a code issue, but **~57 MB of stale artifacts** worth cleaning from disk.

### 1.5 Scratch directory — dev junk

`scratch/` contains `app_hub_index.html`, `find_phone_port.ps1`, `phone_install/index.html`, `phone_install/serve.js`, `phone_install/xcodes.apk`. Not part of the app; delete or archive.

## 3. Bugs

### 3.1 P0 — shipped blockers (already fixed upstream)
1. **Empty secrets permanently undecryptable.** `AndroidKeystoreCrypto.decrypt` guards `size > IV + TAG`. An empty plaintext encrypts to *exactly* `IV + TAG`, so `28 > 28` was false → valid empty secrets were unreadable and reported as "Secure storage unavailable". Fixed to `>=` (covered by `emptyPlaintextRoundTrips`).
2. **Unsafe credential id escaped the directory.** `FileSecureCredentialStore.secretFile` used `require(...)` → raw `IllegalArgumentException` escaped `read()`/`delete()` without `vmCatching`. Fixed to return `VmError.Validation` (covered by `anUnsafeIdIsRejectedRatherThanEscapingTheDirectory`).
3. **Tests silently reported "0 tests".** `androidx.test:runner` was absent, so `AndroidJUnitRunner` loaded nothing while the build succeeded. CI now asserts on results, not exit status.

### 3.2 Agent gaps (P0 — agent works for one screen visit)
4. **Resume still broken**: conversations are persisted and listable, but a resumed run does not continue reliably across app restarts.
5. **No diff review** — `Edit`/`Write` events carry the path but there is no before/after view. The biggest usability gap.
6. **Working directory unvalidated** — `RemotePath.confine` not applied.
7. **Agent limits do nothing** (iterations, commands per run, context tokens).
8. **No background runs** (`claude --bg` not wired).
9. **OmniRoute never live-tested** — gateway has returned 502 throughout; both dialects are unit-tested, neither has spoken to a live server.

### 3.3 P1 — blocks a public release
10. **CI has never run** — push and confirm the 4 jobs pass.
11. **No onboarding** — `hasCompletedOnboarding` stored but unread.
12. **No crash handler / crash reporting** — crashes leave no trace.
13. **No i18n** — 1 string resource; every UI string hardcoded across ~40 files.
14. **No privacy policy / Play data-safety declaration** (app stores credentials).
15. **Auto-reconnect never fires** — `Reconnecting` is declared and rendered but never emitted; Wi-Fi drop kills the session.
16. **Offline gating is HTTP-only** — an offline SSH attempt returns a generic socket error, not an actionable offline error.
17. **Activity timeline never written**.
18. **No terminal text selection/copy**.
19. **No foreground service** — needed before re-adding `FOREGROUND_SERVICE` / `POST_NOTIFICATIONS`.

### 3.4 P2 — polish
20. **Never profiled on-device** (terminal renderer, transcript list, large SFTP listings).
21. **Accessibility never verified with TalkBack**.
22. **No tablet multi-pane layout**.
23. **Room migration path untested** (no v2 yet).
24. **`CredentialReferenceDao` is dead schema** — never written.

### 3.5 Newly noticed in this local tree
25. **27 uncommitted modifications to `origin/main`** — several are large and risky (e.g. `ConnectorsScreen.kt`, `OmniRouteProvider.kt`, `AgentChatViewModel.kt`, `VmApp.kt`). Must be reviewed/committed before CI runs, otherwise state is unknown.
26. **`core/connectors/github/` experimental code is untracked** — not wired to the UI or any module graph.
27. **Release keystore does not exist** — config is wired; losing it later means never updating under this package name.
28. **`core:connectors` `CredentialReferenceDao` dead schema** — no table for credential-references.

## 4. Improvements

### 4.1 Code hygiene
1. **Delete or implement the 6 empty dirs** (`core/agent`, `core/workspace`, `feature/activity`, `feature/dashboard`, `feature/git`, `feature/onboarding`).
2. **Reconcile docs** — README.md / ARCHITECTURE.md say 29 modules; use 24 and list the real modules.
3. **Commit or clean the 27 uncommitted modifications** — large diffs (ConnectorsScreen 583, OmniRouteProvider 271, VmApp 257, AgentChatViewModel 253) should be split into reviewable commits.
4. **Delete `scratch/`** (dev junk) or archive it out of the repo tree.
5. **Archive stale APKs** — ~57 MB of invalid/old artifacts (`x-codes-latest.apk`, `xcodes-v2.apk`, `release/app-release.apk`).
6. **Wire `core:connectors/github/` into the module graph** (it is currently untracked dead-end code).

### 4.2 Product / release readiness
1. **Green CI** (4 jobs) before any release.
2. **i18n** — currently 1 string across ~40 files; needed for Play.
3. **Privacy policy + Play data-safety declaration** (credentials stored).
4. **Create & back up a release keystore** (config exists, no keystore).
5. **Release build / R8 + lint** — debug + release build; release APK 5.4 MB, R8 + lint clean.
6. **Detekt clean** (139-item baseline).
7. **Instrumented tests** — 31 pass, 0 failures on device.
8. **Performance** — profile terminal renderer, transcript, large SFTP listings on-device.
9. **Accessibility** — verify with TalkBack.
10. **Tablet multi-pane layout**.
11. **Crash handler + reporting**.
12. **Foreground service + notifications** for long-running agent/transfer runs.
13. **Reconnect / offline gating** — emit `Reconnecting`, gate offline SSH attempts with an actionable error.
14. **Room v2 migration** — test against the checked-in exported schema.

### 4.3 Agent / UX wins (cheapest, highest value)
15. **Resume across app restarts** — replay a persisted conversation into a live session.
16. **Diff review UI** — before/after view of agent edits (biggest usability gap).
17. **Working-directory confinement** — apply `RemotePath.confine` to agent typed paths.
18. **Agent limits** — enforce `agentMaxIterations`, `agentMaxCommandsPerRun`, `agentMaxContextTokens`.
19. **Background runs** — support `claude --bg`.
20. **Context trimming** — trim oldest turns while keeping tool-call/result pairs together.
21. **Cost / tokens** — show tokens; read cost if the gateway reports it.
22. **Model-picker caching** — cache detected dialect per base URL; one picker, no dual probes.
23. **Diff review** especially for the agent loop (linked above).

---

## 5. Recommended scope — "mobile dev-box client" (phasing)

Shipping this well is **weeks**, not months. Recommended order:
1. Instrumented tests + real-server flows (connect → accept host key → browse files → terminal → agent task).
2. Resume across app restarts — replay a persisted session into a live run.
3. Working-directory confinement + agent limits.
4. **Git status/diff** (reviewable agent changes) — higher value than a full editor.
5. Onboarding, crash handler, reconnect, offline gating, activity log.
6. Keystore, privacy policy, CI green, i18n.
7. Ship.


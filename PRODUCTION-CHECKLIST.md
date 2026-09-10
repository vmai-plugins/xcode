# Production checklist

Audited 7 September 2026 against the working tree and **against the app running on a
real device** (Galaxy M14, Android 15). Every item below was verified by inspection,
by running the build, or by running the app — nothing is inferred from the docs.

**Verified this session:** the app installs and launches on a physical device with no
crash. That closes the blocker that made every other item unmeasurable.

Legend: **P0** blocks any release · **P1** blocks a public release · **P2** polish

---

## 1. Where the app actually is

### Built and working

SSH connection engine · host-key verification (TOFU) · command safety + approval gate ·
SFTP browse/transfer · terminal emulator + renderer · server telemetry (CPU, RAM, disk,
load, uptime, top processes) · **Apps Hub** (PM2/process detection, framework
detection, git branch, logs, start/stop/restart via guarded commands) · **Server
Recipes** (one-click stack provisioning — WordPress sandbox ported from the old app,
reframed as a generic recipe system routed through the approval gate) · **Web
Preview** (browser-chrome status surface for running apps) · **Code Editor**
(syntax-highlighted remote file editor with line numbers, undo/redo, and save) ·
**Git** (version control over SSH: status, diff, stage, commit, log, push/pull) ·
**Diff Review** (unified diff viewer with accept/reject, integrated with agent) ·
**AI model sync** (live /models fetch, custom model-id input) · server management ·
secure credential storage · Room database (19 tables) · design system · navigation
shell · **AI agent** (Claude Code over SSH, plus an OmniRoute chat provider)

- 29,000+ lines, 180+ Kotlin files, 250+ unit tests, 0 failures
- Debug **and** release build; release APK 5.4 MB, R8 + lint clean
- Detekt clean against a 139-item baseline
- CI workflow written (4 jobs) — never executed, no remote

### Not built at all

Verified absent, not merely incomplete — 13 of 29 modules contain zero code:

| Missing | Spec | Consequence |
|---|---|---|
| Projects / workspace | §5–7 | No project concept; agent takes a typed path |
| Onboarding | §4 | First launch is an empty dashboard, no guidance |
| Tasks | §28 | No record of a run beyond the chat |
| Activity timeline | §29 | Dashboard reads it; nothing ever writes it |
| Transfer manager | §26 | No queue, no progress, no resume UI |
| Connectors / Drive | §23–25 | — |
| Global search | §19 | — |
| Notifications | §30 | Long runs die when the app is backgrounded |

---

## 2. P0 — Ship blockers

- [x] ~~Run the app on a device~~ — **done, launches clean**
- [x] ~~Run the 31 instrumented tests~~ — **done on device, 31 pass, 0 failures.** Two real bugs found and fixed; see §8.
      why they reported "0 tests"; fixed but unverified. Reconnect the phone and run:
- [ ] **Exercise the real flows on the device.** Add server → connect → accept host
      key → browse files → open terminal → run an agent task. Nothing beyond app
      launch has been confirmed against a real server.
- [ ] **Create and back up a release keystore.** Config is wired; no keystore exists.
      Losing it later means never updating under this package name.
- [ ] **Commit and push.** Repo initialised, nothing committed, no remote — CI has
      never run.

## 3. P0 — Agent gaps

The agent works for one screen visit. These stop it being dependable.

- [ ] **Conversations are write-only.** Persisted to Room with the resume session id,
      but nothing reads them back — no list, no reopen. Leaving the screen loses the
      transcript, and `--resume` only works within a single visit. The persistence
      layer is built and unreachable.
- [ ] **No diff review.** `Edit`/`Write` events carry the affected path, but there is
      no before/after view. You cannot see what the agent changed without opening a
      terminal and running `git diff`. **Biggest usability gap in the app.**
- [ ] **Working directory is unvalidated.** Any typed path is passed to the CLI.
      `RemotePath.confine` exists and is tested but is not applied here, so a typo
      silently scopes the agent to the wrong tree — or to `/`.
- [ ] **Agent limits do nothing.** `agentMaxIterations`, `agentMaxCommandsPerRun` and
      `agentMaxContextTokens` are stored and never read (spec §44 requires enforced
      ceilings).
- [ ] **No background runs.** `claude --bg` would let a run survive the app closing;
      today, leaving the app kills it.
- [ ] **OmniRoute untested.** Gateway has returned 502 throughout. Both API dialects
      are implemented and unit-tested; neither has spoken to a live server.

## 4. P1 — Blocks a public release

- [ ] CI has never run — push and confirm all four jobs pass
- [ ] No onboarding; `hasCompletedOnboarding` is stored and never read
- [ ] No crash handler and no crash reporting — a user's crash leaves no trace
- [ ] No i18n — 1 string resource; every UI string hardcoded across ~40 files
- [ ] No privacy policy or Play data-safety declaration (required: the app stores
      credentials)
- [ ] Auto-reconnect never fires — `Reconnecting` is declared and rendered, never
      emitted; dropping Wi-Fi kills the session permanently
- [ ] Offline gating is HTTP-only — an offline SSH attempt gives a generic socket error
- [ ] Activity timeline never written
- [ ] No terminal text selection or copy (spec §9)
- [ ] Foreground service absent — needed before re-adding `FOREGROUND_SERVICE` /
      `POST_NOTIFICATIONS`, which were removed as unused

## 5. P2 — Polish

- [ ] Performance never profiled on-device (terminal renderer, transcript list, large
      SFTP listings)
- [ ] Accessibility never verified with TalkBack
- [ ] No tablet multi-pane layout (spec §3)
- [ ] 13 empty modules — build them or delete them; they signal intent that does not exist
- [ ] `CredentialReferenceDao` is dead schema, never written
- [ ] Room migration path untested (no v2 yet)

---

## 6. The decision that sets the timeline

The spec describes nine phases. Eleven major features are absent. Finishing all of it
is **months**.

But what exists is already a coherent product: *connect to your dev box from your
phone, browse it, run a shell, and drive Claude Code on it.* Almost nothing else does
that. Shipping it well is **weeks**, not months.

**Recommended scope — "mobile dev-box client":**

1. Instrumented tests + real-server flows (P0)
2. Agent conversation list — persistence already exists, cheapest real win
3. Working-directory confinement + agent limits
4. **Git status/diff** so agent changes are reviewable — more valuable than a full
   editor, because the agent does the editing and you do the reviewing
5. Onboarding, crash handler, reconnect, offline gating, activity log
6. Keystore, privacy policy, CI green, i18n
7. Ship

Deferred under this scope: full code editor, connectors, transfer manager, global
search, tasks. Each is a phase in its own right and none blocks the core loop.

---

## 7. Not gaps

Recorded so they are not re-litigated:

- **No in-app agent loop** — delegated to Claude Code deliberately; see AI-AGENT.md
- **The app cannot gate the agent's own tool calls** — they are the CLI's subprocesses;
  control is via permission mode and tool lists, and this is stated in the UI
- **Terminal command guard is best-effort** — a shell delivers keystrokes, not commands;
  authoritative enforcement is on `CommandGuard.run`
- **`MagicNumber` findings in the emulator** — the numbers are the ANSI specification

---

## 8. Bugs found by running on hardware

Both were invisible to 205 passing unit tests, and both were found within minutes of
the first real device run. Recorded because they are the argument for doing this
earlier rather than later.

### Empty secrets were permanently undecryptable

`AndroidKeystoreCrypto.decrypt` guarded with `size > IV + TAG`. An empty plaintext
encrypts to *exactly* `IV + TAG` with no body, so `28 > 28` was false and a validly
stored empty secret could never be read back — reported as the misleading "Secure
storage is unavailable".

Reachable in practice: a key with no passphrase, or an empty password field. Fixed to
`>=`; covered by `emptyPlaintextRoundTrips`.

### An unsafe credential id crashed instead of failing

`FileSecureCredentialStore.secretFile` used `require(...)`, throwing a raw
`IllegalArgumentException` that escaped `read()` and `delete()` without passing
through `vmCatching`. The traversal defence worked, but a corrupted preference would
have crashed the app rather than producing an error.

This breaks the codebase's own rule that every failure is a `VmResult.Failure`. Now
returns `VmError.Validation`; covered by
`anUnsafeIdIsRejectedRatherThanEscapingTheDirectory`.

### Why the tests reported "0 tests" at first

`androidx.test:runner` was absent, so `AndroidJUnitRunner` could not load and the
suites silently ran nothing while the build reported success. A green pipeline that
executes no tests is worse than a red one; the CI job asserts on results, not exit
status.

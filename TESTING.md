# Testing

## Strategy

Three levels, chosen so the majority of logic is testable without a device:

| Level | Runs on | Covers |
|---|---|---|
| Unit | JVM | Validation, safety analysis, redaction, result plumbing, mappers, planners |
| Integration | Device / emulator | Room migrations, Keystore crypto, SSH, SFTP, Git, OmniRoute |
| UI | Device / emulator | Onboarding, project creation, server connection, terminal, editor, AI chat, approval dialogs |

Anything that decides whether an operation is safe — command classification, the
permission engine, path confinement, validation — is written as pure Kotlin
specifically so it can be tested exhaustively at the unit level.

## Current coverage

**205 unit tests, all passing.**

> The most valuable lesson so far came from a test that did not exist. `CommandSafety`
> had 32 passing tests and zero call sites: a fully verified safety engine that was
> never consulted before running a command. Tests that pin down *behaviour* say nothing
> about whether anything invokes that behaviour. `CommandGuardTest` exists to cover the
> wiring, and the same treatment is owed to every policy component added from here.

| Suite | Tests | What it pins down |
|---|---|---|
| `SecretRedactorTest` | 10 | Registered-value and structural redaction: API keys, JSON passwords, `Authorization` headers, URL credentials, PEM bodies, throwable messages |
| `VmResultTest` | 6 | map/flatMap/fold semantics, `vmCatching` mapping, `CancellationException` rethrow, host-key mismatch never retryable |
| `SecretTest` | 8 | `toString` masking, wipe semantics, ownership of `wrapping` vs `of`, constant-time equality, char-array scrubbing |
| `ServerValidatorTest` | 17 | Every field rule, including pasted URLs, `user@host` in the wrong field, out-of-range IPs, public key mistaken for private, and edit-without-retyping-the-secret |
| `CommandSafetyTest` | 32 | Every risk tier; flag-order and bundling on `rm`; danger hidden after `;`, `&&` and `|`; quoting; production escalation; path confinement; agent-only rules; and the rule that the agent can never skip a destructive confirmation |
| `RemotePathTest` | 16 | Normalisation, POSIX root semantics, and the sandbox boundary: traversal rejection, sibling-prefix rejection, sensitive-file detection |
| `CommandGuardTest` | 13 | The **wiring**, not the classification: that a blocked command never reaches the approval gate, that the "always confirm" setting genuinely takes effect, that it can never be used to skip an agent proposal, that production escalation reaches the prompt, and that the gate publishes, clears and ignores stale decisions |
| `ClaudeCodeStreamParserTest` | 19 | Every envelope type, tool summaries, and the robustness the real stream demands: non-JSON lines on stdout, malformed JSON, unknown envelopes, and a completion summary observed without a `type` field |
| `ClaudeCodeCommandBuilderTest` | 16 | Shell-injection resistance on the prompt and working directory (`$(...)`, backticks, embedded quotes, newlines), flag construction, and that no `ANTHROPIC_*` credential reaches the command line |
| `SseDecoderTest` | 13 | SSE framing: multi-line data, event names, comments and heartbeats, the `[DONE]` sentinel, a trailing event with no closing blank line, and JSON containing colons |
| `ChatStreamDecoderTest` | 17 | Both gateway dialects — Anthropic `content_block_delta` and OpenAI `choices[].delta` — plus usage under either naming, model-list shapes, and malformed bodies |
| `TerminalEmulatorTest` | 38 | Wrapping (including the deferred wrap at the exact margin), scrollback bounds, cursor addressing and clamping, erase and insert/delete, SGR including 256-colour and truecolour, scroll regions, the alternate screen, OSC titles, escape sequences and UTF-8 split across writes, malformed input, and resize in both directions |

Run them:

```bash
./build.sh testDebugUnitTest
```

## Bugs these tests caught

Worth recording, because they are the reason the suites exist:

1. **`Authorization` header redaction left the token behind.** The pattern used
   `\S+`, which stopped at the first space, so `Authorization: Bearer <jwt>` masked
   only the word `Bearer`. Fixed to consume to end-of-line.
2. **`Secret.useAsChars` did not scrub its buffer.** A corrupted character literal
   meant the array was filled with the wrong value. Fixed, and the wipe value is now
   explicitly NUL via `Char(0)` rather than a raw literal.
3. **A mistyped IP validated as a hostname.** `203.0.113.999` is syntactically a legal
   DNS name, so the hostname rule accepted it. Added an all-numeric-labels check that
   routes such input through IPv4 validation with a specific message.
4. **`rm --no-preserve-root -rf /` was classified as safe.** The regex expected the
   short flags first. Replaced with a real flag parser, which also fixed split
   (`-r -f`) and long-form (`--recursive --force`) variants that would have slipped
   through the same way.
5. **Two safety rules could never fire.** The fork-bomb and `curl … | sh` patterns were
   evaluated per command segment, but segmentation strips exactly the `;` and `|` that
   define them. Split into whole-command rules.

Numbers 4 and 5 are the reason the safety layer has 32 tests rather than a handful:
both bugs left a rule that looked correct in review and did nothing at runtime.

## Instrumented tests

**31 tests written; not yet executed.** They compile and are wired into CI, but no
device or emulator has run them at the time of writing.

| Suite | Tests | Why it cannot be a unit test |
|---|---|---|
| `KeystoreCryptoTest` | 11 | Calls the real Android Keystore, which does not exist on the JVM. Covers round-trip, IV freshness across encryptions, GCM rejecting tampered and truncated input, key reuse across instances, and that destroying the key really orphans existing ciphertext. |
| `SecureCredentialStoreTest` | 12 | Real Keystore plus real filesystem. Asserts the plaintext and the label are genuinely absent from the bytes on disk, that concurrent writes all survive the shared index, that the store reloads after a restart, and that an unsafe id cannot escape the credentials directory. |
| `VmDatabaseTest` | 8 | Real Room + SQLite. Confirms foreign keys are actually *enforced* rather than merely declared — the `PRAGMA foreign_keys = ON` callback had never executed — that deleting a server nulls its project reference instead of destroying the project, and that enums round-trip by name. |

Run them against a connected device or emulator:

```bash
./build.sh :core:security:connectedDebugAndroidTest :core:database:connectedDebugAndroidTest
```

CI runs them on an API 35 emulator via the `instrumented-tests` job.

## Not yet covered

No integration or UI tests exist, because the subsystems they would target — SSH,
SFTP, Git, OmniRoute, the agent — are not implemented. Each phase lands with its own
tests:

- **Phase 2** — SSH handshake against a container, host-key change handling, SFTP
  round-trip with resume, terminal ANSI parsing.
- **Phase 4** — Git status/diff/commit/branch against a fixture repository.
- **Phase 5** — OmniRoute client against `MockWebServer`: streaming, cancellation,
  retry, error mapping.
- **Phase 6** — Agent loop with a scripted provider; permission engine; context
  budget enforcement; destructive-command refusal.
- **Phase 9** — The security suite in the specification: credential leakage, log
  leakage, insecure storage, invalid certificates, host-key changes, expired OAuth,
  malicious command attempts, path traversal, unauthorized project access.

Room migration tests become meaningful at schema version 2; the exported schema for
version 1 is checked in at `core/database/schemas/` so those tests can run against
the real historical schema rather than a regenerated approximation.

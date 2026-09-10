# Security

## Threat model

What this design defends against:

| Threat | Defence |
|---|---|
| Device backup / filesystem extraction yields credentials | Secrets encrypted under a non-exportable Keystore key; backup disabled and explicitly excluded |
| Secrets leaking through logs, crash reports or diagnostics exports | Redaction applied at capture time, before any sink sees the text |
| Database dump revealing credentials | Room stores opaque references only; no secret material in any table |
| Man-in-the-middle on SSH | Trust-on-first-use host keys with an explicit, non-retryable prompt on change |
| Path traversal via a malformed credential id | Credential filenames validated against a strict allowlist pattern |
| AI agent acting outside its sandbox | Tool registry with declared permissions; project-root confinement; destructive-command confirmation |
| Timing attacks on secret comparison | Constant-time comparison in `Secret.equals` |

Explicitly **not** defended against: a rooted device with an active attacker, or a
compromised OS. Keystore raises the cost of extraction; it does not make it impossible.

## Key management

`AndroidKeystoreCrypto` owns a single AES-256-GCM master key, alias
`vmstudio.master.v1`:

- Generated on first use, never on cold start, so a user who stores nothing never
  has a key created.
- `setIsStrongBoxBacked(true)` is attempted first and degraded to a TEE or software
  Keystore key when the device lacks StrongBox. Refusing to run on older hardware
  would be worse for the user than a still-Keystore-protected key. Settings → Security
  reports which the device actually got.
- `setRandomizedEncryptionRequired(true)`: the Keystore owns IV generation. IV reuse
  under GCM is catastrophic, so the app is never in a position to get it wrong.
- The raw key bytes never enter the app process.

Ciphertext layout is `[12-byte IV][ciphertext || 16-byte GCM tag]`.

`KeyPermanentlyInvalidatedException` — raised when the user removes their screen lock
or resets biometrics — is mapped to a specific, actionable error telling the user to
re-enter their credentials, not to a generic failure.

## Credential storage

`FileSecureCredentialStore` writes one encrypted file per secret under
`filesDir/credentials/`, plus a separately encrypted index of non-secret references.

- **One file per secret** so a corrupted or undecryptable entry costs one credential,
  not all of them, and writes never rewrite unrelated secrets.
- **Atomic writes** via temp file + `fsync` + rename, so process death mid-write
  cannot leave a truncated file that fails its GCM tag check on the next read.
- **Filename validation**: credential ids must match `[A-Za-z0-9_-]{1,64}`. Ids are
  generated UUIDs, but the store must not be convertible into an arbitrary-file-write
  primitive by a malformed id arriving from elsewhere.
- **Transactional save**: `DefaultServerRepository` writes secrets first; if the Room
  write then fails, every credential created in that call is deleted. Superseded
  credentials are removed only after the new row commits. A secret is never orphaned
  by a failed save, and never left behind by a delete.

## The `Secret` type

Secrets in memory are `Secret`, not `String`:

- `toString()` returns `[REDACTED]`, so interpolating one into a log line, a crash
  report or a debugger watch cannot leak it.
- The payload is a `ByteArray` that `wipe()` zeroes, rather than an immutable String
  left for the GC.
- `equals` uses `MessageDigest.isEqual` — constant time, so comparison is not a
  timing oracle.
- `use { }` is the only accessor, making every read a greppable call site.
- `useAsChars` scrubs its temporary array in a `finally`.

ViewModels clear secret fields from UI state the moment they are stored: ViewModel
state survives configuration changes and appears in heap dumps.

## Redaction

`SecretRedactor` runs on every log message *before* any sink sees it, so even a
careless `VmLog.d(SSH, tag, "password=$password")` cannot leak. Two layers:

1. **Registered values** — anything the app knows to be a secret is registered when
   loaded and substituted by exact match. Catches secrets in contexts no pattern
   would anticipate. Values under 8 characters are ignored as too collision-prone.
2. **Structural patterns** — PEM private key blocks, `Authorization` and
   `Proxy-Authorization` headers, `Bearer` tokens, `key=value` pairs with
   secret-looking names, and credentials embedded in URLs. Catches secrets the app
   never held as a discrete value, such as one echoed back by a remote shell.

Throwable messages and stack frames are redacted too — the message is the usual leak
vector, since libraries habitually include the offending value in it.

The pattern for `Authorization` consumes to end-of-line rather than to the first
space. An earlier `\S+` version left the JWT behind after masking only `Bearer`;
this is covered by a regression test.

Diagnostics exports are therefore safe to share by construction: entries were
redacted at capture time, so the export performs no scrubbing of its own and cannot
miss anything.

## SSH host verification

`KnownHostKeyEntity` records trusted host keys (fingerprints are not secrets, so they
live in Room). A key that does not match produces `VmError.HostKeyMismatch`, which:

- is **never retryable** — it may indicate interception and always needs a human
  decision;
- shows both the expected and received fingerprints and the key type;
- says plainly that a legitimate server rebuild also causes this, so the user can
  make an informed choice instead of reflexively accepting.

`replaceTrusted` is transactional, so a crash cannot leave a host with no trusted key
while the old one is already gone.

## Backup

`android:allowBackup="false"`. `backup_rules.xml` and `data_extraction_rules.xml`
additionally exclude `credentials/`, the database and shared preferences, so that if
backup is ever enabled the exclusions are already correct. A restored copy would be
undecryptable anyway — the Keystore key cannot leave the device — but relying on that
alone would be sloppy.

## Clipboard

`SecureClipboard.copySensitive` sets `ClipDescription.EXTRA_IS_SENSITIVE`, which
suppresses the system clipboard preview on Android 13+. Only the fact of a copy is
logged, never the value. `clearIfOwned` lets a screen clear the clipboard on exit if
it still holds content this app placed there.

## Command safety

`CommandSafety` classifies a command; `CommandGuard` is what consults it. Everything
that runs a remote command goes through the guard, which is the only sanctioned path
to execution:

1. **Blocked** commands are refused outright and never prompted for. Offering a
   confirmation would imply there is a correct answer other than no.
2. **Destructive** commands raise an approval request and suspend until a person
   decides.
3. **Cautious** commands run without prompting when a user typed them, and prompt
   when the agent proposed them.

The `CommandApprovalGate` is process-wide with exactly one outstanding request.
Queueing several would let a user approve a dialog they believe belongs to the
command they just typed while it actually belongs to something queued behind it.
Approval is a suspending call, so cancelling the caller — leaving the screen, stopping
the agent — cancels the pending approval rather than leaving a dialog that can later
approve a command nobody is waiting for.

**The "always confirm" setting cannot be used to weaken agent proposals.** Turning it
off lets a user's own destructive command run unprompted; a command the agent proposed
still requires confirmation. This is enforced in `CommandSafety` and covered by tests
in both `CommandSafetyTest` and `CommandGuardTest`.

### The interactive terminal is best-effort

An interactive shell delivers keystrokes, not commands. Characters must reach the
remote side as typed so that `less`, `vim` and password prompts work, which means the
app never sees a command — only a stream.

The terminal therefore mirrors the line locally and assesses it when Enter is pressed,
withholding the newline until the check passes. On refusal it sends Ctrl+C, because
the characters are already echoed on the remote line buffer and simply withholding the
newline would leave the command sitting at the prompt looking accepted.

The mirror is deliberately abandoned — and the line sent unchecked — when:

- a full-screen program owns the screen (detected via the alternate screen buffer),
- the user edits with arrow keys, Home/End or history recall,
- a control character is sent.

In those cases a guess about the shell's line buffer is worse than no guess. **This is
a guard against mistakes, not a sandbox.** Authoritative enforcement is on
`CommandGuard.run`, where the whole command string is known: configured project
commands and, once built, every agent tool call.

## Agent safety (design, pending implementation)

- Every tool declares its permissions, risk level and whether it requires confirmation.
- Autonomy levels raise the floor, never the ceiling: destructive and
  production-affecting operations require confirmation at **every** level, including
  Full Agent.
- Filesystem access is confined to the project root; `/etc`, `/root` and sibling
  projects are outside the sandbox. `RemotePath.confine` implements this; the guard
  passes `projectRoot = null` until projects exist, which disables path checks rather
  than inventing a root.
- Servers marked `PRODUCTION` always require confirmation for destructive commands.
  This is live today.
- The user can stop a running agent at any point.

## Reporting

No published channel yet. Open an issue in the repository.

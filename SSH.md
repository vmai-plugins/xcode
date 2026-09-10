# SSH

## Library choice

**sshj 0.40.0**, with BouncyCastle (`bcprov-jdk18on`) and `net.i2p.crypto:eddsa`.

Considered and rejected:

| Option | Why not |
|---|---|
| JSch | Unmaintained since 2018; no ed25519, no modern KEX. |
| Apache MINA SSHD | Heavier, and its server-side machinery is dead weight in a client. |
| Shelling out to a bundled `ssh` | No usable binary on stock Android, and no channel multiplexing. |

sshj is actively maintained, supports curve25519 KEX, ed25519 keys and AES-GCM, and
exposes SFTP over the same transport.

### The Android BouncyCastle problem

Android ships a deliberately cut-down provider registered under the name `BC`. sshj
looks algorithms up by that name and silently gets the stripped version — ed25519
keys fail to parse and some KEX methods disappear. `SshSecurityProviders` replaces
the registration with the full BouncyCastle at position 1, once, before the first
connection. Without this the client works against old servers and fails against new
ones, which is the worst possible failure mode.

## Connection model

`SshConnectionManager` owns **one connection per server**, shared by the terminal,
file browser, Git and the agent. SSH multiplexes channels over one transport, so a
connection per feature would waste a handshake each, multiply authentication cost,
and give the user several connection indicators that can disagree.

- A per-server `Mutex` means concurrent callers share one connection attempt rather
  than racing to open several.
- A session object that exists but is no longer connected is discarded and re-established
  rather than handed out, so a caller never receives a dead socket.
- `SshSession` exposes `execute`, `openShell` and `openSftp`. Callers own the shell
  and SFTP channels they open; the manager owns the transport.

### Command execution

`SshSession.execute` drains stdout and stderr **concurrently**. Reading them in
sequence deadlocks the moment a command fills the pipe of whichever stream is read
second — the classic way to hang on a verbose build.

Output is capped by `CommandLimits.maxOutputBytes`; past the cap bytes are consumed
and discarded rather than the stream being closed, because abandoning it would leave
the remote process blocked on a full pipe instead of exiting.

`CommandResult.exitCode` is nullable: a command killed by a signal never reports one,
and treating that as exit 0 would make a killed build look successful.

## Host key verification

Trust on first use, backed by `known_host_key` in Room. Fingerprints are computed the
way OpenSSH does (`SHA256:` + unpadded base64 of the SHA-256 of the wire-format key),
so the value shown to the user can be compared character-for-character with
`ssh-keygen -lf`. A fingerprint the user cannot verify out-of-band is security
theatre.

### Why the prompt is not inline

sshj calls `HostKeyVerifier.verify` on the transport thread during the handshake.
Blocking it while a dialog waits for a human would stall the transport for as long as
the user takes to read a fingerprint and risks tripping the negotiation timeout.

So `TofuHostKeyVerifier` consults the trust store — a local database read, measured in
microseconds — and **fails the handshake** for an unknown or changed key, recording
the verdict. The manager surfaces it through `pendingHostKeys`; the UI shows the
fingerprint; and only if the user accepts does `trustPendingHostKey` store the key and
reconnect.

The cost is one extra TCP connection on first contact with a host. The benefit is that
a security decision is never made by a thread under time pressure.

A changed key produces `VmError.HostKeyMismatch`, which is **never retryable** and
shows both fingerprints, stating plainly that a legitimate server rebuild looks
identical to interception.

## Authentication

Password, private key, and key-with-passphrase. Agent forwarding is declared in the
model but not implemented, and says so rather than failing obscurely.

The secret is read from the credential store at the last possible moment and wiped
immediately after, so plaintext exists for the duration of one handshake rather than
for the lifetime of the connection. The passphrase `PasswordFinder` returns
`shouldRetry = false`: retrying with the same stored passphrase would loop forever
instead of surfacing an actionable auth failure.

Key material is loaded with `loadKeys(pem, null, passwordFinder)` — passing `null` as
the public key is what tells sshj the first argument is key *content* rather than a
filesystem path.

Authentication failures are **not retryable**: repeating a rejected credential wastes
an attempt and can trip fail2ban.

## Environment probe

One round trip after connecting, in POSIX shell only:

```sh
echo "VMSHELL=${SHELL:-}"; echo "VMHOME=${HOME:-}"; echo "VMOS=$(uname -s 2>/dev/null)"; ...
```

Bashisms are avoided deliberately — the login shell may be `sh`, `dash`, `ash` on
Alpine, or `zsh`. Every field is optional and the probe never fails the connection: a
hardened host may have no `uname`, and an unknown OS is a missing label in the UI, not
a reason to refuse to work. `/bin/sh` is the fallback shell because POSIX requires it;
falling back to bash would break Alpine and BusyBox hosts.

## Keepalive

`KeepAliveProvider.KEEP_ALIVE` is set explicitly. Without it sshj sends nothing, and a
NAT or firewall silently drops an idle session with no notification. The interval is
per-server and configurable; 0 disables it.

The socket read timeout is 0 (none): a long-running command legitimately produces no
traffic for minutes, and keepalives are the correct mechanism for detecting a dead
peer.

## Command safety

`CommandSafety` classifies every command before it runs — whether typed by the user,
configured as a project build step, or proposed by the agent. See
[SECURITY.md](SECURITY.md#command-safety) for the policy; the rules and their tests
live in `core/ssh/src/.../command/`.

`rm` is analysed by **parsing its flags**, not by matching text: flags may be bundled
(`-rf`), split (`-r -f`), spelled long (`--recursive --force`), or ordered so a long
flag precedes the short ones (`rm --no-preserve-root -rf /`). A regex covering all of
those is a bypass waiting to happen.

Rules that depend on shell operators (fork bombs, `curl … | sh`) are evaluated against
the whole command, because segmentation removes exactly the characters that make them
dangerous.

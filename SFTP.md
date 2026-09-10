# SFTP and the remote filesystem

## Channel model

`SftpRemoteFileSystem` opens a **short-lived SFTP channel per operation** over the
shared SSH transport, rather than caching one.

A cached channel would mean a long download blocks directory listings behind it, and a
channel that dies mid-browse takes every other pending operation with it. Opening a
channel is cheap once the transport exists; the isolation is worth it.

## Paths

`RemotePath` implements POSIX path handling directly. `java.io.File` and `java.nio.Path`
are not usable: they follow the *local* platform's rules, so they would introduce
backslashes on Windows-hosted tooling and resolve against the device's filesystem on
Android. Remote paths are always POSIX regardless of what the client runs on.

Normalisation is also a security boundary. `confine(root, candidate)` resolves a path
and returns null if the result escapes the root — this is the mechanism behind the AI
agent's filesystem sandbox. Two details matter:

- A `..` that would escape a **relative** path is preserved rather than dropped, so
  the attempt stays visible; `confine` rejects it. Silently collapsing it would turn a
  traversal attempt into a valid-looking path.
- The containment check appends a separator before comparing, so `/var/www/app-backup`
  is not treated as inside `/var/www/app`.

`RemotePath.looksSensitive` flags files that conventionally hold secrets (`.env*`,
`id_rsa`, `*.pem`, `*.key`, `credentials`, `.netrc`, keystores). This withholds them
from AI context by default and marks them in the browser; it does not prevent the user
opening them.

## Memory

Nothing loads a whole file into memory implicitly:

- Downloads and uploads stream through a 32 KiB buffer into a `RandomAccessFile`.
  32 KiB balances throughput against responsiveness — larger chunks stall cancellation,
  smaller ones waste round trips on a high-latency link.
- `readText` is bounded (8 MiB by default) and refuses larger files with a message
  naming the actual size. Opening a 2 GB log in the editor is never the user's intent,
  and refusing clearly beats an out-of-memory crash.
- Progress is emitted at most every 250 ms rather than per chunk.

## Resume

Both directions resume, and both check that resuming is *correct* rather than just
possible:

- **Download** resumes only when the local partial file is genuinely a prefix of a
  longer remote file. If the remote shrank, the partial is stale and the transfer
  restarts.
- **Upload** resumes only when the remote is shorter than the source, and opens without
  `TRUNC` in that case. A fresh upload opens with `TRUNC` so a shorter replacement
  cannot leave the tail of the previous file.

`TransferEvent.Failed` carries `transferredBytes` so a retry resumes rather than
restarting.

## Operations

Browse, stat, create file, create directory, rename, delete (recursive), chmod, read
text, write text, upload, download.

- **Recursive delete is client-side**, not `rm -rf`. It works on hosts with a
  restricted shell or no shell at all, and each removal is individually reportable.
- **Create file uses `CREAT | EXCL`** so a race with another client fails rather than
  silently truncating a file created a moment earlier.
- **Write text uses `TRUNC`** so a shorter replacement does not leave the tail of the
  old file behind.
- **Ownership is exposed as numeric uid/gid.** Resolving those to names costs a round
  trip per entry against `/etc/passwd`, which would make a thousand-file listing
  unusable over a mobile connection.

## Errors

SFTP status codes are mapped to specific, actionable errors rather than a generic
failure:

| Status | Message |
|---|---|
| `NO_SUCH_FILE` | File not found — refresh; it may have been moved or deleted |
| `PERMISSION_DENIED` | Names the account and suggests checking owner and mode |
| `NO_SPACE_ON_FILESYSTEM` | Remote filesystem is full; retryable |
| `QUOTA_EXCEEDED` | Account quota is full |
| `FILE_ALREADY_EXISTS` | Suggests a different name |

## Not yet implemented

The centralised transfer manager (queue, pause, background continuation via
WorkManager, notifications) is Phase 8. The primitives it needs — resumable streaming
transfers with progress and a `transfer` table in Room — are in place.

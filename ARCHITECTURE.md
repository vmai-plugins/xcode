# Architecture

## Shape

Three layers, strictly one-directional:

```
:app            navigation graph, Application, adaptive shell
   |
   v
:feature:*      screens + ViewModels. Flat: no feature depends on another.
   |
   v
:core:*         domain, data, platform. Never depends on a feature.
```

The rule that keeps this honest: **features are wired together only by the app's
navigation graph**. `:feature:servers` knows nothing about `:feature:editor`; it
exposes composables with callback parameters, and `VmNavHost` decides what those
callbacks do. Any feature can be built and tested alone.

## Modules

| Module | Owns |
|---|---|
| `:core:common` | `VmResult`, `VmError`, logging + redaction, dispatchers, network monitor, user preferences |
| `:core:ui` | Theme tokens, typography, `Vm*` components. No domain knowledge. |
| `:core:security` | Keystore crypto, `Secret`, the encrypted credential store, secure clipboard |
| `:core:database` | Room entities, DAOs, database, startup reconciliation |
| `:core:network` | HTTP/SSE client, retry and backoff *(pending)* |
| `:core:ssh` | Server domain + repository; SSH connection engine *(engine pending)* |
| `:core:sftp` | Remote filesystem and transfers *(pending)* |
| `:core:terminal` | Terminal emulation and sessions *(pending)* |
| `:core:editor` | Editor abstraction and language services *(pending)* |
| `:core:git` | Git operations *(pending)* |
| `:core:project` | Project types, configuration, detection *(pending)* |
| `:core:workspace` | Workspace state across local/remote/connector *(pending)* |
| `:core:ai` | Provider abstraction, OmniRoute client *(pending)* |
| `:core:agent` | Planner, context engine, tool registry, execution loop *(pending)* |
| `:core:connectors` | Connector framework, Google Drive *(pending)* |
| `:feature:*` | One section of the UI each |

Directory `core/ui` maps to Gradle path `:core:ui`. The spec's `core-ui` naming is
expressed as directory nesting, which is the idiomatic Gradle layout.

## Build logic

Seven convention plugins in `build-logic/` own all shared Gradle configuration, so
SDK levels, Java/Kotlin targets and compiler flags cannot drift between 29 modules:

`vmstudio.android.application`, `.library`, `.compose`, `.hilt`, `.room`,
`.feature`, and `vmstudio.jvm.library`.

`vmstudio.android.feature` is the important one: applying it gives a module the
library + Compose + Hilt setup *and* dependencies on `:core:common` and `:core:ui`
and nothing else, which is what structurally prevents feature-to-feature coupling.

## Key decisions

### Errors are values, not exceptions

Every fallible operation returns `VmResult<T>`; failures carry a typed `VmError`
with `summary`, `reason`, `suggestedAction`, `retryable` and `details`. Library
exceptions from sshj, JGit or OkHttp are mapped at the module boundary while the
real cause is still known.

This is what makes "never show *Unknown error*" enforceable rather than aspirational:
`VmErrorPanel` takes a `VmError`, so there is no path that renders a lone string.
`retryable` also drives whether a Retry button appears at all — the app never invites
you to repeat an operation that cannot succeed, such as reconnecting to a host whose
key changed.

`vmCatching` rethrows `CancellationException` rather than capturing it, so structured
concurrency keeps working.

### Secrets never touch Room

Room stores `CredentialRef` ids. The ciphertext lives in `filesDir/credentials/`,
one file per secret, encrypted under a non-exportable Keystore key. A database dump
is not a credential leak. `ProjectEnvVarEntity` follows the same principle: it records
that `DATABASE_URL` exists, never its value. See [SECURITY.md](SECURITY.md).

### minSdk 26

JGit and the local workspace layer need `java.nio.file` and `java.time` without
desugaring caveats, both of which require API 26. Supporting API 24–25 would mean
shipping a degraded Git implementation, which the product cannot afford.

### No destructive migrations

`fallbackToDestructiveMigration` is deliberately absent. Losing a user's servers and
projects on an app upgrade is never acceptable; every schema change ships a real
migration, tested against the checked-in exported schema.

### Process death is a first-class state

An SSH channel, a streaming AI response and a running agent loop all end when the
process does, but their database rows do not. `StartupReconciler` fails interrupted
messages, cancels in-flight tool executions, marks interrupted tasks failed and
re-queues transfers from their resume offset. Without it the user returns to a task
that claims to be running while nothing is happening.

### No dynamic colour

Server environment badges, Git file status and diff backgrounds all carry meaning.
Letting the wallpaper repaint them would make the UI less legible, not more personal.

### The AI is an operator, not the environment

The Android app owns the workspace, files, SSH, terminal, Git, permissions and
execution. The agent reaches all of it through a tool registry with declared
permissions and confirmation requirements — never by manipulating app state directly.
This is what allows the safety layer to sit in one place and apply identically to
the user and to the agent.

## Data flow

```
Compose screen
   |  events
   v
ViewModel  ──  StateFlow<UiState>  ──>  screen
   |
   v
Repository (core)  ──  VmResult<T>
   |                          \
   v                           v
Room (non-secret)      SecureCredentialStore (Keystore)
```

ViewModels expose a single immutable `UiState` rather than several flows, so a screen
depending on multiple sources recomposes once and cannot observe a torn combination.
`stateIn(WhileSubscribed(5_000))` keeps flows alive briefly across configuration
changes so rotation does not re-query and flash an empty list.

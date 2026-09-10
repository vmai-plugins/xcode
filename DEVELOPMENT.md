# Development

## Setup

1. Install Android SDK 36 and a **JDK 17 or 21**.
2. Clone, then set the Gradle JVM (see [README](README.md#toolchain) — Android
   Studio's bundled JBR 25 will not work with Gradle 8.14.3).
3. `local.properties` is generated on first Android Studio sync; from the CLI it must
   contain `sdk.dir=<path>` using forward slashes.

```bash
./build.sh :app:assembleDebug        # build
./build.sh testDebugUnitTest         # all unit tests
./build.sh :core:ssh:testDebugUnitTest   # one module
```

`build.sh` only pins `JAVA_HOME` and delegates to `./gradlew`. Override with
`VMSTUDIO_JDK=/path/to/jdk`.

## Phase plan

Feature modules are added to `app/build.gradle.kts` only when their phase lands, so
the app never ships a screen with no working subsystem behind it.

| Phase | Scope | Status |
|---|---|---|
| 1 | Build, architecture, theme, database, security, server management | **Done** |
| 2 | SSH connection, SFTP, remote filesystem, terminal | **Done** |
| 3 | Project explorer, editor, tabs, search, file operations | Not started |
| 4 | Git workflow | Not started |
| 5 | AI provider abstraction + Claude Code CLI over SSH | **Done** |
| 5b | OmniRoute HTTP provider (chat only; dialect auto-detected) | **Done, untested against a live gateway** |
| 6 | Agent loop — largely obtained by delegating to the CLI; diff review outstanding | Mostly done |
| 7 | Connector framework, Google Drive | Not started |
| 8 | Tasks, activity, server dashboard, transfer manager, notifications | Not started |
| 9 | Security, performance, memory, network and UX audits | Not started |

Each phase ends with: compile, run tests, fix, verify. Do not build on top of a
broken phase.

## Dependency choices

Every version in `gradle/libs.versions.toml` was resolution-verified against Google
Maven / Maven Central before being pinned.

| Dependency | Chosen | Why |
|---|---|---|
| SSH/SFTP | **sshj 0.40.0** + BouncyCastle + eddsa | Actively maintained, works on Android, supports modern KEX and ed25519. JSch is unmaintained; Apache MINA SSHD is heavier and less Android-friendly. |
| Git | **JGit 6.10** | The only mature pure-Java Git implementation. Its `java.nio.file` use sets minSdk 26. |
| Editor | **sora-editor 0.23.6** | Maintained Android code editor with TextMate highlighting and an LSP module, which satisfies the "must allow future LSP integration" requirement without writing one. |
| HTTP/SSE | **OkHttp 4.12** + kotlinx.serialization | Retrofit adds little for a streaming, tool-calling API; direct OkHttp gives full control over SSE framing and cancellation. |
| Terminal | **custom** | Termux's emulator is not published to Maven Central in a usable form. `:core:terminal` implements a VT100/xterm subset. |
| Secure storage | **custom Keystore wrapper** | `androidx.security:security-crypto` is deprecated and offers less control over key properties than this app needs. |

## Conventions

- Public API returns `VmResult<T>`. Map library exceptions to `VmError` at the module
  boundary, where the real cause is still known.
- Never catch `CancellationException` — use `vmCatching`, which rethrows it.
- Comments explain *why*, not *what*. If a line needs a comment to say what it does,
  rename something instead.
- ViewModels expose one immutable `UiState`.
- No `!!` on nullable platform types; no blocking calls on the main thread.
- Source files are ASCII. Non-ASCII characters have been corrupted by tooling in this
  environment. Control characters are built from code points (`Char(0x1B)` for ESC,
  `Char(0)` for NUL), never written as literals. This is not stylistic: a literal
  control byte in source was silently mangled, and the resulting bug (a wipe routine
  filling a buffer with the wrong value) was invisible in review and only caught by a
  test. See `TerminalKeyEncoder` and `Secret` for the pattern.

### Gotchas hit while building this

- `field` is the backing-field keyword inside a property accessor and will not
  resolve to a constructor parameter of that name. `VmError.Validation` uses
  `fieldName`.
- Kotlin 2.2 warns about annotation targets on constructor parameters; the convention
  plugins pass `-Xannotation-default-target=param-property` so Hilt qualifiers behave
  as intended.
- Room converts enums automatically; no `TypeConverter` is needed for them.
- Exposing a `RoomDatabase` subclass across a module boundary requires `api`, not
  `implementation`. Prefer not to expose it at all — `StartupReconciler` exists so
  `:app` never sees Room.
- `ExperimentalFoundationApi` lives in `androidx.compose.foundation`, not in
  `material3`; the IDE will happily import the wrong one.
- sshj's `loadKeys(privateKey, publicKey, passwordFinder)` treats the first argument as
  key *content* only when the second is null. With a non-null second argument it treats
  both as file paths.
- A Kotlin `Regex` cannot reliably match command-line flags. Parse them. See
  `CommandSafety.analyseRemove` and the two bugs recorded in
  [TESTING.md](TESTING.md#bugs-these-tests-caught).

## Adding a feature module

1. `include(":feature:x")` in `settings.gradle.kts`.
2. `feature/x/build.gradle.kts` applying `id("vmstudio.android.feature")` and a
   `namespace`.
3. Add `core` dependencies it needs. **Never** depend on another feature.
4. Expose composables taking callbacks; wire them in `VmNavHost`.
5. Add the module to `app/build.gradle.kts` when it does something real.

## Continuous integration

`.github/workflows/build.yml` runs on every push and pull request:

| Job | What it does |
|---|---|
| **Build and test** | Unit tests, debug assemble, **release assemble**, Android Lint |
| **Static analysis** | Detekt against `detekt.yml` |
| **Secret scan** | Fails if a keystore, `keystore.properties`, `local.properties` or a PEM private key is tracked |

The release variant is built on **every** run, not only on tags. That is deliberate:
R8 shrinking and `lintVital` execute only in the release build, and the pipeline that
would have caught the missing eddsa keep rule is precisely one that builds it. A
debug-only CI would have stayed green while the shippable artefact did not compile.

CI pins **JDK 17**. See the toolchain note above for why the newest JDK is wrong here.

## Release signing

The keystore and its passwords never enter the repository. `app/build.gradle.kts`
reads them from the environment first, then from a gitignored `keystore.properties`:

| Property | Environment variable |
|---|---|
| `storeFile` | `VMSTUDIO_KEYSTORE_FILE` |
| `storePassword` | `VMSTUDIO_KEYSTORE_PASSWORD` |
| `keyAlias` | `VMSTUDIO_KEY_ALIAS` |
| `keyPassword` | `VMSTUDIO_KEY_PASSWORD` |

When none are set the release build still completes, unsigned. That keeps the
R8-and-lint value of the release build available to anyone without the keystore,
which is the part that actually breaks.

To create a keystore (do this yourself; never paste the password into a tool or a
chat):

```bash
keytool -genkey -v -keystore vmstudio-release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias vmstudio
```

Then either write `keystore.properties` in the project root, or set the four
environment variables. For CI, add them as repository secrets named
`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.

**Back the keystore up.** Losing it means never being able to update the app under
the same package name on Play.

## Static analysis

```bash
./build.sh detekt            # check
./build.sh detektBaseline    # re-accept current findings
```

`detekt.yml` is tuned rather than adopted wholesale — a gate that cries wolf gets
switched off within a week. The rules kept are the ones that would indicate a real
defect here: coroutine misuse, swallowed exceptions, unsafe casts, unused members,
and `TODO`/`FIXME` comments, which in this codebase are how "built but not wired"
starts.

`detekt-baseline.xml` accepts 139 pre-existing findings, 109 of them `MagicNumber` in
the terminal emulator where the numbers *are* the ANSI specification. New code is held
to the full ruleset.

## Release checklist

Not yet ready to ship. Outstanding:

- The app has never been run on a device or against a real server.
- No instrumented tests.
- No privacy policy or Play data-safety declaration, both required for a listing
  by an app that stores credentials.
- Strings are hardcoded in Kotlin; there is no localisation.

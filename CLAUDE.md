# Project notes for Claude Code sessions

This file is read automatically at the start of every Claude Code session in
this repo. It exists so operational context (which server does what) does
not need to be re-explained every session.

**Never commit secrets here** (passwords, SSH keys, API tokens, Personal
Access Tokens). Only non-secret facts: hostnames/IPs, purpose, panel type,
paths, installed tool versions. When a session needs a secret, the user
pastes it directly into that session's terminal; it is never written to
this file or any other file in the repo.

## Infrastructure

### VPS 1 - websites
- IP: `31.97.63.239`
- Purpose: hosts the user's websites, including the WordPress site at
  `vmstudio.digital` (docroot `/home/vmstudio.digital/public_html`).
- Panel: CyberPanel (OpenLiteSpeed).
- App-hub / APK distribution: `/home/vmstudio.digital/public_html/apps/`.
  The xcode app's APK lives at
  `/home/vmstudio.digital/public_html/apps/x-codes/xcodes-latest.apk`,
  served publicly at `https://vmstudio.digital/apps/x-codes/xcodes-latest.apk`.
  Existing naming convention in that directory: `xcodes-latest.apk` (current),
  `xcodes-latest.prev.apk` (previous, kept as a rollback copy), `xcodes-v2.apk`
  (an older tagged build). File ownership on that host is `vmstu1627:vmstu1627`.
- No git, no build tooling here - this is a deployment target only, not a
  build machine.

### VPS 2 - AI infrastructure / Android build environment
- IP: `200.234.41.231`
- Purpose: AI infrastructure (OmniRoute gateway lives at
  `https://ai.vmstudio.digital`) and the intended Android build environment
  for this repo (JDK, Android SDK, and the Claude Code CLI have previously
  been found already configured here - verify current versions with
  `java -version`, `sdkmanager --list_installed`, `claude --version` at the
  start of a build session rather than assuming they are still current).
- Panel: CloudPanel (`https://200.234.41.231:8443`).
- Known recurring issue: `~/.bashrc` has previously had a malformed
  `export KAGGLE_API_TOKEN="..."` line (unclosed quote, trailing backslash)
  that breaks every new interactive shell with
  `unexpected EOF while looking for matching` and blocks anything sourcing
  `.bashrc`. If a shell on this host throws that error on login, run
  `sed -i '/^export KAGGLE_API_TOKEN="/d' ~/.bashrc && bash -n ~/.bashrc`
  to remove the broken line, then verify with `bash -n ~/.bashrc` (no output
  = syntax OK). The user should also rotate that Kaggle token since it was
  exposed in a plaintext shell config.
- This repo has previously been cloned to `~/xcode` on this host for builds.
  Cloning a private GitHub repo over HTTPS requires a Personal Access Token
  as the password (GitHub no longer accepts account passwords for git
  operations) - a **classic** token (Settings -> Developer settings ->
  Personal access tokens -> Tokens (classic), `repo` scope) is more reliable
  here than a fine-grained token, since a fine-grained token must have this
  specific repo explicitly granted or GitHub will silently re-prompt for
  credentials instead of giving a clear permission error. Run
  `git config --global credential.helper store` once on this host so the
  token is cached after the first successful clone and never needs
  re-entering.
- Build command once cloned: `./gradlew :app:assembleDebug --no-daemon`
  (from `~/xcode`). Output: `~/xcode/app/build/outputs/apk/debug/app-debug.apk`.

## Build limitations in the Claude Code cloud sandbox

Claude Code sessions running in this project's own cloud sandbox (not VPS 2)
cannot build this app: outbound network access there is HTTPS-CONNECT-proxy
only with a domain allowlist that does not include `dl.google.com`, so the
Android Gradle Plugin cannot be resolved (`./gradlew` fails immediately on
the `com.android.application` plugin). Raw TCP (e.g. SSH to port 22 on
either VPS) is also not possible from that sandbox - connections there time
out rather than being rejected, because the proxy only ever forwards HTTPS,
never raw sockets. Building requires either VPS 2 or the user's own machine;
it cannot be done inside the sandbox session itself.

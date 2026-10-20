#!/usr/bin/env bash
# Installs the shrunk release build (the "smoke" variant) on the running emulator,
# starts it and fails if it crashes. R8 keep-rule mistakes compile fine and only
# crash at runtime, so this is the one check that catches them before a user does.
set -euo pipefail

APK=app/build/outputs/apk/smoke/app-smoke.apk
PKG=digital.vmstudio.code.smoke

adb install -r "$APK"
adb logcat -c
adb shell am start -W -n "$PKG/digital.vmstudio.code.MainActivity"

# Long enough for Hilt, Room, DataStore and the first Compose frames to run.
sleep 20

crash=$(adb logcat -d -b crash || true)
if ! adb shell pidof "$PKG" > /dev/null || [ -n "$crash" ]; then
  echo "::error::The release build crashed on launch"
  echo "$crash"
  adb logcat -d -t 400 | grep -E "AndroidRuntime|FATAL|$PKG" || true
  exit 1
fi
echo "Release build started and is still running"

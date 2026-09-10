#!/usr/bin/env bash
# Convenience wrapper: pins the Gradle JVM to a JDK the toolchain supports.
# See DEVELOPMENT.md for why Android Studio's bundled JBR 25 is not usable here.
set -euo pipefail

# Resolution order:
#   1. VMSTUDIO_JDK — explicit override.
#   2. A JDK 17 auto-provisioned into Gradle's toolchain cache (~/.gradle/jdks).
#   3. JAVA_HOME if already exported by the caller.
#   4. Nothing — let the toolchain resolve it.
if [ -n "${VMSTUDIO_JDK:-}" ]; then
    export JAVA_HOME="${VMSTUDIO_JDK}"
elif [ -z "${JAVA_HOME:-}" ]; then
    for candidate in "$HOME/.gradle/jdks"/*/; do
        if [ -d "$candidate" ] && [ -x "$candidate/bin/java" ]; then
            export JAVA_HOME="$candidate"
            break
        fi
    done
fi

exec ./gradlew "$@"

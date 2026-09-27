#!/usr/bin/env bash
# VMStudio Autopilot APK Builder & Deployer
# Runs on VPS 2 (200.234.41.231) to compile Android APKs and publish to vmstudio.digital/apps/
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
DEPLOY_HOST="${DEPLOY_HOST:-root@31.97.63.239}"
APPS_DIR="/home/vmstudio.digital/public_html/apps"
TARGET_DIR="${APPS_DIR}/x-codes"
BUILD_CACHE="/opt/vmstudio/builds/apk"

# Resolve JDK 17 / 21
if [ -z "${JAVA_HOME:-}" ]; then
    for candidate in /usr/lib/jvm/java-17-openjdk* /usr/lib/jvm/java-21-openjdk* /opt/java/openjdk*; do
        if [ -d "$candidate" ] && [ -x "$candidate/bin/java" ]; then
            export JAVA_HOME="$candidate"
            break
        fi
    done
fi

# Resolve Android SDK
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

if [ -n "${JAVA_HOME:-}" ]; then
    export PATH="${JAVA_HOME}/bin:${PATH}"
fi
if [ -d "${ANDROID_HOME}/cmdline-tools/latest/bin" ]; then
    export PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"
fi

cd "$REPO_DIR"

echo "==> [1/4] Checking repository state..."
if git rev-parse --git-dir > /dev/null 2>&1; then
    git fetch origin main || true
    LOCAL_HASH=$(git rev-parse HEAD 2>/dev/null || echo "none")
    REMOTE_HASH=$(git rev-parse origin/main 2>/dev/null || echo "none")

    if [ "$LOCAL_HASH" = "$REMOTE_HASH" ] && [ "${1:-}" != "--force" ]; then
        echo "Already up-to-date ($LOCAL_HASH). Build skipped. Pass --force to rebuild."
        exit 0
    fi
    git reset --hard origin/main || true
fi

echo "==> [2/4] Compiling APK using Gradle..."
chmod +x ./gradlew
./gradlew :app:assembleDebug --no-daemon

APK_SRC="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_SRC" ]; then
    echo "ERROR: APK artifact not found at $APK_SRC"
    exit 1
fi

echo "==> [3/4] Staging build artifact in $BUILD_CACHE..."
mkdir -p "$BUILD_CACHE"
cp -f "$APK_SRC" "${BUILD_CACHE}/xcodes-latest.apk"

echo "==> [4/4] Deploying to VPS 1 (${DEPLOY_HOST}:${TARGET_DIR})..."
if ssh -o BatchMode=yes -o ConnectTimeout=5 "$DEPLOY_HOST" "test -d $APPS_DIR" 2>/dev/null; then
    ssh "$DEPLOY_HOST" "mkdir -p $TARGET_DIR"
    scp -q "$APK_SRC" "${DEPLOY_HOST}:${TARGET_DIR}/.xcodes-latest.uploading.apk"
    ssh "$DEPLOY_HOST" "cd $TARGET_DIR && cp -p xcodes-latest.apk xcodes-latest.prev.apk 2>/dev/null || true; mv -f .xcodes-latest.uploading.apk xcodes-latest.apk && chown -R vmstu1627:vmstu1627 xcodes-latest.apk 2>/dev/null || true"
    echo ""
    echo "=========================================================="
    echo " SUCCESS: APK Deployed to Production!"
    echo " Direct download: https://vmstudio.digital/apps/x-codes/xcodes-latest.apk"
    echo " Apps Hub:        https://vmstudio.digital/apps/"
    echo "=========================================================="
else
    echo ""
    echo "=========================================================="
    echo " APK built successfully and saved locally to:"
    echo "   ${BUILD_CACHE}/xcodes-latest.apk"
    echo " Note: SSH key to $DEPLOY_HOST is not yet trusted without password."
    echo " Run 'ssh-copy-id $DEPLOY_HOST' on VPS 2 to enable auto-deploy."
    echo "=========================================================="
fi

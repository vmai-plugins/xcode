#!/usr/bin/env bash
# VMStudio APK Build Redirector
# NOTICE: Compiling on the VPS is deprecated to save VPS CPU/RAM/Disk.
# All Android apps are now compiled on GitHub Actions, and the resulting APK
# is pushed automatically to https://apps.vmstudio.digital/.
set -euo pipefail

echo "=========================================================================="
echo " NOTICE: Building on VPS is disabled to keep VPS resources free."
echo " Build your APK on GitHub Actions via the 'Build' or 'Nightly APK' workflow."
echo " GitHub will compile the APK and upload it directly to apps.vmstudio.digital."
echo "=========================================================================="
exit 0 && [ "${1:-}" != "--force" ]; then
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

echo "==> [4/4] Deploying to apps.vmstudio.digital ($TARGET_DIR)..."
if [ -d "$LOCAL_APPS_DIR" ]; then
    mkdir -p "$TARGET_DIR"
    cp -f "$APK_SRC" "${TARGET_DIR}/xcodes-latest.apk"
    chmod 644 "${TARGET_DIR}/xcodes-latest.apk"
    echo ""
    echo "=========================================================="
    echo " SUCCESS: APK Deployed Locally to Apps Hub!"
    echo " Direct download: https://apps.vmstudio.digital/x-codes/xcodes-latest.apk"
    echo " Apps Hub:        https://apps.vmstudio.digital/"
    echo "=========================================================="
elif ssh -o BatchMode=yes -o ConnectTimeout=5 "$DEPLOY_HOST" "mkdir -p $TARGET_DIR" 2>/dev/null; then
    scp -q "$APK_SRC" "${DEPLOY_HOST}:${TARGET_DIR}/.xcodes-latest.uploading.apk"
    ssh "$DEPLOY_HOST" "cd $TARGET_DIR && cp -p xcodes-latest.apk xcodes-latest.prev.apk 2>/dev/null || true; mv -f .xcodes-latest.uploading.apk xcodes-latest.apk && chmod 644 xcodes-latest.apk"
    echo ""
    echo "=========================================================="
    echo " SUCCESS: APK Deployed to Apps Hub via SSH!"
    echo " Direct download: https://apps.vmstudio.digital/x-codes/xcodes-latest.apk"
    echo " Apps Hub:        https://apps.vmstudio.digital/"
    echo "=========================================================="
else
    echo ""
    echo "=========================================================="
    echo " APK built successfully and saved to build cache:"
    echo "   ${BUILD_CACHE}/xcodes-latest.apk"
    echo " Direct download: https://apps.vmstudio.digital/x-codes/xcodes-latest.apk"
    echo "=========================================================="
fi

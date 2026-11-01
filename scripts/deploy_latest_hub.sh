#!/usr/bin/env bash
set -euo pipefail

SRC="/tmp/xcodes-v0.15.28.apk"
if [ ! -f "$SRC" ]; then
    echo "ERROR: $SRC not found"
    exit 1
fi

SIZE=$(du -h "$SRC" | awk '{print $1}')
echo "Staging APK ($SIZE)..."

# 1. Apps Hub directory
APPS_DIR="/home/vmstudio-apps/htdocs/apps.vmstudio.digital"
mkdir -p "$APPS_DIR/x-codes"
cp -f "$SRC" "$APPS_DIR/x-codes/xcodes-latest.apk"
cp -f "$SRC" "$APPS_DIR/x-codes/x-codes-latest.apk"
cp -f "$SRC" "$APPS_DIR/x-codes/xcodes-0.15.28.apk"
cp -f "$SRC" "$APPS_DIR/x-codes-latest.apk"
cp -f "$SRC" "$APPS_DIR/x-codes-v0.15.28.apk"
chown -R vmstudio-apps:vmstudio-apps "$APPS_DIR/x-codes" "$APPS_DIR/x-codes-latest.apk" "$APPS_DIR/x-codes-v0.15.28.apk"
chmod 644 "$APPS_DIR/x-codes"/*.apk "$APPS_DIR/x-codes-latest.apk" "$APPS_DIR/x-codes-v0.15.28.apk"

# 2. Main vmstudio.digital/apps directory
VM_APPS="/home/vmstudio/htdocs/vmstudio.digital/apps"
mkdir -p "$VM_APPS/x-codes"
cp -f "$SRC" "$VM_APPS/x-codes-latest.apk"
cp -f "$SRC" "$VM_APPS/xcodes-latest.apk"
cp -f "$SRC" "$VM_APPS/xcodes-0.15.28.apk"
cp -f "$SRC" "$VM_APPS/x-codes/xcodes-latest.apk"
cp -f "$SRC" "$VM_APPS/x-codes/x-codes-latest.apk"
cp -f "$SRC" "$VM_APPS/x-codes/xcodes-0.15.28.apk"
chown -R vmstudio:vmstudio "$VM_APPS/x-codes" "$VM_APPS/x-codes-latest.apk" "$VM_APPS/xcodes-latest.apk" "$VM_APPS/xcodes-0.15.28.apk"
chmod 644 "$VM_APPS/x-codes"/*.apk "$VM_APPS/x-codes-latest.apk" "$VM_APPS/xcodes-latest.apk" "$VM_APPS/xcodes-0.15.28.apk"

# 3. Update apps.vmstudio.digital/index.html
INDEX="$APPS_DIR/index.html"
if [ -f "$INDEX" ]; then
    sed -i 's/Version: v0\.[0-9]*\.[0-9]*/Version: v0.15.28/g' "$INDEX"
    sed -i 's/Download x-codes APK (v0\.[0-9]*\.[0-9]*)/Download x-codes APK (v0.15.28)/g' "$INDEX"
    sed -i "s/Size: [0-9.]* MB/Size: $SIZE/g" "$INDEX"
    chown vmstudio-apps:vmstudio-apps "$INDEX"
    chmod 644 "$INDEX"
    echo "apps.vmstudio.digital index.html updated."
fi

rm -f "$SRC"
echo "Deployment successful: v0.15.28 ($SIZE)"

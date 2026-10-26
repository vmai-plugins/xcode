#!/usr/bin/env bash
# One-touch script to clean Android SDK, Gradle caches, and intermediate build tools from VPS 2 (200.234.41.231).
# Run this either directly on the VPS or from any machine with SSH access to the VPS.
set -euo pipefail

DEPLOY_HOST="${DEPLOY_HOST:-root@200.234.41.231}"

CLEAN_COMMANDS='
echo "=========================================================================="
echo " Starting VPS Cleanup: Android SDK, Gradle Caches & Build Tools"
echo "=========================================================================="

echo "--> [1/4] Current Disk Usage:"
df -h /
echo ""

echo "--> [2/4] Removing Android SDK, Gradle caches, and build artifacts..."
rm -rf /opt/android-sdk \
       /root/.gradle \
       /home/*/.gradle \
       /opt/vmstudio/builds \
       /tmp/gradle* \
       /tmp/hsperfdata_* 2>/dev/null || true

echo "--> [3/4] Cleaning apt package caches..."
apt-get autoremove -y 2>/dev/null || true
apt-get clean 2>/dev/null || true

echo "--> [4/4] VPS Cleanup Complete! New Disk Usage:"
df -h /
echo "=========================================================================="
'

if [ -d "/home/vmstudio-apps" ] || [ "$(hostname 2>/dev/null)" = "vmstudio" ]; then
    # Running directly on the VPS
    eval "$CLEAN_COMMANDS"
else
    # Running remotely via SSH
    echo "Connecting to $DEPLOY_HOST to execute cleanup..."
    ssh -o StrictHostKeyChecking=accept-new "$DEPLOY_HOST" "$CLEAN_COMMANDS"
fi

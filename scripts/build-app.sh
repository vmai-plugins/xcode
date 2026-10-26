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
exit 0

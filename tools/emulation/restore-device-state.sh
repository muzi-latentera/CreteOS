#!/bin/bash
set -e
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
TRANSFER_DIR="${1:-}"
DEVICE="${2:-}"
if [ -n "$DEVICE" ]; then ADB="$ADB -s $DEVICE"; fi

if [ -z "$TRANSFER_DIR" ]; then
  echo "Usage: restore-device-state.sh <transfer-dir> [device-serial]"
  echo "Example: restore-device-state.sh ~/Desktop/creteos-transfer-20260828-120000"
  exit 1
fi

echo "=== CreteOS Device Restore ==="
echo "Target: $($ADB shell getprop ro.product.model)"
echo "Android: $($ADB shell getprop ro.build.version.release)"
echo "GPU: $($ADB shell getprop ro.hardware.egl) ($($ADB shell getprop ro.board.platform))"
echo ""

# Find latest CreteOS APK
APK=$(ls "$TRANSFER_DIR"/../app/build/outputs/apk/full/debug/app-full-debug.apk 2>/dev/null || \
     find . -name "app-full-debug.apk" | head -1)
if [ -n "$APK" ]; then
  echo "Installing CreteOS: $APK"
  $ADB install -r "$APK"
else
  echo "WARNING: No CreteOS APK found — install manually"
fi

# Push portable storage
if [ -d "$TRANSFER_DIR/CreteOS" ]; then
  echo ""
  echo "Pushing CreteOS/Emulation/ folder..."
  $ADB push "$TRANSFER_DIR/CreteOS/" /storage/emulated/0/CreteOS/
  echo "Done."
else
  echo "WARNING: No CreteOS folder found in $TRANSFER_DIR"
fi

echo ""
echo "======================================"
echo " MANUAL STEPS REQUIRED ON DEVICE"
echo "======================================"
echo ""
echo "1. Open Obtainium → import RJNY Emulation Pack"
echo "   URL: https://github.com/RJNY/Obtainium-Emulation-Pack"
echo "   Version to pin: v7.16.0"
echo ""
echo "2. For each emulator — import your exported data:"
echo "   RetroArch:   Settings → Directory → System/BIOS → point to CreteOS/Emulation/BIOS"
echo "   Dolphin:     Settings → Config → Import User Data → pick Exports/Dolphin/"
echo "   NetherSX2:   App menu → Transfer Data → Import → pick Exports/NetherSX2/"
echo "   DuckStation: Settings → Transfer Data → Import → pick Exports/DuckStation/"
echo "   PPSSPP:      Settings → System → PSP Storage folder → point to Emulation/ROMs/psp"
echo "   Eden:        Settings → Data Storage → point to Emulation/"
echo ""
echo "3. Re-grant ROM folder permissions in each emulator:"
echo "   Each emulator will prompt — select Allow for CreteOS/Emulation/ROMs/<system>"
echo ""
echo "4. Map Pocket Fit controller in each emulator"
echo "   DO NOT copy controller mappings from old device"
echo ""
echo "5. Open CreteOS → Settings → Integrations → Emulation → Scan ROM Root"
echo "   Select: CreteOS/Emulation/ROMs"
echo ""
echo "6. Verify one game per emulator launches directly from CreteOS"
echo ""
echo "7. ONLY AFTER step 6 — test GPU drivers in Eden:"
echo "   Settings → GPU Driver → Install → pick Turnip Gen8 V34 from Drivers/"
echo "   Smoke test one Switch game"
echo "   If regression: test V32 instead"
echo ""
echo "DO NOT: copy shader caches, copy thermal profiles, root the device"

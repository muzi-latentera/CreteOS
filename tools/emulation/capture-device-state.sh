#!/bin/bash
set -e
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
DEVICE="${1:-}"  # optional: -s <serial>
OUT="$HOME/Desktop/creteos-transfer-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"

# Detect connected device
if [ -n "$DEVICE" ]; then ADB="$ADB -s $DEVICE"; fi

echo "=== CreteOS Device State Capture ==="
echo "Device: $($ADB shell getprop ro.product.model)"
echo "Android: $($ADB shell getprop ro.build.version.release)"
echo "GPU: $($ADB shell getprop ro.hardware.egl)"
echo ""

# Record installed emulator package info
PACKAGES=("com.retroarch.aarch64" "org.dolphinemu.dolphinemu" "org.ppsspp.ppsspp" 
  "xyz.trixarian.nethersx2" "com.github.stenzek.duckstation" "me.magnum.melonds"
  "org.azahar_emu.azahar" "dev.eden_emu.eden" "org.vita3k.emulator" 
  "com.ps3native.emulator" "info.cemu.Cemu" "io.latent.creteos")

echo "=== Installed Emulators ===" | tee "$OUT/manifest.txt"
for pkg in "${PACKAGES[@]}"; do
  version=$($ADB shell pm dump "$pkg" 2>/dev/null | grep versionName | head -1 | awk '{print $1}')
  if [ -n "$version" ]; then
    echo "  INSTALLED $pkg $version" | tee -a "$OUT/manifest.txt"
  else
    echo "  NOT_FOUND $pkg" | tee -a "$OUT/manifest.txt"
  fi
done

# Pull portable CreteOS/Emulation folder (ROMs/BIOS/Exports/Drivers/Saves/Configs)
echo ""
echo "=== Pulling CreteOS/Emulation/ from device ==="
$ADB pull /storage/emulated/0/CreteOS/ "$OUT/CreteOS/" 2>&1 || echo "WARNING: CreteOS folder not found on device"

echo ""
echo "=== Capture complete ==="
echo "Output: $OUT"
echo ""
echo "MANUAL STEPS STILL REQUIRED:"
echo "  1. Export user data from each emulator (see docs/EMULATOR_MIGRATION_MATRIX.md)"
echo "  2. Copy exports to $OUT/CreteOS/Emulation/Exports/"
echo "  3. SAF folder permissions DO NOT transfer — must re-grant on new device"
echo "  4. Controller mappings DO NOT transfer — must remap on new device"
echo "  5. Shader caches NOT captured — regenerate on new device"

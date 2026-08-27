# KONKR Pocket Fit 8 Elite — First Boot Guide

> **Device:** KONKR Pocket Fit 8 Elite  
> **Expected SoC:** Snapdragon 8 Elite  
> **Expected GPU:** Adreno 830  
> **Created:** 2026-08-27

---

## ⚠️ CRITICAL WARNINGS

**Read these before starting:**

- **Do NOT copy shader caches from Fold 8** — They are GPU-specific and will cause rendering issues or crashes on Adreno 830
- **Do NOT assume controller mappings transfer** — Device IDs differ; Pocket Fit physical ABXY layout requires remapping in every emulator
- **Do NOT install Turnip drivers system-wide** — Only install inside emulators via AdrenoTools. No root required, no system modifications.
- **SAF folder permissions must be re-granted on each emulator** — This is expected Android behavior and takes ~2 minutes total
- **If an emulator shows wrong package/activity, run:**
  ```bash
  adb shell dumpsys package <packagename> | grep -A2 'android.intent.action.MAIN'
  ```

---

## 1. BEFORE YOU START

### Verify GPU Hardware

After initial Android setup, connect via ADB and verify the GPU:

```bash
# Check EGL implementation (should show Adreno)
adb shell getprop ro.hardware.egl

# Check board platform (should show Snapdragon 8 Elite identifier)
adb shell getprop ro.board.platform

# Additional GPU info
adb shell dumpsys SurfaceFlinger | grep -i "GLES"
```

**Expected results:**
- `ro.hardware.egl` → `adreno`
- `ro.board.platform` → platform identifier for Snapdragon 8 Elite
- GLES should show Adreno 830

**If GPU is not Adreno 830, STOP** — Turnip Gen8 drivers are specifically for Adreno 8xx Elite series.

---

## 2. Android Initial Setup

1. Power on Pocket Fit
2. Complete Android setup wizard
3. **Skip Google account transfer** — do NOT restore from Fold 8 backup
4. Connect to WiFi
5. **Run Android system updates first** — Settings → System → Software update
6. Reboot after updates complete

---

## 3. Enable Developer Options & USB Debugging

1. Settings → About device
2. Tap "Build number" 7 times
3. Settings → Developer options → Enable "USB debugging"
4. Connect USB cable to computer
5. Authorize debugging when prompted on device
6. Verify connection:
   ```bash
   adb devices
   # Should show device ID with "device" status
   ```

---

## 4. Install Obtainium

Obtainium manages APK updates from GitHub releases and other sources.

**Version:** v1.6.13  
**Package:** dev.imranr.obtainium

```bash
# Download from GitHub releases
# https://github.com/ImranR98/Obtainium/releases/tag/v1.6.13

# Install via ADB
adb install Obtainium-v1.6.13.apk
```

Or download directly on device from GitHub.

---

## 5. Import RJNY Emulation Pack

The RJNY pack provides Obtainium configurations for the emulator stack.

**Version:** v7.16.0

1. Launch Obtainium on Pocket Fit
2. Settings → Import/Export → Import
3. Import RJNY Emulation Pack v7.16.0 JSON
4. Let Obtainium download and install all emulators:
   - RetroArch v1.22.2 (pkg: com.retroarch.aarch64)
   - Dolphin 2603a hotfix (pkg: org.dolphinemu.dolphinemu) — from dolphin-emu.org
   - PPSSPP v1.20.4 (pkg: org.ppsspp.ppsspp) — from ppsspp.org
   - melonDS Android 2.0.1 (pkg: me.magnum.melonds)
   - Azahar 2126.0-vanilla (pkg: org.azahar_emu.azahar)
   - NetherSX2 v2.1-4248 STABLE (pkg: xyz.aethersx2.android)
   - Eden v0.2.1 standard (pkg: dev.eden.eden_emulator)
   - DuckStation latest (pkg: com.github.stenzek.duckstation) — Google Play only
   - Vita3K Android v12 (pkg: org.vita3k.emulator) — EXPERIMENTAL
   - Cemu Android 0.5.2 (pkg: info.cemu.cemu) — SapphireRhodonite fork
   - *(Skip PS3Native v0.2.1 for now — EXPERIMENTAL)*

5. Wait for all installs to complete

---

## 6. Install CreteOS APK

```bash
# From computer, install CreteOS
adb install CreteOS-release.apk

# Verify installation
adb shell pm list packages | grep crete
```

---

## 7. Run Seed Script

The seed script pushes ROM folders and emulator exports to the device.

```bash
# From CreteOS project directory
cd /path/to/CreteOS
python3 tools/emulation/restore-device-state.sh

# This script:
# - Pushes Emulation/ folder structure to device
# - Pushes exported user data to Exports/
# - Does NOT push shader caches (intentionally)
```

---

## 8. Configure Each Emulator

For each emulator, complete initial setup:

### RetroArch
1. Launch RetroArch
2. Settings → Directory → Configure paths for:
   - System/BIOS
   - Saves
   - States
   - Screenshots
3. Main Menu → Load Content → navigate to ROM folder
4. Grant SAF permission when prompted
5. Import settings from `Exports/RetroArch/`

### Dolphin
1. Launch Dolphin
2. Settings → Config → Import User Data
3. Select exported ZIP from `Exports/Dolphin/`
4. Grant SAF permission for ROM folders
5. Verify GC/Wii games appear

### PPSSPP
1. Launch PPSSPP
2. Settings → System → PSP folder → select `CreteOS/Emulation/ROMs/psp`
3. Grant SAF permission
4. Verify games appear

**Storage note:** PPSSPP storage folder = shared. Point to `CreteOS/Emulation/ROMs/psp` for ROMs.

### NetherSX2
**Version:** v2.1-4248 STABLE (NOT 2.2n development build)

1. Launch NetherSX2
2. Settings → Transfer Data → Import
3. Select export from `Exports/NetherSX2/`
4. Grant SAF permissions for BIOS and ROM folders
5. Verify BIOS detected and games listed

### DuckStation
1. Launch DuckStation
2. Settings → Transfer Data → Import
3. Grant SAF permissions
4. Verify BIOS and games

### melonDS
1. Launch melonDS
2. Grant folder permissions when prompted
3. Verify BIOS files detected
4. Test loading a game

### Azahar
**Version:** 2126.0-vanilla

1. Launch Azahar
2. Grant folder permissions
3. Configure ROM folder
4. **Remap controller** — physical ABXY will differ from Fold 8

### Eden
**Package:** dev.eden.eden_emulator (NOT dev.eden_emu.eden)

1. Launch Eden
2. Settings → Verify keys/ and firmware/ are detected
3. Grant SAF permissions for game folder
4. **DO NOT install GPU drivers yet** — test stock first

### Cemu Android
**Package:** info.cemu.cemu (lowercase, NOT info.cemu.Cemu)

1. Launch Cemu
2. Grant folder permissions
3. Select game folder
4. **Remap controller** for Pocket Fit layout

### Vita3K
**Status:** EXPERIMENTAL

1. Launch Vita3K
2. **Fresh install required** — data not portable
3. Install games from PKG/ZIP files
4. This will take longer than other emulators

---

## 9. Map Pocket Fit Controller

**Important:** Physical ABXY button layout on Pocket Fit differs from Fold 8 touch controls.

For each emulator:
1. Open controller/input settings
2. Remap all buttons
3. Test in-game to verify

Estimated time: 10-15 minutes total

---

## 10. Launch CreteOS & Scan Library

1. Launch CreteOS app
2. Settings → ROM Directories → add Emulation root folder
3. Trigger library scan
4. Verify all games appear with correct emulator associations
5. Check cover art loaded (if configured)

---

## 11. Test Direct Launch

Test one game per emulator from CreteOS:

| System | Test game | Emulator | Package |
|--------|-----------|----------|---------|
| SNES | Any game | RetroArch | com.retroarch.aarch64 |
| GameCube | Any game | Dolphin | org.dolphinemu.dolphinemu |
| PSP | Any game | PPSSPP | org.ppsspp.ppsspp |
| DS | Any game | melonDS | me.magnum.melonds |
| 3DS | Any game | Azahar | org.azahar_emu.azahar |
| PS2 | Any game | NetherSX2 | xyz.aethersx2.android |
| PS1 | Any game | DuckStation | com.github.stenzek.duckstation |
| Switch | Any game | Eden | dev.eden.eden_emulator |
| Wii U | Any game | Cemu | info.cemu.cemu |

**If launch fails:**
1. Check logcat for errors:
   ```bash
   adb logcat | grep -i "ActivityManager\|Intent"
   ```
2. Verify package/activity names match installed APK
3. See `docs/EMULATOR_LAUNCH_CONTRACTS.md` for intent details

---

## 12. GPU Drivers (ONLY AFTER STEP 11)

**Prerequisites:**
- All stable emulators confirmed working with stock drivers
- At least one Switch game tested in Eden without crashes

### ⚠️ CRITICAL: Use Gen8 Drivers ONLY

The Pocket Fit 8 Elite has an **Adreno 830** GPU. You MUST use Gen8 drivers.

**⚠️ WARNING: v26.3.0-R4 is NOT the A830 driver** — that's a generic Mesa driver for A7xx/A6xx GPUs. DO NOT USE on Pocket Fit.

### Available Gen8 Drivers

| Driver | SHA256 | Purpose |
|--------|--------|---------|
| **Turnip Gen8 V35** | `0b9e43f5a3ae4726f333fbffaa165f1c4a20e74d129285e675d952b46cbffcf2` | PRIMARY for A830 |
| **Turnip Gen8 V32** | `a31e5a5106ec7c858e2b6136a3ad35126b52af8df2cb7e126bac88a610cad5dd` | FALLBACK (proven in video) |

**Driver location on device:** `/storage/emulated/0/CreteOS/Emulation/Drivers/`

### Install Turnip Gen8 V35 (Primary)

1. Launch Eden
2. Settings → GPU Driver → Install
3. Select `Turnip_Gen8_V35.zip`
4. Restart Eden
5. Launch a graphically demanding Switch game
6. Monitor for:
   - Visual glitches
   - Performance regressions
   - Crashes

### If Issues with V35, Fall Back to V32

1. Settings → GPU Driver → Install
2. Select `Turnip_Gen8_V32.zip`
3. Retest

**V32 has been proven working in video testing** — use as fallback if V35 shows regressions.

### Driver Installation Notes

- **Install method:** INSIDE emulators via AdrenoTools
- **⚠️ NEVER flash system-wide** — No root required, no system modifications
- **Each emulator needs driver selected separately** in its settings
- Repeat for other emulators that support AdrenoTools (Dolphin, etc.) if desired

---

## 13. PS3Native Testing (OPTIONAL — LOW PRIORITY)

**Only after ALL stable emulators confirmed working.**

PS3Native v0.2.1 is **EXPERIMENTAL**:
- Package: `com.ps3native.standard` (standard variant)
- Expect crashes
- Expect missing features
- Do not migrate data
- Fresh install only
- Test basic functionality, don't expect playable performance

---

## 14. External Display Testing (OPTIONAL)

If using XREAL glasses or external display:

1. Connect XREAL/display
2. Launch a game from CreteOS
3. Verify output appears on external display
4. Check resolution/aspect ratio
5. Test controller input while display connected

---

## Troubleshooting

### Emulator won't launch game

```bash
# Get package info
adb shell dumpsys package <package> | grep -A2 'android.intent.action.MAIN'

# Test manual launch
adb shell am start -n <package>/<activity>

# Check for errors
adb logcat | grep -i "ActivityManager"
```

### SAF permission not sticking

1. Clear app storage for that emulator
2. Relaunch and re-grant permission
3. Ensure you're selecting the correct folder level

### GPU driver crashes

1. Uninstall custom driver (revert to stock)
2. Clear emulator cache
3. Try V32 fallback if V35 has issues

### Controller not responding

1. Check Bluetooth connection
2. Verify controller is paired in Android settings
3. Remap in emulator settings

---

## Verification Checklist

```markdown
## Hardware
- [ ] GPU confirmed as Adreno 830
- [ ] Android updates installed
- [ ] Developer mode enabled
- [ ] ADB connection working

## Apps Installed
- [ ] Obtainium v1.6.13 (dev.imranr.obtainium)
- [ ] RJNY Pack v7.16.0 imported
- [ ] All emulators installed
- [ ] CreteOS installed

## Emulator Setup
- [ ] RetroArch: configured, SAF granted
- [ ] Dolphin: data imported, SAF granted
- [ ] PPSSPP: folder selected (CreteOS/Emulation/ROMs/psp), SAF granted
- [ ] melonDS: configured, SAF granted
- [ ] Azahar 2126.0: configured, SAF granted, controller mapped
- [ ] NetherSX2 v2.1-4248: data imported, SAF granted
- [ ] DuckStation: data imported, SAF granted
- [ ] Eden v0.2.1: configured (dev.eden.eden_emulator), SAF granted
- [ ] Cemu 0.5.2: configured (info.cemu.cemu), SAF granted, controller mapped
- [ ] Vita3K v12: fresh install, games installed (EXPERIMENTAL)

## CreteOS
- [ ] ROM folders scanned
- [ ] Library populated
- [ ] Direct launch tested (all emulators)

## GPU Drivers
- [ ] Eden tested with stock driver first
- [ ] Turnip Gen8 V35 installed in Eden (SHA256 verified)
- [ ] Switch game tested with custom driver
- [ ] If V35 issues: V32 fallback tested

## Final
- [ ] All stable emulators working
- [ ] Controller mappings complete
- [ ] External display tested (if applicable)
```

---

## Time Estimate

| Phase | Time |
|-------|------|
| Android setup + updates | 15-20 min |
| App installation | 10-15 min |
| Emulator configuration | 20-30 min |
| Controller mapping | 10-15 min |
| Direct launch testing | 15-20 min |
| GPU driver testing | 10-15 min |
| **Total** | **~90-120 min** |

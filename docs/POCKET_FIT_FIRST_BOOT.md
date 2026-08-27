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

**If GPU is not Adreno 830, STOP** — StevenMXZ Gen8 drivers are specifically for Adreno 8xx Elite series.

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
   - RetroArch v1.22.2
   - Dolphin 2603
   - PPSSPP v1.20.4
   - melonDS Android 2.0.1
   - Azahar 2126.0
   - NetherSX2 2.2n/4248
   - Eden v0.2.1
   - DuckStation latest
   - Vita3K Android v12
   - Cemu Android 0.5.2
   - *(Skip PS3Native for now)*

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
2. Settings → System → PSP folder → select `Emulation/PSP/`
3. Grant SAF permission
4. Verify games appear

### NetherSX2
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
1. Launch Azahar
2. Grant folder permissions
3. Configure ROM folder
4. **Remap controller** — physical ABXY will differ from Fold 8

### Eden
1. Launch Eden
2. Settings → Verify keys/ and firmware/ are detected
3. Grant SAF permissions for game folder
4. **DO NOT install GPU drivers yet** — test stock first

### Cemu Android
1. Launch Cemu
2. Grant folder permissions
3. Select game folder
4. **Remap controller** for Pocket Fit layout

### Vita3K
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

| System | Test game | Emulator |
|--------|-----------|----------|
| SNES | Any game | RetroArch |
| GameCube | Any game | Dolphin |
| PSP | Any game | PPSSPP |
| DS | Any game | melonDS |
| 3DS | Any game | Azahar |
| PS2 | Any game | NetherSX2 |
| PS1 | Any game | DuckStation |
| Switch | Any game | Eden |
| Wii U | Any game | Cemu |

**If launch fails:**
1. Check logcat for errors:
   ```bash
   adb logcat | grep -i "ActivityManager\|Intent"
   ```
2. Verify package/activity names match installed APK
3. See `docs/EMULATOR_LAUNCH_CONTRACTS.md` for intent details

---

## 12. Test GPU Drivers (ONLY AFTER STEP 11)

**Prerequisites:**
- All stable emulators confirmed working with stock drivers
- At least one Switch game tested in Eden without crashes

### Install StevenMXZ Turnip Gen8 Driver

1. Launch Eden
2. Settings → GPU Driver → Install
3. Select `StevenMXZ-v26.3.0-R4.zip`
4. Restart Eden
5. Launch a graphically demanding Switch game
6. Monitor for:
   - Visual glitches
   - Performance regressions
   - Crashes

**If issues with R4:**
1. Settings → GPU Driver → Install
2. Select fallback `StevenMXZ-v26.3.0-R2.zip`
3. Retest

**Driver installation is per-emulator.** Repeat for other emulators that support AdrenoTools (Dolphin, etc.) if desired.

---

## 13. PS3Native Testing (OPTIONAL — LOW PRIORITY)

**Only after ALL stable emulators confirmed working.**

PS3Native v0.2.1 is **EXPERIMENTAL**:
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
3. Try older driver version

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
- [ ] Obtainium v1.6.13
- [ ] RJNY Pack v7.16.0 imported
- [ ] All emulators installed
- [ ] CreteOS installed

## Emulator Setup
- [ ] RetroArch: configured, SAF granted
- [ ] Dolphin: data imported, SAF granted
- [ ] PPSSPP: folder selected, SAF granted
- [ ] melonDS: configured, SAF granted
- [ ] Azahar: configured, SAF granted, controller mapped
- [ ] NetherSX2: data imported, SAF granted
- [ ] DuckStation: data imported, SAF granted
- [ ] Eden: configured, SAF granted
- [ ] Cemu: configured, SAF granted, controller mapped
- [ ] Vita3K: fresh install, games installed

## CreteOS
- [ ] ROM folders scanned
- [ ] Library populated
- [ ] Direct launch tested (all emulators)

## GPU Drivers
- [ ] Eden tested with stock driver first
- [ ] StevenMXZ R4 installed in Eden
- [ ] Switch game tested with custom driver

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

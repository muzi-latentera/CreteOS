# Emulator Launch Contracts

> **Purpose:** Document Android Intent contracts for direct game launching from CreteOS  
> **Status:** All launch activities ASSUMED until Phase 9 ADB dumpsys verification  
> **Last updated:** 2026-08-27

---

## Overview

CreteOS launches games by sending Android Intents to emulator apps. Each emulator has specific requirements for:
- Package name (app identifier)
- Activity name (entry point)
- Intent action
- ROM path delivery (extra, data URI, etc.)
- URI scheme (file:// vs content://)
- SAF permission requirements

**Verification status legend:**
- ✅ VERIFIED — Package confirmed via aapt/device inspection
- ⚠️ ASSUMED — Activity/intent based on documentation, needs ADB verification
- ❓ UNKNOWN — No documentation available

---

## RetroArch

- **Package:** `com.retroarch.aarch64` ✅ VERIFIED
- **Activity:** `.browser.retroactivity.RetroActivityFuture` ⚠️ ASSUMED
- **Launch intent action:** `android.intent.action.MAIN`
- **ROM parameter:** 
  - Extra `ROM` — path to ROM file
  - Extra `LIBRETRO` — path to libretro core (.so file)
  - Extra `CONFIGFILE` — (optional) path to config file
  - Extra `REFRESHRATE` — (optional) display refresh rate
- **URI scheme:** `file://`
- **SAF URI grant required:** No (uses file paths)
- **Notes:** 
  - Core must be specified — RetroArch doesn't auto-detect
  - Core path format: `/data/data/com.retroarch.aarch64/cores/<core>.so` or shared storage path
  - Multiple ROM formats supported per core

**Example Intent:**
```kotlin
Intent(Intent.ACTION_MAIN).apply {
    setClassName("com.retroarch.aarch64", "com.retroarch.aarch64.browser.retroactivity.RetroActivityFuture")
    putExtra("ROM", "/sdcard/ROMs/SNES/game.sfc")
    putExtra("LIBRETRO", "/sdcard/RetroArch/cores/snes9x_libretro_android.so")
}
```

---

## Dolphin

- **Package:** `org.dolphinemu.dolphinemu` ⚠️ ASSUMED (not yet installed)
- **Activity:** `.activities.EmulationActivity` ⚠️ ASSUMED
- **Launch intent action:** `android.intent.action.MAIN`
- **ROM parameter:** Extra `filePaths` — ArrayList<String> containing ROM path(s)
- **URI scheme:** `file://` or `content://`
- **SAF URI grant required:** Yes (for content:// URIs)
- **Notes:**
  - filePaths is an ArrayList, even for single games
  - Supports ISO, GCZ, RVZ, WBFS, CISO, GCM formats
  - Can pass multiple paths for multi-disc games
  - Source: dolphin-emu.org (NOT GitHub)

**Example Intent:**
```kotlin
Intent(Intent.ACTION_MAIN).apply {
    setClassName("org.dolphinemu.dolphinemu", "org.dolphinemu.dolphinemu.activities.EmulationActivity")
    putStringArrayListExtra("filePaths", arrayListOf("/sdcard/ROMs/GC/game.iso"))
}
```

---

## PPSSPP

- **Package:** `org.ppsspp.ppsspp` ⚠️ ASSUMED (not yet installed)
- **Activity:** `.PpssppActivity` ⚠️ ASSUMED
- **Launch intent action:** `android.intent.action.VIEW`
- **ROM parameter:** Data URI pointing to ROM
- **URI scheme:** `file://` (preferred)
- **SAF URI grant required:** No (uses file:// URI)
- **Notes:**
  - Uses ACTION_VIEW with data, not extras
  - Supports ISO, CSO, PBP formats
  - File path must be URL-encoded if contains special characters
  - Source: ppsspp.org/files/1_20_4/ppsspp.apk (free version)
  - Storage folder: shared, point to `CreteOS/Emulation/ROMs/psp`

**Example Intent:**
```kotlin
Intent(Intent.ACTION_VIEW).apply {
    setClassName("org.ppsspp.ppsspp", "org.ppsspp.ppsspp.PpssppActivity")
    data = Uri.parse("file:///sdcard/ROMs/PSP/game.iso")
}
```

---

## DuckStation

- **Package:** `com.github.stenzek.duckstation` ⚠️ ASSUMED (not yet installed)
- **Activity:** `.MainActivity` ⚠️ ASSUMED
- **Launch intent action:** `android.intent.action.MAIN`
- **ROM parameter:** Extra `bootPath` — path to ROM file
- **URI scheme:** `file://`
- **SAF URI grant required:** No (uses file paths)
- **Notes:**
  - Some sources show `boot_path`, others `bootPath` — verify on device
  - Supports BIN/CUE, ISO, IMG, CHD, PBP formats
  - For BIN/CUE, pass the .cue file path
  - Source: Google Play only

**Example Intent:**
```kotlin
Intent(Intent.ACTION_MAIN).apply {
    setClassName("com.github.stenzek.duckstation", "com.github.stenzek.duckstation.MainActivity")
    putExtra("bootPath", "/sdcard/ROMs/PS1/game.cue")
}
```

---

## NetherSX2

- **Package:** `xyz.aethersx2.android` ✅ VERIFIED (fork kept AetherSX2 package name)
- **Activity:** ❓ UNKNOWN (may use custom action instead) ⚠️ ASSUMED
- **Launch intent action:** `xyz.aethersx2.android.OPEN` ⚠️ ASSUMED
- **ROM parameter:** Data URI pointing to ROM
- **URI scheme:** `content://` (preferred for SAF compatibility)
- **SAF URI grant required:** Yes
- **Version:** v2.1-4248 STABLE
- **Notes:**
  - Uses custom action, not standard MAIN
  - Requires persistable URI permission for content:// URIs
  - Supports ISO, CHD, CSO, BIN formats (PS2)
  - **DO NOT confuse with xyz.trixarian.nethersx2** — the fork kept the original package name

**Example Intent:**
```kotlin
Intent("xyz.aethersx2.android.OPEN").apply {
    data = contentUri // SAF content:// URI
    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
}
```

---

## Eden (Nintendo Switch)

- **Package:** `dev.eden.eden_emulator` ✅ VERIFIED (from aapt)
- **Activity:** ❓ UNKNOWN ⚠️ ASSUMED
- **Launch intent action:** `android.intent.action.MAIN` ⚠️ ASSUMED
- **ROM parameter:** File path extra (key TBD)
- **URI scheme:** `file://` ⚠️ ASSUMED
- **SAF URI grant required:** TBD
- **Version:** v0.2.1 standard
- **Notes:**
  - Package is `dev.eden.eden_emulator` — NOT `dev.eden_emu.eden` (which was incorrectly assumed)
  - Supports NSP, XCI, NCA formats
  - **Must verify activity after Phase 9 testing:**
    ```bash
    adb shell dumpsys package dev.eden.eden_emulator | grep -A2 'android.intent.action.MAIN'
    ```

---

## melonDS

- **Package:** `me.magnum.melonds` ✅ VERIFIED
- **Activity:** `.ui.MainActivity` ⚠️ ASSUMED
- **Launch intent action:** `android.intent.action.VIEW`
- **ROM parameter:** Data URI pointing to ROM
- **URI scheme:** `content://` or `file://`
- **SAF URI grant required:** Yes (for content:// URIs)
- **Version:** 2.0.1
- **Notes:**
  - Supports NDS, DSI formats
  - May need to handle DSiWare differently

**Example Intent:**
```kotlin
Intent(Intent.ACTION_VIEW).apply {
    setClassName("me.magnum.melonds", "me.magnum.melonds.ui.MainActivity")
    data = Uri.parse("file:///sdcard/ROMs/NDS/game.nds")
}
```

---

## Azahar (3DS)

- **Package:** `org.azahar_emu.azahar` ✅ VERIFIED
- **Activity:** `.activities.EmulationActivity` ⚠️ ASSUMED
- **Launch intent action:** `android.intent.action.MAIN` ⚠️ ASSUMED
- **ROM parameter:** ❓ UNKNOWN — likely file path extra or data URI
- **URI scheme:** ❓ UNKNOWN
- **SAF URI grant required:** TBD
- **Version:** 2126.0-vanilla
- **Notes:**
  - Azahar is a Citra fork/continuation
  - May follow Citra's intent contract
  - Supports 3DS, CIA, CCI formats
  - **Must verify intent contract after Phase 9 testing**

---

## Cemu Android (Wii U)

- **Package:** `info.cemu.cemu` ✅ VERIFIED (case-sensitive lowercase, NOT info.cemu.Cemu)
- **Activity:** `.MainActivity` ⚠️ ASSUMED
- **Launch intent action:** `android.intent.action.MAIN` ⚠️ ASSUMED
- **ROM parameter:** ❓ UNKNOWN
- **URI scheme:** ❓ UNKNOWN
- **SAF URI grant required:** TBD
- **Version:** 0.5.2 (SapphireRhodonite Android fork)
- **Notes:**
  - This is SapphireRhodonite/Cemu fork, NOT upstream desktop Cemu
  - Intent contract may differ from desktop version documentation
  - Supports WUD, WUX, ISO, RPX formats
  - **Must verify intent contract after Phase 9 testing**

---

## Vita3K

- **Package:** `org.vita3k.emulator` ✅ VERIFIED
- **Activity:** ❓ UNKNOWN ⚠️ ASSUMED
- **Launch intent action:** ❓ UNKNOWN ⚠️ ASSUMED
- **ROM parameter:** ❓ UNKNOWN
- **URI scheme:** ❓ UNKNOWN
- **SAF URI grant required:** TBD
- **Version:** v12 (versionName 0.2.0-12)
- **Status:** ⚠️ EXPERIMENTAL
- **Notes:**
  - Vita3K may not support direct game launching via intent
  - Games are "installed" within the emulator, not launched from external paths
  - May need to launch emulator and let user select from installed games
  - **Verify if external launch is even supported**

---

## PS3Native

- **Package:** `com.ps3native.standard` ✅ VERIFIED (standard variant, NOT antutu variant)
- **Activity:** ❓ UNKNOWN ⚠️ ASSUMED
- **Launch intent action:** ❓ UNKNOWN ⚠️ ASSUMED
- **ROM parameter:** ❓ UNKNOWN
- **URI scheme:** ❓ UNKNOWN
- **SAF URI grant required:** TBD
- **Version:** v0.2.1
- **Status:** ⚠️ EXPERIMENTAL
- **Notes:**
  - **EXPERIMENTAL** — do not implement until stable
  - Intent contract likely unstable and subject to change
  - Low priority for Phase 9 testing

---

## Phase 9 Verification Checklist

For each emulator, run these commands after APK installation:

```bash
# 1. Verify package name
adb shell pm list packages | grep -i <emulator>

# 2. Get launchable activities
adb shell dumpsys package <package> | grep -A2 'android.intent.action.MAIN'

# 3. Get all exported activities
adb shell dumpsys package <package> | grep -B1 'exported=true'

# 4. Check for custom intent filters
adb shell dumpsys package <package> | grep -A5 'intent-filter'

# 5. Test launch (without ROM)
adb shell am start -n <package>/<activity>

# 6. Test launch with ROM (adjust extras as needed)
adb shell am start -n <package>/<activity> --es ROM "/sdcard/ROMs/test.rom"
```

---

## Fallback Strategy

If direct launch fails for any emulator:

1. **Try alternative activities** — some emulators have multiple entry points
2. **Try ACTION_VIEW with data URI** — common pattern for media apps
3. **Try content:// instead of file://** — some apps require SAF URIs
4. **Check logcat for intent errors:**
   ```bash
   adb logcat | grep -i "ActivityManager\|Intent"
   ```
5. **Fall back to app launch only** — open emulator, user selects game manually

---

## Version History

| Date | Changes |
|------|---------|
| 2026-08-27 | Corrected all package names: Eden=dev.eden.eden_emulator, Cemu=info.cemu.cemu, NetherSX2=xyz.aethersx2.android, PS3Native=com.ps3native.standard. All activities marked ASSUMED until ADB verification. |
| 2026-08-27 | Initial documentation — all contracts ASSUMED pending device verification |

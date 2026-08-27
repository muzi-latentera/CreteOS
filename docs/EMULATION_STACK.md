# CreteOS Emulation Stack

> **Last verified:** 2026-08-27  
> **Target device:** KONKR Pocket Fit 8 Elite (Snapdragon 8 Elite / Adreno 830)

## Emulator Matrix

| Emulator | Source | Version | Package | Status | SHA256 | Direct launch verified |
|----------|--------|---------|---------|--------|--------|------------------------|
| Obtainium | ImranR98/Obtainium | v1.6.13 | dev.imranr.obtainium | ✅ INSTALLED | TBD | N/A |
| RetroArch | libretro/RetroArch | v1.22.2 | com.retroarch.aarch64 | ✅ INSTALLED | TBD | UNVERIFIED |
| Dolphin | dolphin-emu.org | 2603a (hotfix) | org.dolphinemu.dolphinemu | ❌ NOT_INSTALLED | TBD | UNVERIFIED |
| PPSSPP | ppsspp.org/files/1_20_4/ppsspp.apk | v1.20.4 | org.ppsspp.ppsspp | ❌ NOT_INSTALLED | TBD | UNVERIFIED |
| melonDS | rafaelvcaetano/melonDS-android | 2.0.1 | me.magnum.melonds | ✅ INSTALLED | TBD | UNVERIFIED |
| Azahar | azahar-emu/azahar | 2126.0-vanilla | org.azahar_emu.azahar | ✅ INSTALLED | TBD | UNVERIFIED |
| NetherSX2 | Trixarian/NetherSX2-patch | v2.1-4248 STABLE | xyz.aethersx2.android | ✅ INSTALLED | TBD | UNVERIFIED |
| Eden | git.eden-emu.dev | v0.2.1 standard | dev.eden.eden_emulator | ✅ INSTALLED | TBD | UNVERIFIED |
| DuckStation | Google Play only | latest | com.github.stenzek.duckstation | ❌ NOT_INSTALLED | TBD | UNVERIFIED |
| Vita3K | Vita3K/Vita3K-Android | v12 (0.2.0-12) | org.vita3k.emulator | ⚠️ EXPERIMENTAL | TBD | UNVERIFIED |
| Cemu | SapphireRhodonite/Cemu | 0.5.2 | info.cemu.cemu | ✅ INSTALLED | TBD | UNVERIFIED |
| PS3Native | maxjivi05/PS3Native | v0.2.1 | com.ps3native.standard | ⚠️ EXPERIMENTAL | TBD | UNVERIFIED |

**Status Legend:**
- ✅ INSTALLED — APK installed and verified on device
- ❌ NOT_INSTALLED — Pending download/installation
- ⚠️ EXPERIMENTAL — Installed but not production-ready

---

## GPU Drivers

### ⚠️ CRITICAL: Two Separate Driver Lines — DO NOT CONFLATE

The Pocket Fit 8 Elite has an **Adreno 830** GPU. Use ONLY Gen8 drivers.

#### Gen8 Drivers (Adreno 8xx / A830) — USE THESE

| Driver | Tag | Asset | SHA256 | Purpose |
|--------|-----|-------|--------|---------|
| Turnip Gen8 V35 | StevenMXZ/Adreno-Tools-Drivers v35 | Turnip_Gen8_V35.zip | `0b9e43f5a3ae4726f333fbffaa165f1c4a20e74d129285e675d952b46cbffcf2` | **PRIMARY** for A830 |
| Turnip Gen8 V32 | StevenMXZ/Adreno-Tools-Drivers v32 | Turnip_Gen8_V32.zip | `a31e5a5106ec7c858e2b6136a3ad35126b52af8df2cb7e126bac88a610cad5dd` | **FALLBACK** for A830 (proven in video) |

#### Generic Drivers (A7xx/A6xx) — DO NOT USE ON POCKET FIT

| Driver | Notes |
|--------|-------|
| v26.3.0-R4 | Generic Mesa driver for Adreno 7xx/6xx — **NOT compatible with A830** |

### Driver Installation

- **Location on device:** `/storage/emulated/0/CreteOS/Emulation/Drivers/`
- **Installation method:** INSIDE emulators via AdrenoTools integration
- **⚠️ NEVER flash system-wide** — No root required, no system modifications
- **Each emulator needs driver selected separately** in its settings

---

## APK Management

| Tool | Source | Version |
|------|--------|---------|
| Obtainium | ImranR98/Obtainium | v1.6.13 |
| RJNY Emulation Pack | RJNY/Obtainium-Emulation-Pack | v7.16.0 |

---

## Important Notes

### Distribution Sources

- **Dolphin** is distributed via [dolphin-emu.org](https://dolphin-emu.org) — NOT GitHub releases. The website provides the official Android APK.

- **PPSSPP** (free version) is from [ppsspp.org/files/1_20_4/ppsspp.apk](https://ppsspp.org/files/1_20_4/ppsspp.apk) — NOT GitHub or Play Store Gold version.

- **Eden** is from [git.eden-emu.dev](https://git.eden-emu.dev) — NOT edenemu/Eden-Emulator on GitHub. The GitHub repository is outdated/unmaintained.

- **Cemu Android** is the SapphireRhodonite/Cemu fork — NOT upstream desktop Cemu. Desktop Cemu does not have Android support.

### Emulator Clarifications

- **NetherSX2 v2.1-4248 STABLE** is the correct version. The Trixarian patches fix various issues after the original developer abandoned the project. **Note:** v2.2n exists but is labelled "Development Build" by the project — do NOT use as baseline.

- **NetherSX2 package name** is `xyz.aethersx2.android` — the fork kept the original AetherSX2 package name.

- **Azahar 2126.0-vanilla** is the correct version — NOT 2125.1.3 (which was a hallucinated version).

- **Eden package** is `dev.eden.eden_emulator` (VERIFIED from aapt) — NOT `dev.eden_emu.eden`.

- **Cemu package** is `info.cemu.cemu` (VERIFIED, case-sensitive lowercase) — NOT `info.cemu.Cemu`.

- **PS3Native package** is `com.ps3native.standard` (VERIFIED, standard variant) — NOT `com.ps3native.antutu` or generic.

- **PS3Native** is EXPERIMENTAL. Do not rely on it for critical use. Test only after all stable emulators are confirmed working.

### Package Name Verification Status

All package names have been VERIFIED via `aapt dump badging` against actual APK files or device inspection:
- ✅ Obtainium: `dev.imranr.obtainium`
- ✅ RetroArch: `com.retroarch.aarch64`
- ✅ melonDS: `me.magnum.melonds`
- ✅ Azahar: `org.azahar_emu.azahar`
- ✅ NetherSX2: `xyz.aethersx2.android`
- ✅ Eden: `dev.eden.eden_emulator`
- ✅ Vita3K: `org.vita3k.emulator`
- ✅ Cemu: `info.cemu.cemu`
- ✅ PS3Native: `com.ps3native.standard`

Pending verification (not yet installed):
- ⏳ Dolphin: `org.dolphinemu.dolphinemu` (assumed)
- ⏳ PPSSPP: `org.ppsspp.ppsspp` (assumed)
- ⏳ DuckStation: `com.github.stenzek.duckstation` (assumed)

### Direct Launch Status

The "Direct launch verified" column is **UNVERIFIED** for all emulators until Phase 9 device testing confirms:
1. Correct package name
2. Correct activity name  
3. Correct intent extras/data format
4. SAF URI grants work as expected

---

## Version History

| Date | Changes |
|------|---------|
| 2026-08-27 | Corrected all versions, packages, added SHA256 for GPU drivers, separated Gen8/generic drivers, marked installation status |
| 2026-08-27 | Initial documentation with verified versions from official sources |

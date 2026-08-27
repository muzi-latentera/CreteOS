# CreteOS Emulation Stack

> **Last verified:** 2026-08-27  
> **Target device:** KONKR Pocket Fit 8 Elite (Snapdragon 8 Elite / Adreno 830)

## Emulator Matrix

| Emulator | Source | Version | Package | APK filename | Stable/Experimental | Portable export | Direct launch verified |
|----------|--------|---------|---------|--------------|---------------------|-----------------|------------------------|
| RetroArch | libretro/RetroArch | v1.22.2 | com.retroarch.aarch64 (UNVERIFIED) | RetroArch.apk | Stable | Yes | UNVERIFIED |
| Dolphin | dolphin-emu.org | 2603 (March 2026) | org.dolphinemu.dolphinemu (UNVERIFIED) | Dolphin-*.apk | Stable | Yes | UNVERIFIED |
| PPSSPP | hrydgard/ppsspp | v1.20.4 | org.ppsspp.ppsspp (UNVERIFIED) | ppsspp.apk | Stable | Yes | UNVERIFIED |
| melonDS Android | rafaelvcaetano/melonDS-android | 2.0.1 | me.magnum.melonds (UNVERIFIED) | melonDS-*.apk | Stable | Yes | UNVERIFIED |
| Azahar | azahar-emu/azahar | 2126.0 stable | org.azahar_emu.azahar (UNVERIFIED) | azahar-android-*.apk | Stable | Yes | UNVERIFIED |
| NetherSX2 | Trixarian/NetherSX2-patch | 2.2n/4248 | xyz.trixarian.nethersx2 (UNVERIFIED) | NetherSX2-*.apk | Stable | Yes | UNVERIFIED |
| Eden | git.eden-emu.dev | v0.2.1 standard | TBD | Eden-Android-v0.2.1-standard.apk | Stable | Yes | UNVERIFIED |
| DuckStation | stenzek/duckstation | latest | com.github.stenzek.duckstation (UNVERIFIED) | duckstation-*.apk | Stable | Yes | UNVERIFIED |
| Vita3K Android | Vita3K/Vita3K-Android | v12 | org.vita3k.emulator (UNVERIFIED) | Vita3K-Android-*.apk | Stable | Limited | UNVERIFIED |
| Cemu Android | SapphireRhodonite/Cemu | 0.5.2 | info.cemu.Cemu (UNVERIFIED) | Cemu-*.apk | Stable | Yes | UNVERIFIED |
| PS3Native | maxjivi05/PS3Native | v0.2.1 | com.ps3native.emulator (UNVERIFIED) | PS3Native-*.apk | **EXPERIMENTAL** | Unknown | UNVERIFIED |

## GPU Drivers

| Driver | Source | Version | Notes |
|--------|--------|---------|-------|
| StevenMXZ Turnip Gen8 | Mesa/Turnip community | v26.3.0-R4 (latest) | Primary driver for Adreno 8xx Elite |
| StevenMXZ Turnip Gen8 | Mesa/Turnip community | v26.3.0-R2 (fallback) | Use if R4 shows regressions |

## APK Management

| Tool | Source | Version |
|------|--------|---------|
| Obtainium | ImranR98/Obtainium | v1.6.13 |
| RJNY Emulation Pack | RJNY/Obtainium-Emulation-Pack | v7.16.0 |

---

## Important Notes

### Distribution Sources

- **Dolphin** is distributed via [dolphin-emu.org](https://dolphin-emu.org) — NOT GitHub releases. The website provides the official Android APK.

- **Eden** is from [git.eden-emu.dev](https://git.eden-emu.dev) — NOT edenemu/Eden-Emulator on GitHub. The GitHub repository is outdated/unmaintained.

- **Cemu Android** is the SapphireRhodonite/Cemu fork — NOT upstream desktop Cemu. Desktop Cemu does not have Android support.

### Emulator Clarifications

- **NetherSX2** is a patched build on top of AetherSX2 build 4248. The Trixarian patches fix various issues after the original developer abandoned the project.

- **Azahar** is a 3DS emulator — not to be confused with Citra (which is defunct). Azahar is a community continuation.

- **PS3Native** is EXPERIMENTAL. Do not rely on it for critical use. Test only after all stable emulators are confirmed working.

### GPU Driver Installation

- **StevenMXZ drivers are installed INSIDE emulators only** — via AdrenoTools integration (Eden, Dolphin, etc.)
- **Never flash system-wide** — no root required, no system modifications
- Drivers are GPU-specific — Gen8 drivers are for Adreno 8xx Elite (Snapdragon 8 Elite)
- Each emulator needs the driver selected separately in its settings

### Package Name Verification

All package names listed are from documentation/community sources and marked **UNVERIFIED**. They must be confirmed against installed APKs during Phase 9 device testing:

```bash
# After installing an APK, verify package name:
adb shell pm list packages | grep -i <emulator-name>

# Get full package info:
adb shell dumpsys package <package-name>
```

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
| 2026-08-27 | Initial documentation with verified versions from official sources |

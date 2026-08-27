# Emulator Migration Matrix

> **Purpose:** Guide for migrating emulator data from Galaxy Z Fold 8 to KONKR Pocket Fit 8 Elite  
> **Last updated:** 2026-08-27

## Migration Overview

| Emulator | What transfers portably | Export method | Must redo on Pocket Fit |
|----------|------------------------|---------------|------------------------|
| RetroArch | Configs, saves, BIOS, cores | Copy `RetroArch/` folder from shared storage | Re-select game directories via SAF |
| Dolphin | User data (saves, configs, GC BIOS) | Settings → Config → Export User Data (produces ZIP) | Re-grant SAF ROM folder permissions |
| PPSSPP | Entire game data, saves, configs | Copy `PSP/` folder from shared storage | Re-select PSP folder in settings |
| NetherSX2 | BIOS, memory cards, per-game settings | Settings → Transfer Data → Export | Some global/device settings; re-grant SAF |
| DuckStation | BIOS, memory cards, saves, configs | Settings → Transfer Data (produces ZIP) | Some global/device settings; re-grant SAF |
| melonDS | Saves, configs, BIOS | Copy `melonDS/` folder from shared storage | Re-grant folder permissions |
| Azahar | Save data, configs | Copy save folder from shared storage | Re-grant folder; remap controller for physical buttons |
| Eden | Keys, firmware, mods, ROMs | User data export if available; copy folders | Re-grant SAF; re-select GPU driver on Pocket Fit |
| Cemu Android | Games, saves, mods | Copy from shared storage where applicable | Re-select game folder; re-grant SAF; remap controller |
| Vita3K | **NOT PORTABLE** | N/A — data in private storage | Fresh install required; reinstall all games |
| PS3Native | **DO NOT MIGRATE** | N/A — experimental | Wait until stable release |

---

## DO NOT Transfer

These items are **device-specific** and will cause issues if transferred:

| Item | Reason |
|------|--------|
| **Shader caches** | GPU-specific — Adreno 830 shaders incompatible with previous GPU |
| **Controller mappings** | Device IDs differ between Fold 8 and Pocket Fit physical controls |
| **Thermal profiles** | Device-specific performance tuning |
| **GPU driver files** | Must be installed fresh on target device |

---

## Detailed Migration Steps

### RetroArch

**What transfers:**
- `RetroArch/config/` — core and game-specific configurations
- `RetroArch/saves/` — save states and SRAM
- `RetroArch/system/` — BIOS files
- `RetroArch/cores/` — libretro cores

**Export method:**
```bash
# On source device, copy entire folder
adb pull /sdcard/RetroArch/ ./Exports/RetroArch/
```

**On Pocket Fit:**
1. Copy `RetroArch/` to shared storage
2. Launch RetroArch
3. Go to Settings → Directory → set all paths
4. Grant SAF permissions for ROM folders

---

### Dolphin

**What transfers:**
- GC/Wii saves
- Controller profiles (but mappings need redoing)
- Graphics settings
- Game-specific settings
- GC BIOS (if present)

**Export method:**
1. Open Dolphin → Settings → Config
2. Tap "Export User Data"
3. Save ZIP to accessible location

**On Pocket Fit:**
1. Install Dolphin
2. Settings → Config → Import User Data
3. Select exported ZIP
4. Re-grant SAF permissions for ROM folders
5. Remap controller if using physical buttons

---

### PPSSPP

**What transfers:**
- `PSP/SAVEDATA/` — game saves
- `PSP/PPSSPP_STATE/` — save states
- `PSP/SYSTEM/` — configs
- `PSP/GAME/` — ISOs (if stored here)

**Export method:**
```bash
adb pull /sdcard/PSP/ ./Exports/PSP/
```

**On Pocket Fit:**
1. Copy `PSP/` folder to shared storage
2. Launch PPSSPP
3. Settings → System → PSP folder → select the folder
4. Grant SAF permission

---

### NetherSX2

**What transfers:**
- BIOS files
- Memory cards (saves)
- Per-game settings
- Texture packs

**Export method:**
1. Settings → Transfer Data → Export
2. Save to accessible location

**On Pocket Fit:**
1. Install NetherSX2
2. Settings → Transfer Data → Import
3. Re-grant SAF permissions for ROM/BIOS folders
4. Some global settings (renderer, audio latency) may need adjustment for Pocket Fit

---

### DuckStation

**What transfers:**
- BIOS
- Memory cards
- Save states
- Game settings
- Cheats

**Export method:**
1. Settings → Transfer Data → Export
2. Creates ZIP with all portable data

**On Pocket Fit:**
1. Install DuckStation
2. Settings → Transfer Data → Import
3. Re-grant SAF permissions
4. Adjust renderer settings for Adreno 830 if needed

---

### melonDS

**What transfers:**
- `melonDS/` folder contents:
  - `bios7.bin`, `bios9.bin`, `firmware.bin`
  - `*.sav` files
  - Configuration

**Export method:**
```bash
adb pull /sdcard/melonDS/ ./Exports/melonDS/
```

**On Pocket Fit:**
1. Copy folder to shared storage
2. Launch melonDS
3. Re-grant folder permissions when prompted

---

### Azahar (3DS)

**What transfers:**
- Save data from shared storage
- Configuration files

**Export method:**
1. Locate Azahar data folder in shared storage
2. Copy entire folder

**On Pocket Fit:**
1. Copy to shared storage
2. Launch Azahar
3. Re-grant folder permissions
4. **Important:** Remap controller — Pocket Fit physical ABXY layout differs from touch/Fold controls

---

### Eden (Switch)

**What transfers:**
- `keys/` — prod.keys, title.keys
- `firmware/` — Switch firmware files
- `mods/` — game mods
- ROMs (if in shared storage)
- User data (if export available)

**Export method:**
1. If Eden has user data export, use it
2. Otherwise, manually copy folders from shared storage

**On Pocket Fit:**
1. Copy all folders to appropriate locations
2. Launch Eden
3. Re-grant SAF permissions for all folders
4. **Critical:** Re-select GPU driver (Settings → GPU Driver → install StevenMXZ v26.3.0-R4)

---

### Cemu Android (Wii U)

**What transfers:**
- Games (if in shared storage)
- Save data
- Mods

**Export method:**
1. Copy Cemu folder from shared storage

**On Pocket Fit:**
1. Copy to shared storage
2. Install Cemu
3. Re-select game folder
4. Re-grant SAF permissions
5. Remap controller for Pocket Fit physical layout

---

### Vita3K

**⚠️ NOT PORTABLY MIGRATABLE**

Vita3K stores installed game data in Android private storage (`/data/data/org.vita3k.emulator/`), which is not accessible without root.

**Recommended approach:**
1. Fresh install on Pocket Fit
2. Reinstall all Vita games from PKG/ZIP files
3. Save data will need to be replayed or restored from Vita backup if available

---

### PS3Native

**⚠️ EXPERIMENTAL — DO NOT MIGRATE**

PS3Native is in early experimental stages. Do not attempt migration until:
- Emulator reaches stable release
- Migration path is documented by developers
- Basic functionality is confirmed on Pocket Fit

---

## Migration Checklist

```markdown
## Pre-Migration (on Fold 8)
- [ ] RetroArch: Copy RetroArch/ folder
- [ ] Dolphin: Export User Data ZIP
- [ ] PPSSPP: Copy PSP/ folder
- [ ] NetherSX2: Transfer Data export
- [ ] DuckStation: Transfer Data export
- [ ] melonDS: Copy melonDS/ folder
- [ ] Azahar: Copy save/config folder
- [ ] Eden: Copy keys/, firmware/, mods/
- [ ] Cemu: Copy Cemu folder
- [ ] All exports saved to Exports/ folder

## Post-Migration (on Pocket Fit)
- [ ] RetroArch: Folders copied, SAF granted, dirs configured
- [ ] Dolphin: User Data imported, SAF granted
- [ ] PPSSPP: PSP/ folder copied, folder selected
- [ ] NetherSX2: Transfer Data imported, SAF granted
- [ ] DuckStation: Transfer Data imported, SAF granted
- [ ] melonDS: Folder copied, permissions granted
- [ ] Azahar: Data copied, permissions granted, controller mapped
- [ ] Eden: Keys/firmware copied, SAF granted, GPU driver installed
- [ ] Cemu: Folder copied, SAF granted, controller mapped
- [ ] Vita3K: Fresh install, games reinstalled
- [ ] PS3Native: SKIPPED (experimental)
```

---

## Estimated Time

| Task | Time |
|------|------|
| Export all data from Fold 8 | 10-15 min |
| Copy to Pocket Fit | 5-10 min (depends on data size) |
| Import + SAF grants (all emulators) | 15-20 min |
| Controller remapping | 10-15 min |
| **Total** | **~45-60 min** |

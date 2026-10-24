# Bosgame MGS V mod tools

`CreteModSetup.cs` calls the downloaded SnakeBite 0.9.2.5 backend. It was compiled on the G1 with .NET Framework 4's csc, then run on Bosgame using the existing Wine-CachyOS/Mono runtime in a separate tools prefix. It does not launch games or automate GUI controls.

This helper is bound to the verified 0.9.2.5 API. Inspect newer versions before reuse. `prepare` must be used only on a staging copy with verified original archives and rollback backups. It calls SnakeBite's own backup and migration functions. `install` requires an initialized staging copy and explicitly ordered .mgsv package paths. Check package versions, dependencies and all file conflicts first; it does not replace those checks. `verify` checks the managed 00/01 archive hash. Keep the official manager files and dictionaries together. Do not run arbitrary backend methods against a live game that is running.

A successful install/verify is a file check; gameplay still needs acceptance. Retain original 00.dat, 01.dat and chunk0.dat plus any overwritten loose files, the original Lutris YAML, save backup and a per-file deployment receipt before promoting verified staging files. Use per-file atomic copy and hash verification while the game is stopped. Under Lutris the IHHook dinput8.dll requires `wine.overrides.dinput8: n,b`. Preserve other runner settings.

The current setup and exact rollback path are in `docs/G1_SETUP_AND_RECOVERY.md` and the dated journal. Host tools live under `~/.local/share/creteos-host/mod-tools/mgsv`; original game/download files are outside the portable G1 profile backup. Replacing the G1 should leave Bosgame untouched.

Sources: [SnakeBite](https://github.com/TinManTex/SnakeBite), [Infinite Heaven releases](https://github.com/kapuragu/InfiniteHeaven/releases), [US Naval Prison Facility requirements](https://www.nexusmods.com/metalgearsolidvtpp/mods/978), [Ground Zeroes](https://www.nexusmods.com/metalgearsolidvtpp/mods/2402), [Rock Climb Anywhere](https://www.nexusmods.com/metalgearsolidvtpp/mods/2603).

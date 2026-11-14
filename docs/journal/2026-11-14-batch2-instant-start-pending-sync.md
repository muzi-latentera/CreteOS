# 14 November 2026: batch 2 (instant start, Steam installs, sturdier launches)

Pending vault sync. This was autonomous overnight work at the owner's request; see `docs/ROADMAP_CRETEOS_1_0.md`.

## Changes (source `46685cc`, `a0af9a3`, `07805ec`)

- **Instant start.**
  - Every successful scan saves `%LOCALAPPDATA%\CreteOS\library_snapshot.json` (930 KB). `get_cached_apps` loads it at start-up and the fresh scan replaces it.
  - Automatic Steam and Xbox owned-library updates now refresh in the background, so start-up no longer dims Home with "Refreshing library".
- **Settings.**
  - A file that fails to parse keeps every value that still fits; the bad copy is saved as `settings.json.unreadable-<pid>`.
  - A locked file is retried instead of reading as defaults.
  - Saves write a temporary file and rename it into place.
- **Steam installs.**
  - The watcher is a tested decision function, polling once a second.
  - It has no time limit while progress moves or Steam reports the download paused, and it only gives up after 60 minutes with no progress.
  - It waits ten minutes for Steam's install dialog.
  - The final commit no longer counts as uninstalled.
  - Downloads already running are re-attached at start-up (`steam_active_downloads`).
  - Uninstall waits up to ten minutes for Steam's confirmation.
- **Launches.**
  - Direct exes start in their own folder and retry elevated on error 740.
  - Every ShellExecute route reports failures (declined UAC, missing file) instead of waiting silently.
  - The Crete cover writes Steam's phase on the native artwork window, but only for starting Steam, syncing saves, updating, and waiting for Steam.
- **Input.**
  - The left stick uses hysteresis (on above 0.55, off below 0.4) and only the stronger axis counts.
  - Menu closes the quick menu it opened.
- **Manage saves.**
  - Ludusavi's config is read before any scan starts.
  - Messages name Bosgame or this PC.
  - Drive reads retry once.
  - A failed read now shows a plain reason.

## Verification

- TypeScript passed. 293 frontend and 177 Rust tests passed; new tests cover snapshot start-up, tolerant settings, the Steam watcher, stick hysteresis, the Drive time parse and Drive reasons.
- Installs:

  | Time (GMT) | SHA-256 | Source |
  |---|---|---|
  | 00:19 | `073D0A06…42B3` | `46685cc` |
  | 00:31 | `DA4D2E25…7D37` | `a0af9a3` |
  | 00:41 | `73E82030…A5F6` | |
  | 00:52 | `5F783984…F7E3` | `07805ec` |

  Rollback copies are kept as `creteos.exe.before-<stamp>`.
- On device: after a restart, Home is fully shown 3 s after launch, with no splash and no refresh panel.
- Manage saves for Mouse reads "…matches the latest backup on Bosgame".
- Google Drive still showed "Couldn't reach Google Drive".
  - Reproduced over CreteOS's own SSH route; it is intermittent.
  - The surfaced reason was a Google API `ErrorInfo`. Together with rclone's notice that the shared client "is being retired and will stop working during 2026", this matches the shared-client rate limit already noted on 13 Nov.
  - **Owner action:** create your own Google client ID. The steps are in the guide under Game save recovery.
- `creteos-saves` was redeployed on Bosgame (`0a337921…417c`).
- **One hang observed:** the first start after the 00:41 install stopped responding, with almost no CPU and missing art, while clicks were made 4 s after launch. It did not reproduce in five more starts or in the same click sequence. Cause unknown. A watchdog is on the roadmap (batch 7).

Checkpoint archive `CreteOS-G1-profile-backup-20261114-005428.zip`:
- SHA-256 `8f1010709f6504928b3d3383e07e094f90dc6e9f25e38403524a2eeb8475d5e2`, 1,656 payloads.
- Pack `CreteOS-Recovery-20261114-Batch2InstantStart`: 168 files verified on the SanDisk, with source bundle `ed19c87`.
- Pushed.

## Power and fan profiles, Settings clean-up, Sleep (source `1c8b25a`, `d1b00f8`)

- **OneXConsole investigation** (read-only).
  - It sets TDP with RyzenAdj through its own re-applying service.
  - Per-game presets live in `%APPDATA%\gamerzone\db\core.db`:
    - `game_setting_preset`: `tdp`/`tdpPlugin`/`tdpPluginSep`, `fanTakeEffect`/`fanMode`, and the `rtssTakeEffect`/`rtssFrameValue` frame cap.
    - `game_setting_preset_process`: the binding to a process name.
  - Fan modes, from its own code: 0 automatic, 1 curve `setting.fanPreset1`, 2 curve `fanPreset2`. Each curve is five points (°C, duty).
  - Bindings live in memory and reload only after edits in OneXConsole's own window, so CreteOS's changes apply when OneXConsole restarts.
  - It runs elevated from the `\OneXConsole` scheduled task.
- **`performance.rs`.**
  - Reads and writes those tables through Windows' `winsqlite3` (no new crate).
  - Copies the default preset for new games and validates 4–30 W, fan 0–2, and an off or 20–240 FPS cap.
  - Takes a dated database copy in `%LOCALAPPDATA%\CreteOS\onexconsole-backups` before every write (20 kept), then runs `quick_check`.
  - CreteOS records each game's executable when its window is matched (`game_processes.json`). For a game never launched from CreteOS, the install folder's executables are offered.
  - "Apply now" restarts OneXConsole with one UAC prompt.
- **UI.**
  - Each installed local game's options gain **Performance**: power on battery and on charger, fan, frame limit.
  - Settings gains a **Performance** page (fan curve A/B editor, default power, per-game list) and a **Game saves** page (Drive folder and account, recent activity).
  - Both pages are controller-driven through the new `useDialogNav`.
- **Settings clean-up (Crete).**
  - Hidden: theme, accent and surface pickers; stars, lofi and onyx options; the hero-cover option; the duplicate pinned toggle; controller icon styling and preview; the coffee, Discord and privacy links.
  - Sections are now: Display & Home, Library & accounts, Performance, Game saves, Startup & streaming, Controller, Storage & troubleshooting, About & updates. French and Spanish keys were added.
- **Power menu.** Adds **Sleep** (no confirmation, `SetSuspendState` with SeShutdownPrivilege; the G1 supports only Modern Standby) and **Hibernate** (`shutdown /h`). Neither was exercised: there was nobody to wake the device.

**Verification**
- TypeScript passed. 294 frontend and 179 Rust tests passed, including fixture-database tests for preset create, update and remove, and validation.
- On the G1, through the UI: an Elden Ring profile (10 W battery, 18 W charger, fan curve A, 30 FPS) was written to OneXConsole's database.
  - Database copy before the change: `core-20261114-011404.db`.
  - `integrity_check ok`; the program was remembered as `eldenring`.
  - The profile was then removed through the same screen, and the database rows matched their original state.
- The Settings pages render real values (6/18 W default, curve A 35–85 %, Drive folder `ludusavi-backup-G1-Windows`). Keyboard and mouse navigation and Escape work.
- The Power menu shows Sleep and Hibernate.
- Installs at 01:25 (`968D093A…462E`) and 01:38 (`0FF15D50…296E`).
- **Hang repeated once (01:09):** clicks right after the first start of a newly installed exe. It was not reproduced in four controlled attempts. Still open.
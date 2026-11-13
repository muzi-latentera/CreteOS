# 13 November 2026 — Ludusavi game saves

Pending vault sync.

Bosgame already had Ludusavi 0.31.0, an authenticated rclone Drive remote, a Lutris root and 25 local backup titles. Drive listed 25 game folders. The initial upload was still active during setup. Preserve its existing `ludusavi-backup` folder and upload.

Lutris now reads a global post-exit command at `~/.local/bin/creteos-lutris-save-backup`. It waits for another Ludusavi process to finish, serializes its own jobs, resolves the exited game's title and backs up that game with ten-version retention. Automatic commands decline cloud conflicts and downgrade prompts. The script and prior system configuration allow rollback. The standalone hook wrote and verified a synthetic 13-byte save in an isolated home. No game was launched to test the live callback.

Windows CreteOS opens Ludusavi from **Game details → Launch options → Save backups**. It backs up eligible non-Steam Windows games when their matched game process exits. Steam and streaming clients are excluded from that Windows hook. Ludusavi keeps up to ten versions and restores only when the user picks and confirms a version. A Windows test kept two versions, previewed the older version without changing the active file, then restored it explicitly.

Cross-device test: transferred a copy of the Bosgame Mouse backup over SSH and previewed its two save files on Windows with a restore redirect to a temporary directory. Preview resolved 252 bytes and wrote no live files. This proves the path mapping for those files. It does not establish compatibility for every title, nor verify a game reading a restored save.

The installed hook also checked Mouse on Bosgame after the initial upload process exited. It returned success and found both save files. A direct read of Mouse's Drive mapping matched the host mapping SHA-256. This did not exercise a new upload from a changed game save.

G1 build and install verified at 01:47 GMT. App source `7e1a3c931b2e6761ffd2daf4efbabfd883ac4211`, SHA-256 `F42F74ECCC5BCE90CC7EDF0C9611420DFF85368359109C7C73DFD9735FCB92FB`. The Windows release build, bundled asset checks and 10 focused frontend tests passed. A real Windows game exit is still pending. Ludusavi 0.31.0 and rclone 1.76.0 are installed. G1 Google authorization is pending. The existing Bosgame folder stays in place, and Windows uses a separate repository after authorization. If G1 local backups already exist when authorization completes, verify the destination and perform the first Windows upload before relying on automatic cloud sync.

The full host settings export hit a missing previously recorded Phantom Blade Zero `dxgi.dll`. The private recovery archive uses the last verified host snapshot plus the fresh Lutris system file and save hook. The rest of that host snapshot is historical. No account tokens, OAuth config or game save data were put in the recovery pack.

Verified private archive CreteOS-G1-profile-backup-20261113-014801.zip, SHA-256 a59c398627541069c7f7329e496d8864920b58495055a4616a2229181128586f, contains 1650 manifested payloads. The preceding 12 November profile archive is retained. The replacement pack is CreteOS-Recovery-20261113-Ludusavi. Windows Drive authorization remains pending.

## Google Drive authorization at 07:48 GMT

The G1 authorized rclone and Ludusavi now uses its own ludusavi-backup-G1-Windows path with synchronization enabled. Listed both Games and the Bosgame backup, downloaded Mouse's mapping and both saves to a temporary directory, and verified a separate upload/download test by SHA-256. The test cloud file was removed. No game save was restored. Shared rclone client quota briefly interrupted listing and the shared client is being retired. New devices still need authorization. Verified private archive CreteOS-G1-profile-backup-20261113-074934.zip, SHA-256 0fc65033832e383a5ed937d554a28b6ecab7a686bb58a413f4b5488652c2bdc9, contains 1651 manifested payloads. The previous Ludusavi profile archive is retained. The replacement pack is CreteOS-Recovery-20261113-LudusaviDrive. G1 Drive authorization is complete.

## Bundled tools and pre-launch checks

At 11/13/2026 08:20:39, installed Windows app source 22f9bf76c97ce2d2c23d63c19cb09743935433aa, SHA-256 DA9ECDD6E87246A5B69BD018D422B2BE4312D3C3A50A9A53AD0653D31501B57D. Previous executable retained at C:\Users\Muzi\AppData\Local\CreteOS\creteos.exe.before-save-tools-20261113-082033. Verified embedded frontend assets, launched the installed build, opened Mouse's Save backups dialog and observed the bundled Ludusavi process. Both embedded executables and license files matched the prepared upstream assets. The old WinGet rclone path was migrated to the bundled tool on first use, preserving the existing Google authorization.

Standalone Windows games go through a read-only save check when Play is pressed in the CreteOS local launch dialog. Divergent backups or unavailable cloud checks present an explicit choice. Restoring remains in the included Ludusavi interface. The pre-launch check reads the Windows backup repository. Cross-OS imports from the Bosgame repository still need the documented save path mapping and an explicit restore. Steam, Epic, EA and other known launcher routes keep their existing behavior. Lutris backups still run on Bosgame through its installed hook.

Eleven focused frontend tests passed. The packaged tools backed up and synced a synthetic save through an isolated rclone target, and a read-only preview detected it. The empty Windows Google Drive backup folder was created and a read-only Ludusavi check succeeded. A separate Google Drive fixture upload was blocked by the shared rclone client quota. A real standalone Windows game exit and automatic Drive upload remain unverified.

Verified private archive CreteOS-G1-profile-backup-20261113-082055.zip, SHA-256 2d6a9efb0b4002d0755b56ef6277ed4fdb6fd546547d09d8077aca74ddcce909, contains 1651 manifested payloads. The previous Drive-authorized profile archive is retained. The replacement pack is CreteOS-Recovery-20261113-BundledSaves.
## In-app save comparison

Installed at 11/13/2026 21:31:00, app source 9b05ea0969920c2baf12f46ae16857a104a57b47, SHA-256 844C6B02ED96D7D32EDB4B57978B24B2E5C0BDC0B293B82DB2BD7731DEA93DDE. Rollback executable C:\Users\Muzi\AppData\Local\CreteOS\creteos.exe.before-save-comparison-20261113-213055. The installed executable and embedded frontend assets verified. Twelve focused frontend tests passed. A Bosgame Mouse read-only scan found an active save matching the on-host backup. The Drive lookup reports its own availability when quota or timeout prevents reading it.

Check now compares active save file modification times, device backup history and the latest timestamp in the matching Google Drive backup index. The local pre-launch review opens the same CreteOS dialog. Comparison never launches games, restores files, or writes a backup. A timestamp comparison does not establish which save is further into a game. Manual backup and restore controls are outside this change. Manage game resets the action pane's scroll and focuses its first control.

Verified private archive CreteOS-G1-profile-backup-20261113-213115.zip, SHA-256 35f8998de3e3000bd0ceceeaa85d3825b76518f7e2942ad9de34047772b51abf, contains 1655 manifested payloads. The preceding bundled-tools archive is retained. The replacement pack is CreteOS-Recovery-20261113-SaveComparison.

## Saves and product audit (late 13 November)

This was a read-only audit, recorded in [AUDIT_2026-11-13_SAVES_AND_EXPERIENCE.md](../AUDIT_2026-11-13_SAVES_AND_EXPERIENCE.md). Nothing was built, installed, restored or uploaded, and the SanDisk pack is unchanged. The installed build is still `9b05ea0`.

Measured on the G1:
- Ludusavi has no Windows backups.
- It finds saves for 11 installed Steam Cloud titles (45.7 MB), which the exit hook excludes by design.

Measured on Bosgame:
- The hook log holds only the 01:45 Mouse setup entry.
- The config says `retention.full: 5`, while the hook passes `--full-limit 10`.

The uncommitted manual backup, restore preview and restore backend remains unwired and unbuilt. Its use of `--no-cloud-sync`, combined with the automatic hooks' `--no-force-cloud-conflict`, would leave every later automatic Drive upload declined. Fix that before shipping. The audit also confirmed a Manage game focus-clamp bug in `GameDetailsModal.tsx:549-551,1251`.
## Manage saves, safe Drive sync and first full-app fixes (late 13 November)

Source changes:

- Windows (`game_saves.rs`):
  - Exit backups, manual backups and pre-restore checkpoints all write locally with `--no-cloud-sync`, then run `sync_cloud`.
  - `sync_cloud` uploads only when this device alone has new versions, downloads only when Drive alone does, and refuses a real conflict.
  - The rules come from Ludusavi 0.31 previews measured in an isolated config, with a local rclone remote standing in for Drive. In the upload preview, a Drive-only version shows as `Removed`; in the download preview, a device-only version shows as `Removed`; `mapping.yaml` changes on either side. Cloud reports arrive on stderr.
  - Checkpoints run with `--full-limit 255`, so the chosen version is never pruned. The checkpoint is then locked with the comment `CreteOS: before restore`, and three are kept. A sandbox check showed a locked version surviving `--full-limit 1`.
  - Results are recorded in `%LOCALAPPDATA%\CreteOS\game-saves-status.json`.
  - chrono gains its `std` feature for timestamps; Cargo.lock is unchanged.
- Bosgame:
  - `lutris_save_check.py` implements the same rules, plus `--after-exit` mode for the Lutris hook. It catches errors and returns reasons.
  - Status is recorded in `~/.local/state/creteos/lutris-saves-status.json`.
  - Drive previews take 57–98 s on Bosgame's shared rclone client, so one preview settles the common cases and each preview allows 150 s.
- UI:
  - New `ManageSaves.tsx` (replacing Save backups): automatic read-only check, Back up now, Restore a version with preview and confirm, and Sync with Google Drive.
  - Back and Escape step out of sub-views. The dialog can't be dismissed during work. Labels are explicit.
  - The Launch options menu no longer disappears during the streaming check.
  - Manage game focus bounds now follow the tab being opened.
- Other fixes from the full audit:
  - `set_launch_cover` now accepts the PlayStation providers. Before this, ASOBI tiles failed at the cover.
  - Dialogs with their own controller loop mark `data-owns-input`, so the "Close game?" confirm (and Manage saves opened from the Play dialog) no longer leaks presses to the screen behind.
  - ConfirmModal gained Enter and Esc.

Verification:

- Tests: TypeScript passed, 290 frontend tests passed, and 169 Rust tests passed.
- Bosgame sandbox (isolated HOME, local rclone remote, fixture game and Lutris DB, removed afterwards) passed all of these:
  - backup and upload
  - after-exit
  - preview
  - restore with the unbacked current save preserved as a locked checkpoint
  - conflict refusal that kept the Drive-only version
  - running-game refusal
  - unknown-game message
- Bosgame deployment at 22:40 GMT:
  - Backups were kept as `*.before-safe-sync-20261113` for the hook and Ludusavi config.
  - `~/.local/bin/creteos-saves` installed, SHA-256 `405155608816cb41915b99ac282e891e4a550224de365e7d8d2df5d2b8a94ac2` after the timeout revision.
  - The new hook (SHA-256 `53de7f1480e96fb0578852a8d432f3b17d2ce4b745391a99364ed9fb15e59d09`, mode 0700) was installed.
  - Retention went from 5 to 10.
- The live hook run for Mouse, without launching the game, reported "Save unchanged since the last backup. Google Drive is up to date." in 12 s, and the version count stayed at 1. The first live run, with 45 s preview limits, reported Drive unavailable. That run was the reason for the timeout revision.
- There is no standalone Windows game installed on the G1, so the standalone route can't be exercised here.

The full-app audit against SteamOS is in `docs/AUDIT_2026-11-13_SAVES_AND_EXPERIENCE.md`.
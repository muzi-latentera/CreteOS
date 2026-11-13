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
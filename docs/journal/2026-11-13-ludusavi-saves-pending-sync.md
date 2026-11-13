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

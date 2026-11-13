# 13 November 2026 — Ludusavi game saves

Pending vault sync.

Bosgame already had Ludusavi 0.31.0, an authenticated rclone Drive remote, a Lutris root and 25 local backup titles. Drive listed 25 game folders. The initial upload was still active during setup. Preserve its existing `ludusavi-backup` folder and upload.

Lutris now reads a global post-exit command at `~/.local/bin/creteos-lutris-save-backup`. It waits for another Ludusavi process to finish, serializes its own jobs, resolves the exited game's title and backs up that game with ten-version retention. Automatic commands decline cloud conflicts and downgrade prompts. The script and prior system configuration allow rollback. The standalone hook wrote and verified a synthetic 13-byte save in an isolated home. No game was launched to test the live callback.

Windows CreteOS opens Ludusavi from **Game details → Launch options → Save backups**. It backs up eligible non-Steam Windows games when their matched game process exits. Steam and streaming clients are excluded from that Windows hook. Ludusavi keeps up to ten versions and restores only when the user picks and confirms a version. A Windows test kept two versions, previewed the older version without changing the active file, then restored it explicitly.

Cross-device test: transferred a copy of the Bosgame Mouse backup over SSH and previewed its two save files on Windows with a restore redirect to a temporary directory. Preview resolved 252 bytes and wrote no live files. This proves the path mapping for those files. It does not establish compatibility for every title, nor verify a game reading a restored save.

The full host settings export hit a missing previously recorded Phantom Blade Zero `dxgi.dll`. The private recovery archive uses the last verified host snapshot plus the fresh Lutris system file and save hook. The rest of that host snapshot is historical. No account tokens, OAuth config or game save data were put in the recovery pack.

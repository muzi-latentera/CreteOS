# 12 November 2026 — Reset Lutris play history

Pending vault sync.

Reset playtime and last-played in Bosgame's Lutris database in one transaction. Checked all other columns against their original values and verified SQLite integrity. The live bridge returned 14 launchable games with zero minutes and no last-played timestamp. Replaced the Windows Lutris inventory from that bridge and removed four Lutris records from each CreteOS recents file. Compared all other recent entries to the preceding verified backup. CreteOS was restarted and no game was launched as part of the reset. Future sessions can record new play history.

The installed executable remains source `8bf81bc81ca528cc9c0814d490c4a0b0b7fbe166` with SHA-256 `D59B377CBA07749BF73FA920E1B4F82CD3CF857438F02E3905FF5F9F2CFD22BB`. The existing local launch overlay paints the selected hero image and loading messages before dispatch. The dark background covers titles without a hero image.

The full host export encountered a missing `dxgi.dll` at the recorded Phantom Blade Zero OptiScaler location. The replacement archive includes a fresh post-reset copy of the host Lutris database. Its remaining host files are the preceding verified references. Those carried-forward files were hash-checked against the prior manifest and their current on-host state was not reverified.

Verified private archive `CreteOS-G1-profile-backup-20261112-081540.zip`, SHA-256 `dd844c62758cb61d71ab81758a43a4de54ee2df5084c141ff8ee8d076185142e`, contains 1638 manifested payloads. The preceding `CreteOS-G1-profile-backup-20261112-021610.zip` is retained. Replacement package `CreteOS-Recovery-20261112-LutrisHistoryReset` uses the fresh archive.

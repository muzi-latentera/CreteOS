# 11 November 2026 — Philips TV streaming V-sync

Muzi requested V-sync on the TV for Moonlight and GeForce NOW after a 4K60 session showed tearing. CreteOS uses Windows' active monitor identity. The Philips FTV EDID is `PHL04C3`, the G1 panel is `HSX0224`, and XREAL 1S is `MRG4102`. The rule follows the Philips screen across dock ports and resolutions. A dock with the G1 panel still active does not select the TV rule.

Moonlight receives `--vsync` for a CreteOS stream on the Philips. The installed Moonlight 6.2.0 CLI advertises this option. The launch argument applies to that stream and the native preference remains available on the G1 and XREAL. Frame pacing and the host game's settings are separate.

GeForce NOW receives V-sync On through its existing Custom profile warm-up and readback. The installed NVIDIA app's Gameplay settings saved `customProfile.vSync=1` when set to On, and the prior value `0` when returned to Off after verification. No game was started during that check. CreteOS remembers the previous value before setting the shared NVIDIA profile for the TV. The first later non-TV GeForce NOW game launched from CreteOS restores that saved value if the TV setting is still present. CreteOS also checks the new value before starting the game. The home-screen quick launch uses the same setup when on the TV or when an earlier TV override needs to be restored. GeForce NOW started directly outside CreteOS uses its own current global profile.

All 13 native streaming tests passed, covering Philips detection across port suffixes, exclusion of the G1 and XREAL, NVIDIA V-sync readback, restoring the prior native setting, and preserving a changed native setting. The G1 panel was the only active display during implementation. A physical docked gameplay test is pending.
Installed on the G1 at 2026-11-11T17:36:58.6511368+00:00 from `ef63c911fc82b6f093fc3461e40bc92819ce59de`. Executable SHA-256 `3FC8C3C16677A69AB69E68DE33F95CF3A772B98BB9157B130CDC7CC068505801`. The production build and installed asset check passed. The installer verified 8 saved profile files and retained rollback at `C:\Users\Muzi\AppData\Local\CreteOS-Bootstrap\app-updates\Tv-vsync-20261111-173655`.

Verified private snapshot `CreteOS-G1-profile-backup-20261111-173724.zip`, SHA-256 `dc5be14020ad6122a00b22684e94ff403dbb77995645d7705dde88920d6bd69f`, contains 1599 manifested files and the exact installed executable. The 172256 archive is retained. The local replacement package is `CreteOS-Recovery-20261111-TvVSync`. Use its manifest and USB copy receipt to identify the deployed build. The USB copy requires a connected SanDisk.

Canonical vault sync pending.

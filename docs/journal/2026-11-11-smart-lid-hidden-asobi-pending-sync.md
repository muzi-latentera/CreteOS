# 11 November 2026 — Smart Lid console flashes and ASOBI stream comparison

## Smart Lid

The previous installation launched PowerShell directly from both interactive scheduled tasks. Its supervisor also ran a console-mode powercfg process every three seconds. This supplied two paths for focus-stealing console flashes. Scheduled launches now use a windowless WScript launcher that starts the same supervisor and recovery action hidden. Power plan queries and writes run through ProcessStartInfo with shell execution disabled and CreateNoWindow enabled. The hibernation observer executable and 30-second countdown are the same.

Reinstalled with the lid open, preserving the original Balanced plan, dock hardware IDs and delay. Verified the live observer, managed plan and fresh heartbeat. Both registered actions now point to wscript.exe. The recovery task completed its normal scheduled run with exit code 0. The new supervisor script SHA-256 is `23EC9C7659CB2DDFB5FB17B1ECA0E8F474C7572ED5900E1198CC6886CC39B94B`, windowless launcher SHA-256 `D5C09825E1CEEF497868D6B9B5BE5926DAB81CD0DABE2F63E2C58C108746AAA5`, unchanged SmartLid.exe SHA-256 `DB85D00622141CE914B0179047D190D6B8BB27C8D602C58D61212FE86C8E1D53`. The pre-change files are in the private workspace rollback directory `work/smart-lid-before-hidden-20261111-140543`. The first live docking sequence earlier today showed lid closed, countdown started, dock arrival and countdown cancellation. Live undocked hibernation and resume still await a physical cycle.

## ASOBI

The 13:58:14 session started while the G1 was docked. ASOBI decoded 1920 × 1080 HEVC through Vulkan and upscaled to approximately 4K on the Philips display. ASOBI's transport samples had a median RTT of 71.85 ms, with 162 of 244 over 30 ms, and a 30.2–97.1 Mbps target. The separate 30-second heartbeat samples reported up to 904.1 ms RTT and 542.3 ms delivery jitter. Host stream cadence stayed at 59.9 FPS. At 14:01:36 the dock disconnected and heartbeat RTT was 5.3 ms at 14:01:44. The 14:04 handheld repeat reported 5.75 ms median RTT, including a 912.5 ms startup outlier. After its first two startup samples, median was 5.55 ms. Windows connected to Fibre E433 at 13:53:27 before the laggy session. No Wi-Fi roam or adapter reset was logged during either run. The idle Wi-Fi helper had switched to E433 on sustained transfer before the session.

The evidence identifies severe timing variation during docked streaming and improvement after unplugging. It does not isolate the router, PS5 path, docked renderer/USB interference or the console flashes as the sole cause. No ASOBI settings changed.

## OneXConsole

After Muzi selected the all-chargers policy, changed the existing Desktop process preset to 6 W on battery and 12 W on external power using OneXConsole's native split values. The TV hub uses 12 W when it supplies external power, just like an ordinary charger. Verified the committed fields (tdp=6, tdpPluginSep=1, tdpPlugin=12), Desktop binding and SQLite integrity. The Onimusha 15 W profile and other preset rows were preserved. A consistent pre-change database is saved as `work/onex-audit/before-ac-desktop-12w-20261111-141031.db`. OneXConsole's existing elevated process retained its PID after a normal relaunch request, and its window did not become targetable. The new values are saved for its next restart or sign-in; live wattage application was not verified. The ASOBI lag comparison predates this preset change.

Canonical vault sync pending.

Verified private snapshot `CreteOS-G1-profile-backup-20261111-141310.zip` contains 1581 payloads and has SHA-256 `5f83e5c5bc36e967dd70f0b373423705bce8c93f66d88c789f99ca13c602983d`. Independently checked the archive hash, windowless Smart Lid launcher, both updated tasks and the 6 W battery/12 W external power OneXConsole profile. The 131221 archive is retained.

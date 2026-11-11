# 11 November 2026 — restore Smart Lid after resume and prefer ASOBI SDL

## Smart Lid

The observer now counts awake time for both countdown and timeout. A Windows resume event or a gap between elapsed and awake clocks refreshes the lid subscription and rescans the dock. Unknown lid state triggers re-registration while its awake-time recovery window remains open. The supervisor restores the original plan if the observer exits and attempts to restart it. It re-arms the managed plan only after a fresh known-state heartbeat. If the independent guard restored the original plan, its timestamped signal lets the supervisor distinguish that recovery from a manually selected power plan. Three quick consecutive failures leave Windows' native action active.

The first deployment attempt used a precise awake-clock export unavailable in the G1 process environment. It never activated the managed plan. Switched to QueryUnbiasedInterruptTime and verified the API in the final executable before reinstalling. Native compilation with warnings as errors and the 18 countdown assertions passed. Installed SmartLid.exe SHA-256 `2BD391A40EA1D07827BCD6DF060059568CFB97161B56A8DB874E5EF7763E2234` and supervisor SHA-256 `723DBB6BECD907DBEE90FDEA71108EDB8A35A188D02FB5514BCB5F74706A2758`. The windowless launcher and 30-second configuration remain in use. The previous working files are under the private workspace rollback folder `work/smart-lid-before-resume-20261111-154305`.

With the lid open, deliberately stopped observer PID 10844. The supervisor restored Balanced, started PID 26728, observed a fresh lid/dock heartbeat and reactivated the managed plan within 4.74 seconds. Both scheduled tasks remained enabled. A physical hibernation/resume cycle using the new build was not performed during deployment.

## ASOBI

Changed only the native ASOBI controller_sdl preference from false to true while the app was closed. This opts into SDL directly in place of the Steam Input discovery wait that previously reported no pads. ASOBI then opened to its home window and logged the built-in Xbox controller connected. The PS5 stayed in standby during the smoke test. No other ASOBI setting or credential was copied or changed. If a controller needs Steam Input-specific mappings, switch its backend preference back in ASOBI. A physical dock hot-plug and external pad retest is pending. SDL cannot prevent a USB controller from dropping off its dock.

Verified private snapshot `CreteOS-G1-profile-backup-20261111-154527.zip` contains 1585 payloads, SHA-256 `a8b8860c4953f8ddb62979f29e08cf30a8c410bdf474f4f70e2c84f00000d30b`. Verified archive contents, exact installed Smart Lid files and the allowlisted ASOBI SDL preference. Preserved the previous 144046 archive.

The refreshed local replacement pack is `CreteOS-Recovery-20261111-Resume`. Its Smart Lid installer applies the new helper after the replacement's dock and power plan are identified. After fresh PSN pairing, the backed-up ASOBI preference can be reviewed and reapplied in ASOBI. The SanDisk copy receipt identifies the last physically copied package.

Canonical vault sync pending.

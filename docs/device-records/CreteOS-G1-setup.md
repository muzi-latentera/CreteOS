# CreteOS G1 setup — 17 October 2026

18 October follow-up: Muzi installed the graphics update, changed VRAM to 16 GiB and confirmed Onimusha is working. Game Bar was restored. SSH access from the G1 now works with its own key and a host key supplied directly by Muzi on Bosgame; native `ssh bosgame` is configured. Buddy pairing was completed using a temporary instance on Bosgame's physical Plasma desktop, then its original streaming service was restored. Muzi subsequently confirmed a good Onimusha streaming session; both-end logs were reviewed (see Streaming-review-20261018-2209.txt).

Device: ONE-NETBOOK ONEXPLAYER G1 A, Windows 11 Home build 26200.
Initial source: d17320cb418c0ab624b8bf8c3dd5c7160c8dcf5d, codex/windows-liftoff. Current installed source: 4b1999d6781f64dbe25ad5f59a9eff54f1587753, codex/g1-smart-wifi; retains Steam password login and G1 display updates.

- [x] Resolve ChromeOS/CreteO S naming with Muzi: CreteOS for Windows.
- [x] Read current guide, artifact identity and historical handoffs.
- [x] Verify SanDisk and local copy: 806 manifest files; receipt manifest hash matches.
- [x] Inspect native profile: no existing CreteOS; OneXConsole 0.8.10-fix3 running.
- [x] Install verified CreteOS and verify executable identity/native profile.
- [x] Import portable profile once: 643 files, zero missing/binary mismatches; backups and path rebasing. Onboarding skipped at Muzi's request.
- [x] Desktop appearance and controller accepted by Muzi on G1; local Steam manifest now reports Onimusha installed.
- [x] Steam integrity verification completed without errors; CreteOS native dispatch and direct Steam comparison tested.
- [x] Onimusha startup resolved after user-installed graphics update and VRAM change; user confirmed working. Driver and VRAM changed together, so individual causation is not established.
- [x] Moonlight 6.2.0 and native GeForce NOW 2.0.89.154 installed; this G1's Luna Chrome app association detected and configured.
- [x] Moonlight paired with Bosgame by Muzi; MoonDeckStream visible. GeForce NOW signed in by Muzi.
- [ ] CreteOS Steam/Microsoft/Epic/GOG fresh sign-ins remain required where not completed by Muzi.
- [x] G1 SSH alias/key authorization and independent host identity pinning; Buddy pairing confirmed in CreteOS; 32 installed host games detected including Onimusha.
- [x] OneXConsole 0.8.10-fix3 startup observed after restart; native settings preserved. No per-game presets currently configured.
- [ ] Verify OneXConsole per-game switching from CreteOS with the user; only Onimusha is installed.
- [x] AnyFSE 0.90.21 installed by Muzi; actual CreteOS launcher configured through supported UI, startup home mode enabled, exit-to-desktop enabled, administrator launch off.
- [x] G1 display identity detected: internal panel 2560×1600. Saved stream targets 2560×1600/120: Moonlight 80 Mbps/Auto codec; GFN Auto bitrate. External display identities not copied.
- [x] NVIDIA native Custom quality initialized and read back as 2560×1600/120/Auto bitrate; legacy Balanced metadata corrected with backup.
- [ ] Coordinate home-mode reboot and hibernate/resume.
- [ ] Physical input, touch, native/cloud launch/return and dock/output acceptance.

Appearance/controller, native Onimusha and a short Moonlight Onimusha session are user-confirmed G1 results. GFN/Luna gameplay and return, dock/output, home-mode restart and hibernate acceptance remain pending. Earlier GPD results are reference evidence only.
No live canonical vault found or CRETE_VAULT_ROOT configured; session records are pending vault sync.

## Historical native game test result — 17 October

Steam verification finished at 15:34:26 BST with No Error for Onimusha and Steam Common Redistributables. The CreteOS launch correctly dispatched the installed game through Steam. Direct Steam launches at 15:35:59 and 15:36:35 reproduced an unexpected startup crash, including a screenshot-only comparison. No gameplay menu was reached. The game and its crash reporter exited; no game process was forcibly terminated. CreteOS was restored to Home, with Onimusha showing Local/Play.

The reporter also failed with .NET Win32Exception 126 in window automation. A partially obscured AMD driver notice was visible during one startup, but its full text and the original crash cause were not established. Installed AMD Radeon 890M driver is 32.0.12011.6001, dated 2024-11-01. No speculative driver, firmware, VRAM or power changes were made. File verification did not resolve the failure. This initial failure was superseded on 18 October: Muzi installed Adrenalin 26.8.1, increased VRAM to 16 GiB and confirmed Onimusha working. The individual contribution of those two changes is unproven.

## Exact installation and recovery

Initial CreteOS executable SHA-256: `91864db7de4aea0663b386367d331f808ea7bc6290e9d98cfd63cd002c02d8f1`.
Installer SHA-256: `328863e61a8735426a8fb64529252600c453a15458ebd4d16b81b9995749bf33`.
Native profile: `C:\Users\Muzi\AppData\Local\CreteOS`.
Profile backup: `C:\Users\Muzi\AppData\Local\CreteOS-Bootstrap\G1-import\20261017-145249-962`.
Home configuration backup: `C:\Users\Muzi\AppData\Local\CreteOS-Bootstrap\home-setup\20261017-151623`.
NVIDIA settings backup: `C:\Users\Muzi\AppData\Local\CreteOS-Bootstrap\gfn-settings\initial-G1-20261017-152511.json`.

The first tool-launched install was redirected into Codex's package cache. A native WMI-launched Windows PowerShell installation/import corrected this; Computer Use independently identified the running executable at the normal native path. Do not repeat the initial import.

The outer NVIDIA package launcher requested Windows DirectPlay and stalled on the Windows Features screen; Muzi force-restarted the G1. The profile survived. The unmodified signed inner NVIDIA installer subsequently completed with quiet/no-reboot options and the native client opened successfully. No DirectPlay/security/driver/power changes were applied by the agent.

## Remaining access requirements

Bosgame resolves to the verified LAN address. Sunshine and Buddy ports were briefly unavailable during follow-up and later recovered; Buddy API 8 was verified against the bundled pinned certificate. The server reported free/no active game. Reachability and cached apps do not establish smooth streaming.

MoonDeck Buddy pairing is separate from Moonlight. The rejection was traced to host logs showing an already-pending pairing. The normal Buddy service was subsequently stopped, and its existing wrapper routes GUI windows to the separate Sway streaming desktop. A temporary Buddy GUI instance was started on the physical Plasma desktop while Sunshine was free; Muzi paired the G1, CreteOS confirmed authorization, then the temporary unit was stopped and the original moondeckbuddy.service restarted. No persistent host service files were changed, and existing pairings were retained.

Muzi authorized the fresh G1 public key directly on Bosgame and supplied its public host key and username. Dedicated known_hosts and bosgame.json are now configured under native `CreteOS-Bootstrap\ssh`. The standard native `.ssh/config` provides `ssh bosgame` with the dedicated G1 key, identity-only authentication and strict pinned host verification. A batch-mode SSH test succeeded as muzidube/Bosgame; the actual CreteOS inventory script found 32 installed Steam games including Onimusha. No private keys were copied from another device or into notes/source.

Earlier account check showed Steam/Microsoft disconnected and Connect for Epic/GOG. At the 19 October password-update UI check, Microsoft was connected with60 titles cached; Steam still awaited user sign-in. Epic/GOG were not rechecked. Imported cached ownership/art is retained; live provider authorization must be completed on G1. GFN client sign-in is separate and was observed working.

## Home mode and rollback

Windows GamingHomeApp is `ArtemShpynov.AnyFSE_by4wjhxmygwn4!App`; StartupToGamingHome is 1. AnyFSE targets the normal installed creteos.exe, has no duplicate OneXConsole startup entry, and has ExitFSEOnHomeExit enabled. Settings were reopened and checked after saving. A normal Start-menu AnyFSE launch opens its configuration; that is its documented desktop behavior and does not prove a home-mode login test.

On return, restart once after saving work to observe CreteOS home startup, OneXConsole and exit to desktop. To roll back, disable “Enter full screen experience on startup” in AnyFSE Settings or switch the Windows Gaming home app. No Explorer shell replacement or automatic sign-in was configured. No unattended reboot or hibernate was performed.

## G1 display update — 18 October 2026

Installed source commit 85ca455635901250a5755f36756ef41987ca450a at the existing native app path. Current executable SHA-256: e1dfaf3be0fca96af1273a98a7bc055451905716a4d522d3a28ddd6cb283b41a. The old executable is backed up at C:\Users\Muzi\AppData\Local\CreteOS-Bootstrap\app-updates\G1-display-20261018-225418\creteos.exe. Profile/settings backup: C:\Users\Muzi\AppData\Local\CreteOS-Bootstrap\G1-display\20261018-224405.

The internal panel is 2560×1600 at 144 Hz. Both stream profiles target 2560×1600 at 120 FPS. The launcher now detects the actual display and shows G1 · 1600p / 120fps instead of GPD. Selecting the G1 preset preserves GFN Auto bitrate; Moonlight retains 80 Mbps and Auto codec. Saved profiles and the native NVIDIA Custom settings were read back. Both installed CreteOS preset dialogs were checked; NVIDIA Settings also displayed QHD 2560×1600/120/Auto. A new GFN gameplay session was not tested.

Source typecheck/frontend build, 238 unit tests and two focused Playwright tests passed using Node 24. Windows release cross-built successfully in an isolated Bosgame directory using cargo-xwin, then remote/local executable SHA-256 matched. Build used an empty optional SGDB_API_KEY; existing cached artwork is visible, but live SteamGridDB lookups were not verified. Original installation resources and profile were retained; onboarding did not reappear and Buddy remained paired. No push or PR was created. Full evidence: G1-display-update-report.txt, G1-display-update-install.json and G1-setup-verification.json. Canonical vault remains unavailable; journal entries are pending sync.

Remaining: sign in to desired CreteOS Steam/Microsoft/Epic/GOG integrations; check actual GFN/Luna launch and return; test AnyFSE cold startup and hibernate/resume; verify desired OneXConsole per-game behavior and physical docks/external displays. No further profile import is needed.

## OneXConsole presets — 18 October follow-up

Saved and independently verified: OnimushaWotS process → 15 W; Desktop process fallback → 6 W on battery and AC. Current setting 6 W, automatic startup enabled, manager popup disabled. No fan/CPU/controller overrides enabled. Actual launch/exit power transition remains to be observed during play. See OneXConsole-preset-verification.json.


## 19 October 2026 — Steam password option (pending vault sync)

User requested username/password instead of mandatory QR. Implemented source6135a2ee7aaad9fa5da4580bdf4a313a30364cbc on codex/steam-password-login. RSA public-key password encryption, Steam Guard email/authenticator/approval handling, browser email initiation, session cancellation and guarded completion/cache writes; QR retained. No passwords/codes persisted; existing Windows Credential Manager token integration reused. Updated privacy, setup and upstream notes. Source build18Oct; actual native install19Oct00:00BST. ExeSHA256f94e9817d5b19c1ef5afe78d2ef37ddc582c5ff97ae7d53a6dd196d944c90966; previous executable backed up under native CreteOS-Bootstrap/app-updates/Steam-password-20261019-000012. Build+typecheck,238unit,4browser and5isolated production protocol/crypto/cancellation tests passed. Full Windows release cross-build succeeded on isolated Bosgame toolchain; source/generated lockfile recorded. Native empty form observed; Home/artwork and Microsoft connection present, four checked profile/settings files unchanged. No real account sign-in submitted or accepted by agent; user to verify Steam Guard flow with their own details. No host streaming/service/power changes, no push/PR. Existing optionalSGDBkeyempty limitation retained. See outputs/Steam-password-login-report.txt and Steam-password-native-verification.json. Canonical vault absent; pending sync. Current installed source supersedes85ca455 while retaining G1 display update.

## 19 October 2026 23:44 BST — G1 automatic home Wi-Fi (pending vault sync)

User requests FibreE433 for downloads and Extra otherwise; explicitly chose Moonlight priority. Installed limited-user logon task CreteOS WiFi Smart and native Bootstrap/WiFiSmart helper with automatic, two-hour manual Gaming/Download and persistent Pause controls. Extra default at startup/resume; Steam/DO hints and sustained receive traffic choose Fibre, with two-minute quiet/five-minute minimum residence. Active streaming clients preserved; CreteOS prepares Extra before Moonlight/GFN launches through optional helper. Corrected stale WLAN visibility with native scan; association waits for connected state up to12seconds; app preflight bounded20seconds. Source final4b1999d6781f64dbe25ad5f59a9eff54f1587753, branch codex/g1-smart-wifi.16policy tests,3browser regressions, frontend build/typecheck and Windows release cross-build passed. Live final Extra→E433 Internet(HTTPS Steam200,HTTP Microsoft200)→Extra via PrepareGaming passed; Automatic mode/currentExtra retained. Actual cold-login/hibernate, live stream continuity and each launcher download remain separate acceptance, not inferred from policy tests. HelperscriptSHA8f950f95190bb808403d6042b5495d638234f76287f649cc9619f5997470d1cc, policySHAfff2c6db5552e479fd21a057951f3ccdec2e2ccd63b4af8c0549b64ddb3daa5b. Final installed exeSHAbdd4e8192212428b6a80a41dd82169f7e7890b1d4236b5740f445c99936268b0 at native CreteOS path; previous0890175 app backed up under app-updates/WiFi-smart-20261019-234404. Interim089 actual install remains separately recorded; builds5bb/4b not conflated. App running, four checked profile/settings files unchanged, no import repeated. No router/DNS/driver/OneX/Buddy/SSH changes; no power cycle, no credentials exported, no push/PR. Full evidence outputs/WiFi-smart-setup-report.txt. Canonical vault unavailable; pending sync. Current installed app now4b1999d with Steam password and G1 display changes retained.

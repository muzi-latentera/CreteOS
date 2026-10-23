# CreteOS on OneXPlayer G1 — setup, history and recovery

Maintained device record, updated 23 October 2026 (Europe/London). Covers this G1 installation from 17 October onward. Use this as the entry point for another Windows handheld; use the dated journals for detailed evidence. Earlier GPD results and the USB's pre-17-October history are references, not acceptance results for this G1.

## Current state

| Item | Recorded state |
| --- | --- |
| Device | ONE-NETBOOK ONEXPLAYER G1 A, Windows 11 Home build 26200, computer OXPG1, user Muzi |
| Panel | 2560 × 1600 landscape, 16:10, 144 Hz; streaming profiles target 120 FPS |
| Native installation | `%LOCALAPPDATA%\CreteOS\creteos.exe` |
| Personal data | `%LOCALAPPDATA%\CreteOS`; preserve this directory during updates |
| Bootstrap/helpers/backups | `%LOCALAPPDATA%\CreteOS-Bootstrap` |
| Source branch | `codex/g1-smart-wifi`; contains the G1 display and Steam password-login changes too |
| Installed executable source | `babbdf9bfc2a00c50a082b7e6a3170cddc6a7acf` |
| Installed executable SHA-256 | `A3041EB593A14CA0C1307BAB89D77A9F3C34CF979232861E2E0E8E4472034ADB` |
| Build / install | Built locally on OXPG1 23 October 16:07:04–16:10:01 BST; installed 16:10:31 BST |
| Previous executable | `CreteOS-Bootstrap\app-updates\Provider-lifecycle-20261023-161028` |
| Luna Companion | 0.1.2, Chrome extension `gmneapkhejdngoenacdnamaddbfcihab`, permanent folder `CreteOS\LunaCompanion` |
| Latest live acceptance | Luna advancing playback, 242.269 seconds saved, ended detected, native launch/return artwork events and focus returned to CreteOS |

The app version remains 0.1.0 across multiple builds. Identify an artifact by source commit and executable hash, never by version or file date alone. Documentation commits after the executable's source commit do not imply a newer executable was installed.

## Latest log review — 23 October

The latest recorded Luna session entered waiting → attention → playing, then briefly buffering/playing/waiting → ended. Native artwork was shown at launch and again on return. Return-cover-painted preceded return-cover-released; foreground was restored to CreteOS. The native profile's `luna_playtime.json` contains **242.2691579 seconds** for the tested Galactic Racer entry. The earlier status snapshot reported connected=true, ever_played=true, persisted_seconds=242 and four ignored premature end events.

Fresh native inspection at 16:35:40 BST found the expected executable hash and CreteOS responding, with no Moonlight or NVIDIA streaming client in the inspected process list. The retained last eight stream issues predate this replacement build; the latest test added no new entry to that issue tail. These logs establish working playback accounting, end detection and return handling. They are not a frame recording and cannot establish that every transition was visually flash-free. Duplicate return observer notifications were present, followed by normal cover release and a responsive app.

The failed earlier 15:33 test ended tracking before any live frames were recognized and saved no playtime. That time cannot be reconstructed. The replacement rejects premature end reports, and only credits advancing playback. See [the latest live evidence](device-records/2026-10-23-luna-live-review.json) and [implementation history](journal/2026-10-23-provider-lifecycle-implementation-pending-sync.md).

## What was installed and transferred

On 17 October the SanDisk pack was verified against its manifest (806 files). CreteOS was installed from the supplied `codex/windows-liftoff` source/artifact baseline. A **single** portable import transferred 643 profile/artwork files with no missing files or binary mismatches, backed up existing data and rebased paths. Onboarding was skipped as requested. Do not rerun this initial import over the current live profile.

The import preserved library metadata, artwork, history and settings. Authentication is separate: cached library data is not a transferable provider login. Fresh Steam/Microsoft/Epic/GOG authorizations must be completed where required. GFN client login, CreteOS Steam integration and Steam inside Bosgame/GFN cloud sessions are separate logins. No account passwords, tokens, private keys or wireless keys belong in this record or Git.

Initial import backup: `CreteOS-Bootstrap\G1-import\20261017-145249-962`. Home setup backup: `CreteOS-Bootstrap\home-setup\20261017-151623`.

Moonlight 6.2.0 and native GeForce NOW 2.0.89.154 were installed. The existing Amazon Luna Chrome app was detected and configured. AnyFSE 0.90.21 was installed by Muzi. OneXConsole 0.8.10-fix3 was already present. Lossless Scaling 3.2.2 was later installed by Muzi.

The NVIDIA outer installer initially requested DirectPlay and stalled. The signed inner installer subsequently completed quietly without a reboot. Do not repeat the stalled outer setup or infer that DirectPlay was required.

### Native AppData matters

Packaged Codex/tool processes can see a virtualized AppData view. The first install and some later launches accidentally used that view, producing an unrelated Welcome/onboarding instance. Those instances were closed; the normal native installation and profile were retained. For maintenance use native Windows PowerShell launched outside that package context, verify the actual executable path and hash, and inspect the native profile. During this work a hidden WMI `Win32_Process.Create` launch was used for that purpose. Never accept an onboarding screen as evidence that the real profile vanished.

## Home experience, controller and power

AnyFSE is the Windows Gaming Home app (`ArtemShpynov.AnyFSE_by4wjhxmygwn4!App`). StartupToGamingHome=1. Its target is the actual native `creteos.exe`; launch as administrator is off and exit-to-desktop is enabled. No Explorer shell replacement was used. Muzi accepted CreteOS appearance and controller behavior on the G1. Cold boot, hibernate and every dock/display combination still need explicit device acceptance; imported GPD tests do not cover them.

OneXConsole's startup task was observed enabled and starting hidden. Saved bindings were independently read back on 18 October:

- Default Preset: **6 W**, AC and battery, bound to **Desktop process** (`__virtual_desktop`).
- Onimusha G1: **15 W**, bound to `onimushawots`.
- Automatic startup enabled; manager popup disabled. No fan, CPU or controller overrides added.

Actual launch/exit wattage transitions were not measured during play. A new handheld needs its own supported vendor utility and process bindings; do not copy the G1 vendor database blindly.

On 23 October, at Muzi's request, automatic Windows login and no-resume-sign-in were configured. Official Microsoft Sysinternals Autologon was downloaded, its Microsoft signature verified, and Muzi entered the password directly in that utility and enabled it. Readback: AutoAdminLogon=1; `HKCU\Control Panel\Desktop\DelayLockInterval`=4294967295 (Never); every existing power plan AC/DC CONSOLELOCK=0. No password or LSA secret was read or put in a script. A real cold boot/hibernate round trip was not performed to verify password correctness.

Reapply the power-plan portion in administrator PowerShell on a replacement device only if the same behavior is wanted:

```powershell
powercfg /list | Select-String 'GUID: (\S+)' | ForEach-Object {
  $g = $_.Matches[0].Groups[1].Value
  powercfg /setacvalueindex $g SUB_NONE CONSOLELOCK 0
  powercfg /setdcvalueindex $g SUB_NONE CONSOLELOCK 0
}
powercfg /setactive SCHEME_CURRENT
```

Autologon must be enabled with that device's account password locally. To undo on the G1, use Autologon Disable, restore DelayLockInterval=0 and the previous Balanced AC/DC CONSOLELOCK=1 values. See [the login configuration record](device-records/windows-login-20261023.txt).

## G1 display and streaming settings

The GPD preset label was replaced by detected G1 display identity. The internal panel is 2560×1600 at 144 Hz. Saved Moonlight and GFN targets are 2560×1600 / 120 FPS. Moonlight uses 80 Mbps and automatic codec; GFN retains **Auto bitrate**, including when applying the G1 preset. NVIDIA native Custom settings were read back at the same resolution/FPS/Auto bitrate. Old external-display identities were not applied as G1 identities.

The display update was installed 18 October from `85ca455`; its changes remain in the current branch. That initial build and later early builds used the cached Bosgame cross-compiler. Since Muzi's 22 October instruction, builds have run **locally on the G1**.

16:9 games can still letterbox within a 16:10 stream: 2560×1440 content leaves 80 pixels above and below at 2560×1600. Increasing stream resolution cannot create additional game field of view. Windows portrait orientation/zoom were manually corrected by Muzi during earlier troubleshooting; this latest provider work did not change Windows resolution, orientation or DPI.

## Moonlight, SSH and MoonDeck Buddy

Bosgame is the Linux streaming host. The G1's Moonlight pairing was completed by Muzi; MoonDeckStream is advertised. **Moonlight pairing and CreteOS-to-Buddy pairing are distinct.**

Buddy initially had a pending pairing and its existing service routed GUI windows to the dedicated Sway streaming desktop. A temporary Buddy instance was run on Bosgame's physical Plasma desktop while streaming was idle. Muzi accepted the PIN there. The temporary instance was stopped and the original `moondeckbuddy.service` restored. No permanent service-file changes were needed for that pairing. Keep existing pairings; do not replace them to solve an unrelated session error.

The G1 has its **own SSH key**. Muzi authorized its public key on Bosgame and independently supplied the host public key and username `muzidube`. A strict pinned host identity and dedicated key/config live under `CreteOS-Bootstrap\ssh`; standard `%USERPROFILE%\.ssh\config` exposes `ssh bosgame`. Batch-mode SSH and CreteOS's read-only Steam inventory both succeeded, finding 32 installed host games at initial setup. The host inventory reads installed Steam app IDs; MoonDeckStream's existence alone does not prove that a particular game is installed.

On another device generate a fresh key, authorize only its public key on Bosgame, verify Bosgame's host fingerprint independently, and configure the alias with IdentityFile, IdentitiesOnly and strict host-key checking. Use the app's current Bosgame configuration format; do not copy another device's private key or disable host verification. A public key pasted into chat is not a private login key.

For diagnosis check **both ends**: G1 Moonlight logs and Windows WLAN events; Bosgame Sunshine/Buddy journals, dedicated Sway output and the exact game window. A generic Moonlight `-1` code has had several distinct causes here:

| Evidence | Meaning in the observed incident |
| --- | --- |
| G1 Netwtw14 5005 internal failure beside control disconnect | Local adapter/driver failure; no Wi-Fi automation switch at that moment |
| Sunshine C++/property-tree exception with malformed XML | Host Sunshine failure; idle service restart recovered it |
| Sunshine journal “Quitting from system tray” | Host process was explicitly stopped; restore the existing headless service |
| “Bosgame is already streaming a different game” before a new client log | Buddy preflight rejected a different tracked game; inspect/finish the old session |

Do not treat all four as network faults or repeatedly re-pair. On 22 October an Onimusha test session left alive by the agent blocked Expedition 33. That was a test-cleanup mistake. Only the identified test game and old MoonDeckStream wrapper were ended. Expedition then reached runtime/shader preparation, benchmark and its title screen; the test was closed and Buddy session cleanup verified. Remote Steam must run through the existing dedicated-desktop wrapper; a physical-desktop Steam login is not enough.

### Host display guard — separate from the Windows executable

The giant/cropped Onimusha view was reproduced **on a Bosgame screenshot**, ruling out a G1-only zoom problem. Disconnecting Moonlight left the game alive while the old host undo script resized HEADLESS-1 from 2560×1600@120 to 1920×1080@60. Reconnecting restored 2560×1600 and exposed a broken live-game viewport. A fresh launch with identical game settings looked correct, disproving the initial config-mismatch-only theory.

On 22 October at 14:25:55 BST, Bosgame's `~/.config/sway-sunshine/reset-resolution.sh` was changed to preserve the output while a `steam_app_` game window exists. If Sway inspection fails it skips the reset. With no game it retains the original idle reset. Backup: `reset-resolution.sh.bak-before-live-game-guard-20261022-142555`. The exact installed script is archived [here](device-records/bosgame-reset-resolution-20261022.sh).

Same-client 2560×1600 disconnect/reconnect passed with the same game PID and a correctly sized title. A different-resolution client can still resize an already-running game through the separate set-resolution script; that case remains outside this repair. The Steam keyboard's original appearance was not conclusively explained. Do not indiscriminately change game graphics/mod settings or Windows DPI to address this host viewport failure.

## Automatic home Wi-Fi and provider dropdowns

The helper runs as a limited current-user hidden logon task **CreteOS WiFi Smart**, from `CreteOS-Bootstrap\WiFiSmart`. Install **WiFiSmart.ps1, Policy.ps1 and Sessions.ps1 together**, using [the tracked source and policy documentation](../windows/scripts/wifi-smart/README.md). Its device-specific config identifies the actual adapter and already-saved Windows WLAN profiles; it exports no wireless passwords.

| Situation / Automatic preference | Home network |
| --- | --- |
| Startup/resume and idle | `Hyperoptic_Extra` (gaming/LAN) when available |
| Moonlight launch | Extra |
| GeForce NOW or Luna launch | `Hyperoptic Fibre E433` (fast internet) |
| Download demand, with no protected stream | Fibre E433 |
| A streaming session is active | Hold the current connection; an existing session wins over another launch |
| Away on another network, disconnected, helper absent or paused | Leave the connection alone |
| Requested home network unavailable | Retain current connection and allow launch |

Each provider remembers Automatic / Extra / Fibre E433 / Keep current connection in `stream_wifi_preferences.json`. Moonlight and GFN expose Home Wi-Fi in their stream settings; Luna exposes Amazon Luna Wi-Fi in Launch options. Automatic is currently saved for all three. Session leases bind to the owning process/start time and only their own lease is removed on completion; stale/reused PIDs are ignored. Moonlight follows the owned session/client; GFN follows native session completion; Luna now follows the observed live game end and closes its owned app window before returning. The older Wi-Fi-only implementation held Luna until its PWA window closed; the newer lifecycle work supplies that closure on normal game exit.

Normal download rules resume after stream cleanup. Steam download hints and Windows Delivery Optimization are recognized directly. Other launchers use sustained receive traffic: two 15-second samples over 256 KiB/s; continued traffic over 64 KiB/s extends demand. This can also classify unrelated large transfers. Idle return requires 120 seconds quiet plus five minutes minimum on the fast network. Desktop Wi-Fi modes provide Auto, persistent Pause, and two-hour Gaming/Download overrides. Resume notifications and a long-gap fallback restore the idle preference unless stream/manual protection takes priority.

Native scanning refreshes stale Windows network visibility; association waits for connected-state confirmation up to 12 seconds, and app preflight is bounded to 20 seconds. Policy, session ownership, unavailable-network fallback and installed monitor hold/release were tested. Actual continuity through every provider, travel network and hibernate combination is not implied by unit tests.

### Wi-Fi driver and measured quality

On 21 October the Intel AX210 driver was updated from 23.150.0.4 to 24.80.0.2 using a signed official Intel package with published checksum verification. Original package backup is in the workspace `work\wifi-driver-backup-20261021-002344`. Installer exit 0, no reboot requested. Saved profiles, host ping/SSH and internet connectivity survived.

The same Netwtw14 5005 failure recurred after resume on 22 October. At that failure the helper was holding Extra, with no scan or switch request. A reversible supported workaround changed MIMOPowerSaveMode **Auto SMPS (0) → No SMPS (3)** and restarted only the adapter while idle. Readback and ten host pings passed. Rollback is Auto SMPS (0), using the saved before-state. Long-term stability is still unproven; do not describe the driver update/workaround as a guaranteed cure or apply this G1 driver to an unrelated device.

Earlier successful gameplay had 76.58 incoming FPS despite a 120 FPS target, near-zero network loss and ~18.2 ms average host processing. Client decode/render generally kept up. That points to host/game/capture delivery for that run's motion limitation, without proving which component caused it. A later static title-screen test delivered ~120.61 FPS with zero reported loss; that is not a comparable gameplay benchmark. See [the two-end stream review](device-records/Moonlight-review-20261021.txt).

## Games and provider behavior

### Lutris host games — inspected 23 October, integration pending

Read-only SSH confirmed native Lutris0.5.22 on Bosgame with Mouse: P.I. For Hire, Metal Gear Solid V: The Phantom Pain and Mortal Shell II entries and existing launcher-recorded playtime. Mouse/MGS V configured game executables and prefixes exist. Mortal Shell II still targets its installer setup.exe; its actual installation/game executable is not yet verified, and its recorded time may include installation. The current CreteOS host scanner handles installed Steam manifests only, so these entries are not automatically imported or launchable through its Buddy route. No game/installer was launched or settings changed during inspection.

[Lutris integration audit](LUTRIS_HOST_INTEGRATION.md) records the concrete plan: namespaced Lutris discovery/library entries, separate metadata/launch IDs, runner-preserving non-Steam shortcuts through Buddy where verified, exact game lifecycle/return tests, and cumulative host-playtime refresh without double counting. Public game metadata can be matched where available; Steam achievements/account statistics are not implied. This is feasibility evidence, **not an installed feature**. Current executable/private backup are unchanged; extend backup inputs if this integration is implemented.

### Onimusha

Initial direct Steam launch crashed too, so it was not solely CreteOS dispatch. Steam integrity verification passed. Muzi installed AMD Adrenalin/updates and changed VRAM from 4 to 16 GiB, then confirmed Onimusha working on 18 October. Both changed together; the individual cause is unproven. Xbox Game Bar was restored for the `ms-gamebar` association error.

On Bosgame only, with the game closed, `Graphics_UltrawideFix` was enabled in Onimusha's existing REFramework `re2_fw_config.txt` on 21 October. Backup: `re2_fw_config.txt.bak-before-1610-20261021-003021`. Other aspect/FOV flags were preserved. This is a **game-specific** change and not a global 16:10 conversion; gameplay visual acceptance of that change was not recorded. See [exact change](device-records/Onimusha-1610-change-20261021.txt).

### Steam login

The app gained username/password login alongside QR in `6135a2e`. Password submission uses Steam RSA public-key encryption; Steam Guard email/authenticator/approval and cancellation are supported. Passwords/codes are not persisted; existing Windows Credential Manager token storage is used. Synthetic protocol/cancellation tests passed. Muzi signs into real accounts directly; Steam may still require Guard even without QR. Signing into a host/client account is separate from authorizing the app's library integration.

### Loading artwork, cancellation and freeze repair

Moonlight readiness combines the exact requested Buddy game state and host window events with the local client handoff. Process presence alone cannot establish an interactive game menu. Real authentication/dialog attention must remain usable. B/Escape cancellation remains available; the visible cancel hint/button was removed at Muzi's request. Escape was observed working; physical B across all modes still needs acceptance.

Early foreground retention exposed a UI-thread/worker lock inversion consistent with the recorded return hang. The relevant native commands were moved off the UI message thread (`9e9b514`), and a subsequent actual return completed with no new hang. Return artwork was then held through Buddy/client cleanup (`3eb970a`).

The auxiliary artwork Tauri WebView repeatedly had no usable HWND on the G1; multiple intermediate builds failed or were rolled back. The current executable adds an independent GDI/WIC native artwork popup with NOACTIVATE, bounded local cached-image decoding and proportional cover sizing. It can paint return art directly without waiting for a hidden frontend animation frame. It verifies HWND ownership, respects deliberate Alt-Tab/cancellation and retains a fail-open fallback. Logs now show the native cover at launch and return. This supersedes older entries that say the independent cover was unavailable; those entries remain as history.

GFN readiness can see the first cloud video frame before the game is interactive, including remote Steam. It does not control NVIDIA's remote account authentication. Earlier source review found NVIDIA warmup/focus and late Done handling could expose home/client windows; the final provider work improves shared cover/return handling and watches session/post-session states, but an exhaustive fresh GFN queue/login/game/exit visual test remains outstanding. Do not promise every provider is seamless from Luna's one successful test.

### Luna Companion and local playtime

The explicitly approved Chrome extension is loaded unpacked from the permanent native folder. Keep its manifest key/ID stable and load it in the profile used by the Luna app. After changing files, click **Reload** once in `chrome://extensions`, then close/reopen Luna; copying files alone does not reload a running extension. See [companion instructions](../windows/browser-companion/README.md).

It observes advancing live video, excludes trailers/hidden prompts, and posts bounded phase/counter metadata to `127.0.0.1:59265`. Exact extension Origin, loopback Host, session binding and sequence checks remain enforced. It collects no passwords/cookies/audio/video. The POST handshake fixes Chrome's missing-Origin privileged GET behavior. Real `/game/name/ASIN` links and SPA identity changes are recognized. Premature ended events cannot bind/terminate a fresh native session before actual playing.

CreteOS records local per-game playtime in `luna_playtime.json`, saving periodically and on finish. Waiting, homepage, authentication, buffering and missing bridge time are excluded. The game details show **Playtime (this device)**. No historical Amazon playtime was imported. The live 23 October test proves the first recorded 242.269 seconds persisted and a normal in-page game end returned to CreteOS.

### Lossless Scaling — prepared, not integrated

Profile **Amazon Luna G1**: LS1, Auto / Aspect ratio, frame generation off, Draw FPS off, auto-scale off; hotkey Ctrl+Alt+S. Custom 1.333333 resize-before-scaling failed because Chrome stayed 2560×1600 when LS requested 1920×1200. The profile was restored to Auto with resize disabled. **Automatic true 1080p upscaling is not enabled or verified.** Do not count a 1× filter on an already enlarged browser as upscaling. A 16:9 Luna image retains its aspect ratio; full 16:10 needs actual game support, cropping or stretching. OptiScaler is a game-side tool, not an established browser-cloud integration here.

## Source, builds, Git and rollback

Canonical source repository identified in the original handoff: `https://github.com/muzi-latentera/CreteOS.git`. Windows source is under `windows/`; keep inherited LiftOff license/provenance and Android independent. The G1 checkout initially retained the USB bundle as origin; **a local commit was not a hosted push**. Hosted synchronization is recorded separately in the dated journal. Do not reset or force-push the existing `codex/windows-liftoff` branch to install these G1 changes; preserve the separate G1 branch.

Local toolchain installed 22 October: Node 24.22.1 portable, Rust 1.99.1 MSVC stable, VS Build Tools 2026 18.11.1 VC C++ workload and Windows SDK 10.0.26100. Run frontend commands inside `windows/`, with VS x64 build environment and Cargo available. `npm ci`, `npm run build`, then `npm run tauri -- build --no-bundle -- --locked` produce the native executable. The actual wrapper limited Cargo to four jobs. Existing optional SGDB_API_KEY fallback is empty; cached artwork works, but fresh SteamGridDB lookup was not verified. Keep API keys out of Git and command logs.

Latest recorded checks: 245 frontend tests / 53 files passed with two workers; 127 native release tests passed after native paint/return changes; 11 companion VM tests passed. The small final HWND ownership guard then compiled in the release and passed actual startup verification. Production typecheck/bundle and local native release build passed. Counts refer to these recorded runs, not to a newly rerun full suite for this document. No software behavior changed in this documentation task.

Updates: close only idle CreteOS normally, back up the old executable and live profile, replace the executable, compare SHA-256, relaunch natively and verify profiles/history unchanged. Last install compared settings.json, moonlight_profiles.json, geforce_profiles.json, custom_data.json, stream_wifi_preferences.json and recent_games.json byte-for-byte. Roll back by restoring the corresponding backup executable; restore profile data only if it was actually changed and after preserving the newer copy. Host scripts, helper scripts and extension files need their own matching backups; an executable rollback alone does not undo them.

Git contains source and these sanitized records. It is **not** a backup of Windows Credential Manager, live provider sessions, private SSH keys, WLAN keys, full artwork/profile data, vendor databases or installed drivers. Maintain a separate private device backup. A verified all-branches Git bundle is also saved in the task's `outputs` folder for offline source recovery; it does not include untracked personal runtime data.

## Replacement device checklist

### Actual private profile backup — 23 October, 16:52 BST

Created `outputs/CreteOS-G1-profile-backup-20261023-165259.zip` in the original G1 task workspace, with an extracted copy alongside it and a `.zip.sha256` sidecar. **1,257 payload files**, 722,440,113 uncompressed bytes; ZIP 701,482,568 bytes. SHA-256: `c8e9c02b67f68ab67bccf555b11f64929c4ace070ca120e8a4b5bc47983faf02`. Every ZIP payload was decompressed and hash-checked; all requested source files were present. OneXConsole used SQLite online backup and integrity_check=ok. No live app was closed or reconfigured.

The ZIP contains the live preferences/profiles, onboarding state, ownership/art caches, hidden/pinned items, history, provider Wi-Fi choices, Luna playtime/companion, exact recovery executable/license, and supporting Moonlight, NVIDIA quality, AnyFSE, Lossless Scaling, Wi-Fi helper, non-secret SSH and Windows startup/power configuration. Account-bound progress caches are retained as reference. It includes `RESTORE-INSTRUCTIONS.txt`, `Restore-CreteOS.ps1` and a per-file manifest. The restore script passed an isolated fixture check for hashes, no-write WhatIf, path rebasing, saved onboarding, JSON types, binary files and default exclusion of old G1 display identities.

This is a private, unencrypted local archive, outside Git. Only C: was attached when created, so **copy it and its checksum to an external drive or private off-device backup before returning the G1**. No off-device copy is claimed. Windows Credential Manager/DPAPI/Autologon secrets, browser/Steam sessions, private SSH/Moonlight keys and WLAN passwords are excluded; fresh login/pairing is required. Game saves/downloads are not included. Supporting vendor/device settings are preserved for review rather than blindly applied to another model. Use the included restore instructions; a source Git bundle does not replace this profile ZIP.

1. Preserve the current device's native profile/bootstrap and vendor settings privately before returning it. Export a portable profile with the existing supported process; exclude secrets. Keep the latest source bundle, exact executable and this guide off the device as well.
2. Read the pack manifest/current handoff; verify checksums. Install the current Windows artifact, using native paths. Back up the destination and import portable data once with path rebasing. Verify artwork, history, onboarding state and profile settings before deleting any old device data.
3. Detect the replacement's actual panel, refresh rate and external displays. Create its own display presets; do not assume G1 or GPD geometry. Preserve GFN Auto bitrate where selected.
4. Install/detect Moonlight, NVIDIA native GFN, Chrome Luna app, AnyFSE and the device's vendor utility. Set AnyFSE target to the real executable with administrator launch off; check exit-to-desktop and startup behavior.
5. Authorize provider accounts locally. Pair Moonlight with Bosgame, then pair CreteOS separately with Buddy. If Buddy's prompt is missing, inspect its desktop/pending pairing before changing services. Restore the normal service after any temporary physical-desktop pairing.
6. Generate and authorize a new device SSH key, verify Bosgame identity independently and configure `ssh bosgame` plus CreteOS inventory. Revoke the returned device's key/pairings when appropriate; don't reuse private keys by accident.
7. Install the three matching Wi-Fi helper files and create the limited hidden current-user logon task. Configure the real adapter/profile names, retain existing WLAN credentials and test Auto/away/unavailable fallback. Confirm per-provider defaults and lease release. The existing workspace also contains `outputs/CreteOS-WiFi-WinMini-kit.zip`; inspect its README and use current tracked helper sources rather than assuming that older kit has every later feature.
8. Load the Luna Companion in the Luna Chrome profile, verify ID/version and Reload. Test real playback and in-page quit; confirm saved local playtime and return to CreteOS. Check authentication/fullscreen prompts, cancel and deliberate Alt-Tab as separate cases.
9. Configure supported vendor power presets for the new model and verify actual game/desktop switching. Apply desired Windows no-sign-in settings with that device's credentials. Test cold boot, sleep/hibernate and AC/battery.
10. Test one local game, a Moonlight cold launch and same-game reconnect, a GFN launch/quit, Luna launch/quit/playtime, and dock/unplug/controller operation. Clean up only your own test sessions so they cannot block later games. Record hashes, actual installations, observations and remaining faults separately.

Do not carry the AX210 driver package, No SMPS workaround, Onimusha mod or Bosgame's user-1000 Sway socket onto unrelated hardware/hosts without checking applicability. Bosgame's existing host display guard should not need reinstallation just because the handheld changes.

## Record maintenance and evidence map

**Lasting user instruction (23 October):** keep this guide and dated journals current after every meaningful future setup/code/configuration/deployment change, ready for another handheld in roughly two months. Refresh and verify a new private profile backup whenever deployed backup inputs change, retain the last verified copy, and update the latest-backup locator and actual archive identity here. If refresh is blocked, label the backup stale with the reason. Documentation-only edits do not require another personal-data snapshot. This instruction is persisted in workspace/repository AGENTS.md and APP_JOURNAL.md; it does not install a background schedule.

Append to the dated journals after meaningful work: request, changes/reasoning, source commit, exact build and install identities, actual checks, remaining issues and next action. Update the current-state table here when deployed behavior changes. Follow [APP_JOURNAL.md](APP_JOURNAL.md). The canonical Obsidian Crete vault is unavailable on this G1; all current journal entries are **pending vault sync**. The USB contains earlier vault snapshots, not a live writable canonical vault. No standalone `.codex/memories` directory was present when checked; this document was reconstructed from this conversation, local installation evidence, dated repo journals and supplied app-record snapshots.

Detailed histories: [18 October display](journal/2026-10-18-g1-display-pending-sync.md), [19 October password login](journal/2026-10-19-steam-password-pending-sync.md), [original Wi-Fi setup](journal/2026-10-19-smart-wifi-pending-sync.md), [driver/stream investigation](journal/2026-10-22-stream-review-pending-sync.md), [return hang](journal/2026-10-22-return-hang-pending-sync.md), [return cleanup](journal/2026-10-22-seamless-return-pending-sync.md), [provider Wi-Fi and local builds](journal/2026-10-22-service-wifi-local-build-pending-sync.md), [host crop repair](journal/2026-10-22-moonlight-resume-display-pending-sync.md), [Expedition/GFN review](journal/2026-10-22-expedition-launch-review-pending-sync.md), [provider audit](journal/2026-10-23-provider-lifecycle-audit-pending-sync.md), [implementation and rollbacks](journal/2026-10-23-provider-lifecycle-implementation-pending-sync.md).

Selected original reports are archived under [device-records](device-records/README.md). Their dates matter: older “current installed” values and unchecked items are superseded by later verification, not silently rewritten. The workspace's `outputs` and `work` folders retain full install JSONs, driver rollback package, scripts, binaries and logs; those are outside the source repository unless explicitly archived here. Retain a private backup of them before returning this device.

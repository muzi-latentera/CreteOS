# CreteOS full audit against SteamOS (13 November 2026)

Scope: the whole app. That covers launches and returns, loading screens, menus and modals, navigation feel, every setting, the library, store accounts, the quick menu, power, saves and streaming. Each area is compared with SteamOS Game Mode and the Windows handheld shells.

Method: a code audit of `windows/src` and `windows/src-tauri/src`, plus the device journals, live read-only checks on the G1 and Bosgame, and web research. Findings marked **[V]** were confirmed in code. Findings marked **[I]** are inferred runtime effects and have not been measured on the G1. Items marked **FIXED (13 Nov build)** are in the build made tonight. See §9 for exactly what was installed and verified.

---

## 1. The short version

CreteOS already does several things SteamOS can't:
- one library across Steam, Xbox, Epic, GOG, Battle.net, Lutris-on-Bosgame, GeForce NOW, Luna, Xbox Cloud and PlayStation
- duplicate games merged across stores
- dock-aware Moonlight routing
- per-launch display and audio choices
- art-covered launches and returns
- versioned save backups for games no launcher protects

Where it loses to SteamOS is the **console contract**: the small guarantees that make SteamOS feel like an appliance.
1. **There is no CreteOS menu over a running game.** *Corrected after owner feedback:* leaving a game works. CreteOS is the Windows Gaming Home app through AnyFSE, so the home button returns to it. What's missing is SteamOS's in-game menu: Resume, Exit game, volume, brightness and performance over the game. CreteOS's own hold-to-summon watcher is unused (`fse_watcher.rs:1498`), and the return combo only runs for 20 s after an exit. Winhanced does this with an Xbox Game Bar widget, which is the likely route for CreteOS too.
2. **There is no Sleep.** The Power menu offers only Restart app, Exit, Restart and Shutdown (`PowerModal.tsx:33-73`), and nothing handles resume. This is the main reason people pick SteamOS.
3. **Loading screens don't say what's happening.** Steam phases are parsed (`lib.rs:9212-9271`), but the Crete cover shows art and jokes only (`LaunchOverlay.tsx:315-317`). A 3-minute Steam update looks like a hang.
4. **Navigation is built from many separate pieces.** There are 32 separate controller loops, each guarding the others by hand, and that is where the input leaks come from. Day-to-day control works for the owner, so this is a reliability and maintenance risk rather than something felt constantly. *Owner preferences:* no button hints and no navigation sounds. The **Sound effects** toggle exists and works (`App.jsx:508`), and it is off.
5. **No per-game performance profiles** (TDP, frame limit, refresh) and no performance overlay.
6. **Personal values are baked into the app:** "Bosgame", the Hyperoptic SSIDs, Arctis, XREAL. A new device or network needs code edits.

The table in §3 has the full scorecard. The roadmap is in §8.

## 2. Why people choose SteamOS over Windows (research)

- **Performance and battery.** On the same Legion Go S, Cyberpunk ran at 59 vs 46 FPS and 1h54m vs 1h31m. A light game lasted 6h12m vs 2h47m ([GamingOnLinux](https://www.gamingonlinux.com/2025/05/steamos-massively-beats-windows-on-the-legion-go-s/page=1/), [TechSpot](https://www.techspot.com/news/108059-steamos-significantly-improves-performance-battery-life-lenovo-legion.html)). Windows background load is the cause; a shell can reduce it but cannot remove it.
- **Sleep you trust.** Tap power mid-game, put it in a bag, and resume in seconds. TechRadar says Windows lacks a working sleep-and-resume path for handhelds ([TechRadar](https://www.techradar.com/computing/gaming-pcs/microsofts-windows-11-is-a-detriment-to-handheld-gaming-pcs-and-a-recent-steamos-comparison-highlights-that)). Modern Standby drains in bags; hibernate takes about 15 s ([Retro Handhelds](https://retrohandhelds.gg/the-asus-rog-xbox-ally-needs-more-sleep/)).
- **One coherent console UI.** No desktop, no OEM app layered on Xbox layered on Steam, no popups.
- **The Quick Access Menu:**
  - a 4-level performance overlay
  - per-game TDP, frame limit, refresh rate and scaling profiles that apply silently
  - brightness, Wi-Fi, Bluetooth, notifications and friends, all over the running game
- **Recent polish (2025–26):**
  - downloads that continue with the screen off
  - Sleep without a confirmation prompt
  - power-menu warnings before closing a running game
  - button hints that follow the input device
  - accessibility features: screen reader, high contrast, reduced motion

**Why people stay on Windows:**
- anti-cheat games
- Game Pass, Battle.net and Epic
- OEM features such as rumble and RGB

CreteOS's opening is a Windows handheld that feels like SteamOS while keeping those advantages. The Windows competition is weak:
- Xbox mode is "half-baked" (The Verge, 6/10).
- Armoury Crate is "a clunky hybrid".
- Legion Space is widely disliked.
- Playnite has no quick menu or power control.
- OneXConsole, which the G1 already has, reviews best for its one-button settings panel.

## 3. Scorecard against SteamOS

| Area | SteamOS | CreteOS today | Verdict |
|---|---|---|---|
| Unified library across stores and cloud | Steam only (plus non-Steam shortcuts) | Every store plus streaming, with duplicates merged | **Ahead** |
| Launch: art cover and status | Art, spinner and status | Art covers on every route; native stream cover; no status text in Crete | Behind (status) |
| Launcher prompts during launch | n/a | Handled on Bosgame; local Steam/Epic prompts can hide behind the topmost cover for up to 90 s **[V/I]** | Behind |
| Leave or control a running game | Steam button → Exit game; QAM over the game | Home button returns to CreteOS (AnyFSE). No CreteOS menu over the game | Behind (in-game menu) |
| Return to the shell | Always | Reliable, with art held; about 65 ms of CreteOS showing on local return | On par |
| Suspend and resume | Signature feature | None | **Missing** |
| Per-game performance profiles and overlay | Yes | None (OneXConsole is separate) | **Missing** |
| Quick menu | Performance, brightness, Wi-Fi, BT, notifications, friends | Volume, brightness, power, running apps, downloads, Spotify; CreteOS-focused only | Behind |
| Button legend and feedback | Legend everywhere, follows input device, sounds | No legend or sounds on Crete screens | Behind |
| Back button | B always goes back one level | B on Home opens Power; elsewhere jumps to Home | Behind |
| Modals | One system, animated in and out | Many bespoke ones, about 20 z-index levels, no exit animation | Behind |
| Settings | Curated | About 110 options: about 45 work, about 35 do nothing in Crete, several misleading | Behind |
| Downloads and updates | Queue, updates, screen-off downloads | Steam progress for 40 min max, no queue, no update detection | Behind |
| Cloud saves | Steam Cloud, latest copy only, confusing conflict dialog | Versioned backups, protected restores, safe Drive sync **FIXED (13 Nov build)** | **Ahead** for non-Steam games |
| Streaming (Moonlight, GFN, Luna, PS) | Remote Play only | Deep integration: host checks, Wi-Fi routing, B-cancel | **Ahead** |
| Social, screenshots, achievements | Full | Achievement counts only | Missing |
| Accessibility | Screen reader, high contrast, filters, scale | UI scale, reduced motion | Behind |
| Self-update | Automatic | Manual exe swap | Behind |

## 4. Launches, loading screens and returns

**Done well [V]:**
- Generation counters stop stale launches from acting.
- Exact-window matching uses a score of at least 45 and a 2 s settle, so Steam's popup is never mistaken for the game.
- Steam phases are parsed from logs.
- Native B-cancel works for streams.
- Moonlight has preflight checks and Buddy exit events.
- Real host prompts drop the cover instead of hiding it.
- Every wait has a timeout.
- Held buttons are suppressed with a 1.8 s cooldown after a return.
- Play on a running game focuses it instead of starting a second copy.

**Bugs and risks:**
1. **FIXED (13 Nov build): PlayStation tiles always failed at the cover.** `withStreamCover` passed `ps5RemotePlay` or `playstationCloud`, and `set_launch_cover` rejected both before ASOBI ran (`launch_cover.rs:105`). There is now a test listing every frontend provider.
2. **B after Play doesn't stop a local launch [V].** It only hides the overlay (`useGamepadNavigation.ts:551-569`). The watcher later forces the game to the front, and pressing Play again can start a second copy of a direct-exe game.
3. **Non-Steam launches get 15 s, Steam a fixed 90 s [V]** (`lib.rs:10389`). Epic, GOG and Xbox launchers that are cold or updating end as "unconfirmed". The message tells you to check Steam even for Epic (`en.json:26`). After "Got it", no exit watcher is attached.
4. **Direct-exe launches have no working directory [V]** (`lib.rs:10318`). An "elevation required" error (740) is shown raw. `ShellExecuteW` results are ignored, so a declined UAC prompt becomes a 15 s wait.
5. **Crashes and hangs [I].**
   - A Windows "stopped working" dialog is treated as the user switching apps, so CreteOS isn't restored (`fse_watcher.rs:1400-1405`).
   - Hung windows aren't detected.
6. **"Bosgame is already streaming a different game" is a dead end [V]** (`moondeck.rs:379`). `request_end_stream` exists but nothing offers it.
7. **Resume via "Resume" doesn't re-attach the exit watcher [V]** (`lib.rs:9857-9865`), so the next exit gets no return cover.
8. **Smaller:**
   - a stale `dismiss` closure in `StreamLaunchCover.tsx:195-205`
   - `running_unfocused` is never set
   - no per-game launch arguments

**SteamOS-level target:** the cover shows the game name, a quiet spinner and the real phase ("Updating · 1.2 GB left", "Syncing saves", "Waiting for Epic"), with the jokes as a secondary line and a faint "B Cancel". Launcher prompts drop the cover. Timeouts extend while a phase is progressing.

## 5. Menus, modals, navigation and smoothness

**Done well [V]:**
- Crete is cheap to draw: flat fills, and focus is a 120 ms transform.
- The motion tokens animate only transform and opacity.
- The main input loop polls every 8 ms, with a user-set repeat speed.
- Scrolling is interruptible.
- Sounds are pre-decoded.
- Crete views keep navigation state locally.
- Grid art is lazy-loaded.

**Bugs:**
1. **FIXED (13 Nov build): "Close game?" leaked presses to the screen behind it** when opened from the quick menu. Dialogs with their own controller loop now mark themselves `data-owns-input`, and the main loop yields to them. ConfirmModal also gained Enter and Esc.
2. **FIXED (13 Nov build): pressing Manage game could leave the highlight on Play**, so the next A press launched the game (`GameDetailsModal.tsx`).
3. **FIXED (13 Nov build): the Launch options menu vanished during the streaming check.** That hid Manage game and Manage saves on every Steam, GOG, Epic, Xbox and EA game. The menu is now always available, with play routes shown as "Checking play options…".
4. **Holding A as Game Details opens can start the game [V].** The 20-frame ignore window doesn't record held buttons (`GameDetailsModal.tsx:665-669`). The same pattern appears in 7 other modals. Every modal also drops input for about 333 ms, which reads as lag.
5. **On-screen keyboard [V]:**
   - It can't submit: `onConfirm` is never called.
   - It shows Xbox glyphs only, in hard-coded English.
   - It ignores a physical keyboard.
   - There are two separate keyboards.
   - The Crete Library search field needs a hardware keyboard.
6. **B is unpredictable [V].** On Home it opens Power; elsewhere it jumps to Home. Start doesn't close the tray it opened.
7. **No controller path to the per-game context menu [V].** The branch is unreachable (`useGamepadNavigation.ts:1258`).
8. **Left stick has no hysteresis [V].** Diagonals produce double moves.
9. **Hidden full-screen blurs [V style, I cost].**
   - Crete Game Details runs `blur(18px)` under an opaque panel.
   - Shared modals stack two blurs.
   - Home has a `blur(24px)` backdrop under the scroller.

   These are the likely cause of frame drops on the G1 APU.
10. **Re-render cost [V].**
    - Components are defined inside `App` (`App.jsx:2712-2771`), so they remount on every App render.
    - Context values are rebuilt every render.
    - Cards aren't memoised and nothing is virtualised.
    - The idle timer re-renders App after 4 s, so the first press after a pause pays for a full render.
11. **News cards open the system browser [V].** The controller doesn't work there.

**Structural fix that removes the whole class of bugs:**
1. **One input owner with a modal stack.** One poller; the top overlay gets input. That replaces 32 loops and about 25 keydown listeners.
2. **One Crete dialog system:**
   - shared tokens
   - entry and exit animation
   - a named z-index scale
   - a mandatory button-legend footer
   - one keyboard
3. **A persistent legend bar** generated from each view's key map.

Measure before and after on the G1 with the existing `inputDiagnostics`.

## 6. Settings: does each one do anything?

There are about 110 options:
- about 45 do real work in Crete
- about 15 work partly
- about 35 are LiftOff leftovers with no effect in Crete
- a few are stored but never read
- several are misleading

**Highlights:**
- **Settings can reset without warning [V].** One unparseable field resets every setting (`lib.rs:1228`). Saves overwrite the file in place (`lib.rs:1248`), so a crash mid-save can corrupt it. **High priority.**
- **Misleading options [V]:**
  - "Run as Administrator" is offered for every game but honoured only for direct .exe launches.
  - `home_mode` only changes the hero's height.
  - Pinned games are hidden by default while the toggle says on.
  - The update-channel link builds the same URL both ways.
- **Dead or legacy in Crete [V]:**
  - accent (mostly), surface style, bottom/tab bar options, wide top/bottom bar
  - `bottombar_compact`, `topbar_show_bumpers`
  - the coffee and Discord links
  - the onboarding accent, surface and visual steps, which Crete still asks you to complete
- **Hidden but important [V]:**
  - `hide_on_launch` can only be changed in onboarding.
  - `fetch_store_metadata` contacts outside servers and has no switch.
  - `fse_hard_reload_recovery` has no UI.
- **Personal values in code [V]:** "Bosgame", "Hyperoptic Extra" and "Fibre E433", "HSX0224", Arctis, Philips TV and XREAL. There is no setup screen for the streaming host, SSH or home Wi-Fi.
- **Missing compared with SteamOS:**
  - per-game display and audio memory
  - sleep timer
  - TDP and power profiles
  - frame limit
  - diagnostics and log export
  - storage management
  - Wi-Fi and Bluetooth pickers
  - in-app update

## 7. Library, stores, quick menu and power

**Done well [V]:**
- Cross-store edition merging keeps every copy and offers an edition switch.
- Availability filters.
- Tokens are kept in Credential Manager and scrubbed from errors.
- Refreshes keep the last good data when a source fails.
- Art fills in for on-screen tiles first.
- Real Steam and Xbox install progress.

**Bugs:**
1. **Home Play may choose a non-installed copy [V], and this is intended.** *Owner decision:* streaming from the more powerful PC is preferred, so Play choosing a stream or opening details is acceptable. Only the "Local" tag on the hero needs to match what Play will actually do.
2. **Hiding a merged game brings another copy back [V].** The Pin label, the Running badge and art all check one copy only.
3. **The Steam install watcher gives up after 40 minutes [V]** (`lib.rs:7386`) and shows an error while Steam keeps downloading. Uninstall gives up after 60 s.
4. **Expired Epic/GOG sessions fail silently [V]** (`useStoreAccounts.ts:34`). The quick menu says "Refreshed" even when sources failed.
5. **Fallback recents appear in "Jump back in" [V].** With no history, the first 10 scanned apps are used (`useLibraryData.ts:206-211`).
6. **No virtualisation and a full re-merge whenever the window regains focus [V].** Startup waits for a full rescan with no saved snapshot shown first.
7. **The Wi-Fi icon only shows whether the app thinks it's online.** It has no signal strength or network name. If reading the volume fails, the volume slider starts at 0%.
8. **The SteamGridDB key is compiled into the exe [V].**

**Gaps compared with SteamOS:**
- an in-game overlay
- Wi-Fi and Bluetooth in the quick menu
- Sleep
- a downloads page with a queue and update detection
- notifications
- screenshots and capture
- full achievements

## 8. Roadmap, in order

**Phase 1: Trust (days).** These are the bugs that make it feel broken.
1. The rest of §5's quick wins:
   - ignore-until-released input in every modal
   - kill the hidden blurs
   - memoise cards and providers
   - Start closes the tray
   - B goes back one level
   - left-stick hysteresis
   - a keyboard that can submit
2. Prefer installed copies in merge ranking; make hide, pin and running state apply to all copies.
3. Make settings loading tolerant and save atomically.
4. Real local launch cancel. Timeouts per store and phase. Working directory and UAC error mapping for direct exes.
5. Steam install watcher: no time limit while progress is moving, plus re-attach at startup.

**Phase 2: The console contract (1–2 weeks).** These close the SteamOS gap.
1. **In-game Guide overlay.** An always-on combo (or the Xbox button) opens a small topmost panel over the game with:
   - Resume, Return to CreteOS, Close game, Force quit
   - volume and brightness
   - a performance overlay level

   `close_launched` and `force_close_launched` already exist.
2. **Sleep and resume.**
   - Sleep in the Power menu, with no prompt, matching SteamOS's October change.
   - Handle `WM_POWERBROADCAST`: re-check controllers, display, Wi-Fi and Moonlight; retry pending save uploads.
   - Test it on real hardware: hibernate, dock and undock.
3. **Loading screen with real status and a cancel hint.** Detect launcher prompts.
4. **Per-game profiles.** Display mode, audio device and (through OneXConsole or ADLX) TDP and frame limit, applied on launch and restored on exit.
5. **Notifications and toasts** for backups, uploads, downloads, stream errors and updates, plus error boundaries per screen.

**Phase 3: Polish (ongoing).**
- One input owner and one dialog system.
- A legend bar that follows the input device.
- Navigation sounds and haptics.
- A downloads page.
- Wi-Fi and Bluetooth in the quick menu.
- Screenshots.
- A settings clean-up: remove legacy options and add a streaming host and network setup screen.
- In-app updates.
- Accessibility.

## 9. Saves: what changed tonight and what's verified

**Design (what "seamless" means here):**
- **Every game gets a save status. CreteOS fully manages:**
  - standalone Windows games (GOG offline, Humble, manual)
  - every Lutris game on Bosgame
- **Steam, Epic, GOG Galaxy and EA:** the launcher owns the cloud copy. A silent extra versioned copy is the recommended next step.
- **Streaming services:** saves stay with the service.

**Implemented in the 13 Nov build:**
- **Manage saves** (renamed from Save backups) in every eligible game's Launch options. It opens even while play options are loading, and runs a read-only check as soon as it opens.
  - It shows Saves match / Saves differ / Couldn't check / No saves found yet.
  - It labels *Save last modified*, *Latest backup created* and *Google Drive backup created*.
  - It shows the number of versions kept and the last backup activity.
  - It explains that a later backup date does not mean newer progress.
- **Back up now** creates a version and syncs Google Drive.
- **Restore a version:** pick a version, then preview what it replaces, then confirm. Before restoring, the current save is backed up and **locked** as "Before restore", and the 3 most recent are kept. Locked versions survive retention pruning; this was measured in a sandbox.
- **Sync with Google Drive**, and automatic sync after every backup and restore:
  - uploads when only the device has new versions
  - downloads when only Drive does
  - refuses on a real conflict
  - never deletes a version

  The rules come from Ludusavi 0.31's measured preview output and are covered by Rust tests. This removes the old dead-lock, where one manual or failed upload made every later automatic upload get declined silently.
- **Exit backups always write the local version first**, then sync, then record the result for the Manage saves screen. Bosgame's Lutris hook is now a thin wrapper around the same helper (`creteos-saves`).
- **Bosgame retention** raised from 5 to 10 to match the hook.

**Verified:**
- Rust: 169 tests.
- Frontend: 290 tests plus a clean type check.
- **Bosgame sandbox:** an isolated home folder with its own config and a local folder standing in for Drive. Backup and upload, the exit-hook mode, preview, restore with a protected checkpoint (the unbacked current save was preserved), conflict refusal, the running-game refusal and the unknown-game message all worked.
- **Live Bosgame hook for Mouse:** the save was unchanged, so no new version was made, and it reported "Google Drive is up to date" in 12 s.
- **Remaining:**
  - the G1 install and an on-device UI check (see the journal)
  - a real standalone-Windows game exit with a Drive upload
  - a restore of a real game, which is your call per game

**Saves backlog:**
1. Safety-net versions for Steam, Epic and GOG Galaxy games, using the manifest's `cloud` flags. That covers 11 G1 games at 45 MB.
2. A status line on the game page.
3. A Settings → Saves overview.
4. Catch-up backups when a game outlives CreteOS.
5. A Wine↔Windows path mapping for moving saves between Bosgame and the G1.
6. Bosgame's Drive previews take about 60–100 s on the shared rclone client. Your own Google OAuth client for rclone would make every Drive step much faster.

## 10. Menus and settings: target structure (owner direction, 13 Nov)

**Owner decisions recorded:**
- No button hints, and no navigation sounds. The existing Sound effects toggle stays.
- Holding A must never start a game. Fixed: every modal now ignores buttons already held when it opens, with a regression test.
- Play preferring a stream from Bosgame is intended.
- Steam install tracking must improve.
- Remove the leftover settings.
- Per-game performance is wanted, through each device's own power backend.

**Menus, modelled on SteamOS's split (a menu to go places, a panel to change things):**
- **Main menu** (Home or View button): Home, Library, Downloads, Saves, Settings, Power.
- **Quick panel** (Menu button, and over games through a Game Bar widget):
  - Running game: Resume, Close game, Force quit
  - Performance: per-game profile toggle, power preset, frame limit, refresh rate, overlay level
  - Brightness and volume
  - Wi-Fi and Bluetooth
  - Notifications
- **Power:**
  - Sleep, with no confirmation
  - Restart and Shut down, with a warning when a game is running
  - Exit to desktop
- **Game page "Options"** (the existing Launch options):
  - Play routes
  - Manage saves
  - Manage game
  - Performance profile for this game
  - Properties (launch arguments, run as admin only where it applies)

**Settings, rebuilt around what CreteOS actually does:**

| Section | Keep or merge | Add | Remove (no effect in Crete, or never read) |
|---|---|---|---|
| Library | sources (Steam, Xbox, Epic, GOG, Battle.net, desktop), accounts, uninstalled games, custom folders, refresh | Lutris/Bosgame, PlayStation and cloud source toggles; store-session status with Reconnect | — |
| Home | recents, collections and names, hero banner, launch-from-Home, section size | one "Pinned row: off/top/bottom" control (merges two settings) | `home_mode` (relabel to "Hero size"), `show_hero_cover` |
| Appearance | UI scale, cover sizes, store badges, clock/date/battery, time format, motion (wired into Crete), Sound effects | — | theme picker and all non-Crete themes, accent, surface style, stars/lofi, all bottom-bar/tab-bar/bumper options, wide top/bottom bar, `bottombar_compact`, `topbar_show_bumpers` |
| Streaming | Moonlight profiles, GeForce NOW | **host setup screen**: name, address, SSH key status; home Wi-Fi picker from saved networks; audio device nicknames | hard-coded "Bosgame", Hyperoptic, Arctis and XREAL strings |
| Saves | — | Drive account status, automatic backups, versions kept, safety-net for launcher games, per-game health list | — |
| Performance | — | backend (OneXConsole on the G1; RyzenAdj or vendor tool elsewhere), default and per-game profiles, overlay | — |
| Controller | glyph style, repeat speed, return shortcut, haptics, controller test | — | `gamepad_btn_size` / icon options with no effect in Crete |
| System | start with Windows, language, updates (fix the channel link), factory reset | hide-on-launch (moved from onboarding), store-metadata switch, diagnostics export, storage view, sleep timer | coffee and Discord links, stale privacy link |

Onboarding drops its accent, surface and visual steps.

## 11. Power controls: how the G1 does it, and the plan

- **OneXConsole on this G1** sets TDP through **RyzenAdj**: it ships `libryzenadj`, the PawnIO, inpoutx64 and WinRing0 drivers, and a `readjust` service that keeps re-applying values. It uses **RTSS** for frame limiting and on-screen display, and **PresentMon** for FPS. It already stores **per-game presets bound to game executables** (for example Onimusha at 15 W; see `device-records/OneXConsole-preset-status.txt`). Because it re-applies its own values, CreteOS must not drive RyzenAdj alongside it.
- **Plan:** CreteOS gets one Performance screen with a backend per device.
  - **G1:** read and write OneXConsole's own per-game presets and bindings, so CreteOS shows, edits and creates a profile for the game you're on. First, check how OneXConsole reloads changes. The journal says new values load on an OneXConsole restart or sign-in.
  - **Generic AMD:** RyzenAdj through PawnIO, the signed driver; WinRing0 is now flagged by Defender.
  - **Refresh and resolution:** the existing `launch_display.rs`.
  - **Frame limit:** RTSS where installed, or AMD's own limiter.
- **Over the game:** an **Xbox Game Bar widget**, the same approach Winhanced uses for its HUD. A small sideloaded UWP widget talks to CreteOS over a named pipe and shows:
  - Resume, Close game, Return to CreteOS
  - Power preset
  - Frame limit
  - Brightness and volume
  - Save status

  It works over fullscreen games and while the main window is hidden.
## 12. Winhanced: what it does and what CreteOS should take

Sources:
- the official changelog, about 35 releases from 0.9.1.2 to 1.0.0.6 (11 Nov 2026), at <https://www.winhanced.com/support>
- the FAQ at <https://www.winhanced.com/faq>
- the 1.0 beta notes at <https://www.winhanced.com/support/patch-notes/1-0-beta/>
- PC Guide, XDA and Notebookcheck coverage

Reddit and Patreon were not readable.

**Biggest findings:**
- **Winhanced does not support the G1, or the X1.** On unknown devices it falls back to a generic power slider. Proper G1 power, fan and controller support is a place CreteOS can clearly beat it.
- **It takes over from the OEM app.** On the OneXPlayer 3 it replaced OneXConsole, because OneXConsole kept putting its own power limits back.
- **It found a hidden 25 W firmware cap** on the OneXPlayer 3 that it can raise only while OneXConsole is installed. Check whether the G1 has the same cap.
- **It has a single "Device Control" switch** that hands the hardware back to the vendor app when off.
- **Power:**
  - PawnIO driver and vendor-native routes, with frame caps through RTSS.
  - Silent, Balanced, Performance, Custom and Dynamic modes. "AutoPilot" dynamic TDP holds a target FPS at the least power.
  - Per-game profiles split by battery, AC and eGPU.
  - Community "Smart Profiles".
  - Fan curves, a charge limit, and refresh, resolution, HDR and brightness controls.
- **Overlay:**
  - **HUD 2.0** is an Xbox Game Bar widget, opened with Guide or Win+G. It runs even when Winhanced is closed.
  - **1.0 adds its own full-screen overlay** on Guide, with these pages: Current game (pause, suspend, exit), Performance, Battery, Controller, Power (sleep, hibernate, restart, shut down), Audio, Network, Recording, Music, Guides, Journal, Friends and Discord. The game drops to 30 fps while it is open.
- **Settings sections:**
  - General
  - Libraries, with Storage at the top and one page per store
  - Performance
  - Controller, shown on a picture of the device
  - Integrations
  - Update Center (app, drivers and firmware, Windows Updates held until you stop playing)
  - Updates & Support, with "Report an issue" plus logs

  **Options the device cannot use are hidden, not greyed out.**
- **Libraries:**
  - Stores: Steam, Xbox/Game Pass, Epic, GOG, PlayStation (Remote Play via Chiaki), EA, Ubisoft, Battle.net and Amazon.
  - Streaming and cloud: xCloud, GeForce NOW and Moonlight.
  - Emulators and ROMs: 108 emulators.
  - Apps and PWAs, plus a "Theatre" view for video apps.
  - Steam, Epic, GOG and Battle.net install inside the app.
  - Collections, favourites, hidden games, and Resync/Rescan on every store page.
- **Other features:**
  - a price-comparison store and wishlist
  - cross-store achievements and playtime
  - a Friends hub covering Steam, Xbox, PSN and Discord
  - its own PC-streaming host companion
  - low-power downloads
  - Quick Resume, which suspends games
  - a sleep engine that logs and blocks bad wake sources
  - a screenshot and clip recorder
  - RGB and per-game remapping
  - a crash watchdog
  - 34 languages
- **Reception:** praised for console feel, one overlay for all the performance controls, and reliable sleep. Criticised for lock-ups (XDA prefers Xbox Mode for stability), early scaling and resume bugs, Patreon-gated early features, and stutter while the library syncs after boot.

**What CreteOS already does better:**
- the Bosgame/Lutris library and Moonlight host automation
- Wi-Fi routing per service
- dock-aware streaming
- versioned saves with conflict-safe Drive sync and protected restores
- a recovery pack for setting up a replacement device

**Take from Winhanced, in order:**
1. Seven task-based settings sections, with capability-based hiding (see §10).
2. A G1 Performance backend that cooperates with OneXConsole, plus a Device Control switch for later.
3. An overlay. Start with a Game Bar widget, which is the least risky and works over fullscreen games. Later, a full CreteOS overlay on Guide with Current game, Performance, Power and Audio pages.
4. Suspend/Quick Resume, and the Power menu with Sleep and Hibernate.
5. A Storage page and an Update Center (drivers, Windows Updates held while playing).
6. Report an issue with logs.
7. A Status Pill: active sessions, search, per-app volume, battery, Wi-Fi/Bluetooth.
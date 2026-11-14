# CreteOS 1.0 roadmap

Owner direction, 13 November 2026:
- Keep CreteOS's own look.
- Match Winhanced's feature set, especially its settings, and SteamOS's console behaviour.
- Fix every audit finding, including the lag and the loading-screen flashes.
- The bar is a polished, award-quality handheld OS: smooth, beautiful, and reliable at what matters.

Evidence for every item is in [the full audit](AUDIT_2026-11-13_SAVES_AND_EXPERIENCE.md). Section numbers below (§) refer to it.

## Ground rules

- **Look:** Crete stays. New screens use the existing Crete tokens (colours, radii, dialog styles). Do not borrow Winhanced's visual design, code or artwork; rebuild the features in our own way.
- **Smoothness budget:**
  - Controller response feels immediate: no dead windows on opening (fixed for modals in batch 1).
  - Navigation and scrolling hold 60 fps on the G1 in handheld and docked modes.
  - No visible flash of the desktop or of CreteOS during launch or return.
  - Measure with `utils/inputDiagnostics.ts` and screen recordings, before and after each change.
- **Owner preferences:**
  - No button legend.
  - Navigation sounds stay off by default; the existing toggle remains.
  - Play may prefer streaming from Bosgame.
  - Holding A never starts anything.
- **Every batch:**
  - tests and a type check
  - a local G1 build and install with a rollback copy
  - an on-device check
  - a journal entry
  - the guide updated
  - a verified private backup
  - a new SanDisk pack
  - a commit and push
- **Never copy:** paid early-access gating, anonymous usage telemetry, or any certificate or driver installed without telling the owner.

## Batch 1: done on 13 November (source `ca93352`)

- **Manage saves**: automatic read-only check, Back up now, preview-and-confirm restore with a locked "Before restore" copy, and Sync with Google Drive. Sync is safe in both directions, and the Bosgame hook uses the same helper.
- **PlayStation launches** no longer fail at the launch cover.
- **No more leaked presses:** the "Close game?" dialog, and Manage saves opened from Play, no longer pass button presses to the screen behind them. ConfirmModal accepts Enter and Esc.
- **Held buttons ignored:** a button held when a dialog opens is never treated as a new press. Ten dialogs fixed, with a regression test.
- **Launch options stay available while play options load.** Manage game no longer leaves the focus on Play.
- **Less GPU work:** Crete no longer blurs the live screen behind panels, bars and dialogs, and the Home backdrop blur runs at quarter size. Game cards are memoised.

## Owner's overnight brief (14 Nov, 00:30)

Work through every batch on your own. The bar: someone docks this to a TV, picks up a controller and uses it like a PlayStation. The device must:
- stream Moonlight from Bosgame
- stream GeForce NOW and Amazon Luna perfectly
- play local games, with seamless loading, opening and exiting
- back up saves
- show achievements
- install games from Steam, Epic and GOG when signed in
- control per-game TDP and fan profiles
- offer a Game Bar widget
- keep Settings clean, with unused options removed

The aim is our own Winhanced. Each batch ends with tests, an install, an on-device check, a commit, a push and a SanDisk pack.

## Batch 2: smooth and trustworthy

1. **Instant start.** Show the saved library straight away and rescan in the background. The 13 Nov start-up showed a 27 s splash; the Steam scan alone took 20.7 s.
2. **Launch and return without flashes.**
   - Record traces with `fse.log` plus screen video for local Steam, a direct .exe, Moonlight and GeForce NOW.
   - Remove each gap: cover z-order against launcher windows, display-mode switches, the WebView wake-up on return (about 65 ms recorded), and GeForce NOW's dark start.
   - The loading screen shows the game name and the real phase (Updating, Syncing saves, Waiting for Epic). Jokes become a secondary line.
   - Launcher prompts drop the cover instead of hiding behind it (§4).
3. **Scrolling.**
   - Measure on the G1 first.
   - Virtualise the Library grid.
   - Stop the whole app re-rendering on idle.
   - Memoise the app-level context values.
   - Move the components defined inside `App` out to module level (§5.10).
4. **Steam installs.** No 40-minute cut-off while progress is moving; re-attach to downloads that are already running; longer uninstall wait; a download only counts as "uninstalled" when Steam confirms it (§7.3–4).
5. **Launch robustness:**
   - Real local cancel.
   - Per-store and per-phase timeouts.
   - Direct exes get a working directory; UAC and elevation errors are shown clearly.
   - Crash and hang detection.
   - "End Bosgame session" on the "already streaming" error.
   - Re-attach the exit watcher on Resume (§4).
6. **Settings that never reset:** tolerant field-by-field loading, plus atomic saves (§6).
7. **Manage saves:**
   - The Bosgame Drive read gets a longer limit (the screen showed "Couldn't reach Google Drive").
   - Messages name the device (Bosgame or this PC).
   - Safety-net versions for Steam, Epic and GOG Galaxy games, using the manifest's cloud flags.
   - A status line on the game page.
8. **Small input fixes:**
   - B steps back one level.
   - Start toggles the quick menu.
   - The left stick gets hysteresis.
   - The on-screen keyboard can submit, uses the right glyphs and is translated.
   - One keyboard everywhere.

## Batch 3: settings rebuilt on the Winhanced model (§10, §12)

The sections:
- General
- Libraries (Storage at the top, one page per store, Resync and rescan, hidden games)
- Performance
- Controller (shown on a picture of the G1)
- Streaming (host setup: name, address, SSH status, Moonlight, GeForce NOW, home Wi-Fi from saved networks)
- Saves
- Updates & Support

Options the device can't use are hidden, not greyed out.
- **Remove:** every non-Crete theme and its options, bottom-bar, tab-bar and bumper settings, settings nothing reads, and the leftover links.
- **Replace hard-coded values** with configuration: "Bosgame", the Hyperoptic SSIDs, Arctis, XREAL.
- **Onboarding** becomes Welcome → Device → Libraries → Customise → Done.

## Batch 4: power and performance (§11)

- A Performance backend per device.
  - **G1:** cooperate with OneXConsole's per-executable presets (RyzenAdj through its own service). Do not run a competing RyzenAdj loop.
  - Check whether the G1 has a firmware power cap like the OneXPlayer 3's 25 W.
- Power modes: Silent, Balanced, Performance and Custom, separately for battery and AC.
- Per-game profiles, applied on launch and restored on exit.
- Refresh rate and resolution (existing `launch_display.rs`).
- Frame limit through RTSS or AMD.
- Battery charge limit where the hardware supports it.
- **Fan profiles** (owner request, 14 Nov): quiet, balanced and performance curves, plus custom ones, per game. They go through OneXConsole's own fan settings on the G1, never raw embedded-controller writes, unless the protocol is verified on the device and the owner agrees.
- Later: dynamic power that holds a target frame rate, and a Device Control switch that hands the hardware back to OneXConsole.

## Batch 5: overlay over games

1. **CreteOS Game Bar widget** (UWP, named pipe to CreteOS, installed directly on this device). It shows:
   - Resume, Close game, Return to CreteOS
   - power mode
   - frame limit
   - brightness and volume
   - save status and a performance read-out
2. **Full CreteOS overlay** on the Xbox button. Pages:
   - Current game: session, achievements, suspend, exit
   - Performance
   - Battery: history and charge limit
   - Controller
   - Power
   - Audio: per-app volume
   - Network: Wi-Fi, Bluetooth, ping

## Batch 6: sleep and resume

- Sleep and Hibernate in the Power menu. Sleep needs no confirmation; Restart and Shut down warn when a game is running.
- Resume handling: re-check controllers, display, Wi-Fi and Moonlight, and retry pending save uploads.
- Quick Resume: suspend a game and resume it later.
- Log wake sources and block bad ones.
- Low-power downloads.
- Test on real hardware: cold boot, sleep, hibernate, dock and undock.

## Batch 7: system

- **Storage page:** drives, games per drive, move, cleanup.
- **Update Center:**
  - CreteOS self-update
  - OneXPlayer drivers and firmware
  - Windows Updates held until you stop playing
- **Report an issue:** a few controller-friendly questions, with logs attached and a reference number.
- **Notifications and toasts:** backups, uploads, downloads, stream errors, updates.
- **Status pill:** running games, universal search, per-app volume, battery graph, Wi-Fi and Bluetooth.
- **Crash protection:** an error boundary per screen and a watchdog.

## Batch 8: library parity

- **Installs inside CreteOS:** Steam, Epic, GOG, Battle.net.
- **EA, Ubisoft and Amazon** detection.
- **Favourites and filters:** Favourites, Backlog, Recently added, and user collections with filters.
- **Cross-store data:** achievements across stores, and playtime from every store.
- **Friends hub:** Steam, Xbox, PSN, Discord.
- **Store browser:** price comparison and a wishlist with price-drop alerts.
- **Apps and PWAs,** plus a Theatre view for video apps.
- **Emulators:** detection, RetroAchievements, ROM folders. CreteOS supplies no ROMs or BIOS files.

## Batch 9: extras

- Screenshots and a clip recorder.
- RGB and button remapping on the G1, only after the HID protocol is verified on the device.
- OptiScaler and Lossless Scaling integration, never for anti-cheat games.
- Music.
- Per-game guides and notes.
- Discord.

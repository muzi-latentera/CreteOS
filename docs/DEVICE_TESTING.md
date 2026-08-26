# DEVICE_TESTING.md — KONKR Pocket FIT Elite test checklist

Target device: **KONKR Pocket FIT Elite** (SM8750 / Snapdragon 8 Elite)

Run through this checklist on each significant change before tagging a release.

---

## Core installation

- [ ] APK installs without error
- [ ] App launches to eOr home screen
- [ ] Controller navigation works (D-pad, A/B, shoulder buttons)
- [ ] Set app as Android HOME launcher
- [ ] Home button returns to CreteOS
- [ ] Suspend/resume works correctly (screen off → on → launcher visible)
- [ ] Reboot → unlock → CreteOS appears (not stock launcher)
- [ ] Screen stays landscape orientation

---

## eOr legacy functionality (regression check)

- [ ] ROM scan finds ROMs in configured folder
- [ ] Emulator launches (RetroArch or any installed emulator)
- [ ] Android game launches
- [ ] Artwork loads and displays
- [ ] Settings screen opens
- [ ] Locked Mode still works
- [ ] Dual-screen mode still builds (even if not testable on this device)

---

## GameNative

- [ ] GameNative installed
- [ ] eOr Steam scan imports at least one game
- [ ] Game appears in CreteOS library
- [ ] PLAY launches GameNative directly (does not open library)
- [ ] Game starts correctly
- [ ] Existing GameNative saved settings (driver, DXVK, etc.) are preserved — verify inside GameNative
- [ ] Exiting game returns to CreteOS

---

## XREAL One S display

- [ ] Plug XREAL One S via USB-C
- [ ] Settings → PC & Streaming shows external display detected
- [ ] Reported resolution is 1920×1200 (or as reported by XREAL)
- [ ] Launch GameNative game — observe container_config in logcat:
  ```
  adb logcat -s GameNativeProvider
  # expect: "Applying temporary screenSize override: 1920x1200"
  ```
- [ ] Game launches at correct external resolution
- [ ] Unplug XREAL
- [ ] Next launch uses 1920×1080 (internal)
- [ ] Check inside GameNative: saved game config has NOT been permanently modified

---

## WinNative

- [ ] WinNative installed
- [ ] Export frontend shortcuts from WinNative
- [ ] Settings → PC & Streaming → WinNative → select export folder
- [ ] Rescan finds exported game(s)
- [ ] Game appears in CreteOS library
- [ ] PLAY launches game directly via shortcut
- [ ] WinNative uses its own saved container settings

---

## Winlator CMod

- [ ] Winlator CMod installed
- [ ] Export frontend shortcuts
- [ ] Rescan finds shortcut(s)
- [ ] Direct launch works

---

## GameHub Lite

- [ ] GameHub Lite installed
- [ ] Steam game imported (has Steam AppID)
- [ ] Direct launch via `gamehub.lite.LAUNCH_GAME` works
- [ ] Falls back gracefully if direct launch fails

---

## Moonlight / Sunshine

- [ ] Moonlight installed
- [ ] Sunshine running on PC
- [ ] PC paired in Moonlight
- [ ] Sunshine app (game) configured in Sunshine
- [ ] Manual link: CreteOS game → Moonlight PC + App
- [ ] PLAY starts stream directly without visiting Moonlight library
- [ ] Stream quality acceptable at 1080p

---

## GeForce NOW

- [ ] GeForce NOW installed and logged in
- [ ] Manual link: game → GFN game ID
- [ ] PLAY opens GFN directly to game (or to library if no ID set)

---

## Multi-provider (core scenario)

Test game: **Hollow Knight** (Steam AppID 367520) or any installed game.

- [ ] Game has GameNative target (from Steam scan)
- [ ] Game appears once in library
- [ ] PLAY uses preferred target (GameNative)
- [ ] Long-press / detail screen shows "Play Using…" button
- [ ] Play Using dialog shows all available targets
- [ ] Launching from dialog works for each available provider
- [ ] Setting a different preferred target persists after app restart
- [ ] Unavailable provider shown as disabled in dialog (not a crash)

---

## Display diagnostics

- [ ] Settings → PC & Streaming → displays show correct info
- [ ] Internal display: 1920×1080
- [ ] XREAL attached: external display listed with correct resolution

---

## Update check

- [ ] Settings → About shows CreteOS version
- [ ] Update check points at `muzi-latentera/CreteOS` GitHub releases
- [ ] Does NOT offer official eOr APK as an update

---

## Known limitations / blockers

Document any hardware-specific issues discovered during testing here.

| Issue | Severity | Notes |
|---|---|---|
| | | |

---

## Test environment

| Field | Value |
|---|---|
| Device | KONKR Pocket FIT Elite |
| Android version | |
| Build | |
| Test date | |
| Tester | |

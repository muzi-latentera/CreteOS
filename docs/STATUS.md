# CreteOS — Functionality Status Matrix

Last updated: 2026-08-26

## Key

- ✅ DONE — implemented and tested on real hardware
- 🟡 PARTIAL — code exists but untested / incomplete / has a known issue
- ❌ NOT STARTED — interface/stub exists at most
- 🚫 BLOCKED — waiting on hardware or upstream feature

---

## Foundation

| Item | Status | Notes |
|---|---|---|
| Fork identity (applicationId, app name, updater) | ✅ DONE | io.latent.creteos, updater points at muzi-latentera/CreteOS |
| Upstream sync GitHub Action | ✅ DONE | Weekly PR from keweis2/eOr → main → product |
| CI build/test | ✅ DONE | Runs on product branch pushes |
| PocketDatabase (sidecar Room DB) | ✅ DONE | creteos_pocket.db, schema v1 |
| LaunchTargetRepository | ✅ DONE | Entities, DAOs, domain models all present |
| UnifiedLaunchCoordinator | ✅ DONE | Tested — fires before eOr, falls through to eOr when no target |
| LaunchGameUseCase seam | ✅ DONE | 1 upstream file modified, coordinator injected |
| HOME launcher declaration | ✅ DONE | Tested on Fold 8 — Home button returns to CreteOS |
| eOr regression (ROMs, Android games, settings) | 🟡 PARTIAL | Not formally re-tested after UI changes |
| Unit tests (coordinator, GameNative provider) | 🟡 PARTIAL | 13 tests pass; no tests for artwork resolver, sync, or other providers |
| Upstream eOr merge test | ❌ NOT STARTED | Simulated merge never run |

---

## GameNative

| Item | Status | Notes |
|---|---|---|
| Direct launch intent (LAUNCH_GAME, app_id, game_source) | ✅ DONE | Tested with Bastion (107100) — confirmed in logcat |
| Existing GameNative config preserved (no container_config sent) | ✅ DONE | Verified in tests |
| HOME return after game exit | ✅ DONE | Tested on Fold 8 |
| Frontend sync file discovery | ❌ NOT STARTED | GameNative 1.2.0 doesn't have Frontend Sync yet |
| Automatic library sync via eOr GameRepository | ❌ NOT STARTED | Currently ADB debug seeding only — **must not reach production** |
| Multiple GameNative games | ❌ NOT STARTED | Only Bastion seeded manually |

---

## Artwork

| Item | Status | Notes |
|---|---|---|
| Steam CDN cover URL (library_600x900.jpg) | 🟡 PARTIAL | Works for Bastion via manual DB injection — not a real feature |
| Steam CDN hero URL (library_hero.jpg) | 🟡 PARTIAL | Same — injected manually, had wrong DB field bug |
| PcGameArtworkResolver | ❌ NOT STARTED | Needs implementing using MediaRepository.upsertMedia() / downloadAndCacheBoxArt() |
| Non-Steam PC game artwork | ❌ NOT STARTED | Falls back to eOr scraper only |
| Emulated game artwork | ✅ DONE | eOr scraper handles this unchanged |
| Android game artwork | ✅ DONE | eOr handles this unchanged |
| Direct SQLite writes to gamelauncher.db | 🚫 MUST REMOVE | DebugSeedReceiver raw SQLiteDatabase calls — debug only, never production |

---

## Providers

| Item | Status | Notes |
|---|---|---|
| GameNativeProvider — launch | ✅ DONE | Tested |
| GameNativeProvider — discovery | ❌ NOT STARTED | Returns emptyList() pending Frontend Sync |
| GameHubLiteProvider — launch | 🟡 PARTIAL | Intent constructed, package unverified on real device |
| GameHubLiteProvider — discovery | ❌ NOT STARTED | Returns emptyList() |
| WinNativeProvider — launch | 🟡 PARTIAL | Stub only, package name unverified |
| WinNativeProvider — discovery (.desktop SAF) | ❌ NOT STARTED | Returns emptyList() |
| WinlatorProvider — launch | 🟡 PARTIAL | Stub only, package name unverified |
| WinlatorProvider — discovery (.desktop SAF) | ❌ NOT STARTED | Returns emptyList() |
| MoonlightProvider — launch (ShortcutTrampoline) | 🟡 PARTIAL | Activity name unverified on current Moonlight build |
| MoonlightProvider — discovery (Android shortcuts) | ❌ NOT STARTED | Returns emptyList() |
| GeForceNowProvider — launch (deep link) | 🟡 PARTIAL | Deep link constructed, untested |
| GeForceNowProvider — discovery | ❌ NOT STARTED | Manual linking only by design |
| ProviderSyncCoordinator | ❌ NOT STARTED | Not implemented — rescan calls provider.discoverGames() but nothing processes results |

---

## Library / Game model

| Item | Status | Notes |
|---|---|---|
| Multiple launch targets for one game | 🟡 PARTIAL | DB schema supports it, no UI path to add a second target |
| Preferred target persistence | 🟡 PARTIAL | Repository method exists, not wired to any UI trigger |
| Play Using… dialog | 🟡 PARTIAL | PlayUsingDialog.kt exists as a composable, not integrated into navigation |
| Manual game linking | ❌ NOT STARTED | ManualGameLinkEntity exists in DB, no UI or logic to use it |
| Synthetic provider-only games | ❌ NOT STARTED | Described in brief, not implemented |
| Provider availability / missing-app handling | 🟡 PARTIAL | markProviderUnavailable() exists, not called on any real failure path yet |
| Provider rescan from UI | ❌ NOT STARTED | ProviderSettingsScreen has a button, ProviderSyncCoordinator not implemented |
| GameIdentityResolver (dedup by Steam AppID) | ❌ NOT STARTED | Discussed in brief, no implementation |

---

## Display / XREAL

| Item | Status | Notes |
|---|---|---|
| GamingDisplayManager (DisplayListener, external display detection) | 🟡 PARTIAL | Code written, logic sound, never run on real hardware |
| LaunchContext built with live display state | ✅ DONE | Wired into UnifiedLaunchCoordinator |
| GameNative temporary screenSize override (AUTO_MATCH_DISPLAY) | 🟡 PARTIAL | Code present and unit tested — not tested on KONKR + XREAL |
| Display diagnostics screen | ❌ NOT STARTED | GamingDisplayManager.getAllDisplays() exists, no screen to show it |
| XREAL acceptance test (logcat verify container_config) | 🚫 BLOCKED | Waiting for Pocket FIT Elite + XREAL hardware |

---

## UI

| Item | Status | Notes |
|---|---|---|
| CreteOS status bar (clock, battery, WiFi, power button) | ✅ DONE | Working on Fold 8 |
| Home screen with hero + carousel | 🟡 PARTIAL | Hero artwork visible when DB populated; hero still blank until artwork resolver built |
| Library grid with filter chips | 🟡 PARTIAL | Screen exists, filters by platformId — untested with multiple games |
| Dynamic accent colour from Palette API | 🟡 PARTIAL | Code present, requires loaded artwork to activate |
| Blurred hero background | 🟡 PARTIAL | Fixed in last commit — needs validation |
| Play Using… integrated into navigation | ❌ NOT STARTED | Dialog exists but not reachable from any screen |
| Provider settings screen (rescan, status) | 🟡 PARTIAL | Screen exists, rescan button wired but ProviderSyncCoordinator is a no-op |
| Power menu (Sleep/Restart) | 🟡 PARTIAL | Dialog exists; Restart will fail on non-privileged APK — must be corrected |
| WinHanced visual redesign (full) | 🟡 PARTIAL | **Frozen** — skeleton in place, not to be continued until functionality complete |

---

## Guardrails (things that must never reach production)

| Item | Current state | Required action |
|---|---|---|
| DebugSeedReceiver raw SQLiteDatabase writes to gamelauncher.db | In debug receiver | Add `BuildConfig.DEBUG` guard; document as debug-only |
| Manual ADB sqlite3 database manipulation | Dev workflow only | Remove from any documented process; replace with PcGameArtworkResolver |
| PowerManager.reboot() in status bar power button | Likely fails silently | Replace with Android Power Menu intent or Lock Screen only |

---

## Immediate next priorities (in order)

1. **Guard DebugSeedReceiver** — add `BuildConfig.DEBUG` check, never modifies gamelauncher.db in production
2. **PcGameArtworkResolver** — uses `GameRepository.insertGame()` + `MediaRepository.upsertMedia()` / `downloadAndCacheBoxArt()`, no raw SQLite
3. **ProviderSyncCoordinator** — real implementation that calls discoverGames() and routes through GameRepository
4. **GameNative sync** — wire ScanSteamLibraryUseCase to trigger provider scan; when Frontend Sync files exist, import them
5. **Play Using… navigation** — wire PlayUsingDialog into game detail screen
6. **Preferred target UI** — star button in Play Using dialog persists to LaunchTargetRepository
7. **Moonlight package/activity verification** — check com.limelight ShortcutTrampoline against current Moonlight build on device
8. **Display diagnostics screen** — needed before XREAL test
9. **XREAL acceptance test** — on Pocket FIT + XREAL, verify logcat shows container_config with correct resolution
10. **eOr regression run** — formal test that ROM launch / Android game launch / scraper still work

UI redesign resumes after items 1–9 are complete or individually documented as blocked.

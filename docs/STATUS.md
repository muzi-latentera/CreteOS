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
| Unit tests (coordinator, GameNative provider) | ✅ DONE | 22 tests pass (production-path integration tests) |
| Upstream eOr merge test | ❌ NOT STARTED | Simulated merge never run |

---

## GameNative

| Item | Status | Notes |
|---|---|---|
| Direct launch intent (LAUNCH_GAME, app_id, game_source) | ✅ DONE | Tested with Bastion (107100) — confirmed in logcat |
| Existing GameNative config preserved (no container_config sent) | ✅ DONE | Verified in tests |
| HOME return after game exit | ✅ DONE | Tested on Fold 8 |
| GameNative frontend sync file discovery | ✅ DONE | **Marker file format VERIFIED from GameNative 1.2.0 source (2026-08-26).** See below for details. Uses eOr's Steam Library Folder setting first, falls back to common paths. |
| Automatic library sync via eOr GameRepository | ✅ DONE | ProviderSyncCoordinator uses GameRepository.insertGame() + GameIdentityResolver with LibraryIndex optimization. |
| Multiple GameNative games | 🟡 PARTIAL | Will work once Frontend Sync is configured — user must set same folder in both apps |

### GameNative Marker File Format (Verified 2026-08-26)

Source: `FrontendSyncManager.kt` in utkarshdalal/GameNative

All marker files contain **integer AppIDs as UTF-8 strings**:

| Extension | GameNative DAO | Entity ID Type | File Content |
|-----------|----------------|----------------|--------------|
| `.steam` | `SteamAppDao.getInstalledGames()` | `SteamApp.id: Int` | AppID as string |
| `.gog` | `GOGGameDao.getInstalledGames()` | `GOGGame.id: String` → `toIntOrNull() ?: 0` | **Converted to Int (or 0 if non-numeric)** |
| `.epic` | `EpicGameDao.getInstalledGames()` | `EpicGame.id: Int` (auto-gen) | AppID as string |
| `.amazon` | `AmazonGameDao.getInstalledGames()` | `AmazonGame.appId: Int` (auto-gen) | AppID as string |
| `.pcgame` | `SteamAppDao.getInstalledGames()` | `SteamApp.id: Int` | AppID as string |

**Note:** GOG IDs in the GOG API are strings, but GameNative converts them to Int. If a GOG ID is non-numeric, the marker file contains "0". This is a GameNative limitation.

### Folder Resolution

1. **Primary:** eOr's configured Steam Library path (`Settings → Games → Steam Library Folder`)
2. **Fallback:** Common hardcoded paths (`/sdcard/ROMs`, `/sdcard/Games`, etc.)

User must set the **same folder** in both:
- CreteOS: `Settings → Games → Steam Library Folder`
- GameNative: `Settings → Interface → Frontend Sync`

This is **File-based access**, not SAF. Works on accessible storage only.

---

## Artwork

| Item | Status | Notes |
|---|---|---|
| Steam CDN cover URL (library_600x900.jpg) | ✅ DONE | PcGameArtworkResolver.setRemoteUrlsForSteamGame() |
| Steam CDN hero URL (library_hero.jpg) | ✅ DONE | Cover and hero resolved independently |
| PcGameArtworkResolver | ✅ DONE | Uses MediaRepository.upsertMedia() — never raw SQL |
| Non-Steam PC game artwork | 🟡 PARTIAL | Falls back to eOr scraper. GOG/Epic/Amazon IDs correctly NOT sent to Steam CDN. |
| Emulated game artwork | ✅ DONE | eOr scraper handles this unchanged |
| Android game artwork | ✅ DONE | eOr handles this unchanged |
| Direct SQLite writes to gamelauncher.db | 🚫 MUST REMOVE | DebugSeedReceiver raw SQLiteDatabase calls — debug only, never production |

---

## Providers

| Item | Status | Notes |
|---|---|---|
| GameNativeProvider — launch | ✅ DONE | Tested |
| GameNativeProvider — discovery | ✅ DONE | Reads .steam/.gog/.epic/.amazon/.pcgame files. Uses eOr's Steam Library Folder setting. |
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
| ProviderSyncCoordinator | ✅ DONE | Full implementation: LibraryIndex optimization, stale reconciliation, isPreferred preservation |

---

## Library / Game model

| Item | Status | Notes |
|---|---|---|
| Multiple launch targets for one game | 🟡 PARTIAL | DB schema supports it, no UI path to add a second target |
| Preferred target persistence | ✅ DONE | isPreferred preserved across resyncs (verified in tests) |
| Play Using… dialog | 🟡 PARTIAL | PlayUsingDialog.kt exists as a composable, not integrated into navigation |
| Manual game linking | ❌ NOT STARTED | ManualGameLinkEntity exists in DB, no UI or logic to use it |
| Synthetic provider-only games | ✅ DONE | Provider-namespaced keys (moonlight:, gfn:, etc.) for unresolved games |
| Provider availability / missing-app handling | ✅ DONE | markProviderUnavailable() called when provider.isAvailable() returns false |
| Provider rescan from UI | 🟡 PARTIAL | ProviderSettingsScreen has button, coordinator fully implemented |
| GameIdentityResolver (dedup by Steam AppID) | ✅ DONE | Store ID > Manual Link > Title Match priority; LibraryIndex optimization |

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
| Provider settings screen (rescan, status) | ✅ DONE | Screen exists, rescan button wired to ProviderSyncCoordinator |
| Power menu (Sleep/Restart) | 🟡 PARTIAL | Dialog exists; Restart will fail on non-privileged APK — must be corrected |
| WinHanced visual redesign (full) | 🟡 PARTIAL | **Frozen** — skeleton in place, not to be continued until functionality complete |

---

## Guardrails (things that must never reach production)

| Item | Current state | Required action |
|---|---|---|
| DebugSeedReceiver raw SQLiteDatabase writes to gamelauncher.db | **FIXED** — BuildConfig.DEBUG guard added | Will silently no-op in release builds |
| Manual ADB sqlite3 database manipulation | Dev workflow only — replaced by ProviderSyncCoordinator | Use GameNative Frontend Sync folder for production imports |
| PowerManager.reboot() / POWER_MENU_LAUNCH | **FIXED** — both removed; tested on Android 17, neither works from normal APK | Power button now shows Lock Screen only, with device-admin explanation |

## Correctness pass results (2026-08-26)

All 8 architecture bugs from review fixed and tested:

| Bug | Status |
|---|---|
| GameIdentityResolver source-key ordering (GOG/Epic treated as Steam) | ✅ Fixed — source wins over provider |
| Title matching not implemented | ✅ Fixed — LibraryIndex with byNormalisedTitle map |
| GameIdentityResolver not wired into ProviderSyncCoordinator | ✅ Fixed — every discovered game goes through resolver |
| Steam CDN used for GOG/Epic/Amazon IDs | ✅ Fixed — CDN only when source=="STEAM" |
| PcGameArtworkResolver: cover/hero not independent | ✅ Fixed — cover and hero resolved independently |
| Moonlight: fabricated pcName from activity.packageName | ✅ Fixed — pcName removed; startShortcut() primary |
| isPreferred reset on rescan | ✅ Fixed — currentPreferred preserved from existing target |
| Stale-target reconciliation missing | ✅ Fixed — seen IDs tracked; unseen targets marked unavailable |
| GameNative claimed to have no Frontend Sync | ✅ Corrected — PR #1454 verified in 1.2.0 source |
| GameIdentityResolver O(n²) query pattern | ✅ Fixed — LibraryIndex built once per sync |
| GameNative folder ignores eOr setting | ✅ Fixed — reads steamLibraryPath first |

## Test Coverage

22 tests covering production code paths:

| Test Category | Count | Coverage |
|---|---|---|
| ProviderSyncCoordinator.syncProvider() | 5 | Duplicate prevention, isPreferred preservation, stale reconciliation, artwork invocation |
| GameIdentityResolver.resolve() | 4 | Store ID priority, title matching, ambiguity handling |
| GameNativeProvider | 8 | Launch, availability, host key format, extension mapping |
| PcGameArtworkResolver | 1 | Steam CDN URL format |
| Utility | 4 | buildStoreKey for all sources |

All tests invoke production code paths directly — no illustrative/data-class-only tests.

---

## Immediate next priorities (in order)

1. **Guard DebugSeedReceiver** — add `BuildConfig.DEBUG` check, never modifies gamelauncher.db in production
2. **Play Using… navigation** — wire PlayUsingDialog into game detail screen
3. **Preferred target UI** — star button in Play Using dialog persists to LaunchTargetRepository
4. **Moonlight package/activity verification** — check com.limelight ShortcutTrampoline against current Moonlight build on device
5. **Display diagnostics screen** — needed before XREAL test
6. **XREAL acceptance test** — on Pocket FIT + XREAL, verify logcat shows container_config with correct resolution
7. **eOr regression run** — formal test that ROM launch / Android game launch / scraper still work

UI redesign resumes after items 1–7 are complete or individually documented as blocked.

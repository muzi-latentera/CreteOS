# CreteOS — Implementation Audit

## Upstream baseline

| Field | Value |
|---|---|
| Upstream repo | https://github.com/keweis2/eOr |
| Baseline SHA | 3dd0ea6c65cc4fc5e0f1fd7914aef0c127c5ad16 |
| Baseline tag | v2.6.0 |
| Audit date | 2026-08-26 |

## Project structure

eOr is a native Android app: Kotlin / Jetpack Compose / Hilt / Room / DataStore / Coil.

```
app/src/main/java/com/gamelaunch/frontend/
  domain/
    model/           Game, EmulatorMapping, …
    usecase/         LaunchGameUseCase, CheckForUpdateUseCase, ScanSteamLibraryUseCase
    repository/      GameRepository, EmulatorRepository, SettingsRepository, …
  launcher/
    EmulatorLauncher.kt       ← main launch dispatch
  platform/display/
    DualScreenManager.kt      ← secondary-display / artwork presentation
    DualScreenDevices.kt
  ui/
    screen/
      home/          HomeScreen, HomeViewModel
      detail/        GameDetailScreen, GameDetailViewModel
      settings/      SettingsIndexScreen, …
    dualscreen/      ArtworkBus, ArtworkPresentation, GameSessionState
  data/
    database/        AppDatabase (Room, schema v5)
    preferences/     AppDataStore
    repository/
  MainActivity.kt
```

## Key integration seams

### LaunchGameUseCase
**File:** `domain/usecase/LaunchGameUseCase.kt`

Single call site for launching any game. Currently delegates entirely to `EmulatorLauncher`.
**Our change:** inject `UnifiedLaunchCoordinator` and call `tryLaunch(game)` first.
Falls back to existing `EmulatorLauncher` if coordinator returns null.
This is the **only** change to core eOr launch logic.

### EmulatorLauncher
**File:** `launcher/EmulatorLauncher.kt`

Already contains GameNative direct-launch logic in `launchSteamGame()`:
- action: `app.gamenative.LAUNCH_GAME`
- extras: `app_id` (Int), `game_source` (String)
- Falls back to opening GameNative library if direct launch fails

Also already uses `ActivityOptions.makeBasic().setLaunchDisplayId(displayId)` for dual-screen.

Our `GameNativeProvider` reuses the same intent recipe. We don't duplicate the existing
emulator launcher — for games with no pocket target it still runs unchanged.

### DualScreenManager
**File:** `platform/display/DualScreenManager.kt`

Detects secondary displays via `DisplayManager.DisplayListener`. Uses `setLaunchDisplayId()`
to route games to the correct panel on dual-screen handhelds (RG DS, Thor).

**Our change:** Phase 5 adds `GamingDisplayManager` alongside this (not replacing it) for
XREAL/external PC-display detection. The dual-screen handheld behaviour is preserved.

### CheckForUpdateUseCase
**File:** `domain/usecase/CheckForUpdateUseCase.kt`

**CRITICAL — must fix before any release.**
Hardcodes `REPO = "keweis2/eOr"`. Our fork must point at `muzi-latentera/CreteOS`.
Fixed in Phase 1 via `BuildConfig.UPDATE_REPO`.

## Application identity

| Field | Upstream eOr | CreteOS |
|---|---|---|
| applicationId | `com.gamelaunch.frontend` | `io.latent.creteos` |
| namespace | `com.gamelaunch.frontend` | `com.gamelaunch.frontend` (kept — avoids mass import rename) |
| app name | eOr | CreteOS |
| update repo | keweis2/eOr | muzi-latentera/CreteOS |

## Files we modify in upstream code (full list)

| File | Why | Lines affected | Merge risk |
|---|---|---|---|
| `app/build.gradle.kts` | applicationId, BuildConfig fields | ~10 | Low — isolated block |
| `domain/usecase/LaunchGameUseCase.kt` | inject coordinator, 1 call | ~5 | Low — constructor + 1 line |
| `domain/usecase/CheckForUpdateUseCase.kt` | own update repo | ~3 | Low — companion object |
| `app/src/main/res/values/strings.xml` | app_name = CreteOS | 1 | None |
| `app/src/main/AndroidManifest.xml` | package visibility for provider apps | ~20 | Low — additive only |

**All other custom code lives in new files under `pocket/`.**

## eOr database (DO NOT MODIFY)

AppDatabase is currently at schema version 5. It contains:
- Game, GameEntity, media, emulator mapping, LaunchBox, friends entities

We add a completely separate `PocketDatabase` (`creteos_pocket.db`) for our data.
No changes to AppDatabase schema.

## Known merge risks

- `EmulatorLauncher.kt` — we do NOT modify this; GameNative constants are already correct
- `LaunchGameUseCase.kt` — small constructor injection; any upstream constructor change needs
  a manual re-application of the coordinator injection
- `build.gradle.kts` — applicationId block may drift if upstream changes build config

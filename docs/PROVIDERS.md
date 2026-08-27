# PROVIDERS.md — Backend provider integration reference

Each provider is implemented in `pocket/providers/impl/`.
**Verify package names and intent contracts against the currently installed APK before testing.**
This document must be updated whenever a provider's package name or launch contract changes.

---

## GameNative

| Field | Value |
|---|---|
| Package | `app.gamenative` |
| Launch action | `app.gamenative.LAUNCH_GAME` |
| Key extras | `app_id` (Int), `game_source` (String) |
| Optional extra | `container_config` (String, JSON) |
| Source last verified | eOr EmulatorLauncher.kt audit, 2026-08-26 |

### Discovery
GameNative frontend-sync exports marker files to a user-selected ROMs directory:
- `ROMs/steam/GameTitle.steam` → contains Steam AppID
- `ROMs/gog/GameTitle.gog` → contains GOG AppID
- `ROMs/epic/GameTitle.epic` → etc.

eOr's `ScanSteamLibraryUseCase` already handles this. CreteOS reuses it.

### Launch
Normal launch sends `app_id` + `game_source`, no `container_config`.
GameNative uses its own saved settings for the game.

**XREAL override only:** when `DisplayPolicy.AUTO_MATCH_DISPLAY` is active and an external
display is detected, a temporary `container_config = {"screenSize":"WxH"}` is appended.
This is an in-memory override — it does NOT modify GameNative's saved game configuration.

### Notes
- Direct launch confirmed working in eOr v2.6.0
- `game_source` values: `STEAM`, `GOG`, `EPIC`, `AMAZON`, `CUSTOM`

---

## GameHub Lite

| Field | Value |
|---|---|
| Package (primary) | `gamehub.lite` |
| Package (fork) | `com.producdevity.gamehublite` — verify against installed build |
| Launch action | `gamehub.lite.LAUNCH_GAME` |
| Key extras | `steamAppId` (String), `autoStartGame` (Boolean) |
| Source last verified | GPT research + Daijishō definitions, 2026-08-26 — **NEEDS DEVICE VERIFY** |

### Notes
- Original GameHub was archived January 2026; Producdevity/gamehub-lite is current fork
- GameHub Lite is a patch project against proprietary GameHub APK
- Direct launch may depend on which build variant is installed
- Always falls back to opening GameHub Lite's library if direct launch fails
- Do not rely on this provider as the primary runtime

---

## WinNative

| Field | Value |
|---|---|
| Package | `app.winnative` — **NEEDS VERIFICATION** |
| Launch mechanism | `.desktop` shortcut via `Intent.ACTION_VIEW` |
| Export format | `.desktop` files in user-selected SAF folder |
| Source last verified | GPT research, 2026-08-26 — **NEEDS DEVICE VERIFY** |

### Discovery (Phase 6)
1. User selects WinNative frontend export folder via SAF (`ACTION_OPEN_DOCUMENT_TREE`)
2. Provider indexes `.desktop` files in that folder
3. Each entry creates a `LaunchTarget` with `launchData` = file URI

### Notes
- Package name must be verified against current WinNative release
- We do NOT read WinNative's private container/Wine configuration
- Shortcut is the launch contract — all container settings stay in WinNative

---

## Winlator CMod

| Field | Value |
|---|---|
| Package (CMod) | `com.winlator` or `com.winlator.cmod` — **NEEDS VERIFICATION** |
| Export path | `Downloads/Winlator/Frontend/` |
| Launch mechanism | `.desktop` shortcut via `Intent.ACTION_VIEW` |
| Source last verified | GPT research, 2026-08-26 — **NEEDS DEVICE VERIFY** |

### Discovery (Phase 7)
Same as WinNative: index `.desktop` files from the Winlator frontend export folder.

### Notes
- Only CMod and explicit frontend-export variants are supported
- Vanilla Winlator has no stable direct-launch interface
- Container/compatibility settings stay entirely inside Winlator

---

## Moonlight

| Field | Value |
|---|---|
| Package | `com.limelight` |
| Launch activity | `com.limelight.ShortcutTrampoline` — **VERIFY against current source** |
| Key extras | `PcName`, `UUID`, `AppName`, `AppId` |
| Source last verified | GPT research, 2026-08-26 — **NEEDS DEVICE VERIFY** |

### Discovery (Phase 9)
**Preferred:** enumerate Android launcher shortcuts published by Moonlight via `LauncherApps`.
This avoids any private data access.

**Fallback:** manual linking — user provides PC name and Sunshine app name.

### Launch data JSON
```json
{
  "pcName": "Gaming-PC",
  "uuid": "xxxx-xxxx-xxxx-xxxx",
  "appName": "Cyberpunk 2077",
  "appId": "12345"
}
```

### Notes
- ShortcutTrampoline activity name must be verified against current Moonlight Android source
- Moonlight must already have the PC paired and the Sunshine app configured
- Falls back to opening Moonlight library if ShortcutTrampoline is unavailable

---

## GeForce NOW

| Field | Value |
|---|---|
| Package | `com.nvidia.geforcenow` |
| Deep link | `https://play.geforcenow.com/games?game-id=<GFN-ID>` |
| Source last verified | NVIDIA GFN deep-link spec, 2026-08-26 |

### Discovery
Manual only — no automatic GFN catalog discovery without API key/auth.
GFN game IDs must be manually linked or obtained from the GFN catalog.

### Launch
1. If GFN game ID is set: fire deep link. GFN app handles it if installed.
2. If no game ID: open GFN app library.

### Notes
- NVIDIA does not publish a native Android per-game intent contract (as of 2026-08-26)
- The web deep link is the documented approach for game aggregators
- Ultimate tier supports QHD/120fps on compatible Android devices

---

## Device test status

| Provider | Installed | Discovery | Launch | Notes |
|---|---|---|---|---|
| GameNative | ✅ v1.2.0 | ❌ No export API in 1.2.0 | ✅ Verified | `app.gamenative.LAUNCH_GAME` works. No frontend sync in 1.2.0 — manual seeding only for now |
| GameHub Lite | ✅ v5.1.8 | ❌ Not started | ✅ Verified | `gamehub.lite.LAUNCH_GAME` → `com.xj.landscape.launcher.ui.gamedetail.GameDetailActivity`. Tested 2026-08-26 |
| WinNative | ☐ | ❌ Not started | ❌ Unverified | Package `app.winnative` unconfirmed — install and verify |
| Winlator CMod | ☐ | ❌ Not started | ❌ Unverified | Package unconfirmed |
| Moonlight | ✅ v12.1 | 🟡 Shortcuts API written | 🟡 `ShortcutTrampoline` verified exported | Verified `com.limelight.ShortcutTrampoline` android:exported=true on v12.1. startShortcut() path written, untested |
| GeForce NOW | ✅ installed | N/A (manual) | 🟡 Deep link written | Untested on real game |

Update this table after device testing on the KONKR Pocket FIT Elite.

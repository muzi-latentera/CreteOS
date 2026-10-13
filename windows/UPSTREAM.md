# Windows upstream provenance

- Project: [LiftOff](https://github.com/PixelateWizard/LiftOff)
- Author: Taylor Denby / PixelateWizard
- Imported version: 2.0.2
- Imported commit: `815271be71f13584b617ae04f2043d968c5a9d03`
- License: GNU General Public License version 3, retained in [LICENSE](LICENSE).
- Original documentation: [UPSTREAM-README.md](UPSTREAM-README.md).

CreteOS modifications include application/package identity, the official Meander C artwork, default Crete theme, an Android-style Home/Library/details presentation layer, separate local data and credential namespaces, Windows release filtering, an AnyFSE profile generator, Windows CI and production TypeScript fixes. Internal LiftOff identifiers and optional original themes are retained where changing them adds no product value.

The import includes the React frontend, Tauri/Rust backend, runtime assets and tests. Upstream Git history, CI, editor configuration, legacy application directory and documentation website/media collections were omitted. Future upstream updates should be compared against the pinned commit, preserving this fork's changes.

Distributions of this Windows derivative must comply with the GPL, including providing corresponding source for the distributed binary. Preserve the upstream copyright notices and this attribution. The Android project has its own existing license; the Windows import does not relicense unrelated files.

AnyFSE is a separate [MIT-licensed project](https://github.com/ashpynov/AnyFSE). It is not bundled or installed by this fork. GPD software is separately installed by the device owner.

## Updating LiftOff

Keep `src/crete/` as the presentation boundary. Home and Library wrappers select it only for the Crete theme; optional upstream themes retain their original views. Compare an upstream update against the imported commit above before applying it, and advance that revision only after the update is verified. Do not replace the import with a fresh source dump.

Review these deliberate integration points during updates:

- `src/views/HomeView.tsx`, `LibraryViewContent.tsx`: theme-selected wrappers around the retained LiftOff views.
- `src/components/layout/AppHeader.tsx`, `AppBottomBar.tsx`, `src/App.jsx`: Crete chrome, three main destinations, typed view props and layout/scroll routing. Applications remain accessible through Library → Sources.
- `src/hooks/useGamepadNavigation.ts`: optional view delegate after upstream overlay/launch guards. Store launch, focus handoff and modal priorities stay upstream.
- `src/components/GameDetailsModal.tsx`: named presentation slots, Crete metadata panel, management dropdown and Crete-only keyboard access. Install, uninstall, account and management actions remain the inherited handlers.
- `src/constants.ts`, `src/utils/palettes.ts`, `src/main.jsx`, `src-tauri/src/lib.rs`: theme defaults, styles, identity/data/release namespaces. New installs include owned/uninstalled games; existing saved preferences remain authoritative.
- `src-tauri/src/crete_news.rs` and its command registration: a bounded public RSS request for the Android-style news rail. This adds no dependency and does not alter discovery or launching.
- `src/crete/streaming.ts`, `gfnLinks.json`, `useStreaming.ts` and `src-tauri/src/streaming.rs`: exact Android-curated GFN game-page links and detected Windows streaming clients. Local Play remains preferred for installed games; generic Moonlight/GFN library actions do not establish per-game streaming availability. The GFN table's Android verification is not a Windows session test.
- `src-tauri/src/steam_artwork.rs`: Steam store-response artwork fallback for newer assets with hashed paths. Existing portrait/library art stays preferred; the fallback uses the actual store header/background rather than fabricated imagery.
- `src/types.ts`, UI picker/glyph imports, `src/theme/surfaces.ts` / `tabStyle.ts`, and palette/locales: Crete theme support, product wording and production TypeScript/case-sensitive import fixes. Several inherited files change only branding or comments.
- `src-tauri/tauri.conf.json`, package/crate files, icons, update links and workflow: CreteOS product/release identity and packaging.

Crete Home reads persisted history through existing backend commands because LiftOff's frontend falls back to installed titles on first run. Crete filters operate on the inherited discovered/owned library; this skin does not add bundled emulators, Android games, ROM scraping, Steam achievements, HowLongToBeat data or GameNative on Windows. Those capabilities must not be inferred from the Android reference screenshots.

After each upstream update, run the unit suite, production build, full browser suite and native Windows workflow. Recheck controller return from details, native management availability and one-command launch behavior, then record the imported revision and validation in the app journal.

## Moonlight and MoonDeck on Windows

CreteOS detects the standard Moonlight client and reads the paired host's live advertised apps through the official CLI. Only exact game title matches establish the Moonlight default; Desktop, Steam and MoonDeckStream do not prove installation. Windows automatic selection is local installed → exact live host game → curated GFN link → install. This follows Muzi's requested Windows order; Android remains independent.

Details → Launch options → Moonlight opens a compact stream configuration panel. It refreshes the active display before opening. Defaults: native 1080p/120, XREAL 1200p/90, 4K/60, 80Mbps, automatic codec. Windows monitor identity keys saved preferences; XREAL uses the monitor name or a 1200p mode. Users can override presets, resolution, fps, bitrate and H.264/H.265/AV1. Monitor identity and physical output switching require GPD acceptance.

For Steam entries, MoonDeckStream is selected when advertised. Pair CreteOS separately with MoonDeck Buddy through the panel; the generated PIN is entered on the host. Buddy client identity is stored in Windows Credential Manager. Protocol API 8/9 uses its official public service certificate with hostname checking omitted as in the official client, but certificate trust remains pinned to that certificate; no global TLS changes. CreteOS waits for the stream and Steam, launches the exact Steam ID, observes Running, then ends its own tracked session when that game stops. Existing different-game sessions are refused. These steps require a live native test.

Buddy's API does not supply an installed-game inventory. Automatic routing through MoonDeck for every installed host game still requires a host inventory feed; a published MoonDeckStream entry alone is insufficient. Bosgame's live list was Desktop, Steam Big Picture, Steam, MoonDeckStream and Buddy API8 was reachable on 2026-10-13. No direct native game session is claimed from these read-only checks.

MoonDeck protocol references: https://github.com/FrogTheFrog/moondeck and https://github.com/FrogTheFrog/moondeck-buddy. Moonlight CLI: https://github.com/moonlight-stream/moonlight-qt/blob/master/app/cli/commandlineparser.cpp.

### Bosgame installed inventory via SSH

On 2026-10-13 Muzi authorized SSH access and completed the password prompt locally. Dedicated GPD key authentication to muzidube@Bosgame was verified. CreteOS now runs its fixed, read-only steam_inventory.py via SSH stdin; nothing is installed on the host. It reads Steam/Flatpak roots and external libraries, requires installed StateFlags plus an existing directory, and matches numeric Steam app IDs. Missing SSH/Steam or unreadable records means unknown, not an empty confirmed library. Strict host-key checking and a dedicated key/config live under LOCALAPPDATA/CreteOS-Bootstrap/ssh; private keys and passwords stay out of source/journals. Native Python3 must be available on the host (verified on Bosgame).

When Buddy is paired and MoonDeckStream advertised, an exact installed Steam ID selects MoonDeck automatically ahead of GFN. The backend re-reads installation immediately before launch. SSH queries run off the UI thread with bounded output/timeouts. Local games launch immediately; uninstalled defaults wait for host checks. Non-Steam installations without a published exact host app remain unknown. This supersedes the earlier host-inventory-pending limitation above. Actual direct streaming and physical output switching still need device acceptance.

## Crete feature parity continuation

windows/docs/FEATURE_PARITY.md records the restored Home rows/layout/media settings, retained context/library management controls, uninstalled filter, Spotify, Applications list mode, size information, controller shortcuts and localization. The original optional LiftOff themes remain available. Unsupported fixed Crete navigation settings are filtered from its settings presentation without deleting saved preferences.

Muzi superseded the earlier local-first streaming rule: verified host game→local→GFN→install. A currently running local game still resumes locally; Local is an explicit dropdown action. Android's order remains unchanged.

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
- `src/types.ts`, UI picker/glyph imports, `src/theme/surfaces.ts` / `tabStyle.ts`, and palette/locales: Crete theme support, product wording and production TypeScript/case-sensitive import fixes. Several inherited files change only branding or comments.
- `src-tauri/tauri.conf.json`, package/crate files, icons, update links and workflow: CreteOS product/release identity and packaging.

Crete Home reads persisted history through existing backend commands because LiftOff's frontend falls back to installed titles on first run. Crete filters operate on the inherited discovered/owned library; this skin does not add bundled emulators, Android games, ROM scraping, Steam achievements, HowLongToBeat data or GameNative on Windows. Those capabilities must not be inferred from the Android reference screenshots.

After each upstream update, run the unit suite, production build, full browser suite and native Windows workflow. Recheck controller return from details, native management availability and one-command launch behavior, then record the imported revision and validation in the app journal.

# CreteOS app icon — Meander C

Mark: a Minoan meander folded into a C. Saffron `#E9A93C` on `#0B0E11`.
Geometry: path `M92 20 H20 V80 H92 V60 H40 V40 H72` in a 112x100 box, stroke 13, square caps, miter joins.

## Android (drop into your res/ folder)
- `android/res/drawable/ic_launcher_foreground.xml` — adaptive foreground, vector, mark sized 50/108 so it stays inside the 66dp safe circle for every mask.
- `android/res/drawable/ic_launcher_background.xml` — flat `#0B0E11` background layer.
- `android/res/drawable/ic_launcher_monochrome.xml` — Android 13+ themed-icon layer (white; the system tints it).
- `android/res/mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` — the adaptive-icon manifests wiring the three layers.
- `android/res/mipmap-*/ic_launcher.png` and `ic_launcher_round.png` — legacy raster fallbacks, 48/72/96/144/192 px.
- `android/ic_launcher_foreground_432.png` — transparent 432px foreground if a tool wants a bitmap layer.

Manifest: `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher_round"`.

## Stores
- `play-store-512.png` — Play Console app icon, 512x512, 32-bit PNG, no transparency, no rounding (Play applies its own).
- `app-icon-1024.png` — 1024x1024 square, no alpha. Use for iOS/App Store, sideload listings, and press.

## Web / PWA
- `web/favicon-32.png`, `web/apple-touch-icon-180.png`, `web/pwa-192.png`, `web/pwa-512.png`
- `web/maskable-512.png` — `purpose: "maskable"` variant with the safe-zone padding.

## Vector sources
- `svg/creteos-mark-saffron.svg` / `-cream` / `-dark` / `-currentcolor` — bare mark, transparent background.
- `svg/creteos-adaptive-foreground.svg`, `svg/creteos-adaptive-background.svg` — 108x108 adaptive layers.
- `svg/creteos-icon-1024.svg` — full icon with the background gradient.

## Rules
- Clear space: one stroke width (13 units at source scale) on all sides.
- Minimum size: 20px for the mark on its own, 28px inside a container.
- Never re-stroke, outline, add a second colour to the mark, or rotate it.
- On saffron or any light surface use the dark mark; on photography use the cream mark.

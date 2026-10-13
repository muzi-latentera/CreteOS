# Crete interface feature parity

The Windows foundation remains LiftOff2.0.2 at imported commit815271be71f13584b617ae04f2043d968c5a9d03. This pass restores the Crete presentation gaps audited againste3ca4c9 without replacing discovery, accounts, installs or launch/focus handlers. Android remains independent.

| Audited gap | Current Crete behavior |
|---|---|
| Home pinned rows and collections | Pinned and game/app collection rails, including hidden-collection/name preferences. Enable position/visibility in Appearance→Home; saved preferences remain authoritative. |
| Home customization and animated heroes | Normal/semi/immersive layout sizes, hero cover and placement, row size/title options, artwork visibility and static/animated/custom mode. Video pauses when inactive; static art remains as fallback. |
| Right-click cards | Home, Library and Applications list expose the retained context actions. Keyboard Escape/Up/Down/Enter handling added to the inherited menu. |
| Visible Library management | Manage button opens the existing Add game/app/cloud/folder, hidden entries and collection controls. View/Select still works. |
| Not installed filter | Explicit non-cloud entries with installed=false; unknown installation is not treated as confirmed uninstalled. |
| Spotify mini-player | Retained Spotify mini-player in bottom navigation; immersive Home chip also retained. Helper-tray Spotify remains available. |
| Applications list mode | app_list_view and app_list_cols apply, retaining collection/source filtering, details and right-click. |
| Size information | Actual available on-disk/download-size metadata shown in game information; no invented size for unavailable data. |
| Controller shortcuts | X pins/unpins the selected Home/Library entry; triggers cycle source tabs. Availability filters remain in the navigable toolbar. Smooth pane scrolling also applies to Library and returns to its toolbar. |
| Appearance/language | Card scales/wide grids, store badges, date/clock/battery, accent and bar-background preferences apply. Crete-only labels translated in English/French/Spanish. Unsupported original navigation-chrome/background-effect controls hidden for Crete while saved preferences remain usable in other themes. |

Moonlight policy was explicitly revised by Muzi during this pass: verified host game→local→GFN→install. MoonDeck/Bosgame takes priority even with a local copy. Local remains an explicit menu action; a running local game retains Resume. Host installation uses the read-only SSH inventory and exact Steam IDs; backend rechecks before streaming. Native stream/output/controller acceptance remains separate from mocked browser checks.

Checks and exact build/install identities are recorded in the canonical CreteOS journal/build ledger. The native tests cannot establish physical controller behavior, output switching, actual codec support, suspend/resume or reboot acceptance.

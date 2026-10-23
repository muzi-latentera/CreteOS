# Bosgame Lutris integration

Current as of 23 October 2026. Five games are registered in Steam and discovered in CreteOS: Mouse: P.I. For Hire, Metal Gear Solid V: The Phantom Pain, Mortal Shell II, Stellar Blade, and LEGO Batman: Legacy of the Dark Knight. The user authorized launch tests only for the first three. Stellar Blade and LEGO must not be launched during this task.

CreteOS uses stable `lutris:bosgame:<id>` library identities, the actual registered non-Steam shortcut for launching, and separate public Store matches for metadata. Covers, hero artwork, descriptions and trailer/review entries are available. This does not grant Steam ownership, achievements or cloud saves. Standard non-Steam Steam entries support custom artwork but do not become full Steam Store records with imported descriptions.

Launch route: CreteOS → Moonlight/MoonDeckStream → paired Buddy → Steam 64-bit shortcut game ID → isolated D-Bus session → the existing Lutris runner and prefix. The raw 32-bit shortcut ID remains the window/artwork identity. UMU changes the game process's Steam IDs to `default`, so the host cover and window watcher must follow its bounded, same-owner process ancestry to find the exact shortcut ID. Generic Wine classes and game titles are not accepted as identity.

Playtime is explicitly labeled as Lutris playtime and imported from its cumulative hours, with refresh after exit. Historical Lutris totals can include installer time and cannot be retrospectively certified as actual gameplay. New live test deltas and last-played changes are recorded separately in the dated journal. Do not double-count those totals as another native session timer.

Stellar Blade now points to the installed `~/Games/Stellar Blade/SB.exe`. LEGO remains pointed at its installer while unpacking; preflight blocks that entry rather than launching setup through Moonlight. Retargeting its existing Lutris entry after installation preserves its identity. New entries need shortcut registration with Steam closed; refresh alone does not manufacture a shortcut.

Use `windows/scripts/setup-lutris-shortcuts.py` only while Steam is closed. It backs up VDF, keeps IDs stable, and preserves artwork/custom fields. `decorate-lutris-shortcuts.py` obtains exact-title public Store artwork with per-file backup/hash manifests. All five have portrait covers, headers and heroes. Mortal Shell's optional icon/logo URLs returned 404; do not claim every optional asset exists. `export-lutris-settings.py` exports a verified host settings archive without game files, prefixes, saves or credentials. The recovery guide and latest private-backup locator identify the combined verified archive once refreshed.

## Historical implementation and original audit

The following dated entries describe their state at the time. Earlier read-only/no-launch statements are not current deployment claims.

## Subsequent authorized implementation — 22:33 BST

The opening section is the earlier audit. User subsequently authorized implementation, Mouse/MGS V tests, local G1 build, guide and backup updates; then asked to let the ongoing install finish before live game launches. Source now includes namespaced cached Lutris library entries, fresh executable/installer preflight, registered shortcut IDs independent of metadata products, public title metadata/artwork, launcher-recorded playtime and post-exit cache refresh. Six inventory, nineteen host lifecycle,248frontend and129native tests passed. Build/install/live acceptance pending at this entry.

Host setup backed up the live Lutris DB and Steam shortcuts, closed idle Steam normally, and registered three runner-preserving isolated-D-Bus shortcuts using stable Lutris IDs. The separate bus avoids forwarding a stream launch into the user's physical-desktop Lutris installer. Mortal Shell shortcut registration does not execute its installer; native/UI preflight blocks that target. Retargeting the existing Lutris entry later preserves its identity and can enable it after Refresh host without regenerating IDs. New Lutris entries need host shortcut registration with Steam closed; they can still be discovered as unconfigured entries first. No game has been launched by this implementation task yet.

Private backup refresh is required after the final install and must include `lutris_host_library.json`, the exact executable, host Lutris DB/config/Steam-shortcut snapshot and host setup/export helpers. Game downloads, Wine prefixes and saves are outside that settings backup. Current prior ZIP predates this host change and must not be presented as covering the completed integration.

## Observed host state

Native Lutris `/usr/bin/lutris`, installed Python package version **0.5.22**. Database `~/.local/share/lutris/pga.db`; game configs are under `~/.local/share/lutris/games` (not the historical config-directory path). Sunshine headless and MoonDeck Buddy services were active. There is a 13-byte Steam shortcuts file, with no substantial shortcut inventory.

| Lutris ID | Entry | Installation/launch evidence |
| --- | --- | --- |
| 1 | Mouse: P.I. For Hire | Installed flag set; configured MOUSE.exe exists and configured Wine prefix exists |
| 2 | Metal Gear Solid V: The Phantom Pain | Installed flag set; configured mgsvtpp.exe exists and configured Wine prefix exists |
| 3 | Mortal Shell II | Installed flag set, but configured executable is the downloaded installer's setup.exe, not a verified installed game executable |

All three use Wine and the `proton-cachyos-slr` runner configuration. An installed flag alone is not sufficient to declare a game launchable. A limited executable search under the expected Mortal Shell II game folder found only a redistributable executable; this does not establish a complete installation or identify a game entry point. Verify installation and the actual game executable before adding its launch route. Do not automatically run setup.exe as if it were the game.

Database playtime values were present: approximately0.6595h,0.8669h and0.5975h respectively. The **installed** Lutris game.py updates playtime using timer.duration /3600, confirming hours rather than minutes. These are launcher-recorded totals, not independently verified gameplay; the third entry's installer target is a concrete reason not to present that total as confirmed game time. CLI/process-termination history can affect accounting too. Preserve provenance and avoid adding a separately measured session onto an already refreshed Lutris cumulative total.

## Why the entries do not appear now

CreteOS's `steam_inventory.py` reads installed Steam app manifests only. Rust `HostGame` requires `steamAppId` and name, and revalidates Steam installation before a MoonDeck launch. The frontend host snapshot likewise accepts numeric Steam IDs, and host routing uses `app.source === "steam"` with that ID. Host discovery currently enriches existing game routing; it is not a general Lutris library import. A normal Lutris installation therefore cannot appear automatically just because SSH and Moonlight work.

The model already has generic app entries, artwork/title matching and playtime fields, so this is an integration gap rather than a new streaming-system requirement. Source references: `windows/src-tauri/src/steam_inventory.py`, `streaming.rs`, `moondeck.rs`, `steam_artwork.rs`; `windows/src/crete/hostSnapshot.ts`, `useStreaming.ts`, `streaming.ts`.

## Proposed implementation

1. **Discover installed Lutris entries read-only.** Return host identity, Lutris ID/slug/name, executable-validation state, runner, last-played and launcher-recorded playtime. Retain an unknown/error distinction for missing/unreadable database/config; do not replace a cached library with empty data on SSH failure. Use stable namespaced identities, for example host+Lutris ID, rather than treating local IDs1/2/3 as Steam product IDs.
2. **Separate launch identity from metadata identity.** A Lutris ID and a non-Steam Steam shortcut ID are not a Steam Store product ID. Match exact game/edition for covers, hero art, description, genre and other available public metadata. Keep user corrections and avoid silently merging distinct copies. Do not imply ownership, Steam achievements or cloud saves merely from a metadata match.
3. **Evaluate Steam non-Steam shortcuts as the launch bridge.** Lutris has an official shortcut generator using `lutris:rungameid/<id>` and its configured runner/prefix. Buddy1.10.0 documents non-Steam shortcut process-state support. This can reuse MoonDeckStream and the existing streaming desktop, display profile, Wi-Fi hold and return machinery, while still requiring CreteOS's new inventory/library route. Back up shortcut/config files and coordinate any required Steam reload while idle. No shortcuts were created during this audit.
4. **Validate exact process/window lifecycle.** The shortcut must run on Bosgame's dedicated Sway streaming desktop, not forward to a physical-desktop Lutris GUI instance. Confirm Buddy tracks Wine's actual game lifetime rather than only a short-lived launcher/installer. Verify window matching for the shortcut's custom ID, real game readiness, B/Escape cancel, normal quit/return, reconnect and no lingering tracked app. Direct Lutris launching via a dedicated host-session adapter is an alternative if the shortcut bridge cannot meet these requirements; do not assume a background SSH process alone proves a successful stream.
5. **Import/refresh host playtime with provenance.** Convert Lutris's hours to app minutes/seconds and use its cumulative total as one source, refreshed after game exit. Avoid double counting across native session counters or other devices. If launcher bookkeeping is inadequate, track confirmed host-game runtime separately with a clear label. Test normal exit and failure/disconnect cases before claiming accurate playtime.
6. **Verify metadata limits.** Covers/descriptions/genres can use the existing public title-matching path where an unambiguous record exists. Provider-owned achievements, saves and account statistics need their own supported source; a disc/non-Steam copy does not automatically gain Steam account progress. This audit did not claim complete metadata availability for all three.

Do not re-pair Moonlight/Buddy, alter working host display scripts, change Wine prefixes/runners or modify Windows display/Wi-Fi settings merely to add catalogue entries. Keep the Onimusha/Expedition cleanup lesson: leave no test game/session blocking the user.

## Verification needed before calling it done

Confirm Mortal Shell II installation and correct executable first. Test Mouse or MGS V as one controlled bridge case; then verify discovery, metadata identity, cold launch, actual game handoff, normal quit/artwork return, playtime delta and same-game reconnect. Extend to the remaining launchable entries. Test host unavailable/error caching and new Lutris installs. Build locally on the G1, record exact source/artifact/install identities and preserve profiles. Any actual deployed/profile/host restore-input change must update the setup guide and refresh the private backup with the new integration inputs included. No runtime change occurred in this audit, so the existing private ZIP remains the same dated snapshot.

## Sources and retained evidence

- [Lutris official Steam shortcut integration](https://github.com/lutris/lutris/blob/master/lutris/util/steam/shortcut.py): IDs, rungameid route, runner-preserving shortcuts and artwork.
- [MoonDeck Buddy releases](https://github.com/FrogTheFrog/moondeck-buddy/releases): v1.10.0 non-Steam start-directory/custom-AppId process tracking.
- [Lutris game lifecycle source](https://github.com/lutris/lutris/blob/master/lutris/game.py): process lifecycle and cumulative playtime. Installed0.5.22 source was also inspected for the actual unit conversion.
- Original task workspace: `work/lutris-audit-result.txt`, `work/lutris-detail-audit-result.txt`, `work/lutris-install-check-result.txt`. These contain private host paths; they were retained locally rather than added to source. Audit scripts were sent over SSH stdin and not installed on Bosgame.

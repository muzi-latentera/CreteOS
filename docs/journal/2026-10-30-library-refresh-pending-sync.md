# 30 October 2026 — manual Steam / GeForce NOW refresh (pending vault sync)

User requested both refresh faults fixed, with no games launched. Branch: codex/g1-smart-wifi. Old installed executable remains source aca475c4d87fda4ac55ac5f7206aecb52ee6d59d / SHA256 8338C036964A8A8200785DD719BD2F2B4722B8E52C3E0651418BEBB70D18EC46 until an explicit receipt below.

Changes: manual refresh forces NVIDIA fetch rather than accepting daily cache; background failed attempts retry after five minutes instead of suppressing a whole day. Steam/NVIDIA errors are surfaced by source; saved lists are retained. Steam rejects incomplete API payloads and reports cache-write errors. Remote waits and manual local scans are bounded, late scan results ignored, retry supported. Steam and NVIDIA refresh in parallel before the installed-library scan. Optional other-account refresh is bounded separately. Added native --refresh-library-data for real cache verification without UI/game/auth launch.

Verification so far: 19 focused frontend tests passed; TypeScript passed; 135 native release tests passed. Initial full frontend run: 253/256 passed, three failures during concurrent compilation (two five-second timeouts; following cloud navigation assertion). Lower-worker-count rerun passed all 256 tests / 56 files. NVIDIA raw GraphQL request returned a valid first page; JSON-enveloped GraphQL is invalid for this endpoint and was not adopted.

No install or new private backup yet. Existing snapshot is still the previous deployed build. Next: finish tests, commit, build locally on OXPG1, install with rollback, exercise native refresh, record actual errors/counts and inspect resulting entries without launching a game; refresh private backup and source bundle and push branch. Vault environment is unavailable; this record awaits canonical sync.

## Actual deployment and live check

Built on OXPG1 00:00:58–00:04:23 GMT, c7723e99386474cb461b21a97e1635fc1f25f3c8. Embedded-asset verification passed. Installed 00:05:00 GMT, SHA256 9E22BFB1A00A5BDF54D8427B5CBCF293E3B9DA0FED9D6AF1768B90804AA65C6F. Rollback: native CreteOS-Bootstrap/app-updates/Library-refresh-20261030-000450. All six checked profiles unchanged; no Windows display settings modified. Build/install/cache/service receipts are in docs/device-records/2026-10-30-library-refresh-*.json.

Native refresh actually ran without launching games: NVIDIA succeeded, 6,214 entries, Phantom Blade Zero present with Steam variant 107018152 and parent abebad03-46e0-4ab3-ae49-f7360621a4f0. Steam renewal returned Steam auth EResult 15; prior 148-game owned cache retained. Thus native exit 2 is expected partial failure, not build failure. Steam reauthorization must be done by Muzi in CreteOS before fresh ownership/Phantom Blade inclusion can be verified. Valve documents 15 as access denied: https://partner.steamgames.com/doc/api/steam_api#EResult . It does not establish why the old credential was rejected.

Windows computer-use inspection confirmed the running native CreteOS home screen and visible Could not refresh: Steam / saved-list/reconnect guidance; no launch button was used. Full rerun: 256 frontend tests passed; 135 native release tests passed; TypeScript and production/embedded-asset checks passed. The prior timeouts did not reproduce at two workers.

Private snapshot is marked stale pending replacement for the new executable/catalogue. Old verified archive preserved. Backup completion and Git synchronization follow below. Canonical vault still unavailable; pending sync.

## Private backup completed

New verified snapshot: CreteOS-G1-profile-backup-20261030-000613.zip. SHA256 759e762921763b779006fa0ae0900295b5229522a44839c0bb33d8f4653406a9; 715618516 archive bytes; 1344 payload files / 737064806 uncompressed bytes. All per-file source/staging/ZIP hashes verified, SQLite integrity ok, no optional input missing. Locator updated and stale flag cleared by successful backup. Previous archive preserved. Snapshot includes actual installed executable and latest NVIDIA cache, while Steam ownership remains the saved pre-reauthorization list. Documentation-only final identity updates postdate this snapshot. Archive is local/private, not an off-device or public-Git backup.

## Reauthorization disproved the initial diagnosis

User reconnected Steam. Native readback at 00:11:27: 149 games, Phantom Blade Zero 4115450 present, cache updated 00:10:50. But a fresh maintenance refresh at 00:11:48 still returned Steam auth EResult 15. Therefore the initial conclusion that the saved token was simply rejected/needed reconnecting was incomplete. Existing CreteOS requests WebBrowser credentials then uses GenerateAccessTokenForApp for renewal; that RPC rejects browser tokens. Primary implementation reference: node-steam-session LoginSession.ts (refreshAccessToken restriction and browser getWebCookies flow), https://github.com/DoctorMcKay/node-steam-session/blob/master/src/LoginSession.ts .

Follow-up patch uses finalizelogin + explicitly allowed HTTPS Steam community settoken transfer, reads an account-matched access token from the response cookie only in memory, keeps app-token renewal for non-web tokens, bounds requests/response size and sanitizes errors. Regression tests cover audience selection, transfer destination restrictions and account/type validation. Native tests/local build and real repeated renewal checks pending. No new install yet. Backup locator marked stale for refreshed Steam ownership pending replacement. No games launched; no authentication dialog automated.

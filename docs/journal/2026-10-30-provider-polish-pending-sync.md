# 30 October — startup toast, Phantom Blade route, Ground Zeroes cover and dismiss controls

Pending canonical vault sync. Branch codex/g1-smart-wifi. User reports boot GFN catalogue failure, Phantom Blade only opening the client, stale padded Ground Zeroes cover despite refresh, and default-style Close controls. No intended game launch requested.

Findings and changes: startup called refreshLibraryFromSources without background=true and therefore forced the NVIDIA request/toast. It now uses background cache-preserving refresh, retries catalogue on online events, and offline cache hydration does not advance online throttling. Exact failed boot network exception was not retained; Wi-Fi signal quality is not diagnosed from this toast.

NVIDIA's saved Phantom Blade record has CMS 107018152 / parent abebad03-46e0-4ab3-ae49-f7360621a4f0 / empty shortName. Native UI URL observed shortName equal to parent UUID, so native route construction now uses that fallback and retains existing store-variant matching. Added a real-record route regression. During UI inspection a tile unexpectedly launched an NVIDIA session; agent acknowledged it, used quit controls, confirmed the session ended and the client returned to the game page. No new CreteOS gameplay acceptance is claimed; do not state that zero games were launched during this follow-up.

Ground Zeroes cache points to its publisher portrait-v2 asset, a padded landscape composition. Added reviewed SteamGridDB 134459 / 600x900 portrait, visually inspected from the actual downloaded asset. Existing reviewed-cover replacement invalidates the prior automatic cache path. Custom covers keep precedence. Initial claim that empty merge-save failed to clear was incorrect on inspection; the extra cache-clear patch was removed. No cache-clear rewrite retained.

DismissButton centralizes secondary button colors/borders/font/radius and is reused for library toast, search Close, store account Close and Steam dialog icon. Other bare JSX buttons were inspected: their navigation/stream containers already provide CSS styling. Optional custom_art.json now captured by private backup tooling if present; authentication remains excluded.

Tests, build/deploy receipts and backup completion to follow. Previous installed artifact remains 56316e5 / 4F4B36C1… until explicit install evidence below. No external release or main merge requested. Canonical Crete vault unavailable.

## Pre-build verification

Native release tests: 139 passed. TypeScript passed. Broad frontend run passed 238 tests in 54 files; two unchanged test-file workers failed to start under concurrent native compilation (worker-response timeout, not assertions). Both missing files reran at one worker and passed all 18 tests. The updated catalogue refresh file additionally passed all five tests, including the new offline-to-online retry regression. No unresolved test assertion failure remains. Visual inspection of the downloaded Ground Zeroes portrait confirmed a full-height 600x900 composition. New executable is not yet installed.

## Installed build and initial live verification

Built locally on OXPG1 00:39:51–00:46:08 GMT from 5143c20c9f8dd251c265052ee1441804f0c43b82; embedded assets verified. Installed 00:46:33 GMT. SHA256 1996BB80C3164EF757253531FE076DEFA00FBA72222D09FB5D58C3B6EA6693E9. Rollback: native CreteOS-Bootstrap/app-updates/Library-refresh-20261030-004621. All six checked profiles unchanged; no Windows display setting change. Native source refresh succeeded Steam 149 / NVIDIA 6,214, gamesLaunched=false for that maintenance run (separate from the earlier acknowledged accidental NVIDIA UI launch).

Reviewed portrait installed at native CreteOS/art/grid/reviewed-v1/078c1993dcaa199add4a1b83be1a0c04.jpg, SHA256 FF5FC382FDD3B482FDEF08F7ED0E9CDAF018E4E12F5501C27F14B314BE1DC93F, 600x900. Native cache readback points to that file; UI screenshot inspection confirms full portrait on the Ground Zeroes tile. Restarted app reached Home without a forced-refresh notice. The helper tray manual Refresh Library action was clicked for final notice/dismiss verification; source refresh pending completion. No further game launch used. Actual PC reboot and new CreteOS gameplay launch not tested. Private snapshot marked stale pending replacement. Receipts under docs/device-records/2026-10-30-provider-polish-*.json.

## Final UI check

Manual helper-tray Refresh Library completed successfully. Visually inspected the resulting Library refresh complete banner: Close is now a rounded theme-colored secondary button, rather than a native white browser button. The Ground Zeroes full portrait remains visible. The patched CreteOS was not used to start a game; corrected GFN routing is verified by native route regression and comparison with NVIDIA's observed URL. No claim of a new end-to-end stream test. Private snapshot refresh now starting after successful metadata/UI checks.

## Verified recovery snapshot

CreteOS-G1-profile-backup-20261030-004912.zip: 1368 payload files / 738682985 uncompressed bytes; archive 717156791 bytes; SHA256 b22b6488ba36ce8e90c11d588e5ce1fb0d318e16ddb103a284ac82553f577e0a. Per-file source/staging/ZIP hashes and SQLite integrity passed; no optional input missing. Additional manifest readback confirms the exact installed 1996bb80… binary and FF5fc382… Ground Zeroes portrait. Previous snapshots preserved. Latest locator updated, stale marker cleared by successful backup. Snapshot contains updated optional-custom-art backup tooling; personal archives and secrets remain outside Git. Documentation-only final hash/completion edits postdate archived guide. Dated repo journal still pending canonical vault sync.

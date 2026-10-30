# 30 October — startup toast, Phantom Blade route, Ground Zeroes cover and dismiss controls

Pending canonical vault sync. Branch codex/g1-smart-wifi. User reports boot GFN catalogue failure, Phantom Blade only opening the client, stale padded Ground Zeroes cover despite refresh, and default-style Close controls. No intended game launch requested.

Findings and changes: startup called refreshLibraryFromSources without background=true and therefore forced the NVIDIA request/toast. It now uses background cache-preserving refresh, retries catalogue on online events, and offline cache hydration does not advance online throttling. Exact failed boot network exception was not retained; Wi-Fi signal quality is not diagnosed from this toast.

NVIDIA's saved Phantom Blade record has CMS 107018152 / parent abebad03-46e0-4ab3-ae49-f7360621a4f0 / empty shortName. Native UI URL observed shortName equal to parent UUID, so native route construction now uses that fallback and retains existing store-variant matching. Added a real-record route regression. During UI inspection a tile unexpectedly launched an NVIDIA session; agent acknowledged it, used quit controls, confirmed the session ended and the client returned to the game page. No new CreteOS gameplay acceptance is claimed; do not state that zero games were launched during this follow-up.

Ground Zeroes cache points to its publisher portrait-v2 asset, a padded landscape composition. Added reviewed SteamGridDB 134459 / 600x900 portrait, visually inspected from the actual downloaded asset. Existing reviewed-cover replacement invalidates the prior automatic cache path. Custom covers keep precedence. Initial claim that empty merge-save failed to clear was incorrect on inspection; the extra cache-clear patch was removed. No cache-clear rewrite retained.

DismissButton centralizes secondary button colors/borders/font/radius and is reused for library toast, search Close, store account Close and Steam dialog icon. Other bare JSX buttons were inspected: their navigation/stream containers already provide CSS styling. Optional custom_art.json now captured by private backup tooling if present; authentication remains excluded.

Tests, build/deploy receipts and backup completion to follow. Previous installed artifact remains 56316e5 / 4F4B36C1… until explicit install evidence below. No external release or main merge requested. Canonical Crete vault unavailable.

## Pre-build verification

Native release tests: 139 passed. TypeScript passed. Broad frontend run passed 238 tests in 54 files; two unchanged test-file workers failed to start under concurrent native compilation (worker-response timeout, not assertions). Both missing files reran at one worker and passed all 18 tests. The updated catalogue refresh file additionally passed all five tests, including the new offline-to-online retry regression. No unresolved test assertion failure remains. Visual inspection of the downloaded Ground Zeroes portrait confirmed a full-height 600x900 composition. New executable is not yet installed.

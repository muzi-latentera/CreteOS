# 14 November 2026: batch 2 (instant start, Steam installs, sturdier launches)

Pending vault sync. This was autonomous overnight work at the owner's request; see `docs/ROADMAP_CRETEOS_1_0.md`.

## Changes (source `46685cc`, `a0af9a3`, `07805ec`)

- **Instant start.**
  - Every successful scan saves `%LOCALAPPDATA%\CreteOS\library_snapshot.json` (930 KB). `get_cached_apps` loads it at start-up and the fresh scan replaces it.
  - Automatic Steam and Xbox owned-library updates now refresh in the background, so start-up no longer dims Home with "Refreshing library".
- **Settings.**
  - A file that fails to parse keeps every value that still fits; the bad copy is saved as `settings.json.unreadable-<pid>`.
  - A locked file is retried instead of reading as defaults.
  - Saves write a temporary file and rename it into place.
- **Steam installs.**
  - The watcher is a tested decision function, polling once a second.
  - It has no time limit while progress moves or Steam reports the download paused, and it only gives up after 60 minutes with no progress.
  - It waits ten minutes for Steam's install dialog.
  - The final commit no longer counts as uninstalled.
  - Downloads already running are re-attached at start-up (`steam_active_downloads`).
  - Uninstall waits up to ten minutes for Steam's confirmation.
- **Launches.**
  - Direct exes start in their own folder and retry elevated on error 740.
  - Every ShellExecute route reports failures (declined UAC, missing file) instead of waiting silently.
  - The Crete cover writes Steam's phase on the native artwork window, but only for starting Steam, syncing saves, updating, and waiting for Steam.
- **Input.**
  - The left stick uses hysteresis (on above 0.55, off below 0.4) and only the stronger axis counts.
  - Menu closes the quick menu it opened.
- **Manage saves.**
  - Ludusavi's config is read before any scan starts.
  - Messages name Bosgame or this PC.
  - Drive reads retry once.
  - A failed read now shows a plain reason.

## Verification

- TypeScript passed. 293 frontend and 177 Rust tests passed; new tests cover snapshot start-up, tolerant settings, the Steam watcher, stick hysteresis, the Drive time parse and Drive reasons.
- Installs:

  | Time (GMT) | SHA-256 | Source |
  |---|---|---|
  | 00:19 | `073D0A06…42B3` | `46685cc` |
  | 00:31 | `DA4D2E25…7D37` | `a0af9a3` |
  | 00:41 | `73E82030…A5F6` | |
  | 00:52 | `5F783984…F7E3` | `07805ec` |

  Rollback copies are kept as `creteos.exe.before-<stamp>`.
- On device: after a restart, Home is fully shown 3 s after launch, with no splash and no refresh panel.
- Manage saves for Mouse reads "…matches the latest backup on Bosgame".
- Google Drive still showed "Couldn't reach Google Drive".
  - Reproduced over CreteOS's own SSH route; it is intermittent.
  - The surfaced reason was a Google API `ErrorInfo`. Together with rclone's notice that the shared client "is being retired and will stop working during 2026", this matches the shared-client rate limit already noted on 13 Nov.
  - **Owner action:** create your own Google client ID. The steps are in the guide under Game save recovery.
- `creteos-saves` was redeployed on Bosgame (`0a337921…417c`).
- **One hang observed:** the first start after the 00:41 install stopped responding, with almost no CPU and missing art, while clicks were made 4 s after launch. It did not reproduce in five more starts or in the same click sequence. Cause unknown. A watchdog is on the roadmap (batch 7).

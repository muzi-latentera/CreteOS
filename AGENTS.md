# Repository instructions

<!-- BEGIN APP JOURNAL -->
## App history and session handoff

Follow [docs/APP_JOURNAL.md](docs/APP_JOURNAL.md). Before work, read this app's Obsidian current-state note, lasting rules and recent journal; check build/install records before choosing an artifact. After meaningful work, append changes/reasoning, actual verification, unresolved issues and next steps, and refresh current state. Record exact builds and device installs separately; never infer an install from a successful build. Worktrees share the canonical app journal. If the vault is unavailable, save a pending-sync repo handoff and report it.
<!-- END APP JOURNAL -->

## Keep the replacement-device record current

Lasting user instruction, 23 October 2026: future CreteOS changes must update `docs/G1_SETUP_AND_RECOVERY.md` and the dated journal as part of completion. Muzi expects to reuse this setup on another device in roughly two months. When live profiles/settings or other backup inputs change, create and verify a new timestamped private backup, preserve the previous verified copy, and update the guide and workspace `outputs/CreteOS-G1-profile-backup-latest.json`. If refresh is blocked, mark the backup stale explicitly. Documentation-only changes do not require refreshing personal payloads. Keep personal archives and secrets out of Git; record exact deployed builds separately from source and tests. This rule does not create a scheduled task. See `docs/APP_JOURNAL.md` for details.

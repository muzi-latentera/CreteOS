# CreteOS — app journal

Canonical app: **creteos**. Worktrees use the same app records and identify their own branch/commit in entries.

G1 setup, recovery and replacement-device entry point: [G1_SETUP_AND_RECOVERY.md](G1_SETUP_AND_RECOVERY.md). Keep its deployed state current alongside the dated journal; detailed older records remain historical.

## Locate the Obsidian vault

Use `CRETE_VAULT_ROOT` if configured, or the Obsidian vault named **Crete**. On Muzi's current Mac it is:

```text
/Users/muzidube/Library/CloudStorage/GoogleDrive-muzidube1@gmail.com/My Drive/Obsidian Vaults/Crete
```

Canonical records, relative to that vault:

- `Work/creteos.md` — current state, known issues and where to resume.
- `Work/creteos/journal.md` — dated work history.
- `Work/creteos/builds.md` — exact artifacts and install/deployment events.
- `Work/creteos/rules.md` — lasting decisions and rationale.
- `Work/WORKFLOW.md` — shared recording standard.

Read the current state, rules and recent journal before work. Check build/install evidence before choosing an artifact. If a record does not yet exist, report that gap and create it from the vault templates when meaningful work provides content. Never reconstruct unverified install history from a version or commit date.

After meaningful work append: dated purpose, actual changes and reasoning, branch/commit, checks actually run and results, unresolved issues, and next action. Refresh current state. Record builds separately from installs/deployments, including artifact SHA-256, version, signing identity where relevant, target and observed checks. Keep binaries outside volatile `/tmp`. Link detailed repo verification and Linear issues rather than copying a task board. Read back saved notes.

Repository instructions remain authoritative for development; the vault supplies the dated handoff and app decisions. Keep secrets out of both. If the vault is unavailable, save `docs/journal/` with **pending vault sync** and report the gap. This policy is instruction-based; it does not itself add an automatic logging hook.

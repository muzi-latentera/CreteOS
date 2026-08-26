# UPSTREAM.md — Tracking eOr upstream

## Upstream project

| Field | Value |
|---|---|
| Repository | https://github.com/keweis2/eOr |
| Baseline SHA | 3dd0ea6c65cc4fc5e0f1fd7914aef0c127c5ad16 |
| Baseline version | v2.6.0 |
| Licence | MIT |

## Branch strategy

```
keweis2/eOr (upstream)
        │
        │  git fetch upstream / merge
        ↓
main (upstream mirror)
        │
        │  PR (automated or manual)
        ↓
product (CreteOS product branch)
        │
        │  tagged release builds
        ↓
GitHub Releases (APK)
```

### Rules

- `main` — stays as close as possible to eOr `main`. No feature development here.
- `product` — all CreteOS custom code. Never commit directly to `main`.
- Upstream syncs are automated weekly (see `.github/workflows/upstream-sync.yml`).
- The automated workflow creates a PR from `main` → `product`. **Do not merge without CI passing.**

## Remotes

```bash
git remote -v
# origin   git@github.com:muzi-latentera/CreteOS.git
# upstream https://github.com/keweis2/eOr.git
```

## Manual upstream sync

```bash
# Fetch latest eOr
git fetch upstream

# Update our main mirror
git checkout main
git merge upstream/main --no-edit
git push origin main

# Create PR into product (or merge manually after review)
git checkout product
git merge main --no-edit
# resolve any conflicts — see "Conflict guidance" below
git push origin product
```

## Files we deliberately modify from upstream

These files have intentional differences from eOr. Each upstream merge must re-verify them.

| File | Change | Risk |
|---|---|---|
| `app/build.gradle.kts` | `applicationId = "io.latent.creteos"`, BuildConfig fields (`UPDATE_REPO`, `UPSTREAM_REPO`, `EOR_BASE_VERSION`) | Low — isolated block |
| `domain/usecase/LaunchGameUseCase.kt` | Inject `UnifiedLaunchCoordinator`, call `tryLaunch(game) ?: emulatorLauncher.launch(game)` | Low — constructor + 1 line |
| `domain/usecase/CheckForUpdateUseCase.kt` | `REPO = BuildConfig.UPDATE_REPO` instead of hardcoded `keweis2/eOr` | Low — companion object |
| `app/src/main/res/values/strings.xml` | `app_name = "CreteOS"` | None |
| `app/src/main/AndroidManifest.xml` | `<queries>` block extended with provider package names | Low — additive only |

## Conflict guidance

**`LaunchGameUseCase.kt`** — most likely conflict point. If upstream changes the constructor:

1. Keep the `unifiedLaunchCoordinator: UnifiedLaunchCoordinator` parameter.
2. Keep the `tryLaunch(game) ?: emulatorLauncher.launch(game)` call.
3. Adopt any other upstream changes verbatim.

**`build.gradle.kts`** — if upstream changes the `defaultConfig` block:

1. Keep `applicationId = "io.latent.creteos"`.
2. Keep the BuildConfig fields block at the end of `defaultConfig`.
3. Adopt all other upstream changes.

**`CheckForUpdateUseCase.kt`** — if upstream changes the companion object:

1. Keep `private val REPO = BuildConfig.UPDATE_REPO`.
2. Keep URL fields as `val` (not `const val`) since they reference a non-const.

## Upstream improvements to contribute back

These changes would benefit all eOr users and should be proposed as upstream PRs:

- Generic external-display launch abstraction (generalise `DualScreenManager`)
- Direct GameHub Lite / WinNative launch support

If an upstream PR is accepted, remove the fork-specific patch on next sync.

# Windows fork validation

Local verification on macOS, 2026-10-13, branch `codex/windows-liftoff`:

- Production TypeScript check and Vite build passed. Vite reports large inherited JavaScript chunks; startup performance on hardware is unmeasured.
- Vitest: **37 files, 175 tests passed**. Includes inherited launch/install/metadata checks and Crete filters, real-history handling, defaults and RSS parsing/link boundaries.
- Playwright Chromium: **27 tests passed**. Includes Home empty/played states; three-destination bumper navigation; Library and details at 1280×800, 1280×720, 800×600 and 1920×1080; keyboard selection and return from details; availability filters, inline search, source picker, sorting; controller news activation; management dropdown and one native launch command; inherited account/onboarding, helper and storage-scroll checks. Tauri commands are mocked.
- AnyFSE generator: checked valid executable paths, GPD administrator flag, deduplication, preservation of unrelated startup apps/settings, existing-output refusal and invalid-path rejection. No live AnyFSE configuration was changed.
- `npm audit`: zero reported vulnerabilities after updating the inherited source-map-js dependency override in the initial fork.
- Windows-target Rust check on macOS: resource paths resolved, then compilation stopped because the Windows `llvm-rc` compiler is unavailable. Native packaging/backend tests are delegated to the Windows workflow; no local native-build claim is made.

The inherited browser suite had failures on untouched upstream involving navigation timing, modal selectors/layers and surface assumptions. Checks were reconciled with actual behavior. Account/detail modal input is suppressed briefly on opening; controller checks allow that guard to finish before testing cancellation.

## Visual evidence and platform differences

The checked-in [Home](creteos-home.png), [Library](creteos-library.png) and [details](creteos-details.png) previews show the actual React interface with mocked native commands and labeled **TEST ARTWORK**. They do not demonstrate native discovery, an installed Windows app or target-device performance. The supplied Android Library/local/cloud details references are under `../../reference/android-ui/`; Home additionally follows active Android `ShellTabs.kt`.

Crete preserves LiftOff's install/management handlers, original optional themes and launcher/store backend. Applications remain accessible through Sources. The Crete presentation shows actual available metadata; it does not fabricate Android-only sources, GameNative, achievements or HowLongToBeat values. The public news rail hides itself when the feed is unavailable.

## Native and device acceptance

The GitHub Windows workflow is being run on the fork branch to produce an unsigned NSIS installer and execute backend tests. Record its completed result and exact artifact in the app build ledger; a passing mocked frontend suite is not native verification.

Install the artifact on the target GPD and complete the [device acceptance checklist](WINDOWS_SETUP.md#device-acceptance-checks). Native discovery, credentials, focus handoff, Xbox/AnyFSE transitions, suspend/resume and GPD TDP controls require that hardware validation. The exact GPD model, utility and executable path remain needed for the final device profile.

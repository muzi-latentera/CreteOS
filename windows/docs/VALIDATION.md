# Windows fork validation

## 2026-10-16 rapid-input follow-up

The GPD user rejected 8a44b8b: moving the browser sampler off animation frames did not improve physical rapid taps. An independent Windows XInput capture then recorded all ten Right and ten Left press/release cycles, plus nine presses of each bumper, with CreteOS in the foreground. This does not measure which edges WebView2 received.

A separate regression reproduces a navigation-state bug: four commands before React commits advance only one card. Home/Library selection and picker state now advance synchronously, and each mounted view has its own navigation delegate selected from the current tab reference. The regression covers batched keyboard directions and actual mapped gamepad edges across multiple bumper changes followed immediately by directional input. Mocked checks do not establish physical responsiveness; repeat the rapid-tap sequence on the installed candidate before accepting this fix.

## Initial verification

Local verification on macOS, 2026-10-13, branch `codex/windows-liftoff`:

- Production TypeScript check and Vite build passed. Vite reports large inherited JavaScript chunks; startup performance on hardware is unmeasured.
- Vitest: **37 files, 175 tests passed**. Includes inherited launch/install/metadata checks and Crete filters, real-history handling, defaults and RSS parsing/link boundaries.
- Playwright Chromium: **28 tests passed**. Includes Home empty/played states; three-destination bumper navigation; Library and details at 1280×800, 1280×720, 800×600 and 1920×1080; keyboard selection and return from details; availability filters, inline search, source picker, sorting; controller news activation; management dropdown and one native launch command; Applications collection filtering and return to games; inherited account/onboarding, helper and storage-scroll checks. Tauri commands are mocked.
- AnyFSE generator: checked valid executable paths, GPD administrator flag, deduplication, preservation of unrelated startup apps/settings, existing-output refusal and invalid-path rejection. No live AnyFSE configuration was changed.
- `npm audit`: zero reported vulnerabilities after updating the inherited source-map-js dependency override in the initial fork.
- Windows-target Rust check on macOS: resource paths resolved, then compilation stopped because the Windows `llvm-rc` compiler is unavailable. Native packaging/backend tests are delegated to the Windows workflow; no local native-build claim is made.

The inherited browser suite had failures on untouched upstream involving navigation timing, modal selectors/layers and surface assumptions. Checks were reconciled with actual behavior. Account/detail modal input is suppressed briefly on opening; controller checks allow that guard to finish before testing cancellation.

## Visual evidence and platform differences

The checked-in [Home](creteos-home.png), [Library](creteos-library.png) and [details](creteos-details.png) previews show the actual React interface with mocked native commands and labeled **TEST ARTWORK**. They do not demonstrate native discovery, an installed Windows app or target-device performance. The supplied Android Library/local/cloud details references are under `../../reference/android-ui/`; Home additionally follows active Android `ShellTabs.kt`.

Crete preserves LiftOff's install/management handlers, original optional themes and launcher/store backend. Applications remain accessible through Sources. The Crete presentation shows actual available metadata; it does not fabricate Android-only sources, GameNative, achievements or HowLongToBeat values. The public news rail hides itself when the feed is unavailable.

## Native and device acceptance

The GitHub Windows workflow passed the frontend checks on the Windows runner. Its first packaging attempt exposed a CLI argument error; the command was corrected to `npm run tauri -- build -- --locked` to forward the Cargo flag. Native installer/backend checks are being rerun on the fork branch. Record its completed result and exact artifact in the app build ledger; a passing mocked frontend suite is not native verification.

Install the artifact on the target GPD and complete the [device acceptance checklist](WINDOWS_SETUP.md#device-acceptance-checks). Native discovery, credentials, focus handoff, Xbox/AnyFSE transitions, suspend/resume and GPD TDP controls require that hardware validation. The exact GPD model, utility and executable path remain needed for the final device profile.

## 2026-10-14 candidate
Candidate e660532 passed Windows CI run 38482164616 but has not been installed. Subsequent working-tree changes add the complete 6,140-title NVIDIA GB catalogue, store-specific native CMS routes, consistent Settings pills and startup cover/handoff. The cached 208-entry Steam/Xbox account audit has 119 matches (113 ready-to-play, six requiring cloud installation), 75 unmatched and 14 found only on another store; original Metro licences do not inherit Redux links. Seven catalogue/streaming unit checks and the production frontend build passed. The full local suite reached 190 passing unit tests before final startup-cover refinements. New native window/topmost changes still require Windows CI and observed GPD acceptance; stable window detection is not proof of an interactive game scene. Actual Xbox account API probes returned Resonance 661 minutes/20 unlocked and Requiem 922 minutes/20 unlocked; total-achievement denominators were unavailable.

## 2026-10-14 streaming launch follow-up
Muzi confirmed controller dropdowns work in installed e5408f7, but GFN Start game failed and Luna used Edge. A direct Windows probe using the exact quoted NVIDIA desktop-shortcut format and CEF working directory reached CMS107016426; Muzi then confirmed CONTROL Resonant actually launched. This is observed direct-command evidence, not an installed CreteOS-code test. The saved catalogue labels this title READY_TO_PLAY (not INSTALL_TO_PLAY); shortName is empty and CMS-only launching is accepted by the native client. Follow-up code preserves raw Windows route quoting/cwd, hands off to native GFN client/streamer windows, makes current-settings action attempt Play, routes Luna to its validated installed Chrome PWA/profile and uses an artwork-only spinner. Local production/typecheck and192unit checks passed; three targeted mocked browser tests passed, including spinner/current-settings direct Play and controller menus. New native build/device acceptance still pending. Chromium's launch URL override is documented at https://chromium.googlesource.com/chromium/src/+/refs/heads/main/chrome/common/chrome_switches.h . No browser credentials copied.

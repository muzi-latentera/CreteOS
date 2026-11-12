# 12 November 2026 — XREAL audio and Moonlight handoff

Pending vault sync.

Muzi confirmed the glasses are XREAL and that Moonlight showed a split between the game and loading artwork. Windows retained an XREAL 1S endpoint identity. Added it to the automatic output policy ahead of Philips FTV when both are active. The normal input selection and per-launch overrides still apply. The header shortens the built-in output and microphone to G1 and XREAL endpoints to X.

The two recent Moonlight attempts used 1920 × 1200. The first disconnected before the game was ready. The later attempt reached the handoff. The host watcher accepted any matching game container, and the presenter could leave its wallpaper behind a game still changing geometry. The watcher now waits for the presenter to retire its own wallpaper after output coverage, with a short fallback for visible windowed games.

An isolated 1920 × 1200 Sway compositor on Bosgame verified the fixture window filled the output and the per-stream wallpaper exited before the presenter signalled readiness. Thirty-three Python checks, twenty-two frontend tests, TypeScript checking and the isolated Rust audio-selection test passed. XREAL was disconnected, and no actual Moonlight game stream or fresh-device installation was performed with the new build.

Installed on the G1 at 2026-11-12T00:08:16.6044554+00:00 from `c6f61807d8ef3d991c677d30b3492dd134195ead`, executable SHA-256 `4911D7437C8E75496AA8D6DE0C75A0953E365F71D9488BEC001CD6B512FE60C0`. Bundled assets and 8 existing profiles passed verification. Executable rollback is at `C:\Users\Muzi\AppData\Local\CreteOS-Bootstrap\app-updates\Xreal-handoff-20261112-000813`.

Verified private archive `CreteOS-G1-profile-backup-20261112-000828.zip`, SHA-256 `04ad4e7020a2e33469af0c12666732a310e7088624e1d4a630f9f138dbfd3732`, contains 1635 manifested payloads including the deployed executable. The preceding `CreteOS-G1-profile-backup-20261111-234020.zip` and its Bosgame Lutris reference are retained. Replacement package `CreteOS-Recovery-20261112-XrealHandoff` restores the profile and executable. Use the account, hardware and SSH pairing steps in the setup guide.

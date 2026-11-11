# 11 November 2026 — Launch controller navigation

Pending vault sync.

Launch dialogs use the rendered rows for keyboard and D-pad navigation. The G1 and Bosgame connection fields navigate side to side on the same row and vertically when stacked. Refresh host and Open Moonlight navigate side to side when aligned. The primary footer action wraps to the first available field. Returning from the provider menu restores the full split Play outline and suppresses the individual button outline.

Eleven focused frontend tests, TypeScript checking and twelve browser checks passed. They covered navigation, narrow layouts, 4K and controller focus restoration. No live game or replacement-device installation was performed for this UI change.

Installed on the G1 at 2026-11-11T23:39:58.5988112+00:00 from `0b99ff29a9035a4f03b387e7cdf62d9ad181536e`, executable SHA-256 `9CBF927685B2F0913EF456A89CD8479CDE94A124F8F763A9122597DFABC201FE`. Bundled assets and 8 existing profiles passed verification. Executable rollback is at `C:\Users\Muzi\AppData\Local\CreteOS-Bootstrap\app-updates\Launch-navigation-20261111-233953`.

Verified private archive `CreteOS-G1-profile-backup-20261111-234020.zip`, SHA-256 `63262a14795085bc837281214f36cae81b06a1e8f5d51f37df50e60e6c0d2f32`, contains 1634 manifested payloads including the deployed executable. The preceding `CreteOS-G1-profile-backup-20261111-223034.zip` and its Bosgame Lutris reference are retained. Replacement package `CreteOS-Recovery-20261111-LaunchNavigation` restores the profile and executable. Use the account, hardware and SSH pairing steps in the setup guide.

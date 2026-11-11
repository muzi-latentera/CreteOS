# 11 November 2026 — Launch summaries and Moonlight layout

Pending vault sync.

The header and Sound selectors share one device inventory. Headers resolve the Windows defaults and per-launch overrides, refresh on endpoint notifications, and shorten familiar device names while preserving full names in tooltips. Moonlight shows Bosgame, the display/bitrate summary and audio from left to right. At narrow widths the layout reflows. G1 and Bosgame share the Home connection section. Moonlight puts Sound before Display preset.

Eleven focused frontend tests, TypeScript checking and ten browser checks passed. They covered defaults, overrides, header layout, controller navigation and handheld through 4K viewports. No gameplay or fresh-device installation was performed for this layout change.

Installed on the G1 at 2026-11-11T22:30:13.9188767+00:00 from `9dcc92740dae66ec9cfef5fd76234795c2d1d7d2`, executable SHA-256 `6472E3D47E0C1FD516A629FC8CBE6E8ABC4C89DAB7B4EB69E9727A62CE856D56`. Bundled assets and 8 existing profiles passed verification. Executable rollback is at `C:\Users\Muzi\AppData\Local\CreteOS-Bootstrap\app-updates\Launch-header-20261111-223010`.

Verified private archive `CreteOS-G1-profile-backup-20261111-223034.zip`, SHA-256 `a097e0869e11baa621f7e9a27169f1b9231af903bc81d0c8e7d1c47384fe5a4d`, contains 1618 manifested payloads, including the deployed executable and muted UI settings. The preceding `CreteOS-G1-profile-backup-20261111-212613.zip` and its Bosgame Lutris reference are retained. Replacement package `CreteOS-Recovery-20261111-LaunchHeader` restores the profile and executable. Use the account, hardware and SSH pairing steps in the setup guide.

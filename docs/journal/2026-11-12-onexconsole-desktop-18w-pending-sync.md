# 12 November 2026 — OneXConsole desktop power on external power

Pending vault sync.

At 2026-11-12T20:40:15.417824+00:00 changed the native Desktop process preset from 12 W to 18 W on external power. It retains 6 W on battery, the desktop process binding and the existing all-chargers policy. Compared all other preset rows before and after the update. The SQLite check and the archived preset checks passed. A pre-change SQLite snapshot is at `C:\Users\Muzi\Documents\Codex\2026-10-17\set-up-chrome-os-as-shown\work\onex-audit\before-ac-desktop-18w-20261112-204015.db`. A normal task relaunch left the prior OneXConsole session running. The 18 W value is verified in the saved and archived preset, and live application awaits a full OneXConsole restart or next sign-in.

The same dock-aware Moonlight executable, Wi-Fi helpers and Bosgame network reference were verified against the previous backup. Automatic Moonlight selects E433 on the G1 and Ethernet on Bosgame while docked, as verified in the prior live test. No executable rebuild was required. On a replacement, review the OneXConsole vendor profile and supported TDP before applying its database.

Verified private archive `CreteOS-G1-profile-backup-20261112-204027.zip`, SHA-256 `5c26099db07430a53785d70ab29021470ddbd26bd5f007e067079e11e1c6eb12`, contains 1645 manifested payloads. The preceding `CreteOS-G1-profile-backup-20261112-203252.zip` is retained. The replacement pack is `CreteOS-Recovery-20261112-Desktop18W`.

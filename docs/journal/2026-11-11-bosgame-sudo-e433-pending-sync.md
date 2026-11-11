# 11 November 2026 — Bosgame administrator access and E433 route trial

Muzi ran the one-time setup on Bosgame for permanent passwordless sudo under the existing `muzidube` account. A fresh key-authenticated SSH connection over Bosgame's Ethernet address ran `sudo -n -k /usr/bin/id -u` and returned `0`. `visudo -c -q` succeeded. The active sudoers entry is `/etc/sudoers.d/zz-creteos-muzidube`, owned by `root:root` with mode `0440`. This policy applies to the Bosgame account, including SSH sessions. It is outside the G1 profile archive and should be set up explicitly on a replacement host if desired. A replacement handheld still requires its own authorized SSH key.

Bosgame's Wi-Fi is connected to `Hyperoptic Fibre E433` at 5260 MHz. The E433 2.4 GHz profile has auto-connect disabled. Bosgame's default LAN route continues to use Ethernet. The G1 was on Hyperoptic Extra during the bounded trial. Only replies from Bosgame's Wi-Fi address to the G1 were redirected to `wlan0` for two minutes. Replies from the Ethernet address continued to use `eno1`. Both Sunshine `/serverinfo` requests returned HTTP 200. A TCP-handshake capture and a separate two-packet UDP echo capture confirmed egress on the corresponding physical interfaces.

The trial removed its rule and temporary route table on expiry. A fresh Ethernet SSH connection confirmed both reply addresses again use `eno1`, with passwordless sudo still active. An automatic E433 launch rule and game-stream comparison are still pending. No game was launched and the Delta installer was left running.

Canonical vault sync pending.

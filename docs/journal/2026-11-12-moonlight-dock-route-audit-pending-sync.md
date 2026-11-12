# 12 November 2026 — Dock-aware Moonlight routing and session review

Pending vault sync.

### Deployment and route behaviour

The installed Smart Lid dock IDs supply the physical dock state to the Wi-Fi helper and launch dialog. Automatic uses E433 while docked and Extra while undocked. Wired G1 routing still takes priority and explicit dropdown choices remain available. The launch helper can use a second scan and waits up to ten seconds for the requested SSID, within a 36-second outer allowance. When home Wi-Fi cannot be confirmed, the launcher offers Keep current connection instead of silently proceeding on the other SSID. Steam preflight waits for a transient process-read failure and still requires readable evidence before a normal Steam shutdown. The Moonlight artwork budget accounts for these checks and gives launch or Steam readiness time to settle.

Bosgame has both Ethernet and Wi-Fi addresses on one LAN. The G1 had learned the Wi-Fi MAC for the Ethernet address before the change. The Bosgame hostname resolved to both addresses. In the intermediate live build, Buddy used Ethernet, but Moonlight discovered the Wi-Fi address even with an Ethernet literal on its CLI. Its video still left via Ethernet under the source-port policy rule. The final home lease pins Buddy HTTP, WebSocket and shutdown to the selected address, adds Buddy replies to the same route table and rejects alternate Sunshine addresses for the G1 during the lease. The filter's peer entry expires if its heartbeat stops. The persistent Bosgame ARP policy is scoped to the two physical adapters.

Installed G1 build `87c82f1fa7b6fb01baefe359b333fc490e79271d` at 2026-11-12T20:29:37.3307443+00:00, SHA-256 `1B332D7858DEC19F4BF80742D1138C9526B933377D9D690B19CBF6B3A4005BA9`. Executable and previous G1 preferences are at `C:\Users\Muzi\AppData\Local\CreteOS-Bootstrap\app-updates\Moonlight-dock-route-20261112-202934`. Wi-Fi helper rollback is `C:\Users\Muzi\AppData\Local\CreteOS-Bootstrap\WiFiSmart\backups\MoonlightDock-20261112-201834`. The Bosgame ARP policy is `/etc/sysctl.d/91-creteos-bosgame-arp.conf` and the private host reference includes its exact bytes and installation receipt. To roll it back on Bosgame, remove only that file and restore arp_ignore and arp_announce to 0 for eno1 and wlan0. Returning to an earlier G1 build alone leaves that scoped host setting installed.

The final real docked Phantom Blade Zero test ran from 20:30:49 to 20:32:25 GMT and reached the title menu without loading a save. The G1 joined E433, the Moonlight RTSP response used Bosgame's Ethernet address, and Bosgame packet capture confirmed both Buddy directions and all 42 sampled outbound video packets on eno1 with that same address. Sunshine used HEVC VAAPI on the RX 9070 XT at 3840 x 2160, 60 fps, with 80 Mbps requested and approximately 63 Mbps configured for video. Moonlight measured 58.31 incoming fps, 11 ms average network latency, 4.7 ms average host processing and 1.71 percent network frame loss. All logged video drop events fell within seconds 92 to 94, shortly before exit. Buddy accepted the end request, CreteOS released the hero artwork and both temporary host rules and filter were removed. Separate Ethernet and Wi-Fi lease checks each accepted the chosen address, blocked the other address and verified cleanup. The physical undocked G1 was not streamed in this turn.

### Thirty retained client sessions

The newest 30 unique retained Moonlight logs with video and quality summaries cover 2–12 November GMT, including the two validation streams. The logs have gaps. Bosgame's accessible systemd journal overlaps 17 connections. The old client logs identify the IP endpoint and generally lack the physical interface, selected modal value, or a matching packet capture. Counts describe retained logs.

22 of 30 had under 1 percent network frame loss and eight reached at least 1 percent. Twenty had average RTT of 5 ms or less. Two exceeded 20 ms, with 38.45 percent loss and 572 ms on 4 November, and 61.60 percent loss and 141 ms on 12 November. The selected client endpoints were Ethernet in 21 sessions and Wi-Fi in nine, including legitimate overrides. Three sessions logged a nonzero client termination error and two had a separate quit acknowledgement timeout. These errors do not establish one common fault.

CreteOS separately recorded 19 Moonlight errors in the same date window, including seven Steam safety, busy or desktop checks, four SSH timeouts, two sign-in prompts, two Buddy reachability errors, three readiness timeouts and one disconnect before game readiness. These records can overlap the sessions in the table. The final route verification fixes the reproduced address mismatch. More time and clearer diagnostics mitigate transient launch failures. SSH outages, account prompts and Wi-Fi loss can still occur.

Focus and white text cannot be reconstructed from network logs. Four browser tests with simulated controller input on the current source passed in the installed Edge browser. They cover Moonlight's dropdown choices and bottom actions, a visible focus highlight and saving only on confirmation, the unified Play outline on back, and the GFN footer's directional navigation. Sixteen focused UI unit tests, 19 PowerShell policy/parser checks and ten host Steam preflight tests passed. Physical controller focus still needs a handheld check.

| GMT | Negotiated video | Bosgame IP type | Network frame loss | Average RTT | Client error |
| --- | --- | --- | ---: | ---: | --- |
| 11-12 20:30 | 3840x2160x60 | Ethernet | 1.71% | 11 ms | — |
| 11-12 20:23 | 3840x2160x60 | Wi-Fi | 0.0% | 15 ms | — |
| 11-12 19:26 | 3840x2160x60 | Ethernet | 61.6% | 141 ms | Quit acknowledgement timed out |
| 11-12 02:15 | 2560x1600x90 | Ethernet | 0.0% | 1 ms | — |
| 11-12 01:37 | 2560x1600x90 | Ethernet | 0.0% | 1 ms | — |
| 11-12 00:15 | 1920x1200x90 | Ethernet | 0.12% | 1 ms | — |
| 11-11 23:55 | 1920x1200x90 | Ethernet | 0.13% | 2 ms | — |
| 11-11 23:54 | 1920x1200x90 | Wi-Fi | 1.17% | 1 ms | — |
| 11-11 22:12 | 2560x1600x90 | Wi-Fi | 0.26% | 2 ms | — |
| 11-11 21:39 | 3840x2160x60 | Wi-Fi | 1.18% | 2 ms | — |
| 11-04 21:38 | 2560x1600x120 | Wi-Fi | 0.45% | 1 ms | — |
| 11-04 19:33 | 3840x2160x60 | Ethernet | 0.0% | 8 ms | — |
| 11-04 17:00 | 3840x2160x60 | Wi-Fi | 0.0% | 4 ms | — |
| 11-04 15:22 | 3840x2160x60 | Wi-Fi | 0.05% | 8 ms | — |
| 11-04 14:46 | 3840x2160x60 | Wi-Fi | 0.05% | 5 ms | — |
| 11-04 13:13 | 3840x2160x60 | Ethernet | 0.0% | 8 ms | — |
| 11-04 13:02 | 3840x2160x60 | Ethernet | 1.56% | 10 ms | Connection terminated: 10055, Error code: 00002747 |
| 11-04 11:26 | 3840x2160x60 | Ethernet | 38.45% | 572 ms | — |
| 11-03 23:28 | 2560x1600x120 | Ethernet | 0.45% | 2 ms | — |
| 11-02 23:44 | 1920x1200x90 | Ethernet | 0.03% | 2 ms | — |
| 11-02 20:59 | 2560x1600x120 | Ethernet | 0.12% | 1 ms | — |
| 11-02 20:44 | 2560x1600x120 | Ethernet | 0.92% | 2 ms | Quit acknowledgement timed out |
| 11-02 18:10 | 2560x1600x120 | Wi-Fi | 0.17% | 6 ms | Connection terminated: -1, Error code: -1 |
| 11-02 18:01 | 2560x1600x120 | Ethernet | 15.61% | 1 ms | — |
| 11-02 17:11 | 2560x1600x120 | Ethernet | 0.08% | 1 ms | Connection terminated: -1, Error code: -1 |
| 11-02 15:13 | 2560x1600x90 | Ethernet | 0.0% | 1 ms | — |
| 11-02 14:58 | 2560x1600x90 | Ethernet | 6.97% | 16 ms | — |
| 11-02 14:41 | 2560x1600x120 | Ethernet | 0.01% | 1 ms | — |
| 11-02 14:34 | 1920x1080x60 | Ethernet | 0.78% | 1 ms | — |
| 11-02 14:14 | 2560x1600x120 | Ethernet | 0.0% | 1 ms | — |

Verified private archive `CreteOS-G1-profile-backup-20261112-203252.zip`, SHA-256 `a34575b627a831bf2518b7ea44a1301ec18c7026211177123efe194da6f557f0`, contains 1644 manifested payloads. The preceding `CreteOS-G1-profile-backup-20261112-200052.zip` is retained. Replacement package `CreteOS-Recovery-20261112-MoonlightDockRoute` contains the current G1 build, helper, profiles and source.

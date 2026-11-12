# 12 November 2026 — Moonlight Wi-Fi scan and Bosgame path comparison

Pending vault sync.

The old helper treated the asynchronous Windows scan as complete after a fixed two seconds. The two affected launches logged unavailable before any Wi-Fi association attempt. A direct Windows connection also rejected E433 while it was missing from scan results. A fresh scan showed it absent at 1.54 seconds and present at 2.25 seconds. The helper now holds the scan handle, polls the requested profile for up to four seconds, and records scan API failures and no-match results separately. Existing wired, manual and active-stream rules still apply.

Installed at 2026-11-12T19:59:41.8180426+00:00, SHA-256 `DAA053C4BD80E944ACD9029F200D02B6320F803F4FCBF36C33CE5078024D9908`, rollback `C:\Users\Muzi\AppData\Local\CreteOS-Bootstrap\WiFiSmart\backups\20261112-195939\WiFiSmart.ps1`. The monitor restarted and 19 policy/parser checks passed. A native Windows PowerShell PrepareSession switched from Extra to E433 even though the initial scan did not list E433, then restored Extra. The installed G1 executable was the prior build.

With Bosgame's requested output interface explicitly forced and verified, each row below used a 30-second 65 Mbps UDP payload and parallel RTT probes. These were synthetic local-network tests, without a game session or Sunshine video processing. The ingress column records the interface where Bosgame received the RTT probes.

| G1 SSID | Bosgame transmit interface | Delivered | Missing | Median / p95 RTT | Host ingress |
| --- | --- | ---: | ---: | ---: | --- |
| Hyperoptic_Extra | Ethernet | 65.0 Mbps | 0.0% | 8.81 / 93.93 ms | eno1 148 |
| Hyperoptic_Extra | 5 GHz Wi-Fi | 3.31 Mbps | 94.904% | 3951.69 / 7021.45 ms | wlan0 141 |
| Hyperoptic Fibre E433 | Ethernet | 65.0 Mbps | 0.0% | 8.07 / 17.62 ms | eno1 149 |
| Hyperoptic Fibre E433 | 5 GHz Wi-Fi | 64.99 Mbps | 0.015% | 5.38 / 18.68 ms | wlan0 148 |

Bosgame answers for both interfaces on one subnet. The G1 was observed mapping the Ethernet address to the Wi-Fi MAC, and the Bosgame hostname resolved to both interface addresses. Buddy's preflight calls use the hostname before the media route is applied. This establishes route ambiguity, but the original failed Buddy call has no per-request transport details. Steam's third-attempt error came from an unreadable process during the preflight scan; the logs did not identify the process, and the normal shutdown was not requested in that attempt. No Buddy, Steam, router or host addressing settings were changed.

Phantom Blade Zero has approximately 11 GB of Steam shader data on disk. Steam retained the previous RADV cache manifest and briefly began fetching a newer one at 19:27:12, after the first network losses. It suspended the job at 19:27:15, resumed it after exit and committed it at 19:31:33. A smaller pipeline depot finished by 19:31:37. The older manifest and cached files establish reuse across sessions; a cache update can still fetch a large replacement.

Verified private archive `CreteOS-G1-profile-backup-20261112-200052.zip`, SHA-256 `020fd9bdaa3337840566004ac9058a18463305dbc6ba5882b2bf70fa770216f6`, contains 1643 manifested payloads. The preceding `CreteOS-G1-profile-backup-20261112-194514.zip` is retained. The replacement package is `CreteOS-Recovery-20261112-MoonlightWifiScan`.

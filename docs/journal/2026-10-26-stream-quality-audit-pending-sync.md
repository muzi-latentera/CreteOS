# Stream quality audit — pending vault sync

## Streaming log audit — 26 October 2026, 22:23 GMT

Read-only audit of the G1's five available Moonlight logs for today, Windows WLAN events, WiFiSmart events, CreteOS lifecycle log, and Bosgame Sunshine/Buddy/NetworkManager journals. No games were launched and no runtime, Wi-Fi, driver, profile, display, service or network settings were changed. Therefore no new personal-data backup is required by this documentation-only audit; the existing private archive remains its dated snapshot. Raw private evidence remains under workspace `work/stream-audit-20261026-*`.

| G1 log start (GMT) | Outcome / duration | Network frame loss | Mean network latency | Mean incoming FPS |
| --- | --- | --- | --- | --- |
| 14:58:02 | Normal quit after about 3m23s | 0.00% | 1 ms | 119.74 |
| 15:10:49 | Normal quit after about 2m39s | 0.00% | 6 ms | 120.04 |
| 22:08:39 | Unexpected control disconnect / error -1 after about 64s | 0.00% | 421 ms (variance 786 ms) | 108.18 |
| 22:10:09 | Unexpected control disconnect / error -1 after about 72s | 92.72% | 70 ms (variance 63 ms) | 9.05 |
| 22:12:00 | Normal quit after about 5m24s | 0.00% | 91 ms (variance 152 ms) | 111.95 |

All five client logs requested 80,000 kbps, 2560x1600/120 FPS. Sunshine reported an encoder bitrate of 62,988,000 bits/s in these sessions; do not conflate that logged encoder target with the client request. Jitter loss in the final session was 0.13%, compared with 0.02% in each afternoon session. Final-session host processing averaged 8.8 ms and client decode 0.80 ms; the two failures had normal host processing averages of 4.4/6.2 ms and client decode averages of 0.61/2.56 ms. The concrete fault shown is network delay/loss and control-channel disconnects, not a demonstrated game launch or decoding crash. Zero network frame loss in the first failed attempt does not establish a healthy link: its round-trip delay was very high.

Bosgame Buddy received the evening game's AppID 3321460, reported it Running, and CreteOS recorded host-window-visible and handoff-stable in all three attempts. The latter attempts reconnected to the still-running game. The final session recorded game exit and return to CreteOS; Buddy end request timed out at 22:17:25, then CreteOS requested closure of its owned Moonlight client and released its return cover. Sunshine confirms app exit/cleanup at 22:17:28–30. Client control-stream 10004 messages after normal Quit events are shutdown context, not additional failed launches. Do not label normal Buddy WebSocket peer closure as a server crash without further evidence.

Windows recorded an Extra driver disconnect/reassociation at 22:08:26, before the first evening log began, and no further WLAN disconnection events during the three streams. WiFiSmart recorded Gaming session holds/protect-stream on Hyperoptic Extra, with no network switch during those sessions. The final post-exit Fibre selection was unavailable and retained Extra. This does not support blaming automatic mid-stream switching. Current G1 signal was 90%, 5 GHz, 574 Mbps negotiated receive/transmit; historical signal strength and physical distance during the attempts are not logged, so router distance cannot be confirmed or ruled out. Current signal is not a retrospective measurement. Interference, congestion/backhaul and coverage remain possible causes requiring a controlled comparison.

Bosgame currently has Ethernet .150 and Wi-Fi .133 both active; unlike the earlier dated audit, .150 is now a live advertised stream endpoint. All today's inspected Moonlight negotiation records used .150, and current route to G1 .149 uses Ethernet. NetworkManager changed the default route preference between wired and wireless before/after the failures and during the final session, but that alone does not prove the LAN stream route changed; the more-specific LAN route currently selects Ethernet. No host link-down/carrier loss was established during these streams. Sunshine/Buddy remained active. Do not apply the old '.150 stale' conclusion to this current state.

Conclusion: afternoon streams were healthy; the evening failures and the recovered session show materially worse network behavior. Distance from the router is plausible but unproven. No configuration fix is claimed. A same-position lower-bitrate or proximity comparison would distinguish conditions better than current RSSI alone. The prior 24 October 20 Mbps diagnostic remains historical evidence, not a test performed today. Canonical Crete vault is unavailable; this audit is pending vault sync.

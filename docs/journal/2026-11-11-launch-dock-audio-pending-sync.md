# 11 November 2026 — Clean launches and Philips dock audio

Pending vault sync.

The recent Bosgame attempts were Phantom Blade Zero. Sunshine opened video and audio at 20:50:23. Moonlight disconnected at 20:51:35 and Steam still tracked the game until 20:51:59. Steam recorded a fresh client at 20:52:34 before the successful retry. The retained G1 logs did not preserve the reported Moonlight code, so the code and specific cause of the failed attempt remain unconfirmed.

Steam preflight sends a normal shutdown to an idle Bosgame client on either desktop and waits for exit before the launch proceeds. It checks game processes and Sunshine RTSP and UDP activity. The live idle Bosgame test returned `closed` without starting a game. GeForce NOW routes use the same idle-client shutdown and verified settings warm-up, preserving active and unconfirmed sessions. The installed G1 build closed the test NVIDIA home client and left no NVIDIA client or streamer processes. Windows local Steam continues to reuse its client.

Philips FTV is selected when the HDMI audio endpoint is active. When it disappears, the watcher restores the G1 speakers if available. An explicit output or microphone choice is scoped to the app/game session. The local display selector enumerates Windows modes for the current monitor and applies a temporary resolution/refresh choice. It restores the prior mode if the same monitor still has the selected mode at exit. Local Wi-Fi Automatic uses ordinary rules; a manual selection uses the existing launch network lease. The Philips endpoint identity was verified in Windows, but docked playback was not physically tested.

The local and streaming menu tests, launch-overlay tests, TypeScript check, Rust check and 10 host preflight tests passed. The installed G1 UI showed the ASOBI launch dialog, its actual 2560 × 1600 at 144 Hz current mode, supported alternative modes, and output and microphone controls. No display mode was applied during that UI check, and no end-to-end game stream or new-device setup was run.

Installed on the G1 at 2026-11-11T21:15:45.2464100+00:00 from `945af6036968b03c13b1efab028b81fc3712cc4c`, executable SHA-256 `FE551B8C3A7D6A767C1ABCE1A7FC52B8DAC02A5B688A65477E4A5E1C16288782`. Bundled assets and 8 existing profiles passed verification. Executable rollback is at `C:\Users\Muzi\AppData\Local\CreteOS-Bootstrap\app-updates\Launch-dock-20261111-211539`.

Verified private archive `CreteOS-G1-profile-backup-20261111-211628.zip`, SHA-256 `8afda5da64d231a3c4e08bfe58bf01d5a6a560b4965949c4b2894f1e61d9c602`, contains 1615 manifested payloads including the installed executable and silent UI profile. The preceding `CreteOS-G1-profile-backup-20261111-203859.zip` and its Bosgame Lutris reference are retained. Replacement package `CreteOS-Recovery-20261111-LaunchAndDockAudio` restores the profile and executable. Use the existing account, hardware and SSH pairing checks in the setup guide.

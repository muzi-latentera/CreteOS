# 11 November 2026 — dock controller reconnection and live hibernation

## Controller

At 15:33:38 ASOBI detected two Xbox 360-class pads. Windows' present-device history identifies one controller directly under a G1 root hub and a second behind the dock's Genesys USB hub. ASOBI logged a disconnect at 15:34:11.665 and reconnect at 15:34:12.940. The dock-connected Windows controller's last-arrival record was 15:34:12, matching the reconnect. It disappeared again at 15:35:01 alongside the dock AV adapter; the ASIX interface disappeared at 15:35:02. ASOBI began logging home-menu navigation changes at 15:35:04.551. During the subsequent stream, Steam Input found no controller after 91 polls, ASOBI fell back to SDL and logged successful built-in Xbox input starting at 15:35:14.

The evidence identifies a dock-side controller re-enumeration and proves that the remaining controller worked after undocking. The logs do not establish whether ASOBI lost Windows foreground focus or why the dock-side device initially disconnected. No controller, ASOBI, Steam Input or display configuration was changed.

## Power

At 14:54:59 the live helper received Lid Closed and started its timer. The dock began enumerating at 14:55:17.868, about 18.8 seconds after lid close, and cancelled the timer. Both dock devices were present at 14:55:19.157. The dock disappeared at 14:55:38.555 with the lid still closed. This started a fresh timer and the helper requested Windows hibernation at 14:56:08.570, 30.01 seconds later. Kernel-Power recorded application-requested hibernation at 14:56:13 and resume at 14:56:18, both with state S4. This is physical acceptance of live dock cancellation, undock countdown and hibernation.

At 15:18:39 the observer exited after lid or dock state remained unknown around resume, and the supervisor restored the original Balanced plan at 15:18:42. The worker error does not distinguish which signal stayed unknown. At 15:32:55, Windows recorded another S4 hibernate with reason Button or Lid and resumed at 15:32:57. The worker logged no timer for this later event. Current readback shows Balanced active and the Smart Lid task Ready with no observer running. The 30-second policy is therefore inactive until the helper starts again. A resume-recovery fix remains pending. This was a read-only diagnosis and did not change private backup inputs. Canonical vault sync pending.

# 11 November 2026 — ASOBI app artwork and TV-dock inspection

## ASOBI in Apps

Asobi: Remote Play is Steam app 4505440, stored as `steam://rungameid/4505440` and classified as an app by the native CreteOS profile. The initial Home pin was removed at Muzi's request and the previously disabled Home shelf was restored. The standalone entry appears in Apps under its Steam title. Its custom cover uses the publisher's 300 × 450 Steam library capsule (JPEG SHA-256 `d3bb101aed2ff7e20ca9f550251e3e9329db28b7f9bf94f6c7f3c6f93fe1ea2d`), read from the local Steam cache. CreteOS's Apps grid uses the custom cover. The Open action launches the standalone app directly, where the user can select the console. Per-title PlayStation Remote Play and Cloud entries remain separate.

Verified the cover, category, no ASOBI pin and restored shelf preference in native Windows AppData. Restarted CreteOS normally and observed its main window. ASOBI was already open and its previous stream had disconnected. No new PS5 connection or game was started. The installed executable remains source `f1c818dd8b781dee383044a97e3d10911c37d1ae`, SHA-256 `6109CB5787816DFFE3F7602540A7E2631B531AEFEC9E1697802C06FBBBA60F08`. No app build was needed. Local profile rollback copies are in `work/asobi-artwork-rollback-20261111-111458` and `work/asobi-pin-rollback-20261111-110806`. [Steam listing](https://steamcommunity.com/app/4505440).

## Smart lid and wall-mounted TV — Stage 1

Read-only inspection on Windows 11 Home build 26300, Balanced power plan. Windows reports hibernate, S0 Low Power Idle with networking, and Fast Startup available. S3 is unavailable. The current lid close action is Hibernate on battery and mains. The power button action is Do nothing on both. ACPI Lid (`ACPI\PNP0C0D`) and ACPI Power Button are present and healthy in Device Manager. No power settings, scheduled tasks, firmware or display configuration were changed for this project.

Windows exposes separate [lid state notifications](https://learn.microsoft.com/en-us/windows/win32/power/power-setting-guids) and [active display paths](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-querydisplayconfig). The current snapshot saw one active panel (YHB02P24), Wi-Fi as the only up network adapter, and a generic USB hub (VID 1A86, PID 8091). That hub is not yet identified as the TV dock. The USB stick was connected during this inventory. No lid transition or TV-dock attachment was captured, so event delivery, stable dock identity, and behaviour with the TV off remain unverified. The next diagnostic is to compare USB device identity and active display paths through plug, unplug, and TV-off transitions and capture the lid signal before designing a hibernation timer.

## Recovery

Verified private snapshot `CreteOS-G1-profile-backup-20261111-111506.zip` contains 1569 manifested files and the ASOBI cover. SHA-256 `50e0bd78b8ed1d8589bee27cb3eca43ce219c24e021145eb203e7dc9eea109f1`. Verified the archive hash, cover, pin state and matching installed executable independently. The previous `CreteOS-G1-profile-backup-20261104-222223.zip` is retained.

Open `D:\START-HERE.txt` on the SanDisk and follow its pointer to `CreteOS-Recovery-20261111`. The folder's `PACKAGE.json` and `USB-COPY-RECEIPT.json` record the exact source and verified payloads. Keep the old folders as historical recovery copies. On a replacement device, restore the CreteOS profile and install ASOBI from Steam app 4505440. Reauthorize PSN and pair the PS5 in ASOBI. The custom cover resolves after CreteOS sees the installed Steam app. Fresh Windows installation and physical dock tests remain to be done.

Canonical vault sync pending.

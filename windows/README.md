# CreteOS for Windows

A controller-first Windows launcher based on [LiftOff](https://github.com/PixelateWizard/LiftOff), with CreteOS branding, charcoal surfaces, amber navigation and red launch actions. This is a separate Windows application; the Android launcher at the repository root remains independent.

LiftOff supplies game discovery, library management, controller navigation, account integrations and Windows game handoff. CreteOS supplies Android-style Home, Library and game details, the official logo, visual defaults, application identity, isolated local data and Windows-specific release selection. [Upstream provenance and licensing](UPSTREAM.md) are retained with the source and installer resources.

## Start in Xbox Mode

The initial integration uses [AnyFSE](https://github.com/ashpynov/AnyFSE) as the Windows home app. AnyFSE starts `creteos.exe` and the selected GPD utility. TDP stays under that utility's control. Stock AnyFSE appears as **AnyFSE** in Windows' home-app selector; this fork does not register a separate CreteOS home-app package yet.

Follow [Windows and GPD setup](docs/WINDOWS_SETUP.md). Installing CreteOS alone does not change the Windows shell or enable startup into Xbox Mode.

![CreteOS Windows home preview](docs/creteos-home.png)

![CreteOS Windows Library preview](docs/creteos-library.png)

![CreteOS Windows game details preview](docs/creteos-details.png)

Previews from the running frontend with mocked native commands and clearly labeled test artwork. Layout references are the active Android source and the user-supplied screenshots in `../reference/android-ui/`. [Validation record](docs/VALIDATION.md).

## Play using streaming services

The main **Play** action prefers an installed game on the configured Moonlight host, then an installed local copy, then an exact game link from the Android-curated GeForce NOW table. Moonlight is purple, GeForce NOW green, and native play red. The compact arrow menu retains local play/install and the streaming alternatives. Host Steam IDs come from read-only SSH inventory; generic Desktop/Steam host apps do not prove a game is installed. MoonDeckStream uses the exact Steam ID and requires separate MoonDeck Buddy pairing.

Host apps, installed Steam IDs and Buddy status are saved per host in the WebView’s local storage (`crete.host-snapshots.v1`). A shared background refresh runs at most once every 24 hours, including across app restarts; failed attempts also throttle automatic retries. Opening game details reads saved availability immediately after local client/display detection, without waiting for the network. Failed refreshes retain the last known data. **Refresh host** forces a retry. The stream launch still validates the host, app and game live, so a saved entry is not a guarantee that the host is reachable.

Without a network connection, installed local games become the default and no host queries run. An unreachable host can still appear as the saved Moonlight default when Windows reports an active network; a failed stream offers **Play locally** for an installed copy. Moonlight needs a route to the host, which can be a local network without internet; GeForce NOW requires internet and NVIDIA determines ownership/subscription/current availability. Local store offline-mode requirements still apply.

Moonlight settings follow the active display: GPD 1080p/120fps, XREAL 1200p/90fps, or 4K/60fps TV, all initially 80Mbps. Resolution, frame rate, bitrate and codec remain configurable and saved per display when streaming starts. Neither streaming client is bundled. Browser tests mock native commands; actual streaming, display switching and physical controller acceptance require device checks.

Both stream dialogs support controller A to open an option list, Up/Down to move through fields/actions, Left/Right to cycle a focused Moonlight setting and B to return. Focus scrolls into view. NVIDIA-created desktop shortcuts provide the preferred direct GFN launch when the game ID and store variant match; otherwise the exact native game route is used. The GFN quality dialog can apply resolution/FPS/bitrate while the NVIDIA client is closed, retaining a private settings backup. NVIDIA controls codec and membership/device limits; XREAL uses 1200p/60fps for GFN. Opening a game with current settings changes no quality settings.

Xbox activity IDs are resolved through Microsoft-account title details rather than treated as Store product IDs. Details show available Microsoft-account played hours, unlocked achievements and Gamerscore; unavailable totals remain unknown. Exact-title Steam metadata can supply missing trailers/screenshots. Preferred review videos appear before store media in this order: Whitelight, Skill Up, Joseph Anderson, Noah Caldwell-Gervais, then gameranx and Jake Baldino.

## Develop and build

Use Windows, Node 24, Rust stable, the MSVC C++ build tools, Windows SDK and WebView2. Run from this directory:

```powershell
npm ci
$env:SGDB_API_KEY = "" # Optional SteamGridDB artwork key.
npm run tauri -- dev
```

Create an installer with `npm run tauri -- build -- --locked`. The unsigned installer is written under `src-tauri/target/release/bundle/`. Supply a SteamGridDB key through the build environment if artwork lookup is needed; no key is bundled in this repository.

```powershell
npm run build
npm test
npx playwright install chromium
npm run test:e2e
cargo test --locked --manifest-path src-tauri/Cargo.toml
```

The GitHub workflow `.github/workflows/windows.yml` performs these checks and produces a Windows installer artifact. It does not publish releases. Future Windows releases must use `windows-v<version>` tags so the updater ignores Android releases. Automatic update checks default to off.

## Verification limits

The browser suite mocks Tauri commands and verifies the frontend, controller navigation and dialogs. It cannot verify Windows discovery, installer behavior, FSE transitions, credential storage or GPD hardware controls. Those require a Windows build and the device checklist in the setup guide. This initial fork is not yet validated on a GPD device.

See [privacy and services](PRIVACY.md) and the preserved [upstream README](UPSTREAM-README.md) for inherited features.

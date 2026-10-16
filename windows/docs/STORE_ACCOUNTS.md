# Epic Games and GOG owned libraries

Connect each account in **Settings → Library & accounts**, or during onboarding.
The sign-in window belongs to the store; CreteOS receives an authorization code,
not a password. Complete any two-factor authentication there. Closing the dialog
cancels sign-in. A successful import enables that source and not-installed games.

Both accounts import owned games, including uninstalled games. Epic combines the
Windows asset feed with every cursor page of the library service, then requests
catalogue metadata. GOG reads every product page. DLC, mods and non-game products
are excluded where the provider identifies them. No purchase or installation is
performed by connecting an account.

Epic's `applications` category also appears on ordinary games. It must not be
used to exclude games; DLC/addon/mod categories and parent-game relationships
remain the relevant exclusions. A live import exposed this distinction after
successful authentication initially saved an empty library.

Installed scans still work without account sign-in. GOG registry product IDs and
Epic app IDs join installed copies to ownership records; existing installed IDs,
executables, pins and history are retained. Different stores remain separate
entitlements. A token-free `gog_library_ids.json` mapping keeps GOG entries stable
through installation, uninstall and account disconnect; it can travel with the
portable preferences, unlike the account library caches. Uninstalled entries open the Epic library or their GOG Galaxy page
for installation; CreteOS does not report them as playable native installs.

Refresh runs in the background on startup/reconnection, at most once per day for
a successful cache. **Refresh library** in the account dialog forces a retry.
Local scans never contact account APIs. Failed authorization, pagination or
metadata requests retain the previous complete library. Offline startup uses
that saved library. Disconnect removes the saved account library and token,
while installed-game discovery remains available. Factory reset clears these
credentials as well.

Refresh tokens use separate Windows Credential Manager entries under
`io.latent.creteos.windows.epic` and `io.latent.creteos.windows.gog`. Access tokens
remain in native memory. The local `epic_library.json` and `gog_library.json`
contain account ID/name, update time and game identities/art URLs, with no tokens
or passwords. Do not export them in the G1 portable profile: connect the accounts
on the new device instead. Sign-in uses a separate incognito WebView without main
window IPC permissions. Provider errors omit URLs/bodies to avoid exposing codes
or query-string tokens.

On Windows the private sign-in browser uses a separate per-store WebView2 data
directory under `store-signin/`, because the main window's custom browser flags
cannot share an environment with a differently configured WebView. Construction
runs on a worker thread; the sign-in window is owned by the main window and shown
only after it is ready. `logs/store-accounts.log` records bounded, generic lifecycle
phases and Windows HRESULTs only, without URLs, response bodies, codes or tokens.
The private browser directories and diagnostic log are excluded from G1 exports.

GeForce NOW checks the exact title/known alias **and the owning store variant**;
an Epic/GOG title never borrows a Steam edition. Availability still depends on
NVIDIA, membership and ownership. Moonlight supports a host app advertised under
the exact game title. MoonDeck Buddy's installed inventory is Steam-specific;
owning an Epic/GOG game does not invent a Steam ID or prove a remote installation.
Add the game as a named Sunshine app to use that existing route. Metadata may use
an exact-title informational match without changing the launch identity.

## Protocol references and verification

Original implementation against the public desktop-client flows documented by:

- [Legendary Epic API](https://github.com/derrod/legendary/blob/master/legendary/api/egs.py)
- [Legendary library combination](https://github.com/derrod/legendary/blob/master/legendary/core.py)
- [Heroic gogdl authorization](https://github.com/Heroic-Games-Launcher/heroic-gogdl/blob/main/gogdl/auth.py)
- [Minigalaxy GOG pagination](https://github.com/sharkwouter/minigalaxy/blob/master/minigalaxy/api.py)

The shipped OAuth application identifiers are public desktop-client identifiers,
not user credentials. These interoperability endpoints can change independently
of CreteOS. Native tests cover pagination, failed/looping pages, DLC exclusion,
store identity and safe callback origins; frontend/browser checks use mocked
commands. They do not establish a successful live sign-in, actual account counts,
Credential Manager behavior or physical controller operation. Record those
separately on the installed Windows build.

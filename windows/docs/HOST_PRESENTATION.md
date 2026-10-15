# Artwork on the Moonlight host

Direct MoonDeck Steam launches can mirror the handheld's cached static hero on
the host's dedicated Sunshine display. The Windows launch passes the game's
identity and reads the existing custom/default hero cache; it does not download
new art during launching. The same message list is shared by both renderers.
Returning artwork has no messages.

The opt-in capability is restricted to an SSH-configured host with the dedicated
`/run/user/<uid>/sway-sunshine.sock` compositor and active `HEADLESS-1` output.
It needs Python 3, Pillow, PyGObject/GTK 3, Cairo and swaybg. No physical desktop
is selected as a fallback. Unsupported hosts keep the ordinary client cover and
Buddy lifecycle; the diagnostic phase is `host-artwork-unavailable`.

`host_presentation.py` is staged through the existing pinned SSH connection into
`~/.local/share/creteos-host/<session>/`. An exclusive lock prevents presentations
from competing. A per-session swaybg child holds the centre/cropped hero with
the same dark gradient. A transparent, unfocusable GTK window draws startup
messages. The exact game's container becomes fullscreen and the messages hide
once its window exists. This is window readiness, not proof of an interactive
game menu or received video frame.

Only Steam's main library container is temporarily moved to Sway's scratchpad.
Its workspace, floating geometry and fullscreen state are journaled before the
move and restored on completion. Game windows and Steam dialogs are not hidden.
If Steam logs a launch waiting for interaction and a real dialog appears, the
host restores the library and asks the handheld cover to reveal Moonlight.
Thirty seconds without progress also fails open. Steam's `ShowInterstitials`
step can include asynchronous UI preparation, so its log line alone does not
prove a dialog is visible. No dialog is automatically accepted.

After the game window closes, the host retains the hero without messages while
the handheld returns to CreteOS. Buddy still owns game/stream shutdown; normal
client closure is followed by an explicit artwork finish request. Observer lease
expiry also restores the host (30 seconds normally, 60 after game disappearance).
Only the helper's own wallpaper child is stopped. Generated full-size images
are removed; session state/source are retained for diagnosis. A later stage
recovers journaled containers if a previous helper was interrupted. No Steam,
Sunshine, network, TDP or fan configuration is changed.

Automated checks cover exact game/library/dialog isolation and prompt fallback.
A synthetic GTK window test on the dedicated host can check startup → game →
return artwork and restoration. Real Moonlight launch/quit and frame-level Steam
flash acceptance still require the GPD user test; mocked checks do not prove it.

"""Per-stream artwork on the dedicated Sunshine Sway compositor only.

No game/Steam process is stopped. Only the main Steam library container is
temporarily transparent; it stays mapped so Steam's launch UI can run. Dialogs
remain accessible. A lease and explicit finish restore the library and stop
only our own wallpaper/message processes.
"""
import base64
import json
import os
from pathlib import Path
import re
import shlex
import signal
import socket
import struct
import subprocess
import sys
import time

ROOT = Path.home() / ".local/share/creteos-host"
SOCKET = Path("/run/user") / str(getattr(os, "getuid", lambda: -1)()) / "sway-sunshine.sock"


def ipc(kind, body=""):
    data = body.encode()
    with socket.socket(socket.AF_UNIX) as stream:
        stream.settimeout(2)
        stream.connect(str(SOCKET))
        stream.sendall(b"i3-ipc" + struct.pack("<II", len(data), kind) + data)
        def read(size):
            result = b""
            while len(result) < size:
                part = stream.recv(size - len(result))
                if not part:
                    raise OSError("Sway disconnected")
                result += part
            return result
        header = read(14)
        size, _ = struct.unpack("<II", header[6:])
        if header[:6] != b"i3-ipc" or size > 2 * 1024 * 1024:
            raise ValueError("Invalid Sway response")
        return json.loads(read(size))


def windows(node, workspace=None):
    if node.get("type") == "workspace":
        workspace = node.get("name")
    if node.get("pid"):
        yield node, workspace
    for child in node.get("nodes", []) + node.get("floating_nodes", []):
        yield from windows(child, workspace)


def game_window(node, app_id):
    props = node.get("window_properties") or {}
    if str(props.get("class", "")).lower() == "steam_app_" + app_id or node.get("app_id") == "steam_app_" + app_id:
        return True
    return process_matches(node.get("pid"), app_id)


def process_matches(pid, app_id, proc=Path("/proc")):
    """Follow only this window's same-owner ancestry, bounded to 16 processes.

    UMU replaces Steam IDs inside its container with 'default'. Its Lutris
    launcher retains the registered shortcut identity. Never match by title,
    generic Wine class, or a different process elsewhere on the machine.
    """
    wanted = {("SteamAppId=" + app_id).encode(), ("SteamGameId=" + app_id).encode(),
              ("SteamGameId=" + str((int(app_id) << 32) | 0x02000000)).encode()}
    seen = set()
    try:
        pid = int(pid)
        for _ in range(16):
            if pid <= 1 or pid in seen:
                return False
            seen.add(pid)
            process = proc / str(pid)
            values = process.joinpath("environ").read_bytes().split(b"\0")
            if wanted.intersection(values):
                return True
            # The comm field may contain spaces or parentheses.
            parent = int(process.joinpath("stat").read_text().rsplit(")", 1)[1].split()[1])
            if process.stat().st_uid != (proc / str(parent)).stat().st_uid:
                return False
            pid = parent
    except (OSError, ValueError, IndexError, TypeError):
        return False
    return False

def steam_library(node):
    props = node.get("window_properties") or {}
    return (str(props.get("class", "")).lower() == "steam"
            and props.get("title") in ("Steam", "Steam Big Picture Mode")
            and not props.get("transient_for")
            and props.get("window_type") == "normal")


def needs_attention(pending, waiting_at, now, nodes, started=None):
    # Elapsed time alone does not establish a prompt: shader preparation and
    # slow game startup can legitimately outlast this cover. Require an actual
    # visible Steam dialog and a pending interaction before exposing the host.
    if not pending or waiting_at is None:
        return False
    dialogs = [n for n, _ in nodes if n.get("visible") and
               str((n.get("window_properties") or {}).get("class", "")).lower() == "steam" and
               ((n.get("window_properties") or {}).get("transient_for") or
                (n.get("window_properties") or {}).get("window_type") == "dialog")]
    return bool(dialogs and now - waiting_at > 1)


def write_state(folder, phase):
    temporary = folder / "state.tmp"
    temporary.write_text(json.dumps({"phase": phase, "pid": os.getpid()}))
    temporary.replace(folder / "state.json")


def steam_on_other_display(environment=None, proc=Path("/proc")):
    """Check the existing Steam singleton from inside the dedicated compositor.

    Steam's X11 DISPLAY takes precedence over an inherited Wayland variable.
    Unknown/unreadable processes are not treated as proof of a mismatch.
    """
    environment = os.environ if environment is None else environment
    mismatched = False
    for process in proc.iterdir():
        if not process.name.isdigit():
            continue
        try:
            if (process / "comm").read_text().strip() != "steam":
                continue
            entries = (process / "environ").read_bytes().split(b"\0")
            values = dict(entry.decode(errors="replace").split("=", 1) for entry in entries if b"=" in entry)
        except OSError:
            continue
        for key in ("DISPLAY", "WAYLAND_DISPLAY"):
            if environment.get(key) and values.get(key):
                if environment[key] == values[key]:
                    return False
                mismatched = True
                break
    return mismatched


def restore_saved(folder):
    path = folder / "moved.json"
    if not path.exists():
        return
    saved = json.loads(path.read_text())
    libraries = {n["id"] for n, _ in windows(ipc(4)) if steam_library(n)}
    for ident, original in list(saved.items()):
        if int(ident) not in libraries:
            # Sway container IDs can be reused after a compositor restart.
            # Never apply stale geometry/workspace to a game or another app.
            saved.pop(ident)
            path.write_text(json.dumps(saved))
            continue
        if isinstance(original, dict) and original.get("mode") == "transparent":
            commands = ["opacity set 1"]
            if original["fullscreen"]:
                commands.append("fullscreen enable")
        else:
            # Recover sessions created by the older scratchpad presenter too.
            workspace, floating, rect, full = original
            commands = ["move container to workspace " + json.dumps(workspace)]
            commands += ["floating enable", f"resize set {int(rect['width'])} px {int(rect['height'])} px", f"move position {int(rect['x'])} px {int(rect['y'])} px"] if floating else ["floating disable"]
            if full:
                commands.append("fullscreen enable")
        result = ipc(0, f"[con_id={int(ident)}] " + ", ".join(commands))
        if not all(reply.get("success") for reply in result):
            raise OSError("Steam library restoration was rejected")
        saved.pop(ident)
        path.write_text(json.dumps(saved))


def conceal_library(folder, node, saved):
    """Keep the dedicated compositor's ordinary opaque Steam library mapped.

    Sway does not expose a readable per-container opacity. This dedicated
    presentation uses its normal opaque library and restores opacity to one;
    it never alters the user's physical desktop or a dialog/game container.
    Journal first so interrupted helpers can restore transparency as well as
    old scratchpad sessions. Do not unmap/minimize Steam during UI callbacks.
    """
    if node["id"] in saved:
        return
    saved[node["id"]] = {"mode": "transparent", "fullscreen": node.get("fullscreen_mode", 0)}
    (folder / "moved.json").write_text(json.dumps(saved))
    result = ipc(0, f"[con_id={node['id']}] opacity set 0, fullscreen disable")
    if not all(reply.get("success") for reply in result):
        raise OSError("Steam library transparency was rejected")


def stage(payload):
    import fcntl
    app_id, token = payload["appId"], payload["token"]
    if not re.fullmatch(r"[0-9]{1,10}", app_id) or not re.fullmatch(r"[0-9a-f-]{1,80}", token):
        raise ValueError("Invalid stream identity")
    if not SOCKET.is_socket() or not any(o.get("name") == "HEADLESS-1" and o.get("active") for o in ipc(3)):
        raise RuntimeError("Dedicated Sunshine display unavailable")
    ROOT.mkdir(parents=True, exist_ok=True, mode=0o700)
    # Never replace a presentation belonging to an ongoing stream.
    check = (ROOT / "active.lock").open("a")
    fcntl.flock(check, fcntl.LOCK_EX | fcntl.LOCK_NB)
    # Recover our exact containers if a previous helper was interrupted. The
    # exclusive lock proves no live helper still owns these presentations.
    for previous in ROOT.iterdir():
        if previous.is_dir() and re.fullmatch(r"[0-9a-f-]{1,80}", previous.name):
            restore_saved(previous)
    folder = ROOT / token
    folder.mkdir(mode=0o700)
    image = base64.b64decode(payload.get("art", ""), validate=True)
    if len(image) > 8 * 1024 * 1024:
        raise ValueError("Artwork too large")
    (folder / "art").write_bytes(image)
    resolution = payload.get("resolution", "1920x1080")
    if resolution not in ("1280x720", "1920x1080", "1920x1200", "2560x1440", "2560x1600", "3840x2160"):
        raise ValueError("Invalid output resolution")
    (folder / "config.json").write_text(json.dumps({"appId": app_id, "messages": payload["messages"], "resolution": resolution}))
    (folder / "present.py").write_text(payload["source"])
    check.close()
    command = shlex.join([sys.executable, str(folder / "present.py"), token])
    reply = ipc(0, "exec " + command)
    if not all(item.get("success") for item in reply):
        raise RuntimeError("Could not start host artwork")
    for _ in range(60):
        path = folder / "state.json"
        if path.exists():
            state = json.loads(path.read_text())
            if state["phase"] == "ready":
                return {"token": token}
            if state["phase"] == "wrong-desktop":
                return {"blocked": "steamWrongDesktop"}
            if state["phase"] == "failed":
                break
        time.sleep(.05)
    (folder / "finish").touch()
    raise RuntimeError("Host artwork did not become ready")


def present(token):
    folder = ROOT / token
    if steam_on_other_display():
        write_state(folder, "wrong-desktop")
        return
    import fcntl
    import gi
    gi.require_version("Gtk", "3.0")
    from gi.repository import Gtk, Gdk, GLib, Pango, PangoCairo
    from PIL import Image, ImageOps
    folder = ROOT / token
    config = json.loads((folder / "config.json").read_text())
    lock = (ROOT / "active.lock").open("a")
    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    output = next(o for o in ipc(3) if o.get("name") == "HEADLESS-1" and o.get("active"))
    width, height = output["rect"]["width"], output["rect"]["height"]
    art_size = tuple(map(int, config["resolution"].split("x")))
    # Match the handheld's centre/cover crop and 15%→40% black gradient.
    try:
        with Image.open(folder / "art") as original:
            if original.width * original.height > 25_000_000:
                raise ValueError("Artwork dimensions too large")
            image = ImageOps.fit(original.convert("RGB"), art_size)
    except (OSError, ValueError):
        image = Image.new("RGB", art_size, (10, 13, 16))
    art_width, art_height = art_size
    gradient = Image.new("L", (1, art_height))
    gradient.putdata([round(255 * (.15 + .25 * y / max(1, art_height - 1))) for y in range(art_height)])
    image.paste((0, 0, 0), (0, 0, art_width, art_height), gradient.resize(art_size))
    image.save(folder / "background.png")
    wallpaper = subprocess.Popen(["swaybg", "-o", "HEADLESS-1", "-i", str(folder / "background.png"), "-m", "fill"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    moved = {}
    seen = False
    positioned = False
    attention = False
    closed_at = None
    began = time.monotonic()
    log = Path.home() / ".local/share/Steam/logs/console_log.txt"
    log_offset = log.stat().st_size if log.exists() else 0
    pending = None
    waiting_at = None
    finished = False
    game_containers = set()
    last_output_check = time.monotonic()
    GLib.set_prgname("creteos-host-messages")
    window = Gtk.Window()
    window.set_title("CreteOS stream artwork")
    window.set_decorated(False)
    window.set_resizable(False)
    window.set_accept_focus(False)
    window.set_app_paintable(True)
    window.set_visual(window.get_screen().get_rgba_visual())
    window.set_default_size(min(900, width), 130)
    started = time.monotonic()
    messages = config["messages"]

    def draw(widget, cr):
        import cairo
        cr.set_operator(cairo.OPERATOR_SOURCE)
        cr.set_source_rgba(0, 0, 0, 0)
        cr.paint()
        cr.set_operator(cairo.OPERATOR_OVER)
        layout = widget.create_pango_layout(messages[int((time.monotonic() - started) / 3.5) % len(messages)])
        layout.set_font_description(Pango.FontDescription("Sans " + str(max(13, round(height * .015)))))
        tw, th = layout.get_pixel_size()
        cr.move_to((widget.get_allocated_width() - tw) / 2, (widget.get_allocated_height() - th) / 2)
        cr.set_source_rgba(.95, .92, .86, .88)
        PangoCairo.show_layout(cr, layout)
        return False
    window.connect("draw", draw)

    def restore():
        restore_saved(folder)
        moved.clear()

    def cleanup(*_):
        nonlocal finished
        if finished:
            return
        finished = True
        try:
            restore()
        finally:
            try:
                wallpaper.terminate()
                try:
                    wallpaper.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    wallpaper.kill()  # Only our own swaybg child.
                    wallpaper.wait(timeout=3)
                write_state(folder, "finished")
                # Retain state/source, not full-size art for every launch.
                for name in ("art", "background.png"):
                    (folder / name).unlink(missing_ok=True)
            finally:
                Gtk.main_quit()

    def tick():
        nonlocal seen, positioned, attention, closed_at, log_offset, pending, waiting_at, last_output_check
        try:
            now = time.monotonic()
            if (folder / "finish").exists() or now - began > 86400:
                cleanup()
                return False
            lease = folder / "lease"
            last = lease.stat().st_mtime if lease.exists() else (folder / "config.json").stat().st_mtime
            if time.time() - last > (60 if closed_at else 30):
                cleanup()
                return False
            if log.exists() and not seen:
                with log.open() as stream:
                    stream.seek(log_offset)
                    lines = stream.read(65536)
                    log_offset = stream.tell()
                for line in lines.splitlines():
                    if f"AppID {config['appId']}," not in line:
                        continue
                    if "waiting for user response" in line:
                        pending = line.split("response to ")[-1].split()[0]
                        waiting_at = now
                    elif "continues with user response" in line or "changed task" in line:
                        pending = waiting_at = None
            nodes = list(windows(ipc(4)))
            if now - last_output_check >= 1 and not seen:
                # Sunshine applies the selected client mode after staging.
                ipc(0, f"[pid={os.getpid()}] move position center")
                last_output_check = now
            # "waiting for user response" also covers asynchronous Steam UI
            # preparation. Reveal only a real dialog, or a genuinely stalled
            # launch, not a normal background app-details lookup.
            if needs_attention(pending, waiting_at, now, nodes, began):
                attention = True
            games = [node for node, _ in nodes if game_window(node, config["appId"])]
            if games:
                if not seen:
                    seen = True
                    attention = False
                    window.hide()
                    write_state(folder, "playing")
                closed_at = None
                for game in games:
                    if game['id'] not in game_containers:
                        ipc(0, f"[con_id={game['id']}] border none, fullscreen enable, focus")
                        game_containers.add(game['id'])
            elif seen:
                if closed_at is None:
                    closed_at = now
                    window.hide()
                    write_state(folder, "returning")
            if attention and not seen:
                window.hide()
                restore()
                write_state(folder, "attention")
            else:
                for node, workspace in nodes:
                    if steam_library(node) and workspace != "__i3_scratch":
                        conceal_library(folder, node, moved)
            if not positioned:
                own = [n for n, _ in nodes if n.get("pid") == os.getpid()]
                if own:
                    ipc(0, f"[con_id={own[0]['id']}] floating enable, border none, resize set {min(900,width)} px 130 px, move position center")
                    positioned = True
                    write_state(folder, "ready")
            window.queue_draw()
            return True
        except Exception:
            cleanup()
            return False

    window.show_all()
    GLib.timeout_add(100, tick)
    for sig in (signal.SIGTERM, signal.SIGINT, signal.SIGHUP):
        GLib.unix_signal_add(GLib.PRIORITY_DEFAULT, sig, lambda: (cleanup(), False)[1])
    Gtk.main()


if __name__ == "__main__":
    if "PAYLOAD" in globals():
        try:
            print(json.dumps(stage(PAYLOAD)))
        except Exception:
            print(json.dumps({"unavailable": True}))
    else:
        try:
            present(sys.argv[1])
        except Exception:
            write_state(ROOT / sys.argv[1], "failed")

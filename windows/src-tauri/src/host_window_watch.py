"""Read-only, exact-game window lifecycle for a dedicated Linux stream display.

Output contains lifecycle events only: no titles, pixels, environment values,
credentials, input or commands that close a host application.
"""
import json
import os
import pathlib
import socket
import struct
import sys
import time


class WindowPresence:
    def __init__(self):
        self.first = None
        self.gone = None
        self.seen = False

    def sample(self, present, now):
        if present:
            self.gone = None
            if self.first is None:
                self.first = now
            if not self.seen and now - self.first >= 0.25:
                self.seen = True
                return "visible"
        else:
            self.first = None
            if self.seen:
                if self.gone is None:
                    self.gone = now
                elif now - self.gone >= 0.75:
                    return "closed"
        return None


def has_game_window(node, app_id, pid_matches):
    props = node.get("window_properties") or {}
    exact_class = str(props.get("class", "")).lower() == "steam_app_" + app_id
    exact_app = str(node.get("app_id", "")).lower() == "steam_app_" + app_id
    if node.get("pid") and (exact_class or exact_app or pid_matches(node["pid"])):
        return True
    return any(has_game_window(child, app_id, pid_matches)
               for child in node.get("nodes", []) + node.get("floating_nodes", []))


def read_exact(stream, count):
    result = b""
    while len(result) < count:
        chunk = stream.recv(count - len(result))
        if not chunk:
            raise OSError("IPC closed")
        result += chunk
    return result


def get_tree(path):
    with socket.socket(socket.AF_UNIX) as stream:
        stream.settimeout(2)
        stream.connect(str(path))
        stream.sendall(b"i3-ipc" + struct.pack("<II", 0, 4))
        header = read_exact(stream, 14)
        length, _ = struct.unpack("<II", header[6:])
        if header[:6] != b"i3-ipc" or length > 2 * 1024 * 1024:
            raise ValueError("Invalid IPC reply")
        return json.loads(read_exact(stream, length))


def emit(event):
    print(json.dumps({"event": event}), flush=True)


def main():
    if sys.platform != "linux" or len(sys.argv) not in (2, 3):
        emit("unavailable")
        return
    app_id = sys.argv[1]
    if not app_id.isascii() or not app_id.isdigit() or len(app_id) > 10:
        emit("unavailable")
        return
    root = pathlib.Path("/run/user") / str(os.getuid())
    dedicated = root / "sway-sunshine.sock"
    paths = [dedicated] if dedicated.is_socket() else list(root.glob("sway-ipc.*.sock"))
    # Never choose among multiple desktop sessions or inspect an unrelated one.
    if len(paths) != 1:
        emit("unavailable")
        return

    def pid_matches(pid):
        try:
            values = (pathlib.Path("/proc") / str(int(pid)) / "environ").read_bytes().split(b"\0")
            return any((key + app_id).encode() in values for key in ("SteamAppId=", "SteamGameId="))
        except (OSError, ValueError):
            return False

    tracker = WindowPresence()
    presentation = None
    if len(sys.argv) == 3 and sys.argv[2] != "none":
        token = sys.argv[2]
        if len(token) <= 80 and all(c in "0123456789abcdef-" for c in token):
            presentation = pathlib.Path.home() / ".local/share/creteos-host" / token
    attention_sent = False
    started = time.monotonic()
    heartbeat = started
    get_tree(paths[0])  # Establish capability before delaying client handoff.
    emit("ready")
    while time.monotonic() - started < 86400:
        now = time.monotonic()
        if presentation and presentation.is_dir():
            (presentation / "lease").touch()
            try:
                if not attention_sent and json.loads((presentation / "state.json").read_text())["phase"] == "attention":
                    emit("attention")
                    attention_sent = True
            except (OSError, ValueError, KeyError):
                pass
        event = tracker.sample(has_game_window(get_tree(paths[0]), app_id, pid_matches), now)
        if event:
            emit(event)
            if event == "closed":
                return
        if not tracker.seen and now - started >= 180:
            emit("unavailable")
            return
        if now - heartbeat >= 10:
            emit("alive")
            heartbeat = now
        time.sleep(0.25)


if __name__ == "__main__":
    try:
        main()
    except BrokenPipeError:
        pass
    except Exception:
        # Errors never look like a game exit. The native caller keeps Buddy's
        # normal exit monitoring and existing readiness fallback.
        emit("unavailable")

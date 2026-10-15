"""Run a dedicated streaming service with the actual Sway output environment.

Installed explicitly on the host; this does not alter the user manager's global
environment or stop/move an existing Steam singleton.
"""
import json
import os
from pathlib import Path, PurePosixPath
import re
import shlex
import subprocess
import sys
import tempfile
import time

KEYS = ("DISPLAY", "WAYLAND_DISPLAY", "SWAYSOCK", "XAUTHORITY", "XDG_RUNTIME_DIR",
        "XDG_SESSION_TYPE", "XDG_CURRENT_DESKTOP", "XDG_SEAT", "PULSE_SINK")


def validate_environment(values, runtime):
    expected = str(PurePosixPath(runtime) / "sway-sunshine.sock")
    if values.get("SWAYSOCK") != expected:
        raise ValueError("Unexpected streaming compositor")
    if not re.fullmatch(r":\d+(?:\.\d+)?", values.get("DISPLAY", "")):
        raise ValueError("Streaming X11 display unavailable")
    if not re.fullmatch(r"wayland-\d+", values.get("WAYLAND_DISPLAY", "")):
        raise ValueError("Streaming Wayland display unavailable")
    return {key: values[key] for key in KEYS if isinstance(values.get(key), str)}


def stream_environment(runtime):
    socket = str(Path(runtime) / "sway-sunshine.sock")
    deadline = time.monotonic() + 25
    while not Path(socket).is_socket():
        if time.monotonic() > deadline:
            raise RuntimeError("Dedicated streaming compositor is not ready")
        time.sleep(.2)
    # Sway supplies its real Xwayland DISPLAY, which may differ from the login
    # desktop's value inherited by systemd user services. Never export globally.
    with tempfile.TemporaryDirectory(prefix="creteos-service-env-", dir=runtime) as directory:
        result = Path(directory) / "environment.json"
        script = ("import os,json;from pathlib import Path;Path(" + repr(str(result)) +
                  ").write_text(json.dumps({k:os.environ.get(k) for k in " + repr(KEYS) + "}))")
        command = shlex.join([sys.executable, "-c", script])
        subprocess.run(["swaymsg", "-s", socket, "exec", command], check=True,
                       capture_output=True, timeout=5)
        deadline = time.monotonic() + 5
        while time.monotonic() < deadline:
            try:
                return validate_environment(json.loads(result.read_text()), runtime)
            except (FileNotFoundError, json.JSONDecodeError):
                time.sleep(.05)
        raise RuntimeError("Streaming desktop environment was not returned")


def main(command):
    if not command:
        raise ValueError("A service executable is required")
    runtime = os.environ.get("XDG_RUNTIME_DIR", "/run/user/" + str(os.getuid()))
    values = stream_environment(runtime)
    environment = dict(os.environ)
    for key in KEYS:
        environment.pop(key, None)
    environment.update(values)
    if Path(command[0]).name == "MoonDeckBuddy":
        environment["NO_GUI"] = environment.get("MOONDECKBUDDY_NO_GUI", "true")
    os.execvpe(command[0], command, environment)


if __name__ == "__main__":
    main(sys.argv[1:])

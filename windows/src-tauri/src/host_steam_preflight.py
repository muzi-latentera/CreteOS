"""Move only an idle Steam singleton to the dedicated streaming desktop.

Never kill processes, start a game, approve a dialog or change save settings.
Only fixed status codes leave the host. Failed/unknown checks fail closed.
"""
import json
import os
from pathlib import Path
import subprocess
import sys
import time

# Linux marks these session infrastructure processes non-dumpable. They are
# not Steam/game runtimes. Unknown protected processes still block migration.
PROTECTED_SESSION_PROCESSES = frozenset(('systemd', '(sd-pam)', 'kwin_wayland',
                                       'polkit-kde-auth', 'sway', 'sshd-session'))


def snapshot(proc=Path('/proc')):
    steam, games = [], []
    for process in proc.iterdir():
        if not process.name.isdigit():
            continue
        try:
            if process.stat().st_uid != os.getuid():
                continue
            comm = (process / 'comm').read_text().strip()
            try:
                values = (process / 'environ').read_bytes().split(b'\0')
            except PermissionError:
                if comm in PROTECTED_SESSION_PROCESSES:
                    continue
                raise
            env = dict(v.decode(errors='replace').split('=', 1) for v in values if b'=' in v)
            if comm == 'steam':
                steam.append((int(process.name), env))
            elif comm != 'steamwebhelper' and game_environment(env):
                games.append(int(process.name))
        except FileNotFoundError:
            continue  # A process that exited during enumeration.
        except (PermissionError, OSError):
            # Unable to prove the same-user process is safe to interrupt.
            raise RuntimeError('processCheckUnavailable')
    return steam, games


def game_environment(env):
    return any(env.get(key, '') not in ('', '0') for key in ('SteamAppId', 'SteamGameId'))


def wrong_desktop(steam, target):
    for _, env in steam:
        # Steam uses X11 even if it inherited a Wayland display variable.
        key = 'DISPLAY' if env.get('DISPLAY') else 'WAYLAND_DISPLAY'
        if not env.get(key) or not target.get(key):
            raise RuntimeError('desktopUnknown')
        if env[key] != target[key]:
            return True
    return False


def migrate(target, run=subprocess.run, inspect=snapshot, sleep=time.sleep, clock=time.monotonic):
    steam, games = inspect()
    if not steam or not wrong_desktop(steam, target):
        return 'ready'
    if games:
        return 'gameRunning'
    # A pending install/cloud/confirmation can also make shutdown unsafe.
    # Steam itself decides whether a normal shutdown can complete.
    environment = dict(os.environ)
    for key in ('DISPLAY', 'WAYLAND_DISPLAY', 'SWAYSOCK', 'XAUTHORITY', 'DRI_PRIME'):
        environment.pop(key, None)
    environment.update(target)
    run(['/usr/bin/steam', '-shutdown'], env=environment, capture_output=True, timeout=8, check=True)
    deadline = clock() + 25
    while True:
        remaining, games = inspect()
        if games:
            return 'gameRunning'
        if not remaining:
            break
        if clock() >= deadline:
            return 'shutdownPending'
        sleep(.25)
    # Buddy starts Steam later on this compositor. No restart/game is needed
    # during preflight; this also keeps cancellation from opening extra apps.
    return 'moved'


def main():
    import fcntl
    sys.path.insert(0, str(Path.home() / '.local/share/creteos-host'))
    from host_stream_env import stream_environment
    runtime = '/run/user/' + str(os.getuid())
    with (Path(runtime) / 'creteos-steam-preflight.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        target = stream_environment(runtime)
        # A connected client may belong to another handheld. Never migrate it.
        active = subprocess.run(['ss', '-Htn', 'state', 'established', '(', 'sport', '=', '48010', ')'],
                                capture_output=True, text=True, timeout=3, check=True).stdout.strip()
        steam, _ = snapshot()
        if active and wrong_desktop(steam, target):
            return 'streamActive'
        return migrate(target)


if __name__ == '__main__':
    try:
        result = main()
    except Exception:
        result = 'unavailable'
    print(json.dumps({'status': result}), flush=True)

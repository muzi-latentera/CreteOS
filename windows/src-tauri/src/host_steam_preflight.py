"""Close idle Steam so Buddy can start a fresh client on the streaming desktop.

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
        except (FileNotFoundError, ProcessLookupError):
            continue  # A process that exited during enumeration.
        except (PermissionError, OSError):
            # Unable to prove the same-user process is safe to interrupt.
            raise RuntimeError('processCheckUnavailable')
    return steam, games


def game_environment(env):
    ids = (env.get('SteamAppId', ''), env.get('SteamGameId', ''))
    # Standalone Lutris/UMU prefixes advertise default IDs even without a
    # Steam session. A real Steam shortcut ID still protects its processes.
    if (env.get('LUTRIS_GAME_UUID') and env.get('STEAM_COMPAT_DATA_PATH')
            and 'default' in ids and all(value in ('', '0', 'default') for value in ids)):
        return False
    return any(value not in ('', '0') for value in ids)


def steam_snapshot(proc=Path('/proc')):
    """Checking an already-correct desktop must not inspect unrelated processes.

    Strict game/protected-process checks remain mandatory before any migration.
    """
    steam = []
    for process in proc.iterdir():
        if not process.name.isdigit():
            continue
        try:
            if process.stat().st_uid != os.getuid() or (process / 'comm').read_text().strip() != 'steam':
                continue
            values = (process / 'environ').read_bytes().split(b'\0')
            steam.append((int(process.name), dict(v.decode(errors='replace').split('=', 1) for v in values if b'=' in v)))
        except (FileNotFoundError, ProcessLookupError):
            continue
        except (PermissionError, OSError):
            raise RuntimeError('processCheckUnavailable')
    return steam


def wait_for_snapshot(inspect, timeout=5, clock=time.monotonic, sleep=time.sleep):
    deadline = clock() + timeout
    while True:
        try:
            return inspect()
        except RuntimeError as error:
            if str(error) != 'processCheckUnavailable' or clock() >= deadline:
                raise
            sleep(.25)


def restart_idle(target, stream_active=lambda: False, run=subprocess.run, inspect=snapshot, sleep=time.sleep, clock=time.monotonic):
    steam, games = inspect()
    if games:
        return 'gameRunning'
    if stream_active():
        return 'streamActive'
    if not steam:
        return 'ready'
    # A pending install/cloud/confirmation can also make shutdown unsafe.
    # Steam itself decides whether a normal shutdown can complete.
    environment = dict(os.environ)
    for key in ('DISPLAY', 'WAYLAND_DISPLAY', 'SWAYSOCK', 'XAUTHORITY', 'DRI_PRIME'):
        environment.pop(key, None)
    environment.update(target)
    run(['/usr/bin/steam', '-shutdown'], env=environment, capture_output=True, timeout=8, check=True)
    deadline = clock() + 25
    while True:
        try:
            remaining, games = inspect()
        except RuntimeError as error:
            # Steam's own shutdown briefly makes dying process environments
            # unreadable. Wait for completion; never repeat shutdown or kill.
            if str(error) != 'processCheckUnavailable':
                raise
            if clock() >= deadline:
                return 'shutdownCheckPending'
            sleep(.25)
            continue
        if games:
            return 'gameRunning'
        if not remaining:
            break
        if clock() >= deadline:
            return 'shutdownPending'
        sleep(.25)
    # Buddy starts Steam later on this compositor. No restart/game is needed
    # during preflight; this also keeps cancellation from opening extra apps.
    return 'closed'


def stream_active():
    result = subprocess.run(['ss', '-Htn', 'state', 'established', '(', 'sport', '=', '48010', ')'],
                            capture_output=True, text=True, timeout=3, check=True)
    if result.stdout.strip():
        return True
    # RTSP can close after setup while Sunshine still owns its UDP stream sockets.
    result = subprocess.run(['ss', '-Huanp', '( sport = :47998 or sport = :47999 or sport = :48000 or sport = :48002 )'],
                            capture_output=True, text=True, timeout=3, check=True)
    return any('"sunshine"' in line for line in result.stdout.splitlines())


def main():
    import fcntl
    sys.path.insert(0, str(Path.home() / '.local/share/creteos-host'))
    from host_stream_env import stream_environment
    runtime = '/run/user/' + str(os.getuid())
    with (Path(runtime) / 'creteos-steam-preflight.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        try:
            target = stream_environment(runtime)
        except Exception:
            raise RuntimeError('targetUnavailable')
        # A connected client may belong to another handheld.
        if stream_active():
            return 'streamActive'
        if not wait_for_snapshot(steam_snapshot):
            return 'ready'
        return restart_idle(target, stream_active, inspect=lambda: wait_for_snapshot(snapshot))


if __name__ == '__main__':
    try:
        result = main()
    except BlockingIOError:
        result = 'preflightBusy'
    except subprocess.SubprocessError:
        result = 'commandFailed'
    except RuntimeError as error:
        result = str(error) if str(error) in ('processCheckUnavailable', 'targetUnavailable') else 'unavailable'
    except Exception:
        result = 'unavailable'
    print(json.dumps({'status': result}), flush=True)

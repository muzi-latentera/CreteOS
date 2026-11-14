"""CreteOS save helper for Bosgame.

CreteOS sends this file over SSH for read-only checks and explicit save actions.
The Lutris post-exit hook runs an installed copy with ``--after-exit NAME``.
Backups are always written locally first; Google Drive is then synced only in a
direction that cannot discard a version.
"""
import concurrent.futures
import datetime
import json
import os
import pathlib
import sqlite3
import subprocess
import sys
import time

HOME = pathlib.Path.home()
STATE = pathlib.Path(os.environ.get("XDG_STATE_HOME", HOME / ".local/state")) / "creteos"
CHECKPOINT = "CreteOS: before restore"
CHECKPOINTS_KEPT = 3
DEADLINE = None  # monotonic deadline for explicit actions (CreteOS waits 480 s)


class Refusal(Exception):
    """A reason shown to the user; nothing beyond the reported step was changed."""


def run(tool, *args, timeout=12, stream="stdout", check=True):
    if DEADLINE is not None:
        left = DEADLINE - time.monotonic()
        if left < 5:
            raise Refusal("Bosgame ran out of time. Nothing further was changed.")
        timeout = min(timeout, left)
    result = subprocess.run([str(HOME / ".local/bin" / tool), *args],
                            capture_output=True, stdin=subprocess.DEVNULL, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError(result.returncode)
    return result.stdout if stream == "stdout" else result.stderr


def ludusavi(*args, timeout=12):
    return json.loads(run("ludusavi", "--no-manifest-update", *args, timeout=timeout))


def find_title(name, timeout=12):
    # Ludusavi exits non-zero when nothing matches, but still prints its report.
    raw = run("ludusavi", "--no-manifest-update", "find", "--backup", "--normalized", "--api", "--", name,
              timeout=timeout, check=False)
    try:
        match = json.loads(raw)
    except ValueError:
        raise RuntimeError("find") from None
    return next(iter(match.get("games", {})), None)


# ------------------------------------------------------------------ Drive sync

def plan_sync(upload, download):
    """Ludusavi 0.31 marks a version only this host has as Removed in the
    download preview, and a version only Drive has as Removed in the upload
    preview. mapping.yaml changes with either side, so it decides nothing."""
    paths = set(upload) | set(download)
    if not paths:
        return "synced"
    here = there = False
    for path in paths:
        if path == "mapping.yaml" or path.endswith("/mapping.yaml"):
            continue
        up = (upload.get(path) or {}).get("change")
        down = (download.get(path) or {}).get("change")
        if up == "Removed":
            there = True
        elif down == "Removed":
            here = True
        else:
            return "conflict"
    if here and there:
        return "conflict"
    return "download" if there else "upload"


def cloud_enabled():
    try:
        cloud = ludusavi("config", "show", "--api").get("cloud", {})
    except (RuntimeError, subprocess.TimeoutExpired, ValueError):
        return False
    return cloud.get("synchronize") is True and bool(cloud.get("remote"))


def cloud_report(direction, title, *extra, timeout=150):
    raw = run("ludusavi", "--no-manifest-update", "cloud", direction, *extra, "--api", "--", title,
              timeout=timeout, stream="stderr")
    return json.loads(raw or b"{}").get("cloud", {})


def quick_plan(upload):
    """One preview settles most cases; Bosgame measured 57-98 s per Drive preview."""
    if not upload:
        return "synced"
    changes = [(entry or {}).get("change") for path, entry in upload.items()
               if path != "mapping.yaml" and not path.endswith("/mapping.yaml")]
    if not changes:
        return "upload"
    return "download" if all(change == "Removed" for change in changes) else None


def cloud_plan(title):
    upload = cloud_report("upload", title, "--preview")
    return quick_plan(upload) or plan_sync(upload, cloud_report("download", title, "--preview"))


def sync(title):
    if not cloud_enabled():
        return "off"
    try:
        plan = cloud_plan(title)
        if plan in ("synced", "conflict"):
            return plan
        cloud_report(plan, title, "--force", timeout=240)
        return "uploaded" if plan == "upload" else "downloaded"
    except (RuntimeError, subprocess.TimeoutExpired, ValueError, OSError, Refusal):
        return "unavailable"


CLOUD_MESSAGES = {
    "synced": "Google Drive is up to date.",
    "uploaded": "Uploaded to Google Drive.",
    "downloaded": "Downloaded newer versions from Google Drive.",
    "conflict": "Google Drive and Bosgame each have versions the other lacks. Nothing was overwritten.",
    "off": "Google Drive sync is off.",
}


def cloud_message(state):
    return CLOUD_MESSAGES.get(state, "Google Drive could not be reached. CreteOS will try again after the next backup.")


# ------------------------------------------------------------------ Recorded results

def status_path():
    return STATE / "lutris-saves-status.json"


def record(title, message, cloud):
    STATE.mkdir(mode=0o700, parents=True, exist_ok=True)
    try:
        events = json.loads(status_path().read_text())
    except (OSError, ValueError):
        events = {}
    events[title] = {"when": datetime.datetime.now(datetime.timezone.utc).isoformat(), "message": message, "cloud": cloud}
    temp = status_path().with_suffix(f".{os.getpid()}.next")
    temp.write_text(json.dumps(events, indent=1))
    os.chmod(temp, 0o600)
    temp.replace(status_path())


def last_event(title):
    try:
        return json.loads(status_path().read_text()).get(title)
    except (OSError, ValueError):
        return None


# ------------------------------------------------------------------ Read-only comparison

def compare(game):
    title = find_title(game["name"])
    if title is None:
        return {"scanOk": True, "cloudStatus": "unavailable", "known": False}
    # Read the config before any scan starts: `config show` failed while two other
    # Ludusavi processes ran (13 Nov), which silently reported Drive as unreachable.
    try:
        config = ludusavi("config", "show", "--api")["cloud"]
    except (RuntimeError, subprocess.TimeoutExpired, ValueError, KeyError):
        config = {}
    with concurrent.futures.ThreadPoolExecutor() as jobs:
        active = jobs.submit(ludusavi, "backup", "--preview", "--api", "--", title)
        history = jobs.submit(ludusavi, "backups", "--api", "--", title)
        try:
            backup = history.result().get("games", {}).get(title, {})
        except (RuntimeError, subprocess.TimeoutExpired, ValueError):
            backup = {}
        remote = (config.get("remote") or {}).get("Custom", {}).get("id")
        folder = pathlib.Path(backup["backupPath"]).name if "backupPath" in backup else game_folder(title)
        cloud = jobs.submit(read_cloud, remote, config.get("path"), folder)
        try:
            entry = active.result().get("games", {}).get(title, {})
            times = []
            for path, info in entry.get("files", {}).items():
                if not info.get("ignored") and info.get("change") != "Removed":
                    try:
                        times.append(pathlib.Path(path).stat().st_mtime)
                    except OSError:
                        pass
            latest = int(max(times)) if times else None
            scan_ok = True
        except (RuntimeError, subprocess.TimeoutExpired, ValueError):
            latest, scan_ok, entry = None, False, {}
        cloud_status, cloud_mapping, cloud_detail = cloud.result()
    if config.get("synchronize") is not True:
        cloud_status, cloud_mapping, cloud_detail = "off", None, ""
    return {"scanOk": scan_ok, "known": True, "active": latest, "localChange": entry.get("change"),
            "versions": backup.get("backups", []), "cloudStatus": cloud_status, "cloudMapping": cloud_mapping, "cloudDetail": cloud_detail,
            "lastEvent": last_event(title)}


def game_folder(title):
    import re
    return re.sub(r'[<>:"/\\|?*\x00-\x1f]|^\.|\.$', '_', title)


def read_cloud(remote, path, folder):
    if not remote or not path or not folder or folder in (".", "..") or "/" in folder or "\\" in folder:
        return "unavailable", None, "Ludusavi has no Google Drive folder configured."
    # rclone's shared Google client hits its quota now and then (13-14 Nov), so a
    # failed read is retried once before Drive is reported unreachable.
    detail = ""
    for attempt in range(2):
        if attempt:
            time.sleep(1.5)
        try:
            result = subprocess.run([str(HOME / ".local/bin/rclone"), "cat", f"{remote}:{path}/{folder}/mapping.yaml",
                                     "--retries", "1", "--low-level-retries", "2", "--timeout", "7s"],
                                    capture_output=True, stdin=subprocess.DEVNULL, timeout=12)
        except (OSError, subprocess.TimeoutExpired) as error:
            detail = type(error).__name__
            continue
        if result.returncode == 0:
            return "ok", result.stdout.decode(errors="replace"), ""
        if result.returncode in (3, 4):
            return "missing", None, ""
        detail = drive_reason(result.stderr.decode(errors="replace"), result.returncode)
    return "unavailable", None, detail


def drive_reason(stderr, code):
    """Plain-language reason for a failed rclone Drive call (shown in Manage saves)."""
    text = stderr.lower()
    if any(key in text for key in ("ratelimit", "rate limit", "rate_limit", "quota", "429", "too many requests", "resource_exhausted", "usagelimits")):
        return "Google is limiting rclone's shared Google app right now. Your own Google client ID fixes this (see the setup guide)."
    if "invalid_grant" in text or ("token" in text and ("expired" in text or "revoked" in text)):
        return "Google Drive sign-in has expired. Reconnect rclone to Google Drive."
    if any(key in text for key in ("no such host", "dial tcp", "network is unreachable", "connection refused", "i/o timeout")):
        return "No network connection to Google Drive."
    lines = [line for line in stderr.splitlines() if "ERROR" in line or '"message"' in line]
    return (lines[-1].strip() if lines else f"rclone exit {code}")[-240:]


# ------------------------------------------------------------------ Explicit actions

def ensure_closed(game_id):
    try:
        import yaml
    except ImportError:
        raise Refusal("Bosgame is missing PyYAML, so CreteOS cannot confirm the game is closed.") from None
    root = HOME / ".local/share/lutris"
    try:
        db = sqlite3.connect((root / "pga.db").as_uri() + "?mode=ro", uri=True)
        try:
            row = db.execute("select configpath from games where installed=1 and id=?", (game_id,)).fetchone()
        finally:
            db.close()
        config = yaml.safe_load((root / "games" / (row[0] + ".yml")).read_text()) if row and row[0] \
            and pathlib.Path(row[0]).name == row[0] else None
    except (sqlite3.Error, OSError, yaml.YAMLError):
        raise Refusal("CreteOS could not read this game's Lutris settings on Bosgame.") from None
    if not isinstance(config, dict):
        raise Refusal("Refresh the Lutris library before changing saves.")
    executable = pathlib.Path(str((config.get("game") or {}).get("exe") or "")).name.casefold()
    if not executable:
        raise Refusal("CreteOS could not identify this game's executable, so its saves were not changed.")
    for proc in pathlib.Path("/proc").iterdir():
        if not proc.name.isdecimal():
            continue
        try:
            if proc.stat().st_uid != os.getuid():
                continue
            args = (proc / "cmdline").read_bytes().decode(errors="replace").split("\x00")
            comm = (proc / "comm").read_text().strip().casefold()
        except (FileNotFoundError, ProcessLookupError, PermissionError):
            continue
        if comm == executable[:15] or any(arg.replace("\\", "/").rsplit("/", 1)[-1].casefold() == executable for arg in args):
            raise Refusal("Close the game on Bosgame before changing its saves.")


def versions(title):
    items = ludusavi("backups", "--api", "--", title).get("games", {}).get(title, {}).get("backups", [])
    return sorted(items, key=lambda item: item.get("when", ""), reverse=True)


def protect_latest(title):
    items = versions(title)
    if not items:
        raise Refusal("The current save could not be protected, so nothing was restored.")
    run("ludusavi", "--no-manifest-update", "backups", "edit", "--lock", "--comment", CHECKPOINT,
        "--backup", items[0]["name"], "--", title, timeout=15)
    older = [item for item in items[1:] if item.get("locked") and item.get("comment") == CHECKPOINT]
    for item in older[CHECKPOINTS_KEPT - 1:]:
        run("ludusavi", "--no-manifest-update", "backups", "edit", "--unlock", "--backup", item["name"], "--", title,
            timeout=15, check=False)


def manage(game):
    global DEADLINE
    import fcntl

    DEADLINE = time.monotonic() + 420
    title = find_title(game["name"])
    if title is None:
        raise Refusal("Ludusavi has no save locations for this game.")
    if game["action"] == "sync":
        state = sync(title)
        return {"message": cloud_message(state), "cloud": state}
    STATE.mkdir(mode=0o700, parents=True, exist_ok=True)
    with (STATE / "lutris-saves.lock").open("a") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise Refusal("A Bosgame backup is already running. Try again when it finishes.") from None
        tool = lambda *args: json.loads(run("ludusavi", "--no-manifest-update", *args, timeout=90))
        if game["action"] == "backup":
            ensure_closed(game["id"])
            data = tool("backup", "--api", "--force", "--no-cloud-sync", "--full-limit", "10", "--", title)
            if data.get("errors"):
                raise Refusal("Ludusavi reported a backup error on Bosgame.")
            overall = data.get("overall", {})
            if not overall.get("processedGames"):
                raise Refusal("No save files were found for this game on Bosgame.")
            unchanged = (overall.get("changedGames") or {}).get("same", 0) > 0
            state = sync(title)
            message = "The save has not changed since the latest backup." if unchanged else "Backed up on Bosgame."
            record(title, message, state)
            return {"message": message, "cloud": state, "cloudMessage": cloud_message(state)}
        version = game.get("version")
        chosen = next((item for item in versions(title) if item["name"] == version), None)
        if chosen is None:
            raise Refusal("This backup version is no longer available.")
        args = ("restore", "--api", "--no-cloud-sync", "--backup", version)
        preview = tool(*args, "--preview", "--", title)
        if not preview.get("overall", {}).get("processedGames"):
            raise Refusal("This version has no restorable save data.")
        if game["action"] == "preview":
            entry = preview["games"][title]
            count = lambda change: sum(1 for key in ("files", "registry")
                                       for item in (entry.get(key) or {}).values() if item.get("change") == change)
            return {"created": chosen.get("when"), "replace": count("Different"), "add": count("New"), "same": count("Same")}
        if game["action"] != "restore":
            raise Refusal("Unknown save action.")
        ensure_closed(game["id"])
        # No pruning here: the chosen version may be the oldest of the ten.
        checkpoint = tool("backup", "--api", "--force", "--no-cloud-sync", "--full-limit", "255", "--", title)
        if checkpoint.get("errors"):
            raise Refusal("The current save could not be protected, so nothing was restored.")
        if checkpoint.get("overall", {}).get("processedGames"):
            protect_latest(title)
        ensure_closed(game["id"])
        if DEADLINE - time.monotonic() < 100:
            raise Refusal("Bosgame ran out of time before restoring. Your save was not changed.")
        restored = tool(*args, "--force", "--", title)
        if restored.get("errors"):
            raise Refusal("The restore did not finish. Your previous save is kept as “Before restore”.")
        state = sync(title)
        record(title, "Restored an earlier version.", state)
        return {"message": "Version restored on Bosgame. Your previous save is kept as “Before restore”.", "cloud": state}


# ------------------------------------------------------------------ Lutris post-exit hook

def after_exit(name):
    """Called by creteos-lutris-save-backup while it holds the save lock."""
    title = find_title(name, timeout=30)
    if title is None:
        return "No exact Ludusavi title match. Check the title or add a custom game."
    raw = run("ludusavi", "--no-manifest-update", "backup", "--api", "--force", "--ask-downgrade",
              "--no-cloud-sync", "--full-limit", "10", "--", title, timeout=240, check=False)
    try:
        data = json.loads(raw)
    except ValueError:
        data = {}
    overall = data.get("overall", {})
    if data.get("errors") or not data:
        record(title, "The automatic backup reported an error.", "unavailable")
        return f"{title}: Ludusavi reported {', '.join(data.get('errors', {})) or 'no report'}"
    if not overall.get("processedGames"):
        record(title, "No save files were found after the game closed.", "not-needed")
        return f"{title}: no matching save data was found."
    unchanged = (overall.get("changedGames") or {}).get("same", 0) > 0
    state = sync(title)
    message = "Save unchanged since the last backup." if unchanged else "Backed up after the game closed."
    record(title, message, state)
    return f"{title}: {message} {cloud_message(state)} bytes={overall.get('processedBytes', 0)}"


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "--after-exit":
        try:
            print(after_exit(sys.argv[2]))
        except (OSError, RuntimeError, ValueError, Refusal, subprocess.TimeoutExpired) as error:
            print(f"Backup failed: {error!r}")
            sys.exit(1)
        sys.exit(0)
    try:
        request = json.loads(input())
        print(json.dumps(manage(request) if request.get("action") else compare(request)))
    except Refusal as error:
        print(json.dumps({"error": str(error)}))
    except (OSError, RuntimeError, ValueError, subprocess.TimeoutExpired, KeyError, TypeError, sqlite3.Error):
        print(json.dumps({"error": "Bosgame could not complete the save operation."}))

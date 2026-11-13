"""Read only the selected Lutris game's save and backup metadata."""
import concurrent.futures
import datetime
import json
import pathlib
import subprocess


def run(tool, *args):
    result = subprocess.run([str(pathlib.Path.home() / ".local/bin" / tool), *args],
                            capture_output=True, stdin=subprocess.DEVNULL, timeout=12)
    if result.returncode:
        raise RuntimeError(result.returncode)
    return result.stdout


def ludusavi(*args):
    return json.loads(run("ludusavi", "--no-manifest-update", *args))


def main():
    game = json.loads(input())
    match = ludusavi("find", "--backup", "--normalized", "--api", "--", game["name"])
    title = next(iter(match.get("games", {})), None)
    if title is None:
        return {"scanOk": True, "cloudStatus": "unavailable", "known": False}
    with concurrent.futures.ThreadPoolExecutor() as jobs:
        active = jobs.submit(ludusavi, "backup", "--preview", "--api", "--", title)
        history = jobs.submit(ludusavi, "backups", "--api", "--", title)
        try:
            backup = history.result().get("games", {}).get(title, {})
        except (RuntimeError, subprocess.TimeoutExpired):
            backup = {}
        try:
            config = ludusavi("config", "show", "--api")["cloud"]
        except (RuntimeError, subprocess.TimeoutExpired, KeyError):
            config = {}
        remote = (config.get("remote") or {}).get("Custom", {}).get("id")
        folder = pathlib.Path(backup["backupPath"]).name if "backupPath" in backup else game_folder(title)
        cloud = jobs.submit(read_cloud, remote, config.get("path"), folder)
        try:
            entry = active.result().get("games", {}).get(title, {})
            files = entry.get("files", {})
            times = []
            for path, info in files.items():
                if not info.get("ignored") and info.get("change") != "Removed":
                    try:
                        times.append(pathlib.Path(path).stat().st_mtime)
                    except OSError:
                        pass
            latest = int(max(times)) if times else None
            scan_ok = True
        except (RuntimeError, subprocess.TimeoutExpired):
            latest, scan_ok, entry = None, False, {}
        cloud_status, cloud_mapping = cloud.result()
    return {"scanOk": scan_ok, "known": True, "active": latest, "localChange": entry.get("change"),
            "versions": [item["when"] for item in backup.get("backups", [])],
            "cloudStatus": cloud_status, "cloudMapping": cloud_mapping}


def game_folder(title):
    import re
    return re.sub(r'[<>:"/\\|?*\x00-\x1f]|^\.|\.$', '_', title)


def read_cloud(remote, path, folder):
    if not remote or not path or not folder or folder in (".", "..") or "/" in folder or "\\" in folder:
        return "unavailable", None
    try:
        data = run("rclone", "cat", f"{remote}:{path}/{folder}/mapping.yaml",
                   "--retries", "1", "--low-level-retries", "2", "--timeout", "7s")
        return "ok", data.decode()
    except RuntimeError as error:
        return ("missing" if error.args[0] in (3, 4) else "unavailable"), None
    except (OSError, subprocess.TimeoutExpired):
        return "unavailable", None


if __name__ == "__main__":
    print(json.dumps(main()))

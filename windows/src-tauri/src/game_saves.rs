//! Ludusavi owns save discovery, versioning, and explicit restores.
use serde_json::Value;
use sha2::{Digest, Sha256};
use std::{io::Write, os::windows::process::CommandExt, path::{Path, PathBuf}, process::{Command, Stdio}, sync::{Mutex, OnceLock}};
use windows::Win32::{
    Foundation::{CloseHandle, WAIT_OBJECT_0},
    System::Threading::{OpenProcess, WaitForSingleObject, INFINITE, PROCESS_SYNCHRONIZE},
    UI::WindowsAndMessaging::GetWindowThreadProcessId,
};

static BACKUP_LOCK: Mutex<()> = Mutex::new(());
static TOOLS: OnceLock<Result<PathBuf, String>> = OnceLock::new();
const BUNDLED_TOOLS: &[(&str, &[u8])] = &[
    ("ludusavi.exe", include_bytes!(concat!(env!("OUT_DIR"), "/game-save-tools/ludusavi.exe"))),
    ("rclone.exe", include_bytes!(concat!(env!("OUT_DIR"), "/game-save-tools/rclone.exe"))),
    ("ludusavi-LICENSE.txt", include_bytes!(concat!(env!("OUT_DIR"), "/game-save-tools/ludusavi-LICENSE.txt"))),
    ("rclone-LICENSE.txt", include_bytes!(concat!(env!("OUT_DIR"), "/game-save-tools/rclone-LICENSE.txt"))),
];

pub fn eligible(app_type: &str, source: &str, path: &str) -> bool {
    app_type == "game" && matches!(source, "" | "desktop" | "other" | "custom" | "manual" | "local" | "gog" | "humble")
        && (Path::new(path).is_absolute() || path.starts_with("goggalaxy://"))
}

fn tools() -> Result<&'static Path, String> {
    TOOLS.get_or_init(|| {
        let dir = super::liftoff_dir().join("tools/game-saves-0.31.0-1.76.0");
        std::fs::create_dir_all(&dir).map_err(|error| error.to_string())?;
        for (name, bytes) in BUNDLED_TOOLS {
            let path = dir.join(name);
            if std::fs::read(&path).is_ok_and(|existing| Sha256::digest(&existing) == Sha256::digest(bytes)) {
                continue;
            }
            let temp = path.with_extension(format!("{}.next", std::process::id()));
            std::fs::write(&temp, bytes).map_err(|error| error.to_string())?;
            std::fs::rename(temp, path).map_err(|error| error.to_string())?;
        }
        use_bundled_rclone()?;
        Ok(dir)
    }).as_ref().map(PathBuf::as_path).map_err(Clone::clone)
}

fn use_bundled_rclone() -> Result<(), String> {
    let Some(roaming) = dirs::config_dir() else { return Ok(()); };
    let path = roaming.join("ludusavi/config.yaml");
    let original = match std::fs::read_to_string(&path) {
        Ok(value) => value,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(()),
        Err(error) => return Err(error.to_string()),
    };
    let mut in_rclone = false;
    let mut updated = String::new();
    for line in original.split_inclusive('\n') {
        if !line.starts_with("    ") { in_rclone = line.trim_end() == "  rclone:"; }
        if in_rclone && line.starts_with("    path:") {
            let setting = line.trim().trim_start_matches("path:").trim().trim_matches('"');
            if setting.is_empty() || setting.contains("/Microsoft/WinGet/Packages/Rclone.Rclone_") {
                updated.push_str("    path: rclone.exe\n");
                continue;
            }
        }
        updated.push_str(line);
    }
    if updated != original {
        let temp = path.with_extension(format!("{}.next", std::process::id()));
        std::fs::write(&temp, updated).and_then(|_| std::fs::rename(temp, path))
            .map_err(|error| error.to_string())?;
    }
    Ok(())
}

fn command() -> Result<Command, String> {
    let dir = tools()?;
    let path = std::env::var_os("PATH").unwrap_or_default();
    let path = std::env::join_paths(std::iter::once(dir.to_owned()).chain(std::env::split_paths(&path)))
        .map_err(|error| error.to_string())?;
    let mut command = Command::new(dir.join("ludusavi.exe"));
    command.env("PATH", path).stdin(Stdio::null());
    Ok(command)
}

fn output(args: &[&str], timeout: u64) -> Result<std::process::Output, String> {
    let mut command = command()?;
    command.arg("--try-manifest-update").args(args);
    super::streaming::bounded_output_timeout(command, None, timeout)
        .map_err(|_| "Ludusavi could not complete the save operation.".to_owned())
}

fn query(args: &[&str]) -> Result<Value, String> {
    let output = output(args, 180)?;
    let data: Value = serde_json::from_slice(&output.stdout).map_err(|_| "Ludusavi returned no save report.")?;
    if !output.status.success() && data["games"].as_object().is_none_or(|games| !games.is_empty()) {
        return Err("Ludusavi reported a save or cloud sync error. Check Ludusavi for details.".into());
    }
    Ok(data)
}

fn title(name: &str) -> Result<Option<String>, String> {
    let report = output(&["find", "--backup", "--normalized", "--api", "--", name], 7)?;
    let data: Value = serde_json::from_slice(&report.stdout).map_err(|_| "Save catalog unavailable.")?;
    Ok(data["games"].as_object().and_then(|games| games.keys().next().cloned()))
}

fn backup(name: &str) -> Result<String, String> {
    let _guard = BACKUP_LOCK.lock().map_err(|_| "Save backup unavailable")?;
    let Some(title) = title(name)? else { return Ok("No matching Ludusavi title".into()); };
    let data = query(&["backup", "--api", "--force", "--no-force-cloud-conflict", "--ask-downgrade", "--full-limit", "10", "--", &title])?;
    if data["errors"].as_object().is_some_and(|errors| !errors.is_empty()) {
        return Err("Ludusavi reported a backup or sync conflict. Check Ludusavi.".into());
    }
    if data["overall"]["processedGames"].as_u64().unwrap_or(0) > 0 {
        Ok("Save backup checked".into())
    } else {
        Ok("No matching save files found".into())
    }
}

pub fn watch_exit(hwnd: isize, name: String) {
    let mut pid = 0;
    unsafe { GetWindowThreadProcessId(windows::Win32::Foundation::HWND(hwnd as _), Some(&mut pid)); }
    let Ok(process) = (unsafe { OpenProcess(PROCESS_SYNCHRONIZE, false, pid) }) else { return; };
    let raw = process.0 as usize;
    std::thread::spawn(move || {
        let process = windows::Win32::Foundation::HANDLE(raw as _);
        let exited = unsafe {
            let status = WaitForSingleObject(process, INFINITE);
            let _ = CloseHandle(process);
            status == WAIT_OBJECT_0
        };
        if !exited { return; }
        let message = backup(&name).unwrap_or_else(|error| error);
        let path = super::liftoff_dir().join("game-save-backup.log");
        if let Ok(mut log) = std::fs::OpenOptions::new().append(true).create(true).open(path) {
            let _ = writeln!(log, "{name:?}: {message}");
        }
    });
}

#[tauri::command]
pub fn open_game_saves() -> Result<(), String> {
    command()?
        .arg("gui").creation_flags(super::CREATE_NO_WINDOW).spawn().map_err(|error| error.to_string())?;
    Ok(())
}

#[derive(serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SaveCheck {
    message: &'static str,
    review: bool,
}

fn check(name: &str) -> SaveCheck {
    let result = (|| -> Result<_, String> {
        let _guard = BACKUP_LOCK.try_lock().map_err(|_| "Backup in progress")?;
        let Some(title) = title(name)? else {
            return Ok(SaveCheck { message: "No save locations are known for this title.", review: false });
        };
        let cloud = output(&["cloud", "download", "--preview", "--api", "--", &title], 10)?;
        if !cloud.status.success() { return Err("Cloud check failed".into()); }
        let data: Value = serde_json::from_slice(&cloud.stderr).map_err(|_| "Save comparison unavailable")?;
        let changed = data["cloud"].as_object().is_some_and(|files| files.values().any(|entry| {
            matches!(entry["change"].as_str(), Some("New" | "Different"))
        }));
        if changed {
            return Ok(SaveCheck { message: "Drive has a different backup. Review it before playing, or continue with this device’s save.", review: true });
        }
        let local = output(&["restore", "--preview", "--api", "--", &title], 7)?;
        let data: Value = serde_json::from_slice(&local.stdout).map_err(|_| "Local save comparison unavailable")?;
        let differs = data["games"].as_object().is_some_and(|games| games.values().any(|game| {
            ["files", "registry"].iter().any(|key| game[key].as_object().is_some_and(|items| items.values().any(|entry| {
                matches!(entry["change"].as_str(), Some("New" | "Different"))
            })))
        }));
        Ok(if differs {
            SaveCheck { message: "The latest local backup differs from the active save. Review it or play using this device’s save.", review: true }
        } else {
            SaveCheck { message: "No newer cloud backup was detected.", review: false }
        })
    })();
    result.unwrap_or(SaveCheck { message: "Save check unavailable. You can play using this device’s save.", review: true })
}

#[tauri::command]
pub async fn check_game_saves(name: String, app_type: String, source: String, path: String) -> Result<SaveCheck, String> {
    if !eligible(&app_type, &source, &path) { return Err("Save check is unavailable for this launch.".into()); }
    tauri::async_runtime::spawn_blocking(move || check(&name)).await.map_err(|error| error.to_string())
}

#[cfg(test)]
mod tests {
    use super::eligible;

    #[test]
    fn backup_targets_are_non_steam_local_games() {
        assert!(eligible("game", "gog", r"C:\Games\Game.exe"));
        assert!(eligible("game", "desktop", r"C:\Games\Game.lnk"));
        assert!(!eligible("game", "steam", "steam://rungameid/12"));
        assert!(!eligible("game", "lutris", "lutris://bosgame/1"));
        assert!(!eligible("app", "desktop", r"C:\Apps\App.exe"));
    }
}

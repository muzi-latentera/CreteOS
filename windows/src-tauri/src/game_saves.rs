//! Ludusavi owns save discovery, versioning, and explicit restores.
use serde_json::Value;
use std::{io::Write, os::windows::process::CommandExt, path::{Path, PathBuf}, process::{Command, Stdio}, sync::Mutex};
use windows::Win32::{
    Foundation::{CloseHandle, WAIT_OBJECT_0},
    System::Threading::{OpenProcess, WaitForSingleObject, INFINITE, PROCESS_SYNCHRONIZE},
    UI::WindowsAndMessaging::GetWindowThreadProcessId,
};

static BACKUP_LOCK: Mutex<()> = Mutex::new(());

pub fn eligible(app_type: &str, source: &str, path: &str) -> bool {
    app_type == "game" && matches!(source, "desktop" | "other" | "custom" | "manual" | "local" | "gog" | "humble")
        && (Path::new(path).is_absolute() || path.starts_with("goggalaxy://"))
}

fn executable() -> Option<PathBuf> {
    let local = dirs::data_local_dir()?.join("Programs/Ludusavi/ludusavi.exe");
    local.is_file().then_some(local)
}

fn query(args: &[&str]) -> Result<Value, String> {
    let mut command = Command::new(executable().ok_or("Install Ludusavi to manage Windows saves.")?);
    command.stdin(Stdio::null()).args(["--no-manifest-update"]).args(args);
    let output = super::streaming::bounded_output_timeout(command, None, 180)
        .map_err(|_| "Ludusavi could not complete the save operation.".to_owned())?;
    let data: Value = serde_json::from_slice(&output.stdout).map_err(|_| "Ludusavi returned no save report.")?;
    if !output.status.success() && data["games"].as_object().is_none_or(|games| !games.is_empty()) {
        return Err("Ludusavi reported a save or cloud sync error. Check Ludusavi for details.".into());
    }
    Ok(data)
}

fn title(name: &str) -> Result<Option<String>, String> {
    let data = query(&["find", "--backup", "--normalized", "--api", "--", name])?;
    Ok(data["games"].as_object().and_then(|games| games.keys().next().cloned()))
}

fn backup(name: &str) -> Result<String, String> {
    let _guard = BACKUP_LOCK.lock().map_err(|_| "Save backup unavailable")?;
    let Some(title) = title(name)? else { return Ok("No matching Ludusavi title".into()); };
    let data = query(&["backup", "--api", "--force", "--no-force-cloud-conflict", "--full-limit", "10", "--", &title])?;
    if data["errors"].as_object().is_some_and(|errors| !errors.is_empty()) {
        return Err("Ludusavi reported a backup or sync conflict. Check Ludusavi.".into());
    }
    if data["games"].as_object().is_some_and(|games| !games.is_empty()) {
        Ok("Save backup checked".into())
    } else {
        Ok("No matching save files found".into())
    }
}

pub fn watch_exit(hwnd: isize, name: String) {
    if executable().is_none() { return; }
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
    Command::new(executable().ok_or("Install Ludusavi to manage game saves.")?)
        .arg("gui").creation_flags(super::CREATE_NO_WINDOW).spawn().map_err(|error| error.to_string())?;
    Ok(())
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

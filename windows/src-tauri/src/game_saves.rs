//! Ludusavi owns save discovery, versioning, and explicit restores.
//!
//! Every backup is written locally first. Google Drive is then brought in line by
//! `sync_cloud`, which only uploads or downloads when one side merely lacks the
//! other's versions, and refuses when both sides hold versions the other lacks.
use serde_json::Value;
use base64::{engine::general_purpose::STANDARD, Engine as _};
use sha2::{Digest, Sha256};
use std::{collections::{BTreeMap, HashSet}, io::Write, path::{Path, PathBuf}, process::{Command, Stdio}, sync::{Mutex, OnceLock}};
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
/// Comment Ludusavi stores on the locked copy made before each restore.
const CHECKPOINT: &str = "CreteOS: before restore";
const CHECKPOINTS_KEPT: usize = 3;

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
        return Err("Ludusavi reported a save error.".into());
    }
    Ok(data)
}

fn read(args: &[&str], timeout: u64) -> Result<Value, String> {
    let mut cmd = command()?;
    cmd.arg("--no-manifest-update").args(args);
    let output = super::streaming::bounded_output_timeout(cmd, None, timeout)?;
    serde_json::from_slice(&output.stdout).map_err(|_| "Could not read the save report.".into())
}

fn has_errors(data: &Value) -> bool {
    data["errors"].as_object().is_some_and(|errors| !errors.is_empty())
}

fn title(name: &str) -> Result<Option<String>, String> {
    let report = output(&["find", "--backup", "--normalized", "--api", "--", name], 30)?;
    let data: Value = serde_json::from_slice(&report.stdout).map_err(|_| "Save catalog unavailable.")?;
    Ok(data["games"].as_object().and_then(|games| games.keys().next().cloned()))
}

/// Interactive paths never wait on a manifest download.
fn quick_title(name: &str) -> Result<Option<String>, String> {
    let data = read(&["find", "--backup", "--normalized", "--api", "--", name], 10)?;
    Ok(data["games"].as_object().and_then(|games| games.keys().next().cloned()))
}

// ---------------------------------------------------------------- Drive sync

#[derive(Clone, Copy, Debug, PartialEq)]
enum SyncPlan { Synced, Upload, Download, Conflict }

/// Decide from Ludusavi's two cloud previews which direction is safe.
///
/// Measured on Ludusavi 0.31: a version only this device has is `Removed` in the
/// download preview; a version only Drive has is `Removed` in the upload preview.
/// `mapping.yaml` differs whenever either side changed, so it is not evidence of a
/// direction on its own; mapping-only drift comes from local edits such as locking.
fn plan_sync(upload: &Value, download: &Value) -> SyncPlan {
    let (up, down) = (upload.as_object(), download.as_object());
    let paths: HashSet<&String> = up.into_iter().chain(down).flat_map(|map| map.keys()).collect();
    if paths.is_empty() { return SyncPlan::Synced; }
    fn change<'a>(map: Option<&'a serde_json::Map<String, Value>>, path: &str) -> &'a str {
        map.and_then(|map| map.get(path)).and_then(|entry| entry["change"].as_str()).unwrap_or("")
    }
    let (mut here, mut there) = (false, false);
    for path in paths {
        if path == "mapping.yaml" || path.ends_with("/mapping.yaml") { continue; }
        match (change(up, path), change(down, path)) {
            ("Removed", _) => there = true,
            (_, "Removed") => here = true,
            _ => return SyncPlan::Conflict,
        }
    }
    match (here, there) {
        (true, true) => SyncPlan::Conflict,
        (false, true) => SyncPlan::Download,
        _ => SyncPlan::Upload,
    }
}

fn cloud_json(args: &[&str], timeout: u64) -> Result<Value, String> {
    let mut cmd = command()?;
    cmd.arg("--no-manifest-update").args(args);
    let output = super::streaming::bounded_output_timeout(cmd, None, timeout)?;
    if !output.status.success() { return Err("Google Drive could not be reached.".into()); }
    // Ludusavi prints cloud reports on stderr.
    serde_json::from_slice(&output.stderr).or_else(|_| serde_json::from_slice(&output.stdout))
        .map_err(|_| "Google Drive returned no sync report.".into())
}

fn cloud_enabled() -> bool {
    read(&["config", "show", "--api"], 8).is_ok_and(|config| config["cloud"]["synchronize"] == true
        && config["cloud"]["remote"].as_object().is_some_and(|remote| !remote.is_empty()))
}

/// One Drive preview settles most cases; Bosgame measured 57–98 s per preview.
fn quick_plan(upload: &Value) -> Option<SyncPlan> {
    let entries: Vec<&str> = upload.as_object()?.iter()
        .filter(|(path, _)| path.as_str() != "mapping.yaml" && !path.ends_with("/mapping.yaml"))
        .map(|(_, entry)| entry["change"].as_str().unwrap_or("")).collect();
    if upload.as_object()?.is_empty() { return Some(SyncPlan::Synced); }
    if entries.is_empty() { return Some(SyncPlan::Upload); }
    // Only Drive-side versions: downloading cannot discard anything here.
    entries.iter().all(|change| *change == "Removed").then_some(SyncPlan::Download)
}

fn cloud_plan(title: &str) -> Result<SyncPlan, String> {
    let upload = cloud_json(&["cloud", "upload", "--preview", "--api", "--", title], 150)?;
    if let Some(plan) = quick_plan(&upload["cloud"]) { return Ok(plan); }
    let download = cloud_json(&["cloud", "download", "--preview", "--api", "--", title], 150)?;
    Ok(plan_sync(&upload["cloud"], &download["cloud"]))
}

/// Bring Drive in line with this device when that cannot discard any version.
fn sync_cloud(title: &str) -> &'static str {
    if !cloud_enabled() { return "off"; }
    match cloud_plan(title) {
        Err(_) => "unavailable",
        Ok(SyncPlan::Synced) => "synced",
        Ok(SyncPlan::Conflict) => "conflict",
        Ok(plan) => {
            let direction = if plan == SyncPlan::Upload { "upload" } else { "download" };
            match cloud_json(&["cloud", direction, "--force", "--api", "--", title], 240) {
                Ok(_) if plan == SyncPlan::Upload => "uploaded",
                Ok(_) => "downloaded",
                Err(_) => "unavailable",
            }
        }
    }
}

fn cloud_message(state: &str) -> &'static str {
    match state {
        "synced" => "Google Drive is up to date.",
        "uploaded" => "Uploaded to Google Drive.",
        "downloaded" => "Downloaded newer versions from Google Drive.",
        "conflict" => "Google Drive and this device each have versions the other lacks. Nothing was overwritten.",
        "off" => "Google Drive sync is off.",
        _ => "Google Drive could not be reached. CreteOS will try again after the next backup.",
    }
}

// ---------------------------------------------------------------- Recorded results

#[derive(serde::Deserialize, serde::Serialize, Clone)]
#[serde(rename_all = "camelCase")]
struct SaveEvent { when: String, message: String, cloud: String }

fn now() -> String { chrono::DateTime::<chrono::Utc>::from(std::time::SystemTime::now()).to_rfc3339() }

fn status_path() -> PathBuf { super::liftoff_dir().join("game-saves-status.json") }

fn record(title: &str, message: &str, cloud: &str) {
    let path = status_path();
    let mut events: BTreeMap<String, SaveEvent> = std::fs::read(&path).ok()
        .and_then(|bytes| serde_json::from_slice(&bytes).ok()).unwrap_or_default();
    events.insert(title.to_owned(), SaveEvent { when: now(), message: message.into(), cloud: cloud.into() });
    if let Ok(bytes) = serde_json::to_vec_pretty(&events) {
        let temp = path.with_extension(format!("{}.next", std::process::id()));
        let _ = std::fs::write(&temp, bytes).and_then(|_| std::fs::rename(temp, &path));
    }
}

fn last_event(title: &str) -> Option<SaveEvent> {
    let events: BTreeMap<String, SaveEvent> = serde_json::from_slice(&std::fs::read(status_path()).ok()?).ok()?;
    events.get(title).cloned()
}

// ---------------------------------------------------------------- Automatic backup

fn backup(name: &str) -> Result<String, String> {
    let _guard = BACKUP_LOCK.lock().map_err(|_| "Save backup unavailable")?;
    let Some(title) = title(name)? else { return Ok("No matching Ludusavi title".into()); };
    // Never let an unanswerable cloud prompt stop the local copy.
    let data = query(&["backup", "--api", "--force", "--ask-downgrade", "--no-cloud-sync", "--full-limit", "10", "--", &title])?;
    if has_errors(&data) {
        record(&title, "The automatic backup reported an error.", "unavailable");
        return Err("Ludusavi reported a backup error.".into());
    }
    if data["overall"]["processedGames"].as_u64().unwrap_or(0) == 0 {
        record(&title, "No save files were found after the game closed.", "not-needed");
        return Ok("No matching save files found".into());
    }
    let changed = data["overall"]["changedGames"]["same"].as_u64().unwrap_or(0) == 0;
    let cloud = sync_cloud(&title);
    let message = if changed { "Backed up after the game closed." } else { "Save unchanged since the last backup." };
    record(&title, message, cloud);
    Ok(format!("{message} {}", cloud_message(cloud)))
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
            let _ = writeln!(log, "{} {name:?}: {message}", now());
        }
    });
}

// ---------------------------------------------------------------- Comparison

#[derive(serde::Deserialize, Default)]
#[serde(rename_all = "camelCase", default)]
struct SaveSources {
    known: bool,
    scan_ok: bool,
    active: Option<i64>,
    local_change: Option<String>,
    versions: Vec<BackupVersion>,
    cloud_status: String,
    cloud_mapping: Option<String>,
    last_event: Option<SaveEvent>,
}

#[derive(serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SaveComparison {
    known: bool,
    active: Option<i64>,
    versions: Vec<BackupVersion>,
    cloud: Option<i64>,
    scan_ok: bool,
    cloud_status: String,
    /// `match`, `differ`, `unknown` or `none`.
    verdict: &'static str,
    message: String,
    last_event: Option<SaveEvent>,
}

#[derive(serde::Deserialize, serde::Serialize, Clone, Debug, PartialEq)]
#[serde(rename_all = "camelCase")]
struct BackupVersion {
    name: String,
    when: String,
    #[serde(default)]
    locked: bool,
    #[serde(default)]
    comment: Option<String>,
}

fn time(value: &str) -> Option<i64> {
    chrono::DateTime::parse_from_rfc3339(value.trim().trim_matches(['"', '\'']))
        .ok().map(|stamp| stamp.timestamp())
}

fn compare(sources: SaveSources, device: &str) -> SaveComparison {
    let mut versions = sources.versions;
    versions.retain(|version| time(&version.when).is_some());
    versions.sort_by_key(|version| std::cmp::Reverse(time(&version.when)));
    let cloud = sources.cloud_mapping.as_deref().and_then(|mapping| mapping.lines()
        .filter_map(|line| time(line.trim().strip_prefix("when:")?)).max());
    let cloud_status = if sources.cloud_status == "ok" && cloud.is_none() {
        "unavailable".into()
    } else { sources.cloud_status };
    let same = sources.local_change.as_deref() == Some("Same");
    let (verdict, message) = match (sources.active, versions.first().and_then(|version| time(&version.when)), cloud) {
        _ if !sources.known => ("none", "Ludusavi has no save locations for this game."),
        _ if !sources.scan_ok => ("unknown", "The active save could not be checked."),
        (None, None, None) => ("none", "No save files have been found for this game yet."),
        (Some(active), _, Some(drive)) if drive > active && !same => ("differ", "The Google Drive backup was created after the save was last modified."),
        (Some(active), _, Some(drive)) if active > drive => ("differ", "The save was modified after the latest Google Drive backup."),
        (_, Some(local), Some(drive)) if local != drive =>
            ("differ", if local > drive { "{device} has a backup that is not on Google Drive yet." } else { "Google Drive has a backup that is not on {device}." }),
        (None, _, Some(_)) => ("differ", "Google Drive has a backup, but no active save file was found."),
        (Some(_), Some(local), Some(drive)) if local == drive && same => ("match", "The active save matches the latest backup, and Google Drive has the same backup."),
        (Some(_), Some(_), _) if same => ("match", "The active save matches the latest backup on {device}."),
        (Some(active), Some(local), _) if active > local => ("differ", "The save was modified after the latest backup."),
        (Some(active), Some(local), _) if local > active => ("differ", "The latest backup differs from the active save."),
        (None, Some(_), _) => ("differ", "A backup exists, but no active save file was found."),
        (Some(_), None, None) => ("differ", "An active save was found, but it has not been backed up yet."),
        _ => ("unknown", "No save or backup could be confirmed."),
    };
    let mut message = message.replace("{device}", device);
    if let Some(first) = message.get(0..1) { message = first.to_uppercase() + &message[1..]; }
    SaveComparison { known: sources.known, active: sources.active, versions, cloud, scan_ok: sources.scan_ok, cloud_status,
        verdict, message, last_event: sources.last_event }
}

fn game_folder(title: &str) -> String {
    title.chars().enumerate().map(|(i, c)| {
        if c.is_control() || "<>:\"/\\|?*".contains(c) || c == '.' && (i == 0 || i + 1 == title.chars().count()) { '_' } else { c }
    }).collect()
}

fn versions_of(history: &Value, title: &str) -> Vec<BackupVersion> {
    history["games"][title]["backups"].as_array().into_iter().flatten()
        .filter_map(|backup| serde_json::from_value(backup.clone()).ok()).collect()
}

fn local_sources(name: &str) -> Result<SaveSources, String> {
    let _guard = BACKUP_LOCK.try_lock().map_err(|_| "A backup is in progress. Check again in a moment.")?;
    let found = read(&["find", "--backup", "--normalized", "--api", "--", name], 8)?;
    let Some(title) = found["games"].as_object().and_then(|games| games.keys().next()) else {
        return Ok(SaveSources { scan_ok: true, cloud_status: "unavailable".into(), ..Default::default() });
    };
    // Config first: `config show` can fail while other Ludusavi processes run,
    // which would silently report Drive as unreachable.
    let config = read(&["config", "show", "--api"], 8);
    let (scan, history) = std::thread::scope(|scope| {
        let scan = scope.spawn(|| read(&["backup", "--preview", "--api", "--", title], 12));
        let history = scope.spawn(|| read(&["backups", "--api", "--", title], 9));
        (scan.join().unwrap(), history.join().unwrap())
    });
    let active = scan.as_ref().ok().and_then(|data| data["games"][title]["files"].as_object())
        .and_then(|files| files.iter().filter(|(_, info)| info["ignored"] != true && info["change"] != "Removed")
            .filter_map(|(file, _)| std::fs::metadata(file).ok()?.modified().ok()?
                .duration_since(std::time::UNIX_EPOCH).ok().map(|duration| duration.as_secs() as i64)).max());
    let local_change = scan.as_ref().ok().and_then(|data| data["games"][title]["change"].as_str()).map(str::to_owned);
    let history = history.unwrap_or_default();
    let versions = versions_of(&history, title);
    let folder = history["games"][title]["backupPath"].as_str()
        .and_then(|path| Path::new(path).file_name()?.to_str()).map(str::to_owned).unwrap_or_else(|| game_folder(title));
    let mut sources = SaveSources { known: true, scan_ok: scan.is_ok(), active, local_change, versions,
        cloud_status: "unavailable".into(), cloud_mapping: None, last_event: last_event(title) };
    if let Ok(config) = config {
        if config["cloud"]["synchronize"] != true { sources.cloud_status = "off".into(); }
        else if let (Some(remote), Some(path)) = (config["cloud"]["remote"]["Custom"]["id"].as_str(), config["cloud"]["path"].as_str()) {
            if !path.is_empty() && !folder.is_empty() && folder != "." && folder != ".." {
                let mut cmd = Command::new(tools()?.join("rclone.exe"));
                cmd.stdin(Stdio::null()).args(["cat", &format!("{remote}:{path}/{folder}/mapping.yaml"),
                    "--retries", "1", "--low-level-retries", "2", "--timeout", "7s"]);
                if let Ok(result) = super::streaming::bounded_output_timeout(cmd, None, 11) {
                    match result.status.code() {
                        Some(0) => {
                            sources.cloud_status = "ok".into();
                            sources.cloud_mapping = String::from_utf8(result.stdout).ok();
                        }
                        Some(3 | 4) => sources.cloud_status = "missing".into(),
                        _ => {}
                    }
                }
            }
        }
    }
    Ok(sources)
}

fn lutris_request(id: &str, action: Option<&str>, version: Option<&str>) -> Result<Value, String> {
    let (host, game) = super::lutris::save_target(id)?;
    let mut command = super::streaming::host_command(&host)?;
    let script = STANDARD.encode(include_bytes!("lutris_save_check.py"));
    command.arg(format!("python3 -c 'import base64; exec(base64.b64decode(\"{script}\"))'"));
    let input = serde_json::to_vec(&serde_json::json!({"name": game.name, "id": game.id, "action": action, "version": version})).map_err(|error| error.to_string())?;
    // Bosgame stops itself at 420 s, before any restore it could not finish.
    let result = super::streaming::bounded_output_timeout(command, Some(&input), if action.is_some() { 480 } else { 35 })?;
    if !result.status.success() { return Err("Bosgame could not check this game's saves.".into()); }
    let data: Value = serde_json::from_slice(&result.stdout).map_err(|_| "Bosgame returned an invalid save report.")?;
    if let Some(error) = data["error"].as_str() { return Err(error.to_owned()); }
    Ok(data)
}

#[tauri::command]
pub async fn compare_game_saves(id: String, name: String, app_type: String, source: String, path: String) -> Result<SaveComparison, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let sources = if source == "lutris" { serde_json::from_value(lutris_request(&id, None, None)?).map_err(|_| "Invalid Bosgame save report.")? }
            else if eligible(&app_type, &source, &path) { local_sources(&name)? }
            else { return Err("Save checks are unavailable for this game.".into()); };
        Ok(compare(sources, if source == "lutris" { "Bosgame" } else { "this PC" }))
    }).await.map_err(|error| error.to_string())?
}

// ---------------------------------------------------------------- Explicit actions

#[derive(serde::Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SaveAction {
    id: String,
    name: String,
    app_type: String,
    source: String,
    path: String,
    action: String,
    version: Option<String>,
}

/// Lock the newest backup as the pre-restore copy and release older ones beyond the limit.
fn protect_latest(title: &str) -> Result<(), String> {
    let mut versions = versions_of(&read(&["backups", "--api", "--", title], 10)?, title);
    versions.sort_by_key(|version| std::cmp::Reverse(time(&version.when)));
    let latest = versions.first().ok_or("The current save could not be protected.")?;
    let locked = read_status(&["backups", "edit", "--lock", "--comment", CHECKPOINT, "--backup", &latest.name, "--", title])?;
    if !locked { return Err("The current save could not be protected.".into()); }
    for old in versions.iter().skip(1).filter(|version| version.locked && version.comment.as_deref() == Some(CHECKPOINT)).skip(CHECKPOINTS_KEPT - 1) {
        let _ = read_status(&["backups", "edit", "--unlock", "--backup", &old.name, "--", title]);
    }
    Ok(())
}

fn read_status(args: &[&str]) -> Result<bool, String> {
    let mut cmd = command()?;
    cmd.arg("--no-manifest-update").args(args);
    Ok(super::streaming::bounded_output_timeout(cmd, None, 15)?.status.success())
}

fn manage_local(request: &SaveAction) -> Result<Value, String> {
    let _guard = BACKUP_LOCK.try_lock().map_err(|_| "A backup is in progress. Try again in a moment.")?;
    let Some(title) = quick_title(&request.name)? else { return Err("Ludusavi has no save locations for this game.".into()); };
    let ensure_closed = || {
        let exact_exe = Path::new(&request.path).file_name().and_then(|name| name.to_str())
            .is_some_and(|name| name.to_ascii_lowercase().ends_with(".exe") && super::is_process_running(name));
        if exact_exe || super::best_launch_window(&request.name, &request.path, &request.source).is_some_and(|(_, score)| score >= 45) {
            Err("Close the game before changing its saves.".to_owned())
        } else { Ok(()) }
    };
    match request.action.as_str() {
        "sync" => {
            let cloud = sync_cloud(&title);
            return Ok(serde_json::json!({"message": cloud_message(cloud), "cloud": cloud}));
        }
        "backup" => {
            ensure_closed()?;
            let result = query(&["backup", "--api", "--force", "--no-cloud-sync", "--full-limit", "10", "--", &title])?;
            if has_errors(&result) { return Err("Ludusavi reported a backup error.".into()); }
            if result["overall"]["processedGames"].as_u64().unwrap_or(0) == 0 {
                return Err("No save files were found for this game on this PC.".into());
            }
            let unchanged = result["overall"]["changedGames"]["same"].as_u64().unwrap_or(0) > 0;
            let cloud = sync_cloud(&title);
            let message = if unchanged { "The save has not changed since the latest backup." } else { "Backed up on this PC." };
            record(&title, message, cloud);
            return Ok(serde_json::json!({"message": message, "cloud": cloud, "cloudMessage": cloud_message(cloud)}));
        }
        _ => {}
    }
    let version = request.version.as_deref().ok_or("Choose a backup version.")?;
    let history = read(&["backups", "--api", "--", &title], 10)?;
    let Some(chosen) = versions_of(&history, &title).into_iter().find(|item| item.name == version) else {
        return Err("This backup version is no longer available.".into());
    };
    let preview = query(&["restore", "--api", "--preview", "--no-cloud-sync", "--backup", version, "--", &title])?;
    if preview["overall"]["processedGames"].as_u64().unwrap_or(0) == 0 {
        return Err("This version has no restorable save data.".into());
    }
    let entry = &preview["games"][&title];
    if request.action == "preview" {
        let count = |key: &str, change: &str| entry[key].as_object().map_or(0, |items| items.values().filter(|item| item["change"] == change).count());
        return Ok(serde_json::json!({
            "created": chosen.when,
            "replace": count("files", "Different") + count("registry", "Different"),
            "add": count("files", "New") + count("registry", "New"),
            "same": count("files", "Same") + count("registry", "Same"),
        }));
    }
    if request.action != "restore" { return Err("Unknown save action.".into()); }
    ensure_closed()?;
    // No pruning here: the chosen version may be the oldest of the ten.
    let checkpoint = query(&["backup", "--api", "--force", "--no-cloud-sync", "--full-limit", "255", "--", &title])?;
    if has_errors(&checkpoint) { return Err("The current save could not be protected, so nothing was restored.".into()); }
    if checkpoint["overall"]["processedGames"].as_u64().unwrap_or(0) > 0 { protect_latest(&title)?; }
    ensure_closed()?;
    let restored = query(&["restore", "--api", "--force", "--no-cloud-sync", "--backup", version, "--", &title])?;
    if has_errors(&restored) {
        return Err("The restore did not finish. Your previous save is kept as “Before restore”.".into());
    }
    let cloud = sync_cloud(&title);
    record(&title, "Restored an earlier version.", cloud);
    Ok(serde_json::json!({"message": "Version restored on this PC. Your previous save is kept as “Before restore”.", "cloud": cloud}))
}

#[tauri::command]
pub async fn manage_game_save(request: SaveAction) -> Result<Value, String> {
    if !matches!(request.action.as_str(), "backup" | "sync" | "preview" | "restore") {
        return Err("Unknown save action.".into());
    }
    tauri::async_runtime::spawn_blocking(move || {
        if request.source == "lutris" {
            lutris_request(&request.id, Some(&request.action), request.version.as_deref())
        } else if eligible(&request.app_type, &request.source, &request.path) {
            manage_local(&request)
        } else {
            Err("Save actions are unavailable for this game.".into())
        }
    }).await.map_err(|error| error.to_string())?
}

// ---------------------------------------------------------------- Pre-launch check

#[derive(serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SaveCheck {
    message: &'static str,
    review: bool,
}

fn check(name: &str) -> SaveCheck {
    let result = (|| -> Result<_, String> {
        let _guard = BACKUP_LOCK.try_lock().map_err(|_| "Backup in progress")?;
        let Some(title) = quick_title(name)? else {
            return Ok(SaveCheck { message: "No save locations are known for this title.", review: false });
        };
        if cloud_enabled() {
            match cloud_plan(&title)? {
                SyncPlan::Download => return Ok(SaveCheck { message: "Google Drive has a backup that is not on this device. Review it before playing, or play with this device’s save.", review: true }),
                SyncPlan::Conflict => return Ok(SaveCheck { message: "Google Drive and this device have different backups. Review them before playing, or play with this device’s save.", review: true }),
                SyncPlan::Synced | SyncPlan::Upload => {}
            }
        }
        let local = read(&["restore", "--preview", "--api", "--", &title], 12)?;
        let differs = local["games"].as_object().is_some_and(|games| games.values().any(|game| {
            ["files", "registry"].iter().any(|key| game[key].as_object().is_some_and(|items| items.values().any(|entry| {
                matches!(entry["change"].as_str(), Some("New" | "Different"))
            })))
        }));
        Ok(if differs {
            SaveCheck { message: "The latest backup differs from the active save. Review it, or play with this device’s save.", review: true }
        } else {
            SaveCheck { message: "Saves match the latest backup.", review: false }
        })
    })();
    result.unwrap_or(SaveCheck { message: "Save check unavailable. You can play with this device’s save.", review: true })
}

#[tauri::command]
pub async fn check_game_saves(name: String, app_type: String, source: String, path: String) -> Result<SaveCheck, String> {
    if !eligible(&app_type, &source, &path) { return Err("Save check is unavailable for this launch.".into()); }
    tauri::async_runtime::spawn_blocking(move || check(&name)).await.map_err(|error| error.to_string())
}

#[cfg(test)]
mod tests {
    use super::{compare, eligible, plan_sync, quick_plan, BackupVersion, SaveSources, SyncPlan};
    use serde_json::json;

    #[test]
    fn backup_targets_are_non_steam_local_games() {
        assert!(eligible("game", "gog", r"C:\Games\Game.exe"));
        assert!(eligible("game", "desktop", r"C:\Games\Game.lnk"));
        assert!(!eligible("game", "steam", "steam://rungameid/12"));
        assert!(!eligible("game", "lutris", "lutris://bosgame/1"));
        assert!(!eligible("app", "desktop", r"C:\Apps\App.exe"));
    }

    // Previews below are the shapes Ludusavi 0.31.0 produced in an isolated repository.
    #[test]
    fn sync_direction_never_discards_a_version() {
        let none = json!({});
        assert_eq!(plan_sync(&none, &none), SyncPlan::Synced);
        let local_only = (json!({"G/backup-2/s.sav": {"change": "Different"}, "G/mapping.yaml": {"change": "Different"}}),
            json!({"G/backup-2/s.sav": {"change": "Removed"}, "G/mapping.yaml": {"change": "Different"}}));
        assert_eq!(plan_sync(&local_only.0, &local_only.1), SyncPlan::Upload);
        let drive_only = (json!({"G/backup-9/o.sav": {"change": "Removed"}, "G/mapping.yaml": {"change": "Different"}}),
            json!({"G/backup-9/o.sav": {"change": "Different"}, "G/mapping.yaml": {"change": "Different"}}));
        assert_eq!(plan_sync(&drive_only.0, &drive_only.1), SyncPlan::Download);
        let both = (json!({"G/backup-2/s.sav": {"change": "Different"}, "G/backup-9/o.sav": {"change": "Removed"}}),
            json!({"G/backup-2/s.sav": {"change": "Removed"}, "G/backup-9/o.sav": {"change": "Different"}}));
        assert_eq!(plan_sync(&both.0, &both.1), SyncPlan::Conflict);
        let same_file = (json!({"G/backup-2/s.sav": {"change": "Different"}}), json!({"G/backup-2/s.sav": {"change": "Different"}}));
        assert_eq!(plan_sync(&same_file.0, &same_file.1), SyncPlan::Conflict);
        let mapping_only = json!({"G/mapping.yaml": {"change": "Different"}});
        assert_eq!(plan_sync(&mapping_only, &mapping_only), SyncPlan::Upload);
        // The single-preview shortcut must agree, and defer whenever it is ambiguous.
        assert_eq!(quick_plan(&none), Some(SyncPlan::Synced));
        assert_eq!(quick_plan(&mapping_only), Some(SyncPlan::Upload));
        assert_eq!(quick_plan(&drive_only.0), Some(SyncPlan::Download));
        assert_eq!(quick_plan(&local_only.0), None);
        assert_eq!(quick_plan(&both.0), None);
    }

    #[test]
    fn comparison_labels_are_explicit() {
        let version = |when: &str| BackupVersion { name: when.into(), when: when.into(), locked: false, comment: None };
        let matched = compare(SaveSources { known: true, scan_ok: true, active: Some(1_700_000_000), local_change: Some("Same".into()),
            versions: vec![version("2023-11-14T22:20:00Z"), version("bad")], cloud_status: "ok".into(),
            cloud_mapping: Some("  when: \"2023-11-14T22:20:00Z\"".into()), last_event: None }, "Bosgame");
        let local_only = compare(SaveSources { known: true, scan_ok: true, active: Some(1_700_000_000), local_change: Some("Same".into()),
            versions: vec![version("2023-11-14T22:20:00Z")], cloud_status: "missing".into(), ..Default::default() }, "Bosgame");
        assert_eq!(local_only.message, "The active save matches the latest backup on Bosgame.");
        let ahead = compare(SaveSources { known: true, scan_ok: true, active: Some(1_700_000_000), local_change: Some("Same".into()),
            versions: vec![version("2023-11-14T23:00:00Z")], cloud_status: "ok".into(),
            cloud_mapping: Some("when: \"2023-11-14T22:20:00Z\"".into()), ..Default::default() }, "this PC");
        assert_eq!(ahead.message, "This PC has a backup that is not on Google Drive yet.");
        assert_eq!((matched.verdict, matched.versions.len()), ("match", 1));
        let empty = compare(SaveSources { known: true, scan_ok: true, ..Default::default() }, "this PC");
        assert_eq!(empty.verdict, "none");
        let unknown = compare(SaveSources { known: false, scan_ok: true, ..Default::default() }, "this PC");
        assert_eq!(unknown.verdict, "none");
        let unbacked = compare(SaveSources { known: true, scan_ok: true, active: Some(5), ..Default::default() }, "this PC");
        assert_eq!(unbacked.verdict, "differ");
    }
}

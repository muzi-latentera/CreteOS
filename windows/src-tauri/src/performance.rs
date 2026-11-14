//! Per-game power and fan profiles.
//!
//! On OneXPlayer devices OneXConsole already applies power limits (RyzenAdj,
//! re-applied by its own service) and fan curves, and it stores per-game
//! presets bound to process names in `%APPDATA%\gamerzone\db\core.db`.
//! CreteOS edits those presets instead of running a competing RyzenAdj loop,
//! so the two never fight. Every write is preceded by a dated copy of the
//! database. OneXConsole loads changed presets when it next starts; CreteOS
//! can restart it on request.
//!
//! Fan modes, from OneXConsole's own code: 0 = automatic (firmware),
//! 1 = custom curve A (`setting.fanPreset1`), 2 = custom curve B (`fanPreset2`).
//! Curves are five `[°C, duty 0–1]` points at 45/55/65/75/85 °C.
use serde_json::{Map, Value};
use std::{collections::BTreeMap, ffi::{c_char, c_int, c_void, CStr, CString}, path::PathBuf};

/// Conservative limits for the G1 (Ryzen AI 9 HX 370). OneXConsole's own
/// defaults sit at 6–18 W; its device table lists no G1 maximum.
pub const MIN_TDP: i64 = 4;
pub const MAX_TDP: i64 = 30;
const FAN_TEMPS: [i64; 5] = [45, 55, 65, 75, 85];

// ---------------------------------------------------------------- winsqlite3

#[link(name = "winsqlite3")]
extern "system" {
    fn sqlite3_open_v2(filename: *const c_char, db: *mut *mut c_void, flags: c_int, vfs: *const c_char) -> c_int;
    fn sqlite3_close(db: *mut c_void) -> c_int;
    fn sqlite3_busy_timeout(db: *mut c_void, ms: c_int) -> c_int;
    fn sqlite3_prepare_v2(db: *mut c_void, sql: *const c_char, bytes: c_int, stmt: *mut *mut c_void, tail: *mut *const c_char) -> c_int;
    fn sqlite3_step(stmt: *mut c_void) -> c_int;
    fn sqlite3_finalize(stmt: *mut c_void) -> c_int;
    fn sqlite3_column_count(stmt: *mut c_void) -> c_int;
    fn sqlite3_column_name(stmt: *mut c_void, index: c_int) -> *const c_char;
    fn sqlite3_column_type(stmt: *mut c_void, index: c_int) -> c_int;
    fn sqlite3_column_int64(stmt: *mut c_void, index: c_int) -> i64;
    fn sqlite3_column_double(stmt: *mut c_void, index: c_int) -> f64;
    fn sqlite3_column_text(stmt: *mut c_void, index: c_int) -> *const u8;
    fn sqlite3_bind_int64(stmt: *mut c_void, index: c_int, value: i64) -> c_int;
    fn sqlite3_bind_text(stmt: *mut c_void, index: c_int, text: *const c_char, bytes: c_int, destructor: isize) -> c_int;
    fn sqlite3_bind_null(stmt: *mut c_void, index: c_int) -> c_int;
    fn sqlite3_errmsg(db: *mut c_void) -> *const c_char;
    fn sqlite3_last_insert_rowid(db: *mut c_void) -> i64;
}

const SQLITE_OK: c_int = 0;
const SQLITE_ROW: c_int = 100;
const SQLITE_DONE: c_int = 101;
const OPEN_READONLY: c_int = 0x1;
const OPEN_READWRITE: c_int = 0x2;
const SQLITE_TRANSIENT: isize = -1;

#[derive(Clone, Debug)]
#[allow(dead_code)]
pub enum Param { Int(i64), Text(String), Null }

struct Db(*mut c_void);

impl Drop for Db {
    fn drop(&mut self) { unsafe { sqlite3_close(self.0); } }
}

impl Db {
    fn open(path: &std::path::Path, write: bool) -> Result<Db, String> {
        let name = CString::new(path.to_string_lossy().as_bytes()).map_err(|_| "Invalid database path")?;
        let mut handle = std::ptr::null_mut();
        let code = unsafe { sqlite3_open_v2(name.as_ptr(), &mut handle, if write { OPEN_READWRITE } else { OPEN_READONLY }, std::ptr::null()) };
        let db = Db(handle);
        if code != SQLITE_OK { return Err(db.error()); }
        unsafe { sqlite3_busy_timeout(handle, 3000); }
        Ok(db)
    }

    fn error(&self) -> String {
        if self.0.is_null() { return "Could not open OneXConsole's settings.".into(); }
        unsafe { CStr::from_ptr(sqlite3_errmsg(self.0)) }.to_string_lossy().into_owned()
    }

    fn query(&self, sql: &str, params: &[Param]) -> Result<Vec<Map<String, Value>>, String> {
        let text = CString::new(sql).map_err(|_| "Invalid SQL")?;
        let mut stmt = std::ptr::null_mut();
        if unsafe { sqlite3_prepare_v2(self.0, text.as_ptr(), -1, &mut stmt, std::ptr::null_mut()) } != SQLITE_OK {
            return Err(self.error());
        }
        let result = (|| {
            let mut keep = Vec::new();
            for (index, param) in params.iter().enumerate() {
                let position = index as c_int + 1;
                let code = match param {
                    Param::Int(value) => unsafe { sqlite3_bind_int64(stmt, position, *value) },
                    Param::Null => unsafe { sqlite3_bind_null(stmt, position) },
                    Param::Text(value) => {
                        let owned = CString::new(value.as_str()).map_err(|_| "Invalid text")?;
                        let code = unsafe { sqlite3_bind_text(stmt, position, owned.as_ptr(), -1, SQLITE_TRANSIENT) };
                        keep.push(owned);
                        code
                    }
                };
                if code != SQLITE_OK { return Err(self.error()); }
            }
            let mut rows = Vec::new();
            loop {
                match unsafe { sqlite3_step(stmt) } {
                    SQLITE_ROW => {
                        let mut row = Map::new();
                        for column in 0..unsafe { sqlite3_column_count(stmt) } {
                            let name = unsafe { CStr::from_ptr(sqlite3_column_name(stmt, column)) }.to_string_lossy().into_owned();
                            let value = match unsafe { sqlite3_column_type(stmt, column) } {
                                1 => Value::from(unsafe { sqlite3_column_int64(stmt, column) }),
                                2 => Value::from(unsafe { sqlite3_column_double(stmt, column) }),
                                3 => {
                                    let raw = unsafe { sqlite3_column_text(stmt, column) };
                                    if raw.is_null() { Value::Null } else { Value::from(unsafe { CStr::from_ptr(raw.cast()) }.to_string_lossy().into_owned()) }
                                }
                                _ => Value::Null,
                            };
                            row.insert(name, value);
                        }
                        rows.push(row);
                    }
                    SQLITE_DONE => return Ok(rows),
                    _ => return Err(self.error()),
                }
            }
        })();
        unsafe { sqlite3_finalize(stmt); }
        result
    }

    fn execute(&self, sql: &str, params: &[Param]) -> Result<(), String> { self.query(sql, params).map(|_| ()) }
    fn last_id(&self) -> i64 { unsafe { sqlite3_last_insert_rowid(self.0) } }
}

// ---------------------------------------------------------------- Model

#[derive(serde::Serialize, serde::Deserialize, Clone, Debug, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct Profile {
    /// Watts on battery and on the charger.
    pub tdp_battery: i64,
    pub tdp_charger: i64,
    /// 0 automatic, 1 curve A, 2 curve B.
    pub fan_mode: i64,
    /// 0 = no frame limit (RTSS).
    pub frame_cap: i64,
}

#[derive(serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PerformanceStatus {
    backend: &'static str,
    running: bool,
    default_profile: Option<Profile>,
    fan_curves: Vec<Vec<[f64; 2]>>,
    min_tdp: i64,
    max_tdp: i64,
    games: Vec<GameBinding>,
    message: String,
}

#[derive(serde::Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct GameBinding { process: String, title: String, profile: Profile }

fn database() -> Option<PathBuf> {
    let path = dirs::config_dir()?.join("gamerzone/db/core.db");
    path.is_file().then_some(path)
}

fn onexconsole_running() -> bool { super::is_process_running("OneXConsole.exe") }

fn int(row: &Map<String, Value>, key: &str) -> i64 { row.get(key).and_then(Value::as_i64).unwrap_or(0) }

fn profile_from(row: &Map<String, Value>) -> Profile {
    let separate = int(row, "tdpPluginSep") != 0;
    let battery = int(row, "tdp");
    Profile {
        tdp_battery: battery,
        tdp_charger: if separate { int(row, "tdpPlugin") } else { battery },
        fan_mode: if int(row, "fanTakeEffect") != 0 { int(row, "fanMode").clamp(0, 2) } else { 0 },
        frame_cap: if int(row, "rtssTakeEffect") != 0 { int(row, "rtssFrameValue").max(0) } else { 0 },
    }
}

/// A process name as OneXConsole stores it: the executable's file name,
/// lower-case, without `.exe`.
pub fn process_key(exe: &str) -> Option<String> {
    let name = std::path::Path::new(exe.trim()).file_name()?.to_str()?.to_ascii_lowercase();
    let name = name.strip_suffix(".exe").unwrap_or(&name).to_owned();
    (!name.is_empty() && name.chars().all(|c| c.is_ascii_alphanumeric() || "-_. ".contains(c))).then_some(name)
}

pub fn validate(profile: &Profile) -> Result<(), String> {
    for watts in [profile.tdp_battery, profile.tdp_charger] {
        if !(MIN_TDP..=MAX_TDP).contains(&watts) { return Err(format!("Power must be between {MIN_TDP} and {MAX_TDP} W.")); }
    }
    if !(0..=2).contains(&profile.fan_mode) { return Err("Unknown fan mode.".into()); }
    if !(profile.frame_cap == 0 || (20..=240).contains(&profile.frame_cap)) { return Err("Frame limit must be off or 20–240 FPS.".into()); }
    Ok(())
}

fn parse_curve(raw: Option<&Value>) -> Vec<[f64; 2]> {
    let fallback = vec![[45.0, 0.35], [55.0, 0.45], [65.0, 0.6], [75.0, 0.75], [85.0, 0.85]];
    raw.and_then(Value::as_str).and_then(|text| serde_json::from_str::<Vec<[f64; 2]>>(text).ok())
        .filter(|curve| curve.len() == 5).unwrap_or(fallback)
}

fn backup(path: &std::path::Path) -> Result<PathBuf, String> {
    let folder = super::liftoff_dir().join("onexconsole-backups");
    std::fs::create_dir_all(&folder).map_err(|error| error.to_string())?;
    let stamp = chrono::DateTime::<chrono::Utc>::from(std::time::SystemTime::now()).format("%Y%m%d-%H%M%S").to_string();
    let target = folder.join(format!("core-{stamp}.db"));
    std::fs::copy(path, &target).map_err(|_| "Could not back up OneXConsole's settings, so nothing was changed.")?;
    // Keep the 20 newest copies.
    let mut copies: Vec<_> = std::fs::read_dir(&folder).map_err(|error| error.to_string())?.flatten()
        .filter(|entry| entry.file_name().to_string_lossy().starts_with("core-")).map(|entry| entry.path()).collect();
    copies.sort();
    while copies.len() > 20 { let _ = std::fs::remove_file(copies.remove(0)); }
    Ok(target)
}

pub fn status() -> PerformanceStatus {
    let Some(path) = database() else {
        return PerformanceStatus { backend: "none", running: false, default_profile: None, fan_curves: vec![], min_tdp: MIN_TDP, max_tdp: MAX_TDP, games: vec![],
            message: "No supported power tool was found. On OneXPlayer devices, install OneXConsole.".into() };
    };
    let read = || -> Result<PerformanceStatus, String> {
        let db = Db::open(&path, false)?;
        let presets = db.query("select * from game_setting_preset", &[])?;
        let default_profile = presets.iter().find(|row| int(row, "isDefault") != 0).map(profile_from);
        let by_id: BTreeMap<i64, &Map<String, Value>> = presets.iter().map(|row| (int(row, "id"), row)).collect();
        let games = db.query("select presetId, processName from game_setting_preset_process", &[])?.iter()
            .filter(|row| row.get("processName").and_then(Value::as_str) != Some("__virtual_desktop"))
            .filter_map(|row| {
                let preset = by_id.get(&int(row, "presetId"))?;
                Some(GameBinding { process: row.get("processName")?.as_str()?.to_owned(),
                    title: preset.get("title").and_then(Value::as_str).unwrap_or_default().to_owned(), profile: profile_from(preset) })
            }).collect();
        let setting = db.query("select fanPreset1, fanPreset2 from setting limit 1", &[])?;
        let fan_curves = setting.first().map(|row| vec![parse_curve(row.get("fanPreset1")), parse_curve(row.get("fanPreset2"))]).unwrap_or_default();
        Ok(PerformanceStatus { backend: "onexconsole", running: onexconsole_running(), default_profile, fan_curves,
            min_tdp: MIN_TDP, max_tdp: MAX_TDP, games, message: String::new() })
    };
    read().unwrap_or_else(|error| PerformanceStatus { backend: "onexconsole", running: onexconsole_running(), default_profile: None,
        fan_curves: vec![], min_tdp: MIN_TDP, max_tdp: MAX_TDP, games: vec![], message: format!("OneXConsole's settings could not be read: {error}") })
}

fn apply_profile_sql(db: &Db, preset_id: i64, title: &str, profile: &Profile) -> Result<(), String> {
    let separate = profile.tdp_battery != profile.tdp_charger;
    db.execute("update game_setting_preset set title=?1, tdpTakeEffect=1, tdpPluginSep=?2, tdp=?3, tdpPlugin=?4, \
        fanTakeEffect=?5, fanPluginSep=0, fanMode=?6, fanModePlugin=?6, rtssTakeEffect=?7, rtssFrameValue=?8 where id=?9 and isDefault=0",
        &[Param::Text(title.to_owned()), Param::Int(separate as i64), Param::Int(profile.tdp_battery), Param::Int(profile.tdp_charger),
          Param::Int((profile.fan_mode != 0) as i64), Param::Int(profile.fan_mode), Param::Int((profile.frame_cap > 0) as i64),
          Param::Int(profile.frame_cap), Param::Int(preset_id)])
}

/// Create, update or (with `None`) remove the preset bound to one process.
pub fn set_game_profile_at(path: &std::path::Path, process: &str, title: &str, profile: Option<&Profile>) -> Result<(), String> {
    if let Some(profile) = profile { validate(profile)?; }
    if process == "__virtual_desktop" { return Err("The desktop preset is managed in OneXConsole.".into()); }
    let db = Db::open(path, true)?;
    db.execute("begin immediate", &[])?;
    let outcome = (|| {
        let bound = db.query("select id, presetId from game_setting_preset_process where processName=?1", &[Param::Text(process.to_owned())])?;
        let preset_id = bound.first().map(|row| int(row, "presetId"));
        let shared = match preset_id {
            Some(id) => int(&db.query("select count(*) as n from game_setting_preset_process where presetId=?1", &[Param::Int(id)])?[0], "n") > 1,
            None => false,
        };
        let is_default = match preset_id {
            Some(id) => db.query("select isDefault from game_setting_preset where id=?1", &[Param::Int(id)])?.first().map(|row| int(row, "isDefault") != 0).unwrap_or(true),
            None => false,
        };
        match profile {
            None => {
                db.execute("delete from game_setting_preset_process where processName=?1", &[Param::Text(process.to_owned())])?;
                if let (Some(id), false, false) = (preset_id, shared, is_default) {
                    db.execute("delete from game_setting_preset where id=?1 and isDefault=0", &[Param::Int(id)])?;
                }
            }
            Some(profile) => {
                let own = preset_id.filter(|_| !shared && !is_default);
                let id = match own {
                    Some(id) => id,
                    None => {
                        let columns: Vec<String> = db.query("pragma table_info('game_setting_preset')", &[])?.iter()
                            .filter_map(|row| row.get("name").and_then(Value::as_str).map(str::to_owned))
                            .filter(|name| name != "id").collect();
                        let list = columns.iter().map(|name| format!("\"{name}\"")).collect::<Vec<_>>().join(",");
                        db.execute(&format!("insert into game_setting_preset ({list}) select {list} from game_setting_preset where isDefault=1 order by id limit 1"), &[])?;
                        let id = db.last_id();
                        db.execute("update game_setting_preset set isDefault=0, titleLocaleKey=null, showIndex=(select coalesce(max(showIndex),0)+1 from game_setting_preset) where id=?1", &[Param::Int(id)])?;
                        db.execute("delete from game_setting_preset_process where processName=?1", &[Param::Text(process.to_owned())])?;
                        db.execute("insert into game_setting_preset_process (createTime, presetId, processName) values (null, ?1, ?2)",
                            &[Param::Int(id), Param::Text(process.to_owned())])?;
                        id
                    }
                };
                apply_profile_sql(&db, id, title, profile)?;
            }
        }
        let check = db.query("pragma quick_check", &[])?;
        if check.first().and_then(|row| row.values().next()).and_then(Value::as_str) != Some("ok") { return Err("OneXConsole's settings failed a check.".to_owned()); }
        Ok(())
    })();
    match outcome {
        Ok(()) => db.execute("commit", &[]),
        Err(error) => { let _ = db.execute("rollback", &[]); Err(error) }
    }
}

pub fn set_fan_curve_at(path: &std::path::Path, which: usize, duties: &[f64]) -> Result<(), String> {
    if which > 1 || duties.len() != 5 || duties.iter().any(|duty| !(0.2..=1.0).contains(duty)) || duties.windows(2).any(|pair| pair[1] < pair[0]) {
        return Err("A fan curve needs five rising speeds between 20 and 100 %.".into());
    }
    let curve: Vec<[f64; 2]> = FAN_TEMPS.iter().zip(duties).map(|(temp, duty)| [*temp as f64, (duty * 100.0).round() / 100.0]).collect();
    let text = serde_json::to_string(&curve).map_err(|error| error.to_string())?;
    let db = Db::open(path, true)?;
    db.execute(if which == 0 { "update setting set fanPreset1=?1" } else { "update setting set fanPreset2=?1" }, &[Param::Text(text)])
}

// ---------------------------------------------------------------- Game processes

fn processes_path() -> PathBuf { super::liftoff_dir().join("game_processes.json") }

/// Remember which executable a game launched as, keyed by its launch path, so a
/// per-game preset binds to the process OneXConsole actually sees.
pub fn remember_game_process(launch_path: &str, exe_path: &str) {
    let Some(process) = process_key(exe_path) else { return; };
    let path = processes_path();
    let mut map: BTreeMap<String, String> = std::fs::read(&path).ok().and_then(|bytes| serde_json::from_slice(&bytes).ok()).unwrap_or_default();
    if map.get(launch_path) == Some(&process) { return; }
    map.insert(launch_path.to_owned(), process);
    if let Ok(bytes) = serde_json::to_vec_pretty(&map) {
        let temp = path.with_extension(format!("json.{}.next", std::process::id()));
        let _ = std::fs::write(&temp, bytes).and_then(|_| std::fs::rename(temp, &path));
    }
}

fn known_process(launch_path: &str) -> Option<String> {
    let map: BTreeMap<String, String> = serde_json::from_slice(&std::fs::read(processes_path()).ok()?).ok()?;
    map.get(launch_path).cloned()
}

/// Executables in a game's install folder, largest first, as choices when the
/// game has not been launched from CreteOS yet.
fn install_candidates(install_dir: &str) -> Vec<String> {
    let mut found = Vec::new();
    let mut stack = vec![(PathBuf::from(install_dir), 0)];
    while let Some((dir, depth)) = stack.pop() {
        let Ok(entries) = std::fs::read_dir(&dir) else { continue; };
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_dir() && depth < 3 { stack.push((path, depth + 1)); continue; }
            let name = entry.file_name().to_string_lossy().to_ascii_lowercase();
            if !name.ends_with(".exe") || ["unins", "crash", "redist", "setup", "vc_", "dxsetup", "ue4prereq", "launcher", "helper", "report"].iter().any(|skip| name.contains(skip)) { continue; }
            if let (Some(key), Ok(meta)) = (process_key(&name), entry.metadata()) { found.push((meta.len(), key)); }
        }
    }
    found.sort_by(|a, b| b.0.cmp(&a.0));
    let mut names: Vec<String> = found.into_iter().map(|(_, key)| key).collect();
    names.dedup();
    names.truncate(6);
    names
}

// ---------------------------------------------------------------- Commands

#[tauri::command]
pub async fn get_performance_status() -> PerformanceStatus {
    tauri::async_runtime::spawn_blocking(status).await.unwrap_or_else(|_| status())
}

#[derive(serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct GamePerformance { process: Option<String>, candidates: Vec<String>, profile: Option<Profile>, default_profile: Option<Profile>, backend: &'static str, running: bool }

#[tauri::command]
pub async fn get_game_performance(launch_path: String, install_dir: Option<String>) -> GamePerformance {
    tauri::async_runtime::spawn_blocking(move || {
        let status = status();
        let process = known_process(&launch_path);
        let candidates = if process.is_none() { install_dir.as_deref().map(install_candidates).unwrap_or_default() } else { vec![] };
        let profile = process.as_ref().and_then(|name| status.games.iter().find(|game| &game.process == name)).map(|game| game.profile.clone());
        GamePerformance { process, candidates, profile, default_profile: status.default_profile, backend: status.backend, running: status.running }
    }).await.unwrap_or(GamePerformance { process: None, candidates: vec![], profile: None, default_profile: None, backend: "none", running: false })
}

#[tauri::command]
pub async fn set_game_performance(launch_path: String, process: String, title: String, profile: Option<Profile>) -> Result<Value, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let path = database().ok_or("OneXConsole is not installed.")?;
        let process = process_key(&process).ok_or("Choose the game's program first.")?;
        let backup = backup(&path)?;
        set_game_profile_at(&path, &process, title.trim(), profile.as_ref())?;
        remember_game_process(&launch_path, &format!("{process}.exe"));
        Ok(serde_json::json!({ "process": process, "backup": backup, "appliesOnRestart": onexconsole_running() }))
    }).await.map_err(|error| error.to_string())?
}

#[tauri::command]
pub async fn set_fan_curve(curve: usize, duties: Vec<f64>) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || {
        let path = database().ok_or("OneXConsole is not installed.")?;
        backup(&path)?;
        set_fan_curve_at(&path, curve, &duties)
    }).await.map_err(|error| error.to_string())?
}

/// OneXConsole keeps per-game bindings in memory and reloads them only after
/// edits in its own window (checked in its code, 14 Nov), so CreteOS's changes
/// apply when it restarts. It runs elevated, so restarting it needs one UAC
/// prompt; its `\OneXConsole` scheduled task starts it again as the user.
#[tauri::command]
pub async fn apply_power_settings() -> Result<String, String> {
    tauri::async_runtime::spawn_blocking(|| {
        use std::os::windows::process::CommandExt;
        const CREATE_NO_WINDOW: u32 = 0x0800_0000;
        if !onexconsole_running() {
            let started = std::process::Command::new("schtasks.exe").args(["/Run", "/TN", "\\OneXConsole"]).creation_flags(CREATE_NO_WINDOW)
                .output().map(|out| out.status.success()).unwrap_or(false);
            return Ok(if started { "OneXConsole started with the new settings." } else { "Settings saved. They apply when OneXConsole next starts." }.into());
        }
        let script = "taskkill /IM OneXConsole.exe /F & timeout /t 2 /nobreak >nul & schtasks /Run /TN \\OneXConsole";
        match super::shell_execute_args("runas", "cmd.exe", &format!("/c {script}")) {
            Ok(()) => Ok("Restarting OneXConsole with the new settings…".into()),
            Err(5) => Ok("Settings saved. They apply at your next sign-in (administrator permission was declined).".into()),
            Err(code) => Ok(format!("Settings saved. They apply at your next sign-in (Windows code {code}).")),
        }
    }).await.map_err(|error| error.to_string())?
}

#[cfg(test)]
mod tests {
    use super::*;

    fn fixture() -> PathBuf {
        let dir = std::env::temp_dir().join(format!("creteos-perf-{}-{}", std::process::id(), std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos()));
        std::fs::create_dir_all(&dir).unwrap();
        let path = dir.join("core.db");
        std::fs::File::create(&path).unwrap();
        let db = Db::open(&path, true).unwrap();
        for sql in [
            "create table game_setting_preset (id integer primary key autoincrement, createTime text, isDefault integer, title text, titleLocaleKey text, showIndex integer, tdpTakeEffect integer, tdpPluginSep integer, tdp integer, tdpPlugin integer, tdpDCIn integer, fanTakeEffect integer, fanPluginSep integer, fanMode integer, fanModePlugin integer, rtssTakeEffect integer, rtssFrameValue integer, vibrationLevel integer)",
            "create table game_setting_preset_process (id integer primary key autoincrement, createTime text, presetId integer, processName text)",
            "create table setting (id integer primary key, fanPreset1 text, fanPreset2 text)",
            "insert into game_setting_preset values (1,null,1,'Default Preset','k',0,1,1,6,18,15,0,0,0,0,0,0,3)",
            "insert into game_setting_preset values (2,null,0,'Onimusha',null,1,1,0,15,15,15,0,0,0,0,0,0,3)",
            "insert into game_setting_preset_process values (1,null,2,'onimushawots')",
            "insert into game_setting_preset_process values (2,null,1,'__virtual_desktop')",
            "insert into setting values (1,'[[45,0.35],[55,0.45],[65,0.6],[75,0.75],[85,0.85]]',null)",
        ] { db.execute(sql, &[]).unwrap(); }
        path
    }

    #[test]
    fn creates_updates_and_removes_a_game_preset_without_touching_others() {
        let path = fixture();
        let profile = Profile { tdp_battery: 10, tdp_charger: 20, fan_mode: 1, frame_cap: 60 };
        set_game_profile_at(&path, "eldenring", "Elden Ring", Some(&profile)).unwrap();
        let db = Db::open(&path, false).unwrap();
        let rows = db.query("select * from game_setting_preset where title='Elden Ring'", &[]).unwrap();
        assert_eq!(rows.len(), 1);
        assert_eq!(profile_from(&rows[0]), profile);
        assert_eq!(int(&rows[0], "vibrationLevel"), 3, "copied from the default preset");
        assert_eq!(int(&rows[0], "isDefault"), 0);
        // Update in place, then the untouched presets.
        let lighter = Profile { tdp_battery: 8, tdp_charger: 8, fan_mode: 0, frame_cap: 0 };
        set_game_profile_at(&path, "eldenring", "Elden Ring", Some(&lighter)).unwrap();
        assert_eq!(db.query("select count(*) as n from game_setting_preset", &[]).unwrap()[0]["n"], 3);
        let onimusha = &db.query("select * from game_setting_preset where id=2", &[]).unwrap()[0];
        assert_eq!(int(onimusha, "tdp"), 15);
        let default = &db.query("select * from game_setting_preset where id=1", &[]).unwrap()[0];
        assert_eq!((int(default, "tdp"), int(default, "tdpPlugin")), (6, 18));
        // Remove.
        set_game_profile_at(&path, "eldenring", "", None).unwrap();
        assert_eq!(db.query("select count(*) as n from game_setting_preset", &[]).unwrap()[0]["n"], 2);
        assert!(db.query("select * from game_setting_preset_process where processName='eldenring'", &[]).unwrap().is_empty());
        assert!(set_game_profile_at(&path, "__virtual_desktop", "", None).is_err());
    }

    #[test]
    fn rejects_out_of_range_values_and_reads_curves() {
        assert!(validate(&Profile { tdp_battery: 3, tdp_charger: 10, fan_mode: 0, frame_cap: 0 }).is_err());
        assert!(validate(&Profile { tdp_battery: 10, tdp_charger: 31, fan_mode: 0, frame_cap: 0 }).is_err());
        assert!(validate(&Profile { tdp_battery: 10, tdp_charger: 10, fan_mode: 3, frame_cap: 0 }).is_err());
        assert!(validate(&Profile { tdp_battery: 10, tdp_charger: 10, fan_mode: 2, frame_cap: 10 }).is_err());
        let path = fixture();
        set_fan_curve_at(&path, 1, &[0.3, 0.4, 0.55, 0.7, 0.9]).unwrap();
        assert!(set_fan_curve_at(&path, 0, &[0.5, 0.4, 0.5, 0.6, 0.7]).is_err(), "curves must rise");
        let db = Db::open(&path, false).unwrap();
        let row = &db.query("select fanPreset2 from setting", &[]).unwrap()[0];
        assert_eq!(parse_curve(row.get("fanPreset2"))[4], [85.0, 0.9]);
        assert_eq!(process_key(r"C:\Games\ELDEN RING\Game\eldenring.exe").as_deref(), Some("eldenring"));
    }
}

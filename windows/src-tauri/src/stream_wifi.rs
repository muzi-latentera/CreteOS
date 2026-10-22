//! Optional home-network policy. A lease belongs to an actual launch lifecycle,
//! not to the foreground window or the browser's short-lived proxy process.
use std::{path::PathBuf, time::{SystemTime, UNIX_EPOCH}};
use serde::{Deserialize, Serialize};

#[derive(Clone, Copy, Default, Deserialize, Serialize, PartialEq, Debug)]
#[serde(rename_all = "camelCase")]
pub enum Choice { #[default] Automatic, Gaming, Download, Current }
#[derive(Default, Deserialize, Serialize)]
#[serde(default, rename_all = "camelCase")]
pub struct Preferences { moonlight: Choice, geforce_now: Choice, luna: Choice }
static PREF_LOCK: std::sync::Mutex<()> = std::sync::Mutex::new(());
fn path() -> PathBuf { crate::liftoff_dir().join("stream_wifi_preferences.json") }
fn read() -> Preferences {
    std::fs::read(path()).ok().and_then(|b| serde_json::from_slice(&b).ok()).unwrap_or_default()
}
fn selection(p: &Preferences, provider: &str) -> Result<Choice, String> {
    match provider { "moonlight" => Ok(p.moonlight), "geforceNow" => Ok(p.geforce_now), "luna" => Ok(p.luna), _ => Err("Unknown streaming service".into()) }
}
fn mode(choice: Choice, provider: &str) -> &'static str {
    match choice { Choice::Current => "Current", Choice::Gaming => "Gaming", Choice::Download => "Download",
        Choice::Automatic => if provider == "moonlight" { "Gaming" } else { "Download" } }
}
#[tauri::command]
pub fn get_stream_wifi_preferences() -> Preferences { read() }
#[tauri::command]
pub fn set_stream_wifi_preference(provider: String, choice: Choice) -> Result<(), String> {
    let _lock = PREF_LOCK.lock().map_err(|_| "Wi-Fi preferences busy")?;
    let mut prefs = read();
    selection(&prefs, &provider)?;
    match provider.as_str() { "moonlight" => prefs.moonlight = choice, "geforceNow" => prefs.geforce_now = choice, _ => prefs.luna = choice }
    std::fs::create_dir_all(crate::liftoff_dir()).map_err(|e| e.to_string())?;
    let temp = path().with_extension("tmp");
    std::fs::write(&temp, serde_json::to_vec_pretty(&prefs).map_err(|e| e.to_string())?).map_err(|e| e.to_string())?;
    std::fs::rename(temp, path()).map_err(|e| e.to_string())
}
pub struct Session { lease: PathBuf }
impl Drop for Session { fn drop(&mut self) { let _ = std::fs::remove_file(&self.lease); } }
pub fn prepare(provider: &str) -> Option<Session> {
    let folder = dirs::data_local_dir()?.join("CreteOS-Bootstrap/WiFiSmart");
    let helper = folder.join("WiFiSmart.ps1");
    if !helper.is_file() || !folder.join("config.json").is_file() { return None; }
    let choice = selection(&read(), provider).ok()?;
    let stamp = SystemTime::now().duration_since(UNIX_EPOCH).ok()?;
    let token = format!("{}-{}", std::process::id(), stamp.as_nanos());
    let leases = folder.join("sessions");
    std::fs::create_dir_all(&leases).ok()?;
    let lease = leases.join(format!("{token}.json"));
    let payload = serde_json::json!({"ownerPid":std::process::id(),"createdUnix":stamp.as_secs_f64(),"provider":provider});
    let temp = lease.with_extension("tmp");
    std::fs::write(&temp, serde_json::to_vec(&payload).ok()?).ok()?;
    std::fs::rename(temp, &lease).ok()?;
    let guard = Session { lease };
    let system = std::env::var_os("SystemRoot")?;
    let mut command = std::process::Command::new(PathBuf::from(system).join("System32/WindowsPowerShell/v1.0/powershell.exe"));
    command.args(["-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File"])
        .arg(helper).args(["-Mode", "PrepareSession", "-Preference", mode(choice, provider), "-Token", &token]);
    command.stdout(std::process::Stdio::null()).stderr(std::process::Stdio::null());
    #[cfg(windows)] { use std::os::windows::process::CommandExt; command.creation_flags(0x08000000); }
    // Networking is best effort: unavailable home Wi-Fi must not prevent play.
    if let Ok(mut child) = command.spawn() {
        let start = std::time::Instant::now();
        loop {
            if !matches!(child.try_wait(), Ok(None)) { break; }
            if start.elapsed() > std::time::Duration::from_secs(22) { let _ = child.kill(); let _ = child.wait(); break; }
            std::thread::sleep(std::time::Duration::from_millis(100));
        }
    }
    Some(guard)
}
#[cfg(test)] mod tests {
    use super::*;
    #[test] fn automatic_defaults_and_explicit_overrides() {
        assert_eq!(mode(Choice::Automatic,"moonlight"),"Gaming");
        for provider in ["geforceNow","luna"] { assert_eq!(mode(Choice::Automatic,provider),"Download"); }
        assert_eq!(mode(Choice::Gaming,"luna"),"Gaming");
        assert_eq!(mode(Choice::Download,"moonlight"),"Download");
        assert_eq!(mode(Choice::Current,"moonlight"),"Current");
        assert!(selection(&Preferences::default(),"unknown").is_err());
    }
    #[test] fn preferences_roundtrip_and_reject_unrecognised_choice() {
        let p: Preferences = serde_json::from_str(r#"{"luna":"current","moonlight":"download"}"#).unwrap();
        assert_eq!(p.geforce_now,Choice::Automatic);
        let p: Preferences = serde_json::from_slice(&serde_json::to_vec(&p).unwrap()).unwrap();
        assert_eq!(p.luna,Choice::Current); assert_eq!(p.moonlight,Choice::Download);
        assert!(serde_json::from_str::<Choice>(r#""arbitrary SSID""#).is_err());
    }
    #[test] fn dropping_a_session_releases_only_its_own_lease() {
        let file=std::env::temp_dir().join(format!("crete-wifi-test-{}.json",std::process::id()));
        std::fs::write(&file,b"test").unwrap();
        { let _guard=Session{lease:file.clone()}; assert!(file.exists()); }
        assert!(!file.exists());
    }
}

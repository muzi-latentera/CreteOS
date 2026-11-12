//! Optional home-network policy. A lease belongs to an actual launch lifecycle,
//! not to the foreground window or the browser's short-lived proxy process.
use std::{path::PathBuf, time::{SystemTime, UNIX_EPOCH}};
use serde::{Deserialize, Serialize};
use super::host_moonlight_route::Choice as HostChoice;

#[derive(Clone, Copy, Default, Deserialize, Serialize, PartialEq, Debug)]
#[serde(rename_all = "camelCase")]
pub enum Choice { #[default] Automatic, Gaming, Download, Current }
#[derive(Default, Deserialize, Serialize)]
#[serde(default, rename_all = "camelCase")]
pub struct Preferences { moonlight: Choice, geforce_now: Choice, luna: Choice, ps5_remote_play: Choice, playstation_cloud: Choice, local: Choice, bosgame_connection: HostChoice }
static PREF_LOCK: std::sync::Mutex<()> = std::sync::Mutex::new(());
fn path() -> PathBuf { crate::liftoff_dir().join("stream_wifi_preferences.json") }
fn read() -> Preferences {
    std::fs::read(path()).ok().and_then(|b| serde_json::from_slice(&b).ok()).unwrap_or_default()
}
fn selection(p: &Preferences, provider: &str) -> Result<Choice, String> {
    match provider { "moonlight" => Ok(p.moonlight), "geforceNow" => Ok(p.geforce_now), "luna" => Ok(p.luna), "ps5RemotePlay"=>Ok(p.ps5_remote_play), "playstationCloud"=>Ok(p.playstation_cloud), "local" => Ok(p.local), _ => Err("Unknown streaming service".into()) }
}
fn mode(choice: Choice, provider: &str) -> &'static str {
    match choice { Choice::Current => "Current", Choice::Gaming => "Gaming", Choice::Download => "Download",
        Choice::Automatic => if matches!(provider,"moonlight"|"ps5RemotePlay") { "Gaming" } else { "Download" } }
}
#[tauri::command]
pub fn get_stream_wifi_preferences() -> Preferences { read() }
#[tauri::command(async)]
pub async fn get_stream_network_status(host: Option<String>) -> Result<serde_json::Value, String> {
    tauri::async_runtime::spawn_blocking(move || network_status(host.as_deref())).await.map_err(|e| e.to_string())?
}

pub(crate) fn network_status(host: Option<&str>) -> Result<serde_json::Value, String> {
    let folder = dirs::data_local_dir().ok_or("No profile folder")?.join("CreteOS-Bootstrap/WiFiSmart");
    let helper = folder.join("WiFiSmart.ps1");
    if !helper.is_file() { return Ok(serde_json::json!({"wired":false})); }
    let system = PathBuf::from(std::env::var_os("SystemRoot").ok_or("No Windows folder")?);
    let mut command = std::process::Command::new(system.join("System32/WindowsPowerShell/v1.0/powershell.exe"));
    command.args(["-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File"]).arg(helper).args(["-Mode", "NetworkStatus"]);
    if let Some(host) = host {
        super::streaming::validate_host(host)?;
        command.args(["-RouteTarget", host]);
    }
    #[cfg(windows)] { use std::os::windows::process::CommandExt; command.creation_flags(0x08000000); }
    let output = crate::streaming::bounded_output_timeout(command, None, 9)?;
    let mut status: serde_json::Value = serde_json::from_slice(&output.stdout).map_err(|_| "Could not read active network")?;
    if host.is_some() && status["wired"] != true && status.get("e433").is_none() {
        let mut command = std::process::Command::new(system.join("System32/netsh.exe"));
        command.args(["wlan", "show", "interfaces"]);
        #[cfg(windows)] { use std::os::windows::process::CommandExt; command.creation_flags(0x08000000); }
        let wifi = crate::streaming::bounded_output_timeout(command, None, 3)?;
        let text = String::from_utf8_lossy(&wifi.stdout).replace("\r\n", "\n");
        let interface = status["interfaceName"].as_str().unwrap_or_default();
        let profile = active_profile(&text, interface);
        let config: serde_json::Value = std::fs::read(folder.join("config.json")).ok()
            .and_then(|bytes| serde_json::from_slice(bytes.strip_prefix(b"\xef\xbb\xbf").unwrap_or(&bytes)).ok()).unwrap_or_default();
        status["e433"] = serde_json::json!(profile.is_some() && profile == config["DownloadProfile"].as_str());
    }
    Ok(status)
}

fn active_profile<'a>(output: &'a str, interface: &str) -> Option<&'a str> {
    output.split("\n\n").find_map(|block| {
        let fields = block.lines().filter_map(|line| line.split_once(':'))
            .map(|(key, value)| (key.trim(), value.trim()))
            .collect::<std::collections::HashMap<_, _>>();
        if fields.get("Name") != Some(&interface) || fields.get("State") != Some(&"connected") { return None; }
        fields.get("Profile").copied()
    })
}

pub fn bosgame_route(host: &str) -> Option<(HostChoice, bool)> {
    if !host.trim_end_matches('.').eq_ignore_ascii_case("Bosgame") { return None; }
    match read().bosgame_connection {
        HostChoice::Automatic => Some((HostChoice::Ethernet, false)),
        manual => Some((manual, true)),
    }
}
#[tauri::command]
pub fn set_stream_wifi_preference(provider: String, choice: Choice) -> Result<(), String> {
    let _lock = PREF_LOCK.lock().map_err(|_| "Wi-Fi preferences busy")?;
    let mut prefs = read();
    selection(&prefs, &provider)?;
    match provider.as_str() { "moonlight" => prefs.moonlight = choice, "geforceNow" => prefs.geforce_now = choice, "luna"=>prefs.luna = choice, "ps5RemotePlay"=>prefs.ps5_remote_play=choice, "playstationCloud"=>prefs.playstation_cloud=choice, "local" => prefs.local=choice, _=>unreachable!() }
    save(&prefs)
}

#[tauri::command]
pub fn set_bosgame_connection_preference(choice: HostChoice) -> Result<(), String> {
    let _lock = PREF_LOCK.lock().map_err(|_| "Wi-Fi preferences busy.")?;
    let mut prefs = read();
    prefs.bosgame_connection = choice;
    save(&prefs)
}

fn save(prefs: &Preferences) -> Result<(), String> {
    std::fs::create_dir_all(crate::liftoff_dir()).map_err(|e| e.to_string())?;
    let temp = path().with_extension("tmp");
    std::fs::write(&temp, serde_json::to_vec_pretty(prefs).map_err(|e| e.to_string())?).map_err(|e| e.to_string())?;
    std::fs::rename(temp, path()).map_err(|e| e.to_string())
}
pub struct Session { lease: PathBuf }
impl Drop for Session { fn drop(&mut self) { let _ = std::fs::remove_file(&self.lease); } }
pub fn prepare(provider: &str) -> Option<Session> {
    let choice = selection(&read(), provider).ok()?;
    if provider == "local" && choice == Choice::Automatic { return None; }
    prepare_choice(provider, choice, None)
}
pub fn prepare_for_host(provider:&str,host:&str)->Option<Session>{prepare_choice(provider,selection(&read(),provider).ok()?,Some(host))}

pub fn confirm_moonlight_network(before: &serde_json::Value, after: &serde_json::Value) -> Result<(), String> {
    if before["home"] != true || after["wired"] == true { return Ok(()); }
    let target = match read().moonlight {
        Choice::Current => return Ok(()),
        Choice::Automatic if before["docked"] != true => "gaming",
        Choice::Gaming => "gaming",
        Choice::Automatic | Choice::Download => "e433",
    };
    if after[target] == true { return Ok(()); }
    let name = if target == "e433" { "Hyperoptic Fibre E433" } else { "Hyperoptic Extra" };
    let current = after["profile"].as_str().filter(|name| !name.is_empty()).unwrap_or("disconnected");
    Err(format!("The G1 could not confirm {name}. Current Wi-Fi is {current}. Retry or choose Keep current connection."))
}
pub fn prepare_current(provider: &str) -> Option<Session> {
    selection(&read(),provider).ok()?;
    prepare_choice(provider,Choice::Current,None)
}
fn prepare_choice(provider:&str,choice:Choice,route_target:Option<&str>)->Option<Session>{
    let folder = dirs::data_local_dir()?.join("CreteOS-Bootstrap/WiFiSmart");
    let helper = folder.join("WiFiSmart.ps1");
    if !helper.is_file() || !folder.join("config.json").is_file() { return None; }
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
    let preference = if provider == "moonlight" && route_target.is_some() && choice == Choice::Automatic { "MoonlightAuto" } else { mode(choice, provider) };
    command.args(["-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File"])
        .arg(helper).args(["-Mode", "PrepareSession", "-Preference", preference, "-Token", &token]);
    if let Some(target)=route_target {command.args(["-RouteTarget",target]);}
    command.stdout(std::process::Stdio::null()).stderr(std::process::Stdio::null());
    #[cfg(windows)] { use std::os::windows::process::CommandExt; command.creation_flags(0x08000000); }
    // Networking is best effort: unavailable home Wi-Fi must not prevent play.
    if let Ok(mut child) = command.spawn() {
        let start = std::time::Instant::now();
        loop {
            if !matches!(child.try_wait(), Ok(None)) { break; }
            if start.elapsed() > std::time::Duration::from_secs(36) { let _ = child.kill(); let _ = child.wait(); break; }
            std::thread::sleep(std::time::Duration::from_millis(100));
        }
    }
    Some(guard)
}
#[cfg(test)] mod tests {
    use super::*;
    #[test] fn automatic_defaults_and_explicit_overrides() {
        assert_eq!(mode(Choice::Automatic,"moonlight"),"Gaming");
        assert_eq!(mode(Choice::Automatic,"ps5RemotePlay"),"Gaming");
        for provider in ["geforceNow","luna","playstationCloud"] { assert_eq!(mode(Choice::Automatic,provider),"Download"); }
        assert_eq!(mode(Choice::Gaming,"luna"),"Gaming");
        assert_eq!(mode(Choice::Download,"moonlight"),"Download");
        assert_eq!(mode(Choice::Current,"moonlight"),"Current");
        assert!(selection(&Preferences::default(),"unknown").is_err());
    }
    #[test] fn preferences_roundtrip_and_reject_unrecognised_choice() {
        let p: Preferences = serde_json::from_str(r#"{"luna":"current","moonlight":"download"}"#).unwrap();
        assert_eq!(p.geforce_now,Choice::Automatic);
        assert_eq!(p.bosgame_connection, HostChoice::Automatic);
        let p: Preferences = serde_json::from_slice(&serde_json::to_vec(&p).unwrap()).unwrap();
        assert_eq!(p.luna,Choice::Current); assert_eq!(p.moonlight,Choice::Download);
        assert!(serde_json::from_str::<Choice>(r#""arbitrary SSID""#).is_err());
    }
    #[test] fn detects_only_the_connected_adapter_that_owns_the_route() {
        let text = "Name : Wi-Fi\nState : disconnected\nProfile : Hyperoptic Fibre E433\n\nName : Wi-Fi 2\nState : connected\nBSSID : 11:22\nProfile : Hyperoptic Fibre E433\n";
        assert_eq!(active_profile(text, "Wi-Fi 2"), Some("Hyperoptic Fibre E433"));
        assert_eq!(active_profile(text, "Wi-Fi"), None);
        assert_eq!(active_profile(text, "Ethernet"), None);
    }
    #[test] fn dropping_a_session_releases_only_its_own_lease() {
        let file=std::env::temp_dir().join(format!("crete-wifi-test-{}.json",std::process::id()));
        std::fs::write(&file,b"test").unwrap();
        { let _guard=Session{lease:file.clone()}; assert!(file.exists()); }
        assert!(!file.exists());
    }
}

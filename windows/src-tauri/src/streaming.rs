use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use std::io::{Read, Write};

fn bounded_output(mut command: std::process::Command, input: Option<&[u8]>) -> Result<std::process::Output, String> {
    command.stdout(std::process::Stdio::piped()).stderr(std::process::Stdio::piped());
    if input.is_some() { command.stdin(std::process::Stdio::piped()); }
    #[cfg(windows)] { use std::os::windows::process::CommandExt; command.creation_flags(0x08000000); }
    let mut child = command.spawn().map_err(|e| e.to_string())?;
    let stdout = child.stdout.take().ok_or("Missing stdout")?;
    let stderr = child.stderr.take().ok_or("Missing stderr")?;
    let out = std::thread::spawn(move || { let mut bytes = Vec::new(); stdout.take(1024 * 1024).read_to_end(&mut bytes).map(|_| bytes) });
    let err = std::thread::spawn(move || { let mut bytes = Vec::new(); stderr.take(65536).read_to_end(&mut bytes).map(|_| bytes) });
    if let Some(input) = input {
        if let Some(mut stdin) = child.stdin.take() { let _ = stdin.write_all(input); }
    }
    let start = std::time::Instant::now();
    let status = loop {
        if let Some(status) = child.try_wait().map_err(|e| e.to_string())? { break status; }
        if start.elapsed() > std::time::Duration::from_secs(10) {
            let _ = child.kill(); let _ = child.wait(); return Err("Host query timed out. Check Bosgame's connection.".into());
        }
        std::thread::sleep(std::time::Duration::from_millis(50));
    };
    let stdout = out.join().map_err(|_| "Host output reader failed")?.map_err(|e| e.to_string())?;
    let stderr = err.join().map_err(|_| "Host error reader failed")?.map_err(|e| e.to_string())?;
    Ok(std::process::Output { status, stdout, stderr })
}

fn client_path(provider: &str) -> Result<PathBuf, String> {
    let mut candidates = Vec::new();
    match provider {
        "geforceNow" => {
            if let Some(local) = dirs::data_local_dir() {
                candidates.push(local.join("NVIDIA Corporation/GeForceNOW/CEF/GeForceNOW.exe"));
            }
        }
        "moonlight" => {
            for key in ["ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA"] {
                if let Some(root) = std::env::var_os(key) {
                    candidates.push(PathBuf::from(&root).join("Moonlight Game Streaming/Moonlight.exe"));
                    candidates.push(PathBuf::from(root).join("Programs/Moonlight Game Streaming/Moonlight.exe"));
                }
            }
        }
        _ => return Err("Unknown streaming client".into()),
    }
    candidates.into_iter().find(|path| path.is_file())
        .ok_or_else(|| "Streaming client is not installed in a detected location".into())
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct StreamingClients {
    geforce_now: bool,
    moonlight: bool,
}

#[tauri::command]
pub fn get_streaming_clients() -> StreamingClients {
    StreamingClients { geforce_now: client_path("geforceNow").is_ok(), moonlight: client_path("moonlight").is_ok() }
}

#[tauri::command]
pub fn launch_streaming_client(provider: String) -> Result<(), String> {
    // Only detected, fixed client executables; no shell command or arbitrary
    // paths/arguments from the frontend. This opens the client's library.
    std::process::Command::new(client_path(&provider)?).spawn()
        .map(|_| ()).map_err(|error| error.to_string())
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MoonlightProfile {
    pub host: String,
    pub resolution: String,
    pub fps: u32,
    pub bitrate: u32,
    pub codec: String,
}

pub fn validate_host(host: &str) -> Result<(), String> {
    if !host.starts_with(|c: char| c.is_ascii_alphanumeric()) || host.len() > 253 || !host.chars().all(|c| c.is_ascii_alphanumeric() || ".:-_".contains(c)) {
        return Err("Enter a hostname or IP address".into());
    }
    Ok(())
}

fn validate(profile: &MoonlightProfile) -> Result<(), String> {
    validate_host(&profile.host)?;
    if !["1280x720", "1920x1080", "1920x1200", "2560x1440", "2560x1600", "3840x2160"].contains(&profile.resolution.as_str())
        || ![30, 60, 90, 120].contains(&profile.fps) || !(5000..=150000).contains(&profile.bitrate)
        || !["auto", "H.264", "HEVC", "AV1"].contains(&profile.codec.as_str()) {
        return Err("Unsupported streaming settings".into());
    }
    Ok(())
}

fn query_apps(host: &str) -> Result<Vec<String>, String> {
    let mut command = std::process::Command::new(client_path("moonlight")?);
    command.args(["list", host]);
    let output = bounded_output(command, None)?;
    if !output.status.success() { return Err("Moonlight could not retrieve the host's apps. Check pairing and host availability.".into()); }
    Ok(String::from_utf8_lossy(&output.stdout).lines()
        .filter(|s| !s.is_empty() && *s != "Establishing connection to PC..." && *s != "Loading app list...")
        .take(2000).map(str::to_owned).collect())
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HostGame { pub steam_app_id: String, pub name: String }

fn host_games(host: &str) -> Result<Vec<HostGame>, String> {
    validate_host(host)?;
    let folder = dirs::data_local_dir().ok_or("No local data directory")?.join("CreteOS-Bootstrap/ssh");
    let settings: serde_json::Value = std::fs::read(folder.join("bosgame.json")).ok()
        .and_then(|bytes| serde_json::from_slice(&bytes).ok()).ok_or("Set up Bosgame SSH access to detect installed games")?;
    let configured = settings["host"].as_str().ok_or("Missing SSH host")?;
    if !configured.eq_ignore_ascii_case(host) { return Err("SSH inventory is configured for another host".into()); }
    let username = settings["username"].as_str().ok_or("Missing SSH username")?;
    if !username.starts_with(|c: char| c.is_ascii_alphanumeric()) || !username.chars().all(|c| c.is_ascii_alphanumeric() || "-_".contains(c)) { return Err("Invalid SSH username".into()); }
    let key = folder.join("id_ed25519_bosgame"); let known = folder.join("known_hosts");
    if !key.is_file() || !known.is_file() { return Err("Bosgame SSH key is unavailable".into()); }
    let ssh = PathBuf::from(std::env::var_os("SystemRoot").unwrap_or_else(|| "C:/Windows".into())).join("System32/OpenSSH/ssh.exe");
    let mut command = std::process::Command::new(ssh);
    command.arg("-i").arg(key).args(["-o", "IdentitiesOnly=yes", "-o", "BatchMode=yes", "-o", "ConnectTimeout=5", "-o", "StrictHostKeyChecking=yes", "-o"])
        .arg(format!("UserKnownHostsFile={}", known.display())).arg(format!("{username}@{host}")).arg("python3 -");
    let output = bounded_output(command, Some(include_bytes!("steam_inventory.py")))?;
    if !output.status.success() { return Err("Could not read Bosgame's Steam library. Check SSH access and that Steam is installed for the host login.".into()); }
    let games: Vec<HostGame> = serde_json::from_slice(&output.stdout).map_err(|_| "Invalid host inventory response")?;
    if games.len() > 5000 || games.iter().any(|game| game.steam_app_id.len() > 10 || game.steam_app_id.is_empty() || !game.steam_app_id.bytes().all(|c| c.is_ascii_digit())) {
        return Err("Invalid host inventory".into());
    }
    Ok(games)
}

#[tauri::command]
pub async fn get_host_installed_games(host: String) -> Result<Vec<HostGame>, String> {
    tauri::async_runtime::spawn_blocking(move || host_games(&host)).await.map_err(|e| e.to_string())?
}

#[tauri::command]
pub async fn get_moonlight_apps(host: String) -> Result<Vec<String>, String> {
    let profile = MoonlightProfile { host: host.clone(), resolution: "1920x1080".into(), fps: 120, bitrate: 80000, codec: "auto".into() };
    validate(&profile)?;
    tauri::async_runtime::spawn_blocking(move || query_apps(&host)).await.map_err(|e| e.to_string())?
}

#[tauri::command]
pub fn get_moonlight_profile(window: tauri::Window) -> Result<serde_json::Value, String> {
    let monitor = window.current_monitor().map_err(|e| e.to_string())?.ok_or("No active display detected")?;
    let size = monitor.size();
    let mut key = format!("{}:{}x{}", monitor.name().map(String::as_str).unwrap_or("display"), size.width, size.height);
    let mut xreal = false;
    #[cfg(windows)] {
        use windows::Win32::Graphics::Gdi::{EnumDisplayDevicesW, DISPLAY_DEVICEW};
        use windows::core::PCWSTR;
        if let Some(name) = monitor.name() {
            let wide = name.encode_utf16().chain(Some(0)).collect::<Vec<_>>();
            let mut device = DISPLAY_DEVICEW::default();
            device.cb = std::mem::size_of::<DISPLAY_DEVICEW>() as u32;
            if unsafe { EnumDisplayDevicesW(PCWSTR(wide.as_ptr()), 0, &mut device, 0) }.as_bool() {
                let read = |chars: &[u16]| String::from_utf16_lossy(&chars[..chars.iter().position(|c| *c == 0).unwrap_or(chars.len())]);
                let id = read(&device.DeviceID);
                xreal = read(&device.DeviceString).to_uppercase().contains("XREAL");
                if !id.is_empty() { key = id; }
            }
        }
    }
    let path = super::liftoff_dir().join("moonlight_profiles.json");
    let profiles: serde_json::Value = std::fs::read(path).ok().and_then(|bytes| serde_json::from_slice(&bytes).ok()).unwrap_or(serde_json::json!({}));
    let (resolution, fps) = if size.height >= 2160 { ("3840x2160", 60) } else if xreal || size.height == 1200 { ("1920x1200", 90) } else { ("1920x1080", 120) };
    let saved = profiles.get(&key).cloned().unwrap_or(serde_json::json!({"host":"Bosgame","resolution":resolution,"fps":fps,"bitrate":80000,"codec":"auto"}));
    Ok(serde_json::json!({"displayKey":key,"profile":saved}))
}

#[tauri::command]
pub async fn launch_moonlight(app_name: String, display_key: String, profile: MoonlightProfile, steam_id: Option<String>) -> Result<(), String> {
    validate(&profile)?;
    if display_key.len() > 512 { return Err("Invalid display".into()); }
    if app_name.starts_with('-') || app_name.chars().any(char::is_control) { return Err("Invalid host app name".into()); }
    tauri::async_runtime::spawn_blocking(move || {
        if !query_apps(&profile.host)?.contains(&app_name) { return Err("This app is no longer advertised by the host. Refresh the Moonlight list.".into()); }
        if steam_id.is_some() {
            if app_name != "MoonDeckStream" { return Err("Direct Steam launching requires MoonDeckStream".into()); }
            if super::moondeck::status(&profile.host)?["paired"] != true { return Err("Pair CreteOS with MoonDeck Buddy first".into()); }
            super::moondeck::prepare(&profile.host, steam_id.as_deref().unwrap_or_default())?;
            if !host_games(&profile.host)?.iter().any(|game| Some(&game.steam_app_id) == steam_id.as_ref()) {
                return Err("This game is not installed on Bosgame. Choose local install or GeForce NOW.".into());
            }
        }
        let path = super::liftoff_dir().join("moonlight_profiles.json");
        let mut profiles: serde_json::Value = std::fs::read(&path).ok().and_then(|bytes| serde_json::from_slice(&bytes).ok()).unwrap_or(serde_json::json!({}));
        profiles[display_key] = serde_json::to_value(&profile).map_err(|e| e.to_string())?;
        std::fs::create_dir_all(super::liftoff_dir()).map_err(|e| e.to_string())?;
        std::fs::write(path, serde_json::to_vec_pretty(&profiles).map_err(|e| e.to_string())?).map_err(|e| e.to_string())?;
        std::process::Command::new(client_path("moonlight")?).args(["stream", &profile.host, &app_name,
            "--resolution", &profile.resolution, "--fps", &profile.fps.to_string(), "--bitrate", &profile.bitrate.to_string(),
            "--video-codec", &profile.codec, "--display-mode", "borderless"]).spawn().map_err(|e| e.to_string())?;
        if let Some(id) = steam_id { super::moondeck::launch(&profile.host, &id)?; }
        Ok(())
    }).await.map_err(|e| e.to_string())?
}

#[cfg(test)]
mod tests {
    #[test]
    fn rejects_unknown_clients() {
        assert!(super::client_path("powershell").is_err());
        assert!(super::client_path("C:/anything.exe").is_err());
    }
    #[test]
    fn validates_stream_settings() {
        let mut p = super::MoonlightProfile { host: "Bosgame".into(), resolution: "1920x1200".into(), fps: 90, bitrate: 80000, codec: "AV1".into() };
        assert!(super::validate(&p).is_ok());
        p.host = "--help".into(); assert!(super::validate(&p).is_err());
        p.host = "host & command".into(); assert!(super::validate(&p).is_err());
        p.host = "Bosgame".into(); p.fps = 900; assert!(super::validate(&p).is_err());
    }
}

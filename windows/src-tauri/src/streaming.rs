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

fn geforce_native_game_uri(game_url: &str) -> Result<String, String> {
    let url = reqwest::Url::parse(game_url).map_err(|_| "Invalid GeForce NOW game link")?;
    if url.scheme() != "https" || url.host_str() != Some("play.geforcenow.com") || url.path() != "/games" {
        return Err("Invalid GeForce NOW game link".into());
    }
    let id = url.query_pairs().find(|(key, _)| key == "game-id").map(|(_, value)| value.into_owned()).ok_or("Missing GeForce NOW game ID")?;
    if id.len() != 36 || !id.bytes().enumerate().all(|(i, b)| if [8,13,18,23].contains(&i) { b == b'-' } else { b.is_ascii_hexdigit() }) {
        return Err("Invalid GeForce NOW game ID".into());
    }
    // The native route parser recognises play-game; OS URI handling only
    // recognises view-game, so pass the route directly to the fixed executable.
    let asset = url.query_pairs().find(|(key, _)| key == "asset-id").map(|(_, value)| value.into_owned());
    let asset = asset.filter(|value| value.len() <= 64 && value.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'_' || b == b'-'));
    Ok(format!("#deeplink?action=play-game&game-id={id}&lang=en_GB{}", asset.map(|a| format!("&asset-id={a}")).unwrap_or_default()))
}

#[tauri::command]
pub async fn launch_geforce_game(game_url: String, source: Option<String>, play: Option<bool>) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || {
        let (client, argument) = geforce_launch(&game_url, source.as_deref(), play.unwrap_or(true))?;
        std::process::Command::new(client).arg(argument).spawn().map(|_| ()).map_err(|error| error.to_string())
    }).await.map_err(|e| e.to_string())?
}

fn shortcut_route(arguments: &str, game_id: &str, source: Option<&str>) -> Option<String> {
    let route = arguments.trim().strip_prefix("--url-route=")?.trim_matches('"');
    let query = route.strip_prefix("#?")?;
    let url = reqwest::Url::parse(&format!("https://crete.invalid/?{query}")).ok()?;
    let pairs: std::collections::HashMap<_, _> = url.query_pairs().into_owned().collect();
    let cms = pairs.get("cmsId")?; let name = pairs.get("shortName")?;
    if pairs.len() != 4 || pairs.get("parentGameId")?.as_str() != game_id || pairs.get("launchSource")? != "External"
        || cms.is_empty() || cms.len() > 12 || !cms.bytes().all(|b| b.is_ascii_digit())
        || name.is_empty() || name.len() > 160 || !name.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'_' || b == b'-') { return None; }
    let matches = match source { Some("xbox") => name.ends_with("_xbox_app") || name.ends_with("_xbox"), Some("steam") => name.ends_with("_steam"), Some("epic") => name.ends_with("_epic") || name.ends_with("_epic_games"), Some("gog") => name.ends_with("_gog"), _ => true };
    matches.then(|| format!("#?cmsId={cms}&launchSource=External&shortName={name}&parentGameId={game_id}"))
}

fn geforce_launch(game_url: &str, source: Option<&str>, play: bool) -> Result<(PathBuf, String), String> {
    let fallback = geforce_native_game_uri(game_url)?;
    let url = reqwest::Url::parse(game_url).map_err(|_| "Invalid game link")?;
    let id = url.query_pairs().find(|(key, _)| key == "game-id").map(|(_, value)| value.into_owned()).ok_or("Missing game ID")?;
    let client = client_path("geforceNow")?;
    if !play { return Ok((client, format!("geforcenow://route/#deeplink?action=view-game&game-id={id}"))); }
    let streamer = client.with_file_name("GeForceNOWStreamer.exe");
    if streamer.is_file() {
        if let Some(route) = crate::gfn_catalog::route(&id, source) { return Ok((streamer, format!("--url-route={route}"))); }
        let script = "$d=[Environment]::GetFolderPath('DesktopDirectory');$s=New-Object -ComObject WScript.Shell;$a=@();Get-ChildItem -LiteralPath $d -Filter *.lnk -File | Select-Object -First 200 | ForEach-Object {$l=$s.CreateShortcut($_.FullName);if([IO.Path]::GetFileName($l.TargetPath) -eq 'GeForceNOWStreamer.exe'){$a+=@{target=$l.TargetPath;arguments=$l.Arguments}}};ConvertTo-Json -InputObject $a -Compress";
        let mut command = std::process::Command::new("powershell.exe");
        command.args(["-NoProfile", "-NonInteractive", "-Command", script]);
        if let Ok(output) = bounded_output(command, None) {
            if let Ok(shortcuts) = serde_json::from_slice::<Vec<serde_json::Value>>(&output.stdout) {
                for shortcut in shortcuts {
                    if shortcut["target"].as_str().is_some_and(|target| target.eq_ignore_ascii_case(&streamer.to_string_lossy())) {
                        if let Some(route) = shortcut["arguments"].as_str().and_then(|args| shortcut_route(args, &id, source)) { return Ok((streamer, format!("--url-route={route}"))); }
                    }
                }
            }
        }
    }
    Ok((client, format!("--url-route={fallback}")))
}

#[tauri::command]
pub fn get_geforce_profile(window: tauri::Window) -> Result<serde_json::Value, String> {
    let mut display = get_moonlight_profile(window)?;
    let key = display["displayKey"].as_str().ok_or("Missing display identity")?;
    let profiles: serde_json::Value = std::fs::read(super::liftoff_dir().join("geforce_profiles.json")).ok().and_then(|b| serde_json::from_slice(&b).ok()).unwrap_or(serde_json::json!({}));
    let mut profile = profiles.get(key).cloned().unwrap_or_else(|| display["profile"].clone());
    profile["host"] = serde_json::json!("GeForce NOW");
    if profile["fps"] == 90 { profile["fps"] = serde_json::json!(60); }
    display["profile"] = profile;
    Ok(display)
}

fn update_geforce_settings(settings: &mut serde_json::Value, profile: &MoonlightProfile) -> Result<(), String> {
    let resolutions = ["1280x720", "1920x1080", "1920x1200", "2560x1440", "2560x1600", "3840x2160"];
    if !resolutions.contains(&profile.resolution.as_str()) || ![30,60,120,240].contains(&profile.fps) || !(5000..=100000).contains(&profile.bitrate) { return Err("Unsupported GeForce NOW settings".into()); }
    let config = settings.get_mut("appSettingsConfig").ok_or("GeForce NOW settings format is unsupported; use its Settings screen")?;
    let custom = config.get_mut("customProfile").filter(|v| v.is_object()).ok_or("Configure Custom quality in GeForce NOW once, then retry")?;
    if !["width", "height", "fps", "maxBitrate"].iter().all(|key| custom.get(key).is_some()) { return Err("GeForce NOW profile format is unsupported; use its Settings screen".into()); }
    let (width, height) = profile.resolution.split_once('x').ok_or("Invalid resolution")?;
    custom["width"] = serde_json::json!(width.parse::<u32>().map_err(|_| "Invalid width")?);
    custom["height"] = serde_json::json!(height.parse::<u32>().map_err(|_| "Invalid height")?);
    custom["fps"] = serde_json::json!(profile.fps); custom["maxBitrate"] = serde_json::json!(profile.bitrate / 1000);
    custom["maxBitrateAuto"] = serde_json::json!(false);
    custom["aspectRatio"] = serde_json::json!(if profile.resolution.ends_with("1200") || profile.resolution.ends_with("1600") { "(16:10)" } else { "(16:9)" });
    config["streamingProfiles"] = serde_json::json!("custom");
    Ok(())
}

#[tauri::command]
pub async fn start_geforce_game(game_url: String, display_key: String, profile: MoonlightProfile, source: Option<String>) -> Result<(), String> {
    geforce_native_game_uri(&game_url)?;
    if display_key.is_empty() || display_key.len() > 256 { return Err("Invalid display identity".into()); }
    tauri::async_runtime::spawn_blocking(move || {
        let (client, argument) = geforce_launch(&game_url, source.as_deref(), true)?;
        let mut command = std::process::Command::new("tasklist.exe");
        command.args(["/FO", "CSV", "/NH"]);
        let output = bounded_output(command, None)?;
        if !output.status.success() { return Err("Could not check whether GeForce NOW is running".into()); }
        let processes = String::from_utf8_lossy(&output.stdout).to_ascii_lowercase();
        if processes.contains("geforcenow.exe") || processes.contains("geforcenowstreamer.exe") { return Err("Close GeForce NOW first, then retry Start game to apply these settings.".into()); }
        let root = dirs::data_local_dir().ok_or("No local data folder")?;
        let path = root.join("NVIDIA Corporation/GeForceNOW/sharedstorage.json");
        let original = std::fs::read(&path).map_err(|_| "Open GeForce NOW and configure Custom streaming quality once")?;
        let mut settings: serde_json::Value = serde_json::from_slice(&original).map_err(|_| "Could not read GeForce NOW settings")?;
        update_geforce_settings(&mut settings, &profile)?;
        let backup = root.join("CreteOS-Bootstrap/gfn-settings"); std::fs::create_dir_all(&backup).map_err(|e| e.to_string())?;
        let stamp = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_nanos();
        std::fs::write(backup.join(format!("sharedstorage-{stamp}.json")), &original).map_err(|e| e.to_string())?;
        let temporary = path.with_file_name("sharedstorage.creteos.tmp");
        std::fs::write(&temporary, serde_json::to_vec(&settings).map_err(|e| e.to_string())?).map_err(|e| e.to_string())?;
        std::fs::rename(&temporary, &path).map_err(|e| e.to_string())?;
        let launch = std::process::Command::new(client).arg(argument).spawn();
        if let Err(error) = launch { let _ = std::fs::write(&path, original); return Err(error.to_string()); }
        let path = super::liftoff_dir().join("geforce_profiles.json");
        let mut profiles: serde_json::Value = std::fs::read(&path).ok().and_then(|b| serde_json::from_slice(&b).ok()).unwrap_or(serde_json::json!({}));
        profiles[display_key] = serde_json::to_value(profile).map_err(|e| e.to_string())?;
        std::fs::write(path, serde_json::to_vec(&profiles).map_err(|e| e.to_string())?).map_err(|e| e.to_string())?;
        Ok(())
    }).await.map_err(|e| e.to_string())?
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
        .and_then(|bytes| serde_json::from_slice(bytes.strip_prefix(b"\xEF\xBB\xBF").unwrap_or(&bytes)).ok()).ok_or("Set up Bosgame SSH access to detect installed games")?;
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
    fn geforce_routes_reject_external_urls_and_preserve_exact_game_ids() {
        let route = super::geforce_native_game_uri("https://play.geforcenow.com/games?game-id=5aa78bb8-6af4-49d9-b678-4b7c3685a98c").unwrap();
        assert!(route.contains("action=play-game")); assert!(route.contains("game-id=5aa78bb8-6af4-49d9-b678-4b7c3685a98c"));
        assert!(super::geforce_native_game_uri("https://evil.test/games?game-id=5aa78bb8-6af4-49d9-b678-4b7c3685a98c").is_err());
        assert!(super::geforce_native_game_uri("https://play.geforcenow.com/games?game-id=--bad").is_err());
    }
    #[test]
    fn native_shortcut_routes_match_game_and_store_without_extra_arguments() {
        let id="5aa78bb8-6af4-49d9-b678-4b7c3685a98c";
        let args=format!(" --url-route=\"#?cmsId=102396411&launchSource=External&shortName=a_plague_tale_requiem_xbox_app&parentGameId={id}\"");
        assert!(super::shortcut_route(&args,id,Some("xbox")).is_some());
        assert!(super::shortcut_route(&args,id,Some("steam")).is_none());
        assert!(super::shortcut_route(&format!("{args} --extra"),id,Some("xbox")).is_none());
        assert!(super::shortcut_route(&args,"wrong-id",Some("xbox")).is_none());
    }
    #[test]
    fn geforce_profile_changes_only_stream_settings() {
        let mut settings = serde_json::json!({"appSettingsConfig":{"customProfile":{"width":1920,"height":1080,"fps":60,"maxBitrate":50,"codec":0,"hdrStreamingMode":"OFF"},"otherSetting":true},"unrelated":{"kept":true}});
        let profile = super::MoonlightProfile { host:"GeForce NOW".into(), resolution:"1920x1200".into(), fps:60, bitrate:20000, codec:"auto".into() };
        super::update_geforce_settings(&mut settings, &profile).unwrap();
        assert_eq!(settings["appSettingsConfig"]["customProfile"]["maxBitrate"],20);
        assert_eq!(settings["appSettingsConfig"]["customProfile"]["height"],1200);
        assert_eq!(settings["unrelated"]["kept"],true);
        assert_eq!(settings["appSettingsConfig"]["customProfile"]["codec"],0);
        assert!(super::update_geforce_settings(&mut serde_json::json!({}), &profile).is_err());
    }
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

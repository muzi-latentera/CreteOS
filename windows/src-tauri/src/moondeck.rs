// MoonDeck Buddy protocol: FrogTheFrog/moondeck defaults/python/lib/buddyrequests.py.
// Public service certificate: FrogTheFrog/moondeck-keys (not a private key).
use base64::Engine;
use serde_json::{json, Value};

fn identity(host: &str) -> Result<String, String> {
    let entry = keyring::Entry::new("CreteOS.MoonDeck", host).map_err(|e| e.to_string())?;
    match entry.get_password() {
        Ok(id) => Ok(id),
        Err(keyring::Error::NoEntry) => {
            let mut bytes = [0u8; 32];
            getrandom::getrandom(&mut bytes).map_err(|e| e.to_string())?;
            let id = bytes.iter().map(|b| format!("{b:02x}")).collect::<String>();
            entry.set_password(&id).map_err(|e| e.to_string())?;
            Ok(id)
        }
        Err(e) => Err(e.to_string()),
    }
}

fn client() -> Result<reqwest::blocking::Client, String> {
    // Buddy uses a shared service certificate without a DNS hostname, as in
    // the official client. Trust only its bundled certificate; never disable
    // certificate verification or change Windows' system certificate store.
    let cert = reqwest::Certificate::from_pem(include_bytes!("moondeck_cert.pem")).map_err(|e| e.to_string())?;
    reqwest::blocking::Client::builder().tls_built_in_root_certs(false).add_root_certificate(cert)
        .danger_accept_invalid_hostnames(true).timeout(std::time::Duration::from_secs(5))
        .redirect(reqwest::redirect::Policy::none()).build().map_err(|e| e.to_string())
}

fn request(host: &str, endpoint: &str, body: Option<Value>) -> Result<Value, String> {
    let id = identity(host)?;
    let auth = base64::engine::general_purpose::STANDARD.encode(id.as_bytes());
    let url = format!("https://{host}:59999/{endpoint}");
    let client = client()?;
    let builder = if let Some(body) = body {
        if endpoint == "appData" { client.get(url).json(&body) } else { client.post(url).json(&body) }
    } else { client.get(url) };
    builder.header("Authorization", format!("basic {auth}")).send()
        .map_err(|_| "MoonDeck Buddy is unavailable on this host".to_owned())?
        .error_for_status().map_err(|_| "MoonDeck Buddy rejected the request. Check CreteOS pairing.".to_owned())?
        .json().map_err(|_| "Invalid MoonDeck Buddy response".to_owned())
}

pub fn status(host: &str) -> Result<Value, String> {
    let version = request(host, "apiVersion", None)?;
    if !matches!(version["version"].as_u64(), Some(8 | 9)) { return Err("Unsupported MoonDeck Buddy API version".into()); }
    let id = identity(host)?;
    let pairing = request(host, &format!("pairingState/{id}"), None)?;
    Ok(json!({"paired":pairing["state"].as_u64() == Some(0),"version":version["version"]}))
}

#[tauri::command]
pub async fn get_moondeck_status(host: String) -> Result<Value, String> {
    super::streaming::validate_host(&host)?;
    tauri::async_runtime::spawn_blocking(move || status(&host)).await.map_err(|e| e.to_string())?
}

#[tauri::command]
pub async fn pair_moondeck(host: String, pin: u32) -> Result<(), String> {
    super::streaming::validate_host(&host)?;
    if !(1000..=9999).contains(&pin) { return Err("PIN must have four digits".into()); }
    tauri::async_runtime::spawn_blocking(move || {
        let id = identity(&host)?;
        let hashed = base64::engine::general_purpose::STANDARD.encode(format!("{id}{pin}"));
        let result = request(&host, "pair", Some(json!({"id":id,"hashed_id":hashed})))?;
        if result["result"] == true { Ok(()) } else { Err("Buddy refused pairing. Check the host.".into()) }
    }).await.map_err(|e| e.to_string())?
}

pub fn launch(host: &str, steam_id: &str) -> Result<(), String> {
    if steam_id.is_empty() || steam_id.len() > 10 || !steam_id.bytes().all(|c| c.is_ascii_digit()) {
        return Err("Invalid Steam app ID".into());
    }
    if status(host)?["paired"] != true { return Err("Pair CreteOS with MoonDeck Buddy first".into()); }
    let deadline = std::time::Instant::now();
    while request(host, "streamState", None)?["state"] != 1 {
        if deadline.elapsed() > std::time::Duration::from_secs(20) { return Err("MoonDeckStream did not become ready".into()); }
        std::thread::sleep(std::time::Duration::from_millis(300));
    }
    if request(host, "launchSteam", Some(json!({"big_picture_mode":false,"username":null})))?["result"] != true {
        return Err("Buddy could not start Steam".into());
    }
    let deadline = std::time::Instant::now();
    loop {
        if request(host, "steamUiMode", None)?["mode"].as_u64().is_some_and(|mode| mode != 0) { break; }
        if deadline.elapsed() > std::time::Duration::from_secs(20) { return Err("Steam did not become ready".into()); }
        std::thread::sleep(std::time::Duration::from_millis(300));
    }
    if request(host, "launchSteamApp", Some(json!({"app_id":steam_id})))?["result"] != true {
        return Err("Buddy could not launch this game".into());
    }
    let deadline = std::time::Instant::now();
    loop {
        let data = request(host, "appData", Some(json!({"app_id":steam_id})))?;
        if data["data"]["app_state"] == 1 { break; }
        if deadline.elapsed() > std::time::Duration::from_secs(40) { return Err("Game did not start on Bosgame. Check installation or Steam's launch dialog.".into()); }
        std::thread::sleep(std::time::Duration::from_millis(500));
    }
    let host = host.to_owned(); let id = steam_id.to_owned();
    std::thread::spawn(move || {
        // End only the session still tracking this exact game, after observing
        // it running. Never close another user's game or shut down the host.
        loop {
            std::thread::sleep(std::time::Duration::from_secs(2));
            let Ok(state) = request(&host, "streamState", None) else { break; };
            if state["state"] != 1 { break; }
            let Ok(data) = request(&host, "appData", None) else { break; };
            if data["data"]["app_id"] != id { break; }
            if data["data"]["app_state"] == 0 {
                let _ = request(&host, "endStream", Some(json!({})));
                break;
            }
        }
    });
    Ok(())
}

pub fn prepare(host: &str, steam_id: &str) -> Result<(), String> {
    let active = request(host, "appData", None)?;
    if let Some(id) = active["data"]["app_id"].as_str() {
        if id != steam_id { return Err("Bosgame is already streaming a different game. End that session first.".into()); }
    }
    Ok(())
}

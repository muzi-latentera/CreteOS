// MoonDeck Buddy protocol: FrogTheFrog/moondeck defaults/python/lib/buddyrequests.py.
// Public service certificate: FrogTheFrog/moondeck-keys (not a private key).
use base64::Engine;
use serde_json::{json, Value};

fn state_is(value: &Value, number: u64, name: &str) -> bool {
    value.as_u64() == Some(number) || value.as_str() == Some(name)
}

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
    // Reuse the connection pool for lifecycle polling instead of doing a fresh
    // TCP/TLS handshake for every status request. Authorization remains per request.
    static CLIENT: std::sync::OnceLock<Result<reqwest::blocking::Client, String>> = std::sync::OnceLock::new();
    CLIENT.get_or_init(|| {
        let cert = reqwest::Certificate::from_pem(include_bytes!("moondeck_cert.pem")).map_err(|e| e.to_string())?;
        reqwest::blocking::Client::builder().tls_built_in_root_certs(false).add_root_certificate(cert)
            .danger_accept_invalid_hostnames(true).timeout(std::time::Duration::from_secs(5))
            .redirect(reqwest::redirect::Policy::none()).build().map_err(|e| e.to_string())
    }).clone()
}

fn request(host: &str, endpoint: &str, body: Option<Value>) -> Result<Value, String> {
    let id = identity(host)?;
    let auth = base64::engine::general_purpose::STANDARD.encode(id.as_bytes());
    let url = format!("https://{host}:59999/{endpoint}");
    let client = client()?;
    let builder = if let Some(body) = body {
        if endpoint == "appData" { client.get(url).json(&body) } else { client.post(url).json(&body) }
    } else { client.get(url) };
    let response = builder.header("Authorization", format!("basic {auth}")).send()
        .map_err(|_| "MoonDeck Buddy is unavailable on this host. Check the host's network connection.".to_owned())?;
    if !response.status().is_success() {
        // Pairing URLs contain the private client identity; report only the
        // route name, never the URL, authorization header or response body.
        let route = endpoint.split('/').next().unwrap_or("request");
        return Err(request_error(route, response.status().as_u16()));
    }
    response.json().map_err(|_| "Invalid MoonDeck Buddy response".to_owned())
}

fn request_error(route: &str, code: u16) -> String {
    match code {
        401 | 403 => format!("MoonDeck Buddy did not authorize {route} (HTTP {code}). Check CreteOS pairing."),
        404 => format!("MoonDeck Buddy does not support {route} (HTTP 404). Check the Buddy API version."),
        _ => format!("MoonDeck Buddy rejected {route} (HTTP {code})."),
    }
}

fn app_data_request(version: u64, steam_id: Option<&str>) -> (&'static str, Option<Value>) {
    // API 8 exposes only the app tracked by the current stream. API 9 adds
    // appData, including an optional query for a specific installed app.
    if version == 8 { ("streamedAppData", None) }
    else { ("appData", steam_id.map(|id| json!({"app_id":id}))) }
}

fn app_data(host: &str, version: u64, steam_id: Option<&str>) -> Result<Value, String> {
    let (route, body) = app_data_request(version, steam_id);
    request(host, route, body)
}

fn requested_app_running(data: &Value, steam_id: &str) -> bool {
    data["data"]["app_id"].as_str() == Some(steam_id) && state_is(&data["data"]["app_state"], 1, "Running")
}

pub fn status(host: &str) -> Result<Value, String> {
    let version = request(host, "apiVersion", None)?;
    if !matches!(version["version"].as_u64(), Some(8 | 9)) { return Err("Unsupported MoonDeck Buddy API version".into()); }
    let id = identity(host)?;
    let pairing = request(host, &format!("pairingState/{id}"), None)?;
    Ok(json!({"paired":state_is(&pairing["state"], 0, "Paired"),"version":version["version"]}))
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
    let buddy = status(host)?;
    if buddy["paired"] != true { return Err("Pair CreteOS with MoonDeck Buddy first".into()); }
    let version = buddy["version"].as_u64().ok_or("Missing Buddy API version")?;
    let deadline = std::time::Instant::now();
    while !state_is(&request(host, "streamState", None)?["state"], 1, "Streaming") {
        if deadline.elapsed() > std::time::Duration::from_secs(20) { return Err("MoonDeckStream did not become ready".into()); }
        std::thread::sleep(std::time::Duration::from_millis(300));
    }
    if request(host, "launchSteam", Some(json!({"big_picture_mode":false,"username":null})))?["result"] != true {
        return Err("Buddy could not start Steam".into());
    }
    let deadline = std::time::Instant::now();
    loop {
        let mode = request(host, "steamUiMode", None)?;
        if state_is(&mode["mode"], 1, "Desktop") || state_is(&mode["mode"], 2, "BigPicture") { break; }
        if deadline.elapsed() > std::time::Duration::from_secs(20) { return Err("Steam did not become ready".into()); }
        std::thread::sleep(std::time::Duration::from_millis(300));
    }
    if request(host, "launchSteamApp", Some(json!({"app_id":steam_id})))?["result"] != true {
        return Err("Buddy could not launch this game".into());
    }
    let deadline = std::time::Instant::now();
    loop {
        let data = app_data(host, version, Some(steam_id))?;
        if requested_app_running(&data, steam_id) { break; }
        if deadline.elapsed() > std::time::Duration::from_secs(40) { return Err("Game did not start on Bosgame. Check installation or Steam's launch dialog.".into()); }
        std::thread::sleep(std::time::Duration::from_millis(500));
    }
    Ok(())
}

// Called only after launch observed this exact app Running. Post-game shader
// processing is Updating, not Running, and must not strand the handheld in Steam.
fn exit_state(data: &Value, steam_id: &str) -> Option<bool> {
    if data["data"]["app_id"].as_str() != Some(steam_id) { return None; }
    let state = &data["data"]["app_state"];
    if state_is(state, 0, "Stopped") || state_is(state, 2, "Updating") { Some(true) }
    else if state_is(state, 1, "Running") { Some(false) }
    else { None }
}

#[derive(Debug, PartialEq)]
enum ExitNotification { Running, Exited, StreamEnded, OtherApp, Unknown }

// The notification contains all subscribed topics, in subscription order.
// As in MoonDeck, clearing the previously tracked app also means it ended.
fn exit_notification(value: &Value, id: &str) -> ExitNotification {
    let Some(items) = value.as_array().filter(|items| items.len() == 2) else { return ExitNotification::Unknown; };
    let app = &items[0];
    if let Some(other) = app["data"]["app_id"].as_str() {
        if other != id { return ExitNotification::OtherApp; }
    }
    let stream = &items[1]["state"];
    if state_is(stream, 0, "NotStreaming") || state_is(stream, 2, "StreamEnding") { return ExitNotification::StreamEnded; }
    if !state_is(stream, 1, "Streaming") { return ExitNotification::Unknown; }
    if app.get("data").is_some_and(Value::is_null) { return ExitNotification::Exited; }
    match exit_state(app, id) {
        Some(true) => ExitNotification::Exited,
        Some(false) => ExitNotification::Running,
        None => ExitNotification::Unknown,
    }
}

// Follow MoonDeck's notifyOnChanges feed instead of waiting for two sequential
// HTTP polls. Retain polling if this Buddy version/connection cannot subscribe.
fn watch_exit_notifications(host: &str, id: &str, current: &impl Fn() -> bool, ending: &impl Fn(), note: &impl Fn(&str)) -> Option<bool> {
    use std::net::{TcpStream, ToSocketAddrs};
    use std::time::{Duration, Instant};
    use tungstenite::{client::IntoClientRequest, Connector, Message};
    let cert = native_tls::Certificate::from_pem(include_bytes!("moondeck_cert.pem")).ok()?;
    let tls = native_tls::TlsConnector::builder().disable_built_in_roots(true)
        .add_root_certificate(cert).danger_accept_invalid_hostnames(true).build().ok()?;
    let address = (host, 59999).to_socket_addrs().ok()?.take(4)
        .find_map(|address| TcpStream::connect_timeout(&address, Duration::from_secs(2)).ok())?;
    address.set_read_timeout(Some(Duration::from_secs(3))).ok()?;
    address.set_write_timeout(Some(Duration::from_secs(3))).ok()?;
    let mut request = format!("wss://{host}:59999/notifyOnChanges").into_client_request().ok()?;
    let auth = base64::engine::general_purpose::STANDARD.encode(identity(host).ok()?.as_bytes());
    request.headers_mut().insert("Authorization", format!("basic {auth}").parse().ok()?);
    let config = tungstenite::protocol::WebSocketConfig::default()
        .max_message_size(Some(16 * 1024)).max_frame_size(Some(16 * 1024));
    // Never expose handshake errors: they can contain the private auth header.
    let (mut socket, _) = tungstenite::client_tls_with_config(request, address, Some(config), Some(Connector::NativeTls(tls))).ok()?;
    socket.send(Message::Text(json!(["StreamedAppData", "StreamState"]).to_string().into())).ok()?;
    let ack = socket.read().ok()?;
    let ack: Value = serde_json::from_str(ack.to_text().ok()?).ok()?;
    if ack["result"] != true { return None; }
    match socket.get_mut() {
        tungstenite::stream::MaybeTlsStream::NativeTls(stream) => stream.get_mut().set_read_timeout(Some(Duration::from_millis(500))).ok()?,
        _ => return None, // This feed must always use pinned TLS.
    }
    note("buddy-live-events");
    let mut last_ping = Instant::now();
    let mut last_message = Instant::now();
    while current() {
        match socket.read() {
            Ok(Message::Text(text)) => {
                last_message = Instant::now();
                let value: Value = serde_json::from_str(&text).ok()?;
                if !current() { return Some(false); }
                match exit_notification(&value, id) {
                    ExitNotification::Exited => {
                        note("buddy-game-exit-event");
                        ending();
                        return Some(request_end_stream(host));
                    }
                    ExitNotification::StreamEnded => { note("buddy-stream-end-event"); ending(); return Some(true); }
                    ExitNotification::OtherApp => return Some(false),
                    ExitNotification::Running => {},
                    ExitNotification::Unknown => return None,
                }
            }
            Ok(Message::Ping(_)) | Ok(Message::Pong(_)) => { last_message = Instant::now(); socket.flush().ok()?; }
            Err(tungstenite::Error::Io(error)) if matches!(error.kind(), std::io::ErrorKind::WouldBlock | std::io::ErrorKind::TimedOut) => {},
            _ => return None,
        }
        if last_message.elapsed() >= Duration::from_secs(30) { return None; }
        if last_ping.elapsed() >= Duration::from_secs(10) {
            socket.send(Message::Ping(Vec::new().into())).ok()?;
            last_ping = Instant::now();
        }
    }
    Some(false)
}

fn request_end_stream(host: &str) -> bool {
    request(host, "endStream", Some(json!({}))).is_ok_and(|reply| reply["result"] == true)
}

pub fn watch_exit(host: &str, id: &str, current: impl Fn() -> bool, ending: impl Fn(), note: impl Fn(&str)) -> bool {
        if let Some(ended) = watch_exit_notifications(host, id, &current, &ending, &note) { return ended; }
        note("buddy-poll-fallback");
        let Ok(buddy) = status(host) else { return false; };
        let Some(version) = buddy["version"].as_u64() else { return false; };
        let mut exited_samples = 0;
        let mut failures = 0;
        // End only the session still tracking this exact game, after observing
        // it running. Never close another user's game or shut down the host.
        while current() {
            std::thread::sleep(std::time::Duration::from_millis(500));
            if !current() { return false; }
            let result = request(host, "streamState", None).and_then(|state| {
                if !state_is(&state["state"], 1, "Streaming") { return Ok(None); }
                app_data(host, version, None).map(Some)
            });
            match result {
                Ok(None) => return true,
                Ok(Some(data)) => {
                    failures = 0;
                    match exit_state(&data, id) {
                        Some(true) => exited_samples += 1,
                        Some(false) => exited_samples = 0,
                        None => return false, // Another/unknown app: never end its stream.
                    }
                    if exited_samples >= 2 && current() {
                        ending();
                        return request_end_stream(host);
                    }
                }
                Err(_) => { exited_samples = 0; failures += 1; if failures >= 5 { return false; } }
            }
        }
        false
}

pub fn prepare(host: &str, steam_id: &str) -> Result<(), String> {
    let buddy = status(host)?;
    let version = buddy["version"].as_u64().ok_or("Missing Buddy API version")?;
    let active = app_data(host, version, None)?;
    if let Some(id) = active["data"]["app_id"].as_str() {
        if id != steam_id { return Err("Bosgame is already streaming a different game. End that session first.".into()); }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use serde_json::Value;
    #[test]
    fn live_exit_notification_reacts_without_a_second_poll() {
        use super::ExitNotification::*;
        let message = |data: Value, state: Value| serde_json::json!([{"data":data},{"state":state}]);
        assert_eq!(super::exit_notification(&message(serde_json::json!({"app_id":"883710","app_state":1}), serde_json::json!(1)), "883710"), Running);
        assert_eq!(super::exit_notification(&message(serde_json::json!({"app_id":"883710","app_state":0}), serde_json::json!(1)), "883710"), Exited);
        assert_eq!(super::exit_notification(&message(serde_json::json!({"app_id":"883710","app_state":"Updating"}), serde_json::json!("Streaming")), "883710"), Exited);
        assert_eq!(super::exit_notification(&message(Value::Null, serde_json::json!(1)), "883710"), Exited);
        assert_eq!(super::exit_notification(&message(Value::Null, serde_json::json!(2)), "883710"), StreamEnded);
        assert_eq!(super::exit_notification(&message(serde_json::json!({"app_id":"other","app_state":0}), serde_json::json!(0)), "883710"), OtherApp);
        assert_eq!(super::exit_notification(&serde_json::json!([{}, {"state":1}]), "883710"), Unknown);
        assert_eq!(super::exit_notification(&serde_json::json!({"result":false}), "883710"), Unknown);
    }
    #[test]
    fn post_game_updating_is_exit_only_for_the_same_known_app() {
        assert_eq!(super::exit_state(&serde_json::json!({"data":{"app_id":"883710","app_state":"Updating"}}), "883710"), Some(true));
        assert_eq!(super::exit_state(&serde_json::json!({"data":{"app_id":"883710","app_state":1}}), "883710"), Some(false));
        assert_eq!(super::exit_state(&serde_json::json!({"data":{"app_id":"other","app_state":"Stopped"}}), "883710"), None);
        assert_eq!(super::exit_state(&serde_json::json!({"data":null}), "883710"), None);
    }
    #[test]
    fn uses_api_8_tracked_app_route_and_api_9_optional_app_query() {
        assert_eq!(super::app_data_request(8, None), ("streamedAppData", None));
        assert_eq!(super::app_data_request(8, Some("883710")), ("streamedAppData", None));
        assert_eq!(super::app_data_request(9, None), ("appData", None));
        assert_eq!(super::app_data_request(9, Some("883710")), ("appData", Some(serde_json::json!({"app_id":"883710"}))));
    }
    #[test]
    fn tracked_app_running_must_match_requested_game() {
        let data = serde_json::json!({"data":{"app_id":"883710","app_state":"Running"}});
        assert!(super::requested_app_running(&data, "883710"));
        assert!(!super::requested_app_running(&data, "1649240"));
        assert!(!super::requested_app_running(&serde_json::json!({"data":null}), "883710"));
        assert!(!super::requested_app_running(&serde_json::json!({"data":{"app_id":"883710","app_state":"Stopped"}}), "883710"));
    }
    #[test]
    fn distinguishes_missing_route_from_pairing_rejection() {
        assert!(!super::request_error("appData", 404).contains("pairing"));
        assert!(super::request_error("appData", 404).contains("HTTP 404"));
        assert!(super::request_error("streamedAppData", 401).contains("pairing"));
        assert!(super::request_error("launchSteam", 500).contains("launchSteam"));
    }
    #[test]
    fn accepts_buddy_wire_enum_names_without_treating_missing_as_ready() {
        assert!(super::state_is(&serde_json::json!("Paired"), 0, "Paired"));
        assert!(super::state_is(&serde_json::json!(0), 0, "Paired"));
        assert!(!super::state_is(&serde_json::Value::Null, 0, "Paired"));
        assert!(!super::state_is(&serde_json::json!("NotPaired"), 0, "Paired"));
        assert!(super::state_is(&serde_json::json!("Streaming"), 1, "Streaming"));
        assert!(super::state_is(&serde_json::json!("Running"), 1, "Running"));
    }
}

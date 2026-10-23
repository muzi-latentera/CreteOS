use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use std::io::{Read, Write};
use std::sync::atomic::{AtomicU64, Ordering};
use tauri::Manager;

static SESSION_GENERATION: AtomicU64 = AtomicU64::new(0);
static LAUNCH_EPOCH: AtomicU64 = AtomicU64::new(0);
type OwnedClient = std::sync::Arc<std::sync::Mutex<std::process::Child>>;
static PENDING_MOONLIGHT: std::sync::Mutex<Option<(u64, OwnedClient)>> = std::sync::Mutex::new(None);
pub(crate) fn progress(window:&tauri::Window,message:&str) {
    use tauri::Emitter;
    super::stream_artwork::message(window,message);
    let _=window.emit("stream-progress",serde_json::json!({"message":message}));
}
#[tauri::command(async)]
pub fn present_stream_failure(window:tauri::Window,provider:String)->Result<(),String>{
    if !matches!(provider.as_str(),"moonlight"|"geforceNow"|"luna"){return Err("Unknown streaming client".into());}
    if super::can_return_from_stream(&window,&provider){
        super::fse_watcher::prepare_stream_return(window.app_handle());
        super::show_stream_return(&window)?;
    }
    Ok(())
}
#[tauri::command]
pub fn record_stream_issue(provider:String,game_id:String,message:String)->Result<(),String>{
    use std::io::Write;
    if !matches!(provider.as_str(),"moonlight"|"geforceNow"|"luna") || game_id.len()>1024{return Err("Invalid stream issue".into());}
    // Omit URL/header-bearing errors rather than retaining credentials in logs.
    let message=if ["http:","https:","Authorization","Bearer "].iter().any(|v|message.contains(v)){"Provider request failed (details omitted)".into()}else{message.chars().take(500).filter(|c|!c.is_control()).collect::<String>()};
    let folder=super::liftoff_dir().join("logs");std::fs::create_dir_all(&folder).map_err(|e|e.to_string())?;
    let file=folder.join("stream-issues.jsonl");if std::fs::metadata(&file).is_ok_and(|m|m.len()>256*1024){let _=std::fs::rename(&file,file.with_extension("previous.jsonl"));}
    let mut file=std::fs::OpenOptions::new().create(true).append(true).open(file).map_err(|e|e.to_string())?;
    writeln!(file,"{}",serde_json::json!({"at":super::unix_now_i64(),"provider":provider,"game":game_id,"message":message})).map_err(|e|e.to_string())
}
pub(crate) fn launch_epoch() -> u64 { LAUNCH_EPOCH.load(Ordering::SeqCst) }
pub(crate) fn launch_current(epoch: u64) -> bool { launch_epoch() == epoch }
fn check_launch(epoch: u64) -> Result<(), String> {
    if launch_current(epoch) { Ok(()) } else { Err("Stream launch cancelled".into()) }
}
pub(crate) fn cancel_pending_launch() {
    LAUNCH_EPOCH.fetch_add(1, Ordering::SeqCst);
    let pending = PENDING_MOONLIGHT.lock().ok().and_then(|mut pending| pending.take());
    if let Some((_, client)) = pending {
        // Close only the client created for this pending launch. Never kill a
        // shared Moonlight process or the remote game/Steam session.
        std::thread::spawn(move || {
            if let Ok(child) = client.lock() {
                super::close_owned_moonlight_client(child.id(), || true);
            }
        });
    }
}
pub(crate) fn complete_pending_launch() {
    if let Ok(mut pending) = PENDING_MOONLIGHT.lock() { *pending = None; }
}
#[tauri::command(async)]
pub fn cancel_stream_launch(window: tauri::Window) -> Result<(), String> {
    cancel_pending_launch();
    super::launch_cover::set_launch_cover(window.clone(), false, None, None)?;
    super::fse_watcher::prepare_stream_return(window.app_handle());
    window.show().map_err(|e| e.to_string())?;
    window.set_focus().map_err(|e| e.to_string())
}
static RETURN_COVER: AtomicU64 = AtomicU64::new(0);
static RETURN_PAINTED: AtomicU64 = AtomicU64::new(0);
static RETURN_PRESENTATION: std::sync::Mutex<()> = std::sync::Mutex::new(());
static ENDED_SESSION_GENERATION: AtomicU64 = AtomicU64::new(0);
// 0 unavailable/fallback, 1 waiting for the exact game, 2 visible, 3 closed.
static HOST_GAME_WINDOW: AtomicU64 = AtomicU64::new(0);
fn host_window_state() -> u64 {
    let value = HOST_GAME_WINDOW.load(Ordering::SeqCst);
    if value >> 3 == SESSION_GENERATION.load(Ordering::SeqCst) { value & 7 } else { 0 }
}
fn set_host_window_state(generation: u64, state: u64) {
    let _ = HOST_GAME_WINDOW.fetch_update(Ordering::SeqCst, Ordering::SeqCst, |value|
        if value >> 3 == generation { Some((generation << 3) | state) } else { None });
}
pub(crate) fn moonlight_game_waiting() -> bool { host_window_state() == 1 }
pub(crate) fn moonlight_game_closed() -> bool { host_window_state() == 3 }
fn next_session() -> u64 {
    let generation = SESSION_GENERATION.fetch_add(1, Ordering::SeqCst) + 1;
    RETURN_COVER.store(0, Ordering::SeqCst); RETURN_PAINTED.store(0, Ordering::SeqCst); HOST_GAME_WINDOW.store(generation << 3, Ordering::SeqCst);
    generation
}

#[tauri::command]
pub async fn launch_luna_session(window:tauri::Window, game_id:String, game_name:String, url:String) -> Result<(),String> {
    if !super::luna::luna_url(&url) || game_id.is_empty() || game_id.len()>1024 {return Err("Invalid Luna game".into());}
    tauri::async_runtime::spawn_blocking(move||{
        let wifi=super::stream_wifi::prepare("luna");
        let nonce=super::luna_session::begin(&game_id,&url)?;
        let epoch=launch_epoch();
        let generation=next_session();
        if let Err(error)=super::luna::launch(&url).ok_or("Invalid Luna launch")? {super::luna_session::finish(&nonce);return Err(error);}
        std::thread::spawn(move||{
            let _wifi=wifi;let started=std::time::Instant::now();let mut found=0isize;let mut owner=0u32;let mut observed=false;let mut previous=None;
            while session_current(generation) && launch_current(epoch) {
                if found==0 {if let Some((target,_))=super::best_launch_window(&game_name,&url,"cloud"){found=target.hwnd;owner=target.pid;}}
                let phase=super::luna_session::phase();
                if previous!=Some(phase) {
                    super::fse_watcher::log_stream_transition(window.app_handle(),match phase {super::luna_session::Phase::Waiting=>"luna-phase-waiting",super::luna_session::Phase::Buffering=>"luna-phase-buffering",super::luna_session::Phase::Playing=>"luna-phase-playing",super::luna_session::Phase::Attention=>"luna-phase-attention",super::luna_session::Phase::Ended=>"luna-phase-ended"});
                    progress(&window,match phase {super::luna_session::Phase::Waiting=>"Connecting to Amazon Luna…",super::luna_session::Phase::Buffering=>"Waiting for Luna video…",super::luna_session::Phase::Playing=>"Luna stream ready",super::luna_session::Phase::Attention=>"Amazon Luna needs your attention",super::luna_session::Phase::Ended=>"Returning to CreteOS…"});previous=Some(phase);
                }
                if phase==super::luna_session::Phase::Playing {observed=true;}
                let closed=found!=0 && !super::collect_launch_windows().iter().any(|w|w.hwnd==found && w.pid==owner);
                if phase==super::luna_session::Phase::Ended || closed || (!observed && started.elapsed()>std::time::Duration::from_secs(300)) {
                    super::luna_session::finish(&nonce);
                    if observed && session_current(generation) {
                        begin_return(&window,"luna",generation);
                        // Close only this tracked PWA, after artwork is painted.
                        if found!=0 && super::can_return_from_stream(&window,"luna") && super::collect_launch_windows().iter().any(|w|w.hwnd==found && w.pid==owner) {
                            use windows::Win32::{Foundation::{HWND,WPARAM,LPARAM},UI::WindowsAndMessaging::{PostMessageW,WM_CLOSE}};
                            unsafe {let _=PostMessageW(Some(HWND(found as _)),WM_CLOSE,WPARAM(0),LPARAM(0));}
                            std::thread::sleep(std::time::Duration::from_millis(500));
                        }
                        end_return(&window,"luna",generation);
                    }
                    break;
                }
                std::thread::sleep(std::time::Duration::from_millis(200));
            }
            super::luna_session::finish(&nonce);
        });
        Ok(())
    }).await.map_err(|e|e.to_string())?
}
fn session_current(generation: u64) -> bool { SESSION_GENERATION.load(Ordering::SeqCst) == generation }

fn begin_return(window: &tauri::Window, provider: &str, generation: u64) {
    if !session_current(generation) || !super::can_return_from_stream(window, provider) { return; }
    if RETURN_COVER.compare_exchange(0, generation, Ordering::SeqCst, Ordering::SeqCst).is_ok() {
        use tauri::Emitter;
        let _ = window.emit("stream-return-phase", serde_json::json!({"provider":provider,"phase":"ending","session":generation}));
        super::fse_watcher::prepare_stream_return(window.app_handle());
    }
    // A background/cloaked WebView may pause requestAnimationFrame. The native
    // image can paint and restore our foreground before browser/client cleanup,
    // without waiting for that frontend callback. Its late ack is generation-
    // checked by the same presentation function.
    if super::stream_artwork::native_available() {
        super::stream_artwork::message(window,"");
        let _=present_stream_return_cover(window.clone(),provider.into(),generation);
    }
    // Both the game-window watcher and Buddy may report exit. Each cleanup
    // caller must wait for the same painted, foreground artwork before ending
    // the host stream. This worker wait never blocks the UI message pump.
    let start = std::time::Instant::now();
    while session_current(generation) && RETURN_COVER.load(Ordering::SeqCst) == generation
        && RETURN_PAINTED.load(Ordering::SeqCst) != generation
        && start.elapsed() < std::time::Duration::from_millis(1500) {
        std::thread::sleep(std::time::Duration::from_millis(10));
    }
}

pub(crate) fn end_return(window: &tauri::Window, provider: &str, generation: u64) {
    let Ok(_presentation) = RETURN_PRESENTATION.lock() else { return; };
    if RETURN_COVER.compare_exchange(generation, 0, Ordering::SeqCst, Ordering::SeqCst).is_ok() {
        use tauri::Emitter;
        let _ = super::launch_cover::set_launch_cover(window.clone(), false, None, None);
        super::fse_watcher::log_stream_transition(window.app_handle(), "return-cover-released");
        let _ = window.emit("stream-return-phase", serde_json::json!({"provider":provider,"phase":"ended","session":generation}));
    }
    // Session completion is data, not a foreground/presentation operation.
    // Alt-Tab may legitimately prevent begin_return from displaying artwork.
    if session_current(generation) && ENDED_SESSION_GENERATION.fetch_max(generation, Ordering::SeqCst) < generation {
        use tauri::Emitter;
        let _ = window.emit("stream-session-ended", serde_json::json!({"provider":provider,"session":generation}));
    }
}

// Runs off the UI thread: cover release may wait for the native foreground
// guard, which itself needs the UI message pump to remain responsive.
#[tauri::command(async)]
pub fn present_stream_return_cover(window: tauri::Window, provider: String, session: u64) -> Result<bool, String> {
    if !matches!(provider.as_str(), "moonlight" | "geforceNow" | "luna") { return Err("Unknown streaming client".into()); }
    let _presentation = RETURN_PRESENTATION.lock().map_err(|_| "Return presentation unavailable")?;
    if !session_current(session) || RETURN_COVER.load(Ordering::SeqCst) != session { return Ok(false); }
    // The client may close between the ending event and the paint ack. Only a
    // request originating from our own foreground stream can reach this point.
    if !super::can_return_from_stream(&window, &provider) && !super::foreground_is_desktop() { return Ok(false); }
    super::launch_cover::set_launch_cover(window.clone(), true, None, Some(provider))?;
    if let Err(error) = super::show_stream_return(&window) {
        let _ = super::launch_cover::set_launch_cover(window.clone(), false, None, None);
        return Err(error);
    }
    RETURN_PAINTED.store(session, Ordering::SeqCst);
    super::fse_watcher::log_stream_transition(window.app_handle(), "return-cover-painted");
    // Keep artwork and the native guard through client shutdown. end_return
    // releases both only after the owned client has closed and cleanup ended.
    Ok(true)
}

fn nvidia_console() -> PathBuf {
    dirs::data_local_dir().unwrap_or_default().join("NVIDIA Corporation/GeForceNOW/console.log")
}

#[derive(Default)]
struct NvidiaSession { started: bool, ending: bool, ended: bool }
impl NvidiaSession {
    fn observe(&mut self, line: &str) {
        let line = line.split_whitespace().collect::<Vec<_>>().join(" ");
        if line.contains("NvCameraService streaming state change: Streaming") || line.contains("got onStreamingFirstFrame event") {
            self.started = true; self.ending=false; self.ended = false;
        } else if self.started && (line.contains("NvCameraService streaming state change: PostSessionConnection") || line.contains("NvCameraService streaming state change: PostStreaming")) { self.ending=true;
        } else if self.started && line.contains("NvCameraService streaming state change: Done") { self.ended = true; }
        else if self.ended && line.contains("NvCameraService streaming state change:") {
            // A new native session can start without CreteOS. Stop cleanup as
            // soon as NVIDIA leaves Done, before it reaches Streaming again.
            self.ended = false; self.ending=false;
        }
    }
}

fn watch_geforce_exit(window: tauri::Window, mut log: super::stream_readiness::LogCursor, generation: u64, wifi: Option<super::stream_wifi::Session>) {
    std::thread::spawn(move || {
        let _wifi = wifi;
        let mut session = NvidiaSession::default();
        let mut returning=false;
        let start = std::time::Instant::now();
        while session_current(generation) && start.elapsed() < std::time::Duration::from_secs(86400) {
            for line in log.lines() { session.observe(&line); }
            if returning && !session.ending && !session.ended {
                // A new provider session superseded the one returning. Release
                // artwork without closing either provider window.
                end_return(&window,"geforceNow",generation);return;
            }
            if session.ending && !returning {begin_return(&window,"geforceNow",generation);returning=true;}
            if session.ended {
                begin_return(&window, "geforceNow", generation);
                super::finish_stream_session(window.clone(), "geforceNow", || {
                    for line in log.lines() { session.observe(&line); }
                    session_current(generation) && session.ended
                });
                end_return(&window, "geforceNow", generation);
                return;
            }
            if session.started && !super::is_process_running("GeForceNOWStreamer.exe") && !super::is_process_running("GeForceNOW.exe") { return; }
            // A membership queue can legitimately outlast three minutes. Keep
            // its selected network while the native streamer is still open.
            if !session.started && start.elapsed() > std::time::Duration::from_secs(180)
                && !super::is_process_running("GeForceNOWStreamer.exe") { return; }
            std::thread::sleep(std::time::Duration::from_millis(100));
        }
    });
}

fn warm_geforce_client() -> Result<(), String> {
    // External Streamer shortcuts can calculate Balanced before the initial
    // SharedStorage update arrives. Initialize the main client first, then
    // dispatch the shortcut only after native settings/capabilities are loaded.
    let mut log = super::stream_readiness::LogCursor::new(nvidia_console());
    std::process::Command::new(client_path("geforceNow")?).spawn().map_err(|e| e.to_string())?;
    let start = std::time::Instant::now();
    let mut capabilities = false; let mut storage = false;
    while start.elapsed() < std::time::Duration::from_secs(25) {
        for line in log.lines() {
            capabilities |= line.contains("prefetchDeviceCapabilities response:") && line.contains("true");
            storage |= line.contains("shared storage cache updated");
        }
        if capabilities && storage && start.elapsed() >= std::time::Duration::from_secs(5) { return Ok(()); }
        std::thread::sleep(std::time::Duration::from_millis(200));
    }
    Err("NVIDIA has not finished loading its settings. No game was started. Open its Settings screen, then retry.".into())
}

pub(crate) fn bounded_output(mut command: std::process::Command, input: Option<&[u8]>) -> Result<std::process::Output, String> {
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
pub async fn launch_geforce_game(window: tauri::Window, game_url: String, source: Option<String>, play: Option<bool>) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || {
        let (client, argument) = geforce_launch(&game_url, source.as_deref(), play.unwrap_or(true))?;
        let wifi = if play.unwrap_or(true) { super::stream_wifi::prepare("geforceNow") } else { None };
        let log = super::stream_readiness::LogCursor::new(nvidia_console());
        spawn_geforce(&client, &argument)?;
        if play.unwrap_or(true) { watch_geforce_exit(window, log, next_session(), wifi); }
        Ok(())
    }).await.map_err(|e| e.to_string())?
}

fn geforce_raw_route(argument: &str) -> Result<String, String> {
    let route = argument.strip_prefix("--url-route=").ok_or("Missing NVIDIA route")?;
    if route.contains('"') || route.chars().any(char::is_control) { return Err("Invalid NVIDIA route".into()); }
    // NVIDIA's proxy reads its raw Windows command line. Preserve the quoted
    // value exactly as NVIDIA-created desktop shortcuts do.
    Ok(format!("--url-route=\"{route}\""))
}
fn spawn_geforce(client: &std::path::Path, argument: &str) -> Result<(), String> {
    let mut command = std::process::Command::new(client);
    command.current_dir(client.parent().ok_or("Missing NVIDIA client directory")?);
    #[cfg(windows)] {
        use std::os::windows::process::CommandExt;
        if argument.starts_with("--url-route=") { command.raw_arg(geforce_raw_route(argument)?); }
        else { command.arg(argument); }
    }
    #[cfg(not(windows))] { command.arg(argument); }
    command.spawn().map(|_| ()).map_err(|error| error.to_string())
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
    if !resolutions.contains(&profile.resolution.as_str()) || ![30,60,120,240].contains(&profile.fps) || (profile.bitrate != 0 && !(5000..=100000).contains(&profile.bitrate)) { return Err("Unsupported GeForce NOW settings".into()); }
    let config = settings.get_mut("appSettingsConfig").ok_or("GeForce NOW settings format is unsupported; use its Settings screen")?;
    let custom = config.get_mut("customProfile").filter(|v| v.is_object()).ok_or("Configure Custom quality in GeForce NOW once, then retry")?;
    if !["width", "height", "fps", "maxBitrate"].iter().all(|key| custom.get(key).is_some()) { return Err("GeForce NOW profile format is unsupported; use its Settings screen".into()); }
    let (width, height) = profile.resolution.split_once('x').ok_or("Invalid resolution")?;
    custom["width"] = serde_json::json!(width.parse::<u32>().map_err(|_| "Invalid width")?);
    custom["height"] = serde_json::json!(height.parse::<u32>().map_err(|_| "Invalid height")?);
    custom["fps"] = serde_json::json!(profile.fps); custom["maxBitrate"] = serde_json::json!(profile.bitrate / 1000);
    // NVIDIA uses zero Mbps plus the Auto flag for an automatic bitrate.
    custom["maxBitrateAuto"] = serde_json::json!(profile.bitrate == 0);
    // Match NVIDIA's actual Custom mode, including the native mode-selection
    // enum. Retaining Balanced metadata can select its automatic video mode.
    custom["name"] = serde_json::json!("custom");
    custom["profile"] = serde_json::json!(0);
    custom["description"] = serde_json::json!("settings.streamingProfiles.custom");
    custom["aspectRatio"] = serde_json::json!(if profile.resolution.ends_with("1200") || profile.resolution.ends_with("1600") { "(16:10)" } else { "(16:9)" });
    config["streamingProfiles"] = serde_json::json!("custom");
    Ok(())
}

#[tauri::command]
pub async fn start_geforce_game(window: tauri::Window, game_url: String, display_key: String, profile: MoonlightProfile, source: Option<String>) -> Result<(), String> {
    geforce_native_game_uri(&game_url)?;
    if display_key.is_empty() || display_key.len() > 256 { return Err("Invalid display identity".into()); }
    tauri::async_runtime::spawn_blocking(move || {
        let (client, argument) = geforce_launch(&game_url, source.as_deref(), true)?;
        let mut command = std::process::Command::new("tasklist.exe");
        command.args(["/FO", "CSV", "/NH"]);
        let output = bounded_output(command, None)?;
        if !output.status.success() { return Err("Could not check whether GeForce NOW is running".into()); }
        let processes = String::from_utf8_lossy(&output.stdout).to_ascii_lowercase();
        if processes.contains("geforcenow.exe") || processes.contains("geforcenowstreamer.exe") { return Err("Close GeForce NOW before pressing Play so your chosen settings can be applied.".into()); }
        let wifi = super::stream_wifi::prepare("geforceNow");
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
        let generation = next_session();
        warm_geforce_client()?;
        let readback: serde_json::Value = serde_json::from_slice(&std::fs::read(&path).map_err(|e| e.to_string())?).map_err(|e| e.to_string())?;
        let expected = &settings["appSettingsConfig"];
        let actual = &readback["appSettingsConfig"];
        if actual["streamingProfiles"] != expected["streamingProfiles"] || ["width", "height", "fps", "maxBitrate", "maxBitrateAuto", "name", "profile"].iter().any(|key| actual["customProfile"][*key] != expected["customProfile"][*key]) {
            return Err("NVIDIA changed the selected profile during startup. No game was started; check its Custom quality settings.".into());
        }
        let log = super::stream_readiness::LogCursor::new(nvidia_console());
        let launch = spawn_geforce(&client, &argument);
        if let Err(error) = launch { let _ = std::fs::write(&path, original); return Err(error.to_string()); }
        watch_geforce_exit(window, log, generation, wifi);
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

pub(crate) fn host_command(host: &str) -> Result<std::process::Command, String> {
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
        .arg(format!("UserKnownHostsFile={}", known.display())).arg(format!("{username}@{host}"));
    Ok(command)
}

fn host_games(host: &str) -> Result<Vec<HostGame>, String> {
    let mut command = host_command(host)?;
    command.arg("python3 -");
    let output = bounded_output(command, Some(include_bytes!("steam_inventory.py")))?;
    if !output.status.success() { return Err("Could not read Bosgame's Steam library. Check SSH access and that Steam is installed for the host login.".into()); }
    let games: Vec<HostGame> = serde_json::from_slice(&output.stdout).map_err(|_| "Invalid host inventory response")?;
    if games.len() > 5000 || games.iter().any(|game| game.steam_app_id.len() > 10 || game.steam_app_id.is_empty() || !game.steam_app_id.bytes().all(|c| c.is_ascii_digit())) {
        return Err("Invalid host inventory".into());
    }
    Ok(games)
}

const STEAM_WRONG_DESKTOP: &str = "Steam is open on Bosgame's regular desktop. Finish any running game and restart Steam on its streaming desktop before starting Moonlight.";

fn stage_host_presentation(host: &str, id: &str, resolution: &str, game_id: Option<&str>, game_name: Option<&str>, generation: u64) -> Result<String, String> {
    use base64::Engine;
    let custom = game_id.and_then(|id| super::load_custom_art_inner().get(&format!("hero:{id}")).cloned());
    let art = custom.or_else(|| game_name.and_then(|name| super::load_hero_cache().get(name).cloned()));
    let bytes = art.and_then(|art| {
        if art.starts_with("data:image/") {
            return art.split_once(";base64,").and_then(|(_, data)| {
                if data.len() > 12 * 1024 * 1024 { return None; }
                base64::engine::general_purpose::STANDARD.decode(data).ok()
            });
        }
        // Only cached artwork files are transferred; never an arbitrary path
        // supplied by a frontend or a fresh internet request during launching.
        let path = std::fs::canonicalize(art).ok()?;
        let root = std::fs::canonicalize(super::liftoff_dir()).ok()?;
        if !path.starts_with(root) || std::fs::metadata(&path).ok()?.len() > 8 * 1024 * 1024 { return None; }
        std::fs::read(path).ok()
    }).unwrap_or_default();
    if bytes.len() > 8 * 1024 * 1024 { return Err("Host artwork is too large".into()); }
    let token = format!("{}-{}-{}", std::process::id(), generation, std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).map_err(|e| e.to_string())?.as_micros());
    let source = include_str!("host_presentation.py");
    let payload = serde_json::json!({"appId": id, "token": token, "resolution": resolution, "art": base64::engine::general_purpose::STANDARD.encode(bytes),
        "messages": serde_json::from_str::<serde_json::Value>(include_str!("../../src/components/launch/launchMessages.json")).map_err(|e| e.to_string())?, "source": source});
    let script = format!("import json\nPAYLOAD=json.loads({})\n{}", serde_json::to_string(&payload.to_string()).map_err(|e| e.to_string())?, source);
    let mut command = host_command(host)?;
    command.arg("python3 -");
    let output = bounded_output(command, Some(script.as_bytes()))?;
    let result: serde_json::Value = serde_json::from_slice(&output.stdout).map_err(|_| "Host artwork is unavailable")?;
    if result["blocked"] == "steamWrongDesktop" { return Err(STEAM_WRONG_DESKTOP.into()); }
    if output.status.success() && result["token"].as_str() == Some(token.as_str()) { Ok(token) }
    else { Err("Host artwork is unavailable".into()) }
}

fn finish_host_presentation(host: &str, token: &str) {
    if token.len() > 80 || !token.bytes().all(|b| b.is_ascii_hexdigit() || b == b'-') { return; }
    if let Ok(mut command) = host_command(host) {
        command.arg("python3 -");
        let script = format!("from pathlib import Path\np=Path.home()/'.local/share/creteos-host'/'{token}'\nif p.is_dir(): (p/'finish').touch()\n");
        let _ = bounded_output(command, Some(script.as_bytes()));
    }
}

fn watch_host_game_window(window: tauri::Window, host: String, id: String, presentation: Option<String>, generation: u64, active: std::sync::Arc<std::sync::atomic::AtomicBool>) {
    set_host_window_state(generation, 1);
    std::thread::spawn(move || {
        let result = (|| -> Result<(), ()> {
            use std::io::BufRead;
            if id.is_empty() || id.len() > 10 || !id.bytes().all(|c| c.is_ascii_digit()) { return Err(()); }
            let mut command = host_command(&host).map_err(|_| ())?;
            command.arg(format!("python3 - {id} {}", presentation.as_deref().unwrap_or("none")))
                .stdin(std::process::Stdio::piped()).stdout(std::process::Stdio::piped()).stderr(std::process::Stdio::null());
            #[cfg(windows)] { use std::os::windows::process::CommandExt; command.creation_flags(0x08000000); }
            let mut child = command.spawn().map_err(|_| ())?;
            let written = child.stdin.take().ok_or(()).and_then(|mut stdin| stdin.write_all(include_bytes!("host_window_watch.py")).map_err(|_| ()));
            if written.is_err() { let _ = child.kill(); let _ = child.wait(); return Err(()); }
            let Some(stdout) = child.stdout.take() else { let _ = child.kill(); let _ = child.wait(); return Err(()); };
            let (sender, receiver) = std::sync::mpsc::sync_channel(8);
            let reader = std::thread::spawn(move || {
                for line in std::io::BufReader::new(stdout).lines().map_while(Result::ok) {
                    if line.len() > 256 { break; }
                    let event = serde_json::from_str::<serde_json::Value>(&line).ok()
                        .and_then(|value| value["event"].as_str().map(str::to_owned));
                    if let Some(event) = event { if sender.send(event).is_err() { break; } }
                }
            });
            let mut last_message = std::time::Instant::now();
            while session_current(generation) && active.load(Ordering::SeqCst) {
                match receiver.recv_timeout(std::time::Duration::from_millis(250)) {
                    Ok(event) => {
                        last_message = std::time::Instant::now();
                        if !session_current(generation) || !active.load(Ordering::SeqCst) { break; }
                        match event.as_str() {
                            "compiling" => progress(&window,"Steam is preparing shaders…"),
                            "updating" => progress(&window,"Steam is downloading or updating game files…"),
                            "starting" => progress(&window,"Starting the game on Bosgame…"),
                            "ready" => super::fse_watcher::log_stream_transition(window.app_handle(), "host-game-window-ready"),
                            "visible" => {
                                set_host_window_state(generation, 2);
                                super::fse_watcher::log_stream_transition(window.app_handle(), "host-game-window-visible");
                            }
                            "closed" => {
                                set_host_window_state(generation, 3);
                                super::fse_watcher::log_stream_transition(window.app_handle(), "host-game-window-closed");
                                // Only hide the remote cleanup. Buddy still owns
                                // the safe process/stream shutdown later.
                                begin_return(&window, "moonlight", generation);
                                break;
                            }
                            "alive" => {},
                            "attention" => {
                                set_host_window_state(generation, 0);
                                super::fse_watcher::log_stream_transition(window.app_handle(), "host-launch-attention");
                                use tauri::Emitter;
                                let _ = window.emit("host-launch-attention", serde_json::json!({"session":generation}));
                            },
                            _ => break,
                        }
                    }
                    Err(std::sync::mpsc::RecvTimeoutError::Timeout) => { if last_message.elapsed() > std::time::Duration::from_secs(20) { break; } }
                    Err(_) => break,
                }
            }
            // This is our diagnostic SSH child, never a host game/client.
            let _ = child.kill(); let _ = child.wait(); drop(receiver); let _ = reader.join();
            Ok(())
        })();
        if session_current(generation) && host_window_state() != 3 {
            set_host_window_state(generation, 0);
            if result.is_err() || active.load(Ordering::SeqCst) {
                super::fse_watcher::log_stream_transition(window.app_handle(), "host-game-window-unavailable");
            }
        }
    });
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
    let (resolution, fps) = if size.height >= 2160 { ("3840x2160", 60) } else if size.width == 2560 && size.height == 1600 { ("2560x1600", 120) } else if xreal || size.height == 1200 { ("1920x1200", 90) } else { ("1920x1080", 120) };
    let display_label = if key.to_uppercase().starts_with("MONITOR\\HSX0224\\") { "G1" } else { "Handheld" };
    let saved = profiles.get(&key).cloned().unwrap_or(serde_json::json!({"host":"Bosgame","resolution":resolution,"fps":fps,"bitrate":80000,"codec":"auto"}));
    Ok(serde_json::json!({"displayKey":key,"profile":saved,"nativeResolution":resolution,"nativeFps":fps,"displayLabel":display_label}))
}

#[tauri::command]
pub async fn launch_moonlight(window: tauri::Window, app_name: String, display_key: String, profile: MoonlightProfile, steam_id: Option<String>, game_id: Option<String>, game_name: Option<String>, lutris_id: Option<String>) -> Result<(), String> {
    let epoch = LAUNCH_EPOCH.load(Ordering::SeqCst);
    validate(&profile)?;
    if display_key.len() > 512 { return Err("Invalid display".into()); }
    if app_name.starts_with('-') || app_name.chars().any(char::is_control) { return Err("Invalid host app name".into()); }
    tauri::async_runtime::spawn_blocking(move || {
        let wifi = super::stream_wifi::prepare("moonlight");
        check_launch(epoch)?;
        if !query_apps(&profile.host)?.contains(&app_name) { return Err("This app is no longer advertised by the host. Refresh the Moonlight list.".into()); }
        check_launch(epoch)?;
        let steam_id = if let Some(id)=&lutris_id {Some(super::lutris::launch_id(&profile.host,id,game_id.as_deref())?)} else {
            if game_id.as_deref().is_some_and(|id|id.starts_with("lutris:")){return Err("Missing Lutris launch identity".into());}
            steam_id
        };
        if steam_id.is_some() {
            if app_name != "MoonDeckStream" { return Err("Direct Steam launching requires MoonDeckStream".into()); }
            if super::moondeck::status(&profile.host)?["paired"] != true { return Err("Pair CreteOS with MoonDeck Buddy first".into()); }
            check_launch(epoch)?;
            super::moondeck::prepare(&profile.host, steam_id.as_deref().unwrap_or_default())?;
            check_launch(epoch)?;
            if lutris_id.is_none() && !host_games(&profile.host)?.iter().any(|game| Some(&game.steam_app_id) == steam_id.as_ref()) {
                return Err("This game is not installed on Bosgame. Choose local install or GeForce NOW.".into());
            }
        }
        let path = super::liftoff_dir().join("moonlight_profiles.json");
        let mut profiles: serde_json::Value = std::fs::read(&path).ok().and_then(|bytes| serde_json::from_slice(&bytes).ok()).unwrap_or(serde_json::json!({}));
        profiles[display_key] = serde_json::to_value(&profile).map_err(|e| e.to_string())?;
        std::fs::create_dir_all(super::liftoff_dir()).map_err(|e| e.to_string())?;
        std::fs::write(path, serde_json::to_vec_pretty(&profiles).map_err(|e| e.to_string())?).map_err(|e| e.to_string())?;
        let generation = next_session();
        let presentation = if let Some(id) = steam_id.as_ref() {
            match stage_host_presentation(&profile.host, id, &profile.resolution, game_id.as_deref(), game_name.as_deref(), generation) {
                Ok(token) => { super::fse_watcher::log_stream_transition(window.app_handle(), "host-artwork-ready"); Some(token) },
                Err(error) if error == STEAM_WRONG_DESKTOP => return Err(error),
                Err(_) => { super::fse_watcher::log_stream_transition(window.app_handle(), "host-artwork-unavailable"); None },
            }
        } else { None };
        if let Err(error) = check_launch(epoch) {
            if let Some(token) = &presentation { finish_host_presentation(&profile.host, token); }
            return Err(error);
        }
        let client = std::process::Command::new(client_path("moonlight")?).args(["stream", &profile.host, &app_name,
            "--resolution", &profile.resolution, "--fps", &profile.fps.to_string(), "--bitrate", &profile.bitrate.to_string(),
            "--video-codec", &profile.codec, "--display-mode", "borderless", "--quit-after"]).spawn().map_err(|e| {
                if let Some(token) = &presentation { finish_host_presentation(&profile.host, token); }
                e.to_string()
            })?;
        let client = std::sync::Arc::new(std::sync::Mutex::new(client));
        {
            let mut pending = PENDING_MOONLIGHT.lock().map_err(|_| "Stream launch state unavailable")?;
            if launch_current(epoch) { *pending = Some((epoch, client.clone())); }
        }
        if !launch_current(epoch) {
            if let Ok(child) = client.lock() { super::close_owned_moonlight_client(child.id(), || true); }
            if let Some(token) = &presentation { finish_host_presentation(&profile.host, token); }
            return Err("Stream launch cancelled".into());
        }
        let monitor_active = std::sync::Arc::new(std::sync::atomic::AtomicBool::new(true));
        if let Some(id) = &steam_id { watch_host_game_window(window.clone(), profile.host.clone(), id.clone(), presentation.clone(), generation, monitor_active.clone()); }
        let launch_result = if let Some(id) = &steam_id {
            super::moondeck::launch(&profile.host, id,
                || launch_current(epoch) && session_current(generation) && client.lock().is_ok_and(|mut child| child.try_wait().ok().flatten().is_none()),
                |phase| {
                    super::fse_watcher::log_stream_transition(window.app_handle(), phase);
                    progress(&window,match phase {"buddy-game-running"=>"Waiting for the game window…","buddy-game-launch-requested"=>"Steam is preparing the game…",_=>"Connecting to Bosgame…"});
                })
        } else { Ok(()) };
        if !launch_current(epoch) {
            monitor_active.store(false, Ordering::SeqCst);
            if let Some(token) = &presentation { finish_host_presentation(&profile.host, token); }
            return Err("Stream launch cancelled".into());
        }
        let started = launch_result.is_ok() || matches!(host_window_state(), 2 | 3);
        // Always attach the owned session lifecycle, including after a slow
        // startup error. A late-starting game must not become an orphan stream.
        std::thread::spawn(move || {
            let _wifi = wifi;
            if let Some(id) = steam_id {
                let exit_confirmed = std::sync::atomic::AtomicBool::new(false);
                // A failed/disconnected client can exit before Buddy ever
                // observes Running. Stop watching it too, rather than waiting
                // for a remote game event while the handheld has lost focus.
                let ended = super::moondeck::watch_exit(&profile.host, &id, started, || session_current(generation) && client.lock().is_ok_and(|mut child| child.try_wait().ok().flatten().is_none()), || {
                    exit_confirmed.store(true, Ordering::SeqCst);
                    begin_return(&window, "moonlight", generation);
                }, |phase| super::fse_watcher::log_stream_transition(window.app_handle(), phase));
                monitor_active.store(false, Ordering::SeqCst);
                // A lost host end request must not strand an owned Moonlight
                // window after the exact game has exited. Use normal WM_CLOSE;
                // --quit-after performs Moonlight's own session cleanup.
                if exit_confirmed.load(Ordering::SeqCst) && session_current(generation) && client.lock().is_ok_and(|mut child| child.try_wait().ok().flatten().is_none()) {
                    if let Ok(child) = client.lock() { super::close_owned_moonlight_client(child.id(), || session_current(generation)); }
                    super::fse_watcher::log_stream_transition(window.app_handle(), "moonlight-owned-close-requested");
                }
                if !ended { end_return(&window, "moonlight", generation); }
            }
            // CLI Moonlight exits itself after the host ends its stream. Wait
            // for this launched child only; never terminate a shared client.
            let start = std::time::Instant::now();
            while session_current(generation) && start.elapsed() < std::time::Duration::from_secs(86400) {
                if client.lock().is_ok_and(|mut child| child.try_wait().ok().flatten().is_some()) {
                    if let Some(token) = &presentation { finish_host_presentation(&profile.host, token); }
                    if lutris_id.is_some() { let _=super::lutris::refresh(&profile.host); use tauri::Emitter; let _=window.emit("library-rescan-needed",()); }
                    if session_current(generation) { super::finish_stream_session(window.clone(), "moonlight", || session_current(generation)); }
                    end_return(&window, "moonlight", generation);
                    return;
                }
                std::thread::sleep(std::time::Duration::from_millis(300));
            }
        });
        launch_result
    }).await.map_err(|e| e.to_string())?
}

#[cfg(test)]
mod tests {
    #[test]
    fn nvidia_raw_route_matches_desktop_shortcut_quoting() {
        assert_eq!(super::geforce_raw_route("--url-route=#?cmsId=107016426&launchSource=External").unwrap(), "--url-route=\"#?cmsId=107016426&launchSource=External\"");
        assert!(super::geforce_raw_route("--url-route=bad\"injection").is_err());
    }
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
        assert_eq!(settings["appSettingsConfig"]["streamingProfiles"],"custom");
        assert_eq!(settings["appSettingsConfig"]["customProfile"]["profile"],0);
        assert_eq!(settings["appSettingsConfig"]["customProfile"]["name"],"custom");
        assert!(super::update_geforce_settings(&mut serde_json::json!({}), &profile).is_err());
    }
    #[test]
    fn session_exit_requires_this_launch_to_have_streamed_and_handles_same_batch_restart() {
        let mut session = super::NvidiaSession::default();
        session.observe("NvCameraService streaming state change: Done");
        assert!(!session.ended);
        session.observe("NvCameraService streaming state change: Streaming");
        session.observe("NvCameraService streaming state change: PostStreaming");
        assert!(!session.ended);
        session.observe("NvCameraService streaming state change: Done");
        assert!(session.ended);
        session.observe("NvCameraService streaming state change: Streaming");
        assert!(!session.ended);
        session.observe("NvCameraService streaming state change: Done");
        session.observe("NvCameraService streaming state change: Loading");
        assert!(!session.ended); // Native restart cancels late-window cleanup too.
    }
    #[test]
    fn rejects_unknown_clients() {
        assert!(super::client_path("powershell").is_err());
        assert!(super::client_path("C:/anything.exe").is_err());
    }
    #[test]
    fn geforce_auto_bitrate_preserves_resolution_and_manual_mode_can_replace_it() {
        let mut settings = serde_json::json!({"appSettingsConfig":{"customProfile":{"width":3840,"height":2160,"fps":60,"maxBitrate":80,"maxBitrateAuto":false}}});
        let mut profile = super::MoonlightProfile { host:"GeForce NOW".into(), resolution:"1920x1080".into(), fps:120, bitrate:0, codec:"auto".into() };
        super::update_geforce_settings(&mut settings, &profile).unwrap();
        assert_eq!(settings["appSettingsConfig"]["customProfile"]["maxBitrate"],0);
        assert_eq!(settings["appSettingsConfig"]["customProfile"]["maxBitrateAuto"],true);
        assert_eq!(settings["appSettingsConfig"]["customProfile"]["width"],1920);
        let moonlight = super::MoonlightProfile { host:"Bosgame".into(), ..profile.clone() };
        assert!(super::validate(&moonlight).is_err()); // Auto is GFN-only.
        profile.bitrate=20000;
        super::update_geforce_settings(&mut settings, &profile).unwrap();
        assert_eq!(settings["appSettingsConfig"]["customProfile"]["maxBitrateAuto"],false);
        assert_eq!(settings["appSettingsConfig"]["customProfile"]["maxBitrate"],20);
        profile.bitrate=1000;
        assert!(super::update_geforce_settings(&mut settings, &profile).is_err());
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

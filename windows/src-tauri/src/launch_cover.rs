use std::sync::atomic::{AtomicU64, AtomicBool, Ordering};
use std::sync::Mutex;
use std::time::{SystemTime, UNIX_EPOCH};
use tauri::Manager;

static INDEPENDENT:AtomicBool=AtomicBool::new(false);
pub fn independent()->bool {INDEPENDENT.load(Ordering::SeqCst)}
static UNTIL: AtomicU64 = AtomicU64::new(0);
static GENERATION: AtomicU64 = AtomicU64::new(0);
static Z_ORDER: Mutex<()> = Mutex::new(());
fn now() -> u64 { SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_secs() }
pub fn active() -> bool { UNTIL.load(Ordering::SeqCst) > now() }

// Changing cover z-order must never activate CreteOS during external handoff
// or delayed overlay cleanup. Work only on the command's own managed window.
fn topmost_without_activation(raw: isize, topmost: bool) -> Result<(), String> {
    use windows::Win32::UI::WindowsAndMessaging::{GetWindowThreadProcessId, SetWindowPos, HWND_TOPMOST, HWND_NOTOPMOST, SWP_NOMOVE, SWP_NOSIZE, SWP_NOACTIVATE, SWP_NOOWNERZORDER};
    let hwnd = windows::Win32::Foundation::HWND(raw as _);
    let mut pid = 0;
    unsafe { GetWindowThreadProcessId(hwnd, Some(&mut pid)); }
    if pid != std::process::id() { return Err("Launch cover window is no longer owned by CreteOS".into()); }
    unsafe { SetWindowPos(hwnd, Some(if topmost { HWND_TOPMOST } else { HWND_NOTOPMOST }), 0, 0, 0, 0,
        SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE | SWP_NOOWNERZORDER).map_err(|e| e.to_string()) }
}

// Window operations can synchronously wait on the UI thread. Never make that
// thread wait for Z_ORDER while the guard holds it for a window operation.
#[tauri::command(async)]
pub fn set_launch_cover(window: tauri::Window, active: bool, allow_external_focus: Option<bool>, stream_provider: Option<String>) -> Result<(), String> {
    if stream_provider.as_deref().is_some_and(|p| !matches!(p, "moonlight" | "geforceNow" | "luna")) { return Err("Unknown streaming client".into()); }
    let hwnd = window.hwnd().map_err(|e| format!("Launcher handle: {e}"))?.0 as isize;
    let _z_order = Z_ORDER.lock().map_err(|_| "Launch cover state unavailable")?;
    let generation = GENERATION.fetch_add(1, Ordering::SeqCst) + 1;
    // Slow host launches need the same cover throughout game-window readiness.
    // Retain a bounded escape if the renderer or host stops responding.
    let cover_seconds = if stream_provider.as_deref() == Some("moonlight") { 240 } else { 120 };
    let until = if active { now() + cover_seconds } else { 0 };
    UNTIL.store(until, Ordering::SeqCst);
    INDEPENDENT.store(false,Ordering::SeqCst);
    if active { crate::stream_readiness::begin(); }
    if !active { let _=crate::stream_artwork::visible(&window,false); }
    if let Some(provider)=stream_provider.clone().filter(|_|active && !allow_external_focus.unwrap_or(false)) {
        topmost_without_activation(hwnd,false)?;
        // A presentation failure must not prevent a working client from launching.
        // Keep the established main-window fallback and retain the cause in logs.
        let cover=crate::stream_artwork::visible(&window,true);
        if let Err(error)=&cover {let _=crate::streaming::record_stream_issue(provider.clone(),"artwork".into(),error.clone());}
        if cover.is_ok() {
        INDEPENDENT.store(true,Ordering::SeqCst);
        crate::fse_watcher::log_stream_transition(window.app_handle(),"artwork-native-shown");
        std::thread::spawn(move||{
            let mut cancel_was_down=true;
            let mut shown=true;
            while GENERATION.load(Ordering::SeqCst)==generation && self::active() {
                use windows::Win32::UI::{WindowsAndMessaging::GetForegroundWindow,Input::{KeyboardAndMouse::{GetAsyncKeyState,VK_ESCAPE},XboxController::{XInputGetState,XINPUT_STATE}}};
                let ours=unsafe{GetForegroundWindow()}.0 as isize==hwnd;
                let eligible=ours || crate::can_return_from_stream(&window,&provider);
                if shown!=eligible {let _=crate::stream_artwork::visible(&window,eligible);shown=eligible;}
                let mut down=unsafe{GetAsyncKeyState(VK_ESCAPE.0 as i32)}<0;
                for index in 0..4 {let mut state=XINPUT_STATE::default();if unsafe{XInputGetState(index,&mut state)}==0{down|=state.Gamepad.wButtons.0 & 0x2000!=0;}}
                if eligible && down && !cancel_was_down {use tauri::Emitter;let _=window.emit("stream-launch-cancel",());let _=crate::streaming::cancel_stream_launch(window.clone());return;}
                cancel_was_down=down;
                std::thread::sleep(std::time::Duration::from_millis(50));
            }
            // Do not let a stale guard hide a newer launch/return cover.
            if GENERATION.load(Ordering::SeqCst)==generation {let _=crate::stream_artwork::visible(&window,false);}
        });
        return Ok(());
        }
    }

    topmost_without_activation(hwnd, active && !allow_external_focus.unwrap_or(false))?;
    if active {
        // The launching window is already visible. A late ShowWindow can
        // activate CreteOS after Luna has accepted foreground and autoplay.
        if !allow_external_focus.unwrap_or(false) { window.show().map_err(|e| e.to_string())?; }
        std::thread::spawn(move || {
            if let Some(provider) = stream_provider.filter(|_| !allow_external_focus.unwrap_or(false)) {
                // In Xbox full-screen mode a client taking foreground can
                // shell-cloak the launcher; topmost alone cannot display a
                // cloaked window. Retain foreground until readiness, but only
                // reclaim it from the selected streaming client. Alt-Tab to an
                // unrelated app still escapes. Generation and z-order lock
                // prevent a late reclaim after cancellation or handoff.
                let mut cancel_was_down = true;
                let mut reclaimed = false;
                while GENERATION.load(Ordering::SeqCst) == generation && self::active() {
                    use windows::Win32::UI::WindowsAndMessaging::{GetForegroundWindow, GetWindowThreadProcessId};
                    let foreground = unsafe { GetForegroundWindow() };
                    let ours = hwnd == foreground.0 as isize;
                    let mut pid = 0;
                    unsafe { GetWindowThreadProcessId(foreground, Some(&mut pid)); }
                    let path = crate::process_exe_path(pid);
                    let keep_cover = ours || path.as_deref().is_some_and(|path| stream_process(&provider, path));
                    // The stream window can own foreground underneath our
                    // artwork, so WebView gamepad/keyboard polling may stop.
                    // Observe a fresh B/Escape edge natively while our cover is
                    // visible, never while the user has Alt-Tabbed elsewhere.
                    use windows::Win32::UI::Input::{XboxController::{XInputGetState, XINPUT_STATE}, KeyboardAndMouse::{GetAsyncKeyState, VK_ESCAPE}};
                    let mut cancel_down = unsafe { GetAsyncKeyState(VK_ESCAPE.0 as i32) } < 0;
                    for index in 0..4 {
                        let mut state = XINPUT_STATE::default();
                        if unsafe { XInputGetState(index, &mut state) } == 0 { cancel_down |= state.Gamepad.wButtons.0 & 0x2000 != 0; }
                    }
                    if keep_cover && cancel_down && !cancel_was_down {
                        use tauri::Emitter;
                        let _ = window.emit("stream-launch-cancel", ());
                        let _ = crate::streaming::cancel_stream_launch(window.clone());
                        return;
                    }
                    cancel_was_down = cancel_down;
                    {
                        let Ok(_z_order) = Z_ORDER.lock() else { return; };
                        if GENERATION.load(Ordering::SeqCst) != generation { return; }
                        let _ = topmost_without_activation(hwnd, keep_cover);
                        if keep_cover && !ours && self::active() {
                            crate::focus_external_window_attempt(windows::Win32::Foundation::HWND(hwnd as _));
                            if !reclaimed {
                                crate::fse_watcher::log_stream_transition(window.app_handle(), "launch-cover-foreground-retained");
                                reclaimed = true;
                            }
                        }
                    }
                    std::thread::sleep(std::time::Duration::from_millis(50));
                }
            } else {
                std::thread::sleep(std::time::Duration::from_secs(cover_seconds));
            }
            let Ok(_z_order) = Z_ORDER.lock() else { return; };
            if GENERATION.load(Ordering::SeqCst) == generation && UNTIL.compare_exchange(until, 0, Ordering::SeqCst, Ordering::SeqCst).is_ok() { let _ = topmost_without_activation(hwnd, false); }
        });
    }
    Ok(())
}

pub(crate) fn stream_process(provider: &str, path: &str) -> bool {
    let name = path.rsplit(['\\', '/']).next().unwrap_or_default();
    match provider {
        "moonlight" => name.eq_ignore_ascii_case("Moonlight.exe"),
        "luna" => name.eq_ignore_ascii_case("chrome.exe"),
        "geforceNow" => ["GeForceNOW.exe", "GeForceNOWStreamer.exe"].iter().any(|exe| name.eq_ignore_ascii_case(exe)),
        _ => false,
    }
}

#[cfg(test)] mod tests {
    #[test] fn artwork_guard_only_covers_the_requested_stream_client() {
        assert!(super::stream_process("moonlight", r"C:\Apps\Moonlight.exe"));
        assert!(super::stream_process("geforceNow", r"C:\NVIDIA\GeForceNOWStreamer.exe"));
        assert!(!super::stream_process("moonlight", r"C:\Apps\GeForceNOW.exe"));
        assert!(!super::stream_process("geforceNow", r"C:\Apps\ChatGPT.exe"));
        assert!(!super::stream_process("geforceNow", r"C:\Apps\GeForceNOW.exe.backup"));
    }
}

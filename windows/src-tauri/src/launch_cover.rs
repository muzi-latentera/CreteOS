use std::sync::atomic::{AtomicU64, AtomicBool, Ordering};
use std::sync::Mutex;
use std::time::{SystemTime, UNIX_EPOCH};
use tauri::Manager;

static INDEPENDENT:AtomicBool=AtomicBool::new(false);
pub fn independent()->bool {INDEPENDENT.load(Ordering::SeqCst)}
static UNTIL: AtomicU64 = AtomicU64::new(0);
static GENERATION: AtomicU64 = AtomicU64::new(0);
static LOCAL_STAGED: AtomicBool = AtomicBool::new(false);
static Z_ORDER: Mutex<()> = Mutex::new(());
fn now() -> u64 { SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_secs() }
pub fn active() -> bool { UNTIL.load(Ordering::SeqCst) > now() }
pub(crate) fn generation() -> u64 { GENERATION.load(Ordering::SeqCst) }

pub(crate) struct LocalReturnCover {
    window: tauri::Window,
    generation: u64,
}
impl LocalReturnCover {
    pub(crate) fn stage(window: &tauri::Window, expected: u64, game: windows::Win32::Foundation::HWND) -> Option<Self> {
        let _lock = Z_ORDER.lock().ok()?;
        if generation() != expected || active() { return None; }
        crate::stream_artwork::stage_behind(window, game).ok()?;
        LOCAL_STAGED.store(true, Ordering::SeqCst);
        crate::fse_watcher::log_stream_transition(window.app_handle(), "local-return-cover-staged");
        Some(Self { window: window.clone(), generation: expected })
    }

    pub(crate) fn show(window: &tauri::Window, expected: u64, staged: &mut Option<Self>) {
        let Ok(_lock) = Z_ORDER.lock() else { return; };
        if generation() != expected || active() { return; }
        if crate::stream_artwork::visible(window, true).is_err() { return; }
        if LOCAL_STAGED.swap(false, Ordering::SeqCst) { crate::native_artwork::hide_staged(); }
        UNTIL.store(now() + 15, Ordering::SeqCst);
        INDEPENDENT.store(true, Ordering::SeqCst);
        crate::fse_watcher::log_stream_transition(window.app_handle(), "local-return-cover-shown");
        if staged.is_none() { *staged = Some(Self { window: window.clone(), generation: expected }); }
        let Ok(ours) = window.hwnd() else { return; };
        let ours = ours.0 as isize;
        std::thread::spawn(move || loop {
            {
                let Ok(_lock) = Z_ORDER.lock() else { return; };
                if generation() != expected || !active() || !independent() { return; }
                let foreground = unsafe { windows::Win32::UI::WindowsAndMessaging::GetForegroundWindow() }.0 as isize;
                if foreground == ours || crate::foreground_is_desktop() { crate::native_artwork::raise_if_visible(); }
            }
            std::thread::sleep(std::time::Duration::from_millis(25));
        });
    }
}
impl Drop for LocalReturnCover {
    fn drop(&mut self) {
        let Ok(_lock) = Z_ORDER.lock() else { return; };
        if GENERATION.load(Ordering::SeqCst) != self.generation { return; }
        if LOCAL_STAGED.swap(false, Ordering::SeqCst) { crate::native_artwork::hide_staged(); }
        let shown = INDEPENDENT.swap(false, Ordering::SeqCst);
        UNTIL.store(0, Ordering::SeqCst);
        if shown {
            let _ = crate::stream_artwork::visible(&self.window, false);
            crate::fse_watcher::log_stream_transition(self.window.app_handle(), "local-return-cover-released");
        }
    }
}
fn cover_seconds(provider:Option<&str>)->u64 {
    match provider { Some("moonlight") => 300, Some("luna") => 240, _ => 120 }
}

// Changing cover z-order must never activate CreteOS during external handoff
// or delayed overlay cleanup. Work only on the command's own managed window.
fn topmost_without_activation(raw: isize, topmost: bool) -> Result<(), String> {
    use windows::Win32::UI::WindowsAndMessaging::{GetWindowLongPtrW, GetWindowThreadProcessId, SetWindowPos, GWL_EXSTYLE, WS_EX_TOPMOST, HWND_TOPMOST, HWND_NOTOPMOST, SWP_NOMOVE, SWP_NOSIZE, SWP_NOACTIVATE, SWP_NOOWNERZORDER};
    let hwnd = windows::Win32::Foundation::HWND(raw as _);
    let mut pid = 0;
    unsafe { GetWindowThreadProcessId(hwnd, Some(&mut pid)); }
    if pid != std::process::id() { return Err("Launch cover window is no longer owned by CreteOS".into()); }
    if !topmost && unsafe { GetWindowLongPtrW(hwnd, GWL_EXSTYLE) } & WS_EX_TOPMOST.0 as isize == 0 { return Ok(()); }
    unsafe { SetWindowPos(hwnd, Some(if topmost { HWND_TOPMOST } else { HWND_NOTOPMOST }), 0, 0, 0, 0,
        SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE | SWP_NOOWNERZORDER).map_err(|e| e.to_string()) }
}

pub(crate) fn finish_handoff(window: &tauri::Window, target: isize) -> Result<(), String> {
    use windows::Win32::{Foundation::HWND, UI::WindowsAndMessaging::{GetForegroundWindow, SetWindowPos, SWP_NOACTIVATE, SWP_NOMOVE, SWP_NOOWNERZORDER, SWP_NOSIZE}};
    let ours = window.hwnd().map_err(|e| e.to_string())?;
    let _lock = Z_ORDER.lock().map_err(|_| "Launch cover state unavailable")?;
    let target = HWND(target as _);
    if ours == target || unsafe { GetForegroundWindow() } != target {
        return Err("The game window lost focus during handoff".into());
    }
    // Position CreteOS directly below the game. Moving a non-topmost window
    // to HWND_NOTOPMOST again puts it above ordinary windows, including the game.
    unsafe { SetWindowPos(ours, Some(target), 0, 0, 0, 0,
        SWP_NOACTIVATE | SWP_NOMOVE | SWP_NOSIZE | SWP_NOOWNERZORDER).map_err(|e| e.to_string())?; }
    GENERATION.fetch_add(1, Ordering::SeqCst);
    UNTIL.store(0, Ordering::SeqCst);
    INDEPENDENT.store(false, Ordering::SeqCst);
    let _ = crate::stream_artwork::visible(window, false);
    Ok(())
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
    // Outlast the provider readiness budget (180s for Moonlight/Luna), its
    // minimum artwork hold and the foreground handoff. The frontend's 180s
    // cancel remains the user-visible upper bound.
    let cover_seconds = cover_seconds(stream_provider.as_deref());
    let until = if active { now() + cover_seconds } else { 0 };
    UNTIL.store(until, Ordering::SeqCst);
    let had_independent = INDEPENDENT.swap(false, Ordering::SeqCst);
    if LOCAL_STAGED.swap(false, Ordering::SeqCst) { crate::native_artwork::hide_staged(); }
    if active && had_independent && stream_provider.is_none() { let _ = crate::stream_artwork::visible(&window, false); }
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
                let eligible=ours || crate::can_return_from_stream(&window,&provider) || crate::foreground_is_desktop();
                if shown!=eligible {let _=crate::stream_artwork::visible(&window,eligible);shown=eligible;}
                if eligible { crate::native_artwork::raise_if_visible(); }
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
        "ps5RemotePlay"|"playstationCloud" => name.eq_ignore_ascii_case("asobideck.exe"),
        "luna" => name.eq_ignore_ascii_case("chrome.exe"),
        "geforceNow" => ["GeForceNOW.exe", "GeForceNOWStreamer.exe"].iter().any(|exe| name.eq_ignore_ascii_case(exe)),
        _ => false,
    }
}

#[cfg(test)] mod tests {
    #[test] fn cover_outlasts_readiness_and_foreground_handoff() {
        for provider in ["moonlight","luna"] {assert!(super::cover_seconds(Some(provider))>180+10+3);}
        assert!(super::cover_seconds(Some("geforceNow"))>75+10+3);
    }
    #[test] fn artwork_guard_only_covers_the_requested_stream_client() {
        assert!(super::stream_process("moonlight", r"C:\Apps\Moonlight.exe"));
        assert!(super::stream_process("geforceNow", r"C:\NVIDIA\GeForceNOWStreamer.exe"));
        assert!(!super::stream_process("moonlight", r"C:\Apps\GeForceNOW.exe"));
        assert!(!super::stream_process("geforceNow", r"C:\Apps\ChatGPT.exe"));
        assert!(!super::stream_process("geforceNow", r"C:\Apps\GeForceNOW.exe.backup"));
    }
}

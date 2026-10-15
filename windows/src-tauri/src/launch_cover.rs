use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Mutex;
use std::time::{SystemTime, UNIX_EPOCH};

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

#[tauri::command]
pub fn set_launch_cover(window: tauri::Window, active: bool, allow_external_focus: Option<bool>, stream_provider: Option<String>) -> Result<(), String> {
    if stream_provider.as_deref().is_some_and(|p| !matches!(p, "moonlight" | "geforceNow")) { return Err("Unknown streaming client".into()); }
    let hwnd = window.hwnd().map_err(|e| e.to_string())?.0 as isize;
    let _z_order = Z_ORDER.lock().map_err(|_| "Launch cover state unavailable")?;
    let generation = GENERATION.fetch_add(1, Ordering::SeqCst) + 1;
    // Fail open after two minutes, even if the webview crashes during startup.
    let until = if active { now() + 120 } else { 0 };
    UNTIL.store(until, Ordering::SeqCst);
    if active { crate::stream_readiness::begin(); }
    topmost_without_activation(hwnd, active && !allow_external_focus.unwrap_or(false))?;
    if active {
        // The launching window is already visible. A late ShowWindow can
        // activate CreteOS after Luna has accepted foreground and autoplay.
        if !allow_external_focus.unwrap_or(false) { window.show().map_err(|e| e.to_string())?; }
        std::thread::spawn(move || {
            if let Some(provider) = stream_provider.filter(|_| !allow_external_focus.unwrap_or(false)) {
                // Streaming clients can create their own topmost startup
                // windows. Keep painted artwork above those without taking
                // keyboard focus, and let deliberate Alt-Tab to another app
                // escape the cover. Never reassert after cancellation/handoff.
                while GENERATION.load(Ordering::SeqCst) == generation && self::active() {
                    use windows::Win32::UI::WindowsAndMessaging::{GetForegroundWindow, GetWindowThreadProcessId};
                    let foreground = unsafe { GetForegroundWindow() };
                    let ours = hwnd == foreground.0 as isize;
                    let mut pid = 0;
                    unsafe { GetWindowThreadProcessId(foreground, Some(&mut pid)); }
                    let path = crate::process_exe_path(pid);
                    let keep_cover = ours || path.as_deref().is_some_and(|path| stream_process(&provider, path));
                    {
                        let Ok(_z_order) = Z_ORDER.lock() else { return; };
                        if GENERATION.load(Ordering::SeqCst) != generation { return; }
                        let _ = topmost_without_activation(hwnd, keep_cover);
                    }
                    std::thread::sleep(std::time::Duration::from_millis(200));
                }
            } else {
                std::thread::sleep(std::time::Duration::from_secs(120));
            }
            let Ok(_z_order) = Z_ORDER.lock() else { return; };
            if GENERATION.load(Ordering::SeqCst) == generation && UNTIL.compare_exchange(until, 0, Ordering::SeqCst, Ordering::SeqCst).is_ok() { let _ = topmost_without_activation(hwnd, false); }
        });
    }
    Ok(())
}

fn stream_process(provider: &str, path: &str) -> bool {
    let name = path.rsplit(['\\', '/']).next().unwrap_or_default();
    match provider {
        "moonlight" => name.eq_ignore_ascii_case("Moonlight.exe"),
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

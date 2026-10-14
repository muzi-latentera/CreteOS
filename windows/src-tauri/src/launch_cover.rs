use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{SystemTime, UNIX_EPOCH};

static UNTIL: AtomicU64 = AtomicU64::new(0);
fn now() -> u64 { SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_secs() }
pub fn active() -> bool { UNTIL.load(Ordering::SeqCst) > now() }

// Changing cover z-order must never activate CreteOS during external handoff
// or delayed overlay cleanup. Work only on the command's own managed window.
fn topmost_without_activation(window: &tauri::Window, topmost: bool) -> Result<(), String> {
    use windows::Win32::UI::WindowsAndMessaging::{SetWindowPos, HWND_TOPMOST, HWND_NOTOPMOST, SWP_NOMOVE, SWP_NOSIZE, SWP_NOACTIVATE, SWP_NOOWNERZORDER};
    let raw = window.hwnd().map_err(|e| e.to_string())?;
    let hwnd = windows::Win32::Foundation::HWND(raw.0 as _);
    unsafe { SetWindowPos(hwnd, Some(if topmost { HWND_TOPMOST } else { HWND_NOTOPMOST }), 0, 0, 0, 0,
        SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE | SWP_NOOWNERZORDER).map_err(|e| e.to_string()) }
}

#[tauri::command]
pub fn set_launch_cover(window: tauri::Window, active: bool, allow_external_focus: Option<bool>) -> Result<(), String> {
    // Fail open after two minutes, even if the webview crashes during startup.
    let until = if active { now() + 120 } else { 0 };
    UNTIL.store(until, Ordering::SeqCst);
    if active { crate::stream_readiness::begin(); }
    topmost_without_activation(&window, active && !allow_external_focus.unwrap_or(false))?;
    if active {
        // The launching window is already visible. A late ShowWindow can
        // activate CreteOS after Luna has accepted foreground and autoplay.
        if !allow_external_focus.unwrap_or(false) { window.show().map_err(|e| e.to_string())?; }
        let handle = window.clone();
        std::thread::spawn(move || {
            std::thread::sleep(std::time::Duration::from_secs(120));
            if UNTIL.compare_exchange(until, 0, Ordering::SeqCst, Ordering::SeqCst).is_ok() { let _ = topmost_without_activation(&handle, false); }
        });
    }
    Ok(())
}

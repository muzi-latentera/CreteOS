use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{SystemTime, UNIX_EPOCH};

static UNTIL: AtomicU64 = AtomicU64::new(0);
fn now() -> u64 { SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_secs() }
pub fn active() -> bool { UNTIL.load(Ordering::SeqCst) > now() }

#[tauri::command]
pub fn set_launch_cover(window: tauri::Window, active: bool) -> Result<(), String> {
    // Fail open after two minutes, even if the webview crashes during startup.
    let until = if active { now() + 120 } else { 0 };
    UNTIL.store(until, Ordering::SeqCst);
    window.set_always_on_top(active).map_err(|e| e.to_string())?;
    if active {
        window.show().map_err(|e| e.to_string())?;
        let handle = window.clone();
        std::thread::spawn(move || {
            std::thread::sleep(std::time::Duration::from_secs(120));
            if UNTIL.compare_exchange(until, 0, Ordering::SeqCst, Ordering::SeqCst).is_ok() { let _ = handle.set_always_on_top(false); }
        });
    }
    Ok(())
}

//! One CreteOS at a time.
//!
//! On 14 Nov, starting CreteOS while it was already open (from the Start menu,
//! AnyFSE or a shortcut) started a second full copy on the same WebView2
//! profile. A console shell must instead bring the copy that is already
//! running to the front. The mutex belongs to the process, so it is released
//! when CreteOS exits, including a watchdog relaunch or an update install.
use windows::Win32::Foundation::HWND;
use windows::Win32::UI::WindowsAndMessaging::{IsIconic, SetForegroundWindow, ShowWindow, SW_RESTORE, SW_SHOW};

/// True when this process is the only CreteOS. A failure to create the mutex
/// never blocks start-up.
pub fn claim() -> bool {
    use windows::{core::w, Win32::Foundation::{GetLastError, ERROR_ALREADY_EXISTS}, Win32::System::Threading::CreateMutexW};
    unsafe {
        match CreateMutexW(None, false, w!("Local\\CreteOS-single-instance")) {
            // The handle is deliberately kept open for the life of the process.
            Ok(_handle) => GetLastError() != ERROR_ALREADY_EXISTS,
            Err(_) => true,
        }
    }
}

/// Show and focus the main window of the CreteOS that is already running.
pub fn focus_existing() {
    let me = std::process::id();
    let existing = super::collect_launch_windows().into_iter().find(|window| {
        window.pid != me && window.title == "CreteOS"
            && window.exe_path.as_deref().is_some_and(|path| path.to_ascii_lowercase().ends_with("\\creteos.exe"))
    });
    if let Some(window) = existing {
        let hwnd = HWND(window.hwnd as *mut _);
        unsafe {
            let _ = ShowWindow(hwnd, if IsIconic(hwnd).as_bool() { SW_RESTORE } else { SW_SHOW });
            // Launched by a user action, this process may hand focus over.
            let _ = SetForegroundWindow(hwnd);
        }
    }
}

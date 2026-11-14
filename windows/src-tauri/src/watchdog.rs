//! Main-thread watchdog.
//!
//! Twice on 14 Nov the window stopped responding (almost no CPU, no repaint of
//! fresh state) right after start-up. A console shell must recover on its own,
//! so a background thread pings the main thread every 2 s. If it misses
//! `STALL_PINGS` pings in a row while CreteOS is the foreground window, the
//! stall is logged and CreteOS relaunches itself, at most three times in ten
//! minutes. Missed pings are counted rather than wall time, so time spent
//! asleep or hibernated never looks like a hang, and a game in front is never
//! interrupted.
use std::{io::Write, sync::atomic::{AtomicU32, Ordering}, time::{Duration, SystemTime, UNIX_EPOCH}};

static PENDING: AtomicU32 = AtomicU32::new(0);
/// 25 × 2 s: long enough for the slowest legitimate main-thread work.
const STALL_PINGS: u32 = 25;

fn log(message: &str) {
    let stamp = chrono::DateTime::<chrono::Utc>::from(SystemTime::now()).to_rfc3339();
    if let Ok(mut file) = std::fs::OpenOptions::new().create(true).append(true).open(super::liftoff_dir().join("watchdog.log")) {
        let _ = writeln!(file, "{stamp} {message}");
    }
}

/// Allow a relaunch only if fewer than three happened in the last ten minutes.
fn may_relaunch() -> bool {
    let path = super::liftoff_dir().join("watchdog-relaunches.txt");
    let now = SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0);
    match next_relaunch_log(&std::fs::read_to_string(&path).unwrap_or_default(), now) {
        Some(log) => { let _ = std::fs::write(&path, log); true }
        None => false,
    }
}

/// The updated relaunch log if another relaunch is allowed at `now`, else None.
fn next_relaunch_log(previous: &str, now: u64) -> Option<String> {
    let mut recent: Vec<u64> = previous.lines()
        .filter_map(|line| line.trim().parse().ok()).filter(|at| now.saturating_sub(*at) < 600).collect();
    if recent.len() >= 3 { return None; }
    recent.push(now);
    Some(recent.iter().map(u64::to_string).collect::<Vec<_>>().join("\n"))
}

#[cfg(test)]
mod tests {
    use super::next_relaunch_log;

    #[test]
    fn allows_three_relaunches_in_ten_minutes() {
        assert_eq!(next_relaunch_log("", 1000).as_deref(), Some("1000"));
        assert_eq!(next_relaunch_log("900\n950", 1000).as_deref(), Some("900\n950\n1000"));
        assert_eq!(next_relaunch_log("900\n950\n990", 1000), None);
        // Entries older than ten minutes no longer count, and junk lines are ignored.
        assert_eq!(next_relaunch_log("100\nbad\n200\n950", 1000).as_deref(), Some("950\n1000"));
    }
}

pub fn start(app: tauri::AppHandle, our_hwnd: isize) {
    std::thread::spawn(move || {
        // Let start-up finish before judging the main thread.
        std::thread::sleep(Duration::from_secs(15));
        loop {
            std::thread::sleep(Duration::from_secs(2));
            let missed = PENDING.fetch_add(1, Ordering::SeqCst);
            let _ = app.run_on_main_thread(|| PENDING.store(0, Ordering::SeqCst));
            if missed < STALL_PINGS { continue; }
            let foreground = unsafe { windows::Win32::UI::WindowsAndMessaging::GetForegroundWindow() }.0 as isize;
            if foreground != our_hwnd { continue; }
            log(&format!("main thread missed {missed} pings while CreteOS was in front"));
            if !may_relaunch() { log("not relaunching: three relaunches in the last ten minutes"); PENDING.store(0, Ordering::SeqCst); continue; }
            if let Ok(exe) = std::env::current_exe() {
                use std::os::windows::process::CommandExt;
                log("relaunching CreteOS");
                // Start the new copy about 2 s after this process has exited, so
                // it never shares a half-closed WebView2 profile with this one.
                let start = format!("ping -n 3 127.0.0.1 >nul & start \"\" \"{}\"", exe.display());
                let _ = std::process::Command::new("cmd.exe").args(["/c", &start]).creation_flags(0x0800_0000).spawn();
                std::process::exit(0);
            }
        }
    });
}

use serde::{Deserialize, Serialize};
use windows::{core::PCWSTR, Win32::Graphics::Gdi::{
    ChangeDisplaySettingsExW, EnumDisplayDevicesW, EnumDisplaySettingsExW, GetMonitorInfoW, MonitorFromWindow,
    CDS_TYPE, CDS_TEST, DEVMODEW, DISPLAY_DEVICEW, DISP_CHANGE_SUCCESSFUL, DM_DISPLAYFREQUENCY,
    DM_PELSHEIGHT, DM_PELSWIDTH, ENUM_CURRENT_SETTINGS, ENUM_DISPLAY_SETTINGS_FLAGS,
    ENUM_DISPLAY_SETTINGS_MODE, MONITORINFOEXW, MONITOR_DEFAULTTONEAREST,
}};
use windows::Win32::UI::WindowsAndMessaging::EDD_GET_DEVICE_INTERFACE_NAME;

#[derive(Clone, Copy, Deserialize, Eq, Ord, PartialEq, PartialOrd, Serialize)]
pub struct Mode { width: u32, height: u32, hz: u32 }

fn name(window: &tauri::Window) -> Result<[u16; 32], String> {
    let hwnd = window.hwnd().map_err(|e| e.to_string())?;
    let mut monitor = MONITORINFOEXW::default();
    monitor.monitorInfo.cbSize = std::mem::size_of::<MONITORINFOEXW>() as u32;
    unsafe {
        if !GetMonitorInfoW(MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST), &mut monitor.monitorInfo).as_bool() {
            return Err("Windows could not identify the current display.".into());
        }
    }
    Ok(monitor.szDevice)
}

fn read(name: &[u16; 32], index: ENUM_DISPLAY_SETTINGS_MODE) -> Option<DEVMODEW> {
    let mut mode = DEVMODEW { dmSize: std::mem::size_of::<DEVMODEW>() as u16, ..Default::default() };
    unsafe { EnumDisplaySettingsExW(PCWSTR(name.as_ptr()), index, &mut mode, ENUM_DISPLAY_SETTINGS_FLAGS(0)).as_bool().then_some(mode) }
}

fn summary(mode: &DEVMODEW) -> Mode {
    Mode { width: mode.dmPelsWidth, height: mode.dmPelsHeight, hz: mode.dmDisplayFrequency }
}

fn monitor_id(name: &[u16; 32]) -> Option<[u16; 128]> {
    let mut device = DISPLAY_DEVICEW { cb: std::mem::size_of::<DISPLAY_DEVICEW>() as u32, ..Default::default() };
    unsafe { EnumDisplayDevicesW(PCWSTR(name.as_ptr()), 0, &mut device, EDD_GET_DEVICE_INTERFACE_NAME).as_bool() }
        .then_some(device.DeviceID).filter(|id| id[0] != 0)
}

fn modes(name: &[u16; 32], current: &DEVMODEW) -> Vec<Mode> {
    let mut choices = std::collections::BTreeSet::new();
    for index in 0..1024 {
        let Some(mode) = read(name, ENUM_DISPLAY_SETTINGS_MODE(index)) else { break };
        if mode.dmBitsPerPel == current.dmBitsPerPel && unsafe { mode.Anonymous2.dmDisplayFlags == current.Anonymous2.dmDisplayFlags }
            && (mode.dmPelsWidth >= mode.dmPelsHeight) == (current.dmPelsWidth >= current.dmPelsHeight)
            && mode.dmPelsWidth >= 1024 && mode.dmPelsHeight >= 600 && mode.dmDisplayFrequency >= 50 {
            choices.insert(summary(&mode));
        }
    }
    choices.into_iter().rev().collect()
}

#[tauri::command]
pub fn get_local_display_modes(window: tauri::Window) -> Result<serde_json::Value, String> {
    let name = name(&window)?;
    let current = read(&name, ENUM_CURRENT_SETTINGS).ok_or("Could not read the current display mode.")?;
    Ok(serde_json::json!({ "current": summary(&current), "modes": modes(&name, &current) }))
}

static SESSION: std::sync::Mutex<bool> = std::sync::Mutex::new(false);

pub struct Session { name: [u16; 32], monitor: [u16; 128], original: DEVMODEW, selected: Mode }
impl Drop for Session {
    fn drop(&mut self) {
        if let Ok(mut busy) = SESSION.lock() {
            // A game or user may have changed the display independently.
            if monitor_id(&self.name) == Some(self.monitor)
                && read(&self.name, ENUM_CURRENT_SETTINGS).is_some_and(|current| summary(&current) == self.selected) {
                let mut original = self.original;
                original.dmFields = DM_PELSWIDTH | DM_PELSHEIGHT | DM_DISPLAYFREQUENCY;
                unsafe { ChangeDisplaySettingsExW(PCWSTR(self.name.as_ptr()), Some(&original), None, CDS_TYPE(0), None); }
            }
            *busy = false;
        }
    }
}

pub fn prepare(window: &tauri::Window, choice: Option<Mode>) -> Result<Option<Session>, String> {
    let Some(selected) = choice else { return Ok(None) };
    let mut busy = SESSION.lock().map_err(|_| "Display settings are busy.")?;
    if *busy { return Err("Another launch is using a temporary display mode. Close it and retry.".into()); }
    let name = name(window)?;
    let original = read(&name, ENUM_CURRENT_SETTINGS).ok_or("Could not read the current display mode.")?;
    if summary(&original) == selected { return Ok(None); }
    let monitor = monitor_id(&name).ok_or("Windows could not identify the display for safe restoration.")?;
    if !modes(&name, &original).contains(&selected) { return Err("This display mode is unavailable. Reopen launch options and choose a supported mode.".into()); }
    let mut mode = original;
    mode.dmFields = DM_PELSWIDTH | DM_PELSHEIGHT | DM_DISPLAYFREQUENCY;
    mode.dmPelsWidth = selected.width;
    mode.dmPelsHeight = selected.height;
    mode.dmDisplayFrequency = selected.hz;
    unsafe {
        let name = PCWSTR(name.as_ptr());
        if ChangeDisplaySettingsExW(name, Some(&mode), None, CDS_TEST, None) != DISP_CHANGE_SUCCESSFUL
            || ChangeDisplaySettingsExW(name, Some(&mode), None, CDS_TYPE(0), None) != DISP_CHANGE_SUCCESSFUL {
            return Err("Windows could not apply the selected display mode. Choose Current display mode and retry.".into());
        }
    }
    *busy = true;
    Ok(Some(Session { name, monitor, original, selected }))
}

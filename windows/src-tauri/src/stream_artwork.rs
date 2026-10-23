//! A non-activating artwork window. The streaming client keeps keyboard focus
//! for browser autoplay; no loop raises the launcher over the client.
use std::sync::{Mutex,atomic::{AtomicBool,Ordering}};
use tauri::{Emitter,Manager};
#[derive(Clone,serde::Serialize)]
pub struct Artwork { art:String, message:String }
static ART:Mutex<Artwork>=Mutex::new(Artwork{art:String::new(),message:String::new()});
static PAINTED:AtomicBool=AtomicBool::new(false);
#[tauri::command]
pub fn get_stream_artwork()->Artwork {ART.lock().map(|s|s.clone()).unwrap_or(Artwork{art:String::new(),message:String::new()})}
#[tauri::command]
pub fn stream_artwork_painted(){PAINTED.store(true,Ordering::SeqCst);}
#[tauri::command]
pub fn update_stream_artwork(window:tauri::Window,art:String,message:String)->Result<(),String>{
    if art.len()>12*1024*1024 || message.len()>300{return Err("Invalid stream artwork".into());}
    if let Ok(mut s)=ART.lock(){if s.art!=art {PAINTED.store(false,Ordering::SeqCst);}*s=Artwork{art,message};}
    if let Some(cover)=window.app_handle().get_webview_window("stream-artwork"){let _=cover.emit("stream-artwork",get_stream_artwork());}
    Ok(())
}
pub fn message(window:&tauri::Window,message:&str){
    if let Ok(mut state)=ART.lock(){state.message=message.into();}
    if let Some(cover)=window.app_handle().get_webview_window("stream-artwork"){let _=cover.emit("stream-artwork",get_stream_artwork());}
}
// Native HWND lookup stays inside our PID and exact dedicated title. Tauri's
// raw-window-handle dispatch can be unavailable for this hidden auxiliary window.
fn owned_hwnd()->Result<windows::Win32::Foundation::HWND,String>{
    use windows::Win32::{Foundation::{HWND,LPARAM},UI::WindowsAndMessaging::{EnumWindows,GetWindowThreadProcessId,GetWindowTextW}};
    use windows::core::BOOL;
    unsafe extern "system" fn visit(hwnd:HWND,param:LPARAM)->BOOL {
        let mut pid=0;GetWindowThreadProcessId(hwnd,Some(&mut pid));
        if pid==std::process::id(){
            let mut text=[0u16;128];let count=GetWindowTextW(hwnd,&mut text);
            if count>0 && String::from_utf16_lossy(&text[..count as usize])=="CreteOS streaming artwork" {
                (*(param.0 as *mut Vec<isize>)).push(hwnd.0 as isize);
            }
        }
        BOOL(1)
    }
    let mut found=Vec::<isize>::new();unsafe{let _=EnumWindows(Some(visit),LPARAM(&mut found as *mut _ as isize));}
    if found.len()!=1{return Err("Dedicated streaming artwork HWND is unavailable".into());}
    Ok(HWND(found[0] as _))
}
pub fn visible(window:&tauri::Window,show:bool)->Result<(),String>{
    use windows::Win32::UI::WindowsAndMessaging::{ShowWindow,SetWindowPos,GetWindowLongPtrW,SetWindowLongPtrW,GWL_EXSTYLE,WS_EX_NOACTIVATE,WS_EX_TOOLWINDOW,SW_SHOWNOACTIVATE,SW_HIDE,HWND_TOPMOST,SWP_NOACTIVATE};
    let hwnd=owned_hwnd()?;
    if !show {unsafe{let _=ShowWindow(hwnd,SW_HIDE);}return Ok(());}
    let cover=window.app_handle().get_webview_window("stream-artwork").ok_or("Streaming artwork was not initialized")?;
    let monitor=window.current_monitor().map_err(|e|format!("Streaming monitor: {e}"))?.ok_or("No streaming monitor")?;
    unsafe{let style=GetWindowLongPtrW(hwnd,GWL_EXSTYLE);SetWindowLongPtrW(hwnd,GWL_EXSTYLE,style|WS_EX_NOACTIVATE.0 as isize|WS_EX_TOOLWINDOW.0 as isize);}
    let _=cover.emit("stream-artwork",get_stream_artwork());
    let start=std::time::Instant::now();while !PAINTED.load(Ordering::SeqCst) && start.elapsed()<std::time::Duration::from_millis(1500){std::thread::sleep(std::time::Duration::from_millis(10));}
    let p=monitor.position();let size=monitor.size();
    unsafe{SetWindowPos(hwnd,Some(HWND_TOPMOST),p.x,p.y,size.width as i32,size.height as i32,SWP_NOACTIVATE).map_err(|e|e.to_string())?;let _=ShowWindow(hwnd,SW_SHOWNOACTIVATE);}
    Ok(())
}

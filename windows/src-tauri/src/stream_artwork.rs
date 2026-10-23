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
pub fn visible(window:&tauri::Window,show:bool)->Result<(),String>{
    let app=window.app_handle();
    if !show {if let Some(cover)=app.get_webview_window("stream-artwork"){let _=cover.hide();}return Ok(());}
    let cover=if let Some(cover)=app.get_webview_window("stream-artwork"){cover}else{
        let monitor=window.current_monitor().map_err(|e|e.to_string())?.ok_or("No streaming monitor")?;
        let scale=monitor.scale_factor();let size=monitor.size();let position=monitor.position();
        PAINTED.store(false,Ordering::SeqCst);
        let cover=tauri::WebviewWindowBuilder::new(app,"stream-artwork",tauri::WebviewUrl::App("index.html?stream_artwork=1".into()))
            .title("CreteOS streaming artwork").decorations(false).resizable(false).skip_taskbar(true).always_on_top(true)
            .focused(false).visible(false).position(position.x as f64/scale,position.y as f64/scale)
            .inner_size(size.width as f64/scale,size.height as f64/scale).build().map_err(|e|e.to_string())?;
        use windows::Win32::UI::WindowsAndMessaging::{GetWindowLongPtrW,SetWindowLongPtrW,GWL_EXSTYLE,WS_EX_NOACTIVATE,WS_EX_TOOLWINDOW};
        if let Ok(hwnd)=cover.hwnd(){unsafe{let style=GetWindowLongPtrW(hwnd,GWL_EXSTYLE);SetWindowLongPtrW(hwnd,GWL_EXSTYLE,style|WS_EX_NOACTIVATE.0 as isize|WS_EX_TOOLWINDOW.0 as isize);}}
        cover
    };
    if let Some(monitor)=window.current_monitor().map_err(|e|e.to_string())? {
        let _=cover.set_position(tauri::Position::Physical(*monitor.position()));
        let _=cover.set_size(tauri::Size::Physical(*monitor.size()));
    }
    let _=cover.emit("stream-artwork",get_stream_artwork());
    let start=std::time::Instant::now();while !PAINTED.load(Ordering::SeqCst) && start.elapsed()<std::time::Duration::from_millis(1500){std::thread::sleep(std::time::Duration::from_millis(10));}
    use windows::Win32::UI::WindowsAndMessaging::{ShowWindow,SetWindowPos,SW_SHOWNOACTIVATE,HWND_TOPMOST,SWP_NOMOVE,SWP_NOSIZE,SWP_NOACTIVATE};
    let hwnd=cover.hwnd().map_err(|e|e.to_string())?;
    unsafe {let _=ShowWindow(hwnd,SW_SHOWNOACTIVATE);SetWindowPos(hwnd,Some(HWND_TOPMOST),0,0,0,0,SWP_NOMOVE|SWP_NOSIZE|SWP_NOACTIVATE).map_err(|e|e.to_string())}
}

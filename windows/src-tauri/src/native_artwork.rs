//! Non-activating artwork popup, independent of hidden WebView realization.
use std::sync::{Mutex,OnceLock};
use windows::{core::w,Win32::{Foundation::*,Graphics::{Gdi::*,Imaging::*},System::{Com::*,LibraryLoader::GetModuleHandleW},UI::{Shell::SHCreateMemStream,WindowsAndMessaging::*}}};
struct Image {width:i32,height:i32,pixels:Vec<u8>}
struct State {key:String,image:Option<Image>,message:String}
static STATE:Mutex<State>=Mutex::new(State{key:String::new(),image:None,message:String::new()});
static HANDLE:OnceLock<Result<isize,String>>=OnceLock::new();
pub fn cover_rect(iw:i32,ih:i32,w:i32,h:i32)->(i32,i32,i32,i32){
    let scale=(w as f64/iw.max(1) as f64).max(h as f64/ih.max(1) as f64);
    let dw=(iw as f64*scale).ceil() as i32;let dh=(ih as f64*scale).ceil() as i32;
    ((w-dw)/2,(h-dh)/2,dw,dh)
}
fn bytes(art:&str)->Result<Vec<u8>,String>{
    use base64::Engine;
    if let Some(data)=art.strip_prefix("data:image/").and_then(|v|v.split_once(";base64,").map(|(_,v)|v)) {
        return base64::engine::general_purpose::STANDARD.decode(data).map_err(|_|"Invalid artwork data".into());
    }
    let url=reqwest::Url::parse(art).map_err(|_|"Artwork is not cached locally")?;
    if !matches!(url.host_str(),Some("asset.localhost"|"asset")){return Err("Artwork is not cached locally".into());}
    let decoded=urlencoding::decode(url.path().trim_start_matches('/')).map_err(|_|"Invalid artwork path")?;
    let path=std::path::PathBuf::from(decoded.as_ref()).canonicalize().map_err(|_|"Artwork file unavailable")?;
    let root=crate::liftoff_dir().join("art").canonicalize().map_err(|_|"Artwork cache unavailable")?;
    if !path.starts_with(root){return Err("Artwork is outside the local cache".into());}
    if std::fs::metadata(&path).map_err(|_|"Artwork unavailable")?.len()>12*1024*1024{return Err("Artwork is too large".into());}
    std::fs::read(path).map_err(|_|"Artwork unavailable".into())
}
fn decode(data:&[u8])->Result<Image,String>{unsafe{
    // Each caller owns its COM apartment; no interfaces escape this function.
    let initialized=CoInitializeEx(None,COINIT_MULTITHREADED).is_ok();
    let result=(||->windows::core::Result<Image>{
        let factory:IWICImagingFactory=CoCreateInstance(&CLSID_WICImagingFactory,None,CLSCTX_INPROC_SERVER)?;
        let stream=SHCreateMemStream(Some(data)).ok_or_else(windows::core::Error::from_win32)?;
        let decoder=factory.CreateDecoderFromStream(&stream,std::ptr::null(),WICDecodeMetadataCacheOnLoad)?;
        let frame=decoder.GetFrame(0)?;let converter=factory.CreateFormatConverter()?;
        converter.Initialize(&frame,&GUID_WICPixelFormat32bppBGRA,WICBitmapDitherTypeNone,None,0.0,WICBitmapPaletteTypeCustom)?;
        let(mut width,mut height)=(0,0);converter.GetSize(&mut width,&mut height)?;
        if width==0 || height==0 || width>8192 || height>8192 || width as u64*height as u64>32_000_000{return Err(windows::core::Error::from_win32());}
        let mut pixels=vec![0;(width*height*4)as usize];converter.CopyPixels(std::ptr::null(),width*4,&mut pixels)?;
        Ok(Image{width:width as i32,height:height as i32,pixels})
    })();
    if initialized{CoUninitialize();}result.map_err(|_|"Could not decode cached artwork".into())
}}
pub fn update(art:&str,message:&str)->Result<(),String>{
    let mut state=STATE.lock().map_err(|_|"Artwork state unavailable")?;
    if state.key!=art {let image=if art.is_empty(){None}else{let mut image=decode(&bytes(art)?)?;for pixel in image.pixels.chunks_exact_mut(4){for color in &mut pixel[..3]{*color=(*color as u16*7/10)as u8;}}Some(image)};state.image=image;state.key=art.into();}
    state.message=message.into();drop(state);
    if let Some(Ok(raw))=HANDLE.get(){unsafe{let _=InvalidateRect(Some(HWND(*raw as _)),None,false);}}
    Ok(())
}
pub fn message(text:&str){if let Ok(mut state)=STATE.lock(){state.message=text.into();}if let Some(Ok(raw))=HANDLE.get(){unsafe{let _=InvalidateRect(Some(HWND(*raw as _)),None,false);}}}
unsafe extern "system" fn procedure(hwnd:HWND,msg:u32,wp:WPARAM,lp:LPARAM)->LRESULT{
    if msg==WM_ERASEBKGND{return LRESULT(1);}
    if msg==WM_MOUSEACTIVATE{return LRESULT(MA_NOACTIVATE as isize);}
    if msg==WM_PAINT {
        let _=std::panic::catch_unwind(||unsafe{
            let mut paint=PAINTSTRUCT::default();let dc=BeginPaint(hwnd,&mut paint);let mut rect=RECT::default();let _=GetClientRect(hwnd,&mut rect);
            let brush=CreateSolidBrush(COLORREF(0x0b0908));FillRect(dc,&rect,brush);let _=DeleteObject(brush.into());
            if let Ok(state)=STATE.lock(){
                if let Some(image)=&state.image{
                    let(x,y,width,height)=cover_rect(image.width,image.height,rect.right,rect.bottom);
                    let info=BITMAPINFO{bmiHeader:BITMAPINFOHEADER{biSize:std::mem::size_of::<BITMAPINFOHEADER>()as u32,biWidth:image.width,biHeight:-image.height,biPlanes:1,biBitCount:32,biCompression:BI_RGB.0,..Default::default()},..Default::default()};
                    StretchDIBits(dc,x,y,width,height,0,0,image.width,image.height,Some(image.pixels.as_ptr().cast()),&info,DIB_RGB_COLORS,SRCCOPY);
                }
                if !state.message.is_empty(){
                    let font=CreateFontW((rect.right/70).clamp(24,42),0,0,0,400,0,0,0,DEFAULT_CHARSET,OUT_DEFAULT_PRECIS,CLIP_DEFAULT_PRECIS,DEFAULT_QUALITY,0,w!("Segoe UI"));
                    let old=SelectObject(dc,font.into());SetBkMode(dc,TRANSPARENT);SetTextColor(dc,COLORREF(0xffffff));
                    let mut text=state.message.encode_utf16().collect::<Vec<_>>();DrawTextW(dc,&mut text,&mut rect,DT_CENTER|DT_VCENTER|DT_SINGLELINE);
                    SelectObject(dc,old);let _=DeleteObject(font.into());
                }
            }
            let _=EndPaint(hwnd,&paint);
        });
        return LRESULT(0);
    }
    DefWindowProcW(hwnd,msg,wp,lp)
}
pub fn prepare()->Result<isize,String>{HANDLE.get_or_init(||{
    let(tx,rx)=std::sync::mpsc::channel();
    std::thread::spawn(move||unsafe{
        let result=(||->windows::core::Result<HWND>{
            let instance=HINSTANCE(GetModuleHandleW(None)?.0);
            let class=WNDCLASSW{lpfnWndProc:Some(procedure),hInstance:instance,lpszClassName:w!("CreteOSNativeArtwork"),..Default::default()};
            if RegisterClassW(&class)==0{return Err(windows::core::Error::from_win32());}
            CreateWindowExW(WS_EX_NOACTIVATE|WS_EX_TOOLWINDOW|WS_EX_TOPMOST,w!("CreteOSNativeArtwork"),w!("CreteOS native streaming artwork"),WS_POPUP,0,0,800,600,None,None,Some(instance),None)
        })();
        match result{Ok(hwnd)=>{let _=tx.send(Ok(hwnd.0 as isize));let mut msg=MSG::default();while GetMessageW(&mut msg,None,0,0).0>0{let _=TranslateMessage(&msg);DispatchMessageW(&msg);}},Err(_)=>{let _=tx.send(Err("Native artwork window could not be created".into()));}}
    });
    rx.recv_timeout(std::time::Duration::from_secs(5)).unwrap_or(Err("Native artwork initialization timed out".into()))
}).clone()}
pub fn visible(show:bool,x:i32,y:i32,width:i32,height:i32)->Result<(),String>{
    let hwnd=HWND(prepare()? as _);unsafe{
        if show{SetWindowPos(hwnd,Some(HWND_TOPMOST),x,y,width,height,SWP_NOACTIVATE).map_err(|_|"Native artwork positioning failed")?;let _=InvalidateRect(Some(hwnd),None,false);let _=ShowWindow(hwnd,SW_SHOWNOACTIVATE);let _=UpdateWindow(hwnd);}
        else{let _=ShowWindow(hwnd,SW_HIDE);}
    }Ok(())
}
#[cfg(test)]mod tests{
use super::*;
#[test]fn hidden_native_surface_has_a_real_owned_nonactivating_handle(){unsafe{
    let before=GetForegroundWindow();let hwnd=HWND(super::prepare().unwrap()as _);let mut pid=0;
    GetWindowThreadProcessId(hwnd,Some(&mut pid));assert_eq!(pid,std::process::id());
    assert!(IsWindow(Some(hwnd)).as_bool());assert!(!IsWindowVisible(hwnd).as_bool());
    assert_ne!(GetWindowLongPtrW(hwnd,GWL_EXSTYLE)&WS_EX_NOACTIVATE.0 as isize,0);
    assert_eq!(before,GetForegroundWindow());
}}
#[test]fn windows_decoder_accepts_png_and_rejects_invalid_data(){
    let png=lodepng::encode32(&[255u8,0,0,255],1,1).unwrap();let image=super::decode(&png).unwrap();
    assert_eq!((image.width,image.height),(1,1));assert_eq!(&image.pixels[..4],&[0,0,255,255]);
    assert!(super::decode(b"not an image").is_err());
    assert!(super::bytes("https://example.test/image.jpg").is_err());
}
#[test]fn artwork_cover_preserves_ratio_and_covers_monitor(){
    for(iw,ih,w,h)in[(1920,1080,2560,1600),(1600,2560,2560,1600),(2560,1600,2560,1600)]{
        let(x,y,dw,dh)=super::cover_rect(iw,ih,w,h);assert!(dw>=w&&dh>=h&&x<=0&&y<=0);assert!((dw as f64/dh as f64-iw as f64/ih as f64).abs()<0.002);
    }
}}

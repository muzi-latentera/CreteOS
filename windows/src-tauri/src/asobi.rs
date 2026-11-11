//! ASOBI owns console credentials and launch arguments. We only read registered
//! Steam shortcuts and launch their real ShortcutGameId, never a guessed game ID.
use serde::Serialize;
use std::{collections::HashMap, path::PathBuf};

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all="camelCase")]
pub struct Game { pub id: String, pub name: String, pub label: String, pub provider: String }

#[derive(Debug, PartialEq)]
enum Value { Text(String), Integer(u64), Object(HashMap<String, Value>) }
struct Reader<'a> { bytes: &'a [u8], pos: usize }
impl Reader<'_> {
    fn string(&mut self) -> Result<String,String> {
        let tail=self.bytes.get(self.pos..).ok_or("Truncated Steam shortcut")?;
        let n=tail.iter().position(|&v|v==0).ok_or("Truncated Steam shortcut")?;
        if n>65536 {return Err("Steam shortcut value too large".into())}
        let text=std::str::from_utf8(&tail[..n]).map_err(|_|"Invalid Steam shortcut text")?.to_owned();
        self.pos+=n+1; Ok(text)
    }
    fn object(&mut self, depth:usize) -> Result<HashMap<String,Value>,String> {
        if depth>8 {return Err("Steam shortcut nesting too deep".into())}
        let mut result=HashMap::new();
        loop {
            let kind=*self.bytes.get(self.pos).ok_or("Truncated Steam shortcuts")?;self.pos+=1;
            if kind==8 {return Ok(result)}
            let key=self.string()?.to_ascii_lowercase();
            let value=match kind {
                0=>Value::Object(self.object(depth+1)?), 1=>Value::Text(self.string()?),
                2|7=>{let size=if kind==2 {4}else{8}; let end=self.pos.checked_add(size).ok_or("Invalid shortcut size")?;
                    let data=self.bytes.get(self.pos..end).ok_or("Truncated shortcut integer")?;self.pos=end;
                    let mut bytes=[0;8];bytes[..size].copy_from_slice(data);Value::Integer(u64::from_le_bytes(bytes))},
                _=>return Err("Unsupported Steam shortcut field".into())
            };
            if result.insert(key,value).is_some(){return Err("Duplicate Steam shortcut field".into())}
            if result.len()>10000 {return Err("Too many Steam shortcut fields".into())}
        }
    }
}
fn text<'a>(row:&'a HashMap<String,Value>,key:&str)->Option<&'a str>{match row.get(key)?{Value::Text(v)=>Some(v),_=>None}}
fn digits(s:&str)->bool{!s.is_empty() && s.len()<=20 && s.bytes().all(|c|c.is_ascii_digit())}
fn cloud_id(s:&str)->bool{
    if digits(s){return true}
    let bytes=s.as_bytes();
    (20..=64).contains(&bytes.len()) && bytes[..2].iter().all(u8::is_ascii_uppercase)
        && bytes[2..6].iter().all(u8::is_ascii_digit) && bytes[6]==b'-'
        && bytes.iter().all(|b|b.is_ascii_uppercase()||b.is_ascii_digit()||matches!(b,b'_'|b'-'))
}
fn provider(args:&str)->Option<&'static str>{
    let mut fields=HashMap::new();
    for token in args.split_whitespace(){
        if token=="--steam-tile" {if fields.insert("tile","true").is_some(){return None}continue}
        let (key,value)=token.split_once('=')?;
        if !["--cloud","--console","--account","--game"].contains(&key) || fields.insert(key,value).is_some(){return None}
    }
    if fields.get("tile")!=Some(&"true") || !digits(fields.get("--account")?){return None}
    if let Some(id)=fields.get("--cloud") {return (fields.len()==3 && cloud_id(id)).then_some("playstationCloud")}
    let console=fields.get("--console")?;let game=fields.get("--game")?;
    (fields.len()==4 && console.len()==12 && console.bytes().all(|b|b.is_ascii_hexdigit()) && game.len()<=32 && game.len()>=9 && game.bytes().all(|b|b.is_ascii_uppercase()||b.is_ascii_digit()||b==b'_')).then_some("ps5RemotePlay")
}
fn helper_path()->Option<PathBuf>{Some(dirs::config_dir()?.join("asobi/asobi-tile-4505440.exe"))}
fn parse(bytes:&[u8],helper:&std::path::Path)->Result<Vec<Game>,String>{
    if bytes.len()>4*1024*1024{return Err("Steam shortcuts file too large".into())}
    let root=Reader{bytes,pos:0}.object(0)?;
    let Some(Value::Object(rows))=root.get("shortcuts") else{return Ok(vec![])};
    let mut games=Vec::new();
    for value in rows.values(){
        let Value::Object(row)=value else{continue};
        let Some(exe)=text(row,"exe") else{continue};
        // Require ASOBI's installed helper, not arbitrary commands named Asobi.
        let actual=std::path::Path::new(exe.trim_matches('"'));
        if !super::streaming::gfn_process_guard::same_path(&actual.to_string_lossy(),&helper.to_string_lossy()){continue}
        let Some(mode)=text(row,"launchoptions").and_then(provider) else{continue};
        let Some(Value::Integer(id))=row.get("appid") else{continue};
        if *id>u32::MAX as u64 || *id&0x80000000==0 {continue}
        let Some(name)=text(row,"appname") else{continue};
        if name.is_empty()||name.len()>2048 {continue}
        // ASOBI appends its mode/console label. Preserve the actual game title.
        let name=name.rsplit_once(" (").filter(|(_,suffix)|suffix.ends_with(')')).map(|(base,_)|base).unwrap_or(name).to_owned();
        games.push(Game{id:id.to_string(),label:text(row,"appname").unwrap_or("").to_owned(),name,provider:mode.into()});
    }
    Ok(games)
}
fn games()->Result<Vec<Game>,String>{
    let helper=helper_path().ok_or("ASOBI profile is unavailable")?;
    if !helper.is_file(){return Ok(vec![])}
    let Some(steam)=super::get_steam_install_path() else{return Ok(vec![])};
    // Steam's active user is authoritative; do not merge another user's PSN routes.
    let user=unsafe {
        use windows::{core::w, Win32::System::Registry::{RegGetValueW,HKEY_CURRENT_USER,RRF_RT_REG_DWORD}};
        let mut value=0u32;let mut size=4u32;
        let status=RegGetValueW(HKEY_CURRENT_USER,w!("Software\\Valve\\Steam\\ActiveProcess"),w!("ActiveUser"),RRF_RT_REG_DWORD,None,Some((&mut value as *mut u32).cast()),Some(&mut size));
        if status.0!=0{0}else{value}
    };
    let root=PathBuf::from(steam).join("userdata");
    let files=if user!=0 {vec![root.join(format!("{user}/config/shortcuts.vdf"))]}else{
        // With Steam closed, retain a unique ASOBI profile without selecting or
        // merging another user's routes. An ambiguity requires opening Steam.
        std::fs::read_dir(root).into_iter().flatten().filter_map(Result::ok).take(64).map(|p|p.path().join("config/shortcuts.vdf")).collect()
    };
    let mut profiles=Vec::new();
    for file in files {
        if !file.is_file(){continue}
        if std::fs::metadata(&file).map_err(|_|"Could not inspect Steam shortcuts")?.len()>4*1024*1024{return Err("Steam shortcuts file too large".into())}
        let rows=parse(&std::fs::read(file).map_err(|_|"Could not read Steam shortcuts")?,&helper)?;
        if !rows.is_empty(){profiles.push(rows)}
    }
    if profiles.len()>1{return Err("More than one Steam profile has ASOBI shortcuts. Open Steam with the intended account, then refresh CreteOS.".into())}
    let mut result=profiles.pop().unwrap_or_default();
    result.sort_by(|a,b|a.id.cmp(&b.id));
    let mut ids=std::collections::HashSet::new();
    if result.iter().any(|g|!ids.insert(&g.id)){return Err("Duplicate ASOBI shortcut identity".into())}
    Ok(result)
}
#[tauri::command]
pub fn get_asobi_games()->Result<Vec<Game>,String>{games()}
#[tauri::command]
pub async fn open_asobi_client()->Result<(),String>{
    tauri::async_runtime::spawn_blocking(||{
        open_client_in_background()?;
        let start=std::time::Instant::now();
        while start.elapsed().as_secs()<20 {
            if let Some(hwnd)=client_window(){super::focus_external_window(windows::Win32::Foundation::HWND(hwnd as _));return Ok(())}
            std::thread::sleep(std::time::Duration::from_millis(250));
        }
        Err("ASOBI has not opened. Check its Steam installation.".into())
    }).await.map_err(|e|e.to_string())?
}
pub(crate) fn is_client_uri(uri: &str) -> bool {
    uri == "steam://rungameid/4505440"
}

pub(crate) fn client_window() -> Option<isize> {
    windows().first().map(|window| window.hwnd)
}

pub(crate) fn open_client_in_background() -> Result<(), String> {
    if client_window().is_some() {
        return Ok(());
    }
    let steam = PathBuf::from(super::get_steam_install_path().ok_or("Steam is not installed")?);
    std::process::Command::new(steam.join("steam.exe"))
        .current_dir(&steam)
        .args(["-silent", "-applaunch", "4505440"])
        .spawn()
        .map_err(|error| format!("Could not start ASOBI through Steam: {error}"))?;
    Ok(())
}

pub fn merge(apps:&mut Vec<super::AppEntry>){
    let art=dirs::config_dir().unwrap_or_default().join("asobi/steam_tiles_art");
    let grids=super::load_art_cache();let heroes=super::load_hero_cache();let times=history();
    for game in games().unwrap_or_default(){
        for (suffix,folder,cache,current) in [("p.png",super::grid_art_dir(),super::art_cache_path(),&grids),("_hero.jpg",super::hero_static_art_dir(),super::hero_cache_path(),&heroes)]{
            if current.get(&game.name).is_some_and(|v|!v.is_empty()){continue}
            let source=art.join(format!("{}{suffix}",game.id));
            if !std::fs::metadata(&source).is_ok_and(|m|m.is_file()&&m.len()<12*1024*1024){continue}
            let folder=folder.join("asobi");let target=folder.join(format!("{}{suffix}",game.id));
            if std::fs::create_dir_all(folder).is_ok()&&std::fs::copy(source,&target).is_ok(){super::artwork_cache::replace(&cache,&game.name,&target.to_string_lossy());}
        }
        apps.push(super::AppEntry{
        id:format!("asobi:{}",game.id),name:game.name,source:"playstation".into(),app_type:"game".into(),
        launch_path:format!("asobi://{}/{}",game.provider,game.id),playtime_minutes:times.get(&game.id).map(|t|(t.seconds/60.0).floor() as u32),last_played:times.get(&game.id).map(|t|t.last_played),store_id:Some(game.id),installed:true,..Default::default()
    })}
}
static PHASE:std::sync::atomic::AtomicU8=std::sync::atomic::AtomicU8::new(0);
static OWNED:std::sync::atomic::AtomicBool=std::sync::atomic::AtomicBool::new(false);
static HISTORY_LOCK:std::sync::Mutex<()>=std::sync::Mutex::new(());
pub(crate) fn phase()->super::asobi_session::Phase {
    use super::asobi_session::Phase::*;
    match PHASE.load(std::sync::atomic::Ordering::SeqCst){1=>Playing,2=>Attention,3=>Ending,4=>Ended,_=>Waiting}
}
fn publish(phase:super::asobi_session::Phase){use super::asobi_session::Phase::*;PHASE.store(match phase{Waiting=>0,Playing=>1,Attention=>2,Ending=>3,Ended=>4},std::sync::atomic::Ordering::SeqCst);}
fn history_path()->PathBuf{super::liftoff_dir().join("asobi_playtime.json")}
#[derive(Default,serde::Serialize,serde::Deserialize)]
struct Time {seconds:f64,last_played:i64}
fn history()->HashMap<String,Time>{std::fs::read(history_path()).ok().and_then(|b|serde_json::from_slice(&b).ok()).unwrap_or_default()}
fn credit(id:&str,seconds:f64,last_played:i64)->Result<(),String>{
    if !seconds.is_finite()||seconds<0.0{return Err("Invalid ASOBI session duration".into())}
    let _lock=HISTORY_LOCK.lock().map_err(|_|"ASOBI playtime is busy")?;
    let mut data=history();let item=data.entry(id.into()).or_default();item.seconds+=seconds;item.last_played=last_played;
    let tmp=history_path().with_extension("json.tmp");std::fs::write(&tmp,serde_json::to_vec_pretty(&data).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?;
    std::fs::rename(tmp,history_path()).map_err(|e|e.to_string())
}
fn process_created(pid:u32)->Option<u64>{unsafe{
    use windows::Win32::{Foundation::{CloseHandle,FILETIME},System::Threading::{OpenProcess,GetProcessTimes,PROCESS_QUERY_LIMITED_INFORMATION}};
    let handle=OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION,false,pid).ok()?;
    let(mut c,mut e,mut k,mut u)=(FILETIME::default(),FILETIME::default(),FILETIME::default(),FILETIME::default());
    let result=GetProcessTimes(handle,&mut c,&mut e,&mut k,&mut u);let _=CloseHandle(handle);
    result.ok()?;Some((u64::from(c.dwHighDateTime)<<32)|u64::from(c.dwLowDateTime))
}}
fn windows()->Vec<super::LaunchWindowCandidate>{
    let paths=super::steam_library_paths().into_iter().map(|p|PathBuf::from(p).join("common/Asobi/asobideck.exe")).collect::<Vec<_>>();
    super::collect_launch_windows().into_iter().filter(|w|w.exe_path.as_ref().is_some_and(|p|paths.iter().any(|expected|super::streaming::gfn_process_guard::same_path(p,&expected.to_string_lossy())))).collect()
}
fn close_idle(targets:&[(u32,u64)]){
    use windows::Win32::{Foundation::{HWND,WPARAM,LPARAM},UI::WindowsAndMessaging::{PostMessageW,WM_CLOSE}};
    for target in windows(){
        if targets.iter().any(|(pid,created)|*pid==target.pid&&process_created(*pid)==Some(*created)){
            unsafe{let _=PostMessageW(Some(HWND(target.hwnd as _)),WM_CLOSE,WPARAM(0),LPARAM(0));}
        }
    }
}
struct Owned;
impl Drop for Owned{fn drop(&mut self){OWNED.store(false,std::sync::atomic::Ordering::SeqCst);}}
#[tauri::command]
pub async fn launch_asobi_game(window:tauri::Window,shortcut_id:String)->Result<(),String>{
    use tauri::Emitter;
    tauri::async_runtime::spawn_blocking(move||{
        let game=games()?.into_iter().find(|g|g.id==shortcut_id).ok_or("This ASOBI shortcut is no longer registered. Export it from ASOBI and restart Steam, then refresh CreteOS.")?;
        if OWNED.swap(true,std::sync::atomic::Ordering::SeqCst){return Err("An ASOBI launch or stream is already being tracked. Finish it before starting another.".into())}
        let owned=Owned;
        let mut log=super::stream_readiness::LogCursor::new(PathBuf::from("C:/tmp/asobi.log"));
        publish(super::asobi_session::Phase::Waiting);
        super::streaming::cancel_pending_launch();let epoch=super::streaming::launch_epoch();
        let generation=super::streaming::new_session_generation();
        let was_open=super::is_process_running("asobideck.exe");
        let lease=if was_open{super::stream_wifi::prepare_current(&game.provider)}else{super::stream_wifi::prepare(&game.provider)};
        let id=game.id.parse::<u32>().map_err(|_|"Invalid ASOBI shortcut")?;
        let uri=format!("steam://rungameid/{}",((id as u64)<<32)|0x02000000);
        let steam=PathBuf::from(super::get_steam_install_path().ok_or("Steam is not installed")?).join("steam.exe");
        std::process::Command::new(steam).arg(uri).spawn().map_err(|_|"Could not open the registered ASOBI shortcut")?;
        std::thread::spawn(move||{
            let _owned=owned;let _lease=lease;
            use super::asobi_session::{Phase,State};
            let mut state=State::default();let mut last_credit:Option<std::time::Instant>=None;let mut played_at=0;let mut targets=Vec::new();let mut returning=false;let mut attention_shown=false;
            let started=std::time::Instant::now();let mut seen_client=was_open;
            while super::streaming::session_current(generation){
                let open=super::is_process_running("asobideck.exe");seen_client|=open;
                for line in log.lines(){state.observe(&line);}
                if state.replaced {
                    if returning {super::streaming::end_return(&window,&game.provider,generation);}
                    publish(Phase::Attention);break;
                }
                if !super::streaming::launch_current(epoch){
                    if let Some(last)=last_credit {let _=credit(&game.id,last.elapsed().as_secs_f64(),played_at);}
                    if !was_open || state.ever_played {
                        if targets.is_empty(){targets=windows().into_iter().filter_map(|w|process_created(w.pid).map(|born|(w.pid,born))).collect();}
                        close_idle(&targets);
                    }
                    publish(Phase::Ended);break;
                }
                publish(state.phase);
                if last_credit.is_none() && state.phase==Phase::Playing {
                    played_at=super::unix_now_i64();last_credit=Some(std::time::Instant::now());
                    targets=windows().into_iter().filter_map(|w|process_created(w.pid).map(|born|(w.pid,born))).collect();
                    let _=credit(&game.id,0.0,played_at);
                    let _=super::record_stream_launch(format!("asobi:{}",game.id),game.name.clone(),format!("asobi://{}/{}",game.provider,game.id));
                    let _=window.emit("library-rescan-needed",());
                    super::streaming::progress(&window,"PlayStation video received");
                }
                if let Some(last)=last_credit {
                    if last.elapsed().as_secs()>=15 || matches!(state.phase,Phase::Ending|Phase::Ended) || !open {
                        if credit(&game.id,last.elapsed().as_secs_f64(),played_at).is_ok(){last_credit=if matches!(state.phase,Phase::Ending|Phase::Ended)||!open{None}else{Some(std::time::Instant::now())};}
                    }
                }
                if state.ever_played && matches!(state.phase,Phase::Ending|Phase::Ended) && !returning {
                    super::streaming::begin_return(&window,&game.provider,generation);returning=true;
                }
                if matches!(state.phase,Phase::Attention) && !state.ever_played && !attention_shown {
                    attention_shown=true;
                    super::streaming::progress(&window,"ASOBI needs attention. Check its connection, sign-in or console message.");
                }
                if state.phase==Phase::Ended || state.ever_played&&seen_client&&!open {
                    publish(Phase::Ended);
                    if state.ever_played {
                        if !returning{super::streaming::begin_return(&window,&game.provider,generation);}
                        // Recheck the fresh log before closing only the exact process
                        // instances that rendered this stream. No forced termination.
                        let mut new_session=false;
                        for line in log.lines(){if super::asobi_session::new_activity(&line){new_session=true;}}
                        if !new_session {
                            close_idle(&targets);
                            super::finish_stream_session(window.clone(),&game.provider,||super::streaming::session_current(generation));
                        }
                        std::thread::sleep(std::time::Duration::from_millis(250));
                        super::streaming::end_return(&window,&game.provider,generation);
                        let _=window.emit("library-rescan-needed",());
                    }
                    break;
                }
                if !state.ever_played && !open && started.elapsed().as_secs()>45 {publish(Phase::Attention);break;}
                if !state.ever_played && started.elapsed().as_secs()>180 {
                    // Missing log readiness is not proof of an idle client.
                    // Expose ASOBI and keep watching; never close it on timeout.
                    if !open {publish(Phase::Attention);break;}
                    if !attention_shown {super::streaming::progress(&window,"Check ASOBI's connection or sign-in message.");attention_shown=true;}
                    publish(Phase::Attention);
                }
                std::thread::sleep(std::time::Duration::from_millis(100));
            }
        });
        Ok(())
    }).await.map_err(|e|e.to_string())?
}

#[cfg(test)] mod tests {
    use super::*;
    #[test]fn accepts_only_exported_routes(){
        assert_eq!(provider("--steam-tile --cloud=10016190 --account=123456"),Some("playstationCloud"));
        for id in ["EP4382-CUSA16761_00-DELIVERUSTHEMOON","EP0149-CUSA05667_00-RAINWORLDEU00000","EP0102-CUSA18008_00-VILLAGEFULLGAMEX","EP9000-CUSA08809_00-SOTC0000000000EU","EP0001-CUSA05526_00-STEEPGAMEEEFIGS1"]{assert_eq!(provider(&format!("--steam-tile --cloud={id} --account=123456")),Some("playstationCloud"));}
        assert_eq!(provider("--steam-tile --console=012345abcdef --account=123456 --game=PPSA01285_00"),Some("ps5RemotePlay"));
        for bad in ["--cloud=1 --account=2","--steam-tile --cloud=1 --account=2 --game=X","--steam-tile --cloud=1 --account=2 --cloud=3","--steam-tile --cloud=1 --account=2 --exec=evil","--steam-tile --console=bad --account=2 --game=PPSA01285_00"]{assert_eq!(provider(bad),None,"{bad}")}
    }
    #[test]fn rejects_malformed_binary(){
        for bytes in [vec![],vec![0],vec![1,b'a'],vec![2,b'a',0,1],vec![9,b'a',0]]{assert!(parse(&bytes,std::path::Path::new("C:/asobi.exe")).is_err())}
    }
}

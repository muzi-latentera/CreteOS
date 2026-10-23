//! Local-only Luna media lifecycle bridge. Receives enum/counter metadata from
//! the explicitly installed companion, never browser content or credentials.
use std::{collections::HashMap, io::{Read, Write}, net::{TcpListener, TcpStream}, sync::{Mutex, OnceLock}, time::{Duration, Instant}};
use serde::{Deserialize, Serialize};
const ORIGIN: &str = "chrome-extension://gmneapkhejdngoenacdnamaddbfcihab";
const ADDRESS: &str = "127.0.0.1:59265";
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all="lowercase")]
pub enum Phase { Waiting, Buffering, Playing, Attention, Ended }
#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct Event { nonce:String, tab:u64, frame:u64, document:String, phase:Phase, sequence:u64 }
struct Session { nonce:String, game:String, asin:String, tab:Option<u64>, document:String, phase:Phase, heard:Instant, tick:Instant, claimed:Option<(u64,u64)>, sequence:u64, companion_seen:bool, seconds:f64, saved:f64, ever_played:bool, ignored_early_ends:u64 }
static SESSION: Mutex<Option<Session>> = Mutex::new(None);
static SERVER: OnceLock<Result<(),String>> = OnceLock::new();
static FILE: Mutex<()> = Mutex::new(());
fn path() -> std::path::PathBuf { super::liftoff_dir().join("luna_playtime.json") }
fn totals() -> HashMap<String,f64> { std::fs::read(path()).ok().and_then(|b|serde_json::from_slice(&b).ok()).unwrap_or_default() }
fn persist(s:&mut Session) {
    let delta = s.seconds - s.saved;
    if delta <= 0.0 {return;}
    let Ok(_lock)=FILE.lock() else{return;};
    let mut values=totals(); *values.entry(s.game.clone()).or_default() += delta;
    let p=path(); let temp=p.with_extension("json.tmp");
    if let Ok(data)=serde_json::to_vec_pretty(&values) {
        if std::fs::write(&temp,data).is_ok() && std::fs::rename(temp,p).is_ok(){s.saved=s.seconds;}
    }
}
pub fn merge(apps:&mut [super::AppEntry]) {
    let Ok(_lock)=FILE.lock() else{return;}; let values=totals();
    for app in apps.iter_mut().filter(|a|a.source=="cloud" && super::luna::luna_url(&a.launch_path)) {
        if let Some(seconds)=values.get(&app.id){app.playtime_minutes=Some((seconds/60.0).floor().min(u32::MAX as f64) as u32);}
    }
}
pub fn begin(game:&str,url:&str) -> Result<String,String> {
    start_server()?;
    let mut slot=SESSION.lock().map_err(|_|"Luna session state unavailable")?;
    if slot.as_ref().is_some_and(|s|s.phase!=Phase::Ended){return Err("A Luna session is already being tracked. Finish it before launching another game.".into());}
    let mut bytes=[0u8;24];getrandom::getrandom(&mut bytes).map_err(|e|e.to_string())?;
    let nonce:String=bytes.iter().map(|b|format!("{b:02x}")).collect();
    let asin=reqwest::Url::parse(url).ok().and_then(|u|u.query_pairs().find(|(k,_)|k=="asin").map(|(_,v)|v.into_owned())).ok_or("Luna game URL has no game identity")?;
    *slot=Some(Session{nonce:nonce.clone(),game:game.into(),asin,tab:None,document:String::new(),phase:Phase::Waiting,heard:Instant::now(),tick:Instant::now(),claimed:None,sequence:0,companion_seen:false,seconds:0.0,saved:0.0,ever_played:false,ignored_early_ends:0});Ok(nonce)
}
pub fn phase() -> Phase {
    let Ok(mut slot)=SESSION.lock() else{return Phase::Ended;};
    let Some(s)=slot.as_mut() else{return Phase::Ended;};
    if s.heard.elapsed()>Duration::from_secs(4) && s.phase==Phase::Playing {s.phase=Phase::Buffering;persist(s);}
    s.phase
}
pub fn connected() -> bool { SESSION.lock().ok().is_some_and(|s|s.as_ref().is_some_and(|s|s.companion_seen)) }
// Browser fullscreen/autoplay must be allowed to acquire foreground. The
// main-window fallback retains it and therefore cannot wait for live video.
pub fn needs_foreground(elapsed:Duration, independent:bool, connected:bool, has_window:bool)->bool {
    has_window && elapsed>=Duration::from_secs(3) && (!independent || !connected)
}
pub fn finish(nonce:&str) {if let Ok(mut slot)=SESSION.lock(){if let Some(s)=slot.as_mut().filter(|s|s.nonce==nonce){persist(s);s.phase=Phase::Ended;}}}
fn ingest(s:&mut Session,event:Event,now:Instant)->bool {
    if event.nonce!=s.nonce || event.sequence==0 || s.phase==Phase::Ended || event.document.is_empty() || event.document.len()>80{return false;}
    if s.tab.is_some_and(|tab|tab!=event.tab){return false;}
    // An existing Luna document can report the previous stream's end while
    // Chrome opens the new launch. It must neither claim nor end this session.
    if event.phase==Phase::Ended && !s.ever_played {s.ignored_early_ends+=1;return false;}
    s.tab=Some(event.tab);
    s.companion_seen=true;
    let owner=(event.tab,event.frame);
    if let Some(claimed)=s.claimed {
        if owner!=claimed{return false;}
        if event.document!=s.document {
            // Reloads reset the document counter. Resume without crediting the gap.
            s.document=event.document.clone();s.sequence=0;s.phase=Phase::Buffering;s.tick=now;
        }
        if event.sequence<=s.sequence{return false;}
    }
    else if matches!(event.phase,Phase::Playing|Phase::Buffering){s.claimed=Some(owner);s.document=event.document.clone();}
    else {s.heard=now;s.phase=event.phase;return true;}
    let elapsed=now.duration_since(s.tick).as_secs_f64();
    if s.phase==Phase::Playing && event.phase==Phase::Playing && elapsed<=3.0 {s.seconds+=elapsed;}
    s.tick=now;s.heard=now;s.sequence=event.sequence;s.phase=event.phase;
    s.ever_played|=event.phase==Phase::Playing;true
}
fn reply(stream:&mut TcpStream,status:&str,body:&str) {
    let _=write!(stream,"HTTP/1.1 {status}\r\nContent-Type: application/json\r\nAccess-Control-Allow-Origin: {ORIGIN}\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS\r\nAccess-Control-Allow-Headers: content-type\r\nCache-Control: no-store\r\nConnection: close\r\nContent-Length: {}\r\n\r\n{body}",body.len());
}
fn serve(mut stream:TcpStream) {
    let _=stream.set_read_timeout(Some(Duration::from_secs(2)));let _=stream.set_write_timeout(Some(Duration::from_secs(2)));
    let mut bytes=Vec::new();let mut buffer=[0u8;1024];let header_end;
    loop {let Ok(n)=stream.read(&mut buffer) else{return;};if n==0{return;}bytes.extend_from_slice(&buffer[..n]);
        if bytes.len()>8192{return;} if let Some(end)=bytes.windows(4).position(|x|x==b"\r\n\r\n"){header_end=end+4;break;}}
    let Ok(header)=std::str::from_utf8(&bytes[..header_end]) else{return;};
    let lines=header.lines().collect::<Vec<_>>();let request=lines.first().copied().unwrap_or("").to_string();
    let value=|key:&str|lines.iter().skip(1).filter_map(|l|l.split_once(':')).find(|(k,_)|k.eq_ignore_ascii_case(key)).map(|(_,v)|v.trim());
    if value("Origin")!=Some(ORIGIN) || value("Host")!=Some(ADDRESS){reply(&mut stream,"403 Forbidden","{}");return;}
    if request.starts_with("OPTIONS "){reply(&mut stream,"204 No Content","");return;}
    if request=="GET /status HTTP/1.1" {
        let body=SESSION.lock().ok().and_then(|slot|slot.as_ref().map(|s|serde_json::json!({"phase":s.phase,"connected":s.companion_seen,"session_seconds":s.seconds.floor(),"persisted_seconds":s.saved.floor(),"ever_played":s.ever_played,"ignored_early_ends":s.ignored_early_ends}).to_string())).unwrap_or("{}".into());
        reply(&mut stream,"200 OK",&body);return;
    }
    // Extension GET requests can omit Origin when Chrome grants host access.
    // POST supplies it, keeping the exact extension-origin check intact.
    if request=="GET /session HTTP/1.1" || request=="POST /session HTTP/1.1" {
        let body=SESSION.lock().ok().and_then(|slot|slot.as_ref().filter(|s|s.phase!=Phase::Ended).map(|s|serde_json::json!({"nonce":s.nonce,"asin":s.asin,"tab":s.tab}).to_string())).unwrap_or("{}".into());
        reply(&mut stream,"200 OK",&body);return;
    }
    if value("Transfer-Encoding").is_some(){reply(&mut stream,"400 Bad Request","{}");return;}
    let length=value("Content-Length").and_then(|v|v.parse::<usize>().ok()).filter(|n|*n<=2048);
    if request!="POST /state HTTP/1.1" || value("Content-Type")!=Some("application/json") || length.is_none(){reply(&mut stream,"400 Bad Request","{}");return;}
    let length=length.unwrap();
    while bytes.len()<header_end+length {let Ok(n)=stream.read(&mut buffer) else{return;};if n==0{return;}bytes.extend_from_slice(&buffer[..n]);if bytes.len()>8192{return;}}
    let event=serde_json::from_slice::<Event>(&bytes[header_end..header_end+length]);
    let accepted=if let (Ok(event),Ok(mut slot))=(event,SESSION.lock()) {
        slot.as_mut().is_some_and(|s|{let accepted=ingest(s,event,Instant::now());if s.seconds-s.saved>=10.0 || s.phase==Phase::Ended {persist(s);}accepted})
    }else{false};
    reply(&mut stream,if accepted{"200 OK"}else{"409 Conflict"},"{}");
}
fn start_server()->Result<(),String> {
    SERVER.get_or_init(||{
        let listener=TcpListener::bind(ADDRESS).map_err(|_|"Luna companion port is unavailable. Close another CreteOS instance and retry.".to_string())?;
        std::thread::spawn(move||{for stream in listener.incoming().flatten(){serve(stream);}});Ok(())
    }).clone()
}
#[cfg(test)]mod tests {
    use super::*;
    #[test]fn fallback_releases_the_browser_even_with_a_connected_companion(){
        assert!(needs_foreground(Duration::from_secs(3),false,true,true));
        assert!(!needs_foreground(Duration::from_secs(2),false,true,true));
        assert!(!needs_foreground(Duration::from_secs(3),true,true,true));
        assert!(needs_foreground(Duration::from_secs(3),true,false,true));
        assert!(!needs_foreground(Duration::from_secs(3),false,true,false));
    }
    fn session()->Session{Session{nonce:"test".into(),game:"luna:test".into(),asin:"TEST".into(),tab:None,document:String::new(),phase:Phase::Waiting,heard:Instant::now(),tick:Instant::now(),claimed:None,sequence:0,companion_seen:false,seconds:0.0,saved:0.0,ever_played:false,ignored_early_ends:0}}
    #[test]fn old_end_cannot_claim_or_terminate_a_new_launch(){
        let mut s=session();let now=s.tick;
        assert!(!ingest(&mut s,event(Phase::Ended,99),now));
        assert_eq!(s.tab,None);assert_eq!(s.phase,Phase::Waiting);assert_eq!(s.ignored_early_ends,1);
        assert!(ingest(&mut s,event(Phase::Buffering,1),now));
        assert!(!ingest(&mut s,event(Phase::Ended,2),now));assert_eq!(s.phase,Phase::Buffering);
        assert!(ingest(&mut s,event(Phase::Playing,3),now+Duration::from_secs(1)));
        assert!(ingest(&mut s,event(Phase::Playing,4),now+Duration::from_secs(2)));
        assert!(ingest(&mut s,event(Phase::Ended,5),now+Duration::from_secs(3)));assert_eq!(s.seconds,1.0);
    }
    fn event(phase:Phase,sequence:u64)->Event{Event{nonce:"test".into(),tab:1,frame:0,document:"doc1".into(),phase,sequence}}
    #[test]fn only_advancing_stream_heartbeats_count(){let mut s=session();let now=s.tick;
        assert!(ingest(&mut s,event(Phase::Waiting,1),now));assert_eq!(s.seconds,0.0);
        assert!(ingest(&mut s,event(Phase::Playing,2),now+Duration::from_secs(1)));
        assert!(ingest(&mut s,event(Phase::Playing,3),now+Duration::from_secs(2)));assert_eq!(s.seconds,1.0);
        ingest(&mut s,event(Phase::Buffering,4),now+Duration::from_secs(3));
        ingest(&mut s,event(Phase::Playing,5),now+Duration::from_secs(10));assert_eq!(s.seconds,1.0);
        ingest(&mut s,event(Phase::Ended,6),now+Duration::from_secs(11));assert!(!ingest(&mut s,event(Phase::Playing,7),now+Duration::from_secs(12)));}
    #[test]fn rejects_wrong_nonce_tab_and_duplicate(){let mut s=session();let now=s.tick;ingest(&mut s,event(Phase::Playing,1),now);
        assert!(!ingest(&mut s,event(Phase::Playing,1),now));let mut e=event(Phase::Playing,2);e.tab=2;assert!(!ingest(&mut s,e,now));let mut e=event(Phase::Playing,2);e.nonce="old".into();assert!(!ingest(&mut s,e,now));}
    #[test]fn reload_does_not_count_the_gap_or_reject_reset_counter(){
        let mut s=session();let now=s.tick;
        ingest(&mut s,event(Phase::Playing,20),now);
        ingest(&mut s,event(Phase::Playing,21),now+Duration::from_secs(1));
        let mut e=event(Phase::Playing,1);e.document="doc2".into();
        assert!(ingest(&mut s,e,now+Duration::from_secs(20)));assert_eq!(s.seconds,1.0);
        let mut e=event(Phase::Playing,2);e.document="doc2".into();
        assert!(ingest(&mut s,e,now+Duration::from_secs(21)));assert_eq!(s.seconds,2.0);
    }
    #[test]fn queue_binds_tab_but_not_the_wrong_frame(){
        let mut s=session();let now=s.tick;ingest(&mut s,event(Phase::Waiting,1),now);
        let mut wrong=event(Phase::Playing,2);wrong.tab=2;assert!(!ingest(&mut s,wrong,now));
        let mut video=event(Phase::Playing,2);video.frame=3;assert!(ingest(&mut s,video,now));
        assert!(!ingest(&mut s,event(Phase::Playing,3),now));assert_eq!(s.seconds,0.0);
    }

    #[test]fn malformed_or_extra_browser_data_is_rejected(){
        assert!(serde_json::from_str::<Event>(r#"{"nonce":"test","tab":1,"frame":0,"document":"doc","phase":"playing","sequence":1,"cookie":"secret"}"#).is_err());
        assert!(serde_json::from_str::<Event>(r#"{"nonce":"test","tab":1,"frame":0,"document":"doc","phase":"unknown","sequence":1}"#).is_err());
    }

}

//! Remote library cache and identities. Launch IDs never double as metadata IDs.
use serde::{Deserialize,Serialize};
use std::sync::atomic::{AtomicBool,Ordering};
use tauri::Emitter;

#[derive(Clone,Debug,Serialize,Deserialize,PartialEq)]
#[serde(rename_all="camelCase")]
pub struct Game {
    pub id:String, pub name:String, pub slug:String, pub runner:String, pub launchable:bool,
    pub reason:String, pub playtime_minutes:Option<u32>, pub last_played:i64, pub shortcut_id:Option<String>,
}
#[derive(Default,Serialize,Deserialize)]
struct Cache { host:String, attempted:u64, games:Vec<Game> }
static REFRESHING:AtomicBool=AtomicBool::new(false);
static CACHE_WRITE:std::sync::Mutex<()>=std::sync::Mutex::new(());
fn now()->u64 {std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_secs()}
fn path()->std::path::PathBuf {super::liftoff_dir().join("lutris_host_library.json")}
fn load()->Cache {std::fs::read(path()).ok().and_then(|b|serde_json::from_slice(&b).ok()).unwrap_or_default()}
pub fn identity(host:&str,id:&str)->String {format!("lutris:{}:{}",host.to_ascii_lowercase(),id)}
/// Buddy must receive a ShortcutGameId so it selects steam://rungameid/.
/// The 32-bit VDF appid is retained for artwork and process/window matching.
pub fn buddy_id(shortcut_id:&str)->Result<String,String> {
    let id=shortcut_id.parse::<u32>().map_err(|_|"Invalid registered shortcut ID")?;
    if id & 0x80000000 == 0 {return Err("Registered ID is not a non-Steam shortcut".into());}
    Ok((((id as u64)<<32)|0x02000000).to_string())
}
fn validate(games:&[Game])->Result<(),String> {
    if games.len()>1000 || games.iter().any(|g|g.id.is_empty() || g.id.len()>9 || !g.id.bytes().all(|b|b.is_ascii_digit()) || g.name.is_empty() || g.name.len()>2048 || g.shortcut_id.as_ref().is_some_and(|id|id.parse::<u32>().is_err())) {return Err("Invalid Lutris inventory".into());}
    let mut ids=std::collections::HashSet::new();
    if games.iter().any(|g|!ids.insert(&g.id)){return Err("Duplicate Lutris identity".into());} Ok(())
}
pub fn query(host:&str)->Result<Vec<Game>,String> {
    let mut command=super::streaming::host_command(host)?;
    command.arg("python3 -");
    let output=super::streaming::bounded_output(command,Some(include_bytes!("lutris_inventory.py")))?;
    if !output.status.success(){return Err("Could not read Bosgame's Lutris library. Saved games have been retained.".into());}
    let games:Vec<Game>=serde_json::from_slice(&output.stdout).map_err(|_|"Invalid Lutris response")?;
    validate(&games)?;Ok(games)
}
fn configured_host()->Option<String> {
    let folder=dirs::data_local_dir()?.join("CreteOS-Bootstrap/ssh/bosgame.json");
    let bytes=std::fs::read(folder).ok()?;
    let value:serde_json::Value=serde_json::from_slice(bytes.strip_prefix(b"\xef\xbb\xbf").unwrap_or(&bytes)).ok()?;
    let host=value["host"].as_str()?.to_owned();super::streaming::validate_host(&host).ok()?;Some(host)
}
pub fn refresh(host:&str)->Result<(Vec<Game>,bool),String> {
    let games=query(host)?;let _lock=CACHE_WRITE.lock().map_err(|_|"Lutris cache unavailable")?;let old=load();let changed=old.host!=host || old.games!=games;
    let cache=Cache{host:host.to_owned(),attempted:now(),games:games.clone()};
    std::fs::create_dir_all(super::liftoff_dir()).map_err(|e|e.to_string())?;
    let data=serde_json::to_vec_pretty(&cache).map_err(|e|e.to_string())?;
    let temp=path().with_extension("json.tmp");std::fs::write(&temp,data).map_err(|e|e.to_string())?;
    std::fs::rename(temp,path()).map_err(|e|e.to_string())?;Ok((games,changed))
}
pub fn schedule(app:tauri::AppHandle) {
    let Some(host)=configured_host() else{return;};
    if now().saturating_sub(load().attempted)<300 || REFRESHING.swap(true,Ordering::SeqCst){return;}
    std::thread::spawn(move||{
        match refresh(&host) {
            Ok((_,true))=>{let _=app.emit("library-rescan-needed",());},
            Err(_)=>{if let Ok(_lock)=CACHE_WRITE.lock(){let mut cache=load();cache.attempted=now();if let Ok(data)=serde_json::to_vec(&cache){let _=std::fs::write(path(),data);}}},
            _=>{}
        }
        REFRESHING.store(false,Ordering::SeqCst);
    });
}
pub fn merge(apps:&mut Vec<super::AppEntry>) {
    let cache=load();
    if configured_host().as_deref()!=Some(cache.host.as_str()) || validate(&cache.games).is_err(){return;}
    for game in cache.games {
        apps.push(super::AppEntry{id:identity(&cache.host,&game.id),name:game.name,source:"lutris".into(),app_type:"game".into(),
            store_id:Some(game.id.clone()),launch_path:format!("lutris://{}/{}",cache.host,game.id),installed:false,
            playtime_minutes:game.playtime_minutes,last_played:if game.last_played>0{Some(game.last_played)}else{None},..Default::default()});
    }
}
#[tauri::command]
pub fn get_lutris_progress(game_id:String)->Result<serde_json::Value,String> {
    let cache=load();
    let game=cache.games.iter().find(|game|identity(&cache.host,&game.id)==game_id).ok_or("Lutris game is not in the saved host library")?;
    Ok(serde_json::json!({"minutes":game.playtime_minutes,"last_played":game.last_played,"source":"Lutris on Bosgame"}))
}
#[tauri::command]
pub async fn get_lutris_games(app:tauri::AppHandle,host:String)->Result<Vec<Game>,String> {
    tauri::async_runtime::spawn_blocking(move||{let (games,changed)=refresh(&host)?;if changed{let _=app.emit("library-rescan-needed",());}Ok(games)}).await.map_err(|e|e.to_string())?
}
pub fn launch_id(host:&str,id:&str,game_id:Option<&str>)->Result<String,String> {
    if game_id!=Some(identity(host,id).as_str()){return Err("Lutris host/game identity does not match".into());}
    let game=query(host)?.into_iter().find(|g|g.id==id).ok_or("This Lutris game is no longer installed on Bosgame")?;
    if !game.launchable{return Err(game.reason);}
    game.shortcut_id.ok_or("Add this Lutris game with the CreteOS host shortcut setup before streaming".into())
}
#[cfg(test)]
mod tests {
    #[test]fn buddy_uses_64_bit_shortcut_game_id(){assert_eq!(super::buddy_id("3137199813").unwrap(),"13474170597885870080");assert!(super::buddy_id("2638890").is_err());assert!(super::buddy_id("4294967296").is_err());}
    #[test]fn identities_do_not_collide_with_store_products(){assert_eq!(super::identity("Bosgame","1"),"lutris:bosgame:1");assert_ne!(super::identity("other","1"),super::identity("Bosgame","1"));}
    #[test]fn rejects_bad_and_duplicate_inventory(){let game=super::Game{id:"1".into(),name:"Mouse".into(),slug:"mouse".into(),runner:"wine".into(),launchable:true,reason:String::new(),playtime_minutes:Some(30),last_played:0,shortcut_id:Some("4000000000".into())};assert!(super::validate(&[game.clone()]).is_ok());assert!(super::validate(&[game.clone(),game]).is_err());}
}

//! Read-only account activity. Ownership, launch IDs and local recents stay separate.
use super::*;
use sha2::{Digest, Sha256};

#[derive(Clone, Default, Serialize, Deserialize, Debug)]
pub struct Achievement {
    pub id: String, pub name: String, pub description: String,
    pub unlocked: bool, pub unlocked_at: Option<i64>,
}
#[derive(Clone, Default, Serialize, Deserialize, Debug)]
pub struct Progress {
    pub minutes: Option<u32>, pub last_played: Option<i64>,
    pub earned: Option<u32>, pub total: Option<u32>,
    pub xp: Option<u32>, pub total_xp: Option<u32>,
    pub achievements: Vec<Achievement>,
}
#[derive(Default, Serialize, Deserialize)]
struct Cache { account_id: String, details_at: u64, times_at: u64, value: Progress }
fn path(store: Store, id: &str) -> std::path::PathBuf {
    let key=format!("{:x}",Sha256::digest(id.as_bytes()));
    super::super::liftoff_dir().join("store_progress").join(format!("{}-{key}.json",store.name()))
}
fn cached(store: Store, id: &str, account: &str) -> Cache {
    std::fs::read(path(store,id)).ok().and_then(|b|serde_json::from_slice::<Cache>(&b).ok())
        .filter(|c|c.account_id==account).unwrap_or_else(|| Cache{account_id:account.into(),..Default::default()})
}
fn save(store: Store, id: &str, cache: &Cache) {
    let file=path(store,id); let temp=file.with_extension("json.tmp");
    if std::fs::create_dir_all(file.parent().unwrap()).is_ok() {
        if let Ok(bytes)=serde_json::to_vec(cache) {if std::fs::write(&temp,bytes).is_ok(){let _=std::fs::rename(temp,file);}}
    }
}
fn number(v: &Value) -> Option<u32> {v.as_u64().and_then(|n|u32::try_from(n).ok())}
fn timestamp(v: &Value) -> Option<i64> {
    v.as_i64().filter(|n|*n>0).map(|n|if n>10_000_000_000 {n/1000}else{n}).or_else(||{
        let s=v.as_str()?;
        chrono::DateTime::parse_from_rfc3339(s).or_else(|_|chrono::DateTime::parse_from_str(s,"%Y-%m-%dT%H:%M:%S%z")).ok().map(|t|t.timestamp()).filter(|n|*n>0)
    })
}
fn gql(c: &reqwest::blocking::Client, session: &Session, query: &str, variables: Value) -> Result<Value,String> {
    let value=response(c.post("https://launcher.store.epicgames.com/graphql").bearer_auth(&session.access)
        .header("User-Agent","EpicGamesLauncher/14.0.8-22004686+++Portal+Release-Live").json(&json!({"query":query,"variables":variables})))?;
    if value.get("errors").is_some() {return Err("Account activity is temporarily unavailable".into());}
    Ok(value)
}
fn epic_times(value: &Value) -> Option<HashMap<String,u32>> {
    Some(value.pointer("/data/PlaytimeTracking/total")?.as_array()?.iter().filter_map(|v| {
        // Epic reports seconds; GOG's time_sum is already minutes.
        Some((v["artifactId"].as_str()?.to_ascii_lowercase(),u32::try_from(v["totalTime"].as_u64()?/60).ok()?))
    }).collect())
}
fn apply_time(cache: &mut Cache, minutes: Option<u32>, last: Option<i64>) {
    if minutes.is_some(){cache.value.minutes=minutes;cache.times_at=now();}
    if let Some(last)=last {cache.value.last_played=Some(cache.value.last_played.unwrap_or_default().max(last));}
}
pub(super) fn refresh_times(store: Store, session: &Session, games: &[OwnedGame]) {
    let Ok(c)=client() else{return;};
    if store==Store::Epic {
        let values=gql(&c,session,"query($accountId:String!){PlaytimeTracking{total(accountId:$accountId){artifactId totalTime}}}",json!({"accountId":session.account_id})).ok().and_then(|v|epic_times(&v));
        if let Some(values)=values {for game in games {
            let mut cache=cached(store,&game.id,&session.account_id);
            // A successful full total response omits unplayed artifacts.
            apply_time(&mut cache,Some(values.get(&game.id.to_ascii_lowercase()).copied().unwrap_or(0)),None);save(store,&game.id,&cache);
        }}
    }else{
        for batch in games.chunks(4) {std::thread::scope(|scope|{for game in batch {let c=&c;scope.spawn(move||{
            if let Ok(v)=response(c.get(format!("https://gameplay.gog.com/games/{}/users/{}/sessions",game.id,session.account_id)).bearer_auth(&session.access)) {
                let mut cache=cached(store,&game.id,&session.account_id);apply_time(&mut cache,number(&v["time_sum"]),timestamp(&v["last_played"]));save(store,&game.id,&cache);
            }
        });}});}
    }
}
pub(super) fn merge(apps: &mut [AppEntry], source: &str) {
    let Ok(store)=Store::parse(source) else{return;};let Some(library)=load(store) else{return;};
    for app in apps.iter_mut().filter(|a|a.source==source){if let Some(id)=&app.store_id {
        let value=cached(store,id,&library.account_id).value;
        if value.minutes.is_some(){app.playtime_minutes=value.minutes;}
        if let Some(last)=value.last_played{app.last_played=Some(app.last_played.unwrap_or_default().max(last));}
    }}
}
fn epic_achievements(schema: &Value, player: &Value) -> Option<Progress> {
    let product=schema.pointer("/data/Achievement/productAchievementsRecordBySandbox")?;
    let total=number(&product["totalAchievements"])?;
    if total==0{return None;}
    let records=player.pointer("/data/PlayerAchievement/playerAchievementGameRecordsBySandbox/records")?.as_array()?;
    let record=records.first();
    let unlocked: HashMap<String,&Value>=record.and_then(|r|r["playerAchievements"].as_array()).into_iter().flatten()
        .filter_map(|item|{let v=&item["playerAchievement"];Some((v["achievementName"].as_str()?.into(),v))}).collect();
    let mut result=Progress{earned:Some(record.and_then(|r|number(&r["totalUnlocked"])).unwrap_or(0)),total:Some(total),xp:record.and_then(|r|number(&r["totalXP"])).or(Some(0)),total_xp:number(&product["totalProductXP"]),..Default::default()};
    if let Some(items)=product["achievements"].as_array(){for item in items {
        let a=&item["achievement"];let Some(id)=a["name"].as_str() else{continue;};let user=unlocked.get(id);
        let earned=user.is_some_and(|v|v["unlocked"].as_bool()==Some(true));let hidden=!earned&&a["hidden"].as_bool()==Some(true);
        result.achievements.push(Achievement{id:id.into(),name:if hidden{"Hidden achievement".into()}else{a[if earned{"unlockedDisplayName"}else{"lockedDisplayName"}].as_str().unwrap_or(id).into()},description:if hidden{String::new()}else{a[if earned{"unlockedDescription"}else{"lockedDescription"}].as_str().unwrap_or_default().into()},unlocked:earned,unlocked_at:user.and_then(|v|timestamp(&v["unlockDate"]))});
    }}
    Some(result)
}
fn gog_achievements(value: &Value) -> Option<Progress> {
    let total=number(&value["total_count"])?;if total==0{return None;}
    let items=value["items"].as_array()?;
    if items.len()!=total as usize{return None;} // Never report a partial list as complete.
    let achievements=items.iter().filter_map(|a|{
        let id=a["achievement_key"].as_str().or_else(||a["achievement_id"].as_str())?;
        let date=timestamp(&a["date_unlocked"]);let hidden=date.is_none()&&a["visible"].as_bool()==Some(false);
        Some(Achievement{id:id.into(),name:if hidden{"Hidden achievement".into()}else{a["name"].as_str().unwrap_or(id).into()},description:if hidden{String::new()}else{a["description"].as_str().unwrap_or_default().into()},unlocked:date.is_some(),unlocked_at:date})
    }).collect::<Vec<_>>();
    if achievements.len()!=total as usize{return None;}
    Some(Progress{earned:Some(achievements.iter().filter(|a|a.unlocked).count() as u32),total:Some(total),achievements,..Default::default()})
}
fn merge_achievements(cache: &mut Cache, result: Progress){
    cache.value.earned=result.earned;cache.value.total=result.total;cache.value.xp=result.xp;cache.value.total_xp=result.total_xp;cache.value.achievements=result.achievements;
}

#[tauri::command]
pub async fn store_game_progress(app: tauri::AppHandle, source: String, game_id: String, offline: Option<bool>, refresh: Option<bool>) -> Result<Progress,String> {
    let store=Store::parse(&source)?;
    tauri::async_runtime::spawn_blocking(move|| -> Result<Progress,String> {
        let library=load(store).ok_or("Connect this store first")?;
        let game=library.games.iter().find(|g|g.id.eq_ignore_ascii_case(&game_id)).ok_or("Game is not in this account library")?.clone();
        let old=cached(store,&game.id,&library.account_id);
        let age=now().saturating_sub(old.details_at);
        if offline.unwrap_or(false) || (old.details_at>0&&age<if refresh.unwrap_or(false){10}else{86400}) {return Ok(old.value);}
        let _guard=store.lock().lock().map_err(|_|"Account is busy")?;
        let generation=store.generation().load(Ordering::SeqCst);
        let current=load(store).ok_or("Account disconnected")?;
        if current.account_id!=library.account_id{return Err("Account changed".into());}
        let refresh=store.key()?.get_password().map_err(|_|"Reconnect this account to refresh activity")?;
        let session=match exchange(store,"refresh_token",&refresh){Ok(session)=>session,Err(_)=>return Ok(old.value)};
        if session.account_id!=library.account_id{return Err("Account changed".into());}
        check_generation(store,generation)?;store.key()?.set_password(&session.refresh).map_err(|_|"Could not renew account session")?;
        let c=client()?;let mut cache=cached(store,&game.id,&library.account_id);
        if store==Store::Epic {
            if let Ok(v)=response(c.get(format!("https://library-service.live.use1a.on.epicgames.com/library/api/public/playtime/account/{}/artifact/{}",session.account_id,game.id)).bearer_auth(&session.access)) {
                apply_time(&mut cache,v["totalTime"].as_u64().and_then(|n|u32::try_from(n/60).ok()),timestamp(&v["lastPlayed"]));
            }
            let vars=json!({"sandboxId":game.namespace,"epicAccountId":session.account_id});
            let schema=gql(&c,&session,"query($sandboxId:String!){Achievement{productAchievementsRecordBySandbox(sandboxId:$sandboxId,locale:\"en\"){totalAchievements totalProductXP achievements{achievement{name hidden unlockedDisplayName lockedDisplayName unlockedDescription lockedDescription}}}}}",vars.clone());
            let player=gql(&c,&session,"query($sandboxId:String!,$epicAccountId:String!){PlayerAchievement{playerAchievementGameRecordsBySandbox(sandboxId:$sandboxId,epicAccountId:$epicAccountId){records{totalUnlocked totalXP playerAchievements{playerAchievement{achievementName unlocked unlockDate}}}}}}",vars);
            if let (Ok(schema),Ok(player))=(schema,player){if let Some(value)=epic_achievements(&schema,&player){merge_achievements(&mut cache,value);}}
        }else{
            if let Ok(v)=response(c.get(format!("https://gameplay.gog.com/games/{}/users/{}/sessions",game.id,session.account_id)).bearer_auth(&session.access)) {apply_time(&mut cache,number(&v["time_sum"]),timestamp(&v["last_played"]));}
            if let Ok(v)=response(c.get(format!("https://gameplay.gog.com/clients/{}/users/{}/achievements",game.id,session.account_id)).query(&[("limit","1000")]).bearer_auth(&session.access)) {if let Some(value)=gog_achievements(&v){merge_achievements(&mut cache,value);}}
        }
        check_generation(store,generation)?;cache.details_at=now();save(store,&game.id,&cache);
        let _=app.emit("store-progress-changed",());Ok(cache.value)
    }).await.map_err(|_|"Account activity could not finish".to_string())?
}

#[cfg(test)] mod tests {
    use super::*;
    #[test] fn times_keep_units_and_unknown_distinct_from_zero(){
        let times=epic_times(&json!({"data":{"PlaytimeTracking":{"total":[{"artifactId":"Min","totalTime":1518}]}}})).unwrap();assert_eq!(times["min"],25);
        assert!(epic_times(&json!({"errors":[]})).is_none());
        let mut c=Cache::default();apply_time(&mut c,Some(9533),None);apply_time(&mut c,None,None);assert_eq!(c.value.minutes,Some(9533));assert_eq!(c.value.last_played,None);
    }
    #[test] fn gog_counts_unlocks_and_rejects_partial_lists(){
        let v=json!({"total_count":2,"items":[{"achievement_key":"a","name":"First","date_unlocked":"2024-04-07T22:39:10+0000"},{"achievement_key":"b","visible":false,"name":"Spoiler","date_unlocked":null}]});
        let p=gog_achievements(&v).unwrap();assert_eq!(p.earned,Some(1));assert_eq!(p.total,Some(2));assert_eq!(p.achievements[1].name,"Hidden achievement");
        assert!(gog_achievements(&json!({"total_count":3,"items":[]})).is_none());
    }
    #[test] fn epic_empty_player_record_is_zero_earned_with_known_schema(){
        let p=epic_achievements(&json!({"data":{"Achievement":{"productAchievementsRecordBySandbox":{"totalAchievements":49,"totalProductXP":1000}}}}),&json!({"data":{"PlayerAchievement":{"playerAchievementGameRecordsBySandbox":{"records":[]}}}})).unwrap();assert_eq!(p.earned,Some(0));assert_eq!(p.total,Some(49));
        assert!(epic_achievements(&json!({}),&json!({})).is_none());
    }
}

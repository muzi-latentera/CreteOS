use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::{sync::Mutex, time::{SystemTime, UNIX_EPOCH}};
static FETCH: Mutex<()> = Mutex::new(());
fn now() -> u64 { SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_secs() }
fn normalized(name: &str) -> String { name.chars().filter(|c| c.is_alphanumeric()).flat_map(char::to_lowercase).collect() }
pub fn title(game_name: &str) -> Result<Value, String> {
    title_with_policy(game_name, false)
}
fn title_with_policy(game_name: &str, offline: bool) -> Result<Value, String> {
    title_with_freshness(game_name, offline, 86400)
}
fn title_with_freshness(game_name: &str, offline: bool, max_age: u64) -> Result<Value, String> {
    let _lock = FETCH.lock().map_err(|_| "Xbox details cache unavailable")?;
    let account = crate::load_xbox_account_meta().ok_or("Connect your Microsoft account first")?;
    if account.xuid.is_empty() || !account.xuid.bytes().all(|b| b.is_ascii_digit()) { return Err("Connect your Microsoft account first".into()); }
    let path = crate::liftoff_dir().join("xbox_title_details").join(format!("{}.json", account.xuid));
    let cached = std::fs::read(&path).ok().and_then(|b| serde_json::from_slice::<Value>(&b).ok());
    let find = |body: &Value| body["titles"].as_array().and_then(|titles| titles.iter().find(|t| t["name"].as_str().map(normalized) == Some(normalized(game_name)))).cloned();
    if let Some(ref cache) = cached { if offline || now().saturating_sub(cache["fetchedAt"].as_u64().unwrap_or(0)) < max_age { return find(cache).ok_or("This title is not in your Xbox history".into()); } }
    if offline { return Err("Xbox details have not been saved for offline use yet".into()); }
    let fresh = (|| {
        let (token, userhash, _, xuid) = crate::xbox_xsts_from_refresh()?;
        if xuid != account.xuid { return Err("Microsoft account changed; refresh the library".into()); }
        let mut body: Value = crate::xbox_http()?.get(format!("https://titlehub.xboxlive.com/users/xuid({xuid})/titles/titlehistory/decoration/detail,achievement,stats"))
            .header("Authorization", format!("XBL3.0 x={userhash};{token}"))
            .header("x-xbl-contract-version", "2").header("Accept-Language", "en-US")
            .send().map_err(|_| "Could not refresh Xbox details")?.error_for_status().map_err(|_| "Microsoft did not return Xbox details")?
            .json().map_err(|_| "Invalid Xbox details response")?;
        body["fetchedAt"] = json!(now());
        if let Some(parent) = path.parent() { let _ = std::fs::create_dir_all(parent); }
        if let Ok(bytes) = serde_json::to_vec(&body) { let _ = std::fs::write(&path, bytes); }
        Ok::<_, String>(body)
    })();
    match fresh { Ok(body) => find(&body).ok_or("This title is not in your Xbox history".into()), Err(error) => cached.as_ref().and_then(find).ok_or(error) }
}
pub fn metadata(game_name: &str, offline: bool) -> Result<crate::store_metadata::StoreMetadata, String> {
    let title = title_with_policy(game_name, offline)?; let detail = &title["detail"];
    let id = title["titleId"].as_str().map(str::to_owned).or_else(|| title["titleId"].as_u64().map(|v| v.to_string())).unwrap_or_default();
    serde_json::from_value(json!({"cacheVersion":6,"source":"xbox","appId":id,
        "shortDescription":detail["shortDescription"].as_str().unwrap_or_default(),"aboutHtml":detail["description"].as_str().unwrap_or_default(),
        "developers":detail["developerName"].as_str().map(|v| vec![v]).unwrap_or_default(),
        "publishers":detail["publisherName"].as_str().map(|v| vec![v]).unwrap_or_default(),
        "genres":[],"releaseDate":detail["releaseDate"],"screenshots":[],"movies":[],"controllerSupport":"none","fetchedAt":now()})).map_err(|_| "Invalid Xbox title metadata".into())
}
#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all="camelCase")]
pub struct XboxProgress { pub minutes: Option<f64>, pub earned: Option<u64>, pub total: Option<u64>, pub gamerscore: Option<u64>, pub total_gamerscore: Option<u64> }
fn played_minutes(value: &Value) -> Option<f64> {
    match value {
        Value::Object(map) => {
            if map.get("statname").or_else(|| map.get("name")).and_then(Value::as_str) == Some("MinutesPlayed") {
                return map.get("value").and_then(|v| v.as_f64().or_else(|| v.as_str().and_then(|s| s.parse().ok()))).filter(|n: &f64| n.is_finite() && *n >= 0.0);
            }
            map.values().find_map(played_minutes)
        }, Value::Array(items) => items.iter().find_map(played_minutes), _ => None,
    }
}
#[tauri::command]
pub async fn get_xbox_game_progress(game_name: String, offline: Option<bool>, refresh: Option<bool>) -> Result<XboxProgress, String> {
    if game_name.is_empty() || game_name.len() > 240 { return Err("Invalid game title".into()); }
    tauri::async_runtime::spawn_blocking(move || {
        let offline = offline.unwrap_or(false);
        // Keep daily caching for ordinary browsing. A return from gameplay can
        // refresh totals, with a small cache window to coalesce visible panels.
        let max_age = if refresh.unwrap_or(false) { 10 } else { 86400 };
        let title = title_with_freshness(&game_name, offline, max_age)?;
        let achievement = &title["achievement"];
        let minutes = title.pointer("/stats/minutesPlayed").and_then(Value::as_f64);
        let mut progress = XboxProgress { minutes, earned: achievement["currentAchievements"].as_u64(), total: achievement["totalAchievements"].as_u64().filter(|v| *v > 0),
            gamerscore: achievement["currentGamerscore"].as_u64(), total_gamerscore: achievement["totalGamerscore"].as_u64().filter(|v| *v > 0) };
        let account = crate::load_xbox_account_meta().ok_or("Connect Microsoft first")?;
        let id = title["titleId"].as_str().ok_or("Missing Xbox title ID")?;
        if !id.bytes().all(|b| b.is_ascii_digit()) || id.len() > 12 { return Err("Invalid Xbox title ID".into()); }
        let path = crate::liftoff_dir().join("xbox_progress").join(&account.xuid).join(format!("{id}.json"));
        let cached = std::fs::read(&path).ok().and_then(|b| serde_json::from_slice::<Value>(&b).ok());
        if let Some(ref cache) = cached { if offline || now().saturating_sub(cache["fetchedAt"].as_u64().unwrap_or(0)) < max_age { return serde_json::from_value(cache["progress"].clone()).map_err(|_| "Invalid cached Xbox progress".into()); } }
        if offline { return Ok(progress); }
        // MinutesPlayed belongs to the game's statistics service configuration,
        // not its Microsoft Store product ID. Obtain that SCID from achievements.
        let enrich = (|| {
            let (token, userhash, _, xuid) = crate::xbox_xsts_from_refresh()?;
            if xuid != account.xuid { return Err("Microsoft account changed".into()); }
            let header = format!("XBL3.0 x={userhash};{token}");
            let client = crate::xbox_http()?;
            let body: Value = client.get(format!("https://achievements.xboxlive.com/users/xuid({xuid})/achievements"))
                .query(&[("titleId", id), ("maxItems", "100")]).header("Authorization", &header).header("x-xbl-contract-version", "2")
                .send().map_err(|_| "Xbox achievements unavailable")?.error_for_status().map_err(|_| "Xbox achievements unavailable")?.json().map_err(|_| "Invalid achievements response")?;
            if let Some(items) = body["achievements"].as_array() {
                if body.pointer("/pagingInfo/continuationToken").and_then(Value::as_str).is_none_or(str::is_empty) && !items.is_empty() { progress.total = Some(items.len() as u64); }
                if let Some(scid) = items.first().and_then(|a| a["serviceConfigId"].as_str()).filter(|s| s.len() == 36 && s.bytes().all(|b| b.is_ascii_hexdigit() || b == b'-')) {
                    if let Ok(response) = client.get(format!("https://userstats.xboxlive.com/users/xuid({xuid})/scids/{scid}/stats/MinutesPlayed"))
                        .header("Authorization", &header).header("x-xbl-contract-version", "1").send().and_then(|r| r.error_for_status()) {
                        if let Ok(stats) = response.json::<Value>() { progress.minutes = played_minutes(&stats); }
                    }
                }
            }
            Ok::<_, String>(())
        })();
        if enrich.is_err() { if let Some(old) = cached.and_then(|c| serde_json::from_value::<XboxProgress>(c["progress"].clone()).ok()) { progress.minutes = progress.minutes.or(old.minutes); progress.total = progress.total.or(old.total); } }
        if let Some(parent) = path.parent() { let _ = std::fs::create_dir_all(parent); }
        if let Ok(bytes) = serde_json::to_vec(&json!({"fetchedAt":now(),"progress":progress})) { let _ = std::fs::write(path, bytes); }
        Ok(progress)
    }).await.map_err(|e| e.to_string())?
}

#[cfg(test)] mod tests {
    use super::*;
    #[test] fn preserves_unknown_time_and_reads_real_stat_names() {
        assert_eq!(played_minutes(&json!({"stats":[{"statname":"MinutesPlayed","value":"922"}]})), Some(922.0));
        assert_eq!(played_minutes(&json!({"stats":[]})), None);
        assert_eq!(played_minutes(&json!({"name":"MinutesPlayed","value":"-1"})), None);
    }
}

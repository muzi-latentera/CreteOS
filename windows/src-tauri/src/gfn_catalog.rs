use serde_json::{json, Value};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

fn bundled() -> Value { serde_json::from_str(include_str!("../../src/crete/gfnCatalog.json")).expect("Bundled public NVIDIA catalogue") }
fn path() -> std::path::PathBuf { crate::liftoff_dir().join("geforce_catalog.json") }
fn saved() -> Value {
    std::fs::read(path()).ok().and_then(|bytes| serde_json::from_slice::<Value>(&bytes).ok())
        .filter(|value| value["complete"] == true && value["games"].as_array().is_some_and(|games| !games.is_empty()))
        .unwrap_or_else(bundled)
}
fn store(source: &str) -> Option<&'static str> {
    match source { "steam" => Some("STEAM"), "xbox" => Some("XBOX"), "epic" => Some("EPIC"), "gog" => Some("GOG"), "ea" => Some("EA_APP"), "ubisoft" => Some("UPLAY"), "battlenet" => Some("BATTLENET"), _ => None }
}
pub fn route(game_id: &str, source: Option<&str>) -> Option<String> {
    let catalogue = saved();
    route_in(&catalogue, game_id, source)
}
fn route_in(catalogue: &Value, game_id: &str, source: Option<&str>) -> Option<String> {
    let game = catalogue["games"].as_array()?.iter().find(|game| game["id"] == game_id)?;
    let source = store(source?)?;
    let mut variants = game["variants"].as_array()?.iter().filter(|variant| variant["appStore"] == source);
    let variant = variants.next()?;
    if variants.next().is_some() { return None; }
    let cms = variant["id"].as_str()?;
    // NVIDIA's client uses the parent UUID when new catalogue variants have no
    // shortName (observed for Phantom Blade Zero). Omitting it opens the shell.
    let name = variant["shortName"].as_str().filter(|name| !name.is_empty()).unwrap_or(game_id);
    if cms.is_empty() || cms.len() > 12 || !cms.bytes().all(|b| b.is_ascii_digit())
        || name.len() > 160 || !name.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'_' || b == b'-') { return None; }
    Some(format!("#?cmsId={cms}&launchSource=External&shortName={name}&parentGameId={game_id}"))
}

#[tauri::command]
pub async fn get_geforce_catalog(offline: Option<bool>, force: Option<bool>) -> Result<Value, String> {
    static LOCK: std::sync::OnceLock<tauri::async_runtime::Mutex<()>> = std::sync::OnceLock::new();
    let _guard = LOCK.get_or_init(|| tauri::async_runtime::Mutex::new(())).lock().await;
    let old = saved();
    let now = SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_secs();
    let force = force.unwrap_or(false);
    if offline.unwrap_or(false) {
        return if force { Err("GeForce NOW could not refresh while offline. Saved catalogue retained.".into()) } else { Ok(old["games"].clone()) };
    }
    if !should_fetch(&old, now, force) { return Ok(old["games"].clone()); }
    let refreshed = fetch().await;
    let failed = refreshed.is_err();
    let mut next = refreshed.unwrap_or_else(|_| old.clone());
    next["attemptedAt"] = json!(now);
    let saved = serde_json::to_vec(&next).map_err(|_| ()).and_then(|bytes| {
        let temp = path().with_extension("json.tmp");
        std::fs::write(&temp, bytes).and_then(|_| std::fs::rename(temp, path())).map_err(|_| ())
    });
    if force && failed { return Err("GeForce NOW catalogue request failed. Saved catalogue retained; try Refresh Library again.".into()); }
    if saved.is_err() {
        return if force { Err("GeForce NOW could not save the refreshed catalogue. Saved catalogue retained.".into()) } else { Ok(old["games"].clone()) };
    }
    Ok(next["games"].clone())
}
fn should_fetch(old: &Value, now: u64, force: bool) -> bool {
    let fetched = old["fetchedAt"].as_u64().or_else(|| old["fetchedAtUtc"].as_str()
        .and_then(|stamp| chrono::DateTime::parse_from_rfc3339(stamp).ok()).map(|date| date.timestamp().max(0) as u64)).unwrap_or(0);
    let attempted = old["attemptedAt"].as_u64().unwrap_or(0);
    force || (now.saturating_sub(fetched) >= 86400 && now.saturating_sub(attempted) >= 300)
}
async fn fetch() -> Result<Value, String> {
    let client = reqwest::Client::builder().timeout(Duration::from_secs(12)).redirect(reqwest::redirect::Policy::none()).build().map_err(|e| e.to_string())?;
    let started = std::time::Instant::now();
    let mut cursor = String::new(); let mut games = Vec::new();
    for _ in 0..20 {
        if started.elapsed() >= Duration::from_secs(75) { return Err("Catalogue refresh timed out".into()); }
        let query = format!("{{ apps(country:\"GB\" language:\"en_GB\" orderBy:\"sortName:ASC\" after:{}) {{ numberReturned pageInfo {{endCursor hasNextPage}} items {{id title gfn {{playType minimumMembershipTierLabel}} variants {{id appStore shortName}}}} }} }}", serde_json::to_string(&cursor).unwrap());
        let response = client.post("https://api-prod.nvidia.com/services/gfngames/v1/gameList").header("Content-Type", "application/json").header("Origin", "https://www.nvidia.com").body(query).send().await.map_err(|e| e.to_string())?.error_for_status().map_err(|e| e.to_string())?;
        let mut response = response; let mut bytes = Vec::new();
        while let Some(chunk) = response.chunk().await.map_err(|e| e.to_string())? { if bytes.len() + chunk.len() > 3_000_000 { return Err("Catalogue page too large".into()); } bytes.extend_from_slice(&chunk); }
        let page: Value = serde_json::from_slice(&bytes).map_err(|e| e.to_string())?;
        let apps = &page["data"]["apps"];
        let items = apps["items"].as_array().ok_or("Missing catalogue page")?;
        if items.is_empty() { return Err("Incomplete catalogue".into()); }
        games.extend(items.iter().cloned());
        if apps["pageInfo"]["hasNextPage"] == false { return Ok(json!({"complete":true,"country":"GB","count":games.len(),"fetchedAt":SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_secs(),"games":games})); }
        let next = apps["pageInfo"]["endCursor"].as_str().ok_or("Missing catalogue cursor")?;
        if next == cursor { return Err("Repeated catalogue page".into()); } cursor = next.to_string();
    }
    Err("Catalogue page limit exceeded".into())
}

#[cfg(test)] mod tests {
    #[test] fn new_variants_use_the_native_clients_uuid_short_name() {
        let id="abebad03-46e0-4ab3-ae49-f7360621a4f0";
        let catalogue=serde_json::json!({"games":[{"id":id,"variants":[{"id":"107018152","appStore":"STEAM","shortName":""}]}]});
        assert_eq!(super::route_in(&catalogue,id,Some("steam")).unwrap(),format!("#?cmsId=107018152&launchSource=External&shortName={id}&parentGameId={id}"));
        assert!(super::route_in(&catalogue,id,Some("xbox")).is_none());
    }
    #[test] fn manual_refresh_bypasses_success_and_failure_throttles() {
        let now = 200_000;
        let old = serde_json::json!({"fetchedAt":now-10,"attemptedAt":now-5});
        assert!(!super::should_fetch(&old, now, false));
        assert!(super::should_fetch(&old, now, true));
        let failed = serde_json::json!({"fetchedAt":now-90_000,"attemptedAt":now-10});
        assert!(!super::should_fetch(&failed, now, false));
        assert!(super::should_fetch(&failed, now, true));
        assert!(super::should_fetch(&failed, now+300, false));
    }
    #[test] fn native_variant_matches_nvidia_desktop_shortcut() {
        assert_eq!(super::route("5aa78bb8-6af4-49d9-b678-4b7c3685a98c", Some("xbox")).unwrap(), "#?cmsId=102396411&launchSource=External&shortName=a_plague_tale_requiem_xbox_app&parentGameId=5aa78bb8-6af4-49d9-b678-4b7c3685a98c");
        assert!(super::route("invalid", Some("xbox")).is_none());
    }
}

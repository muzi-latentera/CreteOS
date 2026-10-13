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
    let game = catalogue["games"].as_array()?.iter().find(|game| game["id"] == game_id)?;
    let source = store(source?)?;
    let mut variants = game["variants"].as_array()?.iter().filter(|variant| variant["appStore"] == source);
    let variant = variants.next()?;
    if variants.next().is_some() { return None; }
    let cms = variant["id"].as_str()?;
    let name = variant["shortName"].as_str().unwrap_or("");
    if cms.is_empty() || cms.len() > 12 || !cms.bytes().all(|b| b.is_ascii_digit())
        || name.len() > 160 || !name.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'_' || b == b'-') { return None; }
    Some(format!("#?cmsId={cms}&launchSource=External{}&parentGameId={game_id}", if name.is_empty() { String::new() } else { format!("&shortName={name}") }))
}

#[tauri::command]
pub async fn get_geforce_catalog(offline: Option<bool>) -> Result<Value, String> {
    // The UI renders the bundled/saved catalogue immediately. Refresh is daily,
    // background-only and never part of opening or launching a game.
    static LOCK: std::sync::OnceLock<tauri::async_runtime::Mutex<()>> = std::sync::OnceLock::new();
    let _guard = LOCK.get_or_init(|| tauri::async_runtime::Mutex::new(())).lock().await;
    let old = saved();
    let now = SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_secs();
    let stamp = old["attemptedAt"].as_u64().or_else(|| old["fetchedAt"].as_u64()).or_else(|| old["fetchedAtUtc"].as_str().and_then(|stamp| chrono::DateTime::parse_from_rfc3339(stamp).ok()).map(|date| date.timestamp().max(0) as u64)).unwrap_or(0);
    if offline.unwrap_or(false) || now.saturating_sub(stamp) < 86400 { return Ok(old["games"].clone()); }
    let refreshed = fetch().await;
    let mut next = refreshed.unwrap_or(old);
    next["attemptedAt"] = json!(now);
    if let Ok(bytes) = serde_json::to_vec(&next) { let _ = std::fs::write(path(), bytes); }
    Ok(next["games"].clone())
}
async fn fetch() -> Result<Value, String> {
    let client = reqwest::Client::builder().timeout(Duration::from_secs(12)).redirect(reqwest::redirect::Policy::none()).build().map_err(|e| e.to_string())?;
    let mut cursor = String::new(); let mut games = Vec::new();
    for _ in 0..20 {
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
    #[test] fn native_variant_matches_nvidia_desktop_shortcut() {
        assert_eq!(super::route("5aa78bb8-6af4-49d9-b678-4b7c3685a98c", Some("xbox")).unwrap(), "#?cmsId=102396411&launchSource=External&shortName=a_plague_tale_requiem_xbox_app&parentGameId=5aa78bb8-6af4-49d9-b678-4b7c3685a98c");
        assert!(super::route("invalid", Some("xbox")).is_none());
    }
}

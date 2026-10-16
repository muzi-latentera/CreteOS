//! Epic/GOG ownership sync. Account tokens never enter the library cache or WebView IPC.
//! Protocol references: Legendary's Epic API and Heroic gogdl/Minigalaxy (see docs/STORE_ACCOUNTS.md).
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::collections::{HashMap, HashSet};
use std::sync::{atomic::{AtomicBool, AtomicU64, Ordering}, mpsc, Mutex};
use std::time::{Duration, SystemTime, UNIX_EPOCH};
use tauri::{Emitter, Manager};
use super::AppEntry;

// Public desktop OAuth application identifiers, used by the interoperable clients
// cited above. These are not user credentials. User tokens live only in keyring.
const EPIC_CLIENT: &str = "34a02cf8f4414e29b15921876da36f9a";
const EPIC_DESKTOP: &str = "daafbccc737745039dffe53d94fc76cf";
const GOG_CLIENT: &str = "46899977096215655";
const GOG_DESKTOP: &str = "9d85c43b1482497dbbce61f6e4aa173a433796eeae2ca8c5f6129f2dc4de46d9";
const GOG_REDIRECT: &str = "https://embed.gog.com/on_login_success?origin=client";
static EPIC_GENERATION: AtomicU64 = AtomicU64::new(0);
static GOG_GENERATION: AtomicU64 = AtomicU64::new(0);
static EPIC_LOCK: Mutex<()> = Mutex::new(());
static GOG_LOCK: Mutex<()> = Mutex::new(());
static EPIC_LOGIN: AtomicBool = AtomicBool::new(false);
static GOG_LOGIN: AtomicBool = AtomicBool::new(false);
static GOG_IDS_LOCK: Mutex<()> = Mutex::new(());

#[derive(Clone, Copy, PartialEq, Eq)]
enum Store { Epic, Gog }
impl Store {
    fn parse(s: &str) -> Result<Self, String> { match s { "epic" => Ok(Self::Epic), "gog" => Ok(Self::Gog), _ => Err("Unknown account source".into()) } }
    fn name(self) -> &'static str { if self == Self::Epic { "epic" } else { "gog" } }
    fn generation(self) -> &'static AtomicU64 { if self == Self::Epic { &EPIC_GENERATION } else { &GOG_GENERATION } }
    fn lock(self) -> &'static Mutex<()> { if self == Self::Epic { &EPIC_LOCK } else { &GOG_LOCK } }
    fn login(self) -> &'static AtomicBool { if self == Self::Epic { &EPIC_LOGIN } else { &GOG_LOGIN } }
    fn path(self) -> std::path::PathBuf { super::liftoff_dir().join(format!("{}_library.json", self.name())) }
    fn key(self) -> Result<keyring::Entry, String> { keyring::Entry::new(&format!("io.latent.creteos.windows.{}", self.name()), "refresh_token").map_err(|_| "Windows credential storage is unavailable".into()) }
}
fn now() -> u64 { SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_secs() }
fn client() -> Result<reqwest::blocking::Client, String> {
    reqwest::blocking::Client::builder().timeout(Duration::from_secs(25)).connect_timeout(Duration::from_secs(8))
        .user_agent("UELauncher/11.0.1-14907503+++Portal+Release-Live Windows/10.0.19041.1.256.64bit").build().map_err(|_| "Could not initialize account connection".into())
}
fn response(request: reqwest::blocking::RequestBuilder) -> Result<Value, String> {
    // Do not format reqwest errors: GOG puts authorization material in its URL.
    let r = request.send().map_err(|_| "Could not reach the store. Your saved library is still available.".to_string())?;
    if r.status().as_u16() == 401 || r.status().as_u16() == 403 { return Err("The store session expired. Please reconnect your account.".into()); }
    if !r.status().is_success() { return Err(format!("The store could not complete the request (HTTP {}). Your saved library was kept.", r.status().as_u16())); }
    r.json().map_err(|_| "The store returned an unreadable response. Your saved library was kept.".into())
}

#[derive(Clone, Serialize, Deserialize, Debug, PartialEq)]
struct OwnedGame {
    id: String,
    name: String,
    #[serde(default)] namespace: String,
    #[serde(default)] catalog_id: String,
    #[serde(default)] cover: Option<String>,
}
#[derive(Clone, Serialize, Deserialize, Default)]
struct Library {
    account_id: String,
    account_name: String,
    updated_at: u64,
    games: Vec<OwnedGame>,
}
#[derive(Serialize, Clone)]
pub struct Status {
    connected: bool,
    account_name: Option<String>,
    owned_count: usize,
    updated_at: u64,
}
fn load(store: Store) -> Option<Library> { serde_json::from_slice(&std::fs::read(store.path()).ok()?).ok() }
fn status(store: Store) -> Status {
    let library = load(store);
    // Status never needs to return or read the user's token.
    Status { connected: library.is_some(), account_name: library.as_ref().map(|l| l.account_name.clone()), owned_count: library.as_ref().map_or(0, |l| l.games.len()), updated_at: library.as_ref().map_or(0, |l| l.updated_at) }
}
fn persist(store: Store, library: &Library) -> Result<(), String> {
    let path = store.path(); let tmp = path.with_extension("json.tmp");
    std::fs::write(&tmp, serde_json::to_vec(library).map_err(|_| "Could not save the library".to_string())?).map_err(|_| "Could not save the library".to_string())?;
    std::fs::rename(tmp, path).map_err(|_| "Could not finish saving the library".into())
}

struct Session { access: String, refresh: String, account_id: String, account_name: String }
fn exchange(store: Store, grant: &str, value: &str) -> Result<Session, String> {
    let c = client()?;
    let data = if store == Store::Epic {
        let key = if grant == "refresh_token" { "refresh_token" } else { "code" };
        response(c.post("https://account-public-service-prod03.ol.epicgames.com/account/api/oauth/token")
            .basic_auth(EPIC_CLIENT, Some(EPIC_DESKTOP)).form(&[("grant_type", grant), (key, value), ("token_type", "eg1")]))?
    } else {
        let key = if grant == "refresh_token" { "refresh_token" } else { "code" };
        response(c.get("https://auth.gog.com/token").query(&[("client_id", GOG_CLIENT), ("client_secret", GOG_DESKTOP), ("grant_type", grant), ("redirect_uri", GOG_REDIRECT), (key, value)]))?
    };
    let access = data["access_token"].as_str().filter(|v| !v.is_empty()).ok_or("The store did not authorize access")?.to_string();
    let refresh = data["refresh_token"].as_str().filter(|v| !v.is_empty()).or_else(|| (grant == "refresh_token").then_some(value)).ok_or("The store did not authorize a renewable session")?.to_string();
    let mut account_id = data[if store == Store::Epic { "account_id" } else { "user_id" }].as_str().unwrap_or_default().to_string();
    if account_id.is_empty() { account_id = data["user_id"].as_u64().map(|v| v.to_string()).unwrap_or_default(); }
    let mut account_name = data["displayName"].as_str().unwrap_or_default().to_string();
    if store == Store::Gog {
        let user = response(c.get("https://embed.gog.com/userData.json").bearer_auth(&access))?;
        account_name = user["username"].as_str().unwrap_or("GOG account").to_string();
        if account_id.is_empty() { account_id = user["galaxyUserId"].as_str().unwrap_or_default().to_string(); }
    }
    if account_id.is_empty() { return Err("The store did not identify the connected account".into()); }
    if account_name.is_empty() { account_name = "Epic Games account".into(); }
    Ok(Session { access, refresh, account_id, account_name })
}
fn identifier(v: &Value) -> Option<String> { v.as_str().map(str::to_string).or_else(|| v.as_u64().map(|n| n.to_string())) }
fn safe_id(s: &str) -> bool { !s.is_empty() && s.len() <= 256 && s.chars().all(|c| c.is_ascii_alphanumeric() || "_-.:".contains(c)) }
fn cover_url(v: &Value) -> Option<String> {
    let s = v.as_str()?; let s = if s.starts_with("//") { format!("https:{s}") } else { s.to_string() };
    let u = tauri::Url::parse(&s).ok()?;
    let host = u.host_str()?;
    (u.scheme() == "https" && (host == "images.gog-statics.com" || host.ends_with(".gog.com") || host.ends_with(".gog-statics.com") || host.ends_with(".epicgames.com"))).then_some(s)
}
fn gog_products(page: &Value) -> Result<Vec<OwnedGame>, String> {
    let products = page["products"].as_array().ok_or("GOG returned an incomplete library page")?;
    Ok(products.iter().filter_map(|p| {
        if p["isDlc"].as_bool() == Some(true) || p["isGame"].as_bool() == Some(false) { return None; }
        let id = identifier(&p["id"])?; let name = p["title"].as_str()?.trim();
        if !safe_id(&id) || name.is_empty() { return None; }
        Some(OwnedGame { id, name: name.to_string(), namespace: String::new(), catalog_id: String::new(), cover: cover_url(&p["image"]) })
    }).collect())
}
fn epic_game(item: &Value, meta: &Value) -> Option<OwnedGame> {
    if item["namespace"] == "ue" || item["sandboxType"] == "PRIVATE" || meta["mainGameItem"]["id"].as_str().is_some() { return None; }
    if meta["categories"].as_array().is_some_and(|a| a.iter().any(|c| matches!(c["path"].as_str(), Some("mods" | "dlc" | "addons" | "applications")))) { return None; }
    let id = item["appName"].as_str()?;
    let namespace = item["namespace"].as_str()?;
    let catalog_id = item["catalogItemId"].as_str()?;
    let name = meta["title"].as_str()?.trim();
    if !safe_id(id) || !safe_id(namespace) || !safe_id(catalog_id) || name.is_empty() { return None; }
    let cover = meta["keyImages"].as_array().and_then(|a| a.iter().find(|i| matches!(i["type"].as_str(), Some("DieselGameBoxTall" | "OfferImageTall")))).and_then(|i| cover_url(&i["url"]));
    Some(OwnedGame { id: id.to_string(), namespace: namespace.to_string(), catalog_id: catalog_id.to_string(), name: name.to_string(), cover })
}
fn gog_pages(mut request: impl FnMut(u64) -> Result<Value, String>) -> Result<Vec<OwnedGame>, String> {
    let mut games = Vec::new();
    for page in 1..=1000 {
        let data = request(page)?;
        let total = data["totalPages"].as_u64().ok_or("GOG returned incomplete pagination")?;
        if total > 1000 { return Err("This GOG library needs more pages than this client supports. Your saved library was kept.".into()); }
        games.extend(gog_products(&data)?);
        if page >= total { return Ok(games); }
    }
    Err("GOG library pagination did not finish. Your saved library was kept.".into())
}
fn epic_pages(mut request: impl FnMut(&str) -> Result<Value, String>) -> Result<Vec<Value>, String> {
    let mut records = Vec::new(); let mut cursor = String::new(); let mut cursors = HashSet::new();
    for _ in 0..1000 {
        let data = request(&cursor)?;
        records.extend(data["records"].as_array().ok_or("Epic returned an incomplete library page")?.iter().cloned());
        if !data["responseMetadata"].is_object() { return Err("Epic returned incomplete pagination".into()); }
        let next = data["responseMetadata"]["nextCursor"].as_str().unwrap_or_default();
        if next.is_empty() { return Ok(records); }
        if !cursors.insert(next.to_string()) { return Err("Epic repeated a library page. Your saved library was kept.".into()); }
        cursor = next.to_string();
    }
    Err("Epic library pagination did not finish. Your saved library was kept.".into())
}
fn epic_records(assets: &Value, library: Vec<Value>) -> Result<Vec<Value>, String> {
    let mut records = assets.as_array().ok_or("Epic returned an incomplete asset library")?.clone();
    records.extend(library);
    let mut seen = HashSet::new();
    records.retain(|v| v["appName"].as_str().is_some_and(|id| id != "1" && seen.insert(id.to_ascii_lowercase())));
    Ok(records)
}
fn check_generation(store: Store, generation: u64) -> Result<(), String> {
    if store.generation().load(Ordering::SeqCst) != generation { Err("Account operation cancelled".into()) } else { Ok(()) }
}
fn epic_metadata(c: &reqwest::blocking::Client, access: &str, namespace: &str, items: &[Value], generation: u64) -> Result<Vec<OwnedGame>, String> {
    let mut games = Vec::new();
    for chunk in items.chunks(50) {
        check_generation(Store::Epic, generation)?;
        let ids = chunk.iter().filter_map(|v| v["catalogItemId"].as_str().map(|id| ("id", id))).collect::<Vec<_>>();
        let data = response(c.get(format!("https://catalog-public-service-prod06.ol.epicgames.com/catalog/api/shared/namespace/{namespace}/bulk/items")).bearer_auth(access)
            .query(&ids).query(&[("includeDLCDetails", "true"), ("includeMainGameDetails", "true"), ("locale", "en"), ("country", "GB")]))?;
        for item in chunk {
            let id = item["catalogItemId"].as_str().unwrap_or_default();
            let meta = data.get(id).filter(|m| m.is_object()).ok_or("Epic game metadata is temporarily unavailable. Your saved library was kept.")?;
            if let Some(game) = epic_game(item, meta) { games.push(game); }
        }
    }
    Ok(games)
}
fn fetch_library(store: Store, session: &Session, generation: u64) -> Result<Vec<OwnedGame>, String> {
    let c = client()?; let mut games = Vec::new();
    check_generation(store, generation)?;
    if store == Store::Gog {
        games = gog_pages(|page| {
            check_generation(store, generation)?;
            response(c.get("https://embed.gog.com/account/getFilteredProducts").bearer_auth(&session.access).query(&[("mediaType", "1".to_string()), ("page", page.to_string())]))
        })?;
    } else {
        // Assets contain ordinary owned Windows games; the paginated library adds
        // titles without downloadable assets. Neither feed alone is complete.
        let assets = response(c.get("https://launcher-public-service-prod06.ol.epicgames.com/launcher/api/public/assets/Windows").bearer_auth(&session.access).query(&[("label", "Live")]))?;
        let library = epic_pages(|cursor| {
            check_generation(store, generation)?;
            let mut request = c.get("https://library-service.live.use1a.on.epicgames.com/library/api/public/items").bearer_auth(&session.access).query(&[("includeMetadata", "true")]);
            if !cursor.is_empty() { request = request.query(&[("cursor", cursor)]); }
            response(request)
        })?;
        let records = epic_records(&assets, library)?;
        // Fetch catalog records in namespace batches. A metadata failure must not
        // silently replace a complete cached library with a partial import.
        let mut groups: HashMap<String, Vec<Value>> = HashMap::new();
        for item in records {
            if item["namespace"] == "ue" || item["sandboxType"] == "PRIVATE" { continue; }
            if let (Some(ns), Some(id), Some(app)) = (item["namespace"].as_str(), item["catalogItemId"].as_str(), item["appName"].as_str()) {
                if safe_id(ns) && safe_id(id) && safe_id(app) { groups.entry(ns.to_string()).or_default().push(item); }
            }
        }
        let groups = groups.into_iter().collect::<Vec<_>>();
        // A large claimed/free-game library can span hundreds of namespaces.
        // Bound concurrency without serializing one round trip per owned game.
        for batch in groups.chunks(8) {
            check_generation(store, generation)?;
            let client = &c;
            let results = std::thread::scope(|scope| {
                let tasks = batch.iter().map(|(namespace, items)| scope.spawn(move || epic_metadata(client, &session.access, namespace, items, generation))).collect::<Vec<_>>();
                tasks.into_iter().map(|task| task.join().map_err(|_| "Epic metadata request could not finish".to_string())?).collect::<Result<Vec<_>, String>>()
            })?;
            for result in results { games.extend(result); }
        }
    }
    let mut seen = HashSet::new(); games.retain(|g| seen.insert(g.id.to_ascii_lowercase()));
    games.sort_by_cached_key(|g| g.name.to_lowercase());
    Ok(games)
}

fn launch_path(store: Store, game: &OwnedGame) -> String {
    if store == Store::Epic { format!("com.epicgames.launcher://apps/{}?action=launch&silent=true", urlencoding::encode(&format!("{}:{}:{}", game.namespace, game.catalog_id, game.id))) }
    else { format!("goggalaxy://openGameView/{}", game.id) }
}
fn merge_games(apps: &mut Vec<AppEntry>, store: Store, games: Vec<OwnedGame>, ids: &mut HashMap<String, String>) {
    if store == Store::Gog {
        for app in apps.iter_mut().filter(|a| a.source == "gog") {
            if let Some(id) = &app.store_id {
                // Keep legacy installed identities, but also retain a new owned
                // entry's identity when that game is installed later.
                app.id = ids.entry(id.clone()).or_insert_with(|| app.id.clone()).clone();
            }
        }
    }
    for game in games {
        let found = apps.iter_mut().find(|a| a.source == store.name() && (a.store_id.as_deref() == Some(&game.id) || (store == Store::Epic && a.id.eq_ignore_ascii_case(&format!("epic:{}", game.id)))));
        if let Some(installed) = found {
            installed.store_id = Some(game.id); installed.store_cover_url = game.cover;
            // Keep the installed executable/URI and stable history/pin identity.
            continue;
        }
        let id = if store == Store::Gog { ids.entry(game.id.clone()).or_insert_with(|| format!("gog:{}", game.id)).clone() }
            else { format!("epic:{}", game.id.to_lowercase()) };
        apps.push(AppEntry { id, name: game.name.clone(), source: store.name().into(), app_type: "game".into(), installed: false,
            launch_path: launch_path(store, &game), store_id: Some(game.id), store_cover_url: game.cover, ..Default::default() });
    }
}
pub fn merge_cached(apps: &mut Vec<AppEntry>, source: &str) {
    if let Ok(store) = Store::parse(source) {
        let games = load(store).map(|library| library.games).unwrap_or_default();
        if store == Store::Gog {
            // This tiny token-free identity map has its own lock: library scans
            // must never wait for an account's network sync mutex.
            let Ok(_guard) = GOG_IDS_LOCK.lock() else { return; };
            let path = super::liftoff_dir().join("gog_library_ids.json");
            let mut ids: HashMap<String, String> = std::fs::read(&path).ok().and_then(|data| serde_json::from_slice(&data).ok()).unwrap_or_default();
            ids.retain(|key, value| safe_id(key) && value.starts_with("gog:") && value.len() <= 1024 && !value.chars().any(char::is_control));
            let before = ids.clone();
            merge_games(apps, store, games, &mut ids);
            if ids != before {
                if let Ok(data) = serde_json::to_vec(&ids) {
                    let tmp = path.with_extension("json.tmp");
                    if std::fs::write(&tmp, data).is_err() || std::fs::rename(tmp, path).is_err() { eprintln!("Could not save GOG library identities"); }
                }
            }
        } else { merge_games(apps, store, games, &mut HashMap::new()); }
    }
}
pub fn clear_credentials() {
    for store in [Store::Epic, Store::Gog] {
        if let Ok(key) = store.key() { let _ = key.delete_credential(); }
    }
}

fn login_url(store: Store, state: &str) -> String {
    if store == Store::Epic { format!("https://www.epicgames.com/id/login?redirectUrl={}", urlencoding::encode(&format!("https://www.epicgames.com/id/api/redirect?clientId={EPIC_CLIENT}&responseType=code"))) }
    else { format!("https://auth.gog.com/auth?client_id={GOG_CLIENT}&redirect_uri={}&response_type=code&layout=client2&state={state}", urlencoding::encode(GOG_REDIRECT)) }
}
fn gog_callback(url: &tauri::Url, state: &str) -> Option<Result<String, String>> {
    if url.scheme() != "https" || url.host_str() != Some("embed.gog.com") || url.path() != "/on_login_success" { return None; }
    let query: HashMap<_, _> = url.query_pairs().into_owned().collect();
    if query.get("state").is_some_and(|s| s != state) { return Some(Err("GOG sign-in could not be verified".into())); }
    query.get("code").filter(|s| !s.is_empty()).map(|s| Ok(s.clone()))
}
fn auth_log(store: Store, phase: &str) {
    use std::io::Write;
    static LOG_LOCK: Mutex<()> = Mutex::new(());
    let Ok(_guard) = LOG_LOCK.lock() else { return; };
    let dir = super::liftoff_dir().join("logs");
    if std::fs::create_dir_all(&dir).is_err() { return; }
    let path = dir.join("store-accounts.log");
    let oversized = std::fs::metadata(&path).is_ok_and(|m| m.len() > 128 * 1024);
    if let Ok(mut file) = std::fs::OpenOptions::new().create(true).write(true).append(!oversized).truncate(oversized).open(path) {
        let _ = writeln!(file, "{} {} {}", now(), store.name(), phase);
    }
}
fn window_error_message(error: &str) -> String {
    // Only extract a Windows error number. Never expose a URL, browser error
    // body, credential, authorization code or token from a runtime error.
    let code = error.find("HRESULT(0x").and_then(|start| error.get(start + 8..start + 18))
        .filter(|code| code[2..].chars().all(|c| c.is_ascii_hexdigit()));
    code.map(|code| format!("Could not open the store sign-in window (Windows {code}). Please try again."))
        .unwrap_or_else(|| "Could not open the store sign-in window. Please try again.".into())
}
fn authorize(app: &tauri::AppHandle, store: Store) -> Result<String, String> {
    let (send, receive) = mpsc::channel::<Result<String, String>>();
    let state = super::random_url_safe(24);
    let url = tauri::Url::parse(&login_url(store, &state)).map_err(|_| "Could not start store sign-in")?;
    let label = format!("{}-account-signin", store.name());
    let sender = send.clone(); let callback_state = state.clone(); let epic_state = state;
    auth_log(store, "authorization-start");
    let signin_data = super::liftoff_dir().join("store-signin").join(store.name());
    std::fs::create_dir_all(&signin_data).map_err(|_| "Could not prepare the private store sign-in browser")?;
    // authorize() runs on spawn_blocking. Do not move window construction onto
    // the UI event thread: WebView2 creation can deadlock there on Windows.
    // The main window has custom browser flags, so this browser also needs its
    // own data directory/environment. Incognito prevents persistent sign-in.
    let mut builder = tauri::WebviewWindowBuilder::new(app, &label, tauri::WebviewUrl::External(url))
        .title(if store == Store::Epic { "Connect Epic Games — CreteOS" } else { "Connect GOG — CreteOS" })
        .inner_size(960., 680.).maximized(true).visible(false).incognito(true)
        .data_directory(signin_data)
        .on_navigation(move |url| {
            if store == Store::Gog { if let Some(result) = gog_callback(url, &callback_state) { auth_log(store, "authorization-callback"); let _ = sender.send(result); return false; } }
            if store == Store::Epic && url.scheme() == "https" && url.host_str() == Some("creteos-auth.invalid") && url.path() == "/epic" {
                let q: HashMap<_, _> = url.query_pairs().into_owned().collect();
                if q.get("state") == Some(&callback_state) { if let Some(code) = q.get("code").filter(|s| !s.is_empty()) { auth_log(store, "authorization-callback"); let _ = sender.send(Ok(code.clone())); } }
                return false;
            }
            url.scheme() == "https" || url.as_str() == "about:blank"
        })
        .on_page_load(move |window, payload| {
            auth_log(store, "page-load");
            if store == Store::Epic && payload.url().host_str() == Some("www.epicgames.com") && payload.url().path() == "/id/api/redirect" {
                let state_json = serde_json::to_string(&epic_state).unwrap_or_default();
                let _ = window.eval(&format!("(()=>{{try{{const d=JSON.parse(document.body.innerText);if(d.authorizationCode)location.href='https://creteos-auth.invalid/epic?state='+encodeURIComponent({state_json})+'&code='+encodeURIComponent(d.authorizationCode)}}catch{{}}}})()"));
            }
        });
    if let Some(main) = app.get_webview_window("main") {
        // Owned windows remain above their owner, without a global topmost flag
        // or repeated focus grabs that would interfere with deliberate Alt-Tab.
        builder = builder.parent(&main).map_err(|_| "Could not attach the store sign-in window")?;
    }
    auth_log(store, "window-build-start");
    let window = builder.build().map_err(|error| {
        let message = window_error_message(&format!("{error:?}"));
        auth_log(store, &message); message
    })?;
    auth_log(store, "window-ready");
    window.on_window_event(move |event| {
        if matches!(event, tauri::WindowEvent::CloseRequested { .. } | tauri::WindowEvent::Destroyed) {
            auth_log(store, "window-closed"); let _ = send.send(Err("Sign-in cancelled".into()));
        }
    });
    if window.show().is_err() {
        let _ = window.close();
        return Err("Could not show the store sign-in window".into());
    }
    let _ = window.set_focus();
    let result = receive.recv_timeout(Duration::from_secs(600)).unwrap_or_else(|_| Err("Sign-in timed out. Please try again.".into()));
    auth_log(store, if result.is_ok() { "authorization-received" } else { "authorization-ended-without-code" });
    let _ = window.close();
    result
}
fn notify(app: &tauri::AppHandle) { let _ = app.emit("store-accounts-changed", ()); }

#[tauri::command]
pub fn store_account_status(source: String) -> Result<Status, String> { Ok(status(Store::parse(&source)?)) }

#[tauri::command]
pub fn store_open_library(app: tauri::AppHandle, source: String, store_id: Option<String>) -> Result<(), String> {
    use tauri_plugin_opener::OpenerExt;
    let store = Store::parse(&source)?;
    let uri = if store == Store::Epic { "com.epicgames.launcher://library".to_string() }
        else {
            let id = store_id.filter(|s| !s.is_empty() && s.chars().all(|c| c.is_ascii_digit())).ok_or("No GOG game ID is available")?;
            format!("goggalaxy://openGameView/{id}")
        };
    app.opener().open_url(uri, None::<&str>).map_err(|_| "Could not open the store client. Install Epic Games Launcher or GOG Galaxy first, then try again.".into())
}

#[tauri::command]
pub async fn store_account_connect(app: tauri::AppHandle, source: String) -> Result<Status, String> {
    let store = Store::parse(&source)?;
    if store.login().swap(true, Ordering::SeqCst) { return Err("This sign-in is already open".into()); }
    let generation = store.generation().fetch_add(1, Ordering::SeqCst) + 1;
    tauri::async_runtime::spawn_blocking(move || {
        let result: Result<Status, String> = (|| {
            let code = authorize(&app, store)?;
            let _guard = store.lock().lock().map_err(|_| "Account is busy")?;
            let session = exchange(store, "authorization_code", &code)?;
            let games = fetch_library(store, &session, generation)?;
            if store.generation().load(Ordering::SeqCst) != generation { return Err("Sign-in cancelled".into()); }
            store.key()?.set_password(&session.refresh).map_err(|_| "Could not securely save the store session")?;
            persist(store, &Library { account_id: session.account_id, account_name: session.account_name, updated_at: now(), games })?;
            notify(&app); Ok(status(store))
        })();
        store.login().store(false, Ordering::SeqCst); result
    }).await.map_err(|_| "Store sign-in could not finish".to_string())?
}

#[tauri::command]
pub async fn store_account_refresh(app: tauri::AppHandle, source: String, force: Option<bool>) -> Result<Status, String> {
    let store = Store::parse(&source)?; let generation = store.generation().load(Ordering::SeqCst);
    tauri::async_runtime::spawn_blocking(move || {
        let _guard = store.lock().lock().map_err(|_| "Account is busy")?;
        let old = load(store).ok_or("Connect this store first")?;
        if !force.unwrap_or(false) && now().saturating_sub(old.updated_at) < 86400 { return Ok(status(store)); }
        let refresh = store.key()?.get_password().map_err(|_| "Reconnect this account to refresh its library")?;
        let session = exchange(store, "refresh_token", &refresh)?;
        if session.account_id != old.account_id { return Err("The store returned a different account. Please reconnect.".into()); }
        // Save token rotation even if a later library page fails, keeping the old catalogue.
        if store.generation().load(Ordering::SeqCst) != generation { return Err("Account disconnected".into()); }
        store.key()?.set_password(&session.refresh).map_err(|_| "Could not securely renew the store session")?;
        let games = fetch_library(store, &session, generation)?;
        if store.generation().load(Ordering::SeqCst) != generation { return Err("Account disconnected".into()); }
        persist(store, &Library { account_id: session.account_id, account_name: session.account_name, updated_at: now(), games })?;
        notify(&app); Ok(status(store))
    }).await.map_err(|_| "Store refresh could not finish".to_string())?
}

#[tauri::command]
pub async fn store_account_disconnect(app: tauri::AppHandle, source: String) -> Result<(), String> {
    let store = Store::parse(&source)?; store.generation().fetch_add(1, Ordering::SeqCst);
    if let Some(window) = app.get_webview_window(&format!("{}-account-signin", store.name())) { let _ = window.close(); }
    tauri::async_runtime::spawn_blocking(move || {
        let _guard = store.lock().lock().map_err(|_| "Account is busy")?;
        match store.key()?.delete_credential() { Ok(()) | Err(keyring::Error::NoEntry) => {}, Err(_) => return Err("Could not remove the stored session".into()) }
        if store.path().exists() { std::fs::remove_file(store.path()).map_err(|_| "Could not clear this account's saved library")?; }
        notify(&app); Ok(())
    }).await.map_err(|_| "Account disconnect could not finish".to_string())?
}

#[tauri::command]
pub fn store_account_cancel(app: tauri::AppHandle, source: String) -> Result<(), String> {
    let store = Store::parse(&source)?; store.generation().fetch_add(1, Ordering::SeqCst);
    if let Some(window) = app.get_webview_window(&format!("{}-account-signin", store.name())) { let _ = window.close(); }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn gog_pagination_reads_every_page_and_propagates_failures() {
        let mut pages = Vec::new();
        let games = gog_pages(|page| { pages.push(page); Ok(json!({"totalPages":3,"products":[{"id":page,"title":format!("Game {page}")}]})) }).unwrap();
        assert_eq!(pages, vec![1,2,3]); assert_eq!(games.len(),3);
        assert!(gog_pages(|page| if page == 2 { Err("network failed".into()) } else { Ok(json!({"totalPages":3,"products":[]})) }).is_err());
        assert!(gog_pages(|_| Ok(json!({"products":[]}))).is_err());
    }
    #[test] fn epic_pagination_unions_assets_and_library_without_duplicates() {
        let mut cursors = Vec::new();
        let library = epic_pages(|cursor| { cursors.push(cursor.to_string()); Ok(if cursor.is_empty() { json!({"records":[{"appName":"asset"}],"responseMetadata":{"nextCursor":"page2"}}) } else { json!({"records":[{"appName":"extra"}],"responseMetadata":{}}) }) }).unwrap();
        assert_eq!(cursors,vec!["","page2"]);
        let records=epic_records(&json!([{"appName":"asset"}]), library).unwrap();
        assert_eq!(records.len(),2); assert_eq!(records[1]["appName"],"extra");
        assert!(epic_pages(|_| Ok(json!({"records":[],"responseMetadata":{"nextCursor":"loop"}}))).is_err());
        assert!(epic_pages(|cursor| if cursor.is_empty() { Ok(json!({"records":[],"responseMetadata":{"nextCursor":"next"}})) } else { Err("network failed".into()) }).is_err());
    }
    #[test] fn gog_imports_uninstalled_games_and_filters_addons() {
        let games = gog_products(&json!({"products":[{"id":123,"title":"Owned game","image":"//images.gog-statics.com/cover.jpg"},{"id":124,"title":"DLC","isDlc":true}]})).unwrap();
        let mut apps = vec![]; merge_games(&mut apps, Store::Gog, games, &mut HashMap::new());
        assert_eq!(apps.len(),1); assert!(!apps[0].installed); assert_eq!(apps[0].store_id.as_deref(),Some("123"));
        assert_eq!(apps[0].launch_path,"goggalaxy://openGameView/123");
    }
    #[test] fn installed_gog_keeps_its_identity_and_executable_without_cross_store_merges() {
        let game = OwnedGame{id:"123".into(),name:"Same game".into(),namespace:String::new(),catalog_id:String::new(),cover:None};
        let mut apps=vec![AppEntry{id:"legacy-gog-id".into(),source:"gog".into(),store_id:Some("123".into()),name:game.name.clone(),launch_path:"C:\\Games\\game.exe".into(),installed:true,..Default::default()},AppEntry{id:"steam/1".into(),source:"steam".into(),name:game.name.clone(),..Default::default()}];
        merge_games(&mut apps,Store::Gog,vec![game],&mut HashMap::new());assert_eq!(apps.len(),2);assert_eq!(apps[0].id,"legacy-gog-id");assert!(apps[0].installed);assert_eq!(apps[0].launch_path,"C:\\Games\\game.exe");
    }
    #[test] fn gog_identity_survives_install_uninstall_and_account_disconnect() {
        let game=OwnedGame{id:"123".into(),name:"Same game".into(),namespace:String::new(),catalog_id:String::new(),cover:None};
        let mut ids=HashMap::new(); let mut apps=vec![];
        merge_games(&mut apps,Store::Gog,vec![game.clone()],&mut ids);
        let pinned_id=apps[0].id.clone();
        let mut installed=vec![AppEntry{id:"gog:same_game".into(),source:"gog".into(),store_id:Some("123".into()),installed:true,launch_path:"C:\\Games\\game.exe".into(),..Default::default()}];
        merge_games(&mut installed,Store::Gog,vec![game.clone()],&mut ids);
        assert_eq!(installed.len(),1); assert_eq!(installed[0].id,pinned_id); assert!(installed[0].installed);
        merge_games(&mut installed,Store::Gog,vec![],&mut ids);
        assert_eq!(installed[0].id,pinned_id);
        let mut uninstalled=vec![]; merge_games(&mut uninstalled,Store::Gog,vec![game.clone()],&mut ids);
        assert_eq!(uninstalled[0].id,pinned_id); assert!(!uninstalled[0].installed);
        let mut legacy_ids=HashMap::new();
        installed[0].id="gog:legacy_name".into();
        merge_games(&mut installed,Store::Gog,vec![game.clone()],&mut legacy_ids);
        let mut uninstalled=vec![]; merge_games(&mut uninstalled,Store::Gog,vec![game],&mut legacy_ids);
        assert_eq!(uninstalled[0].id,"gog:legacy_name");
    }
    #[test] fn epic_ids_and_store_edition_survive_import() {
        let item=json!({"appName":"app-a","namespace":"sandbox","catalogItemId":"catalog-a","sandboxType":"PUBLIC"});
        let game=epic_game(&item,&json!({"title":"Owned Epic game","categories":[{"path":"games"}]})).unwrap();
        let mut apps=vec![];merge_games(&mut apps,Store::Epic,vec![game],&mut HashMap::new());assert_eq!(apps[0].id,"epic:app-a");assert!(!apps[0].installed);assert_eq!(apps[0].source,"epic");assert!(apps[0].launch_path.contains("sandbox%3Acatalog-a%3Aapp-a"));
        assert!(epic_game(&item,&json!({"title":"DLC","mainGameItem":{"id":"parent"}})).is_none());
    }
    #[test] fn callback_rejects_other_sites_and_mismatched_state() {
        assert!(gog_callback(&tauri::Url::parse("https://evil.test/on_login_success?code=bad").unwrap(),"expected").is_none());
        assert!(gog_callback(&tauri::Url::parse("https://embed.gog.com/on_login_success?code=test&state=wrong").unwrap(),"expected").unwrap().is_err());
        assert_eq!(gog_callback(&tauri::Url::parse("https://embed.gog.com/on_login_success?origin=client&code=test&state=expected").unwrap(),"expected").unwrap().unwrap(),"test");
    }
    #[test] fn window_errors_only_expose_the_windows_error_number() {
        let message=window_error_message("WindowsError HRESULT(0x8007139F) https://example.test/?code=private");
        assert!(message.contains("0x8007139F")); assert!(!message.contains("https")); assert!(!message.contains("private"));
        assert!(!window_error_message("https://example.test/?access_token=private").contains("private"));
        assert!(!window_error_message("https://example.test/?code=0x01234567").contains("0x01234567"));
    }
    #[test] fn owned_cache_contains_no_tokens_and_rejects_unknown_sources() {
        let v=serde_json::to_string(&Library::default()).unwrap();assert!(!v.contains("token"));assert!(Store::parse("steam").is_err());
        assert!(cover_url(&json!("file:///C:/private")).is_none());assert!(cover_url(&json!("https://untrusted.test/cover")).is_none());
    }
}

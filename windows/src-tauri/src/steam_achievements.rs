//! Steam achievements from Steam's own local cache, with no API key and no
//! network. Steam keeps, per game, `appcache\stats\UserGameStatsSchema_<app>.bin`
//! (names, descriptions, icons) and `UserGameStats_<account>_<app>.bin` (a
//! bitmask per stat plus unlock times), both in binary KeyValues.
use serde::Serialize;
use serde_json::{Map, Value};
use std::path::{Path, PathBuf};

#[derive(Serialize, Clone, Debug, PartialEq)]
pub struct Achievement {
    id: String,
    name: String,
    description: String,
    unlocked: bool,
    unlocked_at: Option<i64>,
    icon: Option<String>,
}

#[derive(Serialize, Debug, Default)]
pub struct SteamProgress {
    earned: Option<u32>,
    total: Option<u32>,
    achievements: Vec<Achievement>,
}

/// Binary KeyValues: type byte, NUL-terminated name, value; 8 ends an object.
fn parse(data: &[u8], at: &mut usize) -> Option<Map<String, Value>> {
    let mut out = Map::new();
    let text = |data: &[u8], at: &mut usize| -> Option<String> {
        let end = data.get(*at..)?.iter().position(|b| *b == 0)? + *at;
        let value = String::from_utf8_lossy(&data[*at..end]).into_owned();
        *at = end + 1;
        Some(value)
    };
    let take = |data: &[u8], at: &mut usize, n: usize| -> Option<Vec<u8>> {
        let bytes = data.get(*at..*at + n)?.to_vec();
        *at += n;
        Some(bytes)
    };
    while *at < data.len() {
        let kind = data[*at];
        *at += 1;
        if kind == 8 { return Some(out); }
        let name = text(data, at)?;
        let value = match kind {
            0 => Value::Object(parse(data, at)?),
            1 => Value::String(text(data, at)?),
            2 | 4 | 6 => Value::from(i32::from_le_bytes(take(data, at, 4)?.try_into().ok()?)),
            3 => Value::from(f32::from_le_bytes(take(data, at, 4)?.try_into().ok()?) as f64),
            7 | 10 => Value::from(u64::from_le_bytes(take(data, at, 8)?.try_into().ok()?)),
            _ => return None,
        };
        out.insert(name, value);
    }
    Some(out)
}

fn read_kv(path: &Path) -> Option<Map<String, Value>> {
    let data = std::fs::read(path).ok()?;
    parse(&data, &mut 0)
}

/// English text from a localised `{ "english": … }` object, or a plain string.
fn english(value: &Value) -> Option<String> {
    value.as_str().map(str::to_owned).or_else(|| value["english"].as_str().map(str::to_owned))
}

fn combine(app_id: &str, schema: &Map<String, Value>, user: Option<&Map<String, Value>>) -> SteamProgress {
    let stats = schema.get(app_id).and_then(|game| game["stats"].as_object());
    let cache = user.and_then(|user| user.get("cache")).and_then(Value::as_object);
    let mut achievements = Vec::new();
    for (stat_id, stat) in stats.into_iter().flatten() {
        let Some(bits) = stat["bits"].as_object() else { continue; };
        let state = cache.and_then(|cache| cache.get(stat_id));
        let mask = state.and_then(|state| state["data"].as_i64()).unwrap_or(0) as u64;
        for (bit, item) in bits {
            let Ok(bit) = bit.parse::<u32>() else { continue; };
            let unlocked = bit < 64 && mask >> bit & 1 == 1;
            let hidden = item["hidden"].as_i64().unwrap_or(0) != 0 || item["hidden"].as_str() == Some("1");
            let id = item["name"].as_str().unwrap_or_default().to_owned();
            let name = english(&item["display"]["name"]).unwrap_or_else(|| id.clone());
            let description = english(&item["display"]["desc"]).unwrap_or_default();
            let icon = item[if unlocked { "icon" } else { "icon_gray" }].as_str().filter(|icon| !icon.is_empty())
                .map(|icon| format!("https://cdn.cloudflare.steamstatic.com/steamcommunity/public/images/apps/{app_id}/{icon}"));
            achievements.push(Achievement {
                id,
                name: if hidden && !unlocked { "Hidden achievement".into() } else { name },
                description: if hidden && !unlocked { String::new() } else { description },
                unlocked,
                unlocked_at: if unlocked { state.and_then(|state| state["AchievementTimes"][bit.to_string()].as_i64()).filter(|at| *at > 0) } else { None },
                icon,
            });
        }
    }
    let total = achievements.len() as u32;
    SteamProgress {
        earned: (total > 0).then(|| achievements.iter().filter(|a| a.unlocked).count() as u32),
        total: (total > 0).then_some(total),
        achievements,
    }
}

fn stats_dir() -> Option<PathBuf> {
    use windows::Win32::System::Registry::HKEY_CURRENT_USER;
    let steam = super::registry_string_value(HKEY_CURRENT_USER, "Software\\Valve\\Steam", Some("SteamPath"))?;
    Some(PathBuf::from(steam.trim_end_matches('\0')).join("appcache").join("stats"))
}

/// The newest stats file for this game, whichever Steam account played it.
fn user_file(dir: &Path, app_id: &str) -> Option<PathBuf> {
    let suffix = format!("_{app_id}.bin");
    std::fs::read_dir(dir).ok()?.flatten()
        .filter(|entry| entry.file_name().to_str().is_some_and(|name| name.starts_with("UserGameStats_") && !name.starts_with("UserGameStatsSchema_") && name.ends_with(&suffix)))
        .max_by_key(|entry| entry.metadata().and_then(|meta| meta.modified()).ok())
        .map(|entry| entry.path())
}

#[tauri::command]
pub async fn steam_achievements(app_id: String) -> Option<SteamProgress> {
    if app_id.is_empty() || !app_id.chars().all(|c| c.is_ascii_digit()) { return None; }
    tauri::async_runtime::spawn_blocking(move || {
        let dir = stats_dir()?;
        let schema = read_kv(&dir.join(format!("UserGameStatsSchema_{app_id}.bin")))?;
        let user = user_file(&dir, &app_id).and_then(|path| read_kv(&path));
        let progress = combine(&app_id, &schema, user.as_ref());
        progress.total.map(|_| progress)
    }).await.ok().flatten()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn kv_string(out: &mut Vec<u8>, name: &str, value: &str) { out.push(1); out.extend(name.as_bytes()); out.push(0); out.extend(value.as_bytes()); out.push(0); }
    fn kv_int(out: &mut Vec<u8>, name: &str, value: i32) { out.push(2); out.extend(name.as_bytes()); out.push(0); out.extend(value.to_le_bytes()); }
    fn kv_open(out: &mut Vec<u8>, name: &str) { out.push(0); out.extend(name.as_bytes()); out.push(0); }

    #[test]
    fn reads_steam_binary_keyvalues() {
        let mut data = Vec::new();
        kv_open(&mut data, "cache");
        kv_int(&mut data, "crc", -5);
        kv_open(&mut data, "1");
        kv_int(&mut data, "data", 0b101);
        kv_open(&mut data, "AchievementTimes");
        kv_int(&mut data, "0", 1646841301);
        kv_int(&mut data, "2", 1647466519);
        data.extend([8, 8, 8, 8]);
        let parsed = parse(&data, &mut 0).unwrap();
        assert_eq!(parsed["cache"]["1"]["data"], 5);
        assert_eq!(parsed["cache"]["1"]["AchievementTimes"]["2"], 1647466519);
        let mut text = Vec::new();
        kv_string(&mut text, "gamename", "ELDEN RING");
        text.push(8);
        assert_eq!(parse(&text, &mut 0).unwrap()["gamename"], "ELDEN RING");
        assert!(parse(&[9, b'x', 0], &mut 0).is_none());
    }

    #[test]
    fn combines_schema_and_unlocks_with_hidden_ones_kept_secret() {
        let schema: Map<String, Value> = serde_json::from_value(serde_json::json!({"1245620": {"stats": {"1": {"bits": {
            "0": {"name": "ACH00", "display": {"name": {"english": "Elden Ring"}, "desc": {"english": "Obtained all achievements"}}, "hidden": 0, "icon": "a.jpg", "icon_gray": "b.jpg"},
            "1": {"name": "ACH01", "display": {"name": {"english": "Elden Lord"}, "desc": {"english": "Ending"}}, "hidden": 1, "icon": "c.jpg", "icon_gray": "d.jpg"},
            "2": {"name": "ACH02", "display": {"name": {"english": "Age of Stars"}, "desc": {"english": "Other ending"}}, "hidden": 1}
        }}}}})).unwrap();
        let user: Map<String, Value> = serde_json::from_value(serde_json::json!({"cache": {"1": {"data": 0b100, "AchievementTimes": {"2": 1646841301}}}})).unwrap();
        let progress = combine("1245620", &schema, Some(&user));
        assert_eq!((progress.earned, progress.total), (Some(1), Some(3)));
        let by = |id: &str| progress.achievements.iter().find(|a| a.id == id).unwrap().clone();
        assert!(!by("ACH00").unlocked);
        assert!(by("ACH00").icon.as_deref().unwrap().ends_with("/1245620/b.jpg"));
        assert_eq!(by("ACH01").name, "Hidden achievement");
        assert_eq!(by("ACH01").description, "");
        assert_eq!((by("ACH02").name.as_str(), by("ACH02").unlocked_at), ("Age of Stars", Some(1646841301)));
        // A game never played on this account still lists its achievements, all locked.
        let fresh = combine("1245620", &schema, None);
        assert_eq!((fresh.earned, fresh.total), (Some(0), Some(3)));
    }
}

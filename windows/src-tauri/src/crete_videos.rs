use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

const CHANNELS: [&str; 6] = ["Whitelight", "Skill Up", "Joseph Anderson", "Noah Caldwell-Gervais", "gameranx", "Jake Baldino"];
#[derive(Clone, Serialize, Deserialize)]
pub struct ReviewVideo { pub id: String, pub title: String, pub channel: String, pub thumbnail: String, pub url: String }
#[derive(Serialize, Deserialize)]
struct Cache { at: u64, videos: Vec<ReviewVideo> }
fn now() -> u64 { SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_secs() }
fn normalized(text: &str) -> String { text.chars().filter(|c| c.is_alphanumeric()).flat_map(char::to_lowercase).collect() }
fn words(text: &str) -> Vec<String> { text.split(|c: char| !c.is_alphanumeric()).map(str::to_lowercase).filter(|w| !w.is_empty() && !matches!(w.as_str(), "a" | "the" | "of" | "and" | "pc" | "edition")).collect() }
fn label(value: &Value) -> String {
    value["simpleText"].as_str().map(str::to_owned).unwrap_or_else(|| value["runs"].as_array().map(|runs| runs.iter().filter_map(|r| r["text"].as_str()).collect::<String>()).unwrap_or_default())
}
fn collect(value: &Value, title: &str, out: &mut Vec<ReviewVideo>) {
    if let Some(video) = value.get("videoRenderer") {
        let id = video["videoId"].as_str().unwrap_or_default();
        let name = label(&video["title"]);
        let channel = label(&video["ownerText"]);
        let title_words = words(title); let video_words = words(&name);
        if id.len() == 11 && id.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'_' || b == b'-')
            && !title_words.is_empty() && title_words.iter().all(|w| video_words.contains(w)) {
            out.push(ReviewVideo { id: id.into(), title: name, channel, thumbnail: format!("https://i.ytimg.com/vi/{id}/hqdefault.jpg"), url: format!("https://www.youtube.com/watch?v={id}") });
        }
    }
    match value { Value::Object(map) => for child in map.values() { collect(child, title, out); }, Value::Array(items) => for child in items { collect(child, title, out); }, _ => {} }
}
fn parse(html: &str, title: &str) -> Vec<ReviewVideo> {
    let Some(start) = html.find("var ytInitialData = ").map(|i| i + "var ytInitialData = ".len()) else { return vec![]; };
    let mut stream = serde_json::Deserializer::from_str(&html[start..]).into_iter::<Value>();
    let mut out = vec![];
    if let Some(Ok(value)) = stream.next() { collect(&value, title, &mut out); }
    out
}
#[tauri::command]
pub async fn get_crete_review_videos(game_name: String) -> Result<Vec<ReviewVideo>, String> {
    if game_name.is_empty() || game_name.len() > 240 { return Err("Invalid game title".into()); }
    let dir = crate::liftoff_dir().join("review_videos");
    let key = normalized(&game_name);
    let path = dir.join(format!("{key}.json"));
    let cached = std::fs::read(&path).ok().and_then(|b| serde_json::from_slice::<Cache>(&b).ok());
    if let Some(ref cache) = cached { if now().saturating_sub(cache.at) < if cache.videos.is_empty() { 3600 } else { 86400 * 7 } { return Ok(cache.videos.clone()); } }
    let client = reqwest::Client::builder().timeout(Duration::from_secs(12)).user_agent("Mozilla/5.0 CreteOS/0.1").build().map_err(|e| e.to_string())?;
    let mut tasks = vec![];
    for channel in CHANNELS.into_iter().chain([""]) {
        let client = client.clone(); let title = game_name.clone();
        tasks.push(tauri::async_runtime::spawn(async move {
            let query = format!("{title} {channel} review");
            let mut response = client.get("https://www.youtube.com/results").query(&[("search_query", query.as_str())]).send().await.ok()?.error_for_status().ok()?;
            let mut bytes = vec![];
            while let Some(chunk) = response.chunk().await.ok()? { if bytes.len() + chunk.len() > 3_000_000 { return None; } bytes.extend_from_slice(&chunk); }
            let html = String::from_utf8(bytes).ok()?;
            let found = parse(&html, &title).into_iter().filter(|v| channel.is_empty() || normalized(&v.channel) == normalized(channel)).take(if channel.is_empty() { 4 } else { 2 }).collect::<Vec<_>>();
            Some(found)
        }));
    }
    let mut videos = vec![]; let mut successes = 0;
    for task in tasks { if let Ok(Some(found)) = task.await { successes += 1; for video in found { if !videos.iter().any(|v: &ReviewVideo| v.id == video.id) { videos.push(video); } } } }
    videos.sort_by_key(|v| CHANNELS.iter().position(|c| normalized(c) == normalized(&v.channel)).unwrap_or(CHANNELS.len()));
    videos.truncate(12);
    if successes == 0 { return Ok(cached.map(|c| c.videos).unwrap_or_default()); }
    let _ = std::fs::create_dir_all(dir);
    if let Ok(bytes) = serde_json::to_vec(&Cache { at: now(), videos: videos.clone() }) { let _ = std::fs::write(path, bytes); }
    Ok(videos)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn selects_exact_game_words_and_valid_video_ids() {
        let html = r#"var ytInitialData = {"contents":[{"videoRenderer":{"videoId":"abcdefghijk","title":{"runs":[{"text":"A Plague Tale: Requiem Review"}]},"ownerText":{"runs":[{"text":"Skill Up"}]}}},{"videoRenderer":{"videoId":"lmnopqrstuv","title":{"simpleText":"A Plague Tale: Innocence Review"}}}]}; trailing script"#;
        let found = parse(html, "A Plague Tale: Requiem");
        assert_eq!(found.len(), 1); assert_eq!(found[0].channel, "Skill Up");
        assert!(parse(html, "Resident Evil 4").is_empty());
    }
}

//! Fixed public feed used by the Android CreteOS Home rail. No launcher/store changes.
#[tauri::command]
pub async fn get_crete_news_rss() -> Result<String, String> {
    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(12))
        .user_agent("CreteOS/0.1 (Windows; gaming launcher)")
        .build().map_err(|e| e.to_string())?;
    let mut response = client
        .get("https://www.reddit.com/r/Games%2Bpcgaming/top/.rss?t=day&limit=100")
        .send().await.map_err(|e| e.to_string())?
        .error_for_status().map_err(|e| e.to_string())?;
    let mut body = Vec::new();
    while let Some(chunk) = response.chunk().await.map_err(|e| e.to_string())? {
        if body.len() + chunk.len() > 1_048_576 { return Err("News feed exceeds size limit".into()); }
        body.extend_from_slice(&chunk);
    }
    String::from_utf8(body).map_err(|e| e.to_string())
}

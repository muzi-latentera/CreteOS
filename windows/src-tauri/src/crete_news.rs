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

fn article_host(host: &str) -> bool {
    ["pcgamer.com", "ign.com", "gamespot.com", "eurogamer.net", "vg247.com", "polygon.com", "rockpapershotgun.com", "videogameschronicle.com", "gematsu.com", "gamingbolt.com", "nintendolife.com", "pushsquare.com", "store.steampowered.com", "blog.playstation.com", "xbox.com"].iter().any(|allowed| host == *allowed || host.ends_with(&format!(".{allowed}")))
}
#[tauri::command]
pub async fn get_crete_article_thumbnail(article_url: String) -> Result<Option<String>, String> {
    let url = reqwest::Url::parse(&article_url).map_err(|_| "Invalid news link")?;
    if url.scheme() != "https" || !url.username().is_empty() || url.port().is_some() || !url.host_str().is_some_and(article_host) { return Ok(None); }
    // No arbitrary-site fetches or redirects to private/local destinations.
    let client = reqwest::Client::builder().redirect(reqwest::redirect::Policy::none()).timeout(std::time::Duration::from_secs(5)).build().map_err(|e| e.to_string())?;
    let mut response = client.get(url).send().await.map_err(|e| e.to_string())?.error_for_status().map_err(|e| e.to_string())?;
    let mut bytes = Vec::new();
    while let Some(chunk) = response.chunk().await.map_err(|e| e.to_string())? { if bytes.len() + chunk.len() > 2_000_000 { return Ok(None); } bytes.extend_from_slice(&chunk); }
    let html = String::from_utf8_lossy(&bytes);
    for tag in html.split('<').filter_map(|part| part.split('>').next()).filter(|tag| tag.starts_with("meta ")) {
        if !tag.contains("og:image\"") && !tag.contains("og:image'") { continue; }
        let Some(rest) = tag.split("content=").nth(1) else { continue; };
        let Some(quote) = rest.chars().next().filter(|c| *c == '\'' || *c == '"') else { continue; };
        let Some(value) = rest[1..].split(quote).next() else { continue; };
        let Ok(image) = reqwest::Url::parse(&value.replace("&amp;", "&")) else { continue; };
        let host = image.host_str().unwrap_or_default();
        let cdn = ["futurecdn.net", "ignimgs.com", "gamespot.com", "vox-cdn.com", "gamer-network.net", "vgc.news", "gematsu.com", "gamingbolt.com", "pushsquare.com", "nintendolife.com", "steamstatic.com", "playstation.com", "xbox.com", "akamaized.net"];
        if image.scheme() == "https" && image.port().is_none() && (article_host(host) || cdn.iter().any(|h| host == *h || host.ends_with(&format!(".{h}")))) { return Ok(Some(image.to_string())); }
    }
    Ok(None)
}

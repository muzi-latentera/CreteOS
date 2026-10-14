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
    ["pcgamer.com", "ign.com", "gamespot.com", "eurogamer.net", "vg247.com", "polygon.com", "rockpapershotgun.com", "videogameschronicle.com", "gematsu.com", "gamingbolt.com", "nintendolife.com", "pushsquare.com", "store.steampowered.com", "blog.playstation.com", "xbox.com", "gamesindustry.biz", "gamesbeat.com", "opencritic.com", "aftermath.site", "digitalfoundry.net", "techpowerup.com", "bsky.app", "gamesradar.com", "gamedeveloper.com"].iter().any(|allowed| host == *allowed || host.ends_with(&format!(".{allowed}")))
}
fn attribute<'a>(tag: &'a str, key: &str) -> Option<&'a str> {
    let bytes = tag.as_bytes(); let mut i = 4;
    while i < bytes.len() {
        while i < bytes.len() && (bytes[i].is_ascii_whitespace() || bytes[i] == b'/') { i += 1; }
        let start = i;
        while i < bytes.len() && !bytes[i].is_ascii_whitespace() && bytes[i] != b'=' { i += 1; }
        let name = tag.get(start..i)?;
        while i < bytes.len() && bytes[i].is_ascii_whitespace() { i += 1; }
        if bytes.get(i) != Some(&b'=') { continue; }
        i += 1; while i < bytes.len() && bytes[i].is_ascii_whitespace() { i += 1; }
        let quote = bytes.get(i).copied().filter(|c| *c == b'\'' || *c == b'"');
        if quote.is_some() { i += 1; }
        let value_start = i;
        while i < bytes.len() && quote.map_or(!bytes[i].is_ascii_whitespace(), |quote| bytes[i] != quote) { i += 1; }
        let value = tag.get(value_start..i)?;
        if quote.is_some() && i < bytes.len() { i += 1; }
        if name.eq_ignore_ascii_case(key) { return Some(value); }
    }
    None
}
fn thumbnail(html: &str) -> Option<String> {
    for tag in html.split('<').filter_map(|part| part.split('>').next()).filter(|tag| tag.get(..4).is_some_and(|text| text.eq_ignore_ascii_case("meta")) && tag.as_bytes().get(4).is_some_and(u8::is_ascii_whitespace)) {
        let name = attribute(tag, "property").or_else(|| attribute(tag, "name")).unwrap_or_default();
        if !["og:image", "twitter:image", "twitter:image:src"].iter().any(|key| name.eq_ignore_ascii_case(key)) { continue; }
        let Some(value) = attribute(tag, "content") else { continue; };
        let value = value.replace("&amp;", "&").replace("&#38;", "&");
        let Ok(image) = reqwest::Url::parse(&value) else { continue; };
        let host = image.host_str().unwrap_or_default();
        let cdn = ["futurecdn.net", "ignimgs.com", "gamespot.com", "vox-cdn.com", "gamer-network.net", "gnwcdn.com", "vgc.news", "gematsu.com", "gamingbolt.com", "pushsquare.com", "nintendolife.com", "steamstatic.com", "playstation.com", "xbox.com", "akamaized.net", "xboxwire.thesourcemediaassets.com"];
        if image.scheme() == "https" && image.username().is_empty() && image.password().is_none() && image.port().is_none() && (article_host(host) || cdn.iter().any(|h| host == *h || host.ends_with(&format!(".{h}")))) { return Some(image.to_string()); }
    }
    None
}
#[tauri::command]
pub async fn get_crete_article_thumbnail(article_url: String) -> Result<Option<String>, String> {
    let url = reqwest::Url::parse(&article_url).map_err(|_| "Invalid news link")?;
    if url.scheme() != "https" || !url.username().is_empty() || url.password().is_some() || url.port().is_some() || !url.host_str().is_some_and(article_host) { return Ok(None); }
    // Follow bounded canonical publisher redirects, retaining the fixed public
    // domain allowlist at every hop.
    let redirects = reqwest::redirect::Policy::custom(|attempt| {
        let url = attempt.url();
        if attempt.previous().len() < 3 && url.scheme() == "https" && url.username().is_empty() && url.password().is_none() && url.port().is_none() && url.host_str().is_some_and(article_host) { attempt.follow() } else { attempt.stop() }
    });
    let client = reqwest::Client::builder().redirect(redirects).user_agent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/143.0.0.0 Safari/537.36").timeout(std::time::Duration::from_secs(8)).build().map_err(|e| e.to_string())?;
    let mut response = client.get(url).send().await.map_err(|e| e.to_string())?.error_for_status().map_err(|e| e.to_string())?;
    let mut bytes = Vec::new();
    while let Some(chunk) = response.chunk().await.map_err(|e| e.to_string())? { if bytes.len() + chunk.len() > 2_000_000 { return Ok(None); } bytes.extend_from_slice(&chunk); }
    let html = String::from_utf8_lossy(&bytes);
    let Some(image_url) = thumbnail(&html) else { return Ok(None); };
    cache_news_image(&image_url).await.map(Some)

}

// Publisher images are fetched once through the native client and served by
// the existing scoped asset protocol, avoiding WebView hotlink/referrer failures.
fn image_extension(bytes: &[u8]) -> Option<&'static str> {
    if bytes.starts_with(b"\x89PNG\r\n\x1a\n") { Some("png") }
    else if bytes.starts_with(&[0xff, 0xd8, 0xff]) { Some("jpg") }
    else if bytes.starts_with(b"RIFF") && bytes.get(8..12) == Some(b"WEBP") { Some("webp") }
    else { None }
}
async fn cache_news_image(image_url: &str) -> Result<String, String> {
    use sha2::{Digest, Sha256};
    let folder = crate::liftoff_dir().join("media").join("news");
    std::fs::create_dir_all(&folder).map_err(|e| e.to_string())?;
    let key = format!("{:x}", Sha256::digest(image_url.as_bytes()));
    for ext in ["jpg", "png", "webp"] {
        let path = folder.join(format!("{key}.{ext}"));
        if path.metadata().is_ok_and(|meta| meta.len() > 0 && meta.len() <= 5_000_000) { return Ok(path.to_string_lossy().into_owned()); }
    }
    let redirects = reqwest::redirect::Policy::custom(|attempt| {
        let image = format!("<meta property='og:image' content='{}'>", attempt.url());
        if attempt.previous().len() < 3 && thumbnail(&image).is_some() { attempt.follow() } else { attempt.stop() }
    });
    let client = reqwest::Client::builder().redirect(redirects).timeout(std::time::Duration::from_secs(8)).user_agent("Mozilla/5.0 CreteOS/0.1").build().map_err(|e| e.to_string())?;
    let mut response = client.get(image_url).send().await.map_err(|e| e.to_string())?.error_for_status().map_err(|e| e.to_string())?;
    let mut bytes = Vec::new();
    while let Some(chunk) = response.chunk().await.map_err(|e| e.to_string())? {
        if bytes.len() + chunk.len() > 5_000_000 { return Err("News image exceeds size limit".into()); }
        bytes.extend_from_slice(&chunk);
    }
    let ext = image_extension(&bytes).ok_or("Unsupported news image format")?;
    let path = folder.join(format!("{key}.{ext}"));
    std::fs::write(&path, bytes).map_err(|e| e.to_string())?;
    Ok(path.to_string_lossy().into_owned())
}

#[cfg(test)] mod tests {
    #[test] fn caches_only_browser_image_formats_not_error_pages() {
        assert_eq!(super::image_extension(b"\x89PNG\r\n\x1a\nrest"), Some("png"));
        assert_eq!(super::image_extension(&[0xff, 0xd8, 0xff, 0]), Some("jpg"));
        assert_eq!(super::image_extension(b"RIFF1234WEBPrest"), Some("webp"));
        assert!(super::image_extension(b"<html>denied</html>").is_none());
        assert!(super::image_extension(b"<svg onload='x'/>").is_none());
    }
    #[test] fn reads_real_games_publisher_cdn_and_flexible_tags() {
        assert_eq!(super::thumbnail("<META\ncontent = 'https://assetsio.gnwcdn.com/nvidia.jpg?x=1&amp;y=2' property = 'og:image'>").unwrap(), "https://assetsio.gnwcdn.com/nvidia.jpg?x=1&y=2");
        assert!(super::thumbnail("<meta name=\"twitter:image\" content=\"https://xboxwire.thesourcemediaassets.com/labs.jpg\">").is_some());
        assert!(super::thumbnail("<meta property=\"og:image\" content=\"https://127.0.0.1/private.jpg\">").is_none());
        assert!(super::article_host("gamesindustry.biz"));
        assert!(!super::article_host("gamesindustry.biz.attacker.test"));
    }
}

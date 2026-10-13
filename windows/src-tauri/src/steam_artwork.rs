use serde_json::Value;

fn steam_image(value: Option<&Value>) -> Option<String> {
    let text = value?.as_str()?;
    let url = reqwest::Url::parse(text).ok()?;
    let host = url.host_str()?;
    (url.scheme() == "https" && host.ends_with(".steamstatic.com"))
        .then(|| text.to_owned())
}

pub fn store_images(value: &Value, appid: u32) -> (Option<String>, Option<String>) {
    let entry = &value[appid.to_string()];
    if entry["success"].as_bool() != Some(true) { return (None, None); }
    let data = &entry["data"];
    let header = steam_image(data.get("header_image"));
    let background = steam_image(data.get("background_raw")).or_else(|| header.clone());
    (header, background)
}

pub fn fetch_store_images(client: &reqwest::blocking::Client, appid: u32) -> (Option<String>, Option<String>) {
    client.get(format!("https://store.steampowered.com/api/appdetails?appids={appid}"))
        .send().ok().and_then(|response| response.error_for_status().ok())
        .and_then(|response| response.json::<Value>().ok())
        .map(|value| store_images(&value, appid)).unwrap_or((None, None))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn accepts_current_hashed_store_art_and_falls_back_to_header() {
        let value = serde_json::json!({"42":{"success":true,"data":{
            "header_image":"https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/42/hash/header.jpg?t=1"
        }}});
        let (header, background) = store_images(&value, 42);
        assert!(header.is_some());
        assert_eq!(header, background);
    }
    #[test]
    fn rejects_failed_responses_and_non_steam_urls() {
        let failed = serde_json::json!({"42":{"success":false}});
        assert_eq!(store_images(&failed, 42), (None, None));
        let unsafe_url = serde_json::json!({"42":{"success":true,"data":{
            "header_image":"https://steamstatic.com.example.org/header.jpg",
            "background_raw":"file:///C:/secret"
        }}});
        assert_eq!(store_images(&unsafe_url, 42), (None, None));
    }
}

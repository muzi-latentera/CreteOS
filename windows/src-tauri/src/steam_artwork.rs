use serde_json::Value;

// Read declared dimensions without decoding/re-encoding publisher artwork.
pub fn image_dimensions(bytes: &[u8]) -> Option<(u32, u32)> {
    if bytes.starts_with(b"\x89PNG\r\n\x1a\n") && bytes.len() >= 24 && &bytes[12..16] == b"IHDR" {
        return Some((u32::from_be_bytes(bytes[16..20].try_into().ok()?), u32::from_be_bytes(bytes[20..24].try_into().ok()?)));
    }
    if bytes.starts_with(&[0xff, 0xd8]) {
        let mut offset = 2;
        while offset + 4 <= bytes.len() {
            if bytes[offset] != 0xff { return None; }
            while offset < bytes.len() && bytes[offset] == 0xff { offset += 1; }
            let marker = *bytes.get(offset)?; offset += 1;
            if marker == 0xd9 || marker == 0xda { return None; }
            if marker == 0x01 || (0xd0..=0xd8).contains(&marker) { continue; }
            let length = u16::from_be_bytes(bytes.get(offset..offset + 2)?.try_into().ok()?) as usize;
            if length < 2 || offset + length > bytes.len() { return None; }
            if matches!(marker, 0xc0 | 0xc1 | 0xc2 | 0xc3 | 0xc5 | 0xc6 | 0xc7 | 0xc9 | 0xca | 0xcb | 0xcd | 0xce | 0xcf) && length >= 7 {
                let h = u16::from_be_bytes(bytes[offset + 3..offset + 5].try_into().ok()?) as u32;
                let w = u16::from_be_bytes(bytes[offset + 5..offset + 7].try_into().ok()?) as u32;
                return Some((w, h));
            }
            offset += length;
        }
    }
    if bytes.len() >= 30 && &bytes[..4] == b"RIFF" && &bytes[8..12] == b"WEBP" {
        if &bytes[12..16] == b"VP8X" {
            let read24 = |start: usize| bytes[start] as u32 | ((bytes[start + 1] as u32) << 8) | ((bytes[start + 2] as u32) << 16);
            return Some((read24(24) + 1, read24(27) + 1));
        }
        if &bytes[12..16] == b"VP8 " && &bytes[23..26] == b"\x9d\x01\x2a" {
            return Some(((u16::from_le_bytes(bytes[26..28].try_into().ok()?) & 0x3fff) as u32, (u16::from_le_bytes(bytes[28..30].try_into().ok()?) & 0x3fff) as u32));
        }
    }
    if bytes.len() >= 25 && &bytes[..4] == b"RIFF" && &bytes[8..16] == b"WEBPVP8L" && bytes[20] == 0x2f {
        let bits = u32::from_le_bytes(bytes[21..25].try_into().ok()?);
        return Some(((bits & 0x3fff) + 1, ((bits >> 14) & 0x3fff) + 1));
    }
    None
}

pub fn is_portrait(path: &str) -> bool {
    std::fs::read(path).ok().and_then(|bytes| image_dimensions(&bytes)).map(|(w, h)| w > 0 && h > w).unwrap_or(false)
}

pub fn download_portrait(client: &reqwest::blocking::Client, url: &str, dir: &std::path::Path, name: &str) -> Option<String> {
    let path = dir.join(format!("{name}.jpg"));
    if is_portrait(path.to_str()?) { return Some(path.to_string_lossy().into_owned()); }
    let bytes = client.get(url).send().ok()?.error_for_status().ok()?.bytes().ok()?;
    if bytes.len() > 20 * 1024 * 1024 { return None; }
    let (w, h) = image_dimensions(&bytes)?;
    if w == 0 || h <= w { return None; }
    std::fs::create_dir_all(dir).ok()?;
    std::fs::write(&path, bytes).ok()?;
    Some(path.to_string_lossy().into_owned())
}

pub fn library_images(value: &Value, appid: u32) -> (Option<String>, Option<String>) {
    let Some(item) = value.pointer("/response/store_items").and_then(Value::as_array)
        .and_then(|items| items.iter().find(|item| item["appid"].as_u64() == Some(appid as u64) && item["success"].as_u64() == Some(1))) else { return (None, None); };
    let assets = &item["assets"];
    let Some(format) = assets["asset_url_format"].as_str().filter(|s| s.starts_with(&format!("steam/apps/{appid}/")) && s.contains("${FILENAME}")) else { return (None, None); };
    let asset = |keys: &[&str]| keys.iter().find_map(|key| {
        let file = assets[*key].as_str()?;
        if file.is_empty() || file.contains("..") || file.starts_with('/') || file.contains(':') || file.contains('\\') { return None; }
        let url = format!("https://shared.fastly.steamstatic.com/store_item_assets/{}", format.replace("${FILENAME}", file));
        steam_image(Some(&Value::String(url)))
    });
    (asset(&["library_capsule_2x", "library_capsule"]), asset(&["library_hero_2x", "library_hero", "raw_page_background"]))
}

pub fn fetch_library_images(client: &reqwest::blocking::Client, appid: u32) -> (Option<String>, Option<String>) {
    let input = serde_json::json!({"ids":[{"appid":appid}],"context":{"language":"english","country_code":"GB"},"data_request":{"include_assets":true}}).to_string();
    client.get("https://api.steampowered.com/IStoreBrowseService/GetItems/v1/").query(&[("input_json", input)])
        .send().ok().and_then(|r| r.error_for_status().ok()).and_then(|r| r.json::<Value>().ok())
        .map(|value| library_images(&value, appid)).unwrap_or((None, None))
}

fn title_key(name: &str) -> String {
    name.replace(['™','®'],"").chars().filter(|c|c.is_alphanumeric()).flat_map(char::to_lowercase).collect()
}
fn art_base_title(name: &str) -> String {
    let clean=name.replace(['™','®'],"").split_whitespace().collect::<Vec<_>>().join(" ");
    let boundary=|c:char|c.is_whitespace() || matches!(c,':'|'–'|'—'|'-');
    for suffix in ["complete edition","definitive edition","ultimate edition","game of the year edition","goty edition","gold edition","deluxe edition","enhanced edition","director's cut","final cut","20 year celebration"] {
        for variant in [format!("the {suffix}"),suffix.to_string()] {
            let Some(start)=clean.len().checked_sub(variant.len()) else {continue;};
            let Some(tail)=clean.get(start..) else {continue;};
            if !tail.eq_ignore_ascii_case(&variant) {continue;}
            let prefix=&clean[..start];
            if !prefix.chars().last().is_some_and(boundary) {continue;}
            let base=prefix.trim_end_matches(boundary);
            if !base.is_empty() {return base.to_string();}
        }
    }
    clean
}
pub fn title_artwork_match(value: &Value, name: &str) -> Option<u32> {
    let items=value["items"].as_array()?;
    let exact=items.iter().filter(|item|item["name"].as_str().is_some_and(|title|title_key(title)==title_key(name))).collect::<Vec<_>>();
    let matches=if exact.is_empty() {items.iter().filter(|item|item["name"].as_str().is_some_and(|title|title_key(&art_base_title(title))==title_key(&art_base_title(name)))).collect::<Vec<_>>()}else{exact};
    if matches.len()!=1 {return None;}
    u32::try_from(matches[0]["id"].as_u64()?).ok()
}
pub fn find_title_appid(client: &reqwest::blocking::Client, name: &str) -> Option<u32> {
    let recorded=artwork_product(name);
    if let Some(id)=recorded["artSteamId"].as_u64().and_then(|id|u32::try_from(id).ok()) {return Some(id);}
    for query in [name.to_string(), art_base_title(name)] {
        let value=client.get("https://store.steampowered.com/api/storesearch/").query(&[("term",query.as_str()),("cc","GB"),("l","english")])
            .send().ok().and_then(|r|r.error_for_status().ok()).and_then(|r|r.json::<Value>().ok());
        if let Some(id)=value.and_then(|value|title_artwork_match(&value,name)) {return Some(id);}
        if query==art_base_title(name) {break;}
    }
    None
}

pub fn xbox_images(value: &Value) -> (Option<String>, Option<String>) {
    let Some(product) = value.pointer("/Payload/Products/0").or_else(|| value.get("Payload")).or_else(|| value.pointer("/Products/0")) else { return (None, None); };
    let images = product.get("Images").or_else(|| product.pointer("/LocalizedProperties/0/Images")).and_then(Value::as_array);
    let select = |kind: &str| images.and_then(|images| images.iter().find_map(|image| {
        let purpose = image["ImageType"].as_str().or_else(|| image["ImagePurpose"].as_str())?;
        if !purpose.eq_ignore_ascii_case(kind) { return None; }
        let text = image["Url"].as_str().or_else(|| image["Uri"].as_str())?;
        let text = if text.starts_with("//") { format!("https:{text}") } else { text.to_owned() };
        let url = reqwest::Url::parse(&text).ok()?;
        (url.scheme() == "https" && url.host_str() == Some("store-images.s-microsoft.com")).then_some(text)
    }));
    (select("Poster"), select("hero").or_else(|| select("SuperHeroArt")))
}

pub fn fetch_xbox_images(client: &reqwest::blocking::Client, product_id: &str) -> (Option<String>, Option<String>) {
    if product_id.len() != 12 || !product_id.bytes().all(|b| b.is_ascii_alphanumeric()) { return (None, None); }
    client.get(format!("https://storeedgefd.dsx.mp.microsoft.com/v9.0/products/{product_id}?market=US&locale=en-US&deviceFamily=Windows.Desktop"))
        .send().ok().and_then(|r| r.error_for_status().ok()).and_then(|r| r.json::<Value>().ok())
        .map(|value| xbox_images(&value)).unwrap_or((None, None))
}

pub fn artwork_product(name: &str) -> Value {
    serde_json::from_str::<Value>(include_str!("../../src/data/artworkProducts.json")).ok()
        .and_then(|products| products.get(name).cloned()).unwrap_or(Value::Null)
}
// Reviewed alternatives for publisher "portrait" files containing a padded
// landscape header. Dimensions alone cannot establish usable cover composition.
pub fn reviewed_portrait(name: &str) -> Option<(String,String)> {
    let record=artwork_product(name);let text=record["portraitUrl"].as_str()?;
    let url=reqwest::Url::parse(text).ok()?;
    if url.scheme()!="https" || url.host_str()!=Some("cdn2.steamgriddb.com") || !url.username().is_empty() || url.password().is_some() || url.port().is_some(){return None;}
    let file=std::path::Path::new(url.path()).file_stem()?.to_str()?;
    if file.len()!=32 || !file.chars().all(|c|c.is_ascii_hexdigit()){return None;}
    Some((text.into(),file.into()))
}
pub fn reviewed_cache_matches(name: &str, path: &str) -> bool {
    let Some((_,key))=reviewed_portrait(name) else{return true;};
    let path=std::path::Path::new(path);
    path.file_stem().and_then(|s|s.to_str())==Some(key.as_str())&&path.parent().is_some_and(|p|p.ends_with("reviewed-v1"))
}

pub fn fetch_community_images(client: &reqwest::blocking::Client, name: &str) -> (Option<String>, Option<String>) {
    let normalize = |name: &str| name.chars().filter(|c| c.is_alphanumeric()).flat_map(char::to_lowercase).collect::<String>();
    // The public website exposes these read-only search routes without an API
    // key. Match the exact title; do not silently use the first search result.
    let games = client.get("https://www.steamgriddb.com/api/public/search/autocomplete").query(&[("term", name)])
        .send().ok().and_then(|r| r.error_for_status().ok()).and_then(|r| r.json::<Value>().ok());
    let Some(id) = games.as_ref().and_then(|g| g["data"].as_array()).and_then(|items| items.iter()
        .find(|game| game["name"].as_str().map(&normalize) == Some(normalize(name))))
        .and_then(|game| game["id"].as_u64()) else { return (None, None); };
    let fetch = |kind: &str| {
        let body = serde_json::json!({"asset_type":kind,"game_id":[id],"page":0,"limit":20,
            "dimensions":if kind == "grid" {vec!["600x900"]} else {vec!["all"]},"styles":["all"],"languages":["all"],"formats":["all"],
            "order":"score_desc","static":true,"animated":false,"nsfw":false,"epilepsy":false,"humor":false,"untagged":true,"user_steam64":null,"user_steam64_likes":null});
        let value = client.post("https://www.steamgriddb.com/api/public/search/assets").json(&body).send().ok()?.error_for_status().ok()?.json::<Value>().ok()?;
        value.pointer("/data/assets")?.as_array()?.iter().find_map(|asset| {
            let w=asset["width"].as_u64()?; let h=asset["height"].as_u64()?;
            if asset["is_animated"].as_bool() == Some(true) || (kind == "grid" && h <= w) || (kind == "hero" && w <= h) {return None;}
            let text=asset["url"].as_str()?; let url=reqwest::Url::parse(text).ok()?;
            (url.scheme()=="https" && url.host_str()?.ends_with(".steamgriddb.com")).then(||text.to_owned())
        })
    };
    (fetch("grid"),fetch("hero"))
}

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
    #[test] fn reviewed_cover_has_exact_source_and_versioned_cache_key(){
        let (url,key)=reviewed_portrait("Child of Light").unwrap();assert!(url.starts_with("https://cdn2.steamgriddb.com/grid/"));
        assert!(!reviewed_cache_matches("Child of Light","C:/art/portrait-v2/Child_of_Light.jpg"));
        assert!(reviewed_cache_matches("Child of Light",&format!("C:/art/reviewed-v1/{key}.jpg")));
        assert!(reviewed_portrait("Unreviewed game").is_none());
    }
    #[test] fn title_art_is_exact_and_never_borrows_a_sequel_or_ambiguous_first_hit() {
        let value=serde_json::json!({"items":[{"id":1086940,"name":"Baldur's Gate 3"},{"id":42,"name":"Baldur's Gate 2"}]});
        assert_eq!(title_artwork_match(&value,"Baldur's Gate 3"),Some(1086940));
        assert_eq!(title_artwork_match(&value,"Baldur's Gate"),None);
        assert_eq!(title_artwork_match(&serde_json::json!({"items":[{"id":8870,"name":"BioShock Infinite"}]}),"BioShock Infinite: Complete Edition"),Some(8870));
        assert_eq!(title_artwork_match(&serde_json::json!({"items":[{"id":42,"name":"Nioh 2 – The Complete Edition"}]}),"Nioh: The Complete Edition"),None);
        assert_eq!(title_artwork_match(&serde_json::json!({"items":[{"id":1,"name":"Prey"},{"id":2,"name":"PREY"}]}),"Prey"),None);
    }
    #[test]
    fn resolves_exact_hashed_portrait_and_library_hero() {
        let value = serde_json::json!({"response":{"store_items":[{"appid":42,"success":1,"assets":{
            "asset_url_format":"steam/apps/42/${FILENAME}?t=7",
            "header":"header/hash.jpg","library_capsule_2x":"portrait/library_capsule_2x.jpg","library_hero":"hero/library_hero.jpg"
        }}]}});
        let (cover, hero) = library_images(&value,42);
        assert!(cover.unwrap().contains("/42/portrait/library_capsule_2x.jpg?t=7"));
        assert!(hero.unwrap().contains("/42/hero/library_hero.jpg"));
        assert_eq!(library_images(&value,43),(None,None));
    }
    #[test]
    fn never_uses_header_as_portrait_or_unsafe_asset_path() {
        let value = serde_json::json!({"response":{"store_items":[{"appid":42,"success":1,"assets":{
            "asset_url_format":"steam/apps/42/${FILENAME}","header":"header.jpg","library_capsule":"../../other.jpg"
        }}]}});
        assert_eq!(library_images(&value,42),(None,None));
    }
    #[test]
    fn checks_png_jpeg_and_truncated_dimensions() {
        let mut png=b"\x89PNG\r\n\x1a\n\0\0\0\rIHDR".to_vec();
        png.extend(600u32.to_be_bytes()); png.extend(900u32.to_be_bytes());
        assert_eq!(image_dimensions(&png),Some((600,900)));
        let jpeg=[0xff,0xd8,0xff,0xc0,0,7,8,3,0x84,2,0x58];
        assert_eq!(image_dimensions(&jpeg),Some((600,900)));
        assert_eq!(image_dimensions(&jpeg[..8]),None);
        assert_eq!(image_dimensions(b"<html>404</html>"),None);
    }
    #[test]
    fn xbox_uses_poster_instead_of_square_boxart() {
        let value=serde_json::json!({"Payload":{"Images":[
            {"ImageType":"BoxArt","Url":"https://store-images.s-microsoft.com/square"},
            {"ImageType":"Poster","Url":"https://store-images.s-microsoft.com/portrait"},
            {"ImageType":"hero","Url":"https://store-images.s-microsoft.com/hero"}
        ]}});
        assert_eq!(xbox_images(&value),(Some("https://store-images.s-microsoft.com/portrait".into()),Some("https://store-images.s-microsoft.com/hero".into())));
    }
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

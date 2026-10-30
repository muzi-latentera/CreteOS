//! Browser refresh tokens use Steam's web login transfer, not GenerateForApp.
//! Credentials/cookies stay in memory; errors never contain response bodies.
use base64::{engine::general_purpose, Engine as _};
use reqwest::blocking::Client;
use serde_json::Value;
use std::io::Read;
use std::time::Duration;

fn claims(token: &str) -> Option<Value> {
    let payload = token.split('.').nth(1)?;
    let bytes = general_purpose::URL_SAFE_NO_PAD.decode(payload)
        .or_else(|_| general_purpose::URL_SAFE.decode(payload)).ok()?;
    serde_json::from_slice(&bytes).ok()
}
pub fn is_browser_token(token: &str) -> bool {
    claims(token).is_some_and(|value| value["aud"].as_array()
        .is_some_and(|aud| aud.iter().any(|a| a == "web")))
}
fn transfer_url(value: &str) -> bool {
    value == "https://steamcommunity.com/login/settoken"
        || value == "https://steamcommunity.com/login/settoken/"
}
fn access_cookie(header: &str, steamid: &str) -> Option<String> {
    let value = header.split(';').next()?.strip_prefix("steamLoginSecure=")?;
    let decoded = urlencoding::decode(value).ok()?;
    let (subject, token) = decoded.split_once("||")?;
    let value = claims(token)?;
    if subject != steamid || value["sub"].as_str()? != steamid
        || value["aud"].as_array()?.iter().any(|a| a == "derive") { return None; }
    Some(token.to_string())
}
fn response_json(response: reqwest::blocking::Response) -> Result<Value, String> {
    let mut bytes = Vec::new();
    response.take(131_073).read_to_end(&mut bytes).map_err(|_| "Steam web response failed")?;
    if bytes.len() > 131_072 { return Err("Steam web response too large".into()); }
    serde_json::from_slice(&bytes).map_err(|_| "Steam web response invalid".into())
}
pub fn access_token(refresh: &str, steamid: &str) -> Result<String, String> {
    let client = Client::builder().timeout(Duration::from_secs(20))
        .redirect(reqwest::redirect::Policy::none()).build().map_err(|_| "Steam web client failed")?;
    let mut random = [0u8; 12];
    getrandom::getrandom(&mut random).map_err(|_| "Steam web session unavailable")?;
    let session: String = random.iter().map(|b| format!("{b:02x}")).collect();
    let response = client.post("https://login.steampowered.com/jwt/finalizelogin")
        .header("Origin", "https://steamcommunity.com").header("Referer", "https://steamcommunity.com/")
        .form(&[("nonce", refresh), ("sessionid", session.as_str()), ("redir", "https://steamcommunity.com/login/home/?goto=")])
        .send().and_then(|r|r.error_for_status()).map_err(|_| "Steam web authorization request failed")?;
    let value = response_json(response)?;
    if let Some(error) = value["error"].as_u64().filter(|n| *n != 0 && *n != 1) {
        return Err(format!("Steam web authorization EResult {error}; reconnect Steam"));
    }
    if value["steamID"].as_str().is_some_and(|id| id != steamid) {
        return Err("Steam web account mismatch".into());
    }
    let transfers = value["transfer_info"].as_array().ok_or("Steam web transfer missing; reconnect Steam")?;
    let transfer = transfers.iter().find(|t| t["url"].as_str().is_some_and(transfer_url))
        .ok_or("Steam community transfer missing")?;
    let params = transfer["params"].as_object().ok_or("Steam web transfer invalid")?;
    let mut form: Vec<(String,String)> = params.iter().map(|(key,value)| {
        value.as_str().map(|v|(key.clone(),v.to_string())).ok_or("Steam web transfer parameter invalid")
    }).collect::<Result<_,_>>()?;
    form.retain(|(key,_)| key != "steamID");
    form.push(("steamID".into(), steamid.into()));
    let response = client.post(transfer["url"].as_str().unwrap()).form(&form)
        .send().and_then(|r|r.error_for_status()).map_err(|_| "Steam community authorization failed")?;
    response.headers().get_all(reqwest::header::SET_COOKIE).iter()
        .filter_map(|h|h.to_str().ok()).find_map(|h|access_cookie(h,steamid))
        .ok_or_else(|| "Steam web access token missing; reconnect Steam".into())
}

#[cfg(test)] mod tests {
    fn token(aud: &[&str], subject: &str) -> String {
        use base64::Engine;
        format!("header.{}.signature", base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(
            serde_json::to_vec(&serde_json::json!({"aud":aud,"sub":subject})).unwrap()))
    }
    #[test] fn browser_tokens_select_web_renewal() {
        assert!(super::is_browser_token(&token(&["web","derive"],"123")));
        assert!(!super::is_browser_token(&token(&["mobile","derive"],"123")));
        assert!(!super::is_browser_token("invalid"));
    }
    #[test] fn transfers_are_confined_to_steam_community() {
        assert!(super::transfer_url("https://steamcommunity.com/login/settoken"));
        for url in ["http://steamcommunity.com/login/settoken", "https://steamcommunity.com.evil.test/login/settoken", "https://evil.test/login/settoken", "https://steamcommunity.com/login/settoken?next=evil"] {
            assert!(!super::transfer_url(url));
        }
    }
    #[test] fn cookie_must_be_an_access_token_for_the_expected_account() {
        let access=token(&["web"],"123");
        let cookie=format!("steamLoginSecure={}; Path=/; Secure",urlencoding::encode(&format!("123||{access}")));
        assert_eq!(super::access_cookie(&cookie,"123"),Some(access));
        assert!(super::access_cookie(&cookie,"456").is_none());
        let refresh=token(&["web","derive"],"123");
        assert!(super::access_cookie(&format!("steamLoginSecure={}",urlencoding::encode(&format!("123||{refresh}"))),"123").is_none());
    }
}

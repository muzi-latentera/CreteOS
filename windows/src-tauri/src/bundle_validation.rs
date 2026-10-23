//! Verify the executable's own embedded entry points before deployment.
//! A successful compiler exit alone does not prove a hashed JS asset resolves.
use serde::Serialize;
use sha2::{Digest, Sha256};

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AssetCheck { path: String, bytes: usize, sha256: String }

pub fn verify(index: &[u8], lookup: impl Fn(&str) -> Option<Vec<u8>>) -> Result<Vec<AssetCheck>, String> {
    let html = std::str::from_utf8(index).map_err(|_| "Embedded index is not UTF-8")?;
    let mut paths = std::collections::BTreeSet::new();
    for attr in ["src=\"", "href=\""] {
        for part in html.split(attr).skip(1) {
            let path = part.split('"').next().unwrap_or_default();
            if path.starts_with("/assets/") && (path.ends_with(".js") || path.ends_with(".css")) {
                paths.insert(path.to_string());
            }
        }
    }
    if !paths.iter().any(|path| path.ends_with(".js")) { return Err("Embedded index has no JavaScript entry".into()); }
    let mut checks = Vec::new();
    for path in paths {
        let bytes = lookup(&path).ok_or_else(|| format!("Missing embedded asset: {path}"))?;
        let prefix = String::from_utf8_lossy(&bytes[..bytes.len().min(100)]).trim_start().to_ascii_lowercase();
        if bytes.is_empty() || prefix.starts_with("<!doctype html") || prefix.starts_with("<html") {
            return Err(format!("Embedded script/style resolves to empty data or HTML: {path}"));
        }
        checks.push(AssetCheck { path, bytes: bytes.len(), sha256: format!("{:x}", Sha256::digest(&bytes)) });
    }
    Ok(checks)
}

#[cfg(test)]
mod tests {
    use super::*;
    const INDEX: &[u8] = br#"<script type="module" src="/assets/index-AbCd1234.js"></script><link href="/assets/main.css">"#;
    #[test]
    fn rejects_missing_hashed_entry_and_html_fallback() {
        assert!(verify(INDEX, |_| None).unwrap_err().contains("Missing embedded asset"));
        assert!(verify(INDEX, |_| Some(b"<!doctype html><html>fallback</html>".to_vec())).unwrap_err().contains("HTML"));
    }
    #[test]
    fn verifies_actual_case_sensitive_script_and_stylesheet_bytes() {
        let result = verify(INDEX, |path| match path {
            "/assets/index-AbCd1234.js" => Some(b"console.log('ready')".to_vec()),
            "/assets/main.css" => Some(b"body{color:white}".to_vec()),
            _ => None,
        }).unwrap();
        assert_eq!(result.len(), 2);
        assert!(result.iter().all(|entry| entry.sha256.len() == 64));
    }
}

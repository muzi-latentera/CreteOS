//! Opt-in, expiring input recordings. No navigation or input injection.
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::{fs::{self, OpenOptions}, io::Write, path::PathBuf, time::{SystemTime, UNIX_EPOCH}};

#[derive(Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct InputDiagnosticConfig {
    session_id: String,
    duration_ms: u64,
    expires_at_ms: u64,
}

fn valid(config: &InputDiagnosticConfig, now: u64) -> bool {
    !config.session_id.is_empty() && config.session_id.len() <= 64
        && config.session_id.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'-' || c == b'_')
        && (1_000..=600_000).contains(&config.duration_ms)
        && config.expires_at_ms > now && config.expires_at_ms.saturating_sub(now) <= 3_600_000
}

fn folder() -> Result<PathBuf, String> {
    Ok(PathBuf::from(std::env::var_os("LOCALAPPDATA").ok_or("LOCALAPPDATA unavailable")?)
        .join("CreteOS-Bootstrap").join("diagnostics"))
}

#[tauri::command]
pub fn input_diagnostic_config() -> Result<Option<InputDiagnosticConfig>, String> {
    let request = folder()?.join("input-request.json");
    if !request.exists() { return Ok(None); }
    let bytes = fs::read(&request).map_err(|e| e.to_string())?;
    if bytes.len() > 4096 { return Err("Input diagnostic request too large".into()); }
    let config: InputDiagnosticConfig = serde_json::from_slice(&bytes).map_err(|e| e.to_string())?;
    let now = SystemTime::now().duration_since(UNIX_EPOCH).map_err(|e| e.to_string())?.as_millis() as u64;
    Ok(valid(&config, now).then_some(config))
}

#[tauri::command]
pub fn append_input_diagnostic(session_id: String, events: Vec<Value>) -> Result<(), String> {
    let config = input_diagnostic_config()?.ok_or("Input diagnostic recording not enabled")?;
    if session_id != config.session_id || events.len() > 1024 { return Err("Invalid input diagnostic batch".into()); }
    let mut bytes = Vec::new();
    for event in events {
        serde_json::to_writer(&mut bytes, &event).map_err(|e| e.to_string())?;
        bytes.push(b'\n');
        if bytes.len() > 1_048_576 { return Err("Input diagnostic batch too large".into()); }
    }
    let mut file = OpenOptions::new().create(true).append(true)
        .open(folder()?.join(format!("input-{}.jsonl", config.session_id))).map_err(|e| e.to_string())?;
    if file.metadata().map_err(|e| e.to_string())?.len() + bytes.len() as u64 > 8_388_608 {
        return Err("Input diagnostic recording size limit reached".into());
    }
    file.write_all(&bytes).map_err(|e| e.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;
    fn config() -> InputDiagnosticConfig { InputDiagnosticConfig { session_id:"gpd-test_1".into(), duration_ms:60_000, expires_at_ms:120_000 } }
    #[test] fn accepts_short_lived_recording() { assert!(valid(&config(), 100_000)); }
    #[test] fn rejects_paths_and_expired_or_excessive_capture() {
        let mut c = config(); c.session_id = "../profile".into(); assert!(!valid(&c,100_000));
        let mut c = config(); c.expires_at_ms = 100_000; assert!(!valid(&c,100_000));
        let mut c = config(); c.duration_ms = 600_001; assert!(!valid(&c,100_000));
    }
}

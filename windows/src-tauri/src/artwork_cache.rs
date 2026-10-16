use std::{collections::HashMap, path::Path, sync::Mutex};
static SAVE: Mutex<()> = Mutex::new(());

// Fetches run concurrently. Merge under a short disk-only lock, never a network
// lock, so a stale snapshot/blank cannot erase another fetch's successful art.
pub fn save(path: &Path, incoming: &HashMap<String, String>) {
    let Ok(_guard) = SAVE.lock() else { return; };
    let mut merged: HashMap<String,String> = if incoming.is_empty() { HashMap::new() } else {
        std::fs::read(path).ok().and_then(|bytes| serde_json::from_slice(&bytes).ok()).unwrap_or_default()
    };
    for (name, value) in incoming {
        if merged.get(name).is_none_or(String::is_empty) { merged.insert(name.clone(),value.clone()); }
    }
    if let Some(parent) = path.parent() { if std::fs::create_dir_all(parent).is_err() { return; } }
    let Ok(bytes) = serde_json::to_vec(&merged) else { return; };
    let temporary = path.with_extension("json.tmp");
    if std::fs::write(&temporary,bytes).is_ok() { let _ = std::fs::rename(temporary,path); }
}

#[cfg(test)] mod tests {
    use super::*;
    #[test] fn concurrent_snapshots_preserve_each_success_and_clear_is_explicit() {
        let path=std::env::temp_dir().join(format!("crete-art-cache-{}-{}.json",std::process::id(),std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos()));
        std::thread::scope(|scope| {for i in 0..8 {let path=&path;scope.spawn(move || save(path,&HashMap::from([(format!("game{i}"),format!("portrait{i}"))])));}});
        save(&path,&HashMap::from([("game0".into(),String::new())]));
        let saved:HashMap<String,String>=serde_json::from_slice(&std::fs::read(&path).unwrap()).unwrap();
        assert_eq!(saved.len(),8); assert_eq!(saved["game0"],"portrait0");
        save(&path,&HashMap::new()); assert_eq!(std::fs::read_to_string(&path).unwrap(),"{}");
        std::fs::remove_file(path).unwrap();
    }
}

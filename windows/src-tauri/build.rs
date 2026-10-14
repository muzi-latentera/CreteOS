fn main() {
    println!("cargo:rerun-if-env-changed=GITHUB_SHA");
    // Load SGDB_API_KEY from src-tauri/.env if present, then fall back to the
    // actual environment. This keeps the key out of source control.
    let env_path = std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join(".env");
    if env_path.exists() {
        if let Ok(content) = std::fs::read_to_string(&env_path) {
            for line in content.lines() {
                let line = line.trim();
                if line.starts_with('#') || line.is_empty() {
                    continue;
                }
                if let Some((k, v)) = line.split_once('=') {
                    let k = k.trim();
                    let v = v.trim().trim_matches('"');
                    println!("cargo:rustc-env={}={}", k, v);
                }
            }
        }
    }
    let commit = std::env::var("GITHUB_SHA").ok().or_else(|| std::process::Command::new("git").args(["rev-parse", "HEAD"]).output().ok().filter(|output| output.status.success()).map(|output| String::from_utf8_lossy(&output.stdout).trim().to_string())).unwrap_or_else(|| "development".into());
    println!("cargo:rustc-env=CRETE_SOURCE_COMMIT={commit}");
    tauri_build::build()
}

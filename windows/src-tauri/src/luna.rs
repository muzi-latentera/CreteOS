use serde::{Deserialize, Serialize};
use std::path::PathBuf;

#[derive(Serialize, Deserialize)]
struct InstalledApp { target: PathBuf, app_id: String, profile: String }
pub fn luna_url(text: &str) -> bool {
    reqwest::Url::parse(text).ok().is_some_and(|url| url.scheme() == "https" && matches!(url.host_str(), Some("luna.amazon.co.uk" | "luna.amazon.com")) && url.username().is_empty() && url.password().is_none() && url.port().is_none())
}
fn option(args: &str, key: &str) -> Option<String> {
    let value = args.split_once(&format!("--{key}="))?.1;
    Some(if let Some(value) = value.strip_prefix('"') { value.split_once('"')?.0.to_string() } else { value.split_whitespace().next()?.to_string() })
}
fn same_executable(left: &std::path::Path, right: &std::path::Path) -> bool {
    left.to_string_lossy().replace('/', "\\").eq_ignore_ascii_case(&right.to_string_lossy().replace('/', "\\"))
}
fn valid(app: &InstalledApp) -> bool {
    let mut roots = Vec::new();
    for key in ["ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA"] { if let Some(root) = std::env::var_os(key) { roots.push(PathBuf::from(root).join("Google/Chrome/Application/chrome_proxy.exe")); } }
    roots.iter().any(|path| same_executable(path, &app.target)) && app.target.is_file()
        && app.app_id.len() == 32 && app.app_id.bytes().all(|c| (b'a'..=b'p').contains(&c))
        && (app.profile == "Default" || app.profile.strip_prefix("Profile ").is_some_and(|number| !number.is_empty() && number.bytes().all(|c| c.is_ascii_digit())))
}
fn installed() -> Result<InstalledApp, String> {
    let cache = crate::liftoff_dir().join("luna_installed_app.json");
    if let Some(app) = std::fs::read(&cache).ok().and_then(|bytes| serde_json::from_slice::<InstalledApp>(&bytes).ok()).filter(valid) { return Ok(app); }
    // Only shortcut metadata: reconstruct validated flags for the fixed Chrome
    // executable rather than executing arbitrary targets/shortcut arguments.
    let script = "$s=New-Object -ComObject WScript.Shell;foreach($d in @([Environment]::GetFolderPath('DesktopDirectory'),[Environment]::GetFolderPath('CommonDesktopDirectory'))){$p=Join-Path $d 'Amazon Luna.lnk';if(Test-Path -LiteralPath $p){$l=$s.CreateShortcut($p);@{target=$l.TargetPath;arguments=$l.Arguments}|ConvertTo-Json -Compress;break}}";
    let mut command = std::process::Command::new("powershell.exe");
    command.args(["-NoProfile", "-NonInteractive", "-Command", script]);
    let output = crate::streaming::bounded_output(command, None).map_err(|_| "Could not read the installed Luna app shortcut")?;
    let value: serde_json::Value = serde_json::from_slice(&output.stdout).map_err(|_| "Create an Amazon Luna desktop shortcut, then retry")?;
    let args = value["arguments"].as_str().ok_or("Missing Luna app arguments")?;
    let app = InstalledApp { target: PathBuf::from(value["target"].as_str().ok_or("Missing Luna app target")?), app_id: option(args, "app-id").ok_or("Missing Luna app ID")?, profile: option(args, "profile-directory").unwrap_or("Default".into()) };
    if !valid(&app) { return Err("The Luna shortcut is not a supported installed Chrome app".into()); }
    let _ = std::fs::write(cache, serde_json::to_vec(&app).map_err(|e| e.to_string())?);
    Ok(app)
}
pub fn launch(url: &str) -> Option<Result<u32, String>> {
    if !luna_url(url) { return None; }
    Some((|| {
        let app = installed()?;
        let mut command = std::process::Command::new(&app.target);
        command.current_dir(app.target.parent().ok_or("Missing Luna app directory")?)
            .arg(format!("--profile-directory={}", app.profile)).arg(format!("--app-id={}", app.app_id))
            .arg(format!("--app-launch-url-for-shortcuts-menu-item={url}")).arg("--start-fullscreen");
        command.spawn().map(|child| child.id()).map_err(|e| e.to_string())
    })())
}
#[cfg(test)] mod tests {
    #[test] fn windows_shortcut_path_separators_match() {
        assert!(super::same_executable(std::path::Path::new(r"C:\Program Files\Google/Chrome/Application/chrome_proxy.exe"), std::path::Path::new(r"c:\Program Files\Google\Chrome\Application\chrome_proxy.exe")));
        assert!(!super::same_executable(std::path::Path::new(r"C:\Program Files\Google/Chrome/Application/chrome_proxy.exe"), std::path::Path::new(r"C:\Other\chrome_proxy.exe")));
    }
    #[test] fn exact_luna_domains_and_shortcut_flags() {
        assert!(super::luna_url("https://luna.amazon.co.uk/play?asin=B0H6GQJMBN"));
        assert!(!super::luna_url("https://luna.amazon.co.uk.attacker.test/play"));
        assert!(!super::luna_url("https://user@luna.amazon.co.uk/play"));
        assert_eq!(super::option("--profile-directory=\"Profile 1\" --app-id=abc", "profile-directory").unwrap(), "Profile 1");
    }
}

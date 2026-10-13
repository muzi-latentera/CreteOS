use serde::Serialize;
use std::path::PathBuf;

fn client_path(provider: &str) -> Result<PathBuf, String> {
    let mut candidates = Vec::new();
    match provider {
        "geforceNow" => {
            if let Some(local) = dirs::data_local_dir() {
                candidates.push(local.join("NVIDIA Corporation/GeForceNOW/CEF/GeForceNOW.exe"));
            }
        }
        "moonlight" => {
            for key in ["ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA"] {
                if let Some(root) = std::env::var_os(key) {
                    candidates.push(PathBuf::from(&root).join("Moonlight Game Streaming/Moonlight.exe"));
                    candidates.push(PathBuf::from(root).join("Programs/Moonlight Game Streaming/Moonlight.exe"));
                }
            }
        }
        _ => return Err("Unknown streaming client".into()),
    }
    candidates.into_iter().find(|path| path.is_file())
        .ok_or_else(|| "Streaming client is not installed in a detected location".into())
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct StreamingClients {
    geforce_now: bool,
    moonlight: bool,
}

#[tauri::command]
pub fn get_streaming_clients() -> StreamingClients {
    StreamingClients { geforce_now: client_path("geforceNow").is_ok(), moonlight: client_path("moonlight").is_ok() }
}

#[tauri::command]
pub fn launch_streaming_client(provider: String) -> Result<(), String> {
    // Only detected, fixed client executables; no shell command or arbitrary
    // paths/arguments from the frontend. This opens the client's library.
    std::process::Command::new(client_path(&provider)?).spawn()
        .map(|_| ()).map_err(|error| error.to_string())
}

#[cfg(test)]
mod tests {
    #[test]
    fn rejects_unknown_clients() {
        assert!(super::client_path("powershell").is_err());
        assert!(super::client_path("C:/anything.exe").is_err());
    }
}

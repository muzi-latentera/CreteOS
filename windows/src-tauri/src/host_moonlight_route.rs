use std::{io::{BufRead, BufReader, Write}, net::Ipv4Addr, process::Stdio, sync::mpsc, time::Duration};

#[derive(Clone, Copy, Debug, Default, PartialEq, serde::Deserialize, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub enum Choice { #[default] Automatic, Ethernet, Wifi }

pub struct Lease {
    pub address: Ipv4Addr,
    _stop: mpsc::Sender<()>,
}

pub fn prepare(host: &str, choice: Choice) -> Result<Lease, String> {
    let mode = match choice {
        Choice::Wifi => "wifi",
        Choice::Ethernet => "ethernet",
        Choice::Automatic => return Err("Choose an interface before requesting a host route.".into()),
    };
    // Read the script separately from the following heartbeat bytes. The SSH
    // server provides the actual peer address even after a DHCP address change.
    let script = include_bytes!("host_moonlight_route.py");
    let script = String::from_utf8_lossy(script).replace("\r\n", "\n");
    let mut command = super::streaming::host_ssh_command(host, true)?;
    command.arg(format!("sudo -n /usr/bin/python3 -u -c 'import sys; exec(compile(sys.stdin.buffer.read(int(sys.argv[1])), \"creteos-route\", \"exec\"))' {} \"$SSH_CONNECTION\" {mode}", script.len()));
    command.stdin(Stdio::piped()).stdout(Stdio::piped()).stderr(Stdio::null());
    #[cfg(windows)] {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x08000000);
    }
    let mut child = command.spawn().map_err(|e| e.to_string())?;
    let mut input = child.stdin.take().expect("piped SSH input");
    let output = child.stdout.take().expect("piped SSH output");
    if let Err(error) = input.write_all(script.as_bytes()) {
        let _ = child.kill();
        let _ = child.wait();
        return Err(error.to_string());
    }
    let (result_tx, result_rx) = mpsc::channel();
    std::thread::spawn(move || {
        let mut line = String::new();
        let _ = BufReader::new(output).read_line(&mut line);
        let _ = result_tx.send(serde_json::from_str::<serde_json::Value>(&line).ok());
    });
    let response = result_rx.recv_timeout(Duration::from_secs(8)).ok().flatten();
    let address = response.as_ref().and_then(|v| v["address"].as_str()).and_then(|value| value.parse::<Ipv4Addr>().ok());
    let Some(address) = address else {
        drop(input);
        let _ = child.kill();
        let _ = child.wait();
        let message = match response.as_ref().and_then(|v| v["error"].as_str()) {
            Some("wifiUnavailable") => "Bosgame needs to be connected to E433 on 5 GHz.",
            Some("interfaceUnavailable") => "Bosgame's selected interface cannot reach this handheld on the local network.",
            Some("routeConflict") => "Bosgame already has a Moonlight route override. End the other session and retry.",
            _ => "CreteOS could not set Bosgame's Moonlight route. Check Bosgame's SSH and passwordless sudo access.",
        };
        return Err(message.into());
    };
    let (stop, keep_alive) = mpsc::channel();
    std::thread::spawn(move || {
        while let Err(mpsc::RecvTimeoutError::Timeout) = keep_alive.recv_timeout(Duration::from_secs(4)) {
            if input.write_all(b"x").is_err() { break; }
        }
        drop(input);
        for _ in 0..10 {
            if !matches!(child.try_wait(), Ok(None)) { return; }
            std::thread::sleep(Duration::from_millis(100));
        }
        let _ = child.kill();
        let _ = child.wait();
    });
    Ok(Lease { address, _stop: stop })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    #[ignore = "requires the idle paired Bosgame and temporary route authorization"]
    fn native_bosgame_connection_lease_starts_and_releases() {
        let route = prepare("Bosgame", Choice::Wifi).unwrap();
        assert!(route.address.is_private());
        assert!(has_route_rule());
        drop(route);
        for _ in 0..10 {
            if !has_route_rule() { return; }
            std::thread::sleep(Duration::from_millis(200));
        }
        panic!("The route was not released after the lease ended");
    }

    fn has_route_rule() -> bool {
        let mut command = super::super::streaming::host_ssh_command("Bosgame", true).unwrap();
        command.arg("ip -4 rule show");
        let result = super::super::streaming::bounded_output_timeout(command, None, 5).unwrap();
        assert!(result.status.success());
        String::from_utf8_lossy(&result.stdout).lines().any(|line| line.starts_with("12150:"))
    }
}

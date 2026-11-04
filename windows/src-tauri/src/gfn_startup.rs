//! Native settings acknowledgement, independent of NVIDIA's capability log wording.
use std::time::Duration;
#[derive(Default)]
pub struct WarmGate { storage:bool, stable_since:Option<Duration> }
impl WarmGate {
    pub fn observe(&mut self,line:&str){
        let text=line.to_ascii_lowercase();
        self.storage |= text.contains("shared storage cache updated") || text.contains("read settings block from sharedstorage");
    }
    pub fn ready(&mut self,elapsed:Duration,profile_matches:bool)->bool{
        if !self.storage || !profile_matches {self.stable_since=None;return false;}
        let since=*self.stable_since.get_or_insert(elapsed);
        elapsed>=Duration::from_secs(5) && elapsed.saturating_sub(since)>=Duration::from_secs(1)
    }
}
#[cfg(test)]mod tests{
use super::*;
#[test]fn current_native_storage_marker_and_stable_custom_profile_are_sufficient(){
 let mut gate=WarmGate::default();gate.observe("libraries/shared-storage shared storage cache updated");
 assert!(!gate.ready(Duration::from_secs(4),true));assert!(gate.ready(Duration::from_secs(5),true));
}
#[test]fn stale_settings_or_a_capability_marker_alone_cannot_start_a_game(){
 let mut gate=WarmGate::default();gate.observe("prefetchDeviceCapabilities response: true");
 assert!(!gate.ready(Duration::from_secs(10),true));gate.observe("Read settings block from sharedStorage:");
 assert!(!gate.ready(Duration::from_secs(11),false));assert!(!gate.ready(Duration::from_secs(12),true));
 assert!(!gate.ready(Duration::from_secs(13),false));assert!(!gate.ready(Duration::from_secs(14),true));
 assert!(gate.ready(Duration::from_secs(15),true));
}
}

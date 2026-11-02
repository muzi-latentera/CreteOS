//! Identity fence for an already-closed, previously confirmed idle client.
use std::collections::BTreeMap;
pub fn same_instances(original: &BTreeMap<u32, u64>, remaining: &BTreeMap<u32, u64>) -> bool {
    !original.is_empty() && remaining.iter().all(|(pid, created)| original.get(pid) == Some(created))
}
pub fn new_activity(line:&str)->bool{
    let line=line.split_whitespace().collect::<Vec<_>>().join(" ");
    if line.contains("onStreamingFirstFrame")||line.contains("gfnStart returned: true"){return true;}
    let state=line.split_once("NvCameraService streaming state change:").or_else(||if line.contains("gfn/StreamerManagerService"){line.split_once("Advancing to state:")}else{None});
    state.is_some_and(|(_,s)|!matches!(s.split_whitespace().next().unwrap_or(""),"Done"|"Idle"|"Stopped"))
}
#[cfg(test)] mod tests {
    use super::*;
    #[test] fn allows_only_remaining_original_instances() {
        let original=BTreeMap::from([(10,100),(11,101)]);
        assert!(same_instances(&original,&original));
        assert!(same_instances(&original,&BTreeMap::from([(11,101)])));
        assert!(same_instances(&original,&BTreeMap::new()));
    }
    #[test] fn refuses_new_processes_and_reused_process_ids() {
        let original=BTreeMap::from([(10,100)]);
        assert!(!same_instances(&original,&BTreeMap::from([(10,101)])));
        assert!(!same_instances(&original,&BTreeMap::from([(12,100)])));
        assert!(!same_instances(&BTreeMap::new(),&BTreeMap::new()));
    }
    #[test] fn new_loading_video_and_unknown_session_states_cancel_cleanup(){
        for line in ["NvCameraService streaming state change: Loading","gfn/StreamerManagerService Advancing to state: Streaming","streamingService onStreamingFirstFrame","gfnStart returned: true","NvCameraService streaming state change: FutureUnknownState"]{
            assert!(new_activity(line));
        }
    }
    #[test] fn idle_end_states_and_unrelated_logs_do_not_mean_new_play(){
        for line in ["NvCameraService streaming state change: Done","gfn/StreamerManagerService Advancing to state: Idle","NvCameraService streaming state change: Stopped","normal browser cleanup"]{assert!(!new_activity(line));}
    }
}

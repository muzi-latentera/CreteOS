//! Signals from the installed ASOBI renderer log. Client/transport startup is
//! not video readiness. Only a successful video-frame submission starts time.
#[derive(Clone,Copy,Debug,Default,PartialEq)]
pub enum Phase { #[default] Waiting, Playing, Attention, Ending, Ended }
#[derive(Default)]
pub struct State { pub phase:Phase, pub ever_played:bool, transport:bool }
impl State {
    pub fn observe(&mut self,line:&str){
        if line.contains("Takion connected, streaming active") {self.transport=true;}
        if self.transport && line.contains("render_frame[") && line.split_once("submitted, result=")
            .is_some_and(|(_,value)|matches!(value.trim(),"0"|"VK_SUCCESS"|"SUCCESS"|"Success")) {
            self.phase=Phase::Playing;self.ever_played=true;
        }
        if ["Stream error:","Cloud stream error:","Internet stream error:","StreamConn: console rejected the saved login PIN","StreamConn: console requested a login PIN but none is saved","StreamConn: Renderer init failed","Main: GPU device lost"].iter().any(|s|line.contains(s)){self.phase=Phase::Attention;}
        if line.contains("Overlay action:")&&line.contains("tearing down stream") || line.contains("Main: tearing down stream after error:"){self.phase=Phase::Ending;}
        if line.contains("Stream ended (render_frame returned false), returning to UI"){self.phase=Phase::Ended;self.transport=false;}
    }
}
#[cfg(test)]mod tests {
    use super::*;
    #[test]fn transport_and_window_are_not_video(){
        let mut s=State::default();for line in ["CloudLaunch: Starting cloud stream", "Main: Suspended ImGui, entering streaming mode","StreamConn: Cloud Takion connected, streaming active","StreamConn: Renderer initialized","render_frame[1]: submitting to graphics queue"]{s.observe(line);assert_eq!(s.phase,Phase::Waiting)}
        s.observe("render_frame[1]: submitted, result=-4");assert!(!s.ever_played);
        s.observe("render_frame[2]: submitted, result=0");assert_eq!(s.phase,Phase::Playing);assert!(s.ever_played);
    }
    #[test]fn failure_and_end_before_video_never_credit_playtime(){
        let mut s=State::default();s.observe("Cloud stream error: denied");assert_eq!(s.phase,Phase::Attention);s.observe("Stream ended (render_frame returned false), returning to UI");assert_eq!(s.phase,Phase::Ended);assert!(!s.ever_played);
    }
    #[test]fn frame_without_current_transport_is_ignored(){let mut s=State::default();s.observe("render_frame[1]: submitted, result=0");assert_eq!(s.phase,Phase::Waiting);}
    #[test]fn quit_precedes_home_return(){let mut s=State::default();s.observe("StreamConn: Takion connected, streaming active");s.observe("render_frame[1]: submitted, result=VK_SUCCESS");s.observe("Overlay action: Stop pressed - tearing down stream");assert_eq!(s.phase,Phase::Ending);s.observe("Stream ended (render_frame returned false), returning to UI");assert_eq!(s.phase,Phase::Ended);}
}

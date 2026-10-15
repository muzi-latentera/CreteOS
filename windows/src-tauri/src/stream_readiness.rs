//! Read only new bytes from NVIDIA's fixed local console log during a launch.
//! Never return or persist raw log/account data. A first video frame establishes
//! a live stream, not that a remote game's interactive menu has appeared.
use std::fs::File;
use std::io::{Read, Seek, SeekFrom};
use std::path::PathBuf;
use std::sync::Mutex;

#[derive(Debug, PartialEq, Eq)]
pub enum Signal { Waiting, FirstFrame, NeedsAttention }
fn signal(line: &str) -> Signal {
    if line.contains("got onStreamingFirstFrame event") { return Signal::FirstFrame; }
    // NVIDIA's Streaming state precedes decoded video. Its fixed native video
    // log emits frame statistics only after real video frames have arrived.
    if line.contains("[GeronimoStatsHandler]") && line.split_once("FrameStats:: n:")
        .and_then(|(_, tail)| tail.trim().split(',').next()).and_then(|count| count.parse::<u64>().ok()).is_some_and(|count| count > 0) { return Signal::FirstFrame; }
    let normalized = line.split_whitespace().collect::<Vec<_>>().join(" ");
    if ["Error", "TerminalError", "ResumableError"].iter().any(|state| normalized.contains(&format!("NvCameraService streaming state change: {state}"))) { return Signal::NeedsAttention; }
    Signal::Waiting
}
pub struct LogCursor { path: PathBuf, offset: u64, anchor: Vec<u8>, carry: String }
impl LogCursor {
    pub(crate) fn new(path: PathBuf) -> Self {
        let mut cursor = Self { path, offset: 0, anchor: Vec::new(), carry: String::new() };
        if let Ok(mut file) = File::open(&cursor.path) {
            cursor.offset = file.metadata().map(|m| m.len()).unwrap_or(0);
            cursor.anchor = Self::anchor(&mut file, cursor.offset);
        }
        cursor
    }
    fn anchor(file: &mut File, offset: u64) -> Vec<u8> {
        let size = offset.min(64) as usize;
        let mut bytes = vec![0; size];
        if file.seek(SeekFrom::Start(offset - size as u64)).is_err() || file.read_exact(&mut bytes).is_err() { return Vec::new(); }
        bytes
    }
    pub(crate) fn lines(&mut self) -> Vec<String> {
        let Ok(mut file) = File::open(&self.path) else { return Vec::new(); };
        let Ok(metadata) = file.metadata() else { return Vec::new(); };
        // NVIDIA can truncate/recreate this log when its client starts. Anchor
        // validation also detects a replacement that grew past the old length.
        if metadata.len() < self.offset || Self::anchor(&mut file, self.offset) != self.anchor {
            self.offset = 0; self.carry.clear();
        }
        if file.seek(SeekFrom::Start(self.offset)).is_err() { return Vec::new(); }
        let mut bytes = Vec::new();
        if (&mut file).take(262_144).read_to_end(&mut bytes).is_err() { return Vec::new(); }
        self.offset += bytes.len() as u64;
        self.anchor = Self::anchor(&mut file, self.offset);
        self.carry.push_str(&String::from_utf8_lossy(&bytes));
        let mut lines = Vec::new();
        while let Some(end) = self.carry.find('\n') {
            lines.push(self.carry.drain(..=end).collect::<String>());
        }
        if self.carry.len() > 4096 { self.carry.clear(); }
        lines
    }
    pub fn poll(&mut self) -> Signal {
        let mut result = Signal::Waiting;
        for line in self.lines() {
            let event = signal(&line);
            if event == Signal::NeedsAttention || (event != Signal::Waiting && result != Signal::NeedsAttention) { result = event; }
        }
        result
    }
}
pub struct StreamCursor { console: LogCursor, video: LogCursor }
impl StreamCursor {
    fn new() -> Self {
        let root = PathBuf::from(std::env::var_os("LOCALAPPDATA").unwrap_or_default()).join("NVIDIA Corporation/GeForceNOW");
        Self { console: LogCursor::new(root.join("console.log")), video: LogCursor::new(root.join("geronimo.log")) }
    }
    pub fn poll(&mut self) -> Signal {
        let console = self.console.poll(); let video = self.video.poll();
        if console == Signal::NeedsAttention || video == Signal::NeedsAttention { Signal::NeedsAttention }
        else if console == Signal::FirstFrame || video == Signal::FirstFrame { Signal::FirstFrame }
        else { Signal::Waiting }
    }
}
static CURSOR: Mutex<Option<StreamCursor>> = Mutex::new(None);
pub fn begin() { if let Ok(mut saved) = CURSOR.lock() { *saved = Some(StreamCursor::new()); } }
pub fn take() -> StreamCursor { CURSOR.lock().ok().and_then(|mut saved| saved.take()).unwrap_or_else(StreamCursor::new) }

#[cfg(test)] mod tests {
    use super::*;
    #[test] fn first_frame_is_distinct_from_starting_or_loading() {
        assert_eq!(signal("INFO streamingService gfnStart returned: true"), Signal::Waiting);
        assert_eq!(signal("NvCameraService streaming state change: Loading"), Signal::Waiting);
        assert_eq!(signal("INFO got onStreamingFirstFrame event {}"), Signal::FirstFrame);
        assert_eq!(signal("INFO NvCameraService  streaming state change:  Streaming"), Signal::Waiting);
        assert_eq!(signal("INFO [GeronimoStatsHandler] FrameStats:: n: 0, dropped: 0"), Signal::Waiting);
        assert_eq!(signal("INFO [GeronimoStatsHandler] FrameStats:: n: 1, dropped: 0"), Signal::FirstFrame);
        assert_eq!(signal("INFO NvCameraService streaming state change: TerminalError"), Signal::NeedsAttention);
    }
    #[test] fn ignores_old_session_and_handles_split_lines_and_replaced_log() {
        let path = std::env::temp_dir().join(format!("crete-stream-log-{}-{}.txt", std::process::id(), std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos()));
        std::fs::write(&path, "old got onStreamingFirstFrame event\n").unwrap();
        let mut cursor = LogCursor::new(path.clone());
        assert_eq!(cursor.poll(), Signal::Waiting);
        use std::io::Write;
        let mut file = std::fs::OpenOptions::new().append(true).open(&path).unwrap();
        file.write_all(b"new got onStreamingFirst").unwrap();
        assert_eq!(cursor.poll(), Signal::Waiting);
        file.write_all(b"Frame event {}\n").unwrap();
        assert_eq!(cursor.poll(), Signal::FirstFrame);
        drop(file);
        std::fs::write(&path, "new client log with a longer replacement prefix than the previous file got onStreamingFirstFrame event\n").unwrap();
        assert_eq!(cursor.poll(), Signal::FirstFrame);
        std::fs::remove_file(path).unwrap();
    }
}

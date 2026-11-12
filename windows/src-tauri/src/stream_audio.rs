use serde::{Deserialize, Serialize};
use std::{collections::BTreeSet, path::PathBuf, sync::{mpsc, Mutex}, time::Duration};
use tauri::Emitter;
use windows::core::{implement, GUID, HRESULT, Interface, PCWSTR, PWSTR};
use windows::Win32::{
    Devices::FunctionDiscovery::PKEY_Device_FriendlyName,
    Foundation::PROPERTYKEY,
    Media::Audio::{eCapture, eCommunications, eConsole, eMultimedia, eRender, EDataFlow, ERole,
        IMMDeviceEnumerator, IMMNotificationClient, IMMNotificationClient_Impl, MMDeviceEnumerator,
        DEVICE_STATE, DEVICE_STATE_ACTIVE},
    System::Com::{CoCreateInstance, CoInitializeEx, CoTaskMemFree, CoUninitialize, CLSCTX_ALL,
        COINIT_MULTITHREADED, STGM_READ, StructuredStorage::{PropVariantClear, PropVariantToStringAlloc}},
};

// MMDevice provides enumeration/notifications. Windows' native default setter
// lives in PolicyConfig. The ten preceding vtable slots are never invoked.
windows::core::imp::define_interface!(PolicyConfig, PolicyConfigVtbl, 0xf8679f50_850a_41cf_9c72_430f290290c8);
#[repr(C)]
pub struct PolicyConfigVtbl {
    base: windows::core::IUnknown_Vtbl,
    unused: [usize; 10],
    set_default_endpoint: unsafe extern "system" fn(*mut std::ffi::c_void, PCWSTR, ERole) -> HRESULT,
}
const POLICY_CONFIG_CLIENT: GUID = GUID::from_u128(0x870af99c_171d_4f9e_af0d_e63df40c2bc9);
const ROLES: [ERole; 3] = [eConsole, eMultimedia, eCommunications];
static SESSION: Mutex<bool> = Mutex::new(false);

struct Com;
impl Com {
    fn enter() -> Result<Self, String> {
        unsafe { CoInitializeEx(None, COINIT_MULTITHREADED).ok().map_err(|e| e.to_string())?; }
        Ok(Self)
    }
}
impl Drop for Com { fn drop(&mut self) { unsafe { CoUninitialize(); } } }

fn enumerator() -> windows::core::Result<IMMDeviceEnumerator> {
    unsafe { CoCreateInstance(&MMDeviceEnumerator, None, CLSCTX_ALL) }
}
fn allocated_string(raw: PWSTR) -> windows::core::Result<String> {
    unsafe {
        let value = raw.to_string();
        CoTaskMemFree(Some(raw.0.cast()));
        value.map_err(|_| windows::core::Error::from_hresult(HRESULT(0x80070057u32 as i32)))
    }
}

#[derive(Serialize)]
struct Device { id: String, name: String }
fn devices(api: &IMMDeviceEnumerator, flow: EDataFlow) -> windows::core::Result<Vec<Device>> {
    unsafe {
        let list = api.EnumAudioEndpoints(flow, DEVICE_STATE_ACTIVE)?;
        let mut result = Vec::new();
        for index in 0..list.GetCount()? {
            let endpoint = list.Item(index)?;
            let id = allocated_string(endpoint.GetId()?)?;
            let properties = endpoint.OpenPropertyStore(STGM_READ)?;
            let mut value = properties.GetValue(&PKEY_Device_FriendlyName)?;
            let name = PropVariantToStringAlloc(&value).and_then(allocated_string);
            let _ = PropVariantClear(&mut value);
            result.push(Device { id, name: name? });
        }
        result.sort_by(|a, b| a.name.cmp(&b.name));
        Ok(result)
    }
}

fn default_id(api: &IMMDeviceEnumerator, flow: EDataFlow, role: ERole) -> Option<String> {
    unsafe { allocated_string(api.GetDefaultAudioEndpoint(flow, role).ok()?.GetId().ok()?).ok() }
}

fn set_default(id: &str, role: ERole) -> windows::core::Result<()> {
    let wide = id.encode_utf16().chain(Some(0)).collect::<Vec<_>>();
    unsafe {
        let policy: PolicyConfig = CoCreateInstance(&POLICY_CONFIG_CLIENT, None, CLSCTX_ALL)?;
        (Interface::vtable(&policy).set_default_endpoint)(policy.as_raw(), PCWSTR(wide.as_ptr()), role).ok()
    }
}

#[tauri::command]
pub async fn get_stream_audio_devices() -> Result<serde_json::Value, String> {
    tauri::async_runtime::spawn_blocking(|| {
        let _com = Com::enter()?;
        let api = enumerator().map_err(|e| e.to_string())?;
        let output = devices(&api, eRender).map_err(|e| e.to_string())?;
        let input = devices(&api, eCapture).map_err(|e| e.to_string())?;
        Ok(serde_json::json!({"output": output, "input": input,
            "defaultOutput": display_output(&output).map(|device| device.id.clone()).or_else(|| default_id(&api, eRender, eConsole)),
            "defaultInput": default_id(&api, eCapture, eConsole)}))
    }).await.map_err(|e| e.to_string())?
}

#[derive(Deserialize)]
pub struct Choice { output: Option<String>, input: Option<String> }

#[derive(Serialize, Deserialize)]
struct Change { capture: bool, role: usize, prior: String, selected: String }

fn journal() -> PathBuf { super::liftoff_dir().join("stream-audio-session.json") }

fn restore(api: &IMMDeviceEnumerator, changes: &[Change]) -> bool {
    let mut complete = true;
    for change in changes {
        let flow = if change.capture { eCapture } else { eRender };
        let Some(role) = ROLES.get(change.role).copied() else { continue };
        if default_id(api, flow, role).as_deref() != Some(&change.selected) { continue; }
        let Ok(active) = devices(api, flow) else { complete = false; continue };
        let previous = active.iter().find(|d| d.id == change.prior).or_else(|| built_in(&active, change.capture));
        if let Some(device) = previous { complete &= set_default(&device.id, role).is_ok(); }
        else { complete = false; }
    }
    complete
}

fn recover(api: &IMMDeviceEnumerator) -> Result<(), String> {
    let path = journal();
    let bytes = match std::fs::read(&path) {
        Ok(bytes) => bytes,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(()),
        Err(error) => return Err(error.to_string()),
    };
    let changes: Vec<Change> = serde_json::from_slice(&bytes).map_err(|e| e.to_string())?;
    if !restore(api, &changes) { return Err("Windows could not restore the previous audio devices yet.".into()); }
    std::fs::remove_file(path).map_err(|e| e.to_string())
}

pub struct Session;
impl Drop for Session {
    fn drop(&mut self) {
        if let Ok(mut busy) = SESSION.lock() {
            if let Ok(_com) = Com::enter() {
                if let Ok(api) = enumerator() {
                    let _ = recover(&api);
                    let _ = dock_output(&api, false);
                }
            }
            *busy = false;
        }
    }
}

pub fn prepare(choice: Option<Choice>) -> Result<Option<Session>, String> {
    let choice = choice.unwrap_or(Choice { output: None, input: None });
    let overrides = choice.output.is_some() || choice.input.is_some();
    let mut busy = SESSION.lock().map_err(|_| "Audio settings are busy.")?;
    if *busy {
        return if overrides { Err("Another CreteOS launch is using an audio device override. End it and retry.".into()) } else { Ok(None) };
    }
    if !overrides {
        // Audio-less and undocked devices can still launch. The watcher also
        // applies the rule as Windows exposes the HDMI endpoint on connection.
        if let Ok(_com) = Com::enter() {
            if let Ok(api) = enumerator() {
                let _ = recover(&api);
                let _ = dock_output(&api, false);
            }
        }
        return Ok(None);
    }
    let _com = Com::enter()?;
    let api = enumerator().map_err(|e| e.to_string())?;
    recover(&api)?;
    let _ = dock_output(&api, false);
    let mut changes = Vec::new();
    for (capture, selected) in [(false, choice.output), (true, choice.input)] {
        let Some(selected) = selected else { continue };
        let flow = if capture { eCapture } else { eRender };
        if !devices(&api, flow).map_err(|e| e.to_string())?.iter().any(|d| d.id == selected) {
            return Err("The selected audio device is disconnected. Choose another device and retry.".into());
        }
        for (index, role) in ROLES.iter().enumerate() {
            let prior = default_id(&api, flow, *role).ok_or("Windows has no default audio device for this role.")?;
            if prior != selected { changes.push(Change { capture, role: index, prior, selected: selected.clone() }); }
        }
    }
    if changes.is_empty() {
        *busy = true;
        return Ok(Some(Session));
    }
    let path = journal();
    let temporary = path.with_extension("tmp");
    std::fs::write(&temporary, serde_json::to_vec(&changes).map_err(|e| e.to_string())?).map_err(|e| e.to_string())?;
    std::fs::rename(temporary, path).map_err(|e| e.to_string())?;
    for change in &changes {
        if let Err(error) = set_default(&change.selected, ROLES[change.role]) {
            let _ = recover(&api);
            return Err(format!("Windows could not select the audio device. {error}"));
        }
    }
    *busy = true;
    Ok(Some(Session))
}

fn built_in(devices: &[Device], capture: bool) -> Option<&Device> {
    let prefix = if capture { "microphone" } else { "speakers" };
    devices.iter().find(|device| {
        let name = device.name.to_ascii_lowercase();
        name.starts_with(prefix) && name.contains("realtek(r) audio")
    })
}

fn display_output(outputs: &[Device]) -> Option<&Device> {
    outputs.iter().find(|device| device.name.to_ascii_lowercase().contains("xreal"))
        .or_else(|| outputs.iter().find(|device| device.name.to_ascii_lowercase().contains("philips")))
}

fn dock_output(api: &IMMDeviceEnumerator, previously_docked: bool) -> windows::core::Result<bool> {
    let outputs = devices(api, eRender)?;
    let display = display_output(&outputs);
    let preferred = display.or_else(|| previously_docked.then(|| built_in(&outputs, false)).flatten());
    if let Some(device) = preferred {
        for role in ROLES {
            if default_id(api, eRender, role).as_deref() != Some(device.id.as_str()) {
                set_default(&device.id, role)?;
            }
        }
    }
    Ok(display.is_some())
}

fn arctis_defaults(api: &IMMDeviceEnumerator, seen: &mut BTreeSet<String>) -> windows::core::Result<()> {
    let mut connected = BTreeSet::new();
    for (capture, flow) in [(false, eRender), (true, eCapture)] {
        let active = devices(api, flow)?;
        for headset in active.iter().filter(|device| device.name.to_ascii_lowercase().contains("arctis nova 7")) {
            connected.insert(headset.id.clone());
            if seen.contains(&headset.id) { continue; }
            if let Some(device) = built_in(&active, capture) {
                for role in ROLES {
                    if default_id(api, flow, role).as_deref() == Some(&headset.id) {
                        set_default(&device.id, role)?;
                    }
                }
            }
        }
    }
    *seen = connected;
    Ok(())
}

#[implement(IMMNotificationClient)]
struct DeviceEvents(mpsc::Sender<()>);
impl IMMNotificationClient_Impl for DeviceEvents_Impl {
    fn OnDeviceStateChanged(&self, _: &PCWSTR, _: DEVICE_STATE) -> windows::core::Result<()> { let _ = self.0.send(()); Ok(()) }
    fn OnDeviceAdded(&self, _: &PCWSTR) -> windows::core::Result<()> { let _ = self.0.send(()); Ok(()) }
    fn OnDeviceRemoved(&self, _: &PCWSTR) -> windows::core::Result<()> { let _ = self.0.send(()); Ok(()) }
    fn OnDefaultDeviceChanged(&self, _: EDataFlow, _: ERole, _: &PCWSTR) -> windows::core::Result<()> { let _ = self.0.send(()); Ok(()) }
    fn OnPropertyValueChanged(&self, _: &PCWSTR, _: &PROPERTYKEY) -> windows::core::Result<()> { Ok(()) }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn prefers_xreal_then_philips_among_other_outputs() {
        let outputs = vec![
            Device { id: "headset".into(), name: "Headphones (Arctis Nova 7)".into() },
            Device { id: "built-in".into(), name: "Speakers (3- Realtek(R) Audio)".into() },
            Device { id: "tv".into(), name: "1 - Philips FTV (AMD High Definition Audio Device)".into() },
            Device { id: "glasses".into(), name: "XREAL 1S (AMD High Definition Audio Device)".into() },
        ];
        assert_eq!(display_output(&outputs).unwrap().id, "glasses");
        assert_eq!(display_output(&outputs[..3]).unwrap().id, "tv");
        assert!(display_output(&outputs[..2]).is_none());
        assert_eq!(built_in(&outputs[..2], false).unwrap().id, "built-in");
    }

    #[test]
    #[ignore = "requires an idle G1 with its Arctis Nova 7 plugged in"]
    fn g1_switches_both_roles_to_arctis_and_restores_realtek() {
        let _com = Com::enter().unwrap();
        let api = enumerator().unwrap();
        arctis_defaults(&api, &mut BTreeSet::new()).unwrap();
        let find = |capture, flow| {
            let active = devices(&api, flow).unwrap();
            let realtek = built_in(&active, capture).unwrap();
            let arctis = active.iter().find(|device| device.name.contains("Arctis Nova 7")).unwrap();
            for role in ROLES { assert_eq!(default_id(&api, flow, role), Some(realtek.id.clone())); }
            (realtek.id.clone(), arctis.id.clone())
        };
        let (speaker, headphones) = find(false, eRender);
        let (mic, headset_mic) = find(true, eCapture);
        let session = prepare(Some(Choice { output: Some(headphones.clone()), input: Some(headset_mic.clone()) })).unwrap().unwrap();
        for role in ROLES {
            assert_eq!(default_id(&api, eRender, role), Some(headphones.clone()));
            assert_eq!(default_id(&api, eCapture, role), Some(headset_mic.clone()));
        }
        drop(session);
        for role in ROLES {
            assert_eq!(default_id(&api, eRender, role), Some(speaker.clone()));
            assert_eq!(default_id(&api, eCapture, role), Some(mic.clone()));
        }
        assert!(!journal().exists());
    }
}

pub fn watch(app: tauri::AppHandle) {
    std::thread::spawn(move || {
        let Ok(_com) = Com::enter() else { return };
        let Ok(api) = enumerator() else { return };
        let (sender, receiver) = mpsc::channel();
        let events: IMMNotificationClient = DeviceEvents(sender).into();
        if unsafe { api.RegisterEndpointNotificationCallback(&events) }.is_err() { return; }
        let mut seen = BTreeSet::new();
        let mut docked = false;
        loop {
            if let Ok(busy) = SESSION.lock() {
                if !*busy {
                    let _ = recover(&api);
                    let _ = arctis_defaults(&api, &mut seen);
                    if let Ok(present) = dock_output(&api, docked) { docked = present; }
                }
            }
            let _ = app.emit("stream-audio-devices-changed", ());
            if receiver.recv().is_err() { break; }
            // Let Windows finish choosing defaults for the newly active endpoints.
            std::thread::sleep(Duration::from_millis(650));
            while receiver.try_recv().is_ok() {}
        }
        let _ = unsafe { api.UnregisterEndpointNotificationCallback(&events) };
    });
}

import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { IoMicOutline, IoVolumeMediumOutline } from "react-icons/io5";

export interface StreamAudioChoice { output: string | null; input: string | null }
interface Device { id: string; name: string }
interface Devices { output: Device[]; input: Device[]; defaultOutput: string | null; defaultInput: string | null }

export function useStreamAudioDevices(enabled: boolean) {
  const [devices, setDevices] = useState<Devices>();
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    if (!enabled) { setDevices(undefined); setFailed(false); return; }
    let active = true;
    let request = 0;
    const refresh = () => {
      const current = ++request;
      void invoke<Devices>("get_stream_audio_devices").then(result => {
        if (active && current === request) { setDevices(result); setFailed(!result); }
      }).catch(() => {
        if (active && current === request) { setDevices(undefined); setFailed(true); }
      });
    };
    refresh();
    const subscription = listen("stream-audio-devices-changed", refresh).catch(() => undefined);
    return () => { active = false; void subscription.then(unlisten => unlisten?.()); };
  }, [enabled]);
  return { devices, failed };
}

function defaultDevice(devices: Devices | undefined, flow: keyof StreamAudioChoice) {
  const id = flow === "output" ? devices?.defaultOutput : devices?.defaultInput;
  return devices?.[flow]?.find(device => device.id === id);
}

function shortDeviceName(name: string, flow: keyof StreamAudioChoice) {
  if (/philips\s+ftv/i.test(name)) return "Philips TV";
  if (/realtek\(r\) audio/i.test(name)) {
    if (/^speakers/i.test(name)) return "G1 speakers";
    if (/^microphone/i.test(name)) return "G1 mic";
  }
  const arctis = name.match(/Arctis (?:Nova )?7\w*/i);
  return arctis ? `${arctis[0]}${/\bchat\b/i.test(name) && flow === "output" ? " Chat" : ""}` : name;
}

export function StreamAudioSummary({ value, devices, failed }: { value: StreamAudioChoice; devices?: Devices; failed: boolean }) {
  return <div className="crete-stream-audio-summary">
    {(["output", "input"] as const).map(flow => {
      const Icon = flow === "output" ? IoVolumeMediumOutline : IoMicOutline;
      const label = flow === "output" ? "Audio output" : "Microphone";
      const id = value[flow];
      const device = id ? devices?.[flow]?.find(item => item.id === id) : defaultDevice(devices, flow);
      const fallback = failed ? "Unavailable" : !devices ? "Checking…" : id ? "Disconnected" : "Windows default";
      const fullName = device?.name ?? fallback;
      return <div className="crete-stream-audio-device" key={flow} title={`${label} · ${fullName}`}>
        <Icon aria-hidden="true" /><span><span className="crete-stream-audio-label">{label}: </span>{device ? shortDeviceName(fullName, flow) : fallback}</span>
      </div>;
    })}
  </div>;
}

export function StreamAudioFields({ value, onChange, moonlight, devices, failed }: {
  value: StreamAudioChoice; onChange: (choice: StreamAudioChoice) => void; moonlight?: boolean; devices?: Devices; failed: boolean;
}) {

  return <div className="crete-stream-fields">
    {(["output", "input"] as const).map(flow => {
      const label = flow === "output" ? "Audio output" : "Microphone";
      const available = devices?.[flow] ?? [];
      const defaultName = defaultDevice(devices, flow)?.name;
      const selected = value[flow];
      return <label key={flow}>{label}<select aria-label={label} value={selected ?? ""} disabled={!devices}
        onChange={event => onChange({ ...value, [flow]: event.target.value || null })}>
        <option value="">{defaultName ? `Windows default · ${defaultName}` : "Windows default"}</option>
        {selected && !available.some(device => device.id === selected) && <option value={selected} disabled>Disconnected device</option>}
        {available.map(device => <option key={device.id} value={device.id}>{device.name}</option>)}
      </select></label>;
    })}
    <p className="crete-stream-field-wide">{failed ? "Windows audio devices are unavailable." : moonlight ? "Moonlight uses the selected output. The microphone choice applies to local Windows apps." : "Audio selections apply during this session."}</p>
  </div>;
}

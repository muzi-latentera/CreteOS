import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";

export interface StreamAudioChoice { output: string | null; input: string | null }
interface Device { id: string; name: string }
interface Devices { output: Device[]; input: Device[]; defaultOutput: string | null; defaultInput: string | null }

export function StreamAudioFields({ value, onChange, moonlight }: {
  value: StreamAudioChoice; onChange: (choice: StreamAudioChoice) => void; moonlight?: boolean;
}) {
  const [devices, setDevices] = useState<Devices>();
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    let active = true;
    const refresh = () => void invoke<Devices>("get_stream_audio_devices").then(result => {
      if (active) { setDevices(result); setFailed(!result); }
    }).catch(() => { if (active) setFailed(true); });
    refresh();
    const subscription = listen("stream-audio-devices-changed", refresh).catch(() => undefined);
    return () => { active = false; void subscription.then(unlisten => unlisten?.()); };
  }, []);

  return <div className="crete-stream-fields">
    {(["output", "input"] as const).map(flow => {
      const label = flow === "output" ? "Audio output" : "Microphone";
      const available = devices?.[flow] ?? [];
      const defaultId = flow === "output" ? devices?.defaultOutput : devices?.defaultInput;
      const defaultName = available.find(device => device.id === defaultId)?.name;
      const selected = value[flow];
      return <label key={flow}>{label}<select aria-label={label} value={selected ?? ""} disabled={!devices}
        onChange={event => onChange({ ...value, [flow]: event.target.value || null })}>
        <option value="">{defaultName ? `Windows default · ${defaultName}` : "Windows default"}</option>
        {selected && !available.some(device => device.id === selected) && <option value={selected} disabled>Disconnected device</option>}
        {available.map(device => <option key={device.id} value={device.id}>{device.name}</option>)}
      </select></label>;
    })}
    <p className="crete-stream-field-wide">{failed ? "Windows audio devices are unavailable." : moonlight ? "Moonlight uses the selected output. The microphone choice applies to local Windows apps." : "Audio selections apply during this stream."}</p>
  </div>;
}

import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";

export interface LocalDisplayMode { width: number; height: number; hz: number }
interface DisplayModes { current: LocalDisplayMode; modes: LocalDisplayMode[] }

const key = ({ width, height, hz }: LocalDisplayMode) => `${width}x${height}@${hz}`;
const label = ({ width, height, hz }: LocalDisplayMode) => `${width} × ${height} · ${hz} Hz`;

export function LocalDisplayField({ value, onChange }: {
  value: LocalDisplayMode | null; onChange: (mode: LocalDisplayMode | null) => void;
}) {
  const [display, setDisplay] = useState<DisplayModes>();
  useEffect(() => {
    let active = true;
    invoke<DisplayModes>("get_local_display_modes").then(result => {
      if (active) setDisplay(result);
    }).catch(() => {});
    return () => { active = false; };
  }, []);
  return <label>Display mode<select aria-label="Display mode" value={value ? key(value) : ""} onChange={event => {
    onChange(display?.modes.find(mode => key(mode) === event.target.value) ?? null);
  }}>
    <option value="">{display ? `Current · ${label(display.current)}` : "Current display mode"}</option>
    {display?.modes.filter(mode => key(mode) !== key(display.current)).map(mode => <option key={key(mode)} value={key(mode)}>{label(mode)}</option>)}
  </select></label>;
}

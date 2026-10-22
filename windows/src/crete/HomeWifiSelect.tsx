import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";

export type WifiProvider = "moonlight" | "geforceNow" | "luna";
type Choice = "automatic" | "gaming" | "download" | "current";
export function HomeWifiSelect({ provider, saving }: { provider: WifiProvider; saving: (busy: boolean) => void }) {
  const [choice, setChoice] = useState<Choice>("automatic");
  const [error, setError] = useState("");
  const [ready, setReady] = useState(false);
  useEffect(() => {
    let active = true;
    setReady(false); setError("");
    void invoke<Record<WifiProvider, Choice>>("get_stream_wifi_preferences").then(prefs => {
      if (active) { setChoice(prefs?.[provider] ?? "automatic"); setReady(true); }
    }).catch(reason => { if (active) setError(String(reason)); });
    return () => { active = false; };
  }, [provider]);
  return <>
    <label>Home Wi-Fi<select aria-label="Home Wi-Fi" disabled={!ready} value={choice} onChange={event => {
      const next = event.target.value as Choice;
      saving(true); setReady(false); setError("");
      void invoke("set_stream_wifi_preference", { provider, choice: next }).then(() => setChoice(next))
        .catch(reason => setError(String(reason))).finally(() => { saving(false); setReady(true); });
    }}>
      <option value="automatic">Automatic · {provider === "moonlight" ? "Extra" : "Fibre E433"}</option>
      <option value="gaming">Hyperoptic Extra</option>
      <option value="download">Hyperoptic Fibre E433</option>
      <option value="current">Keep current connection</option>
    </select></label>
    <p>Remembered for {provider === "moonlight" ? "Moonlight" : provider === "luna" ? "Amazon Luna" : "GeForce NOW"}. Held until the session closes, then normal Wi-Fi rules resume. Other networks are left alone.</p>
    {error && <p role="alert">Could not save Wi-Fi preference: {error}</p>}
  </>;
}

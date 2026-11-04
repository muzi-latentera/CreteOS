import { useEffect, useRef, useState } from "react";
import { invoke } from "@tauri-apps/api/core";

export type WifiProvider = "moonlight" | "geforceNow" | "luna" | "ps5RemotePlay" | "playstationCloud";
type Choice = "automatic" | "gaming" | "download" | "current";
export function HomeWifiSelect({ provider, saving }: { provider: WifiProvider; saving: (busy: boolean) => void }) {
  const [choice, setChoice] = useState<Choice>("automatic");
  const [error, setError] = useState("");
  const [ready, setReady] = useState(false);
  const field = useRef<HTMLSelectElement>(null);
  const restoreFocus = useRef(false);
  useEffect(() => {
    if (ready && restoreFocus.current) {
      restoreFocus.current = false;
      if (document.activeElement === document.body) field.current?.focus({ preventScroll: true });
    }
  }, [ready]);
  const [wired,setWired]=useState(false);
  useEffect(()=>{
    let active=true;
    const refresh=()=>void invoke<{wired?:boolean}>("get_stream_network_status").then(status=>{if(active)setWired(status?.wired===true);}).catch(()=>{});
    refresh();const timer=window.setInterval(refresh,10_000);
    return()=>{active=false;window.clearInterval(timer);};
  },[]);
  useEffect(() => {
    let active = true;
    setReady(false); setError("");
    void invoke<Record<WifiProvider, Choice>>("get_stream_wifi_preferences").then(prefs => {
      if (active) { setChoice(prefs?.[provider] ?? "automatic"); setReady(true); }
    }).catch(reason => { if (active) setError(String(reason)); });
    return () => { active = false; };
  }, [provider]);
  return <>
    {wired && <p className="crete-stream-wired" role="status">Ethernet connected</p>}
    <label>{wired?"Wi-Fi fallback":"Home Wi-Fi"}<select ref={field} aria-label="Home Wi-Fi" disabled={!ready} value={choice} onChange={event => {
      const next = event.target.value as Choice;
      restoreFocus.current = true; saving(true); setReady(false); setError("");
      void invoke("set_stream_wifi_preference", { provider, choice: next }).then(() => setChoice(next))
        .catch(reason => setError(String(reason))).finally(() => { saving(false); setReady(true); });
    }}>
      <option value="automatic">Automatic · {wired?"Ethernet first · ":""}{provider === "moonlight" || provider === "ps5RemotePlay" ? "Extra" : "Fibre E433"}</option>
      <option value="gaming">Hyperoptic Extra</option>
      <option value="download">Hyperoptic Fibre E433</option>
      <option value="current">Keep current connection</option>
    </select></label>
    <p>{provider === "ps5RemotePlay" || provider === "playstationCloud" ? "Held until ASOBI closes." : "Held during play."} Other networks stay unchanged.</p>
    {error && <p role="alert">Could not save Wi-Fi preference: {error}</p>}
  </>;
}

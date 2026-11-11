import { useEffect, useRef, useState } from "react";
import { invoke } from "@tauri-apps/api/core";

export type WifiProvider = "moonlight" | "geforceNow" | "luna" | "ps5RemotePlay" | "playstationCloud" | "local";
type Choice = "automatic" | "gaming" | "download" | "current";
type HostChoice = "automatic" | "ethernet" | "wifi";
export function HomeWifiSelect({ provider, host, saving }: { provider: WifiProvider; host?: string; saving: (busy: boolean) => void }) {
  const [choice, setChoice] = useState<Choice>("automatic");
  const [hostChoice, setHostChoice] = useState<HostChoice>("automatic");
  const [error, setError] = useState("");
  const [ready, setReady] = useState(false);
  const restoreFocus = useRef<HTMLSelectElement | null>(null);
  useEffect(() => {
    if (ready && restoreFocus.current) {
      const field = restoreFocus.current;
      restoreFocus.current = null;
      if (document.activeElement === document.body) field.focus({ preventScroll: true });
    }
  }, [ready]);
  const [wired,setWired]=useState(false);
  const [e433,setE433]=useState(false);
  const bosgame = provider === "moonlight" && /^bosgame\.?$/i.test(host ?? "");
  useEffect(()=>{
    let active=true;
    const refresh=()=>void invoke<{wired?:boolean; e433?:boolean}>("get_stream_network_status", {host}).then(status=>{if(active){setWired(status?.wired===true);setE433(status?.e433===true);}}).catch(()=>{});
    refresh();const timer=window.setInterval(refresh,10_000);
    return()=>{active=false;window.clearInterval(timer);};
  },[host]);
  useEffect(() => {
    let active = true;
    setReady(false); setError("");
    void invoke<Record<WifiProvider, Choice> & {bosgameConnection?:HostChoice}>("get_stream_wifi_preferences").then(prefs => {
      if (active) { setChoice(prefs?.[provider] ?? "automatic"); setHostChoice(prefs?.bosgameConnection ?? "automatic"); setReady(true); }
    }).catch(reason => { if (active) setError(String(reason)); });
    return () => { active = false; };
  }, [provider]);
  const save = (field: HTMLSelectElement, command: string, args: Record<string, string>, onSaved: () => void) => {
    restoreFocus.current = field;
    saving(true); setReady(false); setError("");
    void invoke(command, args).then(onSaved).catch(reason => setError(String(reason)))
      .finally(() => { saving(false); setReady(true); });
  };
  const localLabel = bosgame ? "G1 connection" : wired ? "Wi-Fi fallback" : "Home connection";
  const hostAutomatic = !wired && (choice === "download" || (choice === "current" && e433)) ? "Wi-Fi" : "Ethernet";
  const preferredWifi = provider === "moonlight" || provider === "ps5RemotePlay" ? "Extra" : "Fibre E433";
  const automaticLabel = provider === "local" ? "Automatic · normal network rules"
    : bosgame && wired ? "Auto · Wired → Extra"
    : `Automatic · ${wired ? "Ethernet first · " : ""}${preferredWifi}`;
  const localField = <label>{bosgame ? "G1" : localLabel}<select aria-label={localLabel} disabled={!ready} value={choice} onChange={event => {
      const next = event.target.value as Choice;
      save(event.currentTarget, "set_stream_wifi_preference", {provider, choice:next}, () => setChoice(next));
    }}>
      <option value="automatic">{automaticLabel}</option>
      <option value="gaming">Hyperoptic Extra</option>
      <option value="download">Hyperoptic Fibre E433</option>
      <option value="current">Keep current connection</option>
    </select></label>;
  return <>
    {wired && <p className="crete-stream-wired" role="status">Ethernet connected</p>}
    {bosgame ? <>
      <h3 className="crete-stream-connection-heading">Home connection</h3>
      <div className="crete-stream-fields crete-stream-connection-fields">
        {localField}
        <label>Bosgame<select aria-label="Bosgame connection" disabled={!ready} value={hostChoice} onChange={event => {
          const next = event.target.value as HostChoice;
          save(event.currentTarget, "set_bosgame_connection_preference", {choice:next}, () => setHostChoice(next));
        }}>
          <option value="automatic">Automatic · {hostAutomatic}</option>
          <option value="ethernet">Ethernet</option>
          <option value="wifi">Wi-Fi · E433</option>
        </select></label>
      </div>
    </> : localField}
    {error && <p role="alert">Could not save connection preference: {error}</p>}
  </>;
}

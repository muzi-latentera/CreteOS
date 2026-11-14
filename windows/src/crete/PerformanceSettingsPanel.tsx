import { Fragment, useCallback, useEffect, useRef, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { useCreteText } from "./text";
import { useDialogNav } from "./useDialogNav";
import type { PowerProfile } from "./PerformanceDialog";

interface Status {
  backend: "onexconsole" | "none";
  running: boolean;
  defaultProfile: PowerProfile | null;
  fanCurves: [number, number][][];
  minTdp: number;
  maxTdp: number;
  games: { process: string; title: string; profile: PowerProfile }[];
  message: string;
}

const TEMPS = [45, 55, 65, 75, 85];
const FAN = ["Automatic", "Fan curve A", "Fan curve B"];

/** Settings → Performance: fan curves, the default power profile and per-game profiles. */
export function PerformanceSettingsPanel({ onClose }: { onClose: () => void }) {
  const text = useCreteText();
  const root = useRef<HTMLElement>(null);
  const [status, setStatus] = useState<Status>();
  const [curves, setCurves] = useState<number[][]>([]);
  const [dirty, setDirty] = useState<boolean[]>([false, false]);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const back = useCallback(() => { if (!busy) onClose(); }, [busy, onClose]);
  useDialogNav(root, back);

  const load = () => invoke<Status>("get_performance_status").then(value => {
    setStatus(value);
    setCurves(value.fanCurves.map(curve => curve.map(([, duty]) => Math.round(duty * 100))));
  }).catch(reason => setError(String(reason)));
  useEffect(() => { void load(); }, []);

  const nudge = (curve: number, point: number, delta: number) => {
    setCurves(all => all.map((values, c) => c !== curve ? values : values.map((duty, p) => {
      if (p !== point) return duty;
      const low = p > 0 ? values[p - 1] : 20;
      const high = p < values.length - 1 ? values[p + 1] : 100;
      return Math.min(high, Math.max(low, duty + delta));
    })));
    setDirty(flags => flags.map((flag, c) => c === curve ? true : flag));
  };
  const saveCurve = async (curve: number) => {
    setBusy(true); setError(""); setNotice("");
    try {
      await invoke("set_fan_curve", { curve, duties: curves[curve].map(duty => duty / 100) });
      setDirty(flags => flags.map((flag, c) => c === curve ? false : flag));
      setNotice(text("Fan curve saved in OneXConsole."));
    } catch (reason) { setError(String(reason)); } finally { setBusy(false); }
  };
  const apply = async () => {
    setBusy(true); setError("");
    try { setNotice(await invoke<string>("apply_power_settings")); } catch (reason) { setError(String(reason)); } finally { setBusy(false); }
  };
  const watts = (profile: PowerProfile) => profile.tdpBattery === profile.tdpCharger ? `${profile.tdpBattery} W` : `${profile.tdpBattery} W ${text("battery")} · ${profile.tdpCharger} W ${text("charger")}`;

  return <div className="crete-launch-layer" data-theme="crete" data-owns-input=""><div className="crete-stream-backdrop" onClick={event => { if (event.target === event.currentTarget) back(); }}>
    <section ref={root} role="dialog" aria-modal="true" aria-label={text("Performance")} className="crete-stream-settings crete-stream-dialog crete-manage-saves">
      <header className="crete-stream-heading">
        <span className="crete-stream-eyebrow">SETTINGS · PERFORMANCE</span>
        <h2>{text("Power & fans")}</h2>
        <p>{status?.backend === "onexconsole" ? text("Stored in OneXConsole, which applies them on this device.") : ""}</p>
      </header>
      <div className="crete-stream-body">
        {!status && !error && <p role="status">{text("Reading power settings…")}</p>}
        {status?.backend === "none" && <p>{text(status.message)}</p>}
        {status?.message && status.backend !== "none" && <p role="alert">{text(status.message)}</p>}
        {status?.backend === "onexconsole" && <>
          <h3 className="crete-stream-local-sound">{text("Default power")}</h3>
          <p>{status.defaultProfile ? watts(status.defaultProfile) : "—"} · {text(FAN[status.defaultProfile?.fanMode ?? 0])}. {text("Games without their own profile use this. Change it in OneXConsole.")}</p>
          {curves.map((values, curve) => <div key={curve}>
            <h3 className="crete-stream-local-sound">{text(curve === 0 ? "Fan curve A" : "Fan curve B")}</h3>
            <div className="crete-stream-tools crete-perf">
              {values.map((duty, point) => <div key={point} className="crete-perf-row" data-nav-row="">
                <span>{TEMPS[point]} °C</span>
                <button type="button" aria-label={`${TEMPS[point]} °C −`} onClick={() => nudge(curve, point, -5)}>−</button>
                <strong>{duty}%</strong>
                <button type="button" aria-label={`${TEMPS[point]} °C +`} onClick={() => nudge(curve, point, 5)}>+</button>
              </div>)}
            </div>
            {dirty[curve] && <div className="crete-save-actions"><button type="button" onClick={() => void saveCurve(curve)}>{text(curve === 0 ? "Save fan curve A" : "Save fan curve B")}</button></div>}
          </div>)}
          <h3 className="crete-stream-local-sound">{text("Games with their own profile")}</h3>
          {status.games.length === 0 ? <p>{text("None yet. Open a game → Options → Performance.")}</p>
            : <dl className="crete-save-comparison">{status.games.map(game => <Fragment key={game.process}><dt>{game.title || game.process}</dt><dd>{watts(game.profile)} · {text(FAN[game.profile.fanMode] ?? "Automatic")}{game.profile.frameCap ? ` · ${game.profile.frameCap} FPS` : ""}</dd></Fragment>)}</dl>}
          <p className="crete-save-hint">{text("OneXConsole loads changed profiles when it starts. Apply now restarts it (Windows asks for permission once).")}</p>
          {status.running && <div className="crete-save-actions"><button type="button" onClick={() => void apply()}>{text("Apply now (restarts OneXConsole)")}</button></div>}
        </>}
        {notice && <p role="status" className="crete-save-notice">{text(notice)}</p>}
        {error && <p role="alert">{text(error)}</p>}
      </div>
      <footer className="crete-stream-buttons">
        <button type="button" data-dialog-primary="" onClick={back}>{text("Done")}</button>
      </footer>
    </section>
  </div></div>;
}

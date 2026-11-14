import { useEffect, useRef, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { IoFlashOutline } from "react-icons/io5";
import { useCreteText } from "./text";
import type { App } from "../types";

export interface PowerProfile { tdpBattery: number; tdpCharger: number; fanMode: number; frameCap: number }
interface GamePerformance {
  process: string | null;
  candidates: string[];
  profile: PowerProfile | null;
  defaultProfile: PowerProfile | null;
  backend: "onexconsole" | "none";
  running: boolean;
}

const MIN_TDP = 4;
const MAX_TDP = 30;
const FAN_MODES = ["Automatic", "Fan curve A", "Fan curve B"];
const FRAME_CAPS = [0, 30, 40, 45, 60, 90, 120];
const fallback: PowerProfile = { tdpBattery: 10, tdpCharger: 18, fanMode: 0, frameCap: 0 };

/** Per-game power and fan profile, stored in the device's own power tool (OneXConsole on the G1). */
export function PerformanceDialog({ app, onClose }: { app: App; onClose: () => void }) {
  const text = useCreteText();
  const section = useRef<HTMLElement>(null);
  const [state, setState] = useState<GamePerformance>();
  const [process, setProcess] = useState<string | null>(null);
  const [enabled, setEnabled] = useState(false);
  const [profile, setProfile] = useState<PowerProfile>(fallback);
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [saved, setSaved] = useState("");

  useEffect(() => {
    let live = true;
    invoke<GamePerformance>("get_game_performance", { launchPath: app.launch_path ?? "", installDir: app.install_dir ?? null })
      .then(value => {
        if (!live) return;
        setState(value);
        setProcess(value.process);
        setEnabled(!!value.profile);
        setProfile(value.profile ?? value.defaultProfile ?? fallback);
      })
      .catch(reason => { if (live) setError(String(reason)); });
    return () => { live = false; };
  }, [app.id]);

  useEffect(() => {
    const frame = requestAnimationFrame(() => section.current?.querySelector<HTMLElement>(".crete-save-versions button, .crete-save-actions button, [data-launch-primary]:not(:disabled)")?.focus({ preventScroll: true }));
    return () => cancelAnimationFrame(frame);
  }, [!!state, saved]);

  const step = (key: "tdpBattery" | "tdpCharger", delta: number) =>
    setProfile(value => ({ ...value, [key]: Math.min(MAX_TDP, Math.max(MIN_TDP, value[key] + delta)) }));
  const cycle = (key: "fanMode" | "frameCap", delta: number) => setProfile(value => {
    const options = key === "fanMode" ? [0, 1, 2] : FRAME_CAPS;
    const index = Math.max(0, options.indexOf(value[key]));
    return { ...value, [key]: options[(index + delta + options.length) % options.length] };
  });
  const save = async () => {
    if (busy || !process) return;
    setBusy("Saving…"); setError(""); setSaved("");
    try {
      await invoke("set_game_performance", { launchPath: app.launch_path ?? "", process, title: app.name, profile: enabled ? profile : null });
      setSaved(state?.running ? "Saved in OneXConsole. It applies once OneXConsole restarts." : "Saved in OneXConsole.");
    } catch (reason) {
      setError(String(reason));
    } finally {
      setBusy("");
    }
  };
  const applyNow = async () => {
    if (busy) return;
    setBusy("Applying…");
    try { setSaved(await invoke<string>("apply_power_settings")); } catch (reason) { setError(String(reason)); } finally { setBusy(""); }
  };
  const guard = (run: () => void) => () => { if (!busy) run(); };
  const row = (label: string, value: string, minus: () => void, plus: () => void, disabled = false) => <div className="crete-perf-row">
    <span>{text(label)}</span>
    <button type="button" aria-label={`${text(label)} −`} disabled={disabled} onClick={guard(minus)}>−</button>
    <strong aria-live="polite">{value}</strong>
    <button type="button" aria-label={`${text(label)} +`} disabled={disabled} onClick={guard(plus)}>+</button>
  </div>;

  const unsupported = state?.backend === "none";
  return <div className="crete-stream-backdrop" data-owns-input="" onClick={event => { if (event.target === event.currentTarget && !busy) onClose(); }}>
    <section ref={section} role="dialog" aria-modal="true" aria-label={text("Performance")} className="crete-stream-settings crete-stream-dialog crete-manage-saves">
      <header className="crete-stream-heading">
        <span className="crete-stream-eyebrow">PERFORMANCE · ONEXCONSOLE</span>
        <h2>{text("Performance")}</h2>
        <p>{app.name}</p>
      </header>
      <div className="crete-stream-body">
        {!state && !error && <p role="status">{text("Reading power settings…")}</p>}
        {unsupported && <p>{text("No supported power tool was found. On OneXPlayer devices, install OneXConsole.")}</p>}
        {state && !unsupported && <>
          {process ? <p><IoFlashOutline aria-hidden="true" /> {text("Program")}: <strong>{process}.exe</strong></p>
            : <>
              <p>{text("Choose this game's program, or play it once from CreteOS so it is detected.")}</p>
              <div className="crete-save-versions" role="list">
                {state.candidates.map(name => <button key={name} type="button" role="listitem" onClick={guard(() => setProcess(name))}><span>{name}.exe</span></button>)}
              </div>
            </>}
          {process && <>
            <div className="crete-save-actions">
              <button type="button" aria-pressed={enabled} onClick={guard(() => setEnabled(value => !value))}>
                {text(enabled ? "Custom profile: On" : "Custom profile: Off (uses the default)")}
              </button>
            </div>
            <div className="crete-stream-tools crete-perf">
              {row("Power on battery", `${profile.tdpBattery} W`, () => step("tdpBattery", -1), () => step("tdpBattery", 1), !enabled)}
              {row("Power on charger", `${profile.tdpCharger} W`, () => step("tdpCharger", -1), () => step("tdpCharger", 1), !enabled)}
              {row("Fan", text(FAN_MODES[profile.fanMode] ?? "Automatic"), () => cycle("fanMode", -1), () => cycle("fanMode", 1), !enabled)}
              {row("Frame limit", profile.frameCap ? `${profile.frameCap} FPS` : text("Off"), () => cycle("frameCap", -1), () => cycle("frameCap", 1), !enabled)}
            </div>
            <p className="crete-save-hint">{text("Fan curves A and B are edited in Settings → Performance. Power limits are kept between {{min}} and {{max}} W.", { min: MIN_TDP, max: MAX_TDP })}</p>
          </>}
        </>}
        {busy && <p role="status">{text(busy)}</p>}
        {saved && <p role="status" className="crete-save-notice">{text(saved)}</p>}
        {saved && state?.running && <div className="crete-save-actions"><button type="button" onClick={guard(() => void applyNow())}>{text("Apply now (restarts OneXConsole)")}</button></div>}
        {error && <p role="alert">{text(error)}</p>}
      </div>
      <footer className="crete-stream-buttons">
        <button type="button" onClick={guard(onClose)}>{text("Back")}</button>
        {process && !unsupported && <button data-launch-primary type="button" aria-disabled={!!busy} onClick={guard(() => void save())}>{text("Save")}</button>}
      </footer>
    </section>
  </div>;
}

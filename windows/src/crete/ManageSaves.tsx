import { useEffect, useRef, useState, type MutableRefObject } from "react";
import { invoke } from "@tauri-apps/api/core";
import { IoCloudDoneOutline, IoCloudOfflineOutline, IoSaveOutline, IoTimeOutline } from "react-icons/io5";
import { useCreteText } from "./text";
import type { App } from "../types";

export interface SaveVersion { name: string; when: string; locked?: boolean; comment?: string | null }
export interface SaveEvent { when: string; message: string; cloud: string }
export interface SaveComparison {
  known?: boolean;
  active: number | null;
  versions: SaveVersion[];
  cloud: number | null;
  scanOk: boolean;
  cloudStatus: string;
  cloudDetail?: string | null;
  verdict?: "match" | "differ" | "unknown" | "none";
  message: string;
  lastEvent?: SaveEvent | null;
}
interface SavePreview { created: string; replace: number; add: number; same: number }
interface SaveActionResult { message: string; cloud?: string; cloudMessage?: string }

/** Lets the launch menu's Back/Escape step out of a sub-view, and keeps it open during work. */
export interface ManageSavesControl { back: () => boolean; busy: boolean }

const CHECKPOINT = "CreteOS: before restore";
const VERDICT: Record<NonNullable<SaveComparison["verdict"]>, string> = {
  match: "Saves match",
  differ: "Saves differ",
  unknown: "Couldn’t check",
  none: "No saves found yet",
};
const CLOUD: Record<string, string> = {
  synced: "Google Drive is up to date.",
  uploaded: "Uploaded to Google Drive.",
  downloaded: "Downloaded newer versions from Google Drive.",
  conflict: "Google Drive and this device each have versions the other lacks. Nothing was overwritten.",
  off: "Google Drive sync is off.",
  "not-needed": "",
  unavailable: "Google Drive could not be reached. CreteOS will try again after the next backup.",
};

const stamp = (value: number | string) => new Date(typeof value === "number" ? value * 1000 : value)
  .toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
const plural = (count: number, one: string, many: string) => `${count} ${count === 1 ? one : many}`;

export function ManageSaves({ app, onClose, control }: { app: App; onClose: () => void; control: MutableRefObject<ManageSavesControl | null> }) {
  const text = useCreteText();
  const remote = app.source === "lutris";
  const device = remote ? "Bosgame" : "this PC";
  const section = useRef<HTMLElement>(null);
  const request = useRef(0);
  const [view, setView] = useState<"summary" | "versions" | "confirm">("summary");
  const [result, setResult] = useState<SaveComparison>();
  const [checking, setChecking] = useState(false);
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [chosen, setChosen] = useState<SaveVersion>();
  const [preview, setPreview] = useState<SavePreview>();
  const args = { id: app.id, name: app.name, appType: app.app_type ?? "game", source: app.source ?? "", path: app.launch_path ?? "" };

  const check = async () => {
    const id = ++request.current;
    setChecking(true); setError("");
    try {
      const value = await invoke<SaveComparison>("compare_game_saves", args);
      if (id === request.current) setResult(value);
    } catch (reason) {
      if (id === request.current) setError(String(reason));
    } finally {
      if (id === request.current) setChecking(false);
    }
  };
  const act = async (action: "backup" | "sync" | "restore", label: string, version?: string) => {
    if (busy) return;
    setBusy(label); setError(""); setNotice("");
    try {
      const value = await invoke<SaveActionResult>("manage_game_save", { request: { ...args, action, version } });
      const cloud = value.cloudMessage ?? (value.cloud && action !== "sync" ? CLOUD[value.cloud] : "");
      setNotice([value.message, cloud].filter(Boolean).join(" "));
      setView("summary"); setChosen(undefined); setPreview(undefined);
      void check();
    } catch (reason) {
      setError(String(reason));
    } finally {
      setBusy("");
    }
  };
  const choose = async (version: SaveVersion) => {
    setChosen(version); setPreview(undefined); setError(""); setView("confirm");
    const id = ++request.current;
    try {
      const value = await invoke<SavePreview>("manage_game_save", { request: { ...args, action: "preview", version: version.name } });
      if (id === request.current) setPreview(value);
    } catch (reason) {
      if (id === request.current) setError(String(reason));
    }
  };

  useEffect(() => { void check(); return () => { request.current++; }; }, [app.id]);
  control.current = {
    busy: !!busy,
    back: () => {
      if (busy) return true;
      if (view === "confirm") { setView("versions"); setError(""); return true; }
      if (view === "versions") { setView("summary"); return true; }
      return false;
    },
  };
  useEffect(() => () => { control.current = null; }, [control]);
  // Each view starts on its primary control so the controller never lands nowhere.
  useEffect(() => {
    const frame = requestAnimationFrame(() => {
      const root = section.current;
      const target = root?.querySelector<HTMLElement>("[data-launch-primary]:not(:disabled)")
        ?? root?.querySelector<HTMLElement>(".crete-save-versions button, .crete-save-actions button:not(:disabled)")
        ?? root?.querySelector<HTMLElement>(".crete-stream-buttons button");
      target?.focus({ preventScroll: true });
    });
    return () => cancelAnimationFrame(frame);
  }, [view, !!preview]);

  const versions = result?.versions ?? [];
  const verdict = checking && !result ? undefined : result?.verdict ?? (result ? "unknown" : undefined);
  const drive = !result ? "" : result.cloudStatus === "off" ? "Sync off"
    : result.cloud ? stamp(result.cloud) : result.cloudStatus === "missing" ? "No backup on Google Drive" : "Couldn’t reach Google Drive";
  const versionLabel = (version: SaveVersion, index: number) => version.comment === CHECKPOINT
    ? "Before restore" : index === 0 ? "Latest backup" : "Backup";
  const guard = (run: () => void) => () => { if (!busy) run(); };

  return <div className="crete-stream-backdrop" data-owns-input="" onClick={event => { if (event.target === event.currentTarget && !busy) onClose(); }}>
    <section ref={section} role="dialog" aria-modal="true" aria-label={text("Manage saves")} aria-busy={!!busy || checking} className="crete-stream-settings crete-stream-dialog crete-manage-saves">
      <header className="crete-stream-heading">
        <span className="crete-stream-eyebrow">{remote ? "SAVES ON BOSGAME" : "SAVES ON THIS PC"}</span>
        <h2>{text(view === "summary" ? "Manage saves" : view === "versions" ? "Restore a version" : "Restore this version?")}</h2>
        <p>{app.name}</p>
      </header>
      <div className="crete-stream-body">
        {view === "summary" && <>
          {verdict && <p className="crete-save-verdict" data-verdict={verdict}>{text(VERDICT[verdict])}</p>}
          {result && <p>{text(result.message)}</p>}
          {checking && <p role="status">{text(result ? "Checking again…" : "Checking the active save and backups…")}</p>}
          {result && <dl className="crete-save-comparison">
            <dt>{text("Save last modified")}</dt>
            <dd>{result.active ? stamp(result.active) : result.scanOk ? text("No save file found") : text("Couldn’t check")}</dd>
            <dt>{text("Latest backup created")}</dt>
            <dd>{versions.length ? stamp(versions[0].when) : text("No backup yet")}</dd>
            {result.cloudStatus === "store" ? <>
              <dt>{text("Cloud save")}</dt>
              <dd>{text(`Kept by ${app.source === "epic" ? "Epic" : app.source === "xbox" ? "Xbox" : app.source === "steam" ? "Steam" : "the store"}`)}</dd>
            </> : <>
              <dt>{text("Google Drive backup created")}</dt>
              <dd>{text(drive)}</dd>
            </>}
            <dt>{text("Versions kept")}</dt>
            <dd>{versions.length}</dd>
          </dl>}
          {result && !result.cloud && result.cloudStatus === "unavailable" && result.cloudDetail && <p className="crete-save-hint" data-cloud-detail="">{text("Google Drive")}: {result.cloudDetail}</p>}
          {result?.lastEvent && <p className="crete-save-event"><IoTimeOutline aria-hidden="true" /> {text("Last backup activity")}: {stamp(result.lastEvent.when)} · {text(result.lastEvent.message)}{CLOUD[result.lastEvent.cloud] ? ` ${text(CLOUD[result.lastEvent.cloud])}` : ""}</p>}
          {result && result.verdict !== "none" && <p className="crete-save-hint">{text("Dates show when save files were last modified and when each backup was created. A later backup date does not mean newer game progress.")}</p>}
          {!result && !checking && !error && <p>{text(remote ? "Lutris backs up saves on Bosgame after the game closes." : "CreteOS backs up saves after the game closes.")}</p>}
          {busy && <p role="status">{text(busy)}</p>}
          {notice && <p role="status" className="crete-save-notice"><IoCloudDoneOutline aria-hidden="true" /> {text(notice)}</p>}
          {error && <p role="alert">{text(error)}</p>}
          <div className="crete-save-actions">
            <button type="button" aria-disabled={!!busy || !versions.length} disabled={!versions.length} onClick={guard(() => setView("versions"))}><IoSaveOutline aria-hidden="true" /> {text("Restore a version")}</button>
            {result && result.cloudStatus !== "off" && result.cloudStatus !== "store" && <button type="button" aria-disabled={!!busy} onClick={guard(() => void act("sync", "Syncing with Google Drive…"))}><IoCloudOfflineOutline aria-hidden="true" /> {text("Sync with Google Drive")}</button>}
            <button type="button" aria-disabled={!!busy || checking} onClick={guard(() => { if (!checking) { setNotice(""); void check(); } })}>{text("Check again")}</button>
          </div>
        </>}
        {view === "versions" && <>
          <p>{text("Choose a version to preview. Nothing changes until you confirm.")}</p>
          <div className="crete-save-versions" role="list">
            {versions.map((version, index) => <button key={version.name} type="button" role="listitem" onClick={guard(() => void choose(version))}>
              <span>{stamp(version.when)}</span><small>{text(versionLabel(version, index))}</small>
            </button>)}
          </div>
        </>}
        {view === "confirm" && chosen && <>
          <dl className="crete-save-comparison">
            <dt>{text("Backup created")}</dt><dd>{stamp(chosen.when)}</dd>
            <dt>{text("Current save last modified")}</dt><dd>{result?.active ? stamp(result.active) : text("No save file found")}</dd>
          </dl>
          {!preview && !error && <p role="status">{text("Checking what this version would replace…")}</p>}
          {preview && <p>{preview.replace + preview.add === 0
            ? text("This version already matches the save on {{device}}.", { device })
            : `${text("Restoring replaces")} ${plural(preview.replace, "save file", "save files")}${preview.add ? ` ${text("and adds")} ${preview.add}` : ""} ${text("on {{device}}.", { device })}${preview.same ? ` ${plural(preview.same, "file already matches", "files already match")}.` : ""}`}</p>}
          <p className="crete-save-hint">{text("Your current save is kept first as a locked “Before restore” version, so you can undo this.")}</p>
          {busy && <p role="status">{text(busy)}</p>}
          {error && <p role="alert">{text(error)}</p>}
        </>}
      </div>
      <footer className="crete-stream-buttons">
        <button type="button" aria-disabled={!!busy} onClick={guard(() => { if (!control.current?.back()) onClose(); })}>{text("Back")}</button>
        {view === "summary" && <button data-launch-primary type="button" aria-disabled={!!busy} onClick={guard(() => void act("backup", "Backing up…"))}>{text(busy === "Backing up…" ? "Backing up…" : "Back up now")}</button>}
        {view === "confirm" && <button data-launch-primary type="button" disabled={!preview || preview.replace + preview.add === 0}
          aria-disabled={!!busy} onClick={guard(() => chosen && void act("restore", "Protecting your current save, then restoring…", chosen.name))}>{text("Restore this version")}</button>}
      </footer>
    </section>
  </div>;
}

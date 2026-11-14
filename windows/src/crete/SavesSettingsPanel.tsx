import { Fragment, useCallback, useEffect, useRef, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { useCreteText } from "./text";
import { useDialogNav } from "./useDialogNav";
import type { SaveEvent } from "./ManageSaves";

interface Overview { remote: string | null; folder: string | null; synchronize: boolean; events: [string, SaveEvent][]; error: string | null }

const CLOUD: Record<string, string> = {
  synced: "Drive up to date", uploaded: "Uploaded", downloaded: "Downloaded from Drive", conflict: "Drive conflict, nothing overwritten",
  off: "Drive sync off", "not-needed": "", unavailable: "Drive unreachable",
};

/** Settings → Game saves: where backups go, recent activity and how it works. */
export function SavesSettingsPanel({ onClose }: { onClose: () => void }) {
  const text = useCreteText();
  const root = useRef<HTMLElement>(null);
  const [overview, setOverview] = useState<Overview>();
  const back = useCallback(() => onClose(), [onClose]);
  useDialogNav(root, back);
  useEffect(() => { invoke<Overview>("get_save_overview").then(setOverview).catch(() => setOverview({ remote: null, folder: null, synchronize: false, events: [], error: "Save tools are unavailable." })); }, []);
  const stamp = (when: string) => new Date(when).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });

  return <div className="crete-launch-layer" data-theme="crete" data-owns-input=""><div className="crete-stream-backdrop" onClick={event => { if (event.target === event.currentTarget) back(); }}>
    <section ref={root} role="dialog" aria-modal="true" aria-label={text("Game saves")} className="crete-stream-settings crete-stream-dialog crete-manage-saves">
      <header className="crete-stream-heading">
        <span className="crete-stream-eyebrow">SETTINGS · GAME SAVES</span>
        <h2>{text("Game saves")}</h2>
        <p>{text("Versioned backups with Google Drive sync, run by Ludusavi behind the scenes.")}</p>
      </header>
      <div className="crete-stream-body">
        {!overview && <p role="status">{text("Reading save settings…")}</p>}
        {overview && <>
          <dl className="crete-save-comparison">
            <dt>{text("Google Drive folder")}</dt><dd>{overview.folder ?? "—"}</dd>
            <dt>{text("Drive account")}</dt><dd>{overview.remote ?? text("Not connected")}</dd>
            <dt>{text("Sync after backups")}</dt><dd>{text(overview.synchronize ? "On" : "Off")}</dd>
            <dt>{text("Versions kept per game")}</dt><dd>10</dd>
          </dl>
          {overview.error && <p role="alert">{text(overview.error)}</p>}
          <p className="crete-save-hint">{text("Standalone Windows games and Bosgame's Lutris games back up when they close. Steam, Epic, EA and Xbox keep their own cloud saves. Open a game → Options → Manage saves to back up, check or restore a version.")}</p>
          <h3 className="crete-stream-local-sound">{text("Recent activity on this PC")}</h3>
          {overview.events.length === 0 ? <p>{text("No backups yet on this PC.")}</p>
            : <dl className="crete-save-comparison">{overview.events.map(([title, event]) => <Fragment key={title}>
                <dt>{title}</dt><dd>{stamp(event.when)} · {text(event.message)}{CLOUD[event.cloud] ? ` · ${text(CLOUD[event.cloud])}` : ""}</dd>
              </Fragment>)}</dl>}
          <p className="crete-save-hint">{text("If Manage saves says Google is limiting rclone's shared app, set up your own Google client ID (setup guide → Game save recovery).")}</p>
        </>}
      </div>
      <footer className="crete-stream-buttons">
        <button type="button" data-dialog-primary="" onClick={back}>{text("Done")}</button>
      </footer>
    </section>
  </div></div>;
}

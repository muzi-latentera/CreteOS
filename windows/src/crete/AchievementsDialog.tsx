import { useEffect, useRef } from "react";
import { IoTrophyOutline, IoLockClosedOutline } from "react-icons/io5";
import { useCreteText } from "./text";
import { useStoreProgress, type StoreProgress } from "./useStoreProgress";
import type { App } from "../types";

type Achievement = NonNullable<StoreProgress["achievements"]>[number];

/** Unlocked first (newest first), then locked in the game's own order. */
export function orderAchievements(list: Achievement[]) {
  const unlocked = list.filter(a => a.unlocked).sort((a, b) => (b.unlocked_at ?? 0) - (a.unlocked_at ?? 0));
  return [...unlocked, ...list.filter(a => !a.unlocked)];
}

/** Every achievement for a Steam, Epic, GOG or Bosgame game. */
export function AchievementsDialog({ app, onClose }: { app: App; onClose: () => void }) {
  const text = useCreteText();
  const progress = useStoreProgress(app);
  const section = useRef<HTMLElement>(null);
  const list = orderAchievements(progress?.achievements ?? []);
  useEffect(() => {
    const frame = requestAnimationFrame(() => section.current?.querySelector<HTMLElement>(".crete-achievements button, footer button")?.focus({ preventScroll: true }));
    return () => cancelAnimationFrame(frame);
  }, [list.length > 0]);
  const earned = progress?.earned ?? list.filter(a => a.unlocked).length;
  const total = progress?.total ?? list.length;
  return <div className="crete-stream-backdrop" data-owns-input="" onClick={event => { if (event.target === event.currentTarget) onClose(); }}>
    <section ref={section} role="dialog" aria-modal="true" aria-label={text("Achievements")} className="crete-stream-settings crete-stream-dialog crete-manage-saves">
      <header className="crete-stream-heading">
        <span className="crete-stream-eyebrow">ACHIEVEMENTS</span>
        <h2>{app.name}</h2>
        {total > 0 && <p>{`${earned} of ${total} unlocked`}</p>}
      </header>
      <div className="crete-stream-body">
        {total > 0 && <div className="crete-achievement-bar" aria-hidden="true"><span style={{ width: `${Math.round(earned / total * 100)}%` }} /></div>}
        {!progress && <p role="status">{text("Reading achievements…")}</p>}
        {progress && !list.length && <p>{text(app.source === "steam" ? "No achievements yet. Steam lists them here after the game has been played on this PC." : "This game has no achievements to show.")}</p>}
        {list.length > 0 && <div className="crete-achievements" role="list">
          {list.map(a => <button key={a.id} type="button" role="listitem" data-unlocked={a.unlocked}>
            {a.icon ? <img src={a.icon} alt="" loading="lazy" /> : a.unlocked ? <IoTrophyOutline aria-hidden="true" /> : <IoLockClosedOutline aria-hidden="true" />}
            <span><strong>{a.name}</strong>{a.description && <small>{a.description}</small>}</span>
            <em>{a.unlocked ? (a.unlocked_at ? new Date(a.unlocked_at * 1000).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" }) : "✓") : ""}</em>
          </button>)}
        </div>}
      </div>
      <footer className="crete-stream-buttons"><button type="button" onClick={onClose}>{text("Back")}</button></footer>
    </section>
  </div>;
}

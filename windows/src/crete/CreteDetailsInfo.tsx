import type { App, StoreMetadata } from "../types";
import { lastPlayed, playtime, sourceLabel } from "./model";
import { useCreteText } from "./text";
import { useXboxProgress } from "./useXboxProgress";

export function CreteDetailsInfo({ app, metadata, lastPlayedAt, minutes, installed, stats }: { app: App; metadata: StoreMetadata | null; lastPlayedAt?: number; minutes?: number; installed: boolean; stats?: { label: string; value: string }[] }) {
  const text = useCreteText();
  const progress = useXboxProgress(app.name, app.source === "xbox");
  const progressStats = stats?.filter(item => !(app.source === "xbox" && item.label === text("Playtime")));
  if (progressStats && app.source === "xbox") progressStats.push({ label: text("Playtime"), value: playtime(progress?.minutes ?? minutes) });
  return <aside className="crete-details-info" aria-label={text("Game information")}>
    <section><h3>{text("Game info")}</h3><dl><dt>{text("Developer")}</dt><dd>{metadata?.developers.join(", ") || "—"}</dd>
      <dt>{text("Publisher")}</dt><dd>{metadata?.publishers.join(", ") || "—"}</dd>
      {metadata?.releaseDate && <><dt>{text("Released")}</dt><dd>{metadata.releaseDate}</dd></>}
    </dl></section>
    <section><h3>{text("Progress")}</h3><dl>{progressStats ? progressStats.map(item => <div className="crete-detail-stat" key={item.label}><dt>{item.label}</dt><dd>{item.value}</dd></div>) : <><dt>{text("Last played")}</dt><dd>{lastPlayed(lastPlayedAt)}</dd><dt>{text("Playtime")}</dt><dd>{playtime(progress?.minutes ?? minutes)}</dd></>}
      {progress?.earned != null && <><dt>{text("Achievements")}</dt><dd>{progress.total ? `${progress.earned} / ${progress.total}` : text("Unlocked achievements", { count: progress.earned })}</dd></>}
      {progress?.gamerscore != null && <><dt>Gamerscore</dt><dd>{progress.gamerscore}{progress.totalGamerscore ? ` / ${progress.totalGamerscore}` : ""}</dd></>}
    </dl></section>
    <section><h3>{text("Launch")}</h3><dl><dt>{text("Provider")}</dt><dd>{sourceLabel(app.source)}</dd>
      <dt>{text("Availability")}</dt><dd>{text(app.source === "cloud" ? "Cloud" : installed ? "Local" : "Not installed")}</dd>
      {(app.steam_appid || app.xbox_product_id) && <><dt>{text("App ID")}</dt><dd>{app.steam_appid || app.xbox_product_id}</dd></>}
    </dl></section>
  </aside>;
}

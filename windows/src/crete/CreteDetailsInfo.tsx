import type { App, StoreMetadata } from "../types";
import { lastPlayed, playtime, epochMs, sourceLabel } from "./model";
import { useCreteText } from "./text";
import { useXboxProgress } from "./useXboxProgress";
import { useStoreProgress } from "./useStoreProgress";

export function CreteDetailsInfo({ app, metadata, lastPlayedAt, minutes, installed, stats }: { app: App; metadata: StoreMetadata | null; lastPlayedAt?: number; minutes?: number; installed: boolean; stats?: { label: string; value: string }[] }) {
  const text = useCreteText();
  const xboxProgress = useXboxProgress(app.name, app.source === "xbox");
  const accountProgress = useStoreProgress(app);
  const progress = app.source === "xbox" ? xboxProgress : accountProgress;
  const last = Math.max(epochMs(lastPlayedAt) ?? 0, epochMs(accountProgress?.last_played) ?? 0) || undefined;
  const lastLabel = !last && ["epic","gog"].includes(app.source ?? "") ? "—" : lastPlayed(last);
  const accountSource = ["xbox", "epic", "gog"].includes(app.source ?? "");
  const progressStats = stats?.filter(item => !(accountSource && [text("Playtime"), text("Last played")].includes(item.label)));
  if (progressStats && accountSource) progressStats.push({ label: text("Last played"), value: lastLabel }, { label: text("Playtime"), value: playtime(progress?.minutes ?? minutes) });
  const earned = [...(accountProgress?.achievements ?? [])].filter(a => a.unlocked).sort((a,b) => (b.unlocked_at ?? 0) - (a.unlocked_at ?? 0)).slice(0,4);
  return <aside className="crete-details-info" aria-label={text("Game information")}>
    <section><h3>{text("Game info")}</h3><dl><dt>{text("Developer")}</dt><dd>{metadata?.developers.join(", ") || "—"}</dd>
      <dt>{text("Publisher")}</dt><dd>{metadata?.publishers.join(", ") || "—"}</dd>
      {metadata?.releaseDate && <><dt>{text("Released")}</dt><dd>{metadata.releaseDate}</dd></>}
    </dl></section>
    <section><h3>{text("Progress")}</h3><dl>{progressStats ? progressStats.map(item => <div className="crete-detail-stat" key={item.label}><dt>{item.label}</dt><dd>{item.value}</dd></div>) : <><dt>{text("Last played")}</dt><dd>{lastLabel}</dd><dt>{text("Playtime")}</dt><dd>{playtime(progress?.minutes ?? minutes)}</dd></>}
      {progress?.earned != null && <><dt>{text("Achievements")}</dt><dd>{progress.total ? `${progress.earned} / ${progress.total}` : text("Unlocked achievements", { count: progress.earned })}</dd></>}
      {xboxProgress?.gamerscore != null && <><dt>Gamerscore</dt><dd>{xboxProgress.gamerscore}{xboxProgress.totalGamerscore ? ` / ${xboxProgress.totalGamerscore}` : ""}</dd></>}
      {accountProgress?.xp != null && <><dt>XP</dt><dd>{accountProgress.xp}{accountProgress.total_xp ? ` / ${accountProgress.total_xp}` : ""}</dd></>}
    </dl></section>
    {earned.length > 0 && <section><h3>{text("Recent achievements")}</h3><dl>{earned.map(a => <div className="crete-detail-stat" key={a.id}><dt title={a.description}>{a.name}</dt><dd>{a.unlocked_at ? new Date(a.unlocked_at * 1000).toLocaleDateString() : "✓"}</dd></div>)}</dl></section>}
    <section><h3>{text("Launch")}</h3><dl><dt>{text("Provider")}</dt><dd>{sourceLabel(app.source)}</dd>
      <dt>{text("Availability")}</dt><dd>{text(app.source === "cloud" ? "Cloud" : installed ? "Local" : "Not installed")}</dd>
      {(app.steam_appid || app.xbox_product_id) && <><dt>{text("App ID")}</dt><dd>{app.steam_appid || app.xbox_product_id}</dd></>}
    </dl></section>
  </aside>;
}

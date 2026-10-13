import type { App, StoreMetadata } from "../types";
import { lastPlayed, playtime, sourceLabel } from "./model";

export function CreteDetailsInfo({ app, metadata, lastPlayedAt, minutes, installed }: { app: App; metadata: StoreMetadata | null; lastPlayedAt?: number; minutes?: number; installed: boolean }) {
  return <aside className="crete-details-info" aria-label="Game information">
    <section><h3>Game info</h3><dl><dt>Developer</dt><dd>{metadata?.developers.join(", ") || "—"}</dd>
      <dt>Publisher</dt><dd>{metadata?.publishers.join(", ") || "—"}</dd>
      {metadata?.releaseDate && <><dt>Released</dt><dd>{metadata.releaseDate}</dd></>}
    </dl></section>
    <section><h3>Progress</h3><dl><dt>Last played</dt><dd>{lastPlayed(lastPlayedAt)}</dd>
      <dt>Playtime</dt><dd>{playtime(minutes)}</dd></dl></section>
    <section><h3>Launch</h3><dl><dt>Provider</dt><dd>{sourceLabel(app.source)}</dd>
      <dt>Availability</dt><dd>{app.source === "cloud" ? "Cloud" : installed ? "Local" : "Not installed"}</dd>
      {(app.steam_appid || app.xbox_product_id) && <><dt>App ID</dt><dd>{app.steam_appid || app.xbox_product_id}</dd></>}
    </dl></section>
  </aside>;
}

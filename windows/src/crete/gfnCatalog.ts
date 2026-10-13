import { invoke } from "@tauri-apps/api/core";
import snapshot from "./gfnCatalog.json";
import steamAliases from "./gfnSteamAliases.json";
import titleAliases from "./gfnTitleAliases.json";
import type { App } from "../types";
export interface GeForceGame { id: string; title: string; gfn: { playType: string; minimumMembershipTierLabel?: string | null }; variants: { id: string; appStore: string; shortName?: string }[] }
const normalize = (name: string) => name.replace(/\u00e2\u201e\u00a2|\u00c2\u00ae|[™®]/g, "").normalize("NFKC").toLowerCase().replace(/[^\p{L}\p{N}]/gu, "");
let byId = new Map<string, GeForceGame>();
let byName = new Map<string, GeForceGame[]>();
const listeners = new Set<() => void>();
let pending: Promise<void> | undefined; let attemptedAt = 0;
function install(games: GeForceGame[]) {
  byId = new Map(games.map(game => [game.id, game])); byName = new Map();
  for (const game of games) { const name = normalize(game.title); byName.set(name, [...(byName.get(name) ?? []), game]); }
}
install(snapshot.games);
export const subscribeGeForceCatalog = (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener); }; };
export function refreshGeForceCatalog() {
  if (pending) return pending;
  if (Date.now() - attemptedAt < 86400_000) return Promise.resolve();
  attemptedAt = Date.now();
  pending = invoke<GeForceGame[]>("get_geforce_catalog", { offline: !navigator.onLine }).then(games => {
    if (Array.isArray(games) && games.length) { install(games); listeners.forEach(listener => listener()); }
  }).catch(() => {}).finally(() => { pending = undefined; });
  return pending;
}
export function geforceGame(app: App): GeForceGame | undefined {
  const stores: Record<string, string> = { steam: "STEAM", xbox: "XBOX", gog: "GOG", epic: "EPIC", ea: "EA_APP", ubisoft: "UPLAY", battlenet: "BATTLENET" };
  const store = stores[app.source?.toLowerCase() ?? ""]; if (!store) return;
  const name = normalize(app.name);
  // Original Metro licences must not inherit the old Android Redux links.
  if (app.source === "steam" && [43110, 43160].includes(Number(app.steam_appid))) return;
  const id = app.source === "steam" ? (steamAliases as Record<string, string>)[String(app.steam_appid)] : (titleAliases as Record<string, string>)[name];
  const known = id && byId.get(id);
  if (known && known.variants.some(variant => variant.appStore === store)) return known;
  const matches = (byName.get(name) ?? []).filter(game => game.variants.some(variant => variant.appStore === store));
  return matches.length === 1 ? matches[0] : undefined;
}

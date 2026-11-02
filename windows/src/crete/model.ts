import type { App } from "../types";
import { geforceGame } from "./gfnCatalog";
import { hasKnownHostGame } from "./hostSnapshot";

export type Availability = "all" | "local" | "owned" | "uninstalled" | "cloud";
export const AVAILABILITY = ["all", "local", "owned", "uninstalled", "cloud"] as const;
export const AVAILABILITY_LABELS = ["All Games", "Local", "Owned", "Not installed", "Cloud"];
const OWNED_STORES = new Set(["steam", "xbox", "gog", "epic", "battlenet", "lutris"]);

/** Platform and play location are independent; launch/ownership IDs stay intact. */
export function gamePlatform(app: App): string {
  const source = app.source?.toLowerCase() ?? "";
  if (source === "cloud") {
    try {
      const url = new URL(app.launch_path ?? "");
      if (["xbox.com", "www.xbox.com"].includes(url.hostname) && /\/play(?:\/|$)/.test(url.pathname)) return "xbox";
    } catch { /* Unknown cloud links keep their neutral identity. */ }
  }
  return source;
}
const copies = (app: App) => [app, ...(app.library_variants ?? [])];
export function isLocalGame(app: App) {
  return !["cloud", "playstation", "lutris"].includes(app.source?.toLowerCase() ?? "") && app.installed === true;
}
export function isCloudGame(app: App) {
  return ["cloud", "playstation", "lutris"].includes(app.source?.toLowerCase() ?? "") || (!isLocalGame(app) && (!!geforceGame(app) || hasKnownHostGame(app)));
}
export function matchesGameSource(app: App, source: string) {
  return source.toLowerCase() === "cloud" ? copies(app).some(isCloudGame) : copies(app).some(copy => gamePlatform(copy) === source.toLowerCase());
}
export function availabilityLabel(app: App) {
  if (copies(app).some(isLocalGame)) return "Local";
  if (app.source === "lutris") return "Moonlight · Bosgame";
  if (copies(app).some(isCloudGame)) return "Cloud";
  return app.installed === false ? "Not installed" : "Unknown";
}

export function matchesAvailability(app: App, filter: Availability) {
  if (filter === "cloud") return copies(app).some(isCloudGame);
  if (filter === "uninstalled") return copies(app).some(copy => !["cloud", "playstation", "lutris"].includes(copy.source?.toLowerCase() ?? "") && copy.installed === false);
  if (filter === "local") return copies(app).some(isLocalGame);
  if (filter === "owned") return copies(app).some(copy => OWNED_STORES.has(copy.source?.toLowerCase() ?? ""));
  return true;
}

/** A Cloud/Local result must open the matching copy, not a different edition's route. */
export function availabilityCopy(app: App, filter: Availability): App {
  const { library_variants: variants, ...primary } = app;
  if (matchesAvailability(primary, filter)) return app;
  const selected = variants?.find(copy => matchesAvailability(copy, filter));
  if (!selected) return app;
  return { ...selected, last_played: Math.max(epochMs(app.last_played) ?? 0, epochMs(selected.last_played) ?? 0) || undefined,
    library_variants: [primary, ...(variants ?? []).filter(copy => copy.id !== selected.id)] };
}

export function normalizeSearch(value: string) {
  return value.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().replace(/[^\p{L}\p{N}]+/gu, " ").trim();
}

export function matchesSearch(name: string, query: string) {
  const normalized = normalizeSearch(name);
  return normalizeSearch(query).split(" ").filter(Boolean).every(word => normalized.includes(word));
}

export function epochMs(value?: number) {
  return value && value > 0 ? (value < 100_000_000_000 ? value * 1000 : value) : undefined;
}

export function playedGames(apps: App[], recent: App[]) {
  const rank = new Map(recent.map((app, i) => [app.id, i]));
  const recorded = new Map(recent.map(app => [app.id, app]));
  return apps.filter(app => app.app_type === "game" && (epochMs(app.last_played) || rank.has(app.id)))
    .map(app => {
      const entry = recorded.get(app.id);
      const launchedAt = entry?.launched_at;
      const latest = Math.max(epochMs(app.last_played) ?? 0, epochMs(entry?.last_played) ?? 0, typeof launchedAt === "number" ? epochMs(launchedAt) ?? 0 : 0);
      return latest > (epochMs(app.last_played) ?? 0) ? { ...app, last_played: latest } : app;
    }).sort((a, b) => (epochMs(b.last_played) ?? 0) - (epochMs(a.last_played) ?? 0) || (rank.get(a.id) ?? Number.MAX_SAFE_INTEGER) - (rank.get(b.id) ?? Number.MAX_SAFE_INTEGER));
}

export function playtime(minutes?: number) {
  if (minutes == null || !Number.isFinite(minutes) || minutes < 0) return "—";
  const whole = Math.round(minutes);
  return whole >= 60 ? `${Math.floor(whole / 60)}h${whole % 60 ? ` ${whole % 60}m` : ""}` : `${whole}m`;
}

export function lastPlayed(value?: number) {
  const ms = epochMs(value);
  return ms ? new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric", year: "2-digit" }).format(ms) : "Never";
}

export function steamId(app?: App) {
  return app?.steam_appid != null ? String(app.steam_appid) : app?.id.match(/(?:rungameid\/|steam\/)(\d+)/)?.[1];
}

export function sourceLabel(source?: string) {
  return ({ steam: "Steam", xbox: "Xbox", gog: "GOG", epic: "Epic Games", battlenet: "Battle.net", cloud: "Cloud", playstation: "PlayStation", lutris: "Lutris · Bosgame" } as Record<string, string>)[source ?? ""] ?? source ?? "Local";
}

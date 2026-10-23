import type { App } from "../types";

export type Availability = "all" | "local" | "owned" | "uninstalled" | "cloud";
export const AVAILABILITY = ["all", "local", "owned", "uninstalled", "cloud"] as const;
export const AVAILABILITY_LABELS = ["All Games", "Local", "Owned", "Not installed", "Cloud"];
const OWNED_STORES = new Set(["steam", "xbox", "gog", "epic", "battlenet", "lutris"]);

export function matchesAvailability(app: App, filter: Availability) {
  const source = app.source?.toLowerCase();
  if (filter === "cloud") return source === "cloud";
  if (filter === "uninstalled") return source !== "cloud" && app.installed === false;
  if (filter === "local") return source !== "cloud" && app.installed !== false;
  if (filter === "owned") return OWNED_STORES.has(source ?? "");
  return true;
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
  return ({ steam: "Steam", xbox: "Xbox", gog: "GOG", epic: "Epic Games", battlenet: "Battle.net", cloud: "Cloud", lutris: "Lutris · Bosgame" } as Record<string, string>)[source ?? ""] ?? source ?? "Local";
}

import type { App } from "../types";
import { epochMs } from "./model";

// Only explicit edition suffixes are collapsed. Years, sequel numbers, DLC,
// demos and remakes retain their identity; punctuation/trademarks are cosmetic.
export function gameEdition(name: string) {
  let base = name.normalize("NFKC").replace(/[™®]/g, "").replace(/[’‘]/g, "'")
    .replace(/\s*\((?:PC|Windows)\)\s*$/i, "").trim();
  let rank = 0;
  const suffixes: [RegExp, number][] = [
    [/[\s:–—-]+(?:the\s+)?(?:remastered|remaster)(?:\s+edition)?$/i, 4],
    [/[\s:–—-]+(?:the\s+)?definitive edition$/i, 5],
    [/[\s:–—-]+(?:the\s+)?(?:complete|ultimate|game of the year|goty|gold) edition$/i, 3],
    [/[\s:–—-]+(?:the\s+)?(?:deluxe|enhanced|enchanted|special|voidheart) edition$/i, 2],
    [/[\s:–—-]+(?:the\s+)?(?:director's cut|final cut|complete story|20 year celebration)$/i, 2],
  ];
  let changed = true;
  while (changed) {
    changed = false;
    for (const [suffix, score] of suffixes) if (suffix.test(base)) {
      const stripped = base.replace(suffix, "").trim();
      if (stripped) { base = stripped; rank = Math.max(rank, score); changed = true; break; }
    }
  }
  return { family: base.toLowerCase().replace(/[^\p{L}\p{N}]/gu, ""), rank };
}

const stores: Record<string, number> = { steam: 0, gog: 1, epic: 2, xbox: 3, cloud: 4 };
export const gameCopies = (app: App): App[] => [app, ...(app.library_variants ?? [])];
const clean = ({ library_variants: _, ...app }: App): App => app;

/** Presentation only: every entitlement, installed executable and history ID survives. */
export function preferGameEditions(apps: App[], recent: App[] = []): App[] {
  const groups = new Map<string, App[]>();
  for (const app of apps) {
    const edition = gameEdition(app.name);
    const key = app.app_type === "game" && edition.family ? edition.family : `id:${app.id}`;
    groups.set(key, [...(groups.get(key) ?? []), app]);
  }
  const result: App[] = [];
  for (const group of groups.values()) {
    // Equal names on the SAME store can denote distinct releases (e.g. Prey).
    // Collapse only cross-store copies or an explicit edition relationship.
    const ranks = new Set(group.map(app => gameEdition(app.name).rank));
    const sources = new Set(group.map(app => app.source));
    const ambiguous = ranks.size === 1 && group.some(app => group.filter(other => other.source === app.source).length > 1);
    if (group.length === 1 || (sources.size === 1 && ranks.size === 1) || ambiguous) { result.push(...group); continue; }
    const ordered = [...group].sort((a, b) => gameEdition(b.name).rank - gameEdition(a.name).rank
      || (stores[a.source ?? ""] ?? 10) - (stores[b.source ?? ""] ?? 10)
      || Number(b.installed !== false) - Number(a.installed !== false) || a.id.localeCompare(b.id));
    const chosen = ordered[0];
    const copies = ordered.flatMap(gameCopies).filter((app, i, all) => all.findIndex(other => other.id === app.id) === i);
    const ids = new Set(copies.map(app => app.id));
    const played = Math.max(0, ...copies.map(app => epochMs(app.last_played) ?? 0), ...recent.filter(app => ids.has(app.id)).map(app => Math.max(epochMs(app.last_played) ?? 0, typeof app.launched_at === "number" ? epochMs(app.launched_at) ?? 0 : 0)));
    result.push({ ...chosen, ...(played ? { last_played: played } : {}), library_variants: copies.filter(app => app.id !== chosen.id).map(clean) });
  }
  return result;
}

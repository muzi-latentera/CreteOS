import type { App } from "../types";
export type AsobiProvider = "ps5RemotePlay" | "playstationCloud";
export interface AsobiGame { id: string; name: string; label: string; provider: AsobiProvider }
export const asobiLabel = (provider: AsobiProvider) => provider === "ps5RemotePlay" ? "PS5 Remote Play" : "PlayStation Cloud";
const title = (name: string) => name.replace(/[™®]/g, "").normalize("NFKC").toLowerCase().replace(/[^\p{L}\p{N}]/gu, "");
export function asobiMatches(app: App, games: AsobiGame[]) {
  return games.filter(game => app.source === "playstation" && game.id === app.store_id || !!title(app.name) && title(game.name) === title(app.name));
}
export function asobiProvider(app: App): AsobiProvider | undefined {
  const mode = /^asobi:\/\/(ps5RemotePlay|playstationCloud)\/\d+$/.exec(app.launch_path ?? "")?.[1];
  return app.source === "playstation" ? mode as AsobiProvider | undefined : undefined;
}

import type { App } from "../types";
import links from "./gfnLinks.json";
import titles from "./gfnTitles.json";

// Exact links retained from Android's curated table. They are game-page links,
// not a claim that a Windows cloud session or subscription has been verified.
export function geforceNowUrl(app: App): string | undefined {
  const id = app.source?.toLowerCase() === "steam" ? String(app.steam_appid ?? "") : undefined;
  const aliases: Record<string, string> = {
    "xbox:Indiana Jones and the Great Circle": "indyjones_gp",
    "xbox:Star Wars Outlaws": "swo_gp",
    "xbox:Dishonored 2": "dis2_gp",
    "xbox:Forza Horizon 6": "fh6_gp",
    "gog:Cyberpunk 2077": "cp2077_gog",
    "gog:Baldur's Gate 3": "bg3_gog",
    "ea:Battlefield 6": "bf6_ea",
  };
  const key = id || aliases[`${app.source?.toLowerCase()}:${app.name}`];
  if (key) return (links as Record<string, string>)[key];
  const normalize = (name: string) => name.replace(/[™®]/g, "").trim().toLowerCase();
  const title = Object.entries(titles).find(([name]) => normalize(name) === normalize(app.name));
  if (!title || app.source?.toLowerCase() === "steam") return;
  const link = (links as Record<string, string>)[title[1]];
  if (!link) return;
  // A Steam variant asset must not be forced for an Xbox/GOG/Epic copy.
  const url = new URL(link); url.searchParams.delete("asset-id"); return url.href;
}

export function isPcGame(app: App): boolean {
  return app.app_type === "game" && ["steam", "xbox", "gog", "epic", "ea", "ubisoft", "battlenet", "desktop"].includes(app.source?.toLowerCase() ?? "");
}

export function moonlightMatch(app: App, apps: string[]): string | undefined {
  const normalize = (name: string) => name.replace(/[™®]/g, "").normalize("NFKC").trim().toLowerCase();
  return apps.find(name => normalize(name) === normalize(app.name));
}

export function defaultProvider(app: App, moonlightGame?: string, gfnUrl?: string): "local" | "moonlight" | "geforceNow" | "install" {
  return moonlightGame ? "moonlight" : app.installed ? "local" : gfnUrl ? "geforceNow" : "install";
}

export function moonlightTarget(app: App, apps: string[], installedIds: string[]): string | undefined {
  // Select the host route before Buddy pairing so Play can open its setup.
  // The stream button and native launch still require a paired Buddy.
  if (app.source === "steam" && app.steam_appid && installedIds.includes(String(app.steam_appid)) && apps.includes("MoonDeckStream")) return "MoonDeckStream";
  return moonlightMatch(app, apps);
}

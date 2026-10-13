import type { App } from "../types";
import links from "./gfnLinks.json";

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
  return key ? (links as Record<string, string>)[key] : undefined;
}

export function isPcGame(app: App): boolean {
  return app.app_type === "game" && ["steam", "xbox", "gog", "epic", "ea", "ubisoft", "battlenet", "desktop"].includes(app.source?.toLowerCase() ?? "");
}

export function moonlightMatch(app: App, apps: string[]): string | undefined {
  const normalize = (name: string) => name.replace(/[™®]/g, "").normalize("NFKC").trim().toLowerCase();
  return apps.find(name => normalize(name) === normalize(app.name));
}

export function defaultProvider(app: App, moonlightGame?: string, gfnUrl?: string): "local" | "moonlight" | "geforceNow" | "install" {
  return app.installed ? "local" : moonlightGame ? "moonlight" : gfnUrl ? "geforceNow" : "install";
}

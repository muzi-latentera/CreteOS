import type { App } from "../types";
import { geforceGame } from "./gfnCatalog";

// Exact title/store variants from NVIDIA's public catalogue. Availability is
// separate from the user's subscription and any required cloud installation.
export function geforceNowUrl(app: App): string | undefined {
  const game = geforceGame(app);
  return game ? `https://play.geforcenow.com/games?game-id=${game.id}&lang=en_GB&utm_source=creteos&utm_campaign=launcher` : undefined;
}

export function isPcGame(app: App): boolean {
  return app.app_type === "game" && ["steam", "xbox", "gog", "epic", "ea", "ubisoft", "battlenet", "desktop", "lutris"].includes(app.source?.toLowerCase() ?? "");
}

export function moonlightMatch(app: App, apps: string[]): string | undefined {
  const normalize = (name: string) => name.replace(/[™®]/g, "").normalize("NFKC").trim().toLowerCase();
  return apps.find(name => normalize(name) === normalize(app.name));
}

export function cloudProvider(app: App): "luna" | "geforceNow" | "cloud" {
  try {
    const host = new URL(app.launch_path).hostname;
    if (["luna.amazon.co.uk", "luna.amazon.com"].includes(host)) return "luna";
    if (["play.geforcenow.com", "geforcenow.com"].includes(host)) return "geforceNow";
  } catch { /* Unknown cloud provider keeps a neutral presentation. */ }
  return "cloud";
}

export function defaultProvider(app: App, moonlightGame?: string, gfnUrl?: string): "local" | "moonlight" | "geforceNow" | "luna" | "cloud" | "install" {
  if (app.source === "cloud") return cloudProvider(app);
  if (app.source === "lutris") return "moonlight";
  return moonlightGame ? "moonlight" : app.installed ? "local" : gfnUrl ? "geforceNow" : "install";
}

export function moonlightTarget(app: App, apps: string[], installedIds: string[]): string | undefined {
  // Select the host route before Buddy pairing so Play can open its setup.
  // The stream button and native launch still require a paired Buddy.
  if (app.source === "steam" && app.steam_appid && installedIds.includes(String(app.steam_appid)) && apps.includes("MoonDeckStream")) return "MoonDeckStream";
  return moonlightMatch(app, apps);
}

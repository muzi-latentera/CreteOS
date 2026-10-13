import type { App } from "../types";
import { geforceGame } from "./gfnCatalog";

// Exact title/store variants from NVIDIA's public catalogue. Availability is
// separate from the user's subscription and any required cloud installation.
export function geforceNowUrl(app: App): string | undefined {
  const game = geforceGame(app);
  return game ? `https://play.geforcenow.com/games?game-id=${game.id}&lang=en_GB&utm_source=creteos&utm_campaign=launcher` : undefined;
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

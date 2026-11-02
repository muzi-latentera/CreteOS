import { describe, expect, it, vi } from "vitest";
import type { App } from "../types";
vi.mock("./gfnCatalog", () => ({ geforceGame: (app: App) => app.name === "GFN title" ? { id: "confirmed" } : undefined }));
vi.mock("./hostSnapshot", () => ({ hasKnownHostGame: (app: App) => app.id === "host-only" }));
import { availabilityLabel, gamePlatform, matchesAvailability, matchesGameSource } from "./model";
const game = (source: string, installed = false, extra: Partial<App> = {}): App => ({ id: source, name: source, source, installed, app_type: "game", ...extra });

describe("platform identity and play location", () => {
  it("keeps Xbox cloud/Game Pass under Xbox and Cloud without changing launch identity", () => {
    const app = game("cloud", true, { launch_path: "https://www.xbox.com/en-us/play/games/test/PRODUCT" });
    expect(gamePlatform(app)).toBe("xbox");
    expect(matchesGameSource(app, "Xbox")).toBe(true);
    expect(matchesAvailability(app, "cloud")).toBe(true);
    expect(matchesAvailability(app, "local")).toBe(false);
    expect(app.source).toBe("cloud");
    expect(gamePlatform({ ...app, launch_path: "https://xbox.com.example.test/play/game" })).toBe("cloud");
  });
  it.each(["ps5RemotePlay", "playstationCloud"])("keeps %s PlayStation-branded and remote", mode => {
    const app = game("playstation", true, { launch_path: `asobi://${mode}/1` });
    expect(matchesGameSource(app, "PlayStation")).toBe(true);
    expect(matchesAvailability(app, "cloud")).toBe(true);
    expect(matchesAvailability(app, "local")).toBe(false);
    expect(availabilityLabel(app)).toBe("Cloud");
  });
  it("puts confirmed GFN games in Cloud until installed on the handheld", () => {
    const app = game("steam", false, { name: "GFN title" });
    expect(matchesAvailability(app, "cloud")).toBe(true);
    expect(matchesAvailability(app, "owned")).toBe(true);
    expect(matchesGameSource(app, "Steam")).toBe(true);
    expect(matchesAvailability({ ...app, installed: true }, "cloud")).toBe(false);
    expect(matchesAvailability({ ...app, installed: true }, "local")).toBe(true);
    expect(matchesAvailability(game("steam"), "cloud")).toBe(false);
  });
  it("does not confuse a Bosgame install or launchable shortcut with a G1 install", () => {
    expect(matchesAvailability(game("lutris", true), "local")).toBe(false);
    expect(matchesAvailability(game("lutris", true), "cloud")).toBe(true);
    expect(availabilityLabel(game("lutris", true))).toBe("Moonlight · Bosgame");
    expect(matchesAvailability(game("steam", false, { id: "host-only" }), "cloud")).toBe(true);
    expect(matchesAvailability({ ...game("steam"), installed: undefined }, "local")).toBe(false);
  });
  it("retains the availability of alternate editions rather than only the chosen tile", () => {
    const app = game("steam", false, { library_variants: [game("epic", true), game("playstation", true)] });
    expect(matchesAvailability(app, "local")).toBe(true);
    expect(matchesAvailability(app, "cloud")).toBe(true);
    expect(matchesGameSource(app, "PlayStation")).toBe(true);
  });
});

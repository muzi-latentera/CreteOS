import { describe, expect, it } from "vitest";
import { defaultProvider, geforceNowUrl, isPcGame, moonlightMatch, moonlightTarget } from "./streaming";
import type { App } from "../types";

describe("streaming targets", () => {
  it("routes exact installed Steam IDs through paired MoonDeck, without inferring from a launcher", () => {
    const game: App = { id: "re2", name: "Resident Evil 2", source: "steam", steam_appid: 883710, installed: false };
    const apps = ["MoonDeckStream", "Desktop", "Steam"];
    expect(moonlightTarget(game, apps, ["883710"], true)).toBe("MoonDeckStream");
    expect(moonlightTarget(game, apps, ["883710"], false)).toBeUndefined();
    expect(moonlightTarget(game, apps, ["883711"], true)).toBeUndefined();
    expect(moonlightTarget(game, apps, [], true)).toBeUndefined();
    expect(defaultProvider({ ...game, installed: true }, "MoonDeckStream", "gfn")).toBe("moonlight");
  });
  it("prefers a verified host game, then local, then GFN; launchers are not games", () => {
    const app: App = { id: "control", name: "Control Ultimate Edition", installed: false };
    expect(moonlightMatch(app, ["Desktop", "MoonDeckStream", "Steam"])).toBeUndefined();
    const match = moonlightMatch(app, ["CONTROL Ultimate Edition™"]);
    expect(match).toBe("CONTROL Ultimate Edition™");
    expect(defaultProvider(app, match, "gfn")).toBe("moonlight");
    expect(defaultProvider({ ...app, installed: true }, match, "gfn")).toBe("moonlight");
    expect(defaultProvider({ ...app, installed: true }, undefined, "gfn")).toBe("local");
    expect(defaultProvider(app, undefined, "gfn")).toBe("geforceNow");
    expect(defaultProvider(app)).toBe("install");
    expect(moonlightMatch(app, ["Control", "CONTROL Resonant"])).toBeUndefined();
  });
  it("uses exact Android links without guessing similar titles or app IDs", () => {
    const game: App = { id: "control", name: "Control Ultimate Edition", source: "steam", app_type: "game", steam_appid: 870780 };
    expect(geforceNowUrl(game)).toContain("game-id=5aed2b8b-912f-4e9e-b8e3-dcd8c8613679");
    expect(geforceNowUrl({ ...game, name: "CONTROL Resonant", steam_appid: 3669870 })).toBeUndefined();
    expect(geforceNowUrl({ ...game, name: "Elden Ring", steam_appid: 1245620 })).toBeUndefined();
  });
  it("uses exact non-Steam aliases and excludes application entries", () => {
    expect(geforceNowUrl({ id: "indy", source: "xbox", name: "Indiana Jones and the Great Circle" })).toContain("game-id=3d6f95b6");
    expect(isPcGame({ id: "gfn", name: "GeForce NOW", app_type: "app", source: "desktop" })).toBe(false);
    expect(isPcGame({ id: "game", name: "Game", app_type: "game", source: "steam" })).toBe(true);
  });
});

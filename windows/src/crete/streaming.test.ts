import { describe, expect, it } from "vitest";
import { geforceNowUrl, isPcGame } from "./streaming";
import type { App } from "../types";

describe("streaming targets", () => {
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

import { describe, it, expect } from "vitest";
import { geforceGame } from "./gfnCatalog";

describe("NVIDIA store-specific catalogue", () => {
  it("uses the user's NVIDIA shortcut CMS variant for Xbox Requiem", () => {
    const game = geforceGame({ id: "requiem", name: "A Plague Tale: Requiem", source: "xbox" });
    expect(game?.variants.find(variant => variant.appStore === "XBOX")).toMatchObject({ id: "102396411", shortName: "a_plague_tale_requiem_xbox_app" });
  });
  it("resolves current Steam names and trademarked titles", () => {
    expect(geforceGame({ id: "witcher", name: "The Witcher: Enhanced Edition", source: "steam", steam_appid: 20900 })?.title).toContain("Witcher");
    expect(geforceGame({ id: "re2", name: "Resident Evil 2", source: "steam", steam_appid: 883710 })?.variants.some(variant => variant.appStore === "STEAM")).toBe(true);
  });
  it("does not borrow another store entitlement or another Metro edition", () => {
    expect(geforceGame({ id: "metro", name: "Metro 2033", source: "steam", steam_appid: 43110 })).toBeUndefined();
    expect(geforceGame({ id: "fake", name: "A Plague Tale: Requiemish", source: "xbox" })).toBeUndefined();
    expect(geforceGame({ id: "re2", name: "Resident Evil 2", source: "unknown" })).toBeUndefined();
  });
});

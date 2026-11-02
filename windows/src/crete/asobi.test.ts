import { describe, expect, it } from "vitest";
import { asobiMatches, asobiProvider, type AsobiGame } from "./asobi";
import { preferGameEditions } from "./libraryEditions";
import { matchesAvailability } from "./model";
const cloud: AsobiGame = { id: "3259553641", name: "Returnal", label: "Returnal (Cloud)", provider: "playstationCloud" };
const remote: AsobiGame = { id: "4077481643", name: "Returnal", label: "Returnal (PS5)", provider: "ps5RemotePlay" };
describe("PlayStation route identities", () => {
  it("keeps PC and PlayStation IDs distinct while exposing both exact-title routes", () => {
    expect(asobiMatches({ id: "steam:1649240", name: "Returnal™", source: "steam" }, [cloud, remote])).toEqual([cloud, remote]);
    expect(asobiMatches({ id: "other", name: "Returnal 2" }, [cloud, remote])).toEqual([]);
    expect(asobiMatches({ id: "other", name: "Returnal Deluxe Edition" }, [cloud, remote])).toEqual([]);
  });
  it("does not infer a PlayStation provider for arbitrary Steam shortcuts", () => {
    expect(asobiProvider({ id: "ps", name: "Returnal", source: "playstation", launch_path: "asobi://ps5RemotePlay/4077481643" })).toBe("ps5RemotePlay");
    expect(asobiProvider({ id: "pc", name: "Returnal", source: "steam", launch_path: "asobi://ps5RemotePlay/4077481643" })).toBeUndefined();
  });
  it("groups PC, PS5 and cloud copies without replacing their launch identities", () => {
    const pc = { id: "pc", name: "Returnal", app_type: "game" as const, source: "steam", installed: true };
    const ps = [cloud, remote].map(game => ({ id: `asobi:${game.id}`, name: game.name, source: "playstation", app_type: "game" as const, installed: true, launch_path: `asobi://${game.provider}/${game.id}` }));
    const grouped = preferGameEditions([pc, ...ps]);
    expect(grouped).toHaveLength(1);
    expect(grouped[0].id).toBe("pc");
    expect(grouped[0].library_variants?.map(game => game.launch_path).sort()).toEqual(ps.map(game => game.launch_path).sort());
    expect(matchesAvailability(ps[0], "cloud")).toBe(true);
    expect(matchesAvailability(ps[0], "local")).toBe(false);
  });
});

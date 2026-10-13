import { describe, expect, it } from "vitest";
import type { App } from "../types";
import { matchesAvailability, matchesSearch, playedGames, playtime, lastPlayed } from "./model";
import { DEFAULT_SETTINGS } from "../constants";

describe("CreteOS library data", () => {
  const local: App = { id: "steam/1", name: "Local", source: "steam", installed: true, app_type: "game" };
  const owned: App = { id: "steam/2", name: "Owned", source: "steam", installed: false, app_type: "game" };
  const cloud: App = { id: "cloud/3", name: "Cloud", source: "cloud", installed: true, app_type: "game" };
  it("distinguishes owned, local and cloud capabilities", () => {
    expect(matchesAvailability(owned, "owned")).toBe(true);
    expect(matchesAvailability(owned, "local")).toBe(false);
    expect(matchesAvailability(cloud, "local")).toBe(false);
    expect(matchesAvailability(cloud, "owned")).toBe(false);
    expect(matchesAvailability(local, "cloud")).toBe(false);
    expect(matchesAvailability(cloud, "cloud")).toBe(true);
    expect(matchesAvailability(owned, "uninstalled")).toBe(true);
    expect(matchesAvailability(local, "uninstalled")).toBe(false);
    expect(matchesAvailability(cloud, "uninstalled")).toBe(false);
    expect(matchesAvailability({ ...owned, installed: undefined }, "uninstalled")).toBe(false);
  });
  it("does not invent continue-playing history from an installed game", () => {
    expect(playedGames([local, owned], [])).toEqual([]);
    expect(playedGames([local, owned], [owned])).toEqual([owned]);
  });
  it("orders real history and ignores application shortcuts", () => {
    const first = { ...local, last_played: 2000 };
    const second = { ...owned, last_played: 1000 };
    expect(playedGames([second, first, { ...cloud, app_type: "app" }], [])).toEqual([first, second]);
  });
  it("uses the newer persisted launcher timestamp for the hero", () => {
    const played = playedGames([{ ...local, last_played: 1000 }], [{ ...local, launched_at: 2000 }]);
    expect(played[0].last_played).toBe(2_000_000);
  });
  it("normalizes punctuation and accents and requires every query word", () => {
    expect(matchesSearch("Pokémon: Let's Go!", "pokemon go")).toBe(true);
    expect(matchesSearch("Hollow Knight", "hollow desert")).toBe(false);
    expect(matchesSearch("Hollow Knight", "")).toBe(true);
  });
  it("does not put older local history ahead of a newer Steam play timestamp", () => {
    const older = { ...local, last_played: 1725000000 };
    const newer = { ...owned, last_played: 1726000000 };
    expect(playedGames([older, newer], [{ ...older, launched_at: 1725000000 }]).map(app => app.id)).toEqual([newer.id, older.id]);
  });
  it("shows unknown playtime honestly and formats recorded times", () => {
    expect(playtime()).toBe("—"); expect(playtime(0)).toBe("0m"); expect(playtime(93)).toBe("1h 33m");
    expect(lastPlayed()).toBe("Never");
  });
  it("defaults new installs to Crete and includes the owned library", () => {
    expect(DEFAULT_SETTINGS.theme).toBe("crete");
    expect(DEFAULT_SETTINGS.show_uninstalled_games).toBe(true);
  });
});

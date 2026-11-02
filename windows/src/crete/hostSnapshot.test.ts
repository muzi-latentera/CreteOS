import { beforeEach, expect, it, vi } from "vitest";
const invoke = vi.hoisted(() => vi.fn());
vi.mock("@tauri-apps/api/core", () => ({ invoke }));
beforeEach(() => {
  vi.resetModules(); invoke.mockReset(); localStorage.clear();
  Object.defineProperty(navigator, "onLine", { configurable: true, value: true });
  invoke.mockImplementation(async (command: string) => command === "get_moonlight_apps" ? ["MoonDeckStream"] : command === "get_host_installed_games" ? [{ steamAppId: "883710", name: "Resident Evil 2" }] : { paired: true, version: 8 });
});
it("shares a daily refresh across games and persists it across app restarts", async () => {
  let cache = await import("./hostSnapshot");
  const first = cache.refreshHost("Bosgame");
  expect(cache.refreshHost("BOSGAME")).toBe(first);
  await first;
  expect(invoke).toHaveBeenCalledTimes(3);
  await cache.refreshHost("Bosgame");
  vi.resetModules(); cache = await import("./hostSnapshot");
  expect(cache.readHost("Bosgame").snapshot.games?.[0].steamAppId).toBe("883710");
  await cache.refreshHost("Bosgame");
  expect(invoke).toHaveBeenCalledTimes(3);
});
it("refreshes expired state without discarding the snapshot while waiting", async () => {
  const cache = await import("./hostSnapshot");
  localStorage.setItem(cache.HOST_SNAPSHOT_KEY, JSON.stringify({ bosgame: { attemptedAt: Date.now() - cache.HOST_REFRESH_MS, apps: ["Desktop"] } }));
  let release!: (value: string[]) => void;
  invoke.mockImplementationOnce(() => new Promise(resolve => { release = resolve; }));
  const task = cache.refreshHost("Bosgame"); await Promise.resolve();
  expect(cache.readHost("Bosgame").snapshot.apps).toEqual(["Desktop"]);
  expect(cache.readHost("Bosgame").refreshing).toBe(true);
  release(["MoonDeckStream"]); await task;
  expect(cache.readHost("Bosgame").snapshot.apps).toEqual(["MoonDeckStream"]);
});
it("retains successful data after connection failures and saves the attempt to prevent repeated retries", async () => {
  let cache = await import("./hostSnapshot");
  await cache.refreshHost("Bosgame");
  invoke.mockRejectedValue(new Error("Host offline"));
  await cache.refreshHost("Bosgame", true);
  expect(cache.readHost("Bosgame").snapshot.games).toHaveLength(1);
  expect(cache.readHost("Bosgame").error).toContain("saved availability");
  vi.resetModules(); cache = await import("./hostSnapshot");
  await cache.refreshHost("Bosgame");
  expect(invoke).toHaveBeenCalledTimes(6);
});
it("allows a manual refresh before the next day", async () => {
  const cache = await import("./hostSnapshot");
  await cache.refreshHost("Bosgame"); await cache.refreshHost("Bosgame", true);
  expect(invoke).toHaveBeenCalledTimes(6);
});
it("does not send host requests without a network connection", async () => {
  Object.defineProperty(navigator, "onLine", { configurable: true, value: false });
  const cache = await import("./hostSnapshot");
  await cache.refreshHost("Bosgame"); await cache.refreshHost("Bosgame", true);
  expect(invoke).not.toHaveBeenCalled();
  expect(cache.readHost("Bosgame").snapshot.attemptedAt).toBe(0);
});
it("recovers from invalid storage and isolates host identities", async () => {
  const cache = await import("./hostSnapshot");
  localStorage.setItem(cache.HOST_SNAPSHOT_KEY, "broken");
  expect(cache.readHost("Bosgame").snapshot.games).toBeUndefined();
  await cache.refreshHost("Bosgame");
  expect(cache.readHost("OtherPC").snapshot.games).toBeUndefined();
});
it("classifies saved remote installs offline without making host requests", async () => {
  const cache = await import("./hostSnapshot");
  localStorage.setItem(cache.HOST_SNAPSHOT_KEY, JSON.stringify({ livingroom: {
    attemptedAt: 1, apps: ["MoonDeckStream", "Standalone Fixture"],
    games: [{ steamAppId: "883710", name: "Resident Evil 2" }],
  } }));
  expect(cache.hasKnownHostGame({id:"steam/883710",name:"Resident Evil 2",source:"steam",steam_appid:883710})).toBe(true);
  expect(cache.hasKnownHostGame({id:"other",name:"Standalone Fixture",source:"desktop"})).toBe(true);
  expect(cache.hasKnownHostGame({id:"other",name:"Resident Evil 3",source:"steam",steam_appid:952060})).toBe(false);
  expect(invoke).not.toHaveBeenCalled();
});

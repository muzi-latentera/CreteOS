import { beforeEach, expect, it, vi } from "vitest";
const { invoke } = vi.hoisted(() => ({ invoke: vi.fn() }));
vi.mock("@tauri-apps/api/core", () => ({ invoke }));
const game = { id: "new-catalog-game", title: "Phantom Blade Zero", gfn: { playType: "INCLUDED" }, variants: [{ id: "123456", appStore: "STEAM", shortName: "test" }] };
const app = { id: "steam:4115450", name: "Phantom Blade Zero", source: "steam", steam_appid: 4115450 };
beforeEach(() => { vi.resetModules(); invoke.mockReset().mockResolvedValue([game]); Object.defineProperty(navigator, "onLine", { configurable: true, value: true }); });
it("loads saved routes offline without throttling the first online background request", async () => {
  const catalogue = await import("./gfnCatalog");
  Object.defineProperty(navigator, "onLine", { configurable: true, value: false });
  await catalogue.refreshGeForceCatalog();
  expect(invoke).toHaveBeenLastCalledWith("get_geforce_catalog", { offline: true, force: false });
  Object.defineProperty(navigator, "onLine", { configurable: true, value: true });
  await catalogue.refreshGeForceCatalog();
  expect(invoke).toHaveBeenCalledTimes(2);
  expect(invoke).toHaveBeenLastCalledWith("get_geforce_catalog", { offline: false, force: false });
});
it("manual refresh bypasses the automatic throttle and notifies existing subscribers", async () => {
  const catalogue = await import("./gfnCatalog"); const changed = vi.fn(); catalogue.subscribeGeForceCatalog(changed);
  await catalogue.refreshGeForceCatalog(); await catalogue.refreshGeForceCatalog(); expect(invoke).toHaveBeenCalledTimes(1);
  await catalogue.refreshGeForceCatalog({ force: true }); expect(invoke).toHaveBeenLastCalledWith("get_geforce_catalog", { offline: false, force: true });
  expect(invoke).toHaveBeenCalledTimes(2); expect(changed).toHaveBeenCalledTimes(2); expect(catalogue.geforceGame(app)).toEqual(game);
});
it("reports manual failures and retains the working catalogue for launch routes", async () => {
  const catalogue = await import("./gfnCatalog"); await catalogue.refreshGeForceCatalog();
  invoke.mockRejectedValue(new Error("unavailable")); await expect(catalogue.refreshGeForceCatalog({ force: true })).rejects.toThrow("unavailable");
  expect(catalogue.geforceGame(app)).toEqual(game);
});
it("queues a forced refresh after an already pending background cache read", async () => {
  let finish!: (value: unknown) => void; invoke.mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  const catalogue = await import("./gfnCatalog"); const background = catalogue.refreshGeForceCatalog(); const manual = catalogue.refreshGeForceCatalog({ force: true });
  finish([game]); await Promise.all([background, manual]); expect(invoke).toHaveBeenCalledTimes(2);
  expect(invoke).toHaveBeenLastCalledWith("get_geforce_catalog", { offline: false, force: true });
});
it("rejects an empty manual result without replacing saved game routes", async () => {
  const catalogue = await import("./gfnCatalog"); await catalogue.refreshGeForceCatalog(); invoke.mockResolvedValue([]);
  await expect(catalogue.refreshGeForceCatalog({ force: true })).rejects.toThrow("incomplete"); expect(catalogue.geforceGame(app)).toEqual(game);
});

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { useStreaming } from "./useStreaming";
import { defaultProvider, moonlightTarget } from "./streaming";
import type { App } from "../types";
const invoke = vi.hoisted(() => vi.fn());
vi.mock("@tauri-apps/api/core", () => ({ invoke }));
vi.mock("./hostSnapshot", () => ({ readHost: () => ({ snapshot: { apps: ["MoonDeckStream"], games: [{ steamAppId: "1", name: "Unrelated Steam game" }], buddy: { paired: true, version: 8 } }, refreshing: false }), refreshHost: vi.fn(), subscribeHost: () => () => {} }));
vi.mock("./gfnCatalog", () => ({ refreshGeForceCatalog: vi.fn(), subscribeGeForceCatalog: () => () => {}, geforceGame: () => undefined }));
vi.mock("../components/launch/StreamLaunchCover", () => ({ withStreamCover: (_app: unknown, _provider: unknown, start: () => Promise<void>) => start() }));
const game: App = { id: "lutris:bosgame:1", store_id: "1", source: "lutris", name: "Mouse", installed: false, app_type: "game", steam_appid: 12345 };
let root: Root, host: HTMLDivElement;
async function renderStreaming() {
  const result = { current: null as unknown as ReturnType<typeof useStreaming> };
  function Harness() { result.current = useStreaming(game, true); return null; }
  host = document.createElement("div"); root = createRoot(host);
  await act(async () => root.render(<Harness />));
  return result;
}
afterEach(async () => { await act(async () => root?.unmount()); host?.remove(); });
beforeEach(() => {
  invoke.mockReset();
  Object.defineProperty(navigator, "onLine", { configurable: true, value: true });
  invoke.mockImplementation(async (command: string) => command === "get_streaming_clients" ? { moonlight: true, geforceNow: false } : command === "get_moonlight_profile" ? { displayKey: "g1", profile: { host: "Bosgame", resolution: "2560x1600", fps: 120, bitrate: 80000, codec: "auto" } } : command === "get_lutris_games" ? [{ id: "1", name: "Mouse", launchable: true, shortcutId: "3137199813", reason: "", playtimeMinutes: 39 }] : undefined);
});
it("routes through MoonDeck using Lutris identity rather than borrowed metadata product", async () => {
  const result = await renderStreaming();
  expect(result.current.hostInstalled).toBe(true);
  expect(result.current.moonlightGame).toBe("MoonDeckStream");
  expect(result.current.steamId).toBe("3137199813");
  await act(async () => result.current.stream("MoonDeckStream", result.current.display.profile));
  expect(invoke).toHaveBeenCalledWith("launch_moonlight", expect.objectContaining({ steamId: null, lutrisId: "1", gameId: "lutris:bosgame:1" }));
});
it("keeps installer-targeted entries unavailable despite unrelated Steam inventory", async () => {
  const original = invoke.getMockImplementation()!;
  invoke.mockImplementation((command: string, args: unknown) => command === "get_lutris_games" ? Promise.resolve([{ id: "1", launchable: false, shortcutId: "3137199813", reason: "Lutris still points to an installer" }]) : original(command, args));
  const result = await renderStreaming();
  expect(result.current.inventoryError).toContain("installer");
  expect(result.current.hostInstalled).toBe(false);
  expect(result.current.moonlightGame).toBeUndefined();
});
it("never treats a Lutris ID or metadata product ID as Steam installation ownership", () => {
  expect(moonlightTarget(game, ["MoonDeckStream"], ["1", "12345"])).toBeUndefined();
  expect(defaultProvider(game)).toBe("moonlight");
});

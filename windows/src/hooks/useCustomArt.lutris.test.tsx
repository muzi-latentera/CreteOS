import { act } from "react";
import { createRoot } from "react-dom/client";
import { expect, it, vi } from "vitest";
import { invoke } from "@tauri-apps/api/core";
import { useCustomArt } from "./useCustomArt";
vi.mock("@tauri-apps/api/core", () => ({ invoke: vi.fn(), convertFileSrc: (value: string) => "asset:" + value }));
it("loads remote Lutris covers without enabling network scans for every uninstalled store title", async () => {
  vi.mocked(invoke).mockImplementation(async (command) => command === "get_cached_art_bulk" ? {} : { grid: "mouse.jpg" });
  let art!: ReturnType<typeof useCustomArt>;
  function Harness() { art = useCustomArt(); return null; }
  const host = document.createElement("div"), root = createRoot(host);
  await act(async () => root.render(<Harness />));
  await act(async () => art.fetchGameArt([{ id: "lutris:bosgame:1", name: "Mouse", source: "lutris", installed: false }, { id: "steam:123", name: "Uninstalled", source: "steam", installed: false }]));
  const requests = vi.mocked(invoke).mock.calls.filter(([name]) => name === "fetch_game_art");
  expect(requests).toHaveLength(1);
  expect(requests[0][1]).toEqual(expect.objectContaining({ gameName: "Mouse", source: "lutris", appid: null }));
  expect(art.gameArt["lutris:bosgame:1"]).toBe("asset:mouse.jpg");
  await act(async () => root.unmount());
});

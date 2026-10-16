import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { beforeEach, afterEach, expect, it, vi } from "vitest";
import { useXboxProgress } from "./useXboxProgress";
const { invoke, callbacks } = vi.hoisted(() => ({ invoke: vi.fn(), callbacks: new Map<string, () => void>() }));
vi.mock("@tauri-apps/api/core", () => ({ invoke }));
vi.mock("@tauri-apps/api/event", () => ({ listen: vi.fn(async (name, callback) => { callbacks.set(name, callback); return () => callbacks.delete(name); }) }));
let root: Root; let host: HTMLDivElement;
function Harness({ name = "Requiem" }) { const progress = useXboxProgress(name, true); return <span>{progress?.minutes}</span>; }
beforeEach(async () => {
  vi.useFakeTimers(); vi.clearAllMocks(); callbacks.clear();
  vi.spyOn(navigator, "onLine", "get").mockReturnValue(true);
  (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
  invoke.mockResolvedValue({ minutes: 100 });
  host = document.createElement("div"); document.body.append(host); root = createRoot(host);
  await act(async () => root.render(<Harness />));
});
afterEach(() => { act(() => root.unmount()); host.remove(); vi.restoreAllMocks(); vi.useRealTimers(); });
it("refreshes visible progress after stream completion and again after provider settling", async () => {
  invoke.mockResolvedValue({ minutes: 110 });
  await act(async () => callbacks.get("stream-session-ended")!());
  expect(invoke).toHaveBeenLastCalledWith("get_xbox_game_progress", { gameName: "Requiem", offline: false, refresh: true });
  expect(host.textContent).toBe("110");
  invoke.mockResolvedValue({ minutes: 115 });
  await act(async () => { await vi.advanceTimersByTimeAsync(30_000); });
  expect(host.textContent).toBe("115");
});
it("uses offline cache on return and keeps late results from replacing another title", async () => {
  let resolve!: (data: { minutes: number }) => void;
  invoke.mockImplementationOnce(() => new Promise(r => { resolve = r; }));
  await act(async () => window.dispatchEvent(new Event("focus")));
  vi.spyOn(navigator, "onLine", "get").mockReturnValue(false);
  invoke.mockResolvedValue({ minutes: 20 });
  await act(async () => root.render(<Harness name="Other game" />));
  expect(invoke).toHaveBeenLastCalledWith("get_xbox_game_progress", { gameName: "Other game", offline: true, refresh: false });
  await act(async () => resolve({ minutes: 999 }));
  expect(host.textContent).toBe("20");
});

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { beforeEach, afterEach, expect, it, vi } from "vitest";
import { useStreamLibraryRefresh } from "./useStreamLibraryRefresh";

const { callbacks, unlisten } = vi.hoisted(() => ({ callbacks: new Map<string, (event: any) => void>(), unlisten: vi.fn() }));
vi.mock("@tauri-apps/api/event", () => ({ listen: vi.fn(async (name, callback) => { callbacks.set(name, callback); return unlisten; }) }));
let root: Root;
let host: HTMLDivElement;
const refresh = vi.fn();
function Harness() { useStreamLibraryRefresh(refresh); return null; }
beforeEach(async () => {
  vi.useFakeTimers(); vi.clearAllMocks(); callbacks.clear();
  vi.spyOn(navigator, "onLine", "get").mockReturnValue(true);
  (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
  host = document.createElement("div"); document.body.append(host); root = createRoot(host);
  await act(async () => root.render(<Harness />));
});
afterEach(() => { act(() => root.unmount()); host.remove(); vi.restoreAllMocks(); vi.useRealTimers(); });
const ended = (session: number) => callbacks.get("stream-session-ended")!({ payload: { provider: "moonlight", session } });

it("refreshes quietly after a native end event without a return-artwork event", async () => {
  ended(1);
  expect(refresh).toHaveBeenCalledExactlyOnceWith({ background: true });
  await vi.advanceTimersByTimeAsync(30_000);
  expect(refresh).toHaveBeenCalledTimes(2);
});
it("ignores duplicate/older completions and replaces the delayed refresh for a newer session", async () => {
  ended(2); ended(2); ended(1);
  expect(refresh).toHaveBeenCalledTimes(1);
  await vi.advanceTimersByTimeAsync(20_000);
  ended(3);
  await vi.advanceTimersByTimeAsync(10_000);
  expect(refresh).toHaveBeenCalledTimes(2);
  await vi.advanceTimersByTimeAsync(20_000);
  expect(refresh).toHaveBeenCalledTimes(3);
});
it("defers offline completion until reconnection", async () => {
  vi.spyOn(navigator, "onLine", "get").mockReturnValue(false);
  ended(1);
  expect(refresh).not.toHaveBeenCalled();
  vi.spyOn(navigator, "onLine", "get").mockReturnValue(true);
  window.dispatchEvent(new Event("online"));
  expect(refresh).toHaveBeenCalledExactlyOnceWith({ background: true });
});
it("does not leave a delayed refresh or active listener after unmount", async () => {
  ended(1);
  act(() => root.render(null));
  ended(2);
  await vi.advanceTimersByTimeAsync(30_000);
  expect(refresh).toHaveBeenCalledTimes(1);
  expect(unlisten).toHaveBeenCalledOnce();
});

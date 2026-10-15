import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { withStreamCover } from "./StreamLaunchCover";
const { invoke } = vi.hoisted(() => ({ invoke: vi.fn(async () => undefined) }));
vi.mock("@tauri-apps/api/core", () => ({ invoke }));

describe("stream artwork handoff", () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.clearAllMocks(); vi.restoreAllMocks(); vi.useRealTimers(); });
  it("keeps the artwork opaque through native focus transfer before removing it", async () => {
    let ready!: () => void; let focused!: () => void;
    const states: unknown[] = [];
    const listener = (event: Event) => states.push((event as CustomEvent).detail);
    window.addEventListener("crete-stream-cover", listener);
    invoke.mockImplementation(async command => {
      if (command === "wait_stream_window") await new Promise<void>(resolve => { ready = resolve; });
      if (command === "focus_stream_window") await new Promise<void>(resolve => { focused = resolve; });
    });
    const pending = withStreamCover({ id: "re2", name: "Resident Evil 2" }, "moonlight", async () => undefined);
    await vi.advanceTimersByTimeAsync(50);
    expect(invoke).toHaveBeenCalledWith("wait_stream_window", { provider: "moonlight" });
    expect(states).toHaveLength(1);
    ready();
    await vi.advanceTimersByTimeAsync(10_000);
    expect(invoke).toHaveBeenCalledWith("focus_stream_window", { provider: "moonlight" });
    expect(states).toHaveLength(1);
    expect(invoke).not.toHaveBeenCalledWith("set_launch_cover", { active: false });
    focused(); await pending;
    expect(states[states.length - 1]).toBeNull();
    expect(invoke).toHaveBeenLastCalledWith("set_launch_cover", { active: false });
    window.removeEventListener("crete-stream-cover", listener);
  });
  it("releases the cover after a failed launch and keeps the error", async () => {
    invoke.mockResolvedValue(undefined);
    const result = expect(withStreamCover({ id: "re2", name: "Resident Evil 2" }, "moonlight", async () => { throw new Error("Host unavailable"); })).rejects.toThrow("Host unavailable");
    await vi.advanceTimersByTimeAsync(50);
    await result;
    expect(invoke).toHaveBeenLastCalledWith("set_launch_cover", { active: false });
    expect(invoke).not.toHaveBeenCalledWith("focus_stream_window", expect.anything());
  });
  it("paints twice before opening the external client", async () => {
    invoke.mockResolvedValue(undefined);
    const frames: FrameRequestCallback[] = [];
    const raf = vi.spyOn(window, "requestAnimationFrame").mockImplementation(callback => { frames.push(callback); return frames.length; });
    const launch = vi.fn(async () => undefined);
    try {
      const pending = withStreamCover({ id: "re2", name: "Resident Evil 2" }, "geforceNow", launch);
      await vi.waitFor(() => expect(frames).toHaveLength(1));
      expect(launch).not.toHaveBeenCalled();
      frames.shift()!(0);
      expect(launch).not.toHaveBeenCalled();
      frames.shift()!(16);
      await vi.advanceTimersByTimeAsync(10_000);
      await pending;
      expect(launch).toHaveBeenCalledOnce();
      expect(invoke).toHaveBeenCalledWith("set_launch_cover", { active: true, streamProvider: "geforceNow" });
    } finally { raf.mockRestore(); }
  });
  it("launches immediately after paint but waits ten seconds before handoff", async () => {
    invoke.mockResolvedValue(true);
    const launch = vi.fn(async () => undefined);
    const pending = withStreamCover({ id: "re2", name: "Resident Evil 2" }, "geforceNow", launch);
    await vi.advanceTimersByTimeAsync(50);
    expect(launch).toHaveBeenCalledOnce();
    await vi.advanceTimersByTimeAsync(9949);
    expect(invoke).not.toHaveBeenCalledWith("focus_stream_window", expect.anything());
    await vi.advanceTimersByTimeAsync(1);
    await pending;
    expect(invoke).toHaveBeenCalledWith("focus_stream_window", { provider: "geforceNow" });
  });
  it("reveals a client needing attention without waiting for the minimum", async () => {
    invoke.mockImplementation(async command => command === "wait_stream_window" ? false : undefined);
    const pending = withStreamCover({ id: "re2", name: "Resident Evil 2" }, "geforceNow", async () => undefined);
    await vi.advanceTimersByTimeAsync(50);
    await pending;
    expect(performance.now()).toBeLessThan(10_000);
    expect(invoke).toHaveBeenCalledWith("focus_stream_window", { provider: "geforceNow" });
  });
});

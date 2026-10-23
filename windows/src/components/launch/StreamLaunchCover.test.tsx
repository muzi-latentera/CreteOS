import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { withStreamCover } from "./StreamLaunchCover";
const { invoke, callbacks } = vi.hoisted(() => ({ invoke: vi.fn(async () => undefined), callbacks: new Map<string, () => void>() }));
vi.mock("@tauri-apps/api/core", () => ({ invoke }));
vi.mock("@tauri-apps/api/event", () => ({ listen: vi.fn(async (name, callback) => { callbacks.set(name, callback); return () => callbacks.delete(name); }) }));

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
    expect(invoke).toHaveBeenLastCalledWith("focus_stream_window", { provider: "moonlight" });
    window.removeEventListener("crete-stream-cover", listener);
  });
  it("releases the cover after a failed launch and keeps the error", async () => {
    invoke.mockResolvedValue(undefined);
    const result = expect(withStreamCover({ id: "re2", name: "Resident Evil 2" }, "moonlight", async () => { throw new Error("Host unavailable"); })).rejects.toThrow("Host unavailable");
    await vi.advanceTimersByTimeAsync(50);
    await result;
    expect(invoke).toHaveBeenCalledWith("set_launch_cover", { active: false });
    expect(invoke).toHaveBeenLastCalledWith("present_stream_failure", {provider:"moonlight"});
    expect(invoke).not.toHaveBeenCalledWith("focus_stream_window", expect.anything());
    expect(invoke).not.toHaveBeenCalledWith("record_stream_launch", expect.anything());
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
      frames.shift()!(32); frames.shift()!(48);
      await vi.advanceTimersByTimeAsync(10_000);
      await pending;
      expect(launch).toHaveBeenCalledOnce();
      expect(invoke).toHaveBeenCalledWith("set_launch_cover", { active: true, streamProvider: "geforceNow" });
    } finally { raf.mockRestore(); }
  });
  it("records accepted streamed launches and finishes dialog cleanup before foreground transfer", async () => {
    const order: string[] = [];
    const entry = { id: "oni", name: "Onimusha", launched_at: 1792079000 };
    const history = vi.fn(); window.addEventListener("crete-play-history-changed", history);
    invoke.mockImplementation(async command => {
      if (command === "record_stream_launch") return entry;
      if (command === "focus_stream_window") order.push("focus");
      return true;
    });
    const pending = withStreamCover({ id: "oni", name: "Onimusha", launch_path: "steam://rungameid/2638890" }, "moonlight", async () => undefined, () => order.push("dialog-closed"));
    await vi.advanceTimersByTimeAsync(10_100); await pending;
    expect(invoke).toHaveBeenCalledWith("record_stream_launch", { id: "oni", name: "Onimusha", launchPath: "steam://rungameid/2638890" });
    expect(history).toHaveBeenCalledOnce();
    expect(order).toEqual(["dialog-closed", "focus"]);
    expect(invoke).toHaveBeenLastCalledWith("focus_stream_window", { provider: "moonlight" });
    window.removeEventListener("crete-play-history-changed", history);
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
    await vi.advanceTimersByTimeAsync(50); // Paint dialog removal before native focus.
    await pending;
    expect(invoke).toHaveBeenCalledWith("focus_stream_window", { provider: "geforceNow" });
  });
  it("reveals a client needing attention without waiting for the minimum", async () => {
    invoke.mockImplementation(async command => command === "wait_stream_window" ? false : undefined);
    const pending = withStreamCover({ id: "re2", name: "Resident Evil 2" }, "geforceNow", async () => undefined);
    await vi.advanceTimersByTimeAsync(100);
    await pending;
    expect(performance.now()).toBeLessThan(10_000);
    expect(invoke).toHaveBeenCalledWith("focus_stream_window", { provider: "geforceNow" });
  });
  it("reveals a host prompt while the game launch is still waiting", async () => {
    let finish!: () => void;
    invoke.mockResolvedValue(true);
    const closeDialog = vi.fn();
    const pending = withStreamCover({ id: "oni", name: "Onimusha" }, "moonlight", () => new Promise<void>(resolve => { finish = resolve; }), closeDialog);
    await vi.advanceTimersByTimeAsync(100);
    callbacks.get("host-launch-attention")?.();
    await vi.advanceTimersByTimeAsync(50);
    expect(closeDialog).toHaveBeenCalledOnce();
    expect(invoke).toHaveBeenCalledWith("focus_stream_window", { provider: "moonlight" });
    finish(); await pending;
    expect(callbacks.has("host-launch-attention")).toBe(false);
  });
  it("waits for Luna media readiness rather than the PWA launch promise", async () => {
    let ready!: (value:boolean)=>void;
    invoke.mockImplementation(async command => command === "wait_stream_window" ? new Promise<boolean>(resolve=>{ready=resolve;}) : undefined);
    const launch=vi.fn(async()=>undefined);
    const pending=withStreamCover({id:"luna:test",name:"Luna game"},"luna",launch);
    await vi.advanceTimersByTimeAsync(12_000);
    expect(launch).toHaveBeenCalledOnce();
    expect(invoke).not.toHaveBeenCalledWith("focus_stream_window",expect.anything());
    ready(true);await vi.advanceTimersByTimeAsync(100);await pending;
    expect(invoke).toHaveBeenLastCalledWith("focus_stream_window",{provider:"luna"});
  });

});

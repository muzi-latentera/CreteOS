import { afterEach, describe, expect, it, vi } from "vitest";
import { withStreamCover } from "./StreamLaunchCover";
const { invoke } = vi.hoisted(() => ({ invoke: vi.fn(async () => undefined) }));
vi.mock("@tauri-apps/api/core", () => ({ invoke }));

describe("stream artwork handoff", () => {
  afterEach(() => { vi.clearAllMocks(); vi.useRealTimers(); });
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
    await vi.waitFor(() => expect(invoke).toHaveBeenCalledWith("wait_stream_window", { provider: "moonlight" }));
    expect(states).toHaveLength(1);
    ready();
    await vi.waitFor(() => expect(invoke).toHaveBeenCalledWith("focus_stream_window", { provider: "moonlight" }));
    expect(states).toHaveLength(1);
    expect(invoke).not.toHaveBeenCalledWith("set_launch_cover", { active: false });
    focused(); await pending;
    expect(states[states.length - 1]).toBeNull();
    expect(invoke).toHaveBeenLastCalledWith("set_launch_cover", { active: false });
    window.removeEventListener("crete-stream-cover", listener);
  });
  it("releases the cover after a failed launch and keeps the error", async () => {
    invoke.mockResolvedValue(undefined);
    await expect(withStreamCover({ id: "re2", name: "Resident Evil 2" }, "moonlight", async () => { throw new Error("Host unavailable"); })).rejects.toThrow("Host unavailable");
    expect(invoke).toHaveBeenLastCalledWith("set_launch_cover", { active: false });
    expect(invoke).not.toHaveBeenCalledWith("focus_stream_window", expect.anything());
  });
});

import { afterEach, describe, expect, it, vi } from "vitest";
import { withStreamCover } from "./StreamLaunchCover";
const { invoke } = vi.hoisted(() => ({ invoke: vi.fn(async () => undefined) }));
vi.mock("@tauri-apps/api/core", () => ({ invoke }));

describe("stream artwork handoff", () => {
  afterEach(() => { vi.clearAllMocks(); vi.useRealTimers(); });
  it("waits for client readiness and fade before releasing the cover", async () => {
    vi.useFakeTimers();
    let ready!: () => void;
    invoke.mockImplementation(async command => { if (command === "wait_stream_window") await new Promise<void>(resolve => { ready = resolve; }); });
    const launch = vi.fn(async () => undefined);
    const pending = withStreamCover({ id: "re2", name: "Resident Evil 2" }, "moonlight", launch);
    await vi.waitFor(() => expect(invoke).toHaveBeenCalledWith("wait_stream_window", { provider: "moonlight" }));
    expect(invoke).not.toHaveBeenCalledWith("set_launch_cover", { active: false });
    ready(); await vi.advanceTimersByTimeAsync(649);
    expect(invoke).not.toHaveBeenCalledWith("focus_stream_window", expect.anything());
    await vi.advanceTimersByTimeAsync(1); await pending;
    expect(invoke).toHaveBeenCalledWith("set_launch_cover", { active: false });
    expect(invoke).toHaveBeenLastCalledWith("focus_stream_window", { provider: "moonlight" });
  });
  it("releases the cover after a failed launch and keeps the error", async () => {
    invoke.mockResolvedValue(undefined);
    await expect(withStreamCover({ id: "re2", name: "Resident Evil 2" }, "moonlight", async () => { throw new Error("Host unavailable"); })).rejects.toThrow("Host unavailable");
    expect(invoke).toHaveBeenLastCalledWith("set_launch_cover", { active: false });
    expect(invoke).not.toHaveBeenCalledWith("focus_stream_window", expect.anything());
  });
});

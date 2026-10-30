import { afterEach, describe, expect, it, vi } from "vitest";
import { refreshLibrarySources, SOURCE_REFRESH_TIMEOUT_MS } from "./libraryRefresh";

const defaults = () => ({ steamConnected: true, refreshSteamOwned: vi.fn().mockResolvedValue(149), refreshSteamStatus: vi.fn(), refreshGeForceCatalog: vi.fn().mockResolvedValue(undefined), refreshLocalLibrary: vi.fn().mockResolvedValue(undefined) });
afterEach(() => vi.useRealTimers());
describe("refreshLibrarySources", () => {
  it("refreshes both remote sources concurrently and rescans only after they settle", async () => {
    const options = defaults(); const calls: string[] = [];
    let steam!: () => void, geforce!: () => void;
    options.refreshSteamOwned.mockImplementation(() => new Promise<void>(resolve => { steam = () => { calls.push("steam"); resolve(); }; }));
    options.refreshGeForceCatalog.mockImplementation(() => new Promise<void>(resolve => { geforce = () => { calls.push("geforce"); resolve(); }; }));
    options.refreshSteamStatus.mockImplementation(() => calls.push("status"));
    options.refreshLocalLibrary.mockImplementation(async () => { calls.push("local"); });
    const pending = refreshLibrarySources(options); await Promise.resolve();
    expect(options.refreshSteamOwned).toHaveBeenCalledOnce(); expect(options.refreshGeForceCatalog).toHaveBeenCalledOnce();
    expect(options.refreshLocalLibrary).not.toHaveBeenCalled();
    steam(); await Promise.resolve(); expect(options.refreshLocalLibrary).not.toHaveBeenCalled();
    geforce(); expect(await pending).toEqual({ failedSources: [] });
    expect(calls).toEqual(["steam", "status", "geforce", "local"]);
  });
  it("reports failed sources without preventing the local rescan", async () => {
    const options = defaults(); options.refreshSteamOwned.mockRejectedValue(new Error("auth failed")); options.refreshGeForceCatalog.mockRejectedValue(new Error("offline"));
    expect(await refreshLibrarySources(options)).toEqual({ failedSources: ["Steam", "GeForce NOW"] });
    expect(options.refreshLocalLibrary).toHaveBeenCalledOnce(); expect(options.refreshSteamStatus).not.toHaveBeenCalled();
  });
  it("waits for the local rescan and reports its failure", async () => {
    const options = defaults(); let finish!: () => void;
    options.refreshLocalLibrary.mockImplementation(() => new Promise<void>((_, reject) => { finish = () => reject(new Error("scan failed")); }));
    const pending = refreshLibrarySources(options); await vi.waitFor(() => expect(options.refreshLocalLibrary).toHaveBeenCalledOnce());
    finish(); expect(await pending).toEqual({ failedSources: ["Local library"] });
  });
  it("still refreshes GeForce NOW when Steam is disconnected", async () => {
    const options = { ...defaults(), steamConnected: false };
    expect(await refreshLibrarySources(options)).toEqual({ failedSources: [] });
    expect(options.refreshSteamOwned).not.toHaveBeenCalled(); expect(options.refreshGeForceCatalog).toHaveBeenCalledOnce();
  });
  it("bounds an unresponsive source and permits the scan to finish", async () => {
    vi.useFakeTimers(); const options = defaults(); options.refreshSteamOwned.mockImplementation(() => new Promise(() => {}));
    const pending = refreshLibrarySources(options); await vi.advanceTimersByTimeAsync(SOURCE_REFRESH_TIMEOUT_MS);
    expect(await pending).toEqual({ failedSources: ["Steam"] }); expect(options.refreshLocalLibrary).toHaveBeenCalledOnce();
  });
});

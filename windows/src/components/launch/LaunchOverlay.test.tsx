import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { LaunchOverlay } from "./LaunchOverlay";
import type { App, AccentColors } from "../../types";

const { callbacks, invoke, rumble } = vi.hoisted(() => ({
  callbacks: new Map<string, (event: { payload: unknown }) => unknown>(),
  invoke: vi.fn(),
  rumble: vi.fn(),
}));
vi.mock("@tauri-apps/api/core", () => ({ invoke }));
vi.mock("@tauri-apps/api/event", () => ({
  listen: vi.fn(async (name, callback) => {
    callbacks.set(name, callback);
    return () => callbacks.delete(name);
  }),
}));
vi.mock("react-i18next", () => ({ useTranslation: () => ({ t: (key: string) => key }) }));
vi.mock("../../utils/gamepad", () => ({
  getBestGamepad: () => null, readGpState: vi.fn(), shouldHandleDirectionRepeat: vi.fn(), rumble,
}));

describe("Steam launch confirmation", () => {
  let container: HTMLDivElement;
  let root: Root;
  const onSuccess = vi.fn();
  const onDone = vi.fn();
  const path = "steam://rungameid/3527290";

  beforeEach(async () => {
    vi.clearAllMocks();
    vi.useFakeTimers();
    callbacks.clear();
    vi.stubGlobal("IS_REACT_ACT_ENVIRONMENT", true);
    vi.stubGlobal("requestAnimationFrame", vi.fn(() => 1));
    vi.stubGlobal("cancelAnimationFrame", vi.fn());
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
    await act(async () => root.render(<LaunchOverlay
      app={{ id: "peak", name: "PEAK", app_type: "game", source: "steam", launch_path: path } as App}
      gameArt={{}} customArt={{}} accent={{ primary: "#fff", glow: "rgba(0,0,0," } as AccentColors}
      onSuccess={onSuccess} onDone={onDone}
    />));
  });

  afterEach(() => {
    act(() => root.unmount());
    container.remove();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  async function emit(name: string, payload: unknown = path) {
    await act(async () => { await callbacks.get(name)?.({ payload }); });
  }

  it.each([
    ["syncing_cloud", "launch.syncingCloud"],
    ["updating_game", "launch.updatingGame"],
    ["waiting_for_window", "launch.waitingForWindow"],
  ])("shows the %s Steam phase for the current launch", async (phase, translationKey) => {
    await emit("launch-phase", { launchPath: path, phase });
    expect(container.textContent).toContain(translationKey);
  });

  it("keeps a timeout unconfirmed without success feedback or automatic dismissal", async () => {
    await emit("launch-unconfirmed");
    act(() => vi.advanceTimersByTime(5000));
    expect(container.textContent).toContain("launch.unconfirmed");
    expect(onSuccess).not.toHaveBeenCalled();
    expect(rumble).not.toHaveBeenCalled();
    expect(onDone).not.toHaveBeenCalled();
  });

  it.each(["absent", "error"])("does not celebrate when verification is %s", async (outcome) => {
    if (outcome === "error") invoke.mockRejectedValueOnce(new Error("IPC unavailable"));
    else invoke.mockResolvedValueOnce({ focused: false, running: false });
    await emit("launch-success");
    expect(container.textContent).toContain("launch.unconfirmed");
    expect(onSuccess).not.toHaveBeenCalled();
    expect(rumble).not.toHaveBeenCalled();
  });

  it("celebrates a confirmed game and dismisses", async () => {
    invoke.mockResolvedValueOnce({ focused: true, running: true });
    await emit("launch-success");
    expect(onSuccess).toHaveBeenCalledOnce();
    expect(rumble).toHaveBeenCalledOnce();
    act(() => vi.advanceTimersByTime(700));
    expect(onDone).toHaveBeenCalledOnce();
  });

  it("ignores completion from a different launch", async () => {
    await emit("launch-success", "steam://rungameid/123");
    await emit("launch-unconfirmed", "steam://rungameid/123");
    expect(container.textContent).toContain("launch.contactingSteam");
    expect(invoke).not.toHaveBeenCalledWith("check_launch_focus", expect.anything());
    expect(onSuccess).not.toHaveBeenCalled();
  });
});

describe("app launch confirmation", () => {
  let container: HTMLDivElement;
  let root: Root;
  const onSuccess = vi.fn();
  const onDone = vi.fn();
  const path = "C:\\Windows\\System32\\calc.exe";

  beforeEach(async () => {
    vi.clearAllMocks();
    vi.useFakeTimers();
    callbacks.clear();
    vi.stubGlobal("IS_REACT_ACT_ENVIRONMENT", true);
    vi.stubGlobal("requestAnimationFrame", vi.fn(() => 1));
    vi.stubGlobal("cancelAnimationFrame", vi.fn());
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
    await act(async () => root.render(<LaunchOverlay
      app={{ id: "calculator", name: "Calculator", app_type: "app", launch_path: path } as App}
      gameArt={{}} customArt={{}} accent={{ primary: "#fff", glow: "rgba(0,0,0," } as AccentColors}
      onSuccess={onSuccess} onDone={onDone}
    />));
  });

  afterEach(() => {
    act(() => root.unmount());
    container.remove();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  async function emit(name: string, payload: unknown = path) {
    await act(async () => { await callbacks.get(name)?.({ payload }); });
  }

  it("keeps an unconfirmed app overlay until dismissed", async () => {
    invoke.mockResolvedValueOnce({ focused: false, running: false });
    await emit("launch-success");
    act(() => vi.advanceTimersByTime(5000));
    expect(container.textContent).toContain("launch.unconfirmedApp");
    expect(onSuccess).not.toHaveBeenCalled();
    expect(onDone).not.toHaveBeenCalled();
  });

  it("celebrates a confirmed app and dismisses", async () => {
    invoke.mockResolvedValueOnce({ focused: true, running: true });
    await emit("launch-success");
    expect(onSuccess).toHaveBeenCalledOnce();
    act(() => vi.advanceTimersByTime(700));
    expect(onDone).toHaveBeenCalledOnce();
  });
});


describe("Crete minimal game launch cover", () => {
  it.each(["steam", "cloud"])("keeps %s artwork visible until native handoff completes", async (source) => {
    vi.clearAllMocks(); callbacks.clear();
    vi.stubGlobal("IS_REACT_ACT_ENVIRONMENT", true);
    vi.stubGlobal("requestAnimationFrame", vi.fn(() => 1));
    vi.stubGlobal("cancelAnimationFrame", vi.fn());
    const node = document.createElement("div"); document.body.appendChild(node);
    const root = createRoot(node); const done = vi.fn();
    let release!: (value: boolean) => void;
    const path = source === "cloud" ? "https://luna.amazon.co.uk/play?asin=test" : "steam://rungameid/620";
    invoke.mockImplementation(async command => {
      if (command === "check_launch_focus") return { running: true, focused: true };
      if (command === "handoff_launched_app") return await new Promise<boolean>(resolve => { release = resolve; });
      return undefined;
    });
    await act(async () => root.render(<LaunchOverlay minimal app={{ id: source, name: "Test Game", source, app_type: "game", launch_path: path } as App} gameArt={{}} customArt={{}} heroArt="https://fixture.test/hero.jpg" accent={{ primary: "#fff", glow: "rgba(0,0,0," } as AccentColors} onDone={done} />));
    expect(node.querySelector('[data-game-launch-cover]')).not.toBeNull();
    expect(node.textContent).not.toContain("Test Game");
    expect(node.querySelector("button")).toBeNull();
    let pending: unknown;
    await act(async () => { pending = callbacks.get("launch-success")?.({ payload: path }); await Promise.resolve(); });
    expect(done).not.toHaveBeenCalled();
    expect(node.querySelector('[data-game-launch-cover]')).not.toBeNull();
    await act(async () => { release(true); await pending; });
    expect(done).toHaveBeenCalledOnce();
    act(() => root.unmount()); node.remove(); vi.unstubAllGlobals();
  });
});

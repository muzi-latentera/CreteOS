import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { StreamLaunchCover, withStreamCover } from "./StreamLaunchCover";
import type { App } from "../../types";

const { invoke, callbacks } = vi.hoisted(() => ({ invoke: vi.fn(), callbacks: new Map<string, (event: { payload: unknown }) => void>() }));
vi.mock("@tauri-apps/api/core", () => ({ invoke }));
vi.mock("@tauri-apps/api/event", () => ({ listen: vi.fn(async (name, callback) => { callbacks.set(name, callback); return () => callbacks.delete(name); }) }));
vi.mock("react-i18next", () => ({ useTranslation: () => ({ t: (key: string) => key }) }));
vi.mock("../../utils/gamepad", () => ({ getBestGamepad: () => null, readGpState: () => ({}) }));
let container: HTMLDivElement;
let root: Root;
async function phase(phase: string, session = 1) {
  await act(async () => callbacks.get("stream-return-phase")?.({ payload: { provider: "moonlight", phase, session } }));
}
beforeEach(async () => {
  vi.useFakeTimers(); vi.clearAllMocks(); callbacks.clear();
  vi.stubGlobal("IS_REACT_ACT_ENVIRONMENT", true);
  invoke.mockResolvedValue(true);
  const pending = withStreamCover({ id: "re2", name: "Resident Evil 2" } as App, "moonlight", async () => undefined);
  await vi.advanceTimersByTimeAsync(10_100); await pending;
  container = document.createElement("div"); document.body.appendChild(container); root = createRoot(container);
  await act(async () => root.render(<StreamLaunchCover heroArt={{ re2: "https://fixture.test/re2.jpg" }} customHeroArt={{}} />));
  invoke.mockClear();
});
afterEach(async () => { await act(async () => root.unmount()); container.remove(); vi.useRealTimers(); vi.unstubAllGlobals(); });
it("keeps painted return artwork until client cleanup finishes", async () => {
  await phase("ending");
  expect(container.querySelector("[data-stream-launch-cover]")).not.toBeNull();
  expect(container.textContent).not.toContain("Resident Evil");
  expect(container.querySelector("[data-launch-message]")).toBeNull();
  expect(invoke).not.toHaveBeenCalledWith("present_stream_return_cover", expect.anything());
  await act(async () => { await vi.advanceTimersByTimeAsync(50); });
  expect(invoke).toHaveBeenCalledWith("present_stream_return_cover", { provider: "moonlight", session: 1 });
  expect(container.querySelector("[data-stream-launch-cover]")).not.toBeNull();
  await phase("ended");
  expect(container.querySelector("[data-stream-launch-cover]")).toBeNull();
});
it("clears failed-launch artwork after the error has been readable", async () => {
  await act(async()=>window.dispatchEvent(new CustomEvent("crete-stream-failure",{detail:"Host unavailable"})));
  expect(container.querySelector('[role="alert"]')?.textContent).toBe("Host unavailable");
  await act(async()=>{await vi.advanceTimersByTimeAsync(12_050);});
  expect(container.querySelector("[data-stream-launch-cover]")).toBeNull();
});
it("ignores a stale end event and fails open if the client never completes", async () => {
  invoke.mockImplementation(async command => { if (command === "present_stream_return_cover") await new Promise(() => {}); return true; });
  await phase("ending", 2);
  await phase("ended", 1);
  expect(container.querySelector("[data-stream-launch-cover]")).not.toBeNull();
  await act(async () => { await vi.advanceTimersByTimeAsync(12_050); });
  expect(container.querySelector("[data-stream-launch-cover]")).toBeNull();
  expect(invoke).toHaveBeenCalledWith("set_launch_cover", { active: false });
});
it("does not present late artwork when the session ended before its paint acknowledgement", async () => {
  await phase("ending"); await phase("ended");
  await act(async () => { await vi.advanceTimersByTimeAsync(50); });
  expect(invoke).not.toHaveBeenCalledWith("present_stream_return_cover", expect.anything());
  expect(container.querySelector("[data-stream-launch-cover]")).toBeNull();
});
it("cancels a pending launch immediately with Escape and ignores a late backend result", async () => {
  let finish!: () => void;
  let pending!: Promise<void>;
  const handoff = vi.fn();
  await act(async () => {
    pending = withStreamCover({ id: "oni", name: "Onimusha" } as App, "moonlight", () => new Promise<void>(resolve => { finish = resolve; }), handoff);
    await vi.advanceTimersByTimeAsync(50);
  });
  expect(container.querySelector("button")).toBeNull();
  expect(container.textContent).not.toContain("Cancel launch");
  await act(async () => { window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" })); await pending; });
  expect(container.querySelector("[data-stream-launch-cover]")).toBeNull();
  expect(invoke).toHaveBeenCalledWith("cancel_stream_launch");
  invoke.mockClear();
  await act(async () => { finish(); await vi.advanceTimersByTimeAsync(20_000); });
  expect(handoff).not.toHaveBeenCalled();
  expect(invoke).not.toHaveBeenCalledWith("focus_stream_window", expect.anything());
  expect(invoke).not.toHaveBeenCalledWith("record_stream_launch", expect.anything());
});
it("handles native B cancellation when Moonlight owns input focus", async () => {
  let pending!: Promise<void>;
  await act(async () => {
    pending = withStreamCover({ id: "oni", name: "Onimusha" } as App, "moonlight", () => new Promise(() => {}));
    await vi.advanceTimersByTimeAsync(50);
  });
  await act(async () => { callbacks.get("stream-launch-cancel")?.({ payload: null }); await pending; });
  expect(container.querySelector("[data-stream-launch-cover]")).toBeNull();
  expect(invoke).not.toHaveBeenCalledWith("focus_stream_window", expect.anything());
});
it("releases the duplicate launch guard through native B without any cancel prompt", async () => {
  let pending!: Promise<void>;
  await act(async () => {
    pending = withStreamCover({ id: "oni", name: "Onimusha" } as App, "moonlight", () => new Promise(() => {}));
    await vi.advanceTimersByTimeAsync(50);
  });
  await expect(withStreamCover({ id: "oni", name: "Onimusha" } as App, "moonlight", async () => undefined)).rejects.toThrow("already in progress");
  expect(container.querySelector("button")).toBeNull();
  await act(async () => { callbacks.get("stream-launch-cancel")?.({ payload: null }); await pending; });
  expect(container.querySelector("[data-stream-launch-cover]")).toBeNull();
  const next = withStreamCover({ id: "oni", name: "Onimusha" } as App, "moonlight", async () => undefined);
  await act(async () => { await vi.advanceTimersByTimeAsync(10_100); await next; });
});

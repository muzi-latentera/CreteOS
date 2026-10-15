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
it("paints artwork before returning, then unlocks CreteOS while the client closes behind it", async () => {
  await phase("ending");
  expect(container.querySelector("[data-stream-launch-cover]")).not.toBeNull();
  expect(container.textContent).not.toContain("Resident Evil");
  expect(container.querySelector("[data-launch-message]")).toBeNull();
  expect(invoke).not.toHaveBeenCalledWith("present_stream_return_cover", expect.anything());
  await act(async () => { await vi.advanceTimersByTimeAsync(50); });
  expect(invoke).toHaveBeenCalledWith("present_stream_return_cover", { provider: "moonlight", session: 1 });
  expect(container.querySelector("[data-stream-launch-cover]")).toBeNull();
  await phase("ended");
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

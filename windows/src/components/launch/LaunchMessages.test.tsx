import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, expect, it, vi } from "vitest";
import { LaunchMessages, launchMessages } from "./LaunchMessages";
vi.mock("react-i18next", () => ({ useTranslation: () => ({ t: (_key: string, options: { defaultValue: string }) => options.defaultValue }) }));
afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });
it("rotates through fifteen different playful messages and cleans up its timer", async () => {
  vi.useFakeTimers(); vi.spyOn(Math, "random").mockReturnValue(0); vi.stubGlobal("IS_REACT_ACT_ENVIRONMENT", true);
  const container = document.createElement("div"); document.body.appendChild(container); const root = createRoot(container);
  await act(async () => root.render(<LaunchMessages />));
  expect(container.textContent).toContain("Contacting the mainframe");
  const seen = new Set<string>();
  for (let i = 0; i < launchMessages.length; i++) {
    seen.add(container.querySelector(".crete-launch-message")!.textContent!);
    await act(async () => { await vi.advanceTimersByTimeAsync(3500); });
  }
  expect(seen.size).toBe(15);
  expect(container.querySelector(".crete-launch-message")!.textContent).toBe(launchMessages[0]);
  await act(async () => root.unmount());
  expect(vi.getTimerCount()).toBe(0); container.remove();
});

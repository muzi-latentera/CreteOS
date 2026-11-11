import { act } from "react";
import { createRoot } from "react-dom/client";
import { expect, it, vi } from "vitest";
import { SplashScreen } from "./SplashScreen";

const { invoke } = vi.hoisted(() => ({ invoke: vi.fn().mockResolvedValue(undefined) }));
vi.mock("@tauri-apps/api/core", () => ({ invoke }));
vi.mock("@tauri-apps/api/event", () => ({ listen: vi.fn().mockResolvedValue(vi.fn()) }));
vi.mock("react-i18next", () => ({ useTranslation: () => ({ t: (_: string, fallback: string) => fallback }) }));
vi.mock("../onboarding/previews", () => ({ preloadOnboardingPreviews: vi.fn() }));

it("opens the splash quietly until sound is enabled and stops the cue when exiting", () => {
  const play = vi.fn().mockResolvedValue(undefined);
  const pause = vi.fn();
  const makeAudio = vi.fn(function () {
    return { play, pause, readyState: HTMLMediaElement.HAVE_FUTURE_DATA, removeEventListener: vi.fn() };
  });
  vi.stubGlobal("Audio", makeAudio);
  vi.stubGlobal("IS_REACT_ACT_ENVIRONMENT", true);
  const host = document.createElement("div");
  document.body.append(host);
  const root = createRoot(host);
  try {
    act(() => root.render(<SplashScreen exiting={false} soundEnabled={false} />));
    expect(invoke).toHaveBeenCalledWith("show_main_window");
    expect(makeAudio).not.toHaveBeenCalled();

    act(() => root.render(<SplashScreen exiting={false} soundEnabled />));
    expect(play).toHaveBeenCalledOnce();

    act(() => root.render(<SplashScreen exiting soundEnabled />));
    expect(pause).toHaveBeenCalledOnce();
    expect(play).toHaveBeenCalledOnce();
  } finally {
    act(() => root.unmount());
    host.remove();
    vi.unstubAllGlobals();
  }
});

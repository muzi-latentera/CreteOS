import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const invoke = vi.hoisted(() => vi.fn(async () => undefined));
vi.mock("@tauri-apps/api/core", () => ({ invoke }));

import { AppCrashScreen, ErrorBoundary, SectionCrashCard, recentReloads } from "./ErrorBoundary";

function Broken(): never { throw new Error("draw failed"); }

describe("error boundaries", () => {
  let host: HTMLDivElement;
  let root: Root;
  beforeEach(() => {
    vi.stubGlobal("IS_REACT_ACT_ENVIRONMENT", true);
    vi.stubGlobal("requestAnimationFrame", vi.fn(() => 1));
    vi.stubGlobal("cancelAnimationFrame", vi.fn());
    vi.spyOn(console, "error").mockImplementation(() => {});
    host = document.createElement("div"); document.body.append(host); root = createRoot(host);
    invoke.mockClear(); sessionStorage.clear();
  });
  afterEach(() => { act(() => root.unmount()); host.remove(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

  it("shows the fallback instead of a blank screen and logs the failure", () => {
    act(() => root.render(<ErrorBoundary area="game-details" fallback={() => <p>fallback shown</p>}><Broken /></ErrorBoundary>));
    expect(host.textContent).toBe("fallback shown");
    expect(invoke).toHaveBeenCalledWith("record_frontend_error", expect.objectContaining({ area: "game-details", message: "Error: draw failed" }));
  });

  it("closes a broken page with B or Escape", () => {
    const onClose = vi.fn();
    act(() => root.render(<SectionCrashCard title="Hades" onClose={onClose} />));
    act(() => { window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" })); });
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("stops restarting by itself after three restarts in two minutes", () => {
    const now = Date.now();
    sessionStorage.setItem("crete-error-reloads", JSON.stringify([now - 200_000, now - 1000, now - 2000]));
    expect(recentReloads(now)).toEqual([now - 1000, now - 2000]);
    act(() => root.render(<AppCrashScreen />));
    expect(host.textContent).toContain("Restarting in a few seconds");
    sessionStorage.setItem("crete-error-reloads", JSON.stringify([now - 1000, now - 2000, now - 3000]));
    act(() => root.render(<div />));
    act(() => root.render(<AppCrashScreen />));
    expect(host.textContent).toContain("restarted several times");
  });
});

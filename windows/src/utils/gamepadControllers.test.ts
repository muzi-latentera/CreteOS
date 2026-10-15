import { afterEach, describe, expect, it, vi } from "vitest";
import * as js from "./gamepad.js";
import * as ts from "./gamepad.ts";

afterEach(() => vi.restoreAllMocks());

describe.each([["JavaScript", js], ["TypeScript", ts]] as const)("%s docked controllers", (_name, api) => {
  it("follows external input in modals, retains it after release, then falls back on unplug", () => {
    const pad = (index: number, pressed = false) => ({ index, mapping: "standard", axes: [0, 0, 0, 0],
      buttons: Array.from({length: 16}, (_, i) => ({ pressed: pressed && i === 0 })) } as Gamepad);
    const builtIn = pad(0), external = pad(1, true);
    let pads: (Gamepad | null)[] = [builtIn, external];
    vi.spyOn(navigator, "getGamepads").mockImplementation(() => pads);
    expect(api.getBestGamepad()).toBe(external);
    external.buttons[0] = { pressed: false } as GamepadButton;
    expect(api.getBestGamepad()).toBe(external);
    pads = [builtIn, null];
    expect(api.getBestGamepad()).toBe(builtIn);
  });
});

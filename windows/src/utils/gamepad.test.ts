import { describe, expect, it, vi } from "vitest";
import {
  detectPlatform,
  readGpState,
  shouldHandleDirectionRepeat,
} from "./gamepad";

function gamepad({
  axes = [0, 0, 0, 0],
  pressed = [],
}: {
  axes?: number[];
  pressed?: number[];
} = {}): Gamepad {
  return {
    axes,
    buttons: Array.from({ length: 16 }, (_, index) => ({
      pressed: pressed.includes(index),
      touched: pressed.includes(index),
      value: pressed.includes(index) ? 1 : 0,
    })),
  } as Gamepad;
}

it("does not alternate controllers while a built-in press and an idle trigger axis coexist", async () => {
  vi.resetModules();
  const { getActiveGamepad } = await import("./gamepad");
  const builtIn = { ...gamepad({ pressed: [15] }), index: 0, id: "Built-in", mapping: "standard" } as Gamepad;
  const docked = { ...gamepad({ axes: [0, 0, 0, 0, -1, -1] }), index: 1, id: "Docked", mapping: "standard" } as Gamepad;
  const sample = vi.spyOn(navigator, "getGamepads").mockReturnValue([builtIn, docked]);
  try {
    expect(Array.from({ length: 12 }, () => getActiveGamepad()?.index)).toEqual(Array(12).fill(0));
  } finally { sample.mockRestore(); }
});

it("allows a fresh action to take over without bouncing back to an older held button", async () => {
  vi.resetModules();
  const { getActiveGamepad } = await import("./gamepad");
  const builtIn = { ...gamepad(), index: 0, id: "Built-in", mapping: "standard" } as Gamepad;
  const docked = { ...gamepad(), index: 1, id: "Docked", mapping: "standard" } as Gamepad;
  const sample = vi.spyOn(navigator, "getGamepads").mockReturnValue([builtIn, docked]);
  try {
    expect(getActiveGamepad()?.index).toBe(0);
    builtIn.buttons[15].pressed = true;
    expect(getActiveGamepad()?.index).toBe(0);
    docked.buttons[0].pressed = true;
    expect(Array.from({ length: 12 }, () => getActiveGamepad()?.index)).toEqual(Array(12).fill(1));
    builtIn.buttons[12].pressed = true;
    expect(Array.from({ length: 12 }, () => getActiveGamepad()?.index)).toEqual(Array(12).fill(0));
    sample.mockReturnValue([null, docked]);
    expect(getActiveGamepad()?.index).toBe(1);
  } finally { sample.mockRestore(); }
});

describe("readGpState", () => {
  it("maps standard face and d-pad buttons", () => {
    const state = readGpState(gamepad({ pressed: [0, 12, 15] }));

    expect(state.Enter).toBe(true);
    expect(state.ArrowUp).toBe(true);
    expect(state.ArrowRight).toBe(true);
    expect(state.Escape).toBe(false);
  });

  it("supports stick and hat-axis navigation", () => {
    const state = readGpState(gamepad({ axes: [-0.8, 0.7, 0, 0, 1, 0, 1, -1] }));

    expect(state.ArrowLeft).toBe(true);
    expect(state.ArrowDown).toBe(true);
    expect(state.ArrowRight).toBe(true);
    expect(state.ArrowUp).toBe(true);
  });
});

describe("shouldHandleDirectionRepeat", () => {
  it("fires immediately, then respects the initial and repeat delays", () => {
    const pressTime: Record<string, number> = {};
    const repeating: Record<string, boolean> = {};

    expect(shouldHandleDirectionRepeat("ArrowDown", { ArrowDown: true }, {}, 100, pressTime, repeating)).toBe(true);
    expect(shouldHandleDirectionRepeat("ArrowDown", { ArrowDown: true }, { ArrowDown: true }, 449, pressTime, repeating)).toBe(false);
    expect(shouldHandleDirectionRepeat("ArrowDown", { ArrowDown: true }, { ArrowDown: true }, 450, pressTime, repeating)).toBe(true);
    expect(shouldHandleDirectionRepeat("ArrowDown", { ArrowDown: true }, { ArrowDown: true }, 549, pressTime, repeating)).toBe(false);
    expect(shouldHandleDirectionRepeat("ArrowDown", { ArrowDown: true }, { ArrowDown: true }, 550, pressTime, repeating)).toBe(true);
  });
});

describe("detectPlatform", () => {
  it.each([
    ["Xbox Wireless Controller", "xbox"],
    ["Sony DualSense 054c", "ps"],
    ["Nintendo Switch Pro Controller", "switch"],
    ["Generic USB Controller", null],
  ])("detects %s", (id, expected) => {
    expect(detectPlatform(id)).toBe(expected);
  });
});

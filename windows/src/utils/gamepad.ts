import { noteControllerInput } from "./inputMode";
import { inputTraceGamepads } from "./inputDiagnostics";

export interface GpState {
  ArrowUp: boolean;
  ArrowDown: boolean;
  ArrowLeft: boolean;
  ArrowRight: boolean;
  Enter: boolean;
  Escape: boolean;
  ButtonX: boolean;
  ButtonY: boolean;
  BumperLeft: boolean;
  BumperRight: boolean;
  TriggerLeft: boolean;
  TriggerRight: boolean;
  Select: boolean;
  Start: boolean;
}

export const GAMEPAD_DIRECTION_KEYS = ["ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight"] as const;
export type GamepadDirectionKey = typeof GAMEPAD_DIRECTION_KEYS[number];

let _gamepadInputSuspended = false;
let _resumeWhenReleasedRaf: number | null = null;
let _resumeWhenReleasedTimer: number | null = null;

export function setGamepadInputSuspended(suspended: boolean): void {
  _gamepadInputSuspended = suspended;
  if (suspended) stopAllRumble();
}

export function isGamepadInputSuspended(): boolean {
  return _gamepadInputSuspended;
}

function anyGamepadButtonPressed(): boolean {
  return Array.from(navigator.getGamepads())
    .filter((g): g is Gamepad => g !== null)
    .some(gp => gp.buttons.some(button => button.pressed));
}

export function suspendGamepadInputUntilButtonsReleased(timeoutMs = 5000): void {
  if (_resumeWhenReleasedRaf != null) cancelAnimationFrame(_resumeWhenReleasedRaf);
  if (_resumeWhenReleasedTimer != null) window.clearTimeout(_resumeWhenReleasedTimer);
  _resumeWhenReleasedRaf = null;
  _resumeWhenReleasedTimer = null;
  setGamepadInputSuspended(true);

  const finish = () => {
    if (_resumeWhenReleasedRaf != null) cancelAnimationFrame(_resumeWhenReleasedRaf);
    if (_resumeWhenReleasedTimer != null) window.clearTimeout(_resumeWhenReleasedTimer);
    _resumeWhenReleasedRaf = null;
    _resumeWhenReleasedTimer = null;
    setGamepadInputSuspended(false);
  };

  const tick = () => {
    if (!anyGamepadButtonPressed()) {
      finish();
      return;
    }
    _resumeWhenReleasedRaf = requestAnimationFrame(tick);
  };

  _resumeWhenReleasedTimer = window.setTimeout(finish, timeoutMs);
  tick();
}

export function getRawBestGamepad(): Gamepad | null {
  const gps = Array.from(navigator.getGamepads()).filter((g): g is Gamepad => g !== null);
  return (
    gps.find(gp => gp.mapping === "standard" && gp.axes.length >= 4) ??
    gps.find(gp => gp.buttons.length >= 4    && gp.axes.length >= 4) ??
    gps[0] ?? null
  );
}

export function getBestGamepad(): Gamepad | null {
  // Modals and the main navigation must follow the same physical controller.
  // Choosing the first standard pad here strands docked users on the built-in pad.
  return getActiveGamepad();
}

let _lastActiveIdx: number | null = null;
const _previousControllerActivity = new Map<number, { identity: string; held: Set<string> }>();

function controllerActivity(gp: Gamepad): Set<string> {
  const held = new Set<string>();
  gp.buttons.forEach((button, index) => { if (button.pressed) held.add(`button:${index}`); });
  // Standard mapping exposes triggers as buttons. Additional resting trigger
  // axes (often -1) must not make an idle docked controller look active.
  gp.axes.slice(0, 4).forEach((axis, index) => {
    if (Math.abs(axis) > 0.5) held.add(`axis:${index}:${Math.sign(axis)}`);
  });
  if (gp.mapping !== "standard") {
    for (const index of [6, 7]) {
      const axis = gp.axes[index] ?? 0;
      if (Math.abs(axis) > 0.5) held.add(`hat:${index}:${Math.sign(axis)}`);
    }
  }
  return held;
}

/**
 * Returns the last actively used gamepad.
 * When multiple controllers are connected, switches on a fresh action, not a
 * repeatedly observed held state on another controller.
 * This ensures the controller being physically used always drives the UI.
 */
export function getActiveGamepad(): Gamepad | null {
  if (_gamepadInputSuspended) return null;

  const all = Array.from(navigator.getGamepads());
  inputTraceGamepads(all);
  const valid = all
    .map((g, i) => ({ g, i }))
    .filter((x): x is { g: Gamepad; i: number } =>
      x.g !== null && x.g.buttons.length >= 4 && x.g.axes.length >= 4
    );

  const connected = new Set(valid.map(({ i }) => i));
  for (const index of _previousControllerActivity.keys()) {
    if (!connected.has(index)) _previousControllerActivity.delete(index);
  }
  const sampled = valid.map(({ g, i }) => {
    const identity = `${g.id}|${g.mapping}|${g.buttons.length}|${g.axes.length}`;
    const held = controllerActivity(g);
    const previous = _previousControllerActivity.get(i);
    const fresh = previous?.identity === identity
      ? [...held].some(action => !previous.held.has(action)) : held.size > 0;
    _previousControllerActivity.set(i, { identity, held });
    return { g, i, held, fresh };
  });

  if (valid.length === 0) { _lastActiveIdx = null; return null; }

  // Single controller — no switching needed
  if (valid.length === 1) {
    _lastActiveIdx = valid[0].i;
    return valid[0].g;
  }

  // Switching back merely because the previous controller is still held
  // resets the navigation edge history every poll and repeats one physical tap.
  const takeover = sampled.find(pad => pad.i !== _lastActiveIdx && pad.fresh);
  if (takeover) {
    _lastActiveIdx = takeover.i;
    return takeover.g;
  }

  // Stay on the last active controller if it's still connected
  if (_lastActiveIdx !== null) {
    const current = valid.find(pad => pad.i === _lastActiveIdx)?.g;
    if (current) return current;
  }

  // Fallback: prefer standard mapping, then first valid
  const best = sampled.find(pad => pad.held.size > 0) ?? valid.find(x => x.g.mapping === "standard") ?? valid[0];
  _lastActiveIdx = best.i;
  return best.g;
}

export function readGpState(gp: Gamepad): GpState {
  const btn = (i: number) => !!gp.buttons[i]?.pressed;
  const legacyHat = gp.mapping !== "standard";
  const hatLeft  = legacyHat && (gp.axes[6] ?? 0) < -0.5;
  const hatRight = legacyHat && (gp.axes[6] ?? 0) >  0.5;
  const hatUp    = legacyHat && (gp.axes[7] ?? 0) < -0.5;
  const hatDown  = legacyHat && (gp.axes[7] ?? 0) >  0.5;
  const state = {
    ArrowUp:      btn(12) || hatUp    || gp.axes[1] < -0.5,
    ArrowDown:    btn(13) || hatDown  || gp.axes[1] >  0.5,
    ArrowLeft:    btn(14) || hatLeft  || gp.axes[0] < -0.5,
    ArrowRight:   btn(15) || hatRight || gp.axes[0] >  0.5,
    Enter:        btn(0),
    Escape:       btn(1),
    ButtonX:      btn(2),
    ButtonY:      btn(3),
    BumperLeft:   btn(4),
    BumperRight:  btn(5),
    TriggerLeft:  btn(6),
    TriggerRight: btn(7),
    Select:       btn(8),
    Start:        btn(9),
  };
  noteControllerInput(state);
  return state;
}

export function shouldHandleDirectionRepeat(
  key: GamepadDirectionKey,
  state: Partial<GpState>,
  lastState: Partial<GpState>,
  now: number,
  pressTime: Record<string, number>,
  repeating: Record<string, boolean>,
  initialDelay = 350,
  repeatDelay = 100,
): boolean {
  const pressed = !!state[key];
  const wasPressed = !!lastState[key];

  if (pressed && !wasPressed) {
    pressTime[key] = now;
    repeating[key] = false;
    return true;
  }

  if (pressed && wasPressed) {
    const held = now - (pressTime[key] || now);
    if (!repeating[key] && held >= initialDelay) {
      repeating[key] = true;
      pressTime[key] = now;
      return true;
    }
    if (repeating[key] && held >= repeatDelay) {
      pressTime[key] = now;
      return true;
    }
  } else if (!pressed && wasPressed) {
    pressTime[key] = 0;
    repeating[key] = false;
  }

  return false;
}

export function detectPlatform(gpId: string): "ps" | "switch" | "xbox" | null {
  const id = (gpId ?? "").toLowerCase();
  if (id.includes("054c") || id.includes("dualshock") || id.includes("dualsense") ||
      id.includes("playstation") || id.includes("sony")) return "ps";
  if (id.includes("057e") || id.includes("nintendo") || id.includes("switch") ||
      id.includes("pro controller") || id.includes("joycon")) return "switch";
  if (id.includes("xbox") || id.includes("xinput") || id.includes("045e") ||
      id.includes("microsoft")) return "xbox";
  return null;
}

export type HapticPattern =
  | "tab"
  | "confirm"
  | "cancel"
  | "startup"
  | "startupReady"
  | "launch";

interface DualRumbleEffect {
  duration: number;
  weakMagnitude: number;
  strongMagnitude: number;
  startDelay?: number;
}

const HAPTIC_PATTERNS: Record<HapticPattern, DualRumbleEffect[]> = {
  tab: [{ duration: 45, weakMagnitude: 0.24, strongMagnitude: 0.12 }],
  confirm: [{ duration: 60, weakMagnitude: 0.4, strongMagnitude: 0.3 }],
  cancel: [{ duration: 40, weakMagnitude: 0.2, strongMagnitude: 0.08 }],
  startup: [{ duration: 420, weakMagnitude: 0.28, strongMagnitude: 0.12 }],
  startupReady: [
    { duration: 120, weakMagnitude: 0.48, strongMagnitude: 0.3 },
    { startDelay: 150, duration: 260, weakMagnitude: 0.7, strongMagnitude: 0.58 },
  ],
  launch: [{ duration: 250, weakMagnitude: 0.7, strongMagnitude: 0.9 }],
};

export function rumble(pattern: HapticPattern, enabled = true): void {
  if (!enabled || _gamepadInputSuspended) return;
  const gp = getActiveGamepad();
  const actuator = (gp as any)?.vibrationActuator;
  if (!actuator) return;

  for (const pulse of HAPTIC_PATTERNS[pattern]) {
    actuator.playEffect("dual-rumble", {
      startDelay: pulse.startDelay ?? 0,
      duration: pulse.duration,
      weakMagnitude: pulse.weakMagnitude,
      strongMagnitude: pulse.strongMagnitude,
    }).catch(() => {});
  }
}

export function stopAllRumble(): void {
  const pads = Array.from(navigator.getGamepads()).filter((g): g is Gamepad => g !== null);
  for (const gp of pads) {
    const actuator = (gp as any)?.vibrationActuator;
    if (!actuator) continue;
    actuator.reset?.().catch?.(() => {});
    actuator.playEffect?.("dual-rumble", {
      duration: 1,
      weakMagnitude: 0,
      strongMagnitude: 0,
    }).catch?.(() => {});
  }
}

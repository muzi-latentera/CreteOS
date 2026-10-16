import { invoke } from "@tauri-apps/api/core";

type Row = Record<string, unknown>;
let session: string | null = null;
let rows: Row[] = [];
let sequence = 0;
let dropped = 0;
let previousPoll = 0;
let pollCount = 0;
let maxPollGap = 0;
let previousContext = "";
let previousPad = "";
let writing: Promise<void> | null = null;

/** No IPC, DOM inspection or serialization on the disabled input path. */
export function inputTrace(kind: string, data: Row = {}) {
  if (!session) return;
  if (rows.length >= 20_000) { dropped++; return; }
  rows.push({ sequence: ++sequence, kind, wallMs: Date.now(), nowMs: performance.now(), ...data });
}

export function inputTracePoll(now: number, focused: boolean, ready: boolean) {
  if (!session) return;
  pollCount++;
  const gapMs = previousPoll ? now - previousPoll : 0;
  previousPoll = now;
  maxPollGap = Math.max(maxPollGap, gapMs);
  if (gapMs > 24) inputTrace("poll-gap", { gapMs });
  const context = `${focused}:${ready}`;
  if (context !== previousContext) { previousContext = context; inputTrace("poll-context", { focused, ready }); }
}

export function inputTracePad(gp: Gamepad | null, state?: object, canNavigate?: boolean) {
  if (!session) return;
  const buttons = gp ? Array.from(gp.buttons).flatMap((b, i) => b.pressed ? [i] : []) : [];
  const axes = gp ? Array.from(gp.axes).map(n => Math.round(n * 100) / 100) : [];
  const signature = JSON.stringify([gp?.index, buttons, axes, state, canNavigate]);
  if (signature === previousPad) return;
  previousPad = signature;
  inputTrace("sample", { slot: gp?.index ?? null, connected: !!gp, buttons, axes, mapped: state, canNavigate, gamepadTimestamp: gp?.timestamp });
}

async function flush() {
  if (!session) return;
  if (writing) return writing;
  inputTrace("sample-summary", { pollCount, maxPollGap, dropped });
  const batch = rows.splice(0, 1024);
  if (!batch.length) return;
  writing = (async () => {
    try { await invoke("append_input_diagnostic", { sessionId: session, events: batch }); }
    catch { rows = [...batch, ...rows].slice(-20_000); }
    finally { writing = null; }
  })();
  return writing;
}

function selection(geometry: boolean) {
  const home = document.querySelector('.crete-home[aria-hidden="false"]');
  const library = document.querySelector('[data-tab-pane]:not([aria-hidden="true"]) .crete-library');
  const root = home ?? library;
  const view = home ? "Home" : library?.closest("[data-tab-pane]")?.getAttribute("data-tab-pane") ?? "other";
  const selected = root ? Array.from(root.querySelectorAll<HTMLElement>('[data-selected="true"], [data-focused="true"], [data-nav-focused="true"]')).slice(0, 12) : [];
  return { view, selected: selected.map(element => {
    const identity = { gameId: element.dataset.gameId ?? null, className: element.className };
    if (!geometry) return identity;
    const r = element.getBoundingClientRect(); const css = getComputedStyle(element);
    return { ...identity, x: r.x, y: r.y, width: r.width, height: r.height,
      insideViewport: r.right > 0 && r.bottom > 0 && r.left < innerWidth && r.top < innerHeight,
      opacity: css.opacity, transform: css.transform, outline: css.outline, boxShadow: css.boxShadow,
      transitionDuration: css.transitionDuration,
      animations: element.getAnimations().map(animation => ({ state: animation.playState, time: animation.currentTime })) };
  }) };
}

/** One explicitly requested, bounded recording; it never changes focus or navigation. */
export async function startInputDiagnostics(): Promise<() => void> {
  const config = await invoke<{ sessionId: string; durationMs: number } | null>("input_diagnostic_config").catch(() => null);
  if (!config) return () => {};
  session = config.sessionId; rows = []; sequence = 0; previousPoll = 0; previousPad = ""; previousContext = "";
  pollCount = 0; maxPollGap = 0; dropped = 0;
  inputTrace("start", { timeOrigin: performance.timeOrigin, durationMs: config.durationMs, width: innerWidth, height: innerHeight, devicePixelRatio });
  let signature = "";
  let pendingFrames = 0;
  let frame = 0;
  let previousFrame = 0;
  const observer = new MutationObserver(() => {
    const snapshot = selection(false); const next = JSON.stringify(snapshot);
    if (next !== signature) { signature = next; inputTrace("dom-selection", snapshot); pendingFrames = 2; }
  });
  observer.observe(document.documentElement, { subtree: true, attributes: true, attributeFilter: ["data-selected", "data-focused", "data-nav-focused", "aria-hidden"] });
  const draw = (now: number) => {
    if (previousFrame && now - previousFrame > 40) inputTrace("frame-gap", { gapMs: now - previousFrame });
    previousFrame = now;
    if (pendingFrames) {
      inputTrace(pendingFrames === 2 ? "frame-before-paint" : "paint-opportunity", selection(true));
      pendingFrames--;
    }
    frame = requestAnimationFrame(draw);
  };
  frame = requestAnimationFrame(draw);
  let tasks: PerformanceObserver | null = null;
  try {
    tasks = new PerformanceObserver(list => { for (const task of list.getEntries()) inputTrace("long-task", { startMs: task.startTime, durationMs: task.duration }); });
    tasks.observe({ type: "longtask", buffered: false });
  } catch { inputTrace("long-task-observer-unavailable"); }
  const timer = window.setInterval(() => { void flush(); }, 5000);
  const requestFlush = () => { void flush(); };
  window.addEventListener("crete-input-trace-flush", requestFlush);
  let finished = false;
  const stop = () => {
    if (finished) return; finished = true;
    observer.disconnect(); tasks?.disconnect(); cancelAnimationFrame(frame); clearInterval(timer); clearTimeout(deadline);
    window.removeEventListener("crete-input-trace-flush", requestFlush);
    inputTrace("stop", { pollCount, maxPollGap, dropped });
    // Drain outside the input path, then disable tracing.
    void (async () => { if (writing) await writing; while (rows.length && session) { const before = rows.length; await flush(); if (rows.length >= before) break; } session = null; })();
  };
  const deadline = window.setTimeout(stop, Math.min(600_000, Math.max(1000, config.durationMs)));
  return stop;
}

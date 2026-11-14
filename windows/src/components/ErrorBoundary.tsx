import { Component, useEffect, useRef, type ErrorInfo, type ReactNode } from "react";
import { invoke } from "@tauri-apps/api/core";

let backgroundErrors = 0;

/** Best effort: a render failure is written to CreteOS's log folder. */
export function recordFrontendError(area: string, error: unknown, componentStack?: string | null) {
  // Background (timer/promise) errors can repeat every poll; keep the first few.
  if ((area === "window" || area === "promise") && ++backgroundErrors > 20) return;
  const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
  const stack = [error instanceof Error ? error.stack : "", componentStack ?? ""].filter(Boolean).join("\n");
  void Promise.resolve(invoke("record_frontend_error", { area, message: message.slice(0, 2000), stack: stack.slice(0, 8000) })).catch(() => {});
}

interface Props {
  area: string;
  children: ReactNode;
  /** What to show instead of the broken part. `reset` tries drawing it again. */
  fallback: (reset: () => void) => ReactNode;
}

/**
 * Before 14 Nov a single rendering bug blanked the whole of CreteOS until it
 * was restarted. Boundaries keep the failure to the part that broke.
 */
export class ErrorBoundary extends Component<Props, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  componentDidCatch(error: unknown, info: ErrorInfo) { recordFrontendError(this.props.area, error, info.componentStack); }
  reset = () => this.setState({ failed: false });
  render() { return this.state.failed ? this.props.fallback(this.reset) : this.props.children; }
}

/**
 * Stand-in for a page that failed to draw. Pages like Game details run their
 * own controller loop, so this card reads A and B itself and always closes.
 */
export function SectionCrashCard({ title, onClose }: { title: string; onClose: () => void }) {
  const close = useRef(onClose);
  close.current = onClose;
  useEffect(() => {
    let frame = 0;
    let held = true;
    const poll = () => {
      const pressed = Array.from(navigator.getGamepads?.() ?? []).some(pad => pad?.buttons[0]?.pressed || pad?.buttons[1]?.pressed);
      if (!pressed) held = false;
      else if (!held) { close.current(); return; }
      frame = requestAnimationFrame(poll);
    };
    frame = requestAnimationFrame(poll);
    const key = (event: KeyboardEvent) => { if (event.key === "Escape" || event.key === "Enter") { event.preventDefault(); event.stopPropagation(); close.current(); } };
    window.addEventListener("keydown", key, true);
    return () => { cancelAnimationFrame(frame); window.removeEventListener("keydown", key, true); };
  }, []);
  return <div role="alertdialog" aria-label={title} data-owns-input="" style={{ position: "fixed", inset: 0, zIndex: 6000, display: "grid", placeItems: "center", background: "rgba(5,7,9,0.92)", color: "#f3efe9", fontFamily: "'Segoe UI', sans-serif" }}>
    <div style={{ maxWidth: 480, padding: 28, textAlign: "center", borderRadius: 14, border: "1px solid #ffffff26", background: "#101418" }}>
      <h2 style={{ fontSize: 22, margin: "0 0 10px" }}>{title}</h2>
      <p style={{ opacity: 0.75, lineHeight: 1.5, margin: "0 0 20px" }}>Something on this page went wrong. Go back and open it again.</p>
      <button type="button" autoFocus onClick={() => close.current()} style={{ padding: "10px 26px", borderRadius: 10, border: "1px solid #c9482a", background: "#c9482a", color: "white", fontSize: 15, fontWeight: 600 }}>Back</button>
    </div>
  </div>;
}

const RELOAD_KEY = "crete-error-reloads";
const AUTO_RELOAD_MS = 8000;

/** Reloads recorded in the last two minutes; more than three stops automatic reloads. */
export function recentReloads(now = Date.now()): number[] {
  try {
    const stored = JSON.parse(sessionStorage.getItem(RELOAD_KEY) ?? "[]");
    return Array.isArray(stored) ? stored.filter((at): at is number => typeof at === "number" && now - at < 120_000) : [];
  } catch { return []; }
}

function restart() {
  try { sessionStorage.setItem(RELOAD_KEY, JSON.stringify([...recentReloads(), Date.now()])); } catch { /* storage unavailable */ }
  location.reload();
}

/**
 * Whole-app fallback. The normal controller loop is gone with the app, so this
 * screen reads A (or Enter) itself, and restarts on its own unless it keeps
 * failing.
 */
export function AppCrashScreen() {
  const auto = recentReloads().length < 3;
  const button = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    button.current?.focus();
    const timer = auto ? window.setTimeout(restart, AUTO_RELOAD_MS) : undefined;
    let frame = 0;
    let held = true; // ignore an A press that was already down when this appeared
    const poll = () => {
      const pressed = Array.from(navigator.getGamepads?.() ?? []).some(pad => pad?.buttons[0]?.pressed);
      if (!pressed) held = false;
      else if (!held) { restart(); return; }
      frame = requestAnimationFrame(poll);
    };
    frame = requestAnimationFrame(poll);
    return () => { if (timer) clearTimeout(timer); cancelAnimationFrame(frame); };
  }, [auto]);
  return <div role="alert" style={{ position: "fixed", inset: 0, display: "grid", placeItems: "center", background: "#0b0d10", color: "#f3efe9", fontFamily: "'Segoe UI', sans-serif" }}>
    <div style={{ maxWidth: 520, padding: 32, textAlign: "center" }}>
      <h1 style={{ fontSize: 28, margin: "0 0 12px" }}>CreteOS ran into a problem</h1>
      <p style={{ opacity: 0.75, lineHeight: 1.5, margin: "0 0 24px" }}>{auto ? "Restarting in a few seconds. Any game you're playing keeps running." : "It restarted several times and hit the same problem. Your games are unaffected."}</p>
      <button ref={button} type="button" onClick={restart} style={{ padding: "12px 28px", borderRadius: 10, border: "1px solid #c9482a", background: "#c9482a", color: "white", fontSize: 16, fontWeight: 600 }}>Restart CreteOS</button>
    </div>
  </div>;
}

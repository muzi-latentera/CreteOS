import { useEffect, type RefObject } from "react";
import { getBestGamepad, readGpState, shouldHandleDirectionRepeat, type GpState } from "../utils/gamepad";

const FOCUSABLE = "button:not(:disabled), select:not(:disabled), [data-dialog-focus]";

/**
 * Controller and keyboard navigation for a CreteOS panel: Up/Down move between
 * rows, Left/Right between controls in a row (`[data-nav-row]`), A presses, B
 * goes back. Buttons held when the panel opens are ignored until released.
 * Mark the panel root with `data-owns-input` so the main loop stays out.
 */
export function useDialogNav(root: RefObject<HTMLElement | null>, onBack: () => void, active = true) {
  useEffect(() => {
    if (!active) return;
    const controls = () => Array.from(root.current?.querySelectorAll<HTMLElement>(FOCUSABLE) ?? []);
    const focus = (el?: HTMLElement) => { el?.focus({ preventScroll: true }); el?.scrollIntoView({ block: "nearest" }); };
    const ensure = () => {
      const list = controls();
      if (!list.includes(document.activeElement as HTMLElement)) focus(root.current?.querySelector<HTMLElement>("[data-dialog-primary]:not(:disabled)") ?? list[0]);
    };
    const rows = () => {
      const result: HTMLElement[][] = [];
      for (const el of controls()) {
        const row = el.closest("[data-nav-row]");
        const last = result[result.length - 1];
        if (row && last && last[0].closest("[data-nav-row]") === row) last.push(el); else result.push([el]);
      }
      return result;
    };
    const move = (direction: "up" | "down" | "left" | "right") => {
      const grid = rows();
      const current = document.activeElement as HTMLElement;
      const r = grid.findIndex(row => row.includes(current));
      if (r < 0) { ensure(); return; }
      const c = grid[r].indexOf(current);
      if (direction === "left" || direction === "right") focus(grid[r][c + (direction === "right" ? 1 : -1)]);
      else {
        const next = grid[(r + (direction === "down" ? 1 : -1) + grid.length) % grid.length];
        focus(next[Math.min(c, next.length - 1)]);
      }
    };
    const key = (event: KeyboardEvent) => {
      const map: Record<string, "up" | "down" | "left" | "right"> = { ArrowUp: "up", ArrowDown: "down", ArrowLeft: "left", ArrowRight: "right" };
      if (map[event.key]) { event.preventDefault(); event.stopImmediatePropagation(); move(map[event.key]); }
      else if (event.key === "Escape") { event.preventDefault(); event.stopImmediatePropagation(); onBack(); }
    };
    window.addEventListener("keydown", key, true);
    const timer = window.setTimeout(ensure, 0);
    let frame = 0;
    let last: Partial<GpState> | null = null;
    const pressTime: Record<string, number> = {};
    const repeating: Record<string, boolean> = {};
    const poll = (now: number) => {
      const gp = getBestGamepad();
      if (gp) {
        const state = readGpState(gp);
        if (last === null) last = state; // ignore anything held as the panel opened
        else {
          for (const [name, dir] of [["ArrowUp", "up"], ["ArrowDown", "down"], ["ArrowLeft", "left"], ["ArrowRight", "right"]] as const) {
            if (shouldHandleDirectionRepeat(name, state, last, now, pressTime, repeating)) { move(dir); break; }
          }
          if (state.Enter && !last.Enter) { ensure(); (document.activeElement as HTMLElement | null)?.click(); }
          if (state.Escape && !last.Escape) onBack();
          last = state;
        }
      }
      frame = requestAnimationFrame(poll);
    };
    frame = requestAnimationFrame(poll);
    return () => { window.removeEventListener("keydown", key, true); window.clearTimeout(timer); cancelAnimationFrame(frame); };
  }, [active, root, onBack]);
}

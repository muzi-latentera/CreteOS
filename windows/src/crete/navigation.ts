import { useLayoutEffect, type RefObject } from "react";

export type Navigation = (key: string) => boolean;
export type NavigationRef = RefObject<Navigation | null>;

/** A presentation-only delegate. Upstream modal/launch guards run before it. */
export function useViewNavigation(active: boolean, slot: NavigationRef, handler: Navigation) {
  useLayoutEffect(() => {
    if (!active) return;
    const guarded: Navigation = key => document.querySelector("[data-stream-launch-cover]") ? true : handler(key);
    slot.current = guarded;
    return () => { if (slot.current === guarded) slot.current = null; };
  }, [active, slot, handler]);
}

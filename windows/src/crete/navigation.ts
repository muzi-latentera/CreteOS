import { useLayoutEffect, type RefObject } from "react";

export type Navigation = (key: string) => boolean;
export type NavigationRef = RefObject<Navigation | null>;

/** A presentation-only delegate. Upstream modal/launch guards run before it. */
export function useViewNavigation(active: boolean, slot: NavigationRef, handler: Navigation) {
  useLayoutEffect(() => {
    if (!active) return;
    slot.current = handler;
    return () => { if (slot.current === handler) slot.current = null; };
  }, [active, slot, handler]);
}

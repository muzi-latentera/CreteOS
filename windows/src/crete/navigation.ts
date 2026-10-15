import { useCallback, useLayoutEffect, useRef, useState, type Dispatch, type RefObject, type SetStateAction } from "react";

export type Navigation = (key: string) => boolean;
export type NavigationRef = RefObject<Navigation | null>;

/** Input advances immediately; React may batch the resulting visual updates. */
export function useNavigationState<T>(initial: T) {
  const [value, render] = useState(initial);
  const current = useRef(value);
  const set = useCallback<Dispatch<SetStateAction<T>>>(action => {
    const next = typeof action === "function" ? (action as (previous: T) => T)(current.current) : action;
    current.current = next;
    render(next);
  }, []);
  return [value, set, current] as const;
}

/** Each mounted view owns a slot, so a tab change need not wait for a render.
 * The parent selects the current tab after upstream modal/launch guards. */
export function useViewNavigation(slot: NavigationRef, handler: Navigation) {
  useLayoutEffect(() => {
    const guarded: Navigation = key => document.querySelector("[data-stream-launch-cover]") ? true : handler(key);
    slot.current = guarded;
    return () => { if (slot.current === guarded) slot.current = null; };
  }, [slot, handler]);
}

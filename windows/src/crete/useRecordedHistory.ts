import { invoke } from "@tauri-apps/api/core";
import { useEffect, useState } from "react";
import type { App } from "../types";

/** Read the persisted history, rather than LiftOff's unplayed first-run fallback shelves. */
export function useRecordedHistory(active: boolean, recent: App[]) {
  const [history, setHistory] = useState<App[]>([]);
  useEffect(() => {
    if (!active) return;
    let disposed = false;
    let generation = 0;
    const refresh = () => {
      const request = ++generation;
      Promise.allSettled([invoke<App[]>("get_recent_games"), invoke<App[]>("get_recents")]).then(results => {
        if (disposed || generation !== request) return;
        const entries = results.flatMap(result => result.status === "fulfilled" ? result.value : []);
        if (results.some(result => result.status === "fulfilled")) {
          setHistory([...new Map(entries.map(app => [app.id, app])).values()]);
        }
      });
    };
    refresh();
    window.addEventListener("focus", refresh);
    return () => { disposed = true; window.removeEventListener("focus", refresh); };
  }, [active, recent]);
  return history;
}

import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import type { App } from "../types";
export interface StoreProgress {
  minutes?: number; last_played?: number; earned?: number; total?: number; xp?: number; total_xp?: number;
  achievements?: { id: string; name: string; description: string; unlocked: boolean; unlocked_at?: number }[];
}
export function useStoreProgress(app: App | undefined, enabled = true) {
  const [progress, setProgress] = useState<StoreProgress | null>(null);
  const source = app?.source;
  const gameId = app?.store_id ?? (source === "epic" ? app?.id.replace(/^epic:/, "") : undefined);
  useEffect(() => {
    setProgress(null);
    if (!enabled || !gameId || (source !== "epic" && source !== "gog" && source !== "lutris")) return;
    let active = true, ticket = 0;
    let timer: ReturnType<typeof setTimeout>;
    let release: (() => void) | undefined;
    const read = async (offline: boolean, refresh = false) => {
      const request = ++ticket;
      try {
        const result = source === "lutris" ? await invoke<StoreProgress>("get_lutris_progress", { gameId: app?.id }) : await invoke<StoreProgress>("store_game_progress", { source, gameId, offline, refresh });
        if (active && request === ticket) setProgress(result);
      } catch { /* Saved activity stays visible when a provider cannot refresh. */ }
    };
    const returned = () => {
      if (!navigator.onLine) return;
      void read(false, true);
      clearTimeout(timer); timer = setTimeout(() => { if (active) void read(false, true); }, 30_000);
    };
    void read(true).then(() => { if (active && navigator.onLine) void read(false); });
    window.addEventListener("focus", returned); window.addEventListener("online", returned);
    void listen("stream-session-ended", returned).then(fn => { if (active) release = fn; else fn(); }).catch(() => {});
    return () => { active = false; ticket++; clearTimeout(timer); release?.(); window.removeEventListener("focus", returned); window.removeEventListener("online", returned); };
  }, [source, gameId, enabled]);
  return progress;
}

import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
export interface XboxProgress { minutes?: number; earned?: number; total?: number; gamerscore?: number; totalGamerscore?: number }
export function useXboxProgress(name: string, enabled: boolean) {
  const [progress, setProgress] = useState<XboxProgress | null>(null);
  useEffect(() => {
    setProgress(null); if (!enabled) return;
    let active = true;
    let request = 0;
    let timer: ReturnType<typeof setTimeout>;
    let release: (() => void) | undefined;
    const refresh = (fresh = false) => {
      const ticket = ++request;
      void invoke<XboxProgress>("get_xbox_game_progress", { gameName: name, offline: !navigator.onLine, refresh: fresh })
        .then(result => { if (active && ticket === request) setProgress(result); }).catch(() => {});
    };
    const returned = () => {
      if (!active) return;
      refresh(true);
      clearTimeout(timer);
      timer = setTimeout(() => { if (active) refresh(true); }, 30_000);
    };
    refresh();
    window.addEventListener("focus", returned);
    window.addEventListener("online", returned);
    void listen("stream-session-ended", returned).then(unlisten => { if (active) release = unlisten; else unlisten(); }).catch(() => {});
    return () => { active = false; clearTimeout(timer); release?.(); window.removeEventListener("focus", returned); window.removeEventListener("online", returned); };
  }, [name, enabled]);
  return progress;
}

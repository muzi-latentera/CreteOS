import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
export interface XboxProgress { minutes?: number; earned?: number; total?: number; gamerscore?: number; totalGamerscore?: number }
export function useXboxProgress(name: string, enabled: boolean) {
  const [progress, setProgress] = useState<XboxProgress | null>(null);
  useEffect(() => {
    setProgress(null); if (!enabled) return;
    let active = true;
    void invoke<XboxProgress>("get_xbox_game_progress", { gameName: name, offline: !navigator.onLine }).then(result => { if (active) setProgress(result); }).catch(() => {});
    return () => { active = false; };
  }, [name, enabled]);
  return progress;
}

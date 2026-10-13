import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { openUrl } from "@tauri-apps/plugin-opener";
import type { App } from "../types";
import { geforceNowUrl, isPcGame } from "./streaming";

export function useStreaming(app: App, enabled: boolean) {
  const [clients, setClients] = useState({ geforceNow: false, moonlight: false });
  const [error, setError] = useState<string | null>(null);
  const pcGame = enabled && isPcGame(app);
  const gameUrl = pcGame ? geforceNowUrl(app) : undefined;
  useEffect(() => {
    let active = true;
    setError(null);
    setClients({ geforceNow: false, moonlight: false });
    if (pcGame) void invoke<typeof clients>("get_streaming_clients")
      .then(value => { if (active && value) setClients(value); }).catch(() => {});
    return () => { active = false; };
  }, [pcGame, app.id]);
  const launch = (provider: "geforceNow" | "moonlight", direct = false) => {
    setError(null);
    const request = provider === "geforceNow" && direct && gameUrl
      ? openUrl(gameUrl)
      : invoke("launch_streaming_client", { provider });
    void request.catch(reason => setError(String(reason)));
  };
  return { pcGame, gameUrl, clients, error, launch };
}

import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { openUrl } from "@tauri-apps/plugin-opener";
import type { App } from "../types";
import { defaultProvider, geforceNowUrl, isPcGame, moonlightTarget } from "./streaming";

export interface MoonlightProfile { host: string; resolution: string; fps: number; bitrate: number; codec: string }
export interface DisplayProfile { displayKey: string; profile: MoonlightProfile }
const fallback: DisplayProfile = { displayKey: "display", profile: { host: "Bosgame", resolution: "1920x1080", fps: 120, bitrate: 80000, codec: "auto" } };
const requests = new Map<string, { time: number; promise: Promise<string[]> }>();
export function useStreaming(app: App, enabled: boolean) {
  const [clients, setClients] = useState({ geforceNow: false, moonlight: false });
  const [error, setError] = useState<string | null>(null);
  const [hostError, setHostError] = useState<string | null>(null);
  const [apps, setApps] = useState<string[]>([]);
  const [display, setDisplay] = useState(fallback);
  const [configure, setConfigure] = useState(false);
  const [refreshId, setRefreshId] = useState(0);
  const [checking, setChecking] = useState(false);
  const [buddy, setBuddy] = useState<{ paired: boolean; version: number } | null>(null);
  const [buddyError, setBuddyError] = useState<string | null>(null);
  const [hostGames, setHostGames] = useState<{ steamAppId: string; name: string }[] | null>(null);
  const [inventoryError, setInventoryError] = useState<string | null>(null);
  const pcGame = enabled && isPcGame(app);
  const gameUrl = pcGame ? geforceNowUrl(app) : undefined;
  const hostInstalled = app.source === "steam" && !!app.steam_appid && !!hostGames?.some(game => game.steamAppId === String(app.steam_appid));
  const moonlightGame = pcGame ? moonlightTarget(app, apps, hostGames?.map(game => game.steamAppId) ?? []) : undefined;
  useEffect(() => {
    let active = true;
    setError(null); setApps([]); setConfigure(false);
    if (pcGame) void invoke<typeof clients>("get_streaming_clients").then(value => { if (active && value) setClients(value); }).catch(() => {});
    return () => { active = false; };
  }, [pcGame, app.id]);
  useEffect(() => {
    if (!pcGame || !clients.moonlight) return;
    let active = true;
    setChecking(true); setApps([]); setHostError(null); setHostGames(null); setInventoryError(null); setBuddy(null);
    void (async () => {
      const current = await invoke<DisplayProfile>("get_moonlight_profile");
      if (!current?.profile) throw new Error("Display settings unavailable");
      if (active) setDisplay(current);
      const host = current.profile.host;
      const inventoryRequest = invoke<{ steamAppId: string; name: string }[]>("get_host_installed_games", { host }).then(value => { if (active) { setHostGames(value ?? null); setInventoryError(null); } }).catch(reason => { if (active) { setHostGames(null); setInventoryError(String(reason)); } });
      const buddyRequest = invoke<{ paired: boolean; version: number }>("get_moondeck_status", { host }).then(value => { if (active) { setBuddy(value); setBuddyError(null); } }).catch(reason => { if (active) { setBuddy(null); setBuddyError(String(reason)); } });
      const cached = requests.get(host);
      const promise = cached && Date.now() - cached.time < 30000 ? cached.promise : invoke<string[]>("get_moonlight_apps", { host });
      requests.set(host, { time: Date.now(), promise });
      const list = await promise;
      if (active) setApps(list ?? []);
      await Promise.all([inventoryRequest, buddyRequest]);
    })().catch(reason => { if (active) setHostError(String(reason)); }).finally(() => { if (active) setChecking(false); });
    return () => { active = false; };
  }, [pcGame, clients.moonlight, refreshId, app.id]);
  const refresh = () => { requests.clear(); setRefreshId(value => value + 1); };
  const launch = (provider: "geforceNow" | "moonlight", direct = false) => {
    setError(null);
    if (provider === "moonlight") {
      void invoke<DisplayProfile>("get_moonlight_profile").then(current => { if (current?.profile) setDisplay(current); setConfigure(true); }).catch(reason => setError(String(reason)));
      return;
    }
    void (direct && gameUrl ? openUrl(gameUrl) : invoke("launch_streaming_client", { provider })).catch(reason => setError(String(reason)));
  };
  const stream = async (appName: string, profile: MoonlightProfile) => {
    setError(null);
    try {
      const steamId = appName === "MoonDeckStream" && app.source === "steam" && app.steam_appid ? String(app.steam_appid) : null;
      await invoke("launch_moonlight", { appName, displayKey: display.displayKey, profile, steamId });
      setConfigure(false);
      requests.clear();
    } catch (reason) { setError(String(reason)); }
  };
  const pair = async (pin: number) => {
    setError(null);
    try { await invoke("pair_moondeck", { host: display.profile.host, pin }); } catch (reason) { setError(String(reason)); }
  };
  return { pcGame, gameUrl, clients, error, hostError, apps, moonlightGame, provider: defaultProvider(app, moonlightGame, gameUrl), display, configure, setConfigure, checking, refresh, launch, stream, buddy, buddyError, pair, steamId: app.source === "steam" ? app.steam_appid : undefined, hostGames, hostInstalled, inventoryError };
}

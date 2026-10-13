import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { openUrl } from "@tauri-apps/plugin-opener";
import type { App } from "../types";
import { defaultProvider, geforceNowUrl, isPcGame, moonlightTarget } from "./streaming";
import { readHost, refreshHost, subscribeHost } from "./hostSnapshot";

export interface MoonlightProfile { host: string; resolution: string; fps: number; bitrate: number; codec: string }
export interface DisplayProfile { displayKey: string; profile: MoonlightProfile }
const fallback: DisplayProfile = { displayKey: "display", profile: { host: "Bosgame", resolution: "1920x1080", fps: 120, bitrate: 80000, codec: "auto" } };
export function useStreaming(app: App, enabled: boolean) {
  const [clients, setClients] = useState({ geforceNow: false, moonlight: false });
  const [error, setError] = useState<string | null>(null);
  const [display, setDisplay] = useState(fallback);
  const [configure, setConfigure] = useState(false);
  const [clientsFor, setClientsFor] = useState<string | null>(null);
  const [, update] = useState(0);
  const [online, setOnline] = useState(navigator.onLine);
  const pcGame = enabled && isPcGame(app);
  const host = readHost(display.profile.host);
  const apps = host.snapshot.apps ?? [];
  const hostGames = host.snapshot.games ?? null;
  const buddy = host.snapshot.buddy ?? null;
  // Only local client/profile hydration uses a spinner. Network refresh never
  // hides Play or changes its colour while the saved state is in use.
  const checking = pcGame && clientsFor !== app.id;
  const gameUrl = pcGame ? geforceNowUrl(app) : undefined;
  const hostInstalled = app.source === "steam" && !!app.steam_appid && !!hostGames?.some(game => game.steamAppId === String(app.steam_appid));
  const moonlightGame = pcGame && online && clients.moonlight ? moonlightTarget(app, apps, hostGames?.map(game => game.steamAppId) ?? []) : undefined;
  useEffect(() => subscribeHost(() => update(value => value + 1)), []);
  useEffect(() => {
    const connected = () => { setOnline(true); if (pcGame && clients.moonlight) void refreshHost(display.profile.host); };
    const disconnected = () => setOnline(false);
    window.addEventListener("online", connected); window.addEventListener("offline", disconnected);
    return () => { window.removeEventListener("online", connected); window.removeEventListener("offline", disconnected); };
  }, [pcGame, clients.moonlight, display.profile.host]);
  useEffect(() => {
    if (!pcGame || !clients.moonlight) return;
    const timer = window.setInterval(() => { void refreshHost(display.profile.host); }, 60000);
    return () => clearInterval(timer);
  }, [pcGame, clients.moonlight, display.profile.host]);
  useEffect(() => {
    let active = true;
    setError(null); setConfigure(false);
    if (pcGame) void (async () => {
      const detected = await invoke<typeof clients>("get_streaming_clients");
      if (!active) return;
      setClients(detected ?? { geforceNow: false, moonlight: false });
      if (detected?.moonlight) {
        const current = await invoke<DisplayProfile>("get_moonlight_profile");
        if (active && current?.profile) { setDisplay(current); void refreshHost(current.profile.host); }
      }
    })().catch(() => {}).finally(() => { if (active) setClientsFor(app.id); });
    return () => { active = false; };
  }, [pcGame, app.id]);
  const refresh = () => { void refreshHost(display.profile.host, true); };
  const launch = (provider: "geforceNow" | "moonlight", direct = false) => {
    setError(null);
    if (provider === "moonlight") {
      void invoke<DisplayProfile>("get_moonlight_profile").then(current => { if (current?.profile) setDisplay(current); setConfigure(true); }).catch(reason => setError(String(reason)));
      return;
    }
    if (!online) { setError("GeForce NOW needs an internet connection. Choose an installed local game while offline."); return; }
    void (direct && gameUrl ? openUrl(gameUrl) : invoke("launch_streaming_client", { provider })).catch(reason => setError(String(reason)));
  };
  const stream = async (appName: string, profile: MoonlightProfile) => {
    setError(null);
    try {
      const steamId = appName === "MoonDeckStream" && app.source === "steam" && app.steam_appid ? String(app.steam_appid) : null;
      await invoke("launch_moonlight", { appName, displayKey: display.displayKey, profile, steamId });
      setConfigure(false);
    } catch (reason) { setError(`Could not start Moonlight on ${profile.host}. Connect to the host's network or choose local play. ${String(reason)}`); }
  };
  const pair = async (pin: number) => {
    setError(null);
    try { await invoke("pair_moondeck", { host: display.profile.host, pin }); } catch (reason) { setError(String(reason)); }
  };
  return { pcGame, gameUrl, clients, error, hostError: host.error, apps, moonlightGame, provider: defaultProvider(app, moonlightGame, online ? gameUrl : undefined), display, configure, setConfigure, checking, refreshing: host.refreshing, refresh, launch, stream, buddy, buddyError: null, pair, steamId: app.source === "steam" ? app.steam_appid : undefined, hostGames, hostInstalled, inventoryError: null, localInstalled: !!app.installed, online };
}

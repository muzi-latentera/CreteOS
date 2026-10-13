import { invoke } from "@tauri-apps/api/core";

export const HOST_SNAPSHOT_KEY = "crete.host-snapshots.v1";
export const HOST_REFRESH_MS = 24 * 60 * 60 * 1000;
export interface HostSnapshot {
  attemptedAt: number;
  apps?: string[];
  games?: { steamAppId: string; name: string }[];
  buddy?: { paired: boolean; version: number };
  checkedAt?: number;
}
export interface HostState { snapshot: HostSnapshot; refreshing: boolean; error: string | null }
const states = new Map<string, HostState>();
const pending = new Map<string, Promise<void>>();
const listeners = new Set<() => void>();
const key = (host: string) => host.toLowerCase();
export function readHost(host: string): HostState {
  const id = key(host);
  if (!states.has(id)) {
    let snapshot: HostSnapshot = { attemptedAt: 0 };
    try {
      const saved = JSON.parse(localStorage.getItem(HOST_SNAPSHOT_KEY) ?? "{}")[id];
      if (saved && Number.isFinite(saved.attemptedAt) && saved.attemptedAt >= 0) {
        snapshot = {
          attemptedAt: saved.attemptedAt,
          checkedAt: Number.isFinite(saved.checkedAt) ? saved.checkedAt : undefined,
          apps: Array.isArray(saved.apps) && saved.apps.every((s: unknown) => typeof s === "string") ? saved.apps : undefined,
          games: Array.isArray(saved.games) && saved.games.every((g: any) => typeof g?.steamAppId === "string" && /^\d+$/.test(g.steamAppId) && typeof g.name === "string") ? saved.games : undefined,
          buddy: typeof saved.buddy?.paired === "boolean" && Number.isFinite(saved.buddy.version) ? saved.buddy : undefined,
        };
      }
    } catch { /* A damaged or unavailable cache must not block local play. */ }
    states.set(id, { snapshot, refreshing: false, error: null });
  }
  return states.get(id)!;
}
function publish(host: string, state: HostState) {
  states.set(key(host), state);
  try {
    let saved: Record<string, HostSnapshot> = {};
    try { saved = JSON.parse(localStorage.getItem(HOST_SNAPSHOT_KEY) ?? "{}"); if (!saved || typeof saved !== "object" || Array.isArray(saved)) saved = {}; } catch { /* Replace corrupt storage. */ }
    saved[key(host)] = state.snapshot;
    localStorage.setItem(HOST_SNAPSHOT_KEY, JSON.stringify(saved));
  } catch { /* Keep the in-memory snapshot if disk storage is unavailable. */ }
  listeners.forEach(listener => listener());
}
export function subscribeHost(listener: () => void) {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}
export function refreshHost(host: string, force = false): Promise<void> {
  const existing = pending.get(key(host));
  if (existing) return existing;
  const old = readHost(host);
  const now = Date.now();
  // Persist attempts as well as successes: an unreachable host must not cause
  // every game (or every app restart) to retry the same network requests.
  if (!force && old.snapshot.attemptedAt <= now && now - old.snapshot.attemptedAt < HOST_REFRESH_MS) return Promise.resolve();
  if (navigator.onLine === false) return Promise.resolve();
  const task = Promise.resolve().then(async () => {
    publish(host, { snapshot: { ...old.snapshot, attemptedAt: now }, refreshing: true, error: null });
    const results = await Promise.allSettled([
      invoke<string[]>("get_moonlight_apps", { host }),
      invoke<NonNullable<HostSnapshot["games"]>>("get_host_installed_games", { host }),
      invoke<NonNullable<HostSnapshot["buddy"]>>("get_moondeck_status", { host }),
    ]);
    const snapshot = { ...old.snapshot, attemptedAt: now };
    // A failed query is unknown, never an empty library or an unpaired host.
    if (results[0].status === "fulfilled") snapshot.apps = results[0].value ?? [];
    if (results[1].status === "fulfilled") snapshot.games = results[1].value ?? [];
    if (results[2].status === "fulfilled") snapshot.buddy = results[2].value;
    const failed = results.some(result => result.status === "rejected");
    if (!failed) snapshot.checkedAt = Date.now();
    publish(host, { snapshot, refreshing: false, error: failed ? "Bosgame could not be refreshed. Using saved availability. You can retry with Refresh host." : null });
  }).finally(() => { pending.delete(key(host)); });
  pending.set(key(host), task);
  return task;
}

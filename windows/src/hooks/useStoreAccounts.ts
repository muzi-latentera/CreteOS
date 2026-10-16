import { useCallback, useEffect, useRef, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { suspendGamepadInputUntilButtonsReleased } from "../utils/gamepad";

export type AccountStore = "epic" | "gog";
export interface StoreAccountStatus { connected: boolean; account_name?: string; owned_count: number; updated_at?: number }
const empty: StoreAccountStatus = { connected: false, owned_count: 0 };
const stores: AccountStore[] = ["epic", "gog"];

export function useStoreAccounts(refreshLibrary: (options?: { background?: boolean }) => unknown, revealOwned: (store: AccountStore) => unknown) {
  const [statuses, setStatuses] = useState<Record<AccountStore, StoreAccountStatus>>({ epic: empty, gog: empty });
  const [selected, setSelected] = useState<AccountStore | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const openRef = useRef(false);
  const refreshRef = useRef(refreshLibrary); refreshRef.current = refreshLibrary;
  const revealRef = useRef(revealOwned); revealRef.current = revealOwned;
  const selectedRef = useRef<AccountStore | null>(null);
  const operation = useRef(0);
  const readStatus = useCallback(async () => {
    const values = await Promise.all(stores.map(source => invoke<StoreAccountStatus>("store_account_status", { source }).then(value => value || empty).catch(() => empty)));
    setStatuses({ epic: values[0], gog: values[1] });
  }, []);
  const pending = useRef<Promise<boolean> | null>(null);
  const refreshAll = useCallback((force = false) => {
    if (!navigator.onLine) return Promise.resolve(false);
    if (pending.current) return pending.current;
    const request = Promise.all(stores.map(async source => {
      const status = await invoke<StoreAccountStatus>("store_account_status", { source });
      if (!status?.connected) return false;
      const next = await invoke<StoreAccountStatus>("store_account_refresh", { source, force });
      return !!next && next.updated_at !== status.updated_at;
    }).map(p => p.catch(() => false))).then(async results => { await readStatus(); return results.some(Boolean); }).finally(() => { pending.current = null; });
    pending.current = request; return request;
  }, [readStatus]);
  useEffect(() => {
    void readStatus();
    void refreshAll().then(changed => { if (changed) void refreshRef.current({ background: true }); });
    const online = () => { void refreshAll().then(changed => { if (changed) void refreshRef.current({ background: true }); }); };
    window.addEventListener("online", online);
    let disposed = false; let unlisten: (() => void) | undefined; let activityRelease: (() => void) | undefined;
    void listen("store-accounts-changed", () => { if (!disposed) void readStatus(); }).then(fn => { if (disposed) fn(); else unlisten = fn; });
    void listen("store-progress-changed", () => { if (!disposed) void refreshRef.current({ background: true }); }).then(fn => { if (disposed) fn(); else activityRelease = fn; });
    return () => { disposed = true; unlisten?.(); activityRelease?.(); window.removeEventListener("online", online); };
  }, [readStatus, refreshAll]);
  const open = useCallback((store: AccountStore) => {
    operation.current++; selectedRef.current = store; openRef.current = true;
    setSelected(store); setError(""); setBusy(false);
  }, []);
  const close = useCallback(() => {
    const source = selectedRef.current;
    suspendGamepadInputUntilButtonsReleased();
    operation.current++; selectedRef.current = null; openRef.current = false; setSelected(null); setBusy(false);
    if (source) void invoke("store_account_cancel", { source }).catch(() => {});
  }, []);
  const run = useCallback(async (action: "connect" | "refresh" | "disconnect") => {
    const source = selectedRef.current; if (!source) return;
    const request = ++operation.current;
    setBusy(true); setError("");
    try {
      await invoke(`store_account_${action}`, { source, force: true });
      if (operation.current !== request) return;
      if (action === "connect") await revealRef.current(source);
      await readStatus(); await refreshRef.current({ background: true });
    } catch (e) { if (operation.current === request) setError(String(e)); }
    finally { if (operation.current === request) setBusy(false); }
  }, [readStatus]);
  return { statuses, selected, busy, error, openRef, open, close, run, refreshAll };
}

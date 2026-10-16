import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { useStoreAccounts } from "./useStoreAccounts";

const { invoke } = vi.hoisted(() => ({ invoke: vi.fn() }));
vi.mock("@tauri-apps/api/core", () => ({ invoke }));
vi.mock("@tauri-apps/api/event", () => ({ listen: vi.fn().mockResolvedValue(() => {}) }));
let root: Root; let host: HTMLDivElement;
let accounts: ReturnType<typeof useStoreAccounts>;
const scan = vi.fn(); const reveal = vi.fn();
let connected = false; let timestamp = 100; let fail = false;
let holdConnect: ((value: unknown) => void) | undefined;
function Harness() { accounts = useStoreAccounts(scan, reveal); return null; }
beforeEach(async () => {
  (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
  Object.defineProperty(navigator, "onLine", { configurable: true, value: true });
  connected = false; timestamp = 100; fail = false; holdConnect = undefined;
  scan.mockReset(); reveal.mockReset();
  invoke.mockReset().mockImplementation(async (command, args) => {
    const status = () => ({ connected: connected && args.source === "epic", owned_count: connected ? 3 : 0, updated_at: timestamp });
    if (command === "store_account_status") return status();
    if (command === "store_account_connect") return new Promise(resolve => { holdConnect = resolve; });
    if (command === "store_account_refresh") { if (fail) throw new Error("Offline"); if (args.force) timestamp++; return status(); }
    if (command === "store_account_disconnect") { connected = false; return null; }
    return null;
  });
  host = document.createElement("div"); document.body.append(host); root = createRoot(host);
  await act(async () => root.render(<Harness />));
});
afterEach(() => { act(() => root.unmount()); host.remove(); });
it("does not rescan an unchanged daily cache, refreshes explicitly, and retains it on failure", async () => {
  connected = true;
  await act(async () => { expect(await accounts.refreshAll()).toBe(false); });
  expect(scan).not.toHaveBeenCalled();
  await act(async () => { expect(await accounts.refreshAll(true)).toBe(true); });
  fail = true;
  await act(async () => { expect(await accounts.refreshAll(true)).toBe(false); });
  expect(accounts.statuses.epic.connected).toBe(true);
  expect(accounts.statuses.epic.owned_count).toBe(3);
});
it("does not contact either store while offline", async () => {
  Object.defineProperty(navigator, "onLine", { configurable: true, value: false });
  invoke.mockClear();
  expect(await accounts.refreshAll(true)).toBe(false);
  expect(invoke).not.toHaveBeenCalled();
});
it("cancels a closed sign-in without revealing games from its late response", async () => {
  act(() => accounts.open("epic"));
  let pending!: Promise<void>;
  act(() => { pending = accounts.run("connect"); });
  expect(accounts.busy).toBe(true);
  act(() => accounts.close());
  await act(async () => { holdConnect?.({ connected: true }); await pending; });
  expect(invoke).toHaveBeenCalledWith("store_account_cancel", { source: "epic" });
  expect(reveal).not.toHaveBeenCalled(); expect(scan).not.toHaveBeenCalled();
  expect(accounts.selected).toBeNull();
});
it("reveals owned games only after successful sign-in and rescans in the background", async () => {
  act(() => accounts.open("epic")); let pending!: Promise<void>;
  act(() => { pending = accounts.run("connect"); });
  connected = true;
  await act(async () => { holdConnect?.({ connected: true }); await pending; });
  expect(reveal).toHaveBeenCalledWith("epic");
  expect(scan).toHaveBeenCalledWith({ background: true });
  expect(accounts.statuses.epic.connected).toBe(true);
  await act(async () => { await accounts.run("disconnect"); });
  expect(accounts.statuses.epic.connected).toBe(false);
});

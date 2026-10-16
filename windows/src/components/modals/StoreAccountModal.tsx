import { useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";
import { useTheme } from "../../contexts/ThemeContext";
import { getBestGamepad, readGpState } from "../../utils/gamepad";
import type { useStoreAccounts } from "../../hooks/useStoreAccounts";

export function StoreAccountModal({ accounts }: { accounts: ReturnType<typeof useStoreAccounts> }) {
  const { t } = useTranslation();
  const { theme, accent } = useTheme();
  const panel = useRef<HTMLDivElement>(null);
  const returnFocus = useRef<HTMLElement | null>(null);
  const closeRef = useRef(accounts.close); closeRef.current = accounts.close;
  const source = accounts.selected;
  useEffect(() => {
    if (!source) return;
    returnFocus.current = document.activeElement as HTMLElement;
    const focus = () => panel.current?.querySelector<HTMLButtonElement>("button:not(:disabled)")?.focus();
    const timer = window.setTimeout(focus, 0);
    const move = (direction: number) => {
      const buttons = [...(panel.current?.querySelectorAll<HTMLButtonElement>("button:not(:disabled)") ?? [])];
      const index = buttons.indexOf(document.activeElement as HTMLButtonElement);
      buttons[(index + direction + buttons.length) % buttons.length]?.focus();
    };
    const key = (event: KeyboardEvent) => {
      if (event.key === "Escape") { event.preventDefault(); event.stopImmediatePropagation(); closeRef.current(); }
      else if (["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown", "Tab"].includes(event.key)) {
        event.preventDefault(); event.stopImmediatePropagation(); move(["ArrowLeft", "ArrowUp"].includes(event.key) || event.shiftKey ? -1 : 1);
      }
    };
    window.addEventListener("keydown", key, true);
    let last: ReturnType<typeof readGpState> | undefined;
    const poll = window.setInterval(() => {
      if (!document.hasFocus()) { last = undefined; return; }
      const gp = getBestGamepad(); if (!gp) { last = undefined; return; }
      const state = readGpState(gp);
      if (last) {
        if (state.Escape && !last.Escape) closeRef.current();
        else if (state.Enter && !last.Enter) (document.activeElement as HTMLButtonElement)?.click();
        else if ((state.ArrowRight && !last.ArrowRight) || (state.ArrowDown && !last.ArrowDown)) move(1);
        else if ((state.ArrowLeft && !last.ArrowLeft) || (state.ArrowUp && !last.ArrowUp)) move(-1);
      }
      last = state;
    }, 8);
    return () => { clearTimeout(timer); clearInterval(poll); window.removeEventListener("keydown", key, true); returnFocus.current?.focus({ preventScroll: true }); };
  }, [source]);
  useEffect(() => { if (source && !accounts.busy) panel.current?.querySelector<HTMLButtonElement>("button:not(:disabled)")?.focus(); }, [accounts.busy, source]);
  if (!source) return null;
  const status = accounts.statuses[source]; const store = source === "epic" ? "Epic Games" : "GOG";
  const button = { padding: "12px 18px", borderRadius: 10, border: "1px solid rgba(255,255,255,.2)", background: "#242a30", color: theme.text, cursor: "pointer" };
  return <div data-modal="store-account" style={{ position: "fixed", inset: 0, zIndex: 10050, display: "grid", placeItems: "center", background: "rgba(0,0,0,.7)", padding: 24 }}>
    <div ref={panel} role="dialog" aria-modal="true" aria-labelledby="store-account-title" style={{ width: "min(640px, 100%)", maxHeight: "90vh", overflow: "auto", padding: 32, borderRadius: 24, background: "#11171b", color: theme.text, border: "1px solid rgba(255,255,255,.2)" }}>
      <h2 id="store-account-title" style={{ marginTop: 0 }}>{t("storeAccounts.title", { store })}</h2>
      <p>{status.connected ? t("storeAccounts.connected", { account: status.account_name || store, count: status.owned_count }) : t("storeAccounts.hint", { store })}</p>
      <p style={{ color: theme.textDim }}>{t("storeAccounts.security")}</p>
      {accounts.busy && <p role="status">{t("storeAccounts.working")}</p>}
      {!!accounts.error && <p role="alert" style={{ color: "#ffaaaa", background: "rgba(180,30,30,.15)", borderRadius: 10, padding: 12 }}>{accounts.error}</p>}
      <div style={{ display: "flex", flexWrap: "wrap", gap: 12, marginTop: 24 }}>
        <button disabled={accounts.busy || !navigator.onLine} onClick={() => void accounts.run(status.connected ? "refresh" : "connect")} style={{ ...button, background: accent.primary, color: "#101010" }}>{t(status.connected ? "storeAccounts.refresh" : "storeAccounts.connect")}</button>
        {status.connected && <button disabled={accounts.busy} onClick={() => void accounts.run("disconnect")} style={button}>{t("storeAccounts.disconnect")}</button>}
        {status.connected && <button disabled={accounts.busy || !navigator.onLine} onClick={() => void accounts.run("connect")} style={button}>{t("storeAccounts.reconnect")}</button>}
        <button onClick={accounts.close} style={button}>{t("storeAccounts.close")}</button>
      </div>
    </div>
  </div>;
}

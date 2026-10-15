import { useEffect, useRef, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import type { App } from "../../types";
import { useTranslation } from "react-i18next";
import { getBestGamepad, readGpState } from "../../utils/gamepad";

type Provider = "moonlight" | "geforceNow";
type Cover = { app: App; returning?: boolean } | null;
type ReturnPhase = { provider: Provider; phase: "ending" | "ended"; session: number };
let lastStream: { app: App; provider: Provider } | null = null;
let dismissed = false;
let generation = 0;
export async function withStreamCover(app: App, provider: "moonlight" | "geforceNow", launch: () => Promise<unknown>) {
  const current = ++generation;
  const shownAt = performance.now();
  dismissed = false;
  lastStream = { app, provider };
  window.dispatchEvent(new CustomEvent("crete-stream-cover", { detail: { app } }));
  try {
    await invoke("set_launch_cover", { active: true, streamProvider: provider });
    // Give the artwork two paint opportunities before another client can
    // create a foreground/topmost connection window.
    await new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve())));
    if (dismissed || current !== generation) return;
    await launch();
    const ready = await invoke<boolean>("wait_stream_window", { provider });
    // Do not delay the launch itself. Keep artwork visible for at least ten
    // seconds, except when the client reports an error/needs interaction.
    if (ready !== false && !dismissed && current === generation) {
      await new Promise<void>(resolve => window.setTimeout(resolve, Math.max(0, 10_000 - (performance.now() - shownAt))));
    }
    // Transfer the native foreground while the artwork is still opaque. Fading
    // a child overlay reveals our details page, not the external stream window.
    if (!dismissed && current === generation) await invoke("focus_stream_window", { provider });
  } finally {
    if (current === generation) {
      window.dispatchEvent(new CustomEvent("crete-stream-cover", { detail: null }));
      await invoke("set_launch_cover", { active: false }).catch(() => {});
    }
  }
}

export function StreamLaunchCover({ heroArt, customHeroArt }: { heroArt: Record<string, string>; customHeroArt: Record<string, string> }) {
  const { t } = useTranslation();
  const [cover, setCover] = useState<Cover>(null);
  const returnTicket = useRef(0);
  const returnSession = useRef<number | null>(null);
  const dismiss = () => { dismissed = true; ++returnTicket.current; setCover(null); void invoke("set_launch_cover", { active: false }); };
  useEffect(() => {
    const change = (event: Event) => {
      const detail = (event as CustomEvent<Cover>).detail;
      if (detail) { ++returnTicket.current; returnSession.current = null; setCover(detail); }
      else setCover(previous => previous?.returning ? previous : null);
    };
    window.addEventListener("crete-stream-cover", change);
    return () => { window.removeEventListener("crete-stream-cover", change); };
  }, []);
  useEffect(() => {
    let disposed = false;
    let release: (() => void) | undefined;
    const listener = listen<ReturnPhase>("stream-return-phase", event => {
      if (disposed || !lastStream || lastStream.provider !== event.payload.provider) return;
      if (event.payload.phase === "ended" && returnSession.current !== event.payload.session) return;
      const ticket = ++returnTicket.current;
      if (event.payload.phase === "ended") { returnSession.current = null; setCover(previous => previous?.returning ? null : previous); return; }
      returnSession.current = event.payload.session;
      const app = lastStream.app;
      setCover({ app, returning: true });
      // Let the renderer paint the artwork before the native window is shown.
      requestAnimationFrame(() => requestAnimationFrame(() => {
        if (disposed || ticket !== returnTicket.current) return;
        void invoke<boolean>("present_stream_return_cover", { provider: event.payload.provider, session: event.payload.session })
          .then(shown => { if (!shown && ticket === returnTicket.current) setCover(previous => previous?.returning ? null : previous); })
          .catch(() => { if (ticket === returnTicket.current) setCover(previous => previous?.returning ? null : previous); });
      }));
    });
    void listener.then(unlisten => { if (disposed) unlisten(); else release = unlisten; }).catch(() => {});
    return () => { disposed = true; ++returnTicket.current; release?.(); };
  }, []);
  useEffect(() => {
    if (!cover?.returning) return;
    // A lost event or broken client must not leave the handheld behind artwork.
    const timer = window.setTimeout(dismiss, 12_000);
    return () => window.clearTimeout(timer);
  }, [cover?.returning]);
  useEffect(() => {
    if (!cover) return;
    const key = (event: KeyboardEvent) => { event.preventDefault(); event.stopImmediatePropagation(); if (event.key === "Escape") dismiss(); };
    window.addEventListener("keydown", key, true);
    let frame = 0; let previous = true;
    const poll = () => { const gamepad = getBestGamepad(); if (gamepad) { const pressed = !!readGpState(gamepad).Escape; if (pressed && !previous) dismiss(); previous = pressed; } frame = requestAnimationFrame(poll); };
    frame = requestAnimationFrame(poll);
    return () => { cancelAnimationFrame(frame); window.removeEventListener("keydown", key, true); };
  }, [!!cover]);
  if (!cover) return null;
  const art = customHeroArt[cover.app.id] || heroArt[cover.app.id];
  return <div data-stream-launch-cover="" role="status" aria-label={t("launch.launching")} style={{ position: "fixed", inset: 0, zIndex: 10000, display: "grid", placeContent: "center", background: art ? `linear-gradient(rgba(0,0,0,.15),rgba(0,0,0,.4)),url(${JSON.stringify(art)}) center/cover` : "rgba(10,13,16,.92)" }}>
    <style>{"@keyframes creteLaunchSpinner { to { transform:rotate(360deg) } }"}</style>
    <span aria-hidden="true" style={{ width: 42, height: 42, borderRadius: "50%", border: "3px solid rgba(242,234,219,.18)", borderTopColor: "rgba(242,234,219,.7)", animation: "creteLaunchSpinner 1s linear infinite" }} />
  </div>;
}

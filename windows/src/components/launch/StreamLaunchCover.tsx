import { useEffect, useRef, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import type { App } from "../../types";
import { useTranslation } from "react-i18next";
import { getBestGamepad, readGpState } from "../../utils/gamepad";
import { LaunchMessages } from "./LaunchMessages";

type Provider = "moonlight" | "geforceNow";
type Cover = { app: App; returning?: boolean } | null;
type ReturnPhase = { provider: Provider; phase: "ending" | "ended"; session: number };
let lastStream: { app: App; provider: Provider } | null = null;
let dismissed = false;
let generation = 0;
let cancelActive: (() => void) | undefined;
function cancelLaunch(notifyNative = true) {
  dismissed = true;
  ++generation;
  lastStream = null;
  cancelActive?.();
  cancelActive = undefined;
  window.dispatchEvent(new CustomEvent("crete-stream-cover", { detail: null }));
  if (notifyNative) void invoke("cancel_stream_launch").catch(() => {});
}
export async function withStreamCover(app: App, provider: "moonlight" | "geforceNow", launch: () => Promise<unknown>, beforeHandoff?: () => void) {
  if (cancelActive) throw new Error("A stream launch is already in progress. Cancel it before trying again.");
  const current = ++generation;
  const cancelled = Symbol("cancelled");
  let cancel!: () => void;
  const cancellation = new Promise<typeof cancelled>(resolve => { cancel = () => resolve(cancelled); });
  cancelActive = cancel;
  const shownAt = performance.now();
  dismissed = false;
  let handedOff = false;
  let releaseAttention: (() => void) | undefined;
  lastStream = { app, provider };
  window.dispatchEvent(new CustomEvent("crete-stream-cover", { detail: { app } }));
  try {
    if (provider === "moonlight") releaseAttention = await listen("host-launch-attention", () => {
      if (current !== generation || dismissed) return;
      // A real Steam launch dialog must be accessible rather than concealed
      // indefinitely by either device's artwork. Do not approve it ourselves.
      dismissed = true;
      beforeHandoff?.();
      requestAnimationFrame(() => requestAnimationFrame(() => {
        if (current !== generation) return;
        void invoke("focus_stream_window", { provider }).catch(() => {});
        window.dispatchEvent(new CustomEvent("crete-stream-cover", { detail: null }));
      }));
    });
    await invoke("set_launch_cover", { active: true, streamProvider: provider });
    // Give the artwork two paint opportunities before another client can
    // create a foreground/topmost connection window.
    await new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve())));
    if (dismissed || current !== generation) return;
    if (await Promise.race([launch(), cancellation]) === cancelled) return;
    if (current !== generation) return;
    // Streaming bypasses launch_app, so it must explicitly share its history.
    const entry = await invoke<App>("record_stream_launch", { id: app.id, name: app.name, launchPath: app.launch_path ?? "" }).catch(() => null);
    if (dismissed || current !== generation) return;
    if (entry) window.dispatchEvent(new CustomEvent("crete-play-history-changed", { detail: entry }));
    const ready = await Promise.race([invoke<boolean>("wait_stream_window", { provider }), cancellation]);
    if (ready === cancelled || dismissed || current !== generation) return;
    // Do not delay the launch itself. Keep artwork visible for at least ten
    // seconds, except when the client reports an error/needs interaction.
    if (ready !== false && !dismissed && current === generation) {
      if (await Promise.race([new Promise<void>(resolve => window.setTimeout(resolve, Math.max(0, 10_000 - (performance.now() - shownAt)))), cancellation]) === cancelled) return;
    }
    // Transfer the native foreground while the artwork is still opaque. Fading
    // a child overlay reveals our details page, not the external stream window.
    if (!dismissed && current === generation) {
      beforeHandoff?.();
      await new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve())));
      if (dismissed || current !== generation) return;
      await invoke("focus_stream_window", { provider });
      handedOff = true;
    }
  } finally {
    releaseAttention?.();
    if (cancelActive === cancel) cancelActive = undefined;
    if (current === generation) {
      window.dispatchEvent(new CustomEvent("crete-stream-cover", { detail: null }));
      // Native handoff has already released the cover. No window changes after
      // it confirms foreground: late cleanup can disturb shell composition.
      if (!handedOff) await invoke("set_launch_cover", { active: false }).catch(() => {});
    }
  }
}

export function StreamLaunchCover({ heroArt, customHeroArt }: { heroArt: Record<string, string>; customHeroArt: Record<string, string> }) {
  const { t } = useTranslation();
  const [cover, setCover] = useState<Cover>(null);
  const returnTicket = useRef(0);
  const returnSession = useRef<number | null>(null);
  const dismiss = (notifyNative = true) => {
    ++returnTicket.current; setCover(null);
    if (cover?.returning) { dismissed = true; void invoke("set_launch_cover", { active: false }).catch(() => {}); }
    else cancelLaunch(notifyNative);
  };
  useEffect(() => {
    let disposed = false;
    let release: (() => void) | undefined;
    void listen("stream-launch-cancel", () => { if (!disposed) { ++returnTicket.current; setCover(null); cancelLaunch(false); } })
      .then(unlisten => { if (disposed) unlisten(); else release = unlisten; }).catch(() => {});
    return () => { disposed = true; release?.(); };
  }, []);
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
          .then(() => { if (ticket === returnTicket.current) setCover(previous => previous?.returning ? null : previous); })
          .catch(() => { if (ticket === returnTicket.current) setCover(previous => previous?.returning ? null : previous); });
      }));
    });
    void listener.then(unlisten => { if (disposed) unlisten(); else release = unlisten; }).catch(() => {});
    return () => { disposed = true; ++returnTicket.current; release?.(); };
  }, []);
  useEffect(() => {
    if (!cover?.returning) return;
    // A lost event or broken client must not leave the handheld behind artwork.
    const timer = window.setTimeout(() => dismiss(), 12_000);
    return () => window.clearTimeout(timer);
  }, [cover?.returning]);
  useEffect(() => {
    if (!cover) return;
    // Never leave an unresponsive launch behind indefinite artwork.
    const timeout = window.setTimeout(() => dismiss(), 180_000);
    const key = (event: KeyboardEvent) => { event.preventDefault(); event.stopImmediatePropagation(); if (event.key === "Escape") dismiss(); };
    window.addEventListener("keydown", key, true);
    let frame = 0; let previous = true;
    const poll = () => { const gamepad = getBestGamepad(); if (gamepad) { const pressed = !!readGpState(gamepad).Escape; if (pressed && !previous) dismiss(); previous = pressed; } frame = requestAnimationFrame(poll); };
    frame = requestAnimationFrame(poll);
    return () => { clearTimeout(timeout); cancelAnimationFrame(frame); window.removeEventListener("keydown", key, true); };
  }, [!!cover]);
  if (!cover) return null;
  const art = customHeroArt[cover.app.id] || heroArt[cover.app.id];
  return <div data-stream-launch-cover="" role="status" aria-label={t("launch.launching")} style={{ position: "fixed", inset: 0, zIndex: 10000, display: "grid", placeContent: "center", background: art ? `linear-gradient(rgba(0,0,0,.15),rgba(0,0,0,.4)),url(${JSON.stringify(art)}) center/cover` : "rgba(10,13,16,.92)" }}>
    {!cover.returning && <LaunchMessages />}
    {!cover.returning && <button type="button" onClick={() => dismiss()} style={{ position: "absolute", bottom: 40, justifySelf: "center", padding: "12px 24px", borderRadius: 12, border: "1px solid rgba(255,255,255,.4)", background: "rgba(10,13,16,.75)", color: "white", fontSize: 18 }}>B / Esc · Cancel launch</button>}
  </div>;
}

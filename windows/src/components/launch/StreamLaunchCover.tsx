import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import type { App } from "../../types";
import { useTranslation } from "react-i18next";
import { getBestGamepad, readGpState } from "../../utils/gamepad";

type Cover = { app: App } | null;
let dismissed = false;
export async function withStreamCover(app: App, provider: "moonlight" | "geforceNow", launch: () => Promise<unknown>) {
  dismissed = false;
  window.dispatchEvent(new CustomEvent("crete-stream-cover", { detail: { app } }));
  try {
    await invoke("set_launch_cover", { active: true });
    await launch();
    await invoke("wait_stream_window", { provider });
    // Transfer the native foreground while the artwork is still opaque. Fading
    // a child overlay reveals our details page, not the external stream window.
    if (!dismissed) await invoke("focus_stream_window", { provider });
  } finally {
    window.dispatchEvent(new CustomEvent("crete-stream-cover", { detail: null }));
    await invoke("set_launch_cover", { active: false }).catch(() => {});
  }
}

export function StreamLaunchCover({ heroArt, customHeroArt }: { heroArt: Record<string, string>; customHeroArt: Record<string, string> }) {
  const { t } = useTranslation();
  const [cover, setCover] = useState<Cover>(null);
  const dismiss = () => { dismissed = true; setCover(null); void invoke("set_launch_cover", { active: false }); };
  useEffect(() => {
    const change = (event: Event) => setCover((event as CustomEvent<Cover>).detail);
    window.addEventListener("crete-stream-cover", change);
    return () => { window.removeEventListener("crete-stream-cover", change); };
  }, []);
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

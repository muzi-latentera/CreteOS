import { useEffect, useRef, useState } from "react";
import { IoChevronDownOutline } from "react-icons/io5";
import { useCreteText } from "./text";
import { invoke } from "@tauri-apps/api/core";
import { getBestGamepad, readGpState } from "../utils/gamepad";
import type { useStreaming, MoonlightProfile } from "./useStreaming";

import { HomeWifiSelect } from "./HomeWifiSelect";

type Streaming = ReturnType<typeof useStreaming>;
export function LaunchMenu({ streaming: s, open, setOpen, local, localLabel, localEnabled, manage, manageLabel, block, loading = false, copies = [] }: {
  streaming: Streaming; open: boolean; setOpen: (open: boolean) => void; local: () => void;
  localLabel: string; localEnabled: boolean; manage: () => void; manageLabel: string; block: (blocked: boolean) => void; loading?: boolean;
  copies?: { key: string; label: string; onClick: () => void }[];
}) {
  const text = useCreteText();
  const root = useRef<HTMLDivElement>(null);
  const anchor = useRef<HTMLButtonElement>(null);
  const [profile, setProfile] = useState<MoonlightProfile>(s.display.profile);
  const [target, setTarget] = useState("");
  const [busy, setBusy] = useState(false);
  const [wifiOnly, setWifiOnly] = useState(false);
  const [pin, setPin] = useState<number | null>(null);
  const [choices, setChoices] = useState<HTMLSelectElement | null>(null);
  const choicesRef = useRef<HTMLSelectElement | null>(null);
  const openChoices = (select: HTMLSelectElement) => { choicesRef.current = select; setChoices(select); };
  const closeChoices = () => { const select = choicesRef.current; choicesRef.current = null; setChoices(null); select?.focus(); };
  const gfn = s.geforceConfigure;
  const activeDisplay = gfn ? s.geforceDisplay : s.display;
  const nativeResolution = activeDisplay.nativeResolution ?? "1920x1080";
  const nativeLabel = activeDisplay.displayLabel ?? "Handheld";
  const nativeFps = activeDisplay.nativeFps === 90 && gfn ? 60 : activeDisplay.nativeFps ?? 120;
  const active = open || s.configure || gfn || wifiOnly;
  useEffect(() => { block(active); return () => block(false); }, [active, block]);
  useEffect(() => {
    if (!s.configure && !gfn) return;
    setOpen(false); setProfile(gfn ? s.geforceDisplay.profile : s.display.profile);
  }, [s.configure, gfn, s.display.profile, s.geforceDisplay.profile]);
  useEffect(() => {
    if (!s.configure) return;
    setTarget(s.moonlightGame || (s.steamId && s.apps.includes("MoonDeckStream") ? "MoonDeckStream" : undefined) || s.apps.find(name => name === "Steam Big Picture") || s.apps[0] || "");
  }, [s.configure, s.apps, s.moonlightGame]);
  const close = () => {
    const returnFocus = s.configure || gfn ? root.current?.closest('[data-crete-detail-slot="primary-actions"]')?.querySelector<HTMLButtonElement>('[data-launch-btn]') : anchor.current;
    setOpen(false); setWifiOnly(false); s.setConfigure(false); s.setGeforceConfigure(false); choicesRef.current = null; setChoices(null);
    returnFocus?.focus({ preventScroll: true });
  };
  useEffect(() => {
    if (!active) return;
    const controls = () => Array.from((root.current?.querySelector('[data-stream-choices]') ?? root.current)?.querySelectorAll<HTMLButtonElement | HTMLSelectElement>('button:not([disabled]), select:not([disabled])') ?? []).filter(el => el !== anchor.current);
    const timer = window.setTimeout(() => controls()[0]?.focus(), 0);
    const move = (direction: number) => {
      const list = controls(); const index = list.indexOf(document.activeElement as HTMLButtonElement);
      const next = list[(index + direction + list.length) % list.length];
      next?.focus(); next?.scrollIntoView({ block: "nearest", inline: "nearest" });
    };
    const navigate = (direction: "up" | "down" | "left" | "right") => {
      const list = controls();
      const focused = document.activeElement as HTMLButtonElement | HTMLSelectElement;
      // Choice lists and the provider menu are vertical. Footer actions form
      // a horizontal row; Down must not silently mean Right in that row.
      const footer = focused?.closest(".crete-stream-buttons");
      if (footer) {
        if (direction === "down") return;
        if (direction === "up") {
          const fields = list.filter(el => !el.closest(".crete-stream-buttons"));
          fields[fields.length - 1]?.focus();
          return;
        }
        const row = list.filter(el => el.closest(".crete-stream-buttons") === footer);
        const index = row.indexOf(focused);
        row[Math.max(0, Math.min(row.length - 1, index + (direction === "right" ? 1 : -1)))]?.focus();
        return;
      }
      if (direction === "up" || direction === "down") move(direction === "up" ? -1 : 1);
    };
    const key = (event: KeyboardEvent) => {
      if (document.querySelector("[data-stream-launch-cover]")) return;
      if (event.key === "Escape") { event.preventDefault(); event.stopImmediatePropagation(); if (choicesRef.current) closeChoices(); else close(); }
      else if (event.key === "Enter" && event.target instanceof HTMLSelectElement) { event.preventDefault(); event.stopImmediatePropagation(); openChoices(event.target); }
      else if (["ArrowUp", "ArrowDown"].includes(event.key)) {
        event.preventDefault(); event.stopImmediatePropagation(); navigate(event.key === "ArrowUp" ? "up" : "down");
      }
      else if (["ArrowLeft", "ArrowRight"].includes(event.key) && (event.target as HTMLElement)?.closest(".crete-stream-buttons")) { event.preventDefault(); event.stopImmediatePropagation(); navigate(event.key === "ArrowLeft" ? "left" : "right"); }
      else if (event.key === "Tab") { event.preventDefault(); event.stopImmediatePropagation(); move(event.shiftKey ? -1 : 1); }
    };
    const outside = (event: PointerEvent) => { if (!document.querySelector("[data-stream-launch-cover]") && !root.current?.contains(event.target as Node)) close(); };
    window.addEventListener("keydown", key, true); window.addEventListener("pointerdown", outside);
    let frame = 0; let last: ReturnType<typeof readGpState> | undefined;
    const poll = () => {
      if (document.querySelector("[data-stream-launch-cover]")) { frame = requestAnimationFrame(poll); return; }
      const gp = getBestGamepad();
      if (gp) {
        const state = readGpState(gp);
        if (last) {
          if (state.Escape && !last.Escape) { if (choicesRef.current) closeChoices(); else close(); }
          else if (state.ArrowDown && !last.ArrowDown) navigate("down");
          else if (state.ArrowUp && !last.ArrowUp) navigate("up");
          else if (state.Enter && !last.Enter && document.activeElement instanceof HTMLButtonElement) document.activeElement.click();
          else if (state.Enter && !last.Enter && document.activeElement instanceof HTMLSelectElement) openChoices(document.activeElement);
          else if ((state.ArrowRight && !last.ArrowRight || state.ArrowLeft && !last.ArrowLeft) && document.activeElement instanceof HTMLSelectElement) {
            const select = document.activeElement;
            select.selectedIndex = Math.max(0, Math.min(select.options.length - 1, select.selectedIndex + (state.ArrowRight ? 1 : -1)));
            select.dispatchEvent(new Event("change", { bubbles: true }));
          }
          else if (state.ArrowRight && !last.ArrowRight) navigate("right");
          else if (state.ArrowLeft && !last.ArrowLeft) navigate("left");
        }
        last = state;
      }
      frame = requestAnimationFrame(poll);
    };
    frame = requestAnimationFrame(poll);
    return () => { clearTimeout(timer); cancelAnimationFrame(frame); window.removeEventListener("keydown", key, true); window.removeEventListener("pointerdown", outside); };
  }, [active, s.configure, gfn]);
  useEffect(() => { if (choices) window.setTimeout(() => root.current?.querySelector<HTMLButtonElement>('[data-stream-choices] button[aria-selected="true"]')?.focus(), 0); }, [choices]);
  const choose = (action: () => void) => { setOpen(false); action(); };
  const row = (label: string, provider: string, color: string, action: () => void, disabled = false, key?: string) => <button key={key} type="button" role="menuitem" disabled={disabled} onClick={() => choose(action)}><i style={{ background: color }} />{text(label)}{s.provider === provider && <small aria-hidden="true">{text("DEFAULT")}</small>}</button>;
  const preset = (value: string) => {
    const values: Record<string, [string, number]> = { native: [nativeResolution, nativeFps], xreal: ["1920x1200", gfn ? 60 : 90], tv: ["3840x2160", 60] };
    const [resolution, fps] = values[value] ?? [(gfn ? s.geforceDisplay : s.display).profile.resolution, (gfn ? s.geforceDisplay : s.display).profile.fps];
    setProfile(p => ({ ...p, resolution, fps, bitrate: gfn ? p.bitrate : 80000 }));
  };
  return <div ref={root} className="crete-launch-menu">
    {!loading && <button ref={anchor} type="button" className="crete-details-manage" aria-label={text("Launch options")} aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen(!open)}><IoChevronDownOutline /></button>}
    {open && !loading && <div className="crete-launch-popover" role="menu" aria-label={text("Launch options")}>
      {row(localLabel, "local", "#c9482a", local, !localEnabled)}
      {s.provider === "luna" && row("Amazon Luna Wi-Fi", "", "#b49de5", () => setWifiOnly(true))}
      {copies.map(copy => row(copy.label, "copy", "#a7a29a", copy.onClick, false, copy.key))}
      {s.pcGame && row("Moonlight", "moonlight", "#697580", () => s.launch("moonlight"), !s.clients.moonlight)}
      {s.gameUrl && row("GeForce NOW", "geforceNow", "#7cab00", () => s.launch("geforceNow", true))}
      {!s.gameUrl && s.clients.geforceNow && row("GeForce NOW library", "", "#7cab00", () => s.launch("geforceNow"))}
      <button type="button" role="menuitem" onClick={() => choose(manage)}>{text(manageLabel)}</button>
    </div>}
    {(s.configure || gfn || wifiOnly) && <div className="crete-stream-backdrop"><div role="dialog" aria-modal="true" aria-label={text(wifiOnly ? "Amazon Luna Wi-Fi" : gfn ? "GeForce NOW stream settings" : "Moonlight stream settings")} className="crete-stream-settings">
      <h2>{wifiOnly ? "Amazon Luna" : gfn ? "GeForce NOW" : <>{text("Play on")} {profile.host}</>}</h2>
      <HomeWifiSelect provider={wifiOnly ? "luna" : gfn ? "geforceNow" : "moonlight"} saving={setBusy} />
      {!wifiOnly && <>
      {gfn && <p>{text("Close GeForce NOW before applying settings. NVIDIA may limit quality according to your membership and device. Codec is managed in the NVIDIA app.")}</p>}
      {!gfn && <p>{text(target === "MoonDeckStream" && s.steamId ? s.hostInstalled ? "Installed on Bosgame. Launch this game directly through MoonDeck Buddy." : s.hostGames ? "This game is not installed on Bosgame. Choose another host app, local install or GeForce NOW." : "Checking whether this game is installed on Bosgame…" : s.moonlightGame ? "Game available on the host." : "Choose a host app. Its advertised apps do not confirm this game is installed.")}</p>}
      {!gfn && s.inventoryError && <p role="status">{s.inventoryError}</p>}
      {!gfn && target === "MoonDeckStream" && s.steamId && <div className="crete-buddy-pair">
        {s.buddy?.paired ? <p>{text("CreteOS is paired with MoonDeck Buddy.")}</p> : <><p>{text("MoonDeck Buddy needs a separate pairing for CreteOS.")}</p><button type="button" onClick={() => { const bytes = crypto.getRandomValues(new Uint32Array(1)); const value = 1000 + bytes[0] % 9000; setPin(value); void s.pair(value); }}>{text("Pair MoonDeck Buddy")}</button>{pin && <p>{text("Enter PIN on Bosgame and choose Refresh host", { pin })}</p>}{s.buddyError && <p role="status">{s.buddyError}</p>}</>}
      </div>}
      {!gfn && <label>{text("Host app")}<select aria-label={text("Host app")} value={target} onChange={e => setTarget(e.target.value)}>{s.apps.map(name => <option key={name}>{name}</option>)}</select></label>}
      <label>{text("Display preset")}<select aria-label={text("Display preset")} defaultValue="auto" onChange={e => preset(e.target.value)}><option value="auto">{text("Current display")}</option><option value="native">{nativeLabel} · {nativeResolution.split("x")[1]}p / {nativeFps}fps</option><option value="xreal">XREAL · 1200p / {gfn ? 60 : 90}fps</option><option value="tv">TV · 4K / 60fps</option></select></label>
      <label>{text("Resolution")}<select aria-label={text("Resolution")} value={profile.resolution} onChange={e => setProfile(p => ({ ...p, resolution: e.target.value }))}>{["1280x720", "1920x1080", "1920x1200", "2560x1440", "2560x1600", "3840x2160"].map(value => <option key={value}>{value}</option>)}</select></label>
      <label>{text("Frame rate")}<select aria-label={text("Frame rate")} value={profile.fps} onChange={e => setProfile(p => ({ ...p, fps: Number(e.target.value) }))}>{(gfn ? [30, 60, 120, 240] : [30, 60, 90, 120]).map(value => <option key={value} value={value}>{value}fps</option>)}</select></label>
      <label>{text("Bitrate")}<select aria-label={text("Bitrate")} value={profile.bitrate} onChange={e => setProfile(p => ({ ...p, bitrate: Number(e.target.value) }))}>{gfn && <option value={0}>{text("Auto")}</option>}{Array.from({ length: gfn ? 20 : 30 }, (_, i) => (i + 1) * 5000).map(value => <option key={value} value={value}>{value / 1000}Mbps</option>)}</select></label>
      {!gfn && <label>{text("Codec")}<select aria-label={text("Codec")} value={profile.codec} onChange={e => setProfile(p => ({ ...p, codec: e.target.value }))}><option value="auto">{text("Automatic")}</option><option value="H.264">H.264</option><option value="HEVC">H.265</option><option value="AV1">AV1</option></select></label>}
      {!gfn && <p>{text("Saved host availability refreshes once a day. Starting a stream checks the connection.")}</p>}
      {!s.online && <p role="status">{text("No network connection. Installed local games are available offline.")}</p>}
      {!gfn && s.hostError && <p role="status">{text(s.hostError)}</p>}{s.error && <p role="alert">{s.error}</p>}
      {s.error && s.localInstalled && localEnabled && <button type="button" onClick={() => { close(); local(); }}>{text("Play locally")}</button>}
      {gfn && <p>{text("Play uses your settings above. Auto bitrate lets NVIDIA choose the bitrate.")}</p>}
      {gfn ? <div className="crete-stream-buttons"><button className="crete-gfn-play" type="button" disabled={busy || !s.online} onClick={() => { setBusy(true); void s.geforceStream(profile).finally(() => setBusy(false)); }}>{text(busy ? "Connecting…" : "Play")}</button><button type="button" onClick={close}>{text("Cancel")}</button></div> : <div className="crete-stream-buttons"><button type="button" disabled={busy || !target || !s.online || s.checking || (target === "MoonDeckStream" && !!s.steamId && (!s.buddy?.paired || !s.hostInstalled))} onClick={() => { setBusy(true); void s.stream(target, profile).finally(() => setBusy(false)); }}>{text(busy ? "Connecting…" : "Start stream")}</button><button type="button" disabled={busy || s.refreshing || !s.online} onClick={s.refresh}>{text(s.refreshing ? "Checking…" : "Refresh host")}</button><button type="button" onClick={() => void invoke("launch_streaming_client", { provider: "moonlight" }).catch(() => {} )}>{text("Open Moonlight")}</button><button type="button" onClick={close}>{text("Cancel")}</button></div>}
      {!gfn && <p>{text("Settings are saved for this display when the stream starts. Codec availability depends on the host and device.")}</p>}
      </>}
      {wifiOnly && <div className="crete-stream-buttons"><button type="button" disabled={busy} onClick={close}>Done</button></div>}
      {choices && <div className="crete-stream-choice-backdrop"><div data-stream-choices role="listbox" aria-label={choices.getAttribute("aria-label") ?? "Options"} className="crete-stream-choices">{Array.from(choices.options).map(option => <button type="button" role="option" aria-selected={option.selected} disabled={option.disabled} key={option.value} onClick={() => { choices.value = option.value; choices.dispatchEvent(new Event("change", { bubbles: true })); closeChoices(); }}>{option.text}</button>)}</div></div>}
    </div></div>}
  </div>;
}

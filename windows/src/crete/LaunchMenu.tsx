import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import "./launchMenus.css";
import { IoChevronDownOutline } from "react-icons/io5";
import { useCreteText } from "./text";
import { invoke } from "@tauri-apps/api/core";
import { getBestGamepad, readGpState } from "../utils/gamepad";
import type { useStreaming, MoonlightProfile } from "./useStreaming";

import { HomeWifiSelect } from "./HomeWifiSelect";
import { asobiLabel, type AsobiProvider } from "./asobi";

type Streaming = ReturnType<typeof useStreaming>;
export function LaunchMenu({ streaming: s, open, setOpen, local, localLabel, localEnabled, manage, manageLabel, block, restorePlayFocus, loading = false, copies = [] }: {
  streaming: Streaming; open: boolean; setOpen: (open: boolean) => void; local: () => void;
  localLabel: string; localEnabled: boolean; manage: () => void; manageLabel: string; block: (blocked: boolean) => void; restorePlayFocus?: () => void; loading?: boolean;
  copies?: { key: string; label: string; onClick: () => void }[];
}) {
  const text = useCreteText();
  const root = useRef<HTMLDivElement>(null);
  const panelRoot = useRef<HTMLDivElement>(null);
  const [advanced, setAdvanced] = useState(false);
  const [tools, setTools] = useState(false);
  const [displayPreset, setDisplayPreset] = useState("auto");
  const anchor = useRef<HTMLButtonElement>(null);
  const [profile, setProfile] = useState<MoonlightProfile>(s.display.profile);
  const [target, setTarget] = useState("");
  const [busy, setBusy] = useState(false);
  const [wifiOnly, setWifiOnly] = useState(false);
  const [pin, setPin] = useState<number | null>(null);
  const [psTarget, setPsTarget] = useState("");
  const ps = s.asobiConfigure;
  const psGames = s.asobiGames.filter(game => game.provider === ps);
  useEffect(() => { if (ps) { setOpen(false); setPsTarget(s.asobiGames.find(game => game.provider === ps)?.id ?? ""); } }, [ps, s.asobiGames]);
  const [choices, setChoices] = useState<HTMLSelectElement | null>(null);
  const choicesRef = useRef<HTMLSelectElement | null>(null);
  const openChoices = (select: HTMLSelectElement) => { choicesRef.current = select; setChoices(select); };
  const closeChoices = () => { const select = choicesRef.current; choicesRef.current = null; setChoices(null); select?.focus(); };
  const gfn = s.geforceConfigure;
  const activeDisplay = gfn ? s.geforceDisplay : s.display;
  const nativeResolution = activeDisplay.nativeResolution ?? "1920x1080";
  const nativeLabel = activeDisplay.displayLabel ?? "Handheld";
  const nativeFps = activeDisplay.nativeFps === 90 && gfn ? 60 : activeDisplay.nativeFps ?? 120;
  const active = open || s.configure || gfn || wifiOnly || !!ps;
  useEffect(() => { block(active); return () => block(false); }, [active, block]);
  useEffect(() => {
    if (!s.configure && !gfn) return;
    setOpen(false); setAdvanced(false); setTools(false); setDisplayPreset("auto"); setProfile(gfn ? s.geforceDisplay.profile : s.display.profile);
  }, [s.configure, gfn, s.display.profile, s.geforceDisplay.profile]);
  useEffect(() => {
    if (!s.configure) return;
    setTarget(s.moonlightGame || (s.steamId && s.apps.includes("MoonDeckStream") ? "MoonDeckStream" : undefined) || s.apps.find(name => name === "Steam Big Picture") || s.apps[0] || "");
  }, [s.configure, s.apps, s.moonlightGame]);
  const close = () => {
    const returnFocus = s.configure || gfn || wifiOnly || ps ? root.current?.closest('[data-crete-detail-slot="primary-actions"], .crete-hero')?.querySelector<HTMLButtonElement>('[data-launch-btn], .crete-play') ?? anchor.current : anchor.current;
    setOpen(false); setWifiOnly(false); s.setConfigure(false); s.setGeforceConfigure(false); s.setAsobiConfigure(null); choicesRef.current = null; setChoices(null);
    restorePlayFocus?.();
    returnFocus?.focus({ preventScroll: true });
  };
  useEffect(() => {
    if (!active) return;
    const scope = () => panelRoot.current?.querySelector<HTMLElement>('[data-stream-choices]') ?? panelRoot.current?.querySelector<HTMLElement>('[role=dialog]') ?? root.current;
    const controls = () => Array.from(scope()?.querySelectorAll<HTMLButtonElement | HTMLSelectElement>('button:not([disabled]), select:not([disabled])') ?? []).filter(el => el !== anchor.current);
    const focus = (el?: HTMLElement) => { el?.focus({ preventScroll: true }); el?.scrollIntoView({ block: "nearest", inline: "nearest" }); };
    const repairFocus = () => {
      const list = controls();
      if (!list.some(el => el === document.activeElement)) {
        focus(scope()?.querySelector<HTMLButtonElement>("[data-launch-primary]:not(:disabled)") ?? list[0]);
      }
    };
    const timer = window.setTimeout(repairFocus, 0);
    const move = (direction: number, tab = false) => {
      const list = tab && choicesRef.current ? Array.from(panelRoot.current?.querySelectorAll<HTMLButtonElement>('.crete-stream-choice-panel button:not([disabled])') ?? []) : controls(); const index = list.indexOf(document.activeElement as HTMLButtonElement);
      const next = list[index < 0 ? (direction < 0 ? list.length - 1 : 0) : (index + direction + list.length) % list.length];
      focus(next);
    };
    const navigate = (direction: "up" | "down" | "left" | "right") => {
      const list = controls();
      const focused = document.activeElement as HTMLButtonElement | HTMLSelectElement;
      const grid = focused?.closest(".crete-stream-fields");
      if (grid && (direction === "left" || direction === "right")) {
        const top = focused.getBoundingClientRect().top;
        const row = list.filter(el => el.closest(".crete-stream-fields") === grid && Math.abs(el.getBoundingClientRect().top - top) < 12);
        const index = row.indexOf(focused);
        focus(row[Math.max(0, Math.min(row.length - 1, index + (direction === "right" ? 1 : -1)))]);
        return;
      }
      // Choice lists and the provider menu are vertical. Footer actions form
      // a horizontal row; Down must not silently mean Right in that row.
      const footer = focused?.closest(".crete-stream-buttons");
      if (footer) {
        if (direction === "down") return;
        if (direction === "up") {
          const fields = list.filter(el => !el.closest(".crete-stream-buttons"));
          focus(fields[fields.length - 1]);
          return;
        }
        const row = list.filter(el => el.closest(".crete-stream-buttons") === footer);
        const index = row.indexOf(focused);
        focus(row[Math.max(0, Math.min(row.length - 1, index + (direction === "right" ? 1 : -1)))]);
        return;
      }
      if (direction === "up" || direction === "down") move(direction === "up" ? -1 : 1);
    };
    const key = (event: KeyboardEvent) => {
      if (document.querySelector("[data-stream-launch-cover]")) return;
      if (choicesRef.current && event.key === "Home") { event.preventDefault(); event.stopImmediatePropagation(); focus(controls()[0]); }
      else if (choicesRef.current && event.key === "End") { event.preventDefault(); event.stopImmediatePropagation(); const list = controls(); focus(list[list.length - 1]); }
      else if (event.key === "Escape") { event.preventDefault(); event.stopImmediatePropagation(); if (choicesRef.current) closeChoices(); else close(); }
      else if ((event.key === "Enter" || event.key === " ") && choicesRef.current && event.target instanceof HTMLButtonElement) { event.preventDefault(); event.stopImmediatePropagation(); event.target.click(); }
      else if ((event.key === "Enter" || event.key === " ") && event.target instanceof HTMLSelectElement) { event.preventDefault(); event.stopImmediatePropagation(); openChoices(event.target); }
      else if (["ArrowUp", "ArrowDown"].includes(event.key)) {
        event.preventDefault(); event.stopImmediatePropagation(); navigate(event.key === "ArrowUp" ? "up" : "down");
      }
      else if (["ArrowLeft", "ArrowRight"].includes(event.key) && (event.target as HTMLElement)?.closest(".crete-stream-buttons, .crete-stream-fields")) { event.preventDefault(); event.stopImmediatePropagation(); navigate(event.key === "ArrowLeft" ? "left" : "right"); }
      else if (event.key === "Tab") { event.preventDefault(); event.stopImmediatePropagation(); move(event.shiftKey ? -1 : 1, true); }
    };
    const outside = (event: PointerEvent) => { if (!document.querySelector("[data-stream-launch-cover]") && !root.current?.contains(event.target as Node) && !panelRoot.current?.contains(event.target as Node)) close(); };
    // Returning from an option focuses the select before Enter is released.
    // Do not let that release open a second, native Windows picker.
    const release = (event: KeyboardEvent) => { if ((event.key === "Enter" || event.key === " ") && event.target instanceof HTMLSelectElement) { event.preventDefault(); event.stopImmediatePropagation(); } };
    window.addEventListener("keydown", key, true); window.addEventListener("keyup", release, true); window.addEventListener("pointerdown", outside);
    let frame = 0; let last: ReturnType<typeof readGpState> | undefined;
    let covered = false;
    const poll = () => {
      if (document.querySelector("[data-stream-launch-cover]")) {
        covered = true;
        last = undefined;
        frame = requestAnimationFrame(poll);
        return;
      }
      if (covered) {
        covered = false;
        repairFocus();
      }
      const gp = getBestGamepad();
      if (gp) {
        const state = readGpState(gp);
        if (last) {
          if (state.ArrowDown || state.ArrowUp || state.ArrowLeft || state.ArrowRight || state.Enter) repairFocus();
          if (state.Escape && !last.Escape) { if (choicesRef.current) closeChoices(); else close(); }
          else if (state.ArrowDown && !last.ArrowDown) navigate("down");
          else if (state.ArrowUp && !last.ArrowUp) navigate("up");
          else if (state.Enter && !last.Enter && document.activeElement instanceof HTMLButtonElement) document.activeElement.click();
          else if (state.Enter && !last.Enter && document.activeElement instanceof HTMLSelectElement) openChoices(document.activeElement);
          else if (state.ArrowRight && !last.ArrowRight) navigate("right");
          else if (state.ArrowLeft && !last.ArrowLeft) navigate("left");
        }
        last = state;
      }
      frame = requestAnimationFrame(poll);
    };
    frame = requestAnimationFrame(poll);
    return () => { clearTimeout(timer); cancelAnimationFrame(frame); window.removeEventListener("keydown", key, true); window.removeEventListener("keyup", release, true); window.removeEventListener("pointerdown", outside); };
  }, [active, s.configure, gfn, wifiOnly, ps]);
  useEffect(() => {
    if (!choices) return;
    const timer = window.setTimeout(() => {
      const list = panelRoot.current?.querySelector('[data-stream-choices]');
      (list?.querySelector<HTMLButtonElement>('button[aria-selected="true"]:not(:disabled)') ?? list?.querySelector<HTMLButtonElement>('button:not(:disabled)'))?.focus();
    }, 0);
    return () => clearTimeout(timer);
  }, [choices]);
  useEffect(() => {
    if (busy || !(s.error || s.asobiError) || !(s.configure || gfn || ps)) return;
    const frame = requestAnimationFrame(() => {
      if (!document.querySelector("[data-stream-launch-cover]")) {
        panelRoot.current?.querySelector<HTMLButtonElement>("[data-launch-primary]:not(:disabled)")?.focus({ preventScroll: true });
      }
    });
    return () => cancelAnimationFrame(frame);
  }, [busy, s.error, s.asobiError, s.configure, gfn, ps]);
  const choose = (action: () => void) => { setOpen(false); action(); };
  const row = (label: string, provider: string, color: string, action: () => void, disabled = false, key?: string) => <button key={key} type="button" role="menuitem" disabled={disabled} onClick={() => choose(action)}><i style={{ background: color }} />{text(label)}{s.provider === provider && <small aria-hidden="true">{text("DEFAULT")}</small>}</button>;
  const preset = (value: string) => {
    setDisplayPreset(value);
    const values: Record<string, [string, number]> = { native: [nativeResolution, nativeFps], xreal: ["1920x1200", gfn ? 60 : 90], tv: ["3840x2160", 60] };
    const [resolution, fps] = values[value] ?? [(gfn ? s.geforceDisplay : s.display).profile.resolution, (gfn ? s.geforceDisplay : s.display).profile.fps];
    setProfile(p => ({ ...p, resolution, fps, bitrate: gfn ? p.bitrate : 80000 }));
  };
  return <div ref={root} className="crete-launch-menu">
    {!loading && <button ref={anchor} type="button" className="crete-details-manage" aria-label={text("Launch options")} aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen(!open)}><IoChevronDownOutline /></button>}
    {open && !loading && <div className="crete-launch-popover" role="menu" aria-label={text("Launch options")}>
      {s.provider !== "ps5RemotePlay" && s.provider !== "playstationCloud" && row(localLabel, "local", "#c9482a", local, !localEnabled)}
      {(["ps5RemotePlay", "playstationCloud"] as AsobiProvider[]).filter(provider => s.asobiGames.some(game => game.provider === provider)).map(provider => row(asobiLabel(provider), provider, "#368bea", () => s.setAsobiConfigure(provider), false, provider))}
      {s.provider === "luna" && row("Amazon Luna Wi-Fi", "", "#b49de5", () => setWifiOnly(true))}
      {copies.map(copy => row(copy.label, "copy", "#a7a29a", copy.onClick, false, copy.key))}
      {s.pcGame && row("Moonlight", "moonlight", "#697580", () => s.launch("moonlight"), !s.clients.moonlight)}
      {s.gameUrl && row("GeForce NOW", "geforceNow", "#7cab00", () => s.launch("geforceNow", true))}
      {!s.gameUrl && s.clients.geforceNow && row("GeForce NOW library", "", "#7cab00", () => s.launch("geforceNow"))}
      <button type="button" role="menuitem" onClick={() => choose(manage)}>{text(manageLabel)}</button>
    </div>}
    {createPortal(<div ref={panelRoot} data-theme="crete" className="crete-launch-layer" onPointerDownCapture={event => {
      const select = event.target instanceof Element ? event.target.closest("select") : null;
      if (select instanceof HTMLSelectElement && !select.disabled) { event.preventDefault(); openChoices(select); }
    }}>
    {ps && <div className="crete-stream-backdrop" onClick={event => { if (event.target === event.currentTarget) close(); }}><section role="dialog" aria-modal="true" aria-label={`${asobiLabel(ps)} settings`} className="crete-stream-settings crete-stream-dialog">
      <header className="crete-stream-heading"><span className="crete-stream-eyebrow">PLAYSTATION</span><h2>{asobiLabel(ps)}</h2><p>{ps === "ps5RemotePlay" ? "Your PS5, on this screen." : "Your PlayStation cloud library."}</p></header>
      <div className="crete-stream-body">
        <HomeWifiSelect provider={ps} saving={setBusy} />
        <label>Game shortcut<select aria-label="ASOBI shortcut" value={psTarget} onChange={event => setPsTarget(event.target.value)}>{psGames.map(game => <option key={game.id} value={game.id}>{game.label}</option>)}</select></label>
        <p>Uses your saved ASOBI picture and controller settings.</p>
        <button className="crete-stream-text-action" type="button" disabled={busy} onClick={() => { close(); void invoke("open_asobi_client").catch(() => {}); }}>Open ASOBI settings</button>
        {s.asobiError && <p role="alert">{s.asobiError}</p>}
        {!psGames.length && <p role="status">Add this game's shortcut in ASOBI, restart Steam, then refresh your library.</p>}
      </div>
      <footer className="crete-stream-buttons"><button type="button" onClick={close}>{text("Cancel")}</button><button data-launch-primary type="button" disabled={busy || !psTarget || !s.online} onClick={() => { setBusy(true); void s.startAsobi(psTarget).finally(() => setBusy(false)); }}>{text(busy ? "Connecting…" : "Play")}</button></footer>
    </section></div>}
    {(s.configure || gfn || wifiOnly) && <div className="crete-stream-backdrop" onClick={event => { if (event.target === event.currentTarget) close(); }}><section role="dialog" aria-modal="true" aria-label={text(wifiOnly ? "Amazon Luna Wi-Fi" : gfn ? "GeForce NOW stream settings" : "Moonlight stream settings")} className="crete-stream-settings crete-stream-dialog">
      <header className="crete-stream-heading"><span className="crete-stream-eyebrow">{wifiOnly || gfn ? "CLOUD STREAMING" : "PC STREAMING"}</span><h2>{wifiOnly ? "Amazon Luna" : gfn ? "GeForce NOW" : "Moonlight"}</h2><p>{wifiOnly ? "Choose your connection." : gfn ? "Stream to this display." : profile.host}</p></header>
      <div className="crete-stream-body">
        <HomeWifiSelect provider={wifiOnly ? "luna" : gfn ? "geforceNow" : "moonlight"} saving={setBusy} />
        {!wifiOnly && <>
          <label>{text("Display preset")}<select aria-label={text("Display preset")} value={displayPreset} onChange={e => preset(e.target.value)}><option value="auto">Saved for this display</option><option value="native">{nativeLabel} · {nativeResolution.split("x")[1]}p / {nativeFps}fps</option><option value="xreal">XREAL · 1200p / {gfn ? 60 : 90}fps</option><option value="tv">TV · 4K / 60fps</option><option value="custom" disabled>Custom</option></select></label>
          <p className="crete-stream-summary">{profile.resolution.replace("x", " × ")}<span>·</span>{profile.fps} fps<span>·</span>{profile.bitrate ? `${profile.bitrate / 1000} Mbps` : "Auto bitrate"}</p>
          {!gfn && target === "MoonDeckStream" && s.steamId && !s.buddy?.paired && <div className="crete-stream-notice"><p>{text("MoonDeck Buddy needs a separate pairing for CreteOS.")}</p><button type="button" onClick={() => { const bytes = crypto.getRandomValues(new Uint32Array(1)); const value = 1000 + bytes[0] % 9000; setPin(value); void s.pair(value); }}>{text("Pair MoonDeck Buddy")}</button>{pin && <p>{text("Enter PIN on Bosgame and choose Refresh host", { pin })}</p>}{s.buddyError && <p role="status">{s.buddyError}</p>}</div>}
          {!gfn && !s.hostInstalled && target === "MoonDeckStream" && s.steamId && <p role="status">{s.inventoryError || (s.checking ? "Checking this game on the host…" : "This game is not installed on the host. Choose another host app in Stream options.")}</p>}
          <button className="crete-stream-disclosure" type="button" aria-expanded={advanced} onClick={() => setAdvanced(value => !value)}><span>Stream options</span><IoChevronDownOutline /></button>
          {advanced && <div className="crete-stream-fields" onChange={() => setDisplayPreset("custom")}>
            <label>{text("Resolution")}<select aria-label={text("Resolution")} value={profile.resolution} onChange={e => setProfile(p => ({ ...p, resolution: e.target.value }))}>{["1280x720", "1920x1080", "1920x1200", "2560x1440", "2560x1600", "3840x2160"].map(value => <option key={value}>{value}</option>)}</select></label>
            <label>{text("Frame rate")}<select aria-label={text("Frame rate")} value={profile.fps} onChange={e => setProfile(p => ({ ...p, fps: Number(e.target.value) }))}>{(gfn ? [30, 60, 120, 240] : [30, 60, 90, 120]).map(value => <option key={value} value={value}>{value}fps</option>)}</select></label>
            <label>{text("Bitrate")}<select aria-label={text("Bitrate")} value={profile.bitrate} onChange={e => setProfile(p => ({ ...p, bitrate: Number(e.target.value) }))}>{gfn && <option value={0}>{text("Auto")}</option>}{Array.from({ length: gfn ? 20 : 30 }, (_, i) => (i + 1) * 5000).map(value => <option key={value} value={value}>{value / 1000}Mbps</option>)}</select></label>
            {!gfn && <label>{text("Codec")}<select aria-label={text("Codec")} value={profile.codec} onChange={e => setProfile(p => ({ ...p, codec: e.target.value }))}><option value="auto">{text("Automatic")}</option><option value="H.264">H.264</option><option value="HEVC">H.265</option><option value="AV1">AV1</option></select></label>}
            {!gfn && <label className="crete-stream-field-wide">{text("Host app")}<select aria-label={text("Host app")} value={target} onChange={e => setTarget(e.target.value)}>{s.apps.map(name => <option key={name}>{name}</option>)}</select></label>}
            <p className="crete-stream-field-wide">{gfn ? "Quality depends on your NVIDIA membership. Codec is managed in GeForce NOW." : "Saved for this display when you start. Codec support depends on the host and device."}</p>
          </div>}
          <button className="crete-stream-disclosure" type="button" aria-expanded={tools} onClick={() => setTools(value => !value)}><span>{gfn ? "About these settings" : "Host & connection tools"}</span><IoChevronDownOutline /></button>
          {tools && <div className="crete-stream-tools">{gfn ? <p>CreteOS can restart an idle NVIDIA client to apply settings. Active or unconfirmed sessions stay open.</p> : <><p>{s.buddy?.paired ? "MoonDeck Buddy paired." : "MoonDeck Buddy is not paired."} Host availability is checked again when you start.</p><div><button type="button" disabled={busy || s.refreshing || !s.online} onClick={s.refresh}>{text(s.refreshing ? "Checking…" : "Refresh host")}</button><button type="button" onClick={() => void invoke("launch_streaming_client", { provider: "moonlight" }).catch(() => {})}>{text("Open Moonlight")}</button></div></>}</div>}
          {!gfn && s.hostError && <p role="status">{text(s.hostError)}</p>}{s.error && <p role="alert">{s.error}</p>}
          {s.error && s.localInstalled && localEnabled && <button type="button" onClick={() => { close(); local(); }}>{text("Play locally")}</button>}
        </>}
        {!s.online && <p role="status">{text("No network connection. Installed local games are available offline.")}</p>}
      </div>
      <footer className="crete-stream-buttons"><button type="button" onClick={close}>{text(wifiOnly ? "Done" : "Cancel")}</button>{!wifiOnly && <button data-launch-primary type="button" disabled={busy || !s.online || (!gfn && (!target || s.checking || (target === "MoonDeckStream" && !!s.steamId && (!s.buddy?.paired || !s.hostInstalled))))} onClick={() => { setBusy(true); void (gfn ? s.geforceStream(profile) : s.stream(target, profile)).finally(() => setBusy(false)); }}>{text(busy ? "Connecting…" : gfn ? "Play" : "Start stream")}</button>}</footer>
    </section></div>}
    {choices && <div className="crete-stream-choice-backdrop" onClick={event => { if (event.target === event.currentTarget) closeChoices(); }}><section className="crete-stream-choice-panel" aria-label={choices.getAttribute("aria-label") ?? "Options"}><header><span className="crete-stream-eyebrow">CHOOSE AN OPTION</span><h2>{choices.getAttribute("aria-label") ?? "Options"}</h2></header><div data-stream-choices role="listbox" aria-label={choices.getAttribute("aria-label") ?? "Options"} className="crete-stream-choices">{Array.from(choices.options).map(option => <button type="button" role="option" aria-selected={option.selected} disabled={option.disabled} key={option.value} onClick={() => { choices.value = option.value; choices.dispatchEvent(new Event("change", { bubbles: true })); closeChoices(); }}><span>{option.text}</span><span aria-hidden="true">{option.selected ? "✓" : ""}</span></button>)}</div><footer><button type="button" onClick={closeChoices}>Back</button></footer></section></div>}
    </div>, document.body)}
  </div>;
}

import { useEffect, useRef, useState } from "react";
import { IoChevronDownOutline } from "react-icons/io5";
import { useCreteText } from "./text";
import { invoke } from "@tauri-apps/api/core";
import { getBestGamepad, readGpState } from "../utils/gamepad";
import type { useStreaming, MoonlightProfile } from "./useStreaming";

type Streaming = ReturnType<typeof useStreaming>;
export function LaunchMenu({ streaming: s, open, setOpen, local, localLabel, localEnabled, manage, manageLabel, block }: {
  streaming: Streaming; open: boolean; setOpen: (open: boolean) => void; local: () => void;
  localLabel: string; localEnabled: boolean; manage: () => void; manageLabel: string; block: (blocked: boolean) => void;
}) {
  const text = useCreteText();
  const root = useRef<HTMLDivElement>(null);
  const anchor = useRef<HTMLButtonElement>(null);
  const [profile, setProfile] = useState<MoonlightProfile>(s.display.profile);
  const [target, setTarget] = useState("");
  const [busy, setBusy] = useState(false);
  const [pin, setPin] = useState<number | null>(null);
  const active = open || s.configure;
  useEffect(() => { block(active); return () => block(false); }, [active, block]);
  useEffect(() => {
    if (!s.configure) return;
    setOpen(false); setProfile(s.display.profile);
  }, [s.configure]);
  useEffect(() => {
    if (!s.configure) return;
    setTarget(s.moonlightGame || (s.steamId && s.apps.includes("MoonDeckStream") ? "MoonDeckStream" : undefined) || s.apps.find(name => name === "Steam Big Picture") || s.apps[0] || "");
  }, [s.configure, s.apps, s.moonlightGame]);
  const close = () => { setOpen(false); s.setConfigure(false); anchor.current?.focus(); };
  useEffect(() => {
    if (!active) return;
    const controls = () => Array.from(root.current?.querySelectorAll<HTMLButtonElement | HTMLSelectElement>('button:not([disabled]), select:not([disabled])') ?? []).filter(el => el !== anchor.current);
    const timer = window.setTimeout(() => controls()[0]?.focus(), 0);
    const move = (direction: number) => {
      const list = controls(); const index = list.indexOf(document.activeElement as HTMLButtonElement);
      list[(index + direction + list.length) % list.length]?.focus();
    };
    const key = (event: KeyboardEvent) => {
      if (event.key === "Escape") { event.preventDefault(); event.stopImmediatePropagation(); close(); }
      else if (["ArrowUp", "ArrowDown"].includes(event.key) && !(event.target instanceof HTMLSelectElement)) {
        event.preventDefault(); event.stopImmediatePropagation(); move(event.key === "ArrowUp" ? -1 : 1);
      }
      else if (event.key === "Tab") { event.preventDefault(); event.stopImmediatePropagation(); move(event.shiftKey ? -1 : 1); }
    };
    const outside = (event: PointerEvent) => { if (!root.current?.contains(event.target as Node)) close(); };
    window.addEventListener("keydown", key, true); window.addEventListener("pointerdown", outside);
    let frame = 0; let last: ReturnType<typeof readGpState> | undefined;
    const poll = () => {
      const gp = getBestGamepad();
      if (gp) {
        const state = readGpState(gp);
        if (last) {
          if (state.Escape && !last.Escape) close();
          else if (state.ArrowDown && !last.ArrowDown) move(1);
          else if (state.ArrowUp && !last.ArrowUp) move(-1);
          else if (state.Enter && !last.Enter && document.activeElement instanceof HTMLButtonElement) document.activeElement.click();
          else if ((state.ArrowRight && !last.ArrowRight || state.ArrowLeft && !last.ArrowLeft) && document.activeElement instanceof HTMLSelectElement) {
            const select = document.activeElement;
            select.selectedIndex = Math.max(0, Math.min(select.options.length - 1, select.selectedIndex + (state.ArrowRight ? 1 : -1)));
            select.dispatchEvent(new Event("change", { bubbles: true }));
          }
        }
        last = state;
      }
      frame = requestAnimationFrame(poll);
    };
    frame = requestAnimationFrame(poll);
    return () => { clearTimeout(timer); cancelAnimationFrame(frame); window.removeEventListener("keydown", key, true); window.removeEventListener("pointerdown", outside); };
  }, [active]);
  const choose = (action: () => void) => { setOpen(false); action(); };
  const row = (label: string, provider: string, color: string, action: () => void, disabled = false) => <button type="button" role="menuitem" disabled={disabled} onClick={() => choose(action)}><i style={{ background: color }} />{text(label)}{s.provider === provider && <small aria-hidden="true">{text("DEFAULT")}</small>}</button>;
  const preset = (value: string) => {
    const values: Record<string, [string, number]> = { native: ["1920x1080", 120], xreal: ["1920x1200", 90], tv: ["3840x2160", 60] };
    const [resolution, fps] = values[value] ?? [s.display.profile.resolution, s.display.profile.fps];
    setProfile(p => ({ ...p, resolution, fps, bitrate: 80000 }));
  };
  return <div ref={root} className="crete-launch-menu">
    <button ref={anchor} type="button" className="crete-details-manage" aria-label={text("Launch options")} aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen(!open)}><IoChevronDownOutline /></button>
    {open && <div className="crete-launch-popover" role="menu" aria-label={text("Launch options")}>
      {row(localLabel, "local", "#83d4a0", local, !localEnabled)}
      {s.pcGame && row("Moonlight", "moonlight", "#7164d9", () => s.launch("moonlight"), !s.clients.moonlight)}
      {s.gameUrl && row("GeForce NOW", "geforceNow", "#8cce00", () => s.launch("geforceNow", true))}
      {!s.gameUrl && s.clients.geforceNow && row("GeForce NOW library", "", "#8cce00", () => s.launch("geforceNow"))}
      <button type="button" role="menuitem" onClick={() => choose(manage)}>{text(manageLabel)}</button>
    </div>}
    {s.configure && <div className="crete-stream-backdrop"><div role="dialog" aria-modal="true" aria-label={text("Moonlight stream settings")} className="crete-stream-settings">
      <h2>{text("Play on")} {profile.host}</h2>
      <p>{text(target === "MoonDeckStream" && s.steamId ? s.hostInstalled ? "Installed on Bosgame. Launch this game directly through MoonDeck Buddy." : s.hostGames ? "This game is not installed on Bosgame. Choose another host app, local install or GeForce NOW." : "Checking whether this game is installed on Bosgame…" : s.moonlightGame ? "Game available on the host." : "Choose a host app. Its advertised apps do not confirm this game is installed.")}</p>
      {s.inventoryError && <p role="status">{s.inventoryError}</p>}
      {target === "MoonDeckStream" && s.steamId && <div className="crete-buddy-pair">
        {s.buddy?.paired ? <p>{text("CreteOS is paired with MoonDeck Buddy.")}</p> : <><p>{text("MoonDeck Buddy needs a separate pairing for CreteOS.")}</p><button type="button" onClick={() => { const bytes = crypto.getRandomValues(new Uint32Array(1)); const value = 1000 + bytes[0] % 9000; setPin(value); void s.pair(value); }}>{text("Pair MoonDeck Buddy")}</button>{pin && <p>{text("Enter PIN on Bosgame and choose Refresh host", { pin })}</p>}{s.buddyError && <p role="status">{s.buddyError}</p>}</>}
      </div>}
      <label>{text("Host app")}<select aria-label={text("Host app")} value={target} onChange={e => setTarget(e.target.value)}>{s.apps.map(name => <option key={name}>{name}</option>)}</select></label>
      <label>{text("Display preset")}<select aria-label={text("Display preset")} defaultValue="auto" onChange={e => preset(e.target.value)}><option value="auto">{text("Current display")}</option><option value="native">GPD · 1080p / 120fps</option><option value="xreal">XREAL · 1200p / 90fps</option><option value="tv">TV · 4K / 60fps</option></select></label>
      <label>{text("Resolution")}<select aria-label={text("Resolution")} value={profile.resolution} onChange={e => setProfile(p => ({ ...p, resolution: e.target.value }))}>{["1280x720", "1920x1080", "1920x1200", "2560x1440", "2560x1600", "3840x2160"].map(value => <option key={value}>{value}</option>)}</select></label>
      <label>{text("Frame rate")}<select aria-label={text("Frame rate")} value={profile.fps} onChange={e => setProfile(p => ({ ...p, fps: Number(e.target.value) }))}>{[30, 60, 90, 120].map(value => <option key={value} value={value}>{value}fps</option>)}</select></label>
      <label>{text("Bitrate")}<select aria-label={text("Bitrate")} value={profile.bitrate} onChange={e => setProfile(p => ({ ...p, bitrate: Number(e.target.value) }))}>{Array.from({ length: 30 }, (_, i) => (i + 1) * 5000).map(value => <option key={value} value={value}>{value / 1000}Mbps</option>)}</select></label>
      <label>{text("Codec")}<select aria-label={text("Codec")} value={profile.codec} onChange={e => setProfile(p => ({ ...p, codec: e.target.value }))}><option value="auto">{text("Automatic")}</option><option value="H.264">H.264</option><option value="HEVC">H.265</option><option value="AV1">AV1</option></select></label>
      {s.hostError && <p role="status">{s.hostError}</p>}{s.error && <p role="alert">{s.error}</p>}
      <div className="crete-stream-buttons"><button type="button" disabled={busy || !target || s.checking || (target === "MoonDeckStream" && !!s.steamId && (!s.buddy?.paired || !s.hostInstalled))} onClick={() => { setBusy(true); void s.stream(target, profile).finally(() => setBusy(false)); }}>{text(busy ? "Connecting…" : "Start stream")}</button><button type="button" disabled={busy} onClick={s.refresh}>{text(s.checking ? "Checking…" : "Refresh host")}</button><button type="button" onClick={() => void invoke("launch_streaming_client", { provider: "moonlight" }).catch(() => {} )}>{text("Open Moonlight")}</button><button type="button" onClick={close}>{text("Cancel")}</button></div>
      <p>{text("Settings are saved for this display when the stream starts. Codec availability depends on the host and device.")}</p>
    </div></div>}
  </div>;
}

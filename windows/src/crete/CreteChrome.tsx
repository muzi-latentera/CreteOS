import { useEffect, useState, type ReactNode } from "react";
import { IoHome, IoSettingsOutline, IoFlash, IoWifi, IoOptionsOutline } from "react-icons/io5";
import { CreteMark } from "../components/CreteMark";
import { useSettings } from "../contexts/SettingsContext";
import { useCreteText } from "./text";

export function CreteStatus({ time, date, hasBattery, battery, charging }: { time: string; date?: string; hasBattery: boolean; battery: number; charging: boolean }) {
  const { settings } = useSettings(); const text = useCreteText();
  const [online, setOnline] = useState(navigator.onLine);
  useEffect(() => {
    const update = () => setOnline(navigator.onLine);
    window.addEventListener("online", update); window.addEventListener("offline", update);
    return () => { window.removeEventListener("online", update); window.removeEventListener("offline", update); };
  }, []);
  return <div className="crete-status" aria-label={text("System status")} style={settings.topbar_background === false ? { background: "transparent", borderColor: "transparent" } : undefined}>
    {settings.show_date && date && <span>{date}</span>}{settings.show_clock !== false && <time>{time}</time>}<span className="crete-status-divider" />
    <span title={text(online ? "Network available" : "Offline")} aria-label={text(online ? "Network available" : "Offline")} style={{ opacity: online ? 1 : .35 }}><IoWifi /></span>
    {hasBattery && settings.show_battery !== false && <span>{charging && <IoFlash aria-label={text("Charging")} />}{Math.round(battery)}%</span>}
  </div>;
}

export function CreteHeader({ tab, time, date, hasBattery, battery, charging, sections }: { tab: string; time: string; date?: string; hasBattery: boolean; battery: number; charging: boolean; sections?: ReactNode }) {
  return <header className="crete-header"><span className="crete-header-mark" aria-label="CreteOS"><CreteMark size={26} /></span>
    <CreteStatus time={time} date={date} hasBattery={hasBattery} battery={battery} charging={charging} />
    {tab === "Settings" && <div className="crete-settings-sections">{sections}</div>}
  </header>;
}

export function CreteBottomNav({ tab, switchTab, onToggleTray, spotifyMiniBar }: { tab: string; switchTab: (tab: string) => void; onToggleTray: () => void; spotifyMiniBar?: ReactNode }) {
  const { settings } = useSettings(); const text = useCreteText();
  return <nav className="crete-bottom-nav" aria-label={text("Main navigation")} style={settings.bottombar_background === false ? { background: "transparent", borderColor: "transparent" } : undefined}>
    <button type="button" aria-label={text("Home")} aria-current={tab === "Home" ? "page" : undefined} onClick={() => switchTab("Home")}><IoHome /></button>
    <button type="button" aria-label={text("Library")} aria-current={tab === "Games" || tab === "Apps" ? "page" : undefined} onClick={() => switchTab("Games")}><svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2.4" aria-hidden="true"><path d="M3 3h18v18H3zM9 3v18M15 3v18" /></svg></button>
    {spotifyMiniBar && <div className="crete-spotify-mini">{spotifyMiniBar}</div>}
    <div className="crete-nav-spacer" />
    <button type="button" className="crete-helper-button" aria-label={text("Open helper tray")} title={text("Quick controls · Menu")} onClick={onToggleTray}><IoOptionsOutline /></button>
    <button type="button" aria-label={text("Settings")} aria-current={tab === "Settings" ? "page" : undefined} onClick={() => switchTab("Settings")}><IoSettingsOutline /></button>
  </nav>;
}

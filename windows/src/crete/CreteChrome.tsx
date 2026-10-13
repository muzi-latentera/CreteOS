import { useEffect, useState, type ReactNode } from "react";
import { IoHome, IoSettingsOutline, IoFlash, IoWifi, IoOptionsOutline } from "react-icons/io5";
import { CreteMark } from "../components/CreteMark";

export function CreteStatus({ time, hasBattery, battery, charging }: { time: string; hasBattery: boolean; battery: number; charging: boolean }) {
  const [online, setOnline] = useState(navigator.onLine);
  useEffect(() => {
    const update = () => setOnline(navigator.onLine);
    window.addEventListener("online", update); window.addEventListener("offline", update);
    return () => { window.removeEventListener("online", update); window.removeEventListener("offline", update); };
  }, []);
  return <div className="crete-status" aria-label="System status"><time>{time}</time><span className="crete-status-divider" />
    <span title={online ? "Network available" : "Offline"} aria-label={online ? "Network available" : "Offline"} style={{ opacity: online ? 1 : .35 }}><IoWifi /></span>
    {hasBattery && <span>{charging && <IoFlash aria-label="Charging" />}{Math.round(battery)}%</span>}
  </div>;
}

export function CreteHeader({ tab, time, hasBattery, battery, charging, sections }: { tab: string; time: string; hasBattery: boolean; battery: number; charging: boolean; sections?: ReactNode }) {
  return <header className="crete-header"><span className="crete-header-mark" aria-label="CreteOS"><CreteMark size={26} /></span>
    <CreteStatus time={time} hasBattery={hasBattery} battery={battery} charging={charging} />
    {tab === "Settings" && <div className="crete-settings-sections">{sections}</div>}
  </header>;
}

export function CreteBottomNav({ tab, switchTab, onToggleTray }: { tab: string; switchTab: (tab: string) => void; onToggleTray: () => void }) {
  return <nav className="crete-bottom-nav" aria-label="Main navigation">
    <button type="button" aria-label="Home" aria-current={tab === "Home" ? "page" : undefined} onClick={() => switchTab("Home")}><IoHome /></button>
    <button type="button" aria-label="Library" aria-current={tab === "Games" || tab === "Apps" ? "page" : undefined} onClick={() => switchTab("Games")}><svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2.4" aria-hidden="true"><path d="M3 3h18v18H3zM9 3v18M15 3v18" /></svg></button>
    <div className="crete-nav-spacer" />
    <button type="button" className="crete-helper-button" aria-label="Open helper tray" title="Quick controls · Menu" onClick={onToggleTray}><IoOptionsOutline /></button>
    <button type="button" aria-label="Settings" aria-current={tab === "Settings" ? "page" : undefined} onClick={() => switchTab("Settings")}><IoSettingsOutline /></button>
  </nav>;
}

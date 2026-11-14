import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { PerformanceSettingsPanel } from "./PerformanceSettingsPanel";
import { SavesSettingsPanel } from "./SavesSettingsPanel";
import "./launchMenus.css";

/** Settings panels opened by `crete-open-panel` events (see the "open_*" settings actions). */
export function CretePanels() {
  const [panel, setPanel] = useState<string | null>(null);
  useEffect(() => {
    const open = (event: Event) => setPanel(String((event as CustomEvent).detail ?? ""));
    window.addEventListener("crete-open-panel", open);
    return () => window.removeEventListener("crete-open-panel", open);
  }, []);
  if (!panel) return null;
  const close = () => setPanel(null);
  const body = panel === "open_performance" ? <PerformanceSettingsPanel onClose={close} />
    : panel === "open_saves" ? <SavesSettingsPanel onClose={close} /> : null;
  return body ? createPortal(body, document.body) : null;
}

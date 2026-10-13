import { useDeferredValue, useEffect, useMemo, useRef, useState, type RefObject } from "react";
import { IoLayersOutline, IoSearchOutline, IoFilterOutline, IoClose } from "react-icons/io5";
import type { App } from "../types";
import { CreteGameCard } from "./CreteGameCard";
import { AVAILABILITY, AVAILABILITY_LABELS, matchesAvailability, matchesSearch, type Availability } from "./model";
import { useViewNavigation, type NavigationRef } from "./navigation";

export interface CreteLibraryProps {
  tab: "Games" | "Apps";
  active?: boolean;
  filteredApps: App[];
  customArt: Record<string, string>;
  gameArt: Record<string, string>;
  pins: string[];
  isRunning: (id: string) => boolean;
  openDetails: (app: App) => void;
  scrollRef: RefObject<HTMLDivElement>;
  navigationRef: NavigationRef;
  switchTab: (tab: string) => void;
  onSourceSelect: (index: number) => void;
  gameSourceTabs: string[];
  gameSourceTab: string;
  gamesSort: string;
  setGamesSort: (sort: string) => void;
  onSearch: () => void;
}

export function CreteLibraryView(p: CreteLibraryProps) {
  const [availability, setAvailability] = useState<Availability>("all");
  const [query, setQuery] = useState("");
  const deferredQuery = useDeferredValue(query);
  const [searching, setSearching] = useState(false);
  const [popup, setPopup] = useState<"sources" | "sort" | null>(null);
  const [popupIndex, setPopupIndex] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [toolbarIndex, setToolbarIndex] = useState<number | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const gridRef = useRef<HTMLDivElement>(null);
  const [cols, setCols] = useState(5);
  const appsMode = p.tab === "Apps";
  const items = useMemo(() => p.filteredApps.filter(app => (appsMode || matchesAvailability(app, availability)) && matchesSearch(app.name, deferredQuery)), [p.filteredApps, availability, deferredQuery, appsMode]);
  const index = Math.max(0, items.findIndex(app => app.id === selectedId));
  const selected = items[index];
  const filters = appsMode ? ["Applications"] : AVAILABILITY_LABELS;
  const choices = popup === "sort" ? ["Recently played", "A–Z", "Store"] : [...p.gameSourceTabs, appsMode ? "Game Library" : "Applications"];
  const filterCount = filters.length;
  const choose = (i: number) => {
    if (popup === "sort") p.setGamesSort(["recent", "az", "store"][i]);
    else if (i === p.gameSourceTabs.length) p.switchTab(appsMode ? "Games" : "Apps");
    else { if (appsMode) p.switchTab("Games"); p.onSourceSelect(i); }
    setPopup(null); setToolbarIndex(null); setSelectedId(null);
  };
  const showPopup = (kind: "sources" | "sort") => { setPopup(popup === kind ? null : kind); setPopupIndex(0); };
  const activateToolbar = (i: number) => {
    if (i < filterCount) { if (!appsMode) setAvailability(AVAILABILITY[i]); setSelectedId(null); }
    else if (i === filterCount) showPopup("sources");
    else if (i === filterCount + 1) setSearching(value => !value);
    else showPopup("sort");
  };

  useViewNavigation(!!p.active, p.navigationRef, key => {
    if (popup) {
      if (key === "Escape") setPopup(null);
      else if (["ArrowDown", "ArrowRight", "ArrowUp", "ArrowLeft"].includes(key)) setPopupIndex(i => (i + (key === "ArrowDown" || key === "ArrowRight" ? 1 : -1) + choices.length) % choices.length);
      else if (key === "Enter") choose(popupIndex);
      return true;
    }
    if (key === "Escape" && searching) { setSearching(false); setQuery(""); return true; }
    if (key === "ButtonX") { showPopup("sources"); return true; }
    if (key === "ButtonY") { p.onSearch(); return true; }
    if ((key === "TriggerLeft" || key === "TriggerRight") && !appsMode) {
      const i = AVAILABILITY.indexOf(availability);
      setAvailability(AVAILABILITY[(i + (key === "TriggerRight" ? 1 : -1) + AVAILABILITY.length) % AVAILABILITY.length]);
      setSelectedId(null); return true;
    }
    if (toolbarIndex !== null) {
      if (key === "ArrowDown") setToolbarIndex(null);
      else if (key === "ArrowLeft" || key === "ArrowRight") setToolbarIndex(i => Math.max(0, Math.min(filterCount + 2, (i ?? 0) + (key === "ArrowRight" ? 1 : -1))));
      else if (key === "Enter") activateToolbar(toolbarIndex);
      else return key === "ArrowUp";
      return true;
    }
    if (key === "Enter") { if (selected) p.openDetails(selected); else setToolbarIndex(0); return true; }
    if (key === "ArrowUp" && index < cols) { setToolbarIndex(0); return true; }
    const delta = ({ ArrowLeft: -1, ArrowRight: 1, ArrowUp: -cols, ArrowDown: cols } as Record<string, number>)[key];
    if (delta != null) { const next = items[Math.max(0, Math.min(items.length - 1, index + delta))]; if (next) setSelectedId(next.id); return true; }
    return false;
  });

  useEffect(() => {
    const grid = gridRef.current;
    if (!grid) return;
    const observer = new ResizeObserver(() => setCols(getComputedStyle(grid).gridTemplateColumns.split(" ").filter(Boolean).length));
    observer.observe(grid);
    return () => observer.disconnect();
  }, []);
  useEffect(() => { if (searching && p.active) inputRef.current?.focus(); }, [searching, p.active]);
  useEffect(() => {
    if (!p.active || toolbarIndex !== null) return;
    gridRef.current?.querySelector('[data-selected="true"]')?.scrollIntoView({ block: "nearest", inline: "nearest", behavior: "auto" });
  }, [p.active, selected?.id, toolbarIndex]);

  return <div className="crete-library crete-scroller" ref={p.scrollRef}>
    <div className="crete-library-toolbar" aria-label="Library filters">
      <div className="crete-filters">{filters.map((label, i) => <button key={label} type="button"
        className="crete-filter" data-nav-focused={toolbarIndex === i} aria-pressed={appsMode || AVAILABILITY[i] === availability}
        onClick={() => activateToolbar(i)}>{label}</button>)}</div>
      <div className="crete-library-actions"><span className="crete-count" aria-live="polite">{items.length} {appsMode ? "apps" : "games"}</span>
        <button type="button" className="crete-tool" aria-expanded={popup === "sources"} data-nav-focused={toolbarIndex === filterCount} onClick={() => showPopup("sources")}><IoLayersOutline /><span>{p.gameSourceTab === "All" ? "Sources" : p.gameSourceTab}</span></button>
        <button type="button" className="crete-tool" aria-label="Search games" aria-expanded={searching} data-nav-focused={toolbarIndex === filterCount + 1} onClick={() => setSearching(value => !value)}><IoSearchOutline /></button>
        <button type="button" className="crete-tool" aria-label="Sort games" aria-expanded={popup === "sort"} data-nav-focused={toolbarIndex === filterCount + 2} onClick={() => showPopup("sort")}><IoFilterOutline /></button>
      </div>
    </div>
    {searching && <div className="crete-search"><input ref={inputRef} data-crete-text-input aria-label="Search games" placeholder="Search games…" value={query} onChange={event => { setQuery(event.target.value); setSelectedId(null); }} onKeyDown={event => { if (event.key === "Escape") { setSearching(false); setQuery(""); event.stopPropagation(); } }} />
      <button type="button" aria-label="Close search" onClick={() => { setSearching(false); setQuery(""); }}><IoClose /></button></div>}
    {popup && <div className="crete-picker" role="group" aria-label={popup === "sort" ? "Sort order" : "Library sources"}>
      {choices.map((label, i) => <button type="button" key={`${i}-${label}`} data-nav-focused={popupIndex === i}
        aria-pressed={popup === "sort" ? ["recent", "az", "store"][i] === p.gamesSort : label === p.gameSourceTab}
        onClick={() => choose(i)}>{label}</button>)}
      <button type="button" className="crete-picker-close" onClick={() => setPopup(null)}>Close</button>
    </div>}
    <div className="crete-library-grid" ref={gridRef}>{items.map(app => <CreteGameCard key={app.id} app={app}
      art={p.customArt[app.id] || p.gameArt[app.id]} selected={toolbarIndex === null && selected?.id === app.id}
      pinned={p.pins.includes(app.id)} running={p.isRunning(app.id)} onSelect={() => { setSelectedId(app.id); setToolbarIndex(null); }} onOpen={() => p.openDetails(app)} />)}</div>
    {!items.length && <div className="crete-empty"><h2>No {appsMode ? "applications" : "games"} found</h2><p>{query ? "Try another search." : "Choose another filter or add a source in Settings."}</p></div>}
  </div>;
}

import { useDeferredValue, useEffect, useMemo, useRef, useState, type RefObject, type CSSProperties, type ComponentType } from "react";
import { IoLayersOutline, IoSearchOutline, IoFilterOutline, IoClose } from "react-icons/io5";
import type { App, Settings } from "../types";
import { CreteGameCard } from "./CreteGameCard";
import { AVAILABILITY, AVAILABILITY_LABELS, matchesAvailability, matchesSearch, type Availability } from "./model";
import { useNavigationState, useViewNavigation, type NavigationRef } from "./navigation";
import { useCreteText } from "./text";
import { StoreBadge } from "../components/ui/StoreBadge";
import { cancelPaneScroll, scrollPane } from "./smoothScroll";
import { gameCopies } from "./libraryEditions";

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
  togglePin: (app: App) => void;
  onManageLibrary: () => void;
  setContextMenu: (menu: { x: number; y: number; app: App }) => void;
  settings: Settings;
  AppIcon?: ComponentType<{ app: App; size: number }>;
}

export function CreteLibraryView(p: CreteLibraryProps) {
  const text = useCreteText();
  const [availability, setAvailability] = useState<Availability>("all");
  const [query, setQuery] = useState("");
  const deferredQuery = useDeferredValue(query);
  const [searching, setSearching, searchingRef] = useNavigationState(false, `${p.tab}.searching`);
  const [popup, setPopup, popupRef] = useNavigationState<"sources" | "sort" | null>(null, `${p.tab}.popup`);
  const [popupIndex, setPopupIndex, popupIndexRef] = useNavigationState(0, `${p.tab}.popupIndex`);
  const [selectedId, setSelectedId, selectedIdRef] = useNavigationState<string | null>(null, `${p.tab}.selectedId`);
  const [toolbarIndex, setToolbarIndex, toolbarIndexRef] = useNavigationState<number | null>(null, `${p.tab}.toolbarIndex`);
  const inputRef = useRef<HTMLInputElement>(null);
  const gridRef = useRef<HTMLDivElement>(null);
  const directScrollRef = useRef(false);
  const takeScroll = () => { directScrollRef.current = true; if (p.scrollRef.current) cancelPaneScroll(p.scrollRef.current); };
  const [cols, setCols] = useState(5);
  const [gridWidth, setGridWidth] = useState(0);
  const appsMode = p.tab === "Apps";
  const items = useMemo(() => p.filteredApps.filter(app => (appsMode || matchesAvailability(app, availability)) && matchesSearch(app.name, deferredQuery)), [p.filteredApps, availability, deferredQuery, appsMode]);
  const index = Math.max(0, items.findIndex(app => app.id === selectedId));
  const selected = items[index];
  const filters = (appsMode ? ["Applications"] : AVAILABILITY_LABELS).map(label => text(label));
  const choices = popup === "sort" ? ["Recently played", "A–Z", "Store"] : [...p.gameSourceTabs, appsMode ? "Game Library" : "Applications"];
  const filterCount = filters.length;
  const lastToolbarIndex = filterCount + (appsMode ? 2 : 3);
  const choose = (i: number) => {
    if (popupRef.current === "sort") p.setGamesSort(["recent", "az", "store"][i]);
    else if (i === p.gameSourceTabs.length) p.switchTab(appsMode ? "Games" : "Apps");
    else p.onSourceSelect(i);
    setPopup(null); setToolbarIndex(null); setSelectedId(null);
  };
  const showPopup = (kind: "sources" | "sort") => { setPopup(popupRef.current === kind ? null : kind); setPopupIndex(0); };
  const activateToolbar = (i: number) => {
    if (i < filterCount) { if (!appsMode) setAvailability(AVAILABILITY[i]); setSelectedId(null); }
    else if (i === filterCount) showPopup("sources");
    else if (i === filterCount + 1) setSearching(value => !value);
    else if (!appsMode && i === filterCount + 2) showPopup("sort");
    else p.onManageLibrary();
  };

  useViewNavigation(p.navigationRef, key => {
    directScrollRef.current = false;
    const popup = popupRef.current;
    const popupIndex = popupIndexRef.current;
    const searching = searchingRef.current;
    const toolbarIndex = toolbarIndexRef.current;
    const index = Math.max(0, items.findIndex(app => app.id === selectedIdRef.current));
    const selected = items[index];
    const choices = popup === "sort" ? ["Recently played", "A–Z", "Store"] : [...p.gameSourceTabs, appsMode ? "Game Library" : "Applications"];
    if (popup) {
      if (key === "Escape") setPopup(null);
      else if (["ArrowDown", "ArrowRight", "ArrowUp", "ArrowLeft"].includes(key)) setPopupIndex(i => (i + (key === "ArrowDown" || key === "ArrowRight" ? 1 : -1) + choices.length) % choices.length);
      else if (key === "Enter") choose(popupIndex);
      return true;
    }
    if (key === "Escape" && searching) { setSearching(false); setQuery(""); return true; }
    if (key === "ButtonX" && selected && toolbarIndex === null) { p.togglePin(selected); return true; }
    if (key === "Select") { p.onManageLibrary(); return true; }
    if (key === "ButtonY") { p.onSearch(); return true; }
    if ((key === "TriggerLeft" || key === "TriggerRight") && !appsMode) {
      const i = Math.max(0, p.gameSourceTabs.indexOf(p.gameSourceTab));
      p.onSourceSelect((i + (key === "TriggerRight" ? 1 : -1) + p.gameSourceTabs.length) % p.gameSourceTabs.length);
      setSelectedId(null); return true;
    }
    if (toolbarIndex !== null) {
      if (key === "ArrowDown") setToolbarIndex(null);
      else if (key === "ArrowLeft" || key === "ArrowRight") setToolbarIndex(i => Math.max(0, Math.min(lastToolbarIndex, (i ?? 0) + (key === "ArrowRight" ? 1 : -1))));
      else if (key === "Enter") activateToolbar(toolbarIndex);
      else return key === "ArrowUp";
      return true;
    }
    if (key === "Enter") { if (selected) p.openDetails(selected); else setToolbarIndex(0); return true; }
    if (key === "ArrowUp" && index < cols) { setToolbarIndex(0); return true; }
    const delta = ({ ArrowLeft: -1, ArrowRight: 1, ArrowUp: -cols, ArrowDown: cols } as Record<string, number>)[key];
    if (delta != null) { const next = items[Math.max(0, Math.min(items.length - 1, index + delta))]; if (next) setSelectedId(next.id); return true; }
    return false;
  }, p.tab);

  useEffect(() => {
    const grid = gridRef.current;
    if (!grid) return;
    const observer = new ResizeObserver(() => { setCols(getComputedStyle(grid).gridTemplateColumns.split(" ").filter(Boolean).length); setGridWidth(grid.clientWidth); });
    observer.observe(grid);
    return () => observer.disconnect();
  }, []);
  useEffect(() => { if (searching && p.active) inputRef.current?.focus(); }, [searching, p.active]);
  useEffect(() => {
    if (!p.active || directScrollRef.current) return;
    const pane = p.scrollRef.current;
    if (!pane) return;
    if (toolbarIndex !== null) { scrollPane(pane, { top: 0 }); return; }
    const target = gridRef.current?.querySelector('[data-selected="true"]');
    if (!target) return;
    const rect = pane.getBoundingClientRect(); const bounds = target.getBoundingClientRect();
    const scale = rect.height / pane.clientHeight || 1; const style = getComputedStyle(pane);
    const top = rect.top + (parseFloat(style.scrollPaddingTop) || 0) * scale;
    const bottom = rect.bottom - (parseFloat(style.scrollPaddingBottom) || 0) * scale;
    const delta = bounds.top < top ? bounds.top - top : bounds.bottom > bottom ? bounds.bottom - bottom : 0;
    if (delta) scrollPane(pane, { top: pane.scrollTop + delta / scale });
  }, [p.active, selected?.id, toolbarIndex]);

  const listMode = appsMode && p.settings.app_list_view;
  const scale = appsMode ? p.settings.app_cover_scale : p.settings.game_cover_scale;
  const wide = p.settings.wide_layout && (appsMode ? p.settings.wide_apps : p.settings.wide_games);
  const baseColumns = gridWidth >= 1500 ? 7 : gridWidth > 0 && gridWidth < 920 ? 4 : 5;
  const columns = Math.max(1, Math.round((baseColumns + (wide ? 2 : 0)) / (scale ?? 1)));
  return <div className="crete-library crete-scroller" ref={p.scrollRef}
    onPointerDownCapture={event => { if (event.pointerType === "touch") takeScroll(); else directScrollRef.current = false; }}
    onWheelCapture={takeScroll}>
    <div className="crete-library-toolbar" aria-label="Library filters">
      <div className="crete-filters">{filters.map((label, i) => <button key={label} type="button"
        className="crete-filter" data-nav-focused={toolbarIndex === i} aria-pressed={appsMode || AVAILABILITY[i] === availability}
        onClick={() => activateToolbar(i)}>{label}</button>)}</div>
      <div className="crete-library-actions"><span className="crete-count" aria-live="polite">{items.length} {text(appsMode ? "apps" : "games")}</span>
        <button type="button" className="crete-tool" aria-expanded={popup === "sources"} data-nav-focused={toolbarIndex === filterCount} onClick={() => showPopup("sources")}><IoLayersOutline /><span>{p.gameSourceTab === "All" ? text("Sources") : p.gameSourceTab}</span></button>
        <button type="button" className="crete-tool" aria-label={text("Search games")} aria-expanded={searching} data-nav-focused={toolbarIndex === filterCount + 1} onClick={() => setSearching(value => !value)}><IoSearchOutline /></button>
        {!appsMode && <button type="button" className="crete-tool" aria-label={text("Sort games")} aria-expanded={popup === "sort"} data-nav-focused={toolbarIndex === filterCount + 2} onClick={() => showPopup("sort")}><IoFilterOutline /></button>}
        <button type="button" className="crete-tool" aria-label={text("Manage library")} data-nav-focused={toolbarIndex === lastToolbarIndex} onClick={p.onManageLibrary}><IoLayersOutline /><span>{text("Manage")}</span></button>
      </div>
    </div>
    {searching && <div className="crete-search"><input ref={inputRef} data-crete-text-input aria-label={text("Search games")} placeholder={text("Search games…")} value={query} onChange={event => { setQuery(event.target.value); setSelectedId(null); }} onKeyDown={event => { if (event.key === "Escape") { setSearching(false); setQuery(""); event.stopPropagation(); } }} />
      <button type="button" aria-label={text("Close search")} onClick={() => { setSearching(false); setQuery(""); }}><IoClose /></button></div>}
    {popup && <div className="crete-picker" role="group" aria-label={text(popup === "sort" ? "Sort order" : "Library sources")}>
      {choices.map((label, i) => <button type="button" key={`${i}-${label}`} data-nav-focused={popupIndex === i}
        aria-pressed={popup === "sort" ? ["recent", "az", "store"][i] === p.gamesSort : label === p.gameSourceTab}
        onClick={() => choose(i)}>{label === "All" || !p.gameSourceTabs.includes(label) ? text(label) : label}</button>)}
      <button type="button" className="crete-picker-close" onClick={() => setPopup(null)}>{text("Close")}</button>
    </div>}
    <div className={`crete-library-grid${listMode ? " crete-library-list" : ""}`} ref={gridRef}
      style={{ gridTemplateColumns: `repeat(${listMode ? Math.max(1, p.settings.app_list_cols ?? 1) : columns}, minmax(0, 1fr))`, "--crete-app-list-cols": Math.max(1, p.settings.app_list_cols ?? 1) } as CSSProperties}>
      {items.map(app => listMode ? <button key={app.id} type="button" className="crete-app-list-item" aria-label={app.name} data-game-id={app.id} data-selected={toolbarIndex === null && selected?.id === app.id}
        onFocus={() => { setSelectedId(app.id); setToolbarIndex(null); }} onClick={() => p.openDetails(app)}
        onContextMenu={event => { event.preventDefault(); p.setContextMenu({ x: event.clientX, y: event.clientY, app }); }}>
        {p.AppIcon ? <p.AppIcon app={app} size={48} /> : <span>{app.name.slice(0, 1)}</span>}<strong>{app.name}</strong>
        {p.settings.show_store_badges !== false && <StoreBadge source={app.source} inline />}{p.pins.includes(app.id) && <span aria-label={text("Pinned")}>◆</span>}{p.isRunning(app.id) && <span>{text("Running")}</span>}
      </button> : <CreteGameCard key={app.id} app={app}
      art={p.customArt[app.id] || p.gameArt[app.id]} selected={toolbarIndex === null && selected?.id === app.id}
      pinned={gameCopies(app).some(copy => p.pins.includes(copy.id))} running={p.isRunning(app.id)} showStoreBadge={p.settings.show_store_badges}
      onContextMenu={event => { event.preventDefault(); p.setContextMenu({ x: event.clientX, y: event.clientY, app }); }}
      onSelect={() => { setSelectedId(app.id); setToolbarIndex(null); }} onOpen={() => p.openDetails(app)} />)}</div>
    {!items.length && <div className="crete-empty"><h2>{text(appsMode ? "No applications found" : "No games found")}</h2><p>{text(query ? "Try another search." : "Choose another filter or add a source in Settings.")}</p></div>}
  </div>;
}

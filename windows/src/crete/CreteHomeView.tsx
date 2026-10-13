import { useRecordedHistory } from "./useRecordedHistory";
import { useNews } from "./useNews";
import { openUrl } from "@tauri-apps/plugin-opener";
import { useEffect, useMemo, useRef, useState, type RefObject, type ReactNode, type CSSProperties } from "react";
import { IoGameControllerOutline, IoPlay } from "react-icons/io5";
import type { App, Settings } from "../types";
import { useStoreMetadata } from "../hooks/useStoreMetadata";
import { CreteGameCard } from "./CreteGameCard";
import { defaultProvider, geforceNowUrl, moonlightTarget } from "./streaming";
import { useStreaming } from "./useStreaming";
import { scrollPane } from "./smoothScroll";
import { useViewNavigation, type NavigationRef } from "./navigation";
import { playedGames, playtime, lastPlayed, steamId, sourceLabel } from "./model";
import { isHeroVideoLayer, resolveHeroType } from "../utils/heroMedia";
import { useCreteText } from "./text";

export interface CreteHomeProps {
  active: boolean; apps: App[]; recent: App[]; pins: string[];
  customArt: Record<string, string>; gameArt: Record<string, string>;
  heroStatic: Record<string, string>; customHeroArt: Record<string, string>;
  heroAnimated: Record<string, string>; heroCustomType: Record<string, string>;
  scrollRef: RefObject<HTMLDivElement>; navigationRef: NavigationRef; settings: Settings;
  onOpenDetails: (app: App) => void; triggerLaunch: (app: App, recent: App[]) => void;
  recentRef: RefObject<App[]>; onLibrary: () => void; onSearch: () => void;
  isRunning: (id: string) => boolean; togglePin: (app: App) => void;
  setContextMenu: (menu: { x: number; y: number; app: App }) => void;
  gameCollections: { id: string; name: string }[]; appCollections: { id: string; name: string }[];
  gameMemberships: Record<string, string[]>; appMemberships: Record<string, string[]>;
  homeHiddenCollections: string[]; appPaused: boolean; spotifyHeroChip?: ReactNode;
}

export function CreteHomeView(p: CreteHomeProps) {
  const text = useCreteText();
  const recorded = useRecordedHistory(p.active, p.recent);
  const games = useMemo(() => playedGames(p.apps, recorded), [p.apps, recorded]);
  const hero = games[0];
  const streaming = useStreaming(hero ?? { id: "", name: "" }, p.active && !!hero);
  const recentItems = p.settings.show_recent_games_only ? games.slice(1) : [...games.slice(1), ...p.recent.filter(app => app.app_type === "app")];
  const pinned = p.settings.show_home_pinned !== false && p.settings.home_pinned_pos !== "none"
    ? p.pins.flatMap(id => p.apps.find(app => app.id === id) ?? []) : [];
  const collections = p.settings.show_home_collections ? [...(p.gameCollections ?? []).map(col => ({ ...col, kind: "game" })), ...(p.appCollections ?? []).map(col => ({ ...col, kind: "app" }))]
    .filter(col => !(p.homeHiddenCollections ?? []).includes(col.name)).map(col => ({ id: `collection:${col.kind}:${col.id}`, name: col.name,
      items: p.apps.filter(app => app.app_type === col.kind && (col.kind === "game" ? p.gameMemberships : p.appMemberships)?.[app.id]?.includes(col.id)) })) : [];
  const rows = [
    ...(p.settings.home_pinned_pos === "top" && pinned.length ? [{ id: "pinned", name: text("Pinned"), items: pinned }] : []),
    ...(p.settings.show_home_recents !== false && recentItems.length ? [{ id: "recent", name: text("Jump back in"), items: recentItems }] : []),
    ...collections.filter(row => row.items.length),
    ...(p.settings.home_pinned_pos !== "top" && pinned.length ? [{ id: "pinned", name: text("Pinned"), items: pinned }] : []),
  ];
  const rowSignature = rows.map(row => `${row.id}:${row.items.map(app => app.id).join(",")}`).join(";");
  const [selection, setSelection] = useState<{ row: string; id: string } | null>(null);
  const [launchError, setLaunchError] = useState<string | null>(null);
  const news = useNews(p.active);
  const [newsIndex, setNewsIndex] = useState(-1);
  const onNews = newsIndex >= 0 && news.length > 0;
  const rowIndex = selection ? rows.findIndex(row => row.id === selection.row) : -1;
  const selectedRow = rows[rowIndex];
  const cardIndex = Math.max(0, selectedRow?.items.findIndex(app => app.id === selection?.id) ?? 0);
  const selected = onNews ? undefined : selectedRow?.items[cardIndex] ?? hero;
  const heroSectionRef = useRef<HTMLElement>(null);
  const rowsRef = useRef<Record<string, HTMLDivElement | null>>({});
  const newsRef = useRef<HTMLDivElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const meta = useStoreMetadata(hero?.source, steamId(hero), hero?.xbox_product_id, !!hero && p.active && p.settings.fetch_store_metadata);
  const staticArtwork = hero && ((p.heroCustomType?.[hero.id] !== "animated" ? p.customHeroArt[hero.id] : undefined) || p.heroStatic[hero.id] || meta.data?.background || meta.data?.headerImage);
  const heroType = resolveHeroType(p.settings.animated_heroes, hero && p.heroCustomType?.[hero.id]);
  const animatedArtwork = hero && (p.heroAnimated?.[hero.id] || (heroType === "animated" ? p.customHeroArt[hero.id] : undefined));
  const artwork = heroType === "animated" && animatedArtwork ? animatedArtwork : staticArtwork;
  const showArtwork = p.settings.home_mode === "normal" || p.settings.show_immersive_hero_art !== false;
  const heroProvider = hero ? p.isRunning(hero.id) ? "local" : defaultProvider(hero, streaming.moonlightGame, geforceNowUrl(hero)) : "install";
  const open = (app: App) => {
    if (!p.settings.home_launch_games_directly) { p.onOpenDetails(app); return; }
    const host = moonlightTarget(app, streaming.apps, streaming.hostGames?.map(game => game.steamAppId) ?? [], !!streaming.buddy?.paired);
    if (!p.isRunning(app.id) && (streaming.checking || host)) { p.onOpenDetails(app); return; }
    setLaunchError(null);
    const url = app.installed === false ? geforceNowUrl(app) : undefined;
    if (url) void openUrl(url).catch(error => setLaunchError(String(error)));
    else if (app.installed === false) p.onOpenDetails(app);
    else p.triggerLaunch(app, p.recentRef.current);
  };
  const selectRow = (index: number) => {
    const row = rows[index];
    setNewsIndex(-1);
    setSelection(row ? { row: row.id, id: row.items[Math.min(cardIndex, row.items.length - 1)].id } : null);
  };
  useEffect(() => {
    if (!selection) return;
    const row = rows.find(row => row.id === selection.row);
    if (!row) setSelection(null);
    else if (!row.items.some(app => app.id === selection.id)) setSelection({ row: row.id, id: row.items[0].id });
  }, [rowSignature, selection?.row, selection?.id]);
  useViewNavigation(p.active, p.navigationRef, key => {
    if (key === "ButtonY") { p.onSearch(); return true; }
    if (key === "ButtonX" && selected) { p.togglePin(selected); return true; }
    if (onNews) {
      if (key === "ArrowUp") selectRow(rows.length - 1);
      else if (key === "ArrowLeft" || key === "ArrowRight") setNewsIndex(Math.max(0, Math.min(news.length - 1, newsIndex + (key === "ArrowRight" ? 1 : -1))));
      else if (key === "Enter") void openUrl(news[newsIndex].url).catch(() => {});
      else return key === "ArrowDown";
      return true;
    }
    if (key === "ArrowDown") {
      if (rowIndex + 1 < rows.length) selectRow(rowIndex + 1);
      else if (news.length) { setSelection(null); setNewsIndex(0); }
      return true;
    }
    if (key === "ArrowUp") { selectRow(rowIndex - 1); return true; }
    if ((key === "ArrowLeft" || key === "ArrowRight") && selectedRow) {
      const next = Math.max(0, Math.min(selectedRow.items.length - 1, cardIndex + (key === "ArrowRight" ? 1 : -1)));
      setSelection({ row: selectedRow.id, id: selectedRow.items[next].id }); return true;
    }
    if (key === "Enter") { if (selected) open(selected); else p.onLibrary(); return true; }
    return ["ArrowLeft", "ArrowRight"].includes(key);
  });
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    const sync = () => { if (p.active && !p.appPaused && !document.hidden) void video.play().catch(() => {}); else video.pause(); };
    sync(); video.addEventListener("loadeddata", sync); document.addEventListener("visibilitychange", sync);
    return () => { video.pause(); video.removeEventListener("loadeddata", sync); document.removeEventListener("visibilitychange", sync); };
  }, [p.active, p.appPaused, artwork]);
  useEffect(() => {
    if (!p.active) return;
    const pane = p.scrollRef.current;
    if (!pane) return;
    if (!selectedRow && !onNews) { scrollPane(pane, { top: 0 }); return; }
    const row = onNews ? newsRef.current : rowsRef.current[selectedRow?.id ?? ""];
    const target = row?.querySelector('[data-selected="true"]');
    if (!target || !row) return;
    const bounds = target.getBoundingClientRect(); const rect = pane.getBoundingClientRect();
    const scale = rect.height / pane.clientHeight || 1; const style = getComputedStyle(pane);
    const top = rect.top + (parseFloat(style.scrollPaddingTop) || 0) * scale;
    const bottom = rect.bottom - (parseFloat(style.scrollPaddingBottom) || 0) * scale;
    const delta = bounds.top < top ? bounds.top - top : bounds.bottom > bottom ? bounds.bottom - bottom : 0;
    if (delta) scrollPane(pane, { top: pane.scrollTop + delta / scale });
    const rowBounds = row.getBoundingClientRect();
    const horizontal = bounds.left < rowBounds.left ? bounds.left - rowBounds.left : bounds.right > rowBounds.right ? bounds.right - rowBounds.right : 0;
    if (horizontal) scrollPane(row, { left: row.scrollLeft + horizontal / scale });
  }, [p.active, selection?.row, selection?.id, newsIndex]);
  const context = (event: React.MouseEvent, app: App) => { event.preventDefault(); p.setContextMenu({ x: event.clientX, y: event.clientY, app }); };
  return <div ref={p.scrollRef} className="crete-home crete-scroller" data-home-mode={p.settings.cinematic_home ? "immersive" : p.settings.home_mode}
    data-copy-position={p.settings.hero_content_pos} aria-hidden={!p.active}
    style={{ contentVisibility: p.active ? "visible" : "hidden", pointerEvents: p.active ? "auto" : "none", "--crete-home-cover": `${152 * (p.settings.home_cover_scale ?? 1)}px`, "--crete-section-size": p.settings.home_section_title_size === "large" ? "20px" : p.settings.home_section_title_size === "medium" ? "17px" : "15px" } as CSSProperties}>
    {showArtwork && staticArtwork && <div className="crete-home-backdrop" style={{ backgroundImage: `url(${JSON.stringify(staticArtwork)})` }} />}
    {!hero ? <div className="crete-empty"><IoGameControllerOutline size={40} /><h1>{text("No games played yet")}</h1><p>{text("Your recently played games will appear here.")}</p><button type="button" onClick={p.onLibrary}>{text("Open Library")}</button></div> :
      <section ref={heroSectionRef} className="crete-hero" data-focused={!selectedRow && !onNews} aria-label={text("Continue playing")} onContextMenu={event => context(event, hero)}>
        {showArtwork && staticArtwork && <img className="crete-hero-art" src={staticArtwork} alt="" />}
        {showArtwork && heroType === "animated" && animatedArtwork && (isHeroVideoLayer(heroType, animatedArtwork)
          ? <video ref={videoRef} className="crete-hero-art" src={animatedArtwork} muted loop playsInline style={{ opacity: 0 }}
            onPlaying={event => { event.currentTarget.style.opacity = "1"; }} onError={event => { event.currentTarget.style.opacity = "0"; }} />
          : <img className="crete-hero-art" src={animatedArtwork} alt="" onError={event => { event.currentTarget.style.display = "none"; }} />)}
        <div className="crete-hero-shade" />
        <div className="crete-hero-copy"><span className="crete-eyebrow crete-amber">{text("Continue where you stopped")}</span>
          <div className="crete-hero-title">{p.settings.show_hero_cover && (p.customArt[hero.id] || p.gameArt[hero.id]) && <img className="crete-hero-cover" src={p.customArt[hero.id] || p.gameArt[hero.id]} alt="" />}<h1>{hero.name}</h1></div>
          <div className="crete-hero-tags">{p.settings.show_store_badges !== false && <span>{sourceLabel(hero.source)}</span>}{hero.installed && <span className="crete-local">{text("Local")}</span>}</div>
          <button type="button" className="crete-play" disabled={!p.isRunning(hero.id) && streaming.checking} data-cloud={heroProvider === "moonlight" || heroProvider === "geforceNow"}
            onFocus={() => { setSelection(null); setNewsIndex(-1); }} onClick={() => open(hero)}><IoPlay />{text(!p.isRunning(hero.id) && streaming.checking ? "Checking Bosgame…" : heroProvider === "moonlight" ? "Play via Moonlight" : heroProvider === "geforceNow" ? "Play via GeForce NOW" : "Play")}</button>
          {launchError && <p role="alert">{launchError}</p>}{p.spotifyHeroChip}
        </div>
        <aside className="crete-hero-info"><span className="crete-eyebrow">{text("Game info")}</span><dl><dt>{text("Developer")}</dt><dd>{meta.data?.developers.join(", ") || "—"}</dd><dt>{text("Publisher")}</dt><dd>{meta.data?.publishers.join(", ") || "—"}</dd><dt>{text("Playtime")}</dt><dd>{playtime(hero.playtime_minutes)}</dd><dt>{text("Last played")}</dt><dd>{lastPlayed(hero.last_played)}</dd></dl></aside>
      </section>}
    {rows.map(row => <section className="crete-rail" key={row.id} data-home-row={row.id} aria-label={row.name}>{(p.settings.show_home_collection_names !== false || !row.id.startsWith("collection:")) && <h2>{row.name}<span /><small>{row.items.length}</small></h2>}
      <div className="crete-rail-cards" ref={node => { rowsRef.current[row.id] = node; }}>{row.items.map(app => <CreteGameCard key={app.id} app={app} art={p.customArt[app.id] || p.gameArt[app.id]} pinned={p.pins.includes(app.id)} running={p.isRunning(app.id)} selected={!onNews && selection?.row === row.id && selection.id === app.id} showStoreBadge={p.settings.show_store_badges}
        onSelect={() => { setSelection({ row: row.id, id: app.id }); setNewsIndex(-1); }} onContextMenu={event => context(event, app)} onOpen={() => open(app)} />)}</div>
    </section>)}
    {news.length > 0 && <section className="crete-rail crete-news"><h2>{text("What's new")}<span /><small>{news.length}</small></h2><div ref={newsRef} className="crete-rail-cards">{news.map((post, i) => <button key={post.id} type="button" className="crete-news-card" data-selected={onNews && newsIndex === i} onFocus={() => { setSelection(null); setNewsIndex(i); }} onClick={() => void openUrl(post.url).catch(() => {})}>{post.thumbnail && <img src={post.thumbnail} alt="" loading="lazy" onError={event => { event.currentTarget.style.display = "none"; }} />}<div><small>{post.subreddit}</small><strong>{post.title}</strong></div></button>)}</div></section>}
  </div>;
}

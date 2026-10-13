import { useRecordedHistory } from "./useRecordedHistory";
import { useNews } from "./useNews";
import { openUrl } from "@tauri-apps/plugin-opener";
import { useEffect, useMemo, useRef, useState, type RefObject } from "react";
import { IoGameControllerOutline, IoPlay } from "react-icons/io5";
import type { App, Settings } from "../types";
import { useStoreMetadata } from "../hooks/useStoreMetadata";
import { CreteGameCard } from "./CreteGameCard";
import { defaultProvider, geforceNowUrl, moonlightMatch } from "./streaming";
import { useStreaming } from "./useStreaming";
import { scrollPane } from "./smoothScroll";
import { useViewNavigation, type NavigationRef } from "./navigation";
import { playedGames, playtime, lastPlayed, steamId, sourceLabel } from "./model";

export interface CreteHomeProps {
  active: boolean;
  apps: App[];
  recent: App[];
  pins: string[];
  customArt: Record<string, string>;
  gameArt: Record<string, string>;
  heroStatic: Record<string, string>;
  customHeroArt: Record<string, string>;
  scrollRef: RefObject<HTMLDivElement>;
  navigationRef: NavigationRef;
  settings: Settings;
  onOpenDetails: (app: App) => void;
  triggerLaunch: (app: App, recent: App[]) => void;
  recentRef: RefObject<App[]>;
  onLibrary: () => void;
  onSearch: () => void;
  isRunning: (id: string) => boolean;
}

export function CreteHomeView(p: CreteHomeProps) {
  const recorded = useRecordedHistory(p.active, p.recent);
  const games = useMemo(() => playedGames(p.apps, recorded), [p.apps, recorded]);
  const hero = games[0];
  const streaming = useStreaming(hero ?? { id: "", name: "" }, p.active && !!hero);
  const rail = games.slice(1);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [launchError, setLaunchError] = useState<string | null>(null);
  const news = useNews(p.active);
  const [newsIndex, setNewsIndex] = useState(-1);
  const onNews = newsIndex >= 0 && news.length > 0;
  const railIndex = Math.max(0, rail.findIndex(app => app.id === selectedId));
  const onRail = !!selectedId && rail.length > 0 && !onNews;
  const heroRef = useRef<HTMLButtonElement>(null);
  const heroSectionRef = useRef<HTMLElement>(null);
  const railRef = useRef<HTMLDivElement>(null);
  const newsRef = useRef<HTMLDivElement>(null);
  const openNews = (index: number) => { void openUrl(news[index].url).catch(() => {}); };
  const meta = useStoreMetadata(hero?.source, steamId(hero), hero?.xbox_product_id, !!hero && p.active && p.settings.fetch_store_metadata);
  const artwork = hero && (p.customHeroArt[hero.id] || p.heroStatic[hero.id] || meta.data?.background || meta.data?.headerImage);
  const heroProvider = hero ? defaultProvider(hero, streaming.moonlightGame, geforceNowUrl(hero)) : "install";
  const cloudHeroUrl = heroProvider === "geforceNow" && hero ? geforceNowUrl(hero) : undefined;
  const open = (app: App) => {
    if (p.settings.home_launch_games_directly && app.installed === false && moonlightMatch(app, streaming.apps)) { p.onOpenDetails(app); return; }
    setLaunchError(null);
    const url = app.installed === false ? geforceNowUrl(app) : undefined;
    if (p.settings.home_launch_games_directly && url) void openUrl(url).catch(error => setLaunchError(String(error)));
    else if (p.settings.home_launch_games_directly) p.triggerLaunch(app, p.recentRef.current);
    else p.onOpenDetails(app);
  };

  useViewNavigation(p.active, p.navigationRef, key => {
    if (key === "ButtonY") { p.onSearch(); return true; }
    if (onNews) {
      if (key === "ArrowUp") { setNewsIndex(-1); if (rail.length) setSelectedId(rail[railIndex].id); }
      else if (key === "ArrowLeft" || key === "ArrowRight") setNewsIndex(Math.max(0, Math.min(news.length - 1, newsIndex + (key === "ArrowRight" ? 1 : -1))));
      else if (key === "Enter") openNews(newsIndex);
      else return key === "ArrowDown";
      return true;
    }
    if (!hero) {
      if (key === "ArrowDown" && news.length) setNewsIndex(0);
      if (key === "Enter") p.onLibrary();
      return ["ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "Enter"].includes(key);
    }
    if (key === "ArrowDown" && news.length && (onRail || !rail.length)) setNewsIndex(0);
    else if (key === "ArrowDown" && rail.length) setSelectedId(rail[railIndex].id);
    else if (key === "ArrowUp") setSelectedId(null);
    else if ((key === "ArrowLeft" || key === "ArrowRight") && onRail) {
      const next = Math.max(0, Math.min(rail.length - 1, railIndex + (key === "ArrowRight" ? 1 : -1)));
      setSelectedId(rail[next].id);
    } else if (key === "Enter") open(onRail ? rail[railIndex] : hero);
    else return ["ArrowLeft", "ArrowRight"].includes(key);
    return true;
  });

  useEffect(() => {
    if (!p.active) return;
    const scroller = p.scrollRef.current;
    if (!scroller) return;
    if (!onRail && !onNews) {
      // Scroll this pane only: scrollIntoView can also move ancestor panes,
      // and revealing the Play button alone leaves the hero title offscreen.
      scrollPane(scroller, { top: 0 });
      return;
    }
    const target = onNews ? newsRef.current?.querySelector('[data-selected="true"]') : onRail ? railRef.current?.querySelector(`[data-selected="true"]`) : heroSectionRef.current;
    if (!target) return;
    const pane = scroller.getBoundingClientRect();
    const bounds = target.getBoundingClientRect();
    const scale = pane.height / scroller.clientHeight || 1;
    const style = getComputedStyle(scroller);
    const top = pane.top + (parseFloat(style.scrollPaddingTop) || 0) * scale;
    const bottom = pane.bottom - (parseFloat(style.scrollPaddingBottom) || 0) * scale;
    const delta = bounds.top < top ? bounds.top - top : bounds.bottom > bottom ? bounds.bottom - bottom : 0;
    if (delta) scrollPane(scroller, { top: scroller.scrollTop + delta / scale });
    const row = onNews ? newsRef.current : railRef.current;
    if (row) {
      const rowBounds = row.getBoundingClientRect();
      const horizontal = bounds.left < rowBounds.left ? bounds.left - rowBounds.left : bounds.right > rowBounds.right ? bounds.right - rowBounds.right : 0;
      if (horizontal) scrollPane(row, { left: row.scrollLeft + horizontal / scale });
    }
  }, [p.active, selectedId, onRail, onNews, newsIndex]);

  return <div ref={p.scrollRef} className="crete-home crete-scroller" aria-hidden={!p.active}
    style={{ contentVisibility: p.active ? "visible" : "hidden", pointerEvents: p.active ? "auto" : "none" }}>
    {artwork && <div className="crete-home-backdrop" style={{ backgroundImage: `url(${JSON.stringify(artwork)})` }} />}
    {!hero ? <div className="crete-empty"><IoGameControllerOutline size={40} /><h1>No games played yet</h1>
      <p>Your recently played games will appear here.</p><button type="button" onClick={p.onLibrary}>Open Library</button></div> : <>
      <section ref={heroSectionRef} className="crete-hero" data-focused={!onRail && !onNews} aria-label="Continue playing">
        {artwork && <img className="crete-hero-art" src={artwork} alt="" />}
        <div className="crete-hero-shade" />
        <div className="crete-hero-copy"><span className="crete-eyebrow crete-amber">Continue where you stopped</span>
          <h1>{hero.name}</h1><div className="crete-hero-tags"><span>{sourceLabel(hero.source)}</span>
            {hero.installed !== false && !!hero.source && hero.source !== "cloud" && <span className="crete-local">Local</span>}</div>
          <button type="button" ref={heroRef} className="crete-play" data-cloud={!!cloudHeroUrl || heroProvider === "moonlight"} onFocus={() => { setSelectedId(null); setNewsIndex(-1); }} onClick={() => open(hero)}><IoPlay /> {heroProvider === "moonlight" ? "Play via Moonlight" : cloudHeroUrl ? "Play via GeForce NOW" : "Play"}</button>
          {launchError && <p role="alert">{launchError}</p>}
        </div>
        <aside className="crete-hero-info"><span className="crete-eyebrow">Game info</span><dl>
          <dt>Developer</dt><dd>{meta.data?.developers.join(", ") || "—"}</dd>
          <dt>Publisher</dt><dd>{meta.data?.publishers.join(", ") || "—"}</dd>
          <dt>Playtime</dt><dd>{playtime(hero.playtime_minutes)}</dd>
          <dt>Last played</dt><dd>{lastPlayed(hero.last_played)}</dd>
        </dl></aside>
      </section>
      {rail.length > 0 && <section className="crete-rail"><h2>Jump back in <span /> <small>{rail.length}</small></h2>
        <div className="crete-rail-cards" ref={railRef}>{rail.map(app => <CreteGameCard key={app.id} app={app}
          art={p.customArt[app.id] || p.gameArt[app.id]} pinned={p.pins.includes(app.id)} running={p.isRunning(app.id)}
          selected={onRail && rail[railIndex].id === app.id} onSelect={() => { setSelectedId(app.id); setNewsIndex(-1); }} onOpen={() => open(app)} />)}</div>
      </section>}
    </>}
    {news.length > 0 && <section className="crete-rail crete-news"><h2>What's new <span /> <small>{news.length}</small></h2>
      <div ref={newsRef} className="crete-rail-cards">{news.map((post, i) => <button key={post.id} type="button" className="crete-news-card"
        data-selected={onNews && newsIndex === i} onFocus={() => setNewsIndex(i)} onClick={() => openNews(i)}>
        {post.thumbnail && <img src={post.thumbnail} alt="" loading="lazy" onError={event => { event.currentTarget.style.display = "none"; }} />}
        <div><small>{post.subreddit}</small><strong>{post.title}</strong></div>
      </button>)}</div>
    </section>}
  </div>;
}

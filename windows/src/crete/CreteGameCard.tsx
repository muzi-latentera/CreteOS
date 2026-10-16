import { useState, type MouseEvent } from "react";
import type { App } from "../types";
import { StoreBadge } from "../components/ui/StoreBadge";
import { useCreteText } from "./text";

interface Props {
  app: App;
  art?: string;
  selected: boolean;
  pinned?: boolean;
  running?: boolean;
  onSelect: () => void;
  onOpen: () => void;
  onContextMenu?: (event: MouseEvent<HTMLButtonElement>) => void;
  showStoreBadge?: boolean;
}

export function CreteGameCard({ app, art, selected, pinned, running, onSelect, onOpen, onContextMenu, showStoreBadge = true }: Props) {
  const text = useCreteText();
  const [failedUrls, setFailedUrls] = useState<string[]>([]);
  const [landscapeUrl, setLandscapeUrl] = useState<string | null>(null);
  const image = [art, app.store_cover_url].find(url => !!url && !failedUrls.includes(url));
  const landscape = !!image && landscapeUrl === image;
  return <button type="button" className="crete-game-card" data-selected={selected} data-game-id={app.id}
    aria-label={app.name} aria-current={selected ? "true" : undefined}
    onFocus={onSelect} onPointerDown={onSelect} onClick={onOpen} onContextMenu={onContextMenu}>
    {image ? <><img src={image} alt="" loading="lazy" decoding="async" data-landscape={landscape}
      onLoad={event => setLandscapeUrl(event.currentTarget.naturalWidth > event.currentTarget.naturalHeight ? image : null)}
      onError={() => setFailedUrls(previous => [...previous, image])} />
      {landscape && <strong className="crete-landscape-title">{app.name}</strong>}</>
      : <span className="crete-cover-fallback"><span aria-hidden="true">{app.name.slice(0, 1)}</span><strong>{app.name}</strong></span>}
    {showStoreBadge && <span className="crete-card-store"><StoreBadge source={app.source} inline /></span>}
    {pinned && <span className="crete-card-pin" aria-label={text("Pinned")}>◆</span>}
    {running && <span className="crete-card-running">{text("Running")}</span>}
  </button>;
}

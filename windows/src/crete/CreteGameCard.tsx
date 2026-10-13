import { useState } from "react";
import type { App } from "../types";
import { StoreBadge } from "../components/ui/StoreBadge";

interface Props {
  app: App;
  art?: string;
  selected: boolean;
  pinned?: boolean;
  running?: boolean;
  onSelect: () => void;
  onOpen: () => void;
}

export function CreteGameCard({ app, art, selected, pinned, running, onSelect, onOpen }: Props) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  const image = art && failedUrl !== art ? art : undefined;
  return <button type="button" className="crete-game-card" data-selected={selected} data-game-id={app.id}
    aria-label={app.name} aria-current={selected ? "true" : undefined}
    onFocus={onSelect} onPointerDown={onSelect} onClick={onOpen}>
    {image ? <img src={image} alt="" loading="lazy" decoding="async" onError={() => setFailedUrl(art ?? null)} />
      : <span className="crete-cover-fallback"><span aria-hidden="true">{app.name.slice(0, 1)}</span><strong>{app.name}</strong></span>}
    <span className="crete-card-store"><StoreBadge source={app.source} inline /></span>
    {pinned && <span className="crete-card-pin" aria-label="Pinned">◆</span>}
    {running && <span className="crete-card-running">Running</span>}
  </button>;
}

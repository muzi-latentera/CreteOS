import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
export interface ReviewVideo { id: string; title: string; channel: string; thumbnail: string; url: string }
const cache = new Map<string, Promise<ReviewVideo[]>>();
export function useReviewVideos(name: string, enabled: boolean) {
  const [videos, setVideos] = useState<ReviewVideo[]>([]);
  useEffect(() => {
    setVideos([]);
    if (!enabled || !navigator.onLine) return;
    let active = true;
    if (!cache.has(name)) cache.set(name, invoke<ReviewVideo[]>("get_crete_review_videos", { gameName: name }).catch(() => { cache.delete(name); return []; }));
    void cache.get(name)!.then(result => { if (active) setVideos(result ?? []); });
    return () => { active = false; };
  }, [name, enabled]);
  return videos;
}

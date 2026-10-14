import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
export interface ReviewVideo { id: string; title: string; channel: string; thumbnail: string; url: string }
export function reviewChoices(videos: ReviewVideo[]) {
  const chosen: ReviewVideo[] = []; const channels = new Set<string>();
  for (const video of videos) { const channel = video.channel.toLowerCase().replace(/[^a-z0-9]/g, ""); if (!channels.has(channel)) { chosen.push(video); channels.add(channel); } if (chosen.length === 4) return chosen; }
  for (const video of videos) { if (!chosen.includes(video)) chosen.push(video); if (chosen.length === 4) break; }
  return chosen;
}
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

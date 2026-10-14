import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { parseNews, type NewsPost } from "./news";

let cached: { posts: NewsPost[]; at: number } | undefined;
let pending: Promise<NewsPost[]> | undefined;
const listeners = new Set<(posts: NewsPost[]) => void>();
function publish(posts: NewsPost[]) { cached = { posts: [...posts], at: Date.now() }; listeners.forEach(listener => listener(cached!.posts)); }
function load() {
  if (cached && Date.now() - cached.at < 30 * 60_000) return Promise.resolve(cached.posts);
  if (!pending) pending = invoke<string>("get_crete_news_rss").then(parseNews).then(async posts => {
    const enriched = [...posts]; let next = 0;
    publish(enriched);
    await Promise.all(Array.from({ length: 4 }, async () => {
      while (next < enriched.length) { const index = next++; const post = enriched[index]; if (!post.thumbnail && post.articleUrl) {
        const thumbnail = await invoke<string | null>("get_crete_article_thumbnail", { articleUrl: post.articleUrl }).catch(() => null);
        if (thumbnail) { enriched[index] = { ...post, thumbnail }; publish(enriched); }
      } }
    }));
    return enriched;
  }).catch(() => cached?.posts ?? []).then(posts => {
    cached = { posts, at: Date.now() }; return posts;
  }).finally(() => { pending = undefined; });
  return pending;
}
export function useNews(active: boolean) {
  const [posts, setPosts] = useState<NewsPost[]>(cached?.posts ?? []);
  useEffect(() => {
    if (!active) return;
    let disposed = false;
    const update = (posts: NewsPost[]) => { if (!disposed) setPosts(posts); };
    listeners.add(update);
    const refresh = () => { void load().then(result => { if (!disposed) setPosts(result); }); };
    refresh(); window.addEventListener("focus", refresh);
    return () => { disposed = true; listeners.delete(update); window.removeEventListener("focus", refresh); };
  }, [active]);
  return posts;
}

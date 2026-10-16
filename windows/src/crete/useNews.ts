import { useEffect, useState } from "react";
import { convertFileSrc, invoke } from "@tauri-apps/api/core";
import { parseNews, type NewsPost } from "./news";

let cached: { posts: NewsPost[]; at: number } | undefined;
let pending: Promise<NewsPost[]> | undefined;
const listeners = new Set<(posts: NewsPost[]) => void>();
const failedImages = new Set<string>();
const recovering = new Set<string>();
function publish(posts: NewsPost[]) { cached = { posts: [...posts], at: Date.now() }; listeners.forEach(listener => listener(cached!.posts)); }
function onImageError(post: NewsPost) {
  if (!post.thumbnail || failedImages.has(post.thumbnail)) return;
  failedImages.add(post.thumbnail);
  if (!post.articleUrl || recovering.has(post.id)) return;
  recovering.add(post.id);
  // RSS hotlinks can fail even when the article has a working picture. Ask the
  // native downloader for that picture instead of permanently hiding the image.
  void invoke<string | null>("get_crete_article_thumbnail", { articleUrl: post.articleUrl }).catch(() => null).then(path => {
    if (!path || !cached) return;
    const thumbnail = /^https:\/\//.test(path) ? path : convertFileSrc(path);
    if (!failedImages.has(thumbnail)) publish(cached.posts.map(item => item.id === post.id ? { ...item, thumbnail } : item));
  }).finally(() => recovering.delete(post.id));
}
function load() {
  const incomplete = cached?.posts.some(post => !post.thumbnail || failedImages.has(post.thumbnail));
  if (cached && Date.now() - cached.at < (incomplete ? 60_000 : 30 * 60_000)) return Promise.resolve(cached.posts);
  if (!pending) pending = invoke<string>("get_crete_news_rss").then(parseNews).then(async posts => {
    const enriched = [...posts]; let next = 0;
    publish(enriched);
    await Promise.all(Array.from({ length: 4 }, async () => {
      while (next < enriched.length) { const index = next++; const post = enriched[index]; if (!post.thumbnail && post.articleUrl) {
        const thumbnail = await invoke<string | null>("get_crete_article_thumbnail", { articleUrl: post.articleUrl }).catch(() => null);
        if (thumbnail) { enriched[index] = { ...post, thumbnail: /^https:\/\//.test(thumbnail) ? thumbnail : convertFileSrc(thumbnail) }; publish(enriched); }
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
  return { posts, onImageError };
}

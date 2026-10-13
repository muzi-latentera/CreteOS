import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { parseNews, type NewsPost } from "./news";

let cached: { posts: NewsPost[]; at: number } | undefined;
let pending: Promise<NewsPost[]> | undefined;
function load() {
  if (cached && Date.now() - cached.at < 30 * 60_000) return Promise.resolve(cached.posts);
  if (!pending) pending = invoke<string>("get_crete_news_rss").then(parseNews).catch(() => []).then(posts => {
    cached = { posts, at: Date.now() }; return posts;
  }).finally(() => { pending = undefined; });
  return pending;
}
export function useNews(active: boolean) {
  const [posts, setPosts] = useState<NewsPost[]>(cached?.posts ?? []);
  useEffect(() => {
    if (!active) return;
    let disposed = false;
    const refresh = () => { void load().then(result => { if (!disposed) setPosts(result); }); };
    refresh(); window.addEventListener("focus", refresh);
    return () => { disposed = true; window.removeEventListener("focus", refresh); };
  }, [active]);
  return posts;
}

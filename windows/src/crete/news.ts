export interface NewsPost { id: string; title: string; url: string; subreddit: string; thumbnail?: string; articleUrl?: string }

export function newsImage(candidate?: string | null): string | undefined {
  if (!candidate) return;
  try { const image = new URL(candidate); if (image.protocol === "https:" && ["redd.it", "redditmedia.com", "ytimg.com"].some(host => image.hostname === host || image.hostname.endsWith(`.${host}`))) return image.href; } catch { /* malformed feed image */ }
}

export function parseNews(xml: string): NewsPost[] {
  const document = new DOMParser().parseFromString(xml, "application/xml");
  if (document.querySelector("parsererror")) return [];
  const buckets: Record<string, NewsPost[]> = { games: [], pcgaming: [] };
  const titles = new Set<string>();
  for (const entry of Array.from(document.getElementsByTagName("entry"))) {
    const title = entry.getElementsByTagName("title")[0]?.textContent?.trim() ?? "";
    const href = Array.from(entry.getElementsByTagName("link")).find(link => !link.getAttribute("rel") || link.getAttribute("rel") === "alternate")?.getAttribute("href");
    if (!title || !href || /mega\s?thread|(?:weekly|daily|monthly) (?:thread|discussion)|what (?:are|have) you (?:been )?playing|(?:suggestion|recommendation|tech support) thread|simple questions|free talk friday/i.test(title)) continue;
    try {
      const url = new URL(href);
      if (url.protocol !== "https:" || !["reddit.com", "www.reddit.com"].includes(url.hostname)) continue;
      const subreddit = url.pathname.match(/^\/r\/(games|pcgaming)\/comments\//i)?.[1].toLowerCase();
      const normalized = title.toLowerCase().replace(/[^\p{L}\p{N}]/gu, "");
      if (!subreddit || titles.has(normalized) || buckets[subreddit].length >= 10) continue;
      titles.add(normalized);
      const candidate = entry.getElementsByTagNameNS("http://search.yahoo.com/mrss/", "thumbnail")[0]?.getAttribute("url");
      const html = new DOMParser().parseFromString(entry.getElementsByTagName("content")[0]?.textContent ?? "", "text/html");
      let thumbnail = newsImage(candidate) || Array.from(html.images).map(img => newsImage(img.getAttribute("src"))).find(Boolean);
      let articleUrl: string | undefined;
      for (const link of Array.from(html.querySelectorAll('a[href]'))) {
        try {
          const article = new URL(link.getAttribute("href")!);
          if (article.protocol !== "https:" || article.hostname.endsWith("reddit.com") || article.hostname.endsWith("redd.it")) continue;
          if (!articleUrl || link.textContent?.trim() === "[link]") articleUrl = article.href;
          const videoId = article.hostname === "youtu.be" ? article.pathname.slice(1) : ["www.youtube.com", "youtube.com"].includes(article.hostname) ? article.searchParams.get("v") || article.pathname.match(/^\/(?:live|shorts|embed)\/([\w-]{11})(?:\/|$)/)?.[1] : undefined;
          if (videoId && /^[\w-]{11}$/.test(videoId)) thumbnail ||= `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`;
        } catch { /* malformed article */ }
      }
      buckets[subreddit].push({ id: url.href, title, url: url.href, subreddit: subreddit === "games" ? "r/Games" : "r/pcgaming", thumbnail, articleUrl });
    } catch { /* Ignore malformed external links. */ }
  }
  return Array.from({ length: 10 }, (_, i) => [buckets.games[i], buckets.pcgaming[i]]).flat().filter(Boolean);
}

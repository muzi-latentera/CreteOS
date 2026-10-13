export interface NewsPost { id: string; title: string; url: string; subreddit: string; thumbnail?: string }

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
      let thumbnail: string | undefined;
      if (candidate) {
        const image = new URL(candidate);
        if (image.protocol === "https:" && (image.hostname === "redd.it" || image.hostname.endsWith(".redd.it") || image.hostname === "redditmedia.com" || image.hostname.endsWith(".redditmedia.com"))) thumbnail = image.href;
      }
      buckets[subreddit].push({ id: url.href, title, url: url.href, subreddit: subreddit === "games" ? "r/Games" : "r/pcgaming", thumbnail });
    } catch { /* Ignore malformed external links. */ }
  }
  return Array.from({ length: 10 }, (_, i) => [buckets.games[i], buckets.pcgaming[i]]).flat().filter(Boolean);
}

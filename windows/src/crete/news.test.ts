import { describe, expect, it } from "vitest";
import { parseNews } from "./news";

const entry = (title: string, url: string, thumb = "") => `<entry><title>${title}</title><link rel="alternate" href="${url}"/>${thumb && `<media:thumbnail url="${thumb}"/>`}</entry>`;
const feed = (...entries: string[]) => `<feed xmlns="http://www.w3.org/2005/Atom" xmlns:media="http://search.yahoo.com/mrss/">${entries.join("")}</feed>`;
describe("CreteOS news feed", () => {
  it("interleaves both communities, removes duplicate headlines and discussion threads", () => {
    const posts = parseNews(feed(entry("First game", "https://www.reddit.com/r/Games/comments/one"), entry("Second game", "https://www.reddit.com/r/Games/comments/two"), entry("PC news", "https://www.reddit.com/r/pcgaming/comments/three"), entry("FIRST GAME!", "https://www.reddit.com/r/pcgaming/comments/four"), entry("Weekly discussion", "https://www.reddit.com/r/Games/comments/five")));
    expect(posts.map(post => post.title)).toEqual(["First game", "PC news", "Second game"]);
  });
  it("accepts only Reddit HTTPS article links and Reddit thumbnail hosts", () => {
    const posts = parseNews(feed(entry("Wrong host", "https://reddit.com.evil.test/r/Games/comments/one"), entry("Script", "javascript:alert(1)"), entry("Good", "https://www.reddit.com/r/Games/comments/two", "https://preview.redd.it/image.jpg"), entry("No external image", "https://reddit.com/r/pcgaming/comments/three", "https://external.test/image.jpg")));
    expect(posts).toHaveLength(2); expect(posts[0].thumbnail).toBe("https://preview.redd.it/image.jpg"); expect(posts[1].thumbnail).toBeUndefined();
  });
  it("hides empty or malformed feeds", () => {
    expect(parseNews("<bad")).toEqual([]); expect(parseNews(feed())).toEqual([]);
  });
  it("uses embedded RSS pictures and YouTube thumbnails for text-only Games posts", () => {
    const body = `<entry><title>Game review</title><link href="https://www.reddit.com/r/Games/comments/review"/><content type="html">&lt;a href="https://www.youtube.com/watch?v=abcdefghijk"&gt;[link]&lt;/a&gt;</content></entry>`;
    expect(parseNews(feed(body))[0].thumbnail).toBe("https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg");
  });
});

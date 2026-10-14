import { describe, it, expect } from "vitest";
import { reviewChoices, type ReviewVideo } from "./useReviewVideos";
describe("preferred review choices", () => {
  const video = (id: string, channel: string): ReviewVideo => ({ id, channel, title: id, url: id, thumbnail: id });
  it("keeps the four preferred channels before repeated reviews", () => {
    const videos = [video("a", "Whitelight"), video("b", "Whitelight"), video("c", "Skill Up"), video("d", "Joseph Anderson"), video("e", "Noah Caldwell-Gervais"), video("f", "gameranx")];
    expect(reviewChoices(videos).map(v => v.id)).toEqual(["a", "c", "d", "e"]);
  });
  it("fills up to four without duplicating videos if fewer channels exist", () => {
    const videos = [video("a", "Whitelight"), video("b", "Whitelight"), video("c", "Skill Up"), video("d", "Skill Up"), video("e", "Skill Up")];
    expect(reviewChoices(videos).map(v => v.id)).toEqual(["a", "c", "b", "d"]);
    expect(reviewChoices([])).toEqual([]);
  });
});

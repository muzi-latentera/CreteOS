import { describe, expect, it } from "vitest";
import { compareText } from "./compareText";

describe("compareText", () => {
  it("orders names exactly as localeCompare with base sensitivity", () => {
    const names = ["ABZU", "abzû", "Alien: Isolation", "112 Operator", "Élden", "elden ring", "", "Zelda", "zebra", "Ōkami"];
    const expected = [...names].sort((a, b) => a.localeCompare(b, undefined, { sensitivity: "base" }));
    expect([...names].sort(compareText)).toEqual(expected);
    expect(compareText(undefined, "")).toBe(0);
    expect(compareText("ABZU", "abzu")).toBe(0);
  });
});

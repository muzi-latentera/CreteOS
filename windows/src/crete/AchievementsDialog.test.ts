import { describe, expect, it } from "vitest";
import { orderAchievements } from "./AchievementsDialog";

describe("achievement order", () => {
  it("lists unlocked achievements newest first, then locked ones in game order", () => {
    const a = (id: string, unlocked: boolean, unlocked_at?: number) => ({ id, name: id, description: "", unlocked, unlocked_at });
    const ordered = orderAchievements([a("locked1", false), a("old", true, 100), a("locked2", false), a("new", true, 300), a("undated", true)]);
    expect(ordered.map(item => item.id)).toEqual(["new", "old", "undated", "locked1", "locked2"]);
  });
});

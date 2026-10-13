import { describe, expect, it } from "vitest";
import { selectWindowsRelease } from "./windowsReleases";

const releases = [
  { tag_name: "v9.0.0", name: "Android release" },
  { tag_name: "windows-v0.2.0" },
  { tag_name: "windows-v0.3.0-alpha.1", prerelease: true },
  { tag_name: "windows-v0.1.0" },
  { tag_name: "windows-v99.0.0", draft: true },
  { tag_name: "windows-vbroken" },
];

describe("Windows release feed", () => {
  it("ignores Android, drafts and prereleases on the stable channel", () => {
    expect(selectWindowsRelease(releases, false)?.tag_name).toBe("windows-v0.2.0");
  });
  it("selects the highest Windows version regardless of publication order", () => {
    expect(selectWindowsRelease(releases, true)?.tag_name).toBe("windows-v0.3.0-alpha.1");
  });
  it("does not suggest Android when no Windows builds have been released", () => {
    expect(selectWindowsRelease([{ tag_name: "v2.6.0" }], true)).toBeNull();
  });
});

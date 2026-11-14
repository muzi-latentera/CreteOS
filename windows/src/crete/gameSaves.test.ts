import { describe, expect, it } from "vitest";
import type { App } from "../types";
import { canManageSaves, hasLocalSaveBackup, hasSaveSafetyNet } from "./gameSaves";

const game = (source: string, launch_path: string): App => ({ id: source, name: "Game", source, app_type: "game", installed: true, launch_path } as App);

describe("which games get save management", () => {
  it("backs up direct programs and GOG to Google Drive", () => {
    expect(hasLocalSaveBackup(game("desktop", "C:\\Games\\Game.exe"))).toBe(true);
    expect(hasLocalSaveBackup(game("gog", "goggalaxy://openGameView/1"))).toBe(true);
    expect(hasLocalSaveBackup(game("steam", "steam://rungameid/1"))).toBe(false);
  });
  it("keeps a local safety net for store games, but not for streams", () => {
    expect(hasSaveSafetyNet(game("steam", "steam://rungameid/1145360"))).toBe(true);
    expect(hasSaveSafetyNet(game("epic", "com.epicgames.launcher://apps/x?action=launch"))).toBe(true);
    expect(hasSaveSafetyNet(game("xbox", "shell:AppsFolder\\Game!App"))).toBe(true);
    expect(hasSaveSafetyNet(game("cloud", "https://luna.amazon.co.uk/play"))).toBe(false);
    expect(canManageSaves(game("steam", "steam://rungameid/1"))).toBe(true);
    expect(canManageSaves({ ...game("steam", "steam://rungameid/1"), installed: false })).toBe(false);
  });
});

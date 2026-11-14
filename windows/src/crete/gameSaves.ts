import type { App } from "../types";

/**
 * Store games (Steam, Epic, Xbox…) keep their main save in the store's cloud;
 * CreteOS keeps three local safety versions, never uploaded. Mirrors
 * `game_saves::safety_net`.
 */
export function hasSaveSafetyNet(app: App) {
  return app.app_type === "game" && app.installed !== false
    && ["steam", "epic", "xbox", "battlenet", "ea", "ubisoft", "amazon"].includes(app.source ?? "")
    && !/^https?:\/\//i.test(app.launch_path ?? "");
}

/** Manage saves is offered for Drive-backed games and store-game safety nets. */
export const canManageSaves = (app: App) => hasLocalSaveBackup(app) || hasSaveSafetyNet(app);

/** Games whose saves CreteOS backs up to Google Drive (direct programs and GOG). */
export function hasLocalSaveBackup(app: App) {
  return app.app_type === "game" && app.installed !== false
    && ["", "desktop", "other", "custom", "manual", "local", "gog", "humble"].includes(app.source ?? "")
    && /^(?:[a-z]:[\\/]|\\\\|goggalaxy:\/\/)/i.test(app.launch_path ?? "");
}

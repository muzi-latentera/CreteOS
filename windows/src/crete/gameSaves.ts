import type { App } from "../types";

export function hasLocalSaveBackup(app: App) {
  return app.app_type === "game" && app.installed !== false
    && ["", "desktop", "other", "custom", "manual", "local", "gog", "humble"].includes(app.source ?? "")
    && /^(?:[a-z]:[\\/]|\\\\|goggalaxy:\/\/)/i.test(app.launch_path ?? "");
}

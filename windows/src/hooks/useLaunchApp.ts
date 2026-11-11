import { invoke } from "@tauri-apps/api/core";
import { emit } from "@tauri-apps/api/event";
import { useCallback } from "react";
import { getRunAsAdmin } from "../constants";
import type { App } from "../types";
import type { LocalDisplayMode } from "../crete/LocalDisplayField";
import type { StreamAudioChoice } from "../crete/StreamAudioFields";

export interface LocalLaunchOptions { audio: StreamAudioChoice; display: LocalDisplayMode | null }

interface LaunchAppResult {
  launchMode: string;
  childPid?: number | null;
  detail?: string | null;
  fallbackReason?: string | null;
}

export function launchApp(app: App, options?: LocalLaunchOptions) {
  return invoke<LaunchAppResult>("launch_app", {
    path: app.launch_path,
    id: app.id,
    name: app.name,
    appType: app.app_type,
    source: app.source ?? "",
    runAsAdmin: getRunAsAdmin(app.id),
    options,
  }).then((result) => {
    console.warn("launch_app result", {
      name: app.name,
      source: app.source ?? "",
      path: app.launch_path,
      ...result,
    });
    return result;
  }).catch((err) => {
    const message = typeof err === "string" ? err : err instanceof Error ? err.message : undefined;
    window.setTimeout(() => emit("launch-failed", { id: app.id, message }).catch(() => {}), 250);
    throw err;
  });
}

export function useLaunchApp() {
  return useCallback((app: App) => launchApp(app), []);
}

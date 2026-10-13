import { useCallback, useEffect, useRef, useState } from "react";
import { isNewerVersion } from "../utils/version";
import { selectWindowsRelease, type GithubRelease } from "../utils/windowsReleases";
import { WINDOWS_RELEASE_PREFIX } from "../constants";

export type UpdateStatus = "checking" | "up_to_date" | "available" | "error" | null;

export interface UpdateRelease {
  // Windows tag with "windows-v" stripped, e.g. "0.1.0-alpha.5".
  version: string;
  // Raw tag as published, used for nothing but display fallbacks.
  tag: string;
  htmlUrl: string;
  name: string;
  prerelease: boolean;
}

interface UseUpdateCheckOptions {
  appVersion: string;
  githubRepo: string;
  channel?: "stable" | "prerelease";
  // When false, only manual checks run. Defaults to false so that a caller
  // that has not opted in cannot accidentally start background traffic.
  autoCheckEnabled?: boolean;
  intervalHours?: number;
  // Delay before the first automatic check after mount. Kept generous so the
  // check never competes with the startup library scan for the main thread.
  startupDelayMs?: number;
  // Returns true when it is safe to interrupt the user with a modal.
  canPrompt?: () => boolean;
  onUpdateFound?: (release: UpdateRelease) => void;
}

export interface UpdateCheckData {
  updateStatus: UpdateStatus;
  updateInfo: string | null;
  checkForUpdates: () => void;
  pendingRelease: UpdateRelease | null;
  dismissPendingRelease: () => void;
  skipPendingRelease: () => void;
}

const LAST_CHECK_KEY = "liftoff_update_last_checked";
const SKIPPED_VERSION_KEY = "liftoff_update_skipped_version";
const DEFAULT_INTERVAL_HOURS = 12;
const DEFAULT_STARTUP_DELAY_MS = 25000;
// How often to re-test the "is it safe to prompt" gate once a release is held.
const PROMPT_RETRY_MS = 20000;

function readLastChecked(): number {
  try {
    const raw = window.localStorage.getItem(LAST_CHECK_KEY);
    const value = raw ? Number(raw) : 0;
    return Number.isFinite(value) ? value : 0;
  } catch {
    return 0;
  }
}

function writeLastChecked(timestamp: number): void {
  try {
    window.localStorage.setItem(LAST_CHECK_KEY, String(timestamp));
  } catch {
    // Storage failures are non-fatal; worst case we check again next launch.
  }
}

function readSkippedVersion(): string {
  try {
    return window.localStorage.getItem(SKIPPED_VERSION_KEY) ?? "";
  } catch {
    return "";
  }
}

function writeSkippedVersion(version: string): void {
  try {
    window.localStorage.setItem(SKIPPED_VERSION_KEY, version);
  } catch {
    // Non-fatal, see above.
  }
}

function toUpdateRelease(raw: GithubRelease | undefined, githubRepo: string): UpdateRelease | null {
  const tag = raw?.tag_name?.trim();
  if (!tag) return null;
  const version = tag.slice(WINDOWS_RELEASE_PREFIX.length);
  return {
    version,
    tag,
    htmlUrl: raw?.html_url || `https://github.com/${githubRepo}/releases`,
    name: raw?.name?.trim() || tag,
    prerelease: Boolean(raw?.prerelease),
  };
}

async function fetchLatestRelease(
  githubRepo: string,
  wantPrerelease: boolean,
): Promise<UpdateRelease | null> {
  const url = `https://api.github.com/repos/${githubRepo}/releases?per_page=100`;

  const response = await fetch(url, {
    headers: { Accept: "application/vnd.github+json" },
  });
  if (!response.ok) throw new Error(`GitHub API responded ${response.status}`);
  const data = await response.json();

  const list = Array.isArray(data) ? (data as GithubRelease[]) : [];
  return toUpdateRelease(selectWindowsRelease(list, wantPrerelease) ?? undefined, githubRepo);
}

export function useUpdateCheck({
  appVersion,
  githubRepo,
  channel = "stable",
  autoCheckEnabled = false,
  intervalHours = DEFAULT_INTERVAL_HOURS,
  startupDelayMs = DEFAULT_STARTUP_DELAY_MS,
  canPrompt,
  onUpdateFound,
}: UseUpdateCheckOptions): UpdateCheckData {
  const [updateStatus, setUpdateStatus] = useState<UpdateStatus>(null);
  const [updateInfo, setUpdateInfo] = useState<string | null>(null);
  const [pendingRelease, setPendingRelease] = useState<UpdateRelease | null>(null);

  // One automatic prompt per app session, no matter what.
  const promptedThisSessionRef = useRef(false);
  const inFlightRef = useRef(false);

  const channelRef = useRef(channel);
  const canPromptRef = useRef(canPrompt);
  const onUpdateFoundRef = useRef(onUpdateFound);
  useEffect(() => { channelRef.current = channel; });
  useEffect(() => { canPromptRef.current = canPrompt; });
  useEffect(() => { onUpdateFoundRef.current = onUpdateFound; });

  const runCheck = useCallback(
    async (isAutomatic: boolean) => {
      if (inFlightRef.current) return;
      inFlightRef.current = true;
      if (!isAutomatic) setUpdateStatus("checking");

      try {
        const release = await fetchLatestRelease(
          githubRepo,
          channelRef.current === "prerelease",
        );
        if (isAutomatic) writeLastChecked(Date.now());

        if (!release) {
          if (!isAutomatic) setUpdateStatus("up_to_date");
          return;
        }

        if (!isNewerVersion(release.version, appVersion)) {
          if (!isAutomatic) setUpdateStatus("up_to_date");
          return;
        }

        setUpdateStatus("available");
        setUpdateInfo(release.version);

        if (!isAutomatic) return;
        if (promptedThisSessionRef.current) return;
        if (readSkippedVersion() === release.version) return;
        setPendingRelease(release);
      } catch {
        if (!isAutomatic) setUpdateStatus("error");
      } finally {
        inFlightRef.current = false;
      }
    },
    [appVersion, githubRepo],
  );

  const checkForUpdates = useCallback(() => {
    void runCheck(false);
  }, [runCheck]);

  // Automatic scheduling. Uses wall-clock comparison rather than trusting a
  // long-lived interval, because handhelds sleep and resume constantly and a
  // suspended timer would otherwise silently skip its window.
  useEffect(() => {
    if (!autoCheckEnabled) return;

    let cancelled = false;
    const intervalMs = Math.max(1, intervalHours) * 60 * 60 * 1000;

    const maybeCheck = () => {
      if (cancelled) return;
      if (promptedThisSessionRef.current) return;
      if (Date.now() - readLastChecked() < intervalMs) return;
      void runCheck(true);
    };

    const startupTimer = window.setTimeout(maybeCheck, startupDelayMs);
    // Tick well below the interval so a wake-from-sleep is picked up promptly.
    const tickTimer = window.setInterval(maybeCheck, 15 * 60 * 1000);

    return () => {
      cancelled = true;
      window.clearTimeout(startupTimer);
      window.clearInterval(tickTimer);
    };
  }, [autoCheckEnabled, intervalHours, startupDelayMs, runCheck]);

  // Hold the found release until the app is actually idle enough to interrupt.
  useEffect(() => {
    if (!pendingRelease) return;
    if (promptedThisSessionRef.current) return;

    let cancelled = false;

    const attempt = () => {
      if (cancelled) return true;
      if (promptedThisSessionRef.current) return true;
      const gate = canPromptRef.current;
      if (gate && !gate()) return false;
      promptedThisSessionRef.current = true;
      onUpdateFoundRef.current?.(pendingRelease);
      return true;
    };

    if (attempt()) return;

    const retry = window.setInterval(() => {
      if (attempt()) window.clearInterval(retry);
    }, PROMPT_RETRY_MS);

    return () => {
      cancelled = true;
      window.clearInterval(retry);
    };
  }, [pendingRelease]);

  const dismissPendingRelease = useCallback(() => {
    setPendingRelease(null);
  }, []);

  const skipPendingRelease = useCallback(() => {
    setPendingRelease((current) => {
      if (current) writeSkippedVersion(current.version);
      return null;
    });
  }, []);

  return {
    updateStatus,
    updateInfo,
    checkForUpdates,
    pendingRelease,
    dismissPendingRelease,
    skipPendingRelease,
  };
}

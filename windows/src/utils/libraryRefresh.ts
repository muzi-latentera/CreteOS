interface RefreshLibrarySourcesOptions {
  steamConnected: boolean;
  refreshSteamOwned: () => Promise<unknown>;
  refreshSteamStatus: () => void;
  refreshGeForceCatalog: () => Promise<void>;
  refreshStoreAccounts?: () => Promise<unknown>;
  refreshLocalLibrary: () => Promise<void> | void;
}

export const SOURCE_REFRESH_TIMEOUT_MS = 95_000;
export async function withRefreshDeadline<T>(request: Promise<T>, timeout = SOURCE_REFRESH_TIMEOUT_MS): Promise<T> {
  let timer: ReturnType<typeof setTimeout>;
  try {
    return await Promise.race([request, new Promise<never>((_, reject) => {
      timer = setTimeout(() => reject(new Error("Library refresh timed out")), timeout);
    })]);
  } finally { clearTimeout(timer!); }
}

export async function refreshLibrarySources({
  steamConnected,
  refreshSteamOwned,
  refreshSteamStatus,
  refreshGeForceCatalog,
  refreshStoreAccounts,
  refreshLocalLibrary,
}: RefreshLibrarySourcesOptions): Promise<{ failedSources: string[] }> {
  const failedSources: string[] = [];
  const requests: [string, () => Promise<unknown>][] = [["GeForce NOW", refreshGeForceCatalog]];
  if (refreshStoreAccounts) requests.push(["Other accounts", () => withRefreshDeadline(Promise.resolve().then(refreshStoreAccounts), 30_000)]);
  if (steamConnected) requests.unshift(["Steam", async () => { await refreshSteamOwned(); refreshSteamStatus(); }]);
  const results = await Promise.allSettled(requests.map(([, run]) => withRefreshDeadline(Promise.resolve().then(run))));
  results.forEach((result, index) => { if (result.status === "rejected") failedSources.push(requests[index][0]); });
  // Always rescan after the remote caches settle, even if one service is offline.
  try { await withRefreshDeadline(Promise.resolve().then(refreshLocalLibrary)); }
  catch { failedSources.push("Local library"); }
  return { failedSources };
}

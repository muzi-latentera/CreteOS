interface RefreshLibrarySourcesOptions {
  steamConnected: boolean;
  refreshSteamOwned: () => Promise<unknown>;
  refreshSteamStatus: () => void;
  refreshLocalLibrary: () => Promise<void> | void;
}

export async function refreshLibrarySources({
  steamConnected,
  refreshSteamOwned,
  refreshSteamStatus,
  refreshLocalLibrary,
}: RefreshLibrarySourcesOptions): Promise<void> {
  if (steamConnected) {
    try {
      await refreshSteamOwned();
      refreshSteamStatus();
    } catch {
      // A local rescan remains useful when Steam is offline or authentication expired.
    }
  }
  await refreshLocalLibrary();
}

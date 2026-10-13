import { compareVersions } from "./version";
import { WINDOWS_RELEASE_PREFIX } from "../constants";

export interface GithubRelease {
  tag_name?: string;
  name?: string;
  html_url?: string;
  draft?: boolean;
  prerelease?: boolean;
}

/** Android and Windows releases share a repository; only Windows tags apply. */
export function selectWindowsRelease(releases: GithubRelease[], wantPrerelease: boolean) {
  let best: GithubRelease | null = null;
  for (const release of releases) {
    if (release.draft || (!wantPrerelease && release.prerelease)) continue;
    if (!release.tag_name?.startsWith(WINDOWS_RELEASE_PREFIX)) continue;
    const version = release.tag_name.slice(WINDOWS_RELEASE_PREFIX.length);
    if (compareVersions(version, version) === null) continue;
    const bestVersion = best?.tag_name?.slice(WINDOWS_RELEASE_PREFIX.length);
    if (!bestVersion || compareVersions(version, bestVersion)! > 0) best = release;
  }
  return best;
}

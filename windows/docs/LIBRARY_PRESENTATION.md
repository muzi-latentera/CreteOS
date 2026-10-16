# Preferred editions and artwork

The Library presents one preferred copy within the selected source/availability
filter. Recognized better editions win before store preference; Steam wins ties.
For example, Steam BioShock 2 Remastered replaces original BioShock 2 and the
Epic remaster, while Epic BioShock Infinite Complete Edition beats Steam standard.
Definitive/remastered and complete/ultimate/GOTY/gold/deluxe/enhanced suffixes are
recognized conservatively. Sequel numbers, years, remakes, demos and DLC stay
distinct. Ambiguous same-store releases are retained rather than silently merged.

This is presentation grouping, not deletion or hiding of account ownership. Other
copies remain in the Play dropdown and management actions, with their actual IDs,
executables and provider options. Explicit source filters still expose that store's
copies. Home recents, pins and collections use preferred representatives while
retaining underlying IDs/history; playtime totals are never added together.

Artwork first uses validated portraits from the owning store when supplied, then
an unambiguous Steam artwork-only title match or reviewed artwork alias, with
existing community sources as fallbacks. No Steam ownership/launch ID is inferred
from an artwork match. Landscape headers cannot become portrait covers. Cached
blank attempts retry once per background session/reconnection; failures do not
loop continuously. Concurrent cache writes merge atomically so stale snapshots or
blank failures cannot erase other successful downloads. Explicit clear remains
an actual clear. Custom artwork remains separate.

Reddit's r/Games feed often supplies no thumbnail, unlike r/pcgaming. CreteOS uses
YouTube thumbnails or the linked publisher's metadata image, downloaded to its
local cache. Known public publisher/CDN hosts are checked on redirects; unsupported
publishers, blocked requests or missing metadata can still leave a blank. It does
not manufacture an unrelated news image. A public 16 October probe found no feed
images in the first ten Games posts and publisher images on most linked pages;
that probe is separate from actual native/UI acceptance.

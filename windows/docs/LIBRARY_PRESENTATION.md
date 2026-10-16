# Preferred editions and artwork

The Library presents one preferred copy within the selected source/availability
filter. Recognized better editions win before store preference; Steam wins ties.
For example, Steam BioShock 2 Remastered replaces original BioShock 2 and the
Epic remaster, while Epic BioShock Infinite Complete Edition beats Steam standard.
Definitive/remastered and complete/ultimate/GOTY/gold/deluxe/enhanced/special/voidheart suffixes are
recognized conservatively. Sequel numbers, years, remakes, demos and DLC stay
distinct. Ambiguous same-store releases are retained rather than silently merged.
Trademark symbols are removed before Unicode compatibility normalization so ™
does not become the letters TM. Explicit Enhanced/Redux upgrades and the reviewed
STALKER Chornobyl/Chernobyl and Prypiat/Pripyat campaign spellings share a family.
Steam wins Dark Souls III/NieR ties; GTA V Enhanced and Ethan Carter Redux win
over their older editions. Epic Windows/mobile artifacts are filtered at import.

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
Network and disk artwork work runs on a blocking worker, outside the UI event
thread and the async executor; a slow publisher must not block navigation.
Some official 600x900 images are themselves padded landscape headers. Reviewed
portrait alternatives in artworkProducts.json take precedence for Freedom Cry,
Arkham Origins Blackgate, Child of Light, Blood Dragon, Mortal Kombat Komplete
and The Deed: Dynasty. Their source/author links are retained. A versioned cache
key and explicit atomic correction replace old non-empty covers; user custom art
is not overwritten. Dimensions alone are not a composition-quality test.

Reddit's r/Games feed often supplies no thumbnail, unlike r/pcgaming. CreteOS uses
YouTube thumbnails or the linked publisher's metadata image, downloaded to its
local cache. Known public publisher/CDN hosts are checked on redirects; unsupported
publishers, blocked requests or missing metadata can still leave a blank. It does
not manufacture an unrelated news image. A public 16 October probe found no feed
images in the first ten Games posts and publisher images on most linked pages;
that probe is separate from actual native/UI acceptance.

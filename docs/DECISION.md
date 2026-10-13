# Decision record: Monochrome on a Galaxy Watch 4

Date: 2026-10-09. Upstream inspected: `monochrome-music/monochrome` @ `eb602ad` (v2.5.1, 2026-10-06), shallow clone in `upstream/`.

## 1. Decision

You cannot literally fork Monochrome onto the watch. Upstream is a TypeScript/JavaScript/Vite web app: of its files, 108 are `.js`, 30 `.ts`, and there is **zero** Kotlin, Java, Gradle or `AndroidManifest.xml`. There is no Android code to fork. The closest real thing is a **native Kotlin/Compose Wear OS app that ports Monochrome's HiFi-API client contract** (routes, response envelopes, stream-manifest handling) and talks to **a HiFi-API endpoint you supply**, playing through Media3. Approaches A and C are the same project here: A is "port the logic", C is "native client", and the only thing that separates them is whether the contract is read from upstream or guessed. The earlier scaffold guessed (`api/featured`, `stream_url`); this reads it from upstream.

| | Reuses Monochrome code | Works on Watch 4 | Effort | Maintainability | Legal / ToS |
|---|---|---|---|---|---|
| **A/C. Native client, contract ported from upstream** (chosen) | Logic, not code: routes, DTO shapes, manifest parser | Yes (Media3 + Compose for Wear are first-class) | Medium | Medium: depends on upstream's API staying put | Low if the endpoint is yours and paid for |
| B. Phone runs Monochrome, watch is a remote | Most (runs the real PWA) | Only as a controller; audio plays on the phone | High: two apps, Data Layer, driving a web page from native code | Low | Same as A |
| D. WebView wrapper | All of it | **No**: Google documents Wear OS as a device that may have no WebView provider | Low | Low | Same as A, plus upstream's official-origin gate blocks streaming off-domain |

Rejected: **B** because you asked for an app on the watch, and B makes the watch a remote for the phone (kept as a possible later add-on). **D** because the platform does not promise a WebView provider on Wear OS, and even if one existed, the PWA is a desktop/phone layout on a 212 dp round screen with no guarantee of background audio.

### What was deliberately not ported
- The direct-to-TIDAL access path in `js/HiFi.ts` (the browser talking to TIDAL's own hosts with credentials that are not the user's). It is not ported, copied or referenced anywhere in this repo; the app only speaks to the user's own HiFi endpoint.
- Upstream's list of community API instances (`INSTANCES.md`). There is no default endpoint; an unconfigured app refuses to make requests.

Upstream's own README says a self-hosted copy cannot stream and tells you to supply "a working and paid HiFi API endpoint". That is the path this app follows. Keep it personal.

## 2. Verification

### Build and tests (this repo, run locally)
- `./gradlew --offline :core:data:testDebugUnitTest --rerun-tasks`: `BUILD SUCCESSFUL`, **63 tests, 0 failures**, 8 suites (repository, demo, endpoint policy, browse/album/artist/manifests contract, catalog contract, primitives, resolver, stream manifest). Fixtures are **synthetic**, shaped after upstream's parsers and typings (upstream's own tests contain no response fixtures); none was captured from a live server.
- `:app:assembleDebug` builds (~63 MB, dex stored uncompressed by AGP).
- `:app:assembleRelease` builds with R8 (~4 MB), signed with the debug keystore. Inspected, not just built: `apksigner verify` passes; `dexdump` shows `HifiApi`'s Retrofit annotations (`search/`, `track/`, `trackManifests/`, `@Url`) and generic `Signature`s (suspend return types) intact; `seeds.txt` shows `JsonElement.Companion` / `serializer()` kept for the converter's reflective lookup. Unused routes (`info`, `album`, `artist`, artist/album search) are removed by R8 as dead code. **R8 success is not runtime proof: the release APK has not run on any device.**

### Upstream facts
- Upstream has no Android code: `find` for `*.kt/*.java/*.gradle*/AndroidManifest.xml` returned nothing.
- Route contract read from `js/api.js` (`fetchWithRetry`, `searchTracks`, `getTrack`, `normalizeTrackManifestResponse`, `parseTrackLookup`, `extractStreamUrlFromManifest`, `findSearchSection`, `getAlbum`, `getArtist`, `deduplicateAlbums`), `js/HiFi.ts` types, `js/container-classes.ts`, `js/utils.js` (`QUALITY_TOKENS`).
- Google's doc line is real: `getCurrentWebViewPackage()` "can return `null` if the device ... doesn't support using `WebView`, such as a Wear OS device". The design assumes no WebView; a browser app on the watch is not evidence either way.

### On the physical Watch 4: reported by a separate device session (not observed by the session that wrote this section)
- Device `samsung SM-R870`, API 36. `FEATURE_WEBVIEW: no`, WebView provider: none (Diagnostics screen and `pm list features`). Decoders: AAC, FLAC, MP3, Opus present; E-AC-3 not available.
- An ~18 MB shrunk debug APK installed with `Success`. Launch reached `.MainActivity`; Home rendered; Settings showed "Album art" defaulting to **Off** with "Off (no cover requests)", and toggling showed "On (loads from image CDN)".

### Not verified
- **Remote playback against a real endpoint**: never. Which route an endpoint serves (`/trackManifests/` vs `/track/`), whether signed links load and pass the policy, and whether a live stream decodes are all unknown. No endpoint has been supplied; none will be invented, and community instances / upstream's credentials are off limits.
- **On this build**: Diagnostics walk, demo-track playback through `PlaybackService` (position advancing, media notification), Album-art persistence across `am force-stop`, and the covers-off "no CDN request" logcat check. The release APK has never been installed.
- Battery, Bluetooth routing, LTE, and Samsung's background limits on the playback service.

## 3. Plan / status

1. **[done] HiFi contract layer**: search, info, track, trackManifests (+ signed-link fetch), album (paged), artist (tree scan, dedupe), quality tokens, covers, manifest parser.
2. **[done] Wiring**: `CatalogRepository` → `HifiCatalog`; placeholder `monochrome/` package deleted; cancellation rethrown.
3. **[done] Stream resolution at play time**: `PlaybackService` → `HifiUri.resolve` (pure, tested) → `HifiCatalog.resolvePlayable`.
4. **[done] On-watch input**: one `TextEntryScreen` (Wear keyboard) for search and endpoint entry.
5. **[done] Diagnostics screen** replacing the removed WebView screen.
6. **[decided: not supported] DASH / hi-res**: `resolvePlayable` steps quality down (HI_RES → LOSSLESS → HIGH → LOW) and fails naming every quality tried. Reason: Bluetooth/mono-speaker output gains nothing from hi-res; DASH segments would widen the stream-policy surface; `media3-exoplayer-dash` is not even in the offline cache here. Not a TODO.
7. **[done] Hardening**: `EndpointPolicy` on base URL, signed link and final stream URL (catalog + resolver); album art opt-in; relative manifest URLs dropped by design.
8. **[done] Small installable APK**: R8 release (~4 MB) signed with the debug key; `scripts/reinstall.sh` installs release by default, `--variant debug` keeps the shrink + re-sign path.
9. **Next**: on-device pass of this build (see §2 "Not verified"); live playback once an endpoint is supplied; artist/album screens if wanted.

## 4. Risks / unknowns

- **API drift.** Upstream says `INSTANCES.md` is outdated ("switched away from hifi-api (kinda)"). Both playback routes are implemented from upstream's code; neither is confirmed against a server.
- **Release-only failures.** R8 output was inspected but never run; a reflective path not covered by the inspection could still fail only in release. Fall back to `--variant debug` if the release build misbehaves.
- **Cleartext policy is a heuristic** for hostnames (single-label / `.local` names are assumed private). An endpoint whose CDN serves `http://` URLs on a public host will be refused by design.
- **Expiring URLs.** Resolution happens at play time; expiry mid-track on long tracks is untested.
- **Background limits.** Samsung may stop the playback service under battery optimisation; needs a soak test on the watch.
- **Flaky install link.** The watch's Wi-Fi sleeps unless it is charging with the Wireless debugging page on screen; the adb port changes on every toggle.
- **Legal.** The catalog behind these endpoints is TIDAL's. Personal use against an endpoint you are entitled to use; do not distribute. The release APK is signed with a debug key precisely because it is not for distribution.

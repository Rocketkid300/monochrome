# Mono Watch

A native Wear OS music player for the **Samsung Galaxy Watch 4** (Wear OS,
450×450 round, minSdk 30). It ports the logic of
[Monochrome](https://github.com/monochrome-music/monochrome)'s HiFi-API client
into Kotlin/Compose. It does **not** run Monochrome's web UI.

Personal / self-hosted use only. See [docs/DECISION.md](docs/DECISION.md) for the
decision record, what was verified, and the risks.

## Why not just fork Monochrome onto the watch?

Monochrome is a web app / PWA (TypeScript + Vite). It contains no Android code,
so there is nothing to put on the watch. Wear OS does not promise a WebView
provider (Google's docs list "a Wear OS device" among devices where
`getCurrentWebViewPackage()` can be null), so wrapping the web UI is not a design
this app relies on. **The app never uses WebView**; the Diagnostics screen only
reports what the device has.

## What is ported from upstream

`:core:data/.../catalog/hifi/` reimplements, in Kotlin, the parts of upstream's
`js/api.js`, `js/HiFi.ts` types and `js/utils.js` that talk to a **HiFi-API
endpoint you supply**:

| Route | Used for | Wired into the app? |
|---|---|---|
| `GET search/?s=` | track search | yes (Browse → Search) |
| `GET trackManifests/?id=&quality=&adaptive=&formats=` | signed manifest link (tried first) | yes (playback) |
| `GET track/?id=&quality=` | playback info incl. the stream manifest (fallback) | yes (playback) |
| `GET search/?a=` / `?al=` | artist / album search | no |
| `GET info/?id=` | track metadata | no |
| `GET album/?id=[&offset=&limit=]` | album + paged track list | no |
| `GET artist/?id=` and `?f=&skip_tracks=true` | artist, discography fallback | no |

The unwired routes are ported and unit-tested but have no screen yet; R8 removes
them from the release APK as unused code.

Also ported: quality tokens (`HifiQuality`), cover-art URL building
(`HifiImages`), the stream-manifest parser (`StreamManifest`, incl. upstream's
keyword priority and "no `urls` array → null"), `findSearchSection`, and the
artist tree scan with `prepareAlbum` / `prepareArtist` / `deduplicateAlbums`.

Search results carry **no stream URL**. Tracks get a placeholder
`hifi://track/<id>` URI (`HifiUri`); just before playback `PlaybackService`
resolves it with `HifiCatalog.resolvePlayable` (`/trackManifests/` first, then
`/track/`, as upstream does).

**Not ported, on purpose:** upstream's direct-to-TIDAL access path in
`js/HiFi.ts`, and its list of community instances. There is no default server.

## Modules

```
:app             Wear entry point, navigation, permissions, theme
:core:data       Track model, catalogs (demo + hifi), settings, endpoint policy
:feature:player  PlaybackService (Media3), view models, Wear screens, diagnostics
```

## Status

- `:app:assembleDebug`, `:app:assembleRelease` (R8) and
  `:core:data:testDebugUnitTest` build and pass. Unit tests use **synthetic
  fixtures** shaped after upstream's parsers; they were not captured from a live
  server.
- Remote search and playback are wired (`CatalogRepository` → `HifiCatalog`,
  `PlaybackService` resolver). With "Remote backend" off, or no endpoint set, the
  app uses the bundled demo list. The home list is always the demo list (the HiFi
  contract has no "featured" route).
- On-device status: see [docs/DECISION.md §2](docs/DECISION.md). In short, a
  device session reports the app installed and partially walked on a Watch 4;
  **remote playback has never run against a real endpoint**, and the release
  APK has not run on a device at all.

## Build

Prerequisites: JDK 17, Android SDK 35.

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew --offline :app:assembleRelease :app:assembleDebug :core:data:testDebugUnitTest
```

- Release: `app/build/outputs/apk/release/app-release.apk`, about **4 MB**,
  minified by R8, signed with the debug keystore (personal sideloading only;
  see the comment in `app/build.gradle.kts`).
- Debug: `app/build/outputs/apk/debug/app-debug.apk`, about **63 MB** (AGP stores
  dex uncompressed).

`local.properties` points `sdk.dir` at the SDK on this machine and is git-ignored.

## Installing on the watch

Wireless debugging to the watch drops large transfers, so install with
`scripts/reinstall.sh`. By default it builds the **release** APK, checks its
alignment and signature, and installs it; nothing is re-packed.

```bash
scripts/reinstall.sh --connect <watch-ip>:<connect-port>
```

- `--variant debug` builds the debug APK instead and shrinks it first
  (recompresses the dex, keeps `resources.arsc` and native libs stored, re-signs
  with the debug key: ≈18 MB). Use it when you need a debuggable build.
- `--serial SERIAL` (or `ANDROID_SERIAL`) picks a device when several are attached.
- `--no-build` reuses the last APK of the variant; `--no-install` only builds and
  verifies.
- `--scan <ip>` is an opt-in scan of **one** host's ephemeral ports to find the
  adb port (slow; only if you don't have the port).

Release and debug are signed with the same key, so either installs over the other.

The watch's side matters as much as the script: put it **on the charger**, turn on
**Settings → Developer options → Wireless debugging**, and keep the **screen on
that page** for the whole transfer; otherwise its Wi-Fi sleeps and the install
fails with `device offline`. Use the "IP address & Port" from that page; the
pairing port is a different number, and both change whenever the setting is
toggled.

## Configuring an endpoint

There is no default endpoint; an unconfigured app makes no requests to any
HiFi server. Set it in **Settings → HiFi endpoint** (a HiFi-API-compatible base
URL you run or pay for). Saving a valid endpoint also turns the remote backend on.

Cleartext policy: `https://` is always accepted. `http://` is accepted only for
loopback, RFC 1918, link-local, Tailscale (`100.64.0.0/10`, `*.ts.net`), `.local`
and single-label hostnames (`EndpointPolicy`, unit-tested). The manifest sets
`usesCleartextTraffic` because Android cannot scope it to private networks, so
the rule is enforced in code, on the base URL, the signed manifest link, and the
final stream URL (inside `HifiCatalog` and again in the player's resolver). A
cleartext link to a **public** host is refused, so an endpoint whose CDN hands
out `http://` stream URLs will not play ("Stream URL refused"). The hostname
rules are a heuristic: a single-label name could in principle resolve publicly.

Which route your endpoint serves (`/trackManifests/` or `/track/`) is
**unconfirmed**: neither has been tried against a real server.

## Album art

**Off by default** (Settings → Album art). Covers load from a third-party image
CDN (`resources.tidal.com`, as upstream does), so with the setting off no cover
requests are made. Demo mode still streams its audio from `soundhelix.com`; with
an endpoint configured, traffic goes to your endpoint and whatever stream hosts
it hands back. A missing or failed cover never breaks a row: lists show no art,
and Now Playing only draws one when a URL exists.

## DASH / hi-res: not supported, by design

DASH playback is intentionally not implemented. The watch's audio goes out over
a Bluetooth codec or a mono speaker, so hi-res DASH would cost bandwidth and
battery with no audible gain, and DASH segment URLs would widen what the stream
policy has to police. `HifiCatalog.resolvePlayable` handles it automatically:
when a quality only yields DASH it steps down (HI_RES → LOSSLESS → HIGH → LOW)
until a plain URL turns up, and if none does it fails with an error naming every
quality tried. Playback defaults to HIGH (AAC), so this rarely triggers. Tested.

## Diagnostics

Settings → **Diagnostics** reports the device, `FEATURE_WEBVIEW`, the active
WebView provider (if any), endpoint reachability, and which audio decoders (AAC,
FLAC, MP3, Opus, E-AC-3) the device has.

## Limitations

- The HiFi contract is read from upstream, which says its own `INSTANCES.md` is
  out of date ("switched away from hifi-api (kinda)"). Both playback routes are
  implemented from upstream's code but untested against a real server.
- Relative URLs in a manifest (`/media/1.m4a`) are dropped on purpose: there is no
  safe base to resolve them against. An endpoint that returns only relative URLs
  fails with "no playable stream".
- Artist, album and track-info screens do not exist yet (the routes are ported).
- No offline caching, accounts or queue management.

## License

Apache-2.0 (matching the upstream Monochrome project).

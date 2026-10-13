# Claude Code continuation prompt — Mono Watch (post-install)

Copy everything below the divider into Claude Code. Run it from
`/home/archlinux/Code/monochrome-watch`.

---

## Where things stand

`/home/archlinux/Code/monochrome-watch` is a **from-scratch native Kotlin/Compose
Wear OS client** for a Samsung Galaxy Watch 4. It ports Monochrome's HiFi route
contract (`/search`, `/info`, `/track`, `/trackManifests/`, `/album`, `/artist`)
into `:core:data`, plays through a Media3 `MediaSessionService` in
`:feature:player`, and points at a **user-supplied** endpoint (no default).

It is **built, unit-tested, and now actually INSTALLED and running on the
physical watch.** Read `README.md` and `docs/DECISION.md` first for the full
rationale. This prompt is about the remaining real-device work and hardening —
**not** another from-scratch decision. Do not re-open the approach choice.

## Verified on the physical device (this already happened — don't redo it unless it broke)

- **Device:** `samsung SM-R870` (Galaxy Watch 4), **API 36** (not API 30 — it
  has been updated). App `targetSdk` is 35; it runs fine.
- **`FEATURE_WEBVIEW`: no**; WebView provider: `none`. Confirmed via the app's
  own Diagnostics screen and `adb shell pm list features`. The native-only
  decision is validated; do not attempt a WebView path.
- **Decoders present:** AAC (`c2.android.aac.decoder`), FLAC
  (`c2.android.flac.decoder`), MP3 (`c2.android.mp3.decoder`), Opus
  (`c2.android.opus.decoder`). **E-AC-3 not available** (fine — HiFi is
  FLAC/AAC-first).
- **UI walked end-to-end via `adb shell input tap` + `uiautomator dump`:** Home
  (*Mono Watch / Now playing / Search / Settings*), Settings (*Remote backend:
  Off — demo tracks*, *HiFi endpoint: not set*, *Diagnostics*), and Diagnostics
  all render. No `FATAL EXCEPTION`; `topResumedActivity` = `.MainActivity`.

## What is NOT yet verified (be honest about this)

- **Real playback.** No endpoint is configured and none is available. The
  `/trackManifests/` → `/track/` fallback, signed-URL handling, and actual
  decoding of a live stream have **never been exercised against a real server**.
  Do not fabricate an endpoint, do not reuse Monochrome's embedded credentials,
  and do not point at community instances.
- DASH (hi-res) is parsed but not supported.
- `EndpointPolicy` currently only constrains the **base** URL; signed manifest
  and stream URLs bypass it (cleartext is allowed globally).
- `HifiImages` hardcodes `resources.tidal.com` for cover art.

## Critical: installing to this watch is painful — read this before you try

The normal debug APK is **~63 MB** (AGP stores `classes*.dex` **uncompressed**),
and the Wi-Fi to the watch is flaky (RTT up to ~380 ms, retransmits, drops
mid-transfer → `adb: connect error for write: device offline`). **Installing the
63 MB APK fails.** Shrinking it by recompressing the dex and re-signing the same
debug key got it to **~17 MB** and it installed with `Success`.

Working recipe (also the thing to turn into a script — see task 1):

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
BT=/home/archlinux/Code/android-sdk/build-tools/35.0.0
SRC=/home/archlinux/Code/monochrome-watch/app/build/outputs/apk/debug/app-debug.apk
W=/tmp/opencode/apkwork
rm -rf "$W"; mkdir -p "$W/c"
cd "$W/c" && unzip -q "$SRC"
rm -rf META-INF
# R+ rules: resources.arsc and lib/*.so MUST stay stored (uncompressed) + aligned
zip -r -9 -q "$W/small.apk" . -x 'lib/*' 'resources.arsc'
zip -r -0 -q "$W/small.apk" lib resources.arsc
cd "$W"
"$BT/zipalign" -f -p 4 small.apk aligned.apk
"$BT/apksigner" sign --ks ~/.android/debug.keystore --ks-pass pass:android \
  --key-pass pass:android --ks-key-alias androiddebugkey --out signed.apk aligned.apk
adb install -r --no-incremental "$W/signed.apk"
```

Gotchas learned the hard way:
- `--no-incremental` avoids the broken incremental path (it produced
  `INSTALL_PARSE_FAILED_NOT_APK` on a dropped transfer).
- Compressing `resources.arsc` or `lib/*.so` fails with
  `INSTALL_FAILED_INVALID_APK: Failed to extract native libraries` /
  `... resources.arsc ... must be stored uncompressed and aligned`.
- **Finding the watch:** adb is `1.0.41` with **no `mdns`**, and the watch uses
  a **randomized MAC** and **ephemeral IP/port** (seen as `192.168.1.12:36107`;
  previously `.6:41879`). Discover it by ping-sweeping the /24, then scanning
  ephemeral ports `32768–60999` on each live host for the adb port:
  ```bash
  for i in $(seq 1 254); do ping -c1 -W1 192.168.1.$i >/dev/null 2>&1 & done; wait
  ip neigh | grep -E 'REACHABLE|STALE'
  # for each live host H:
  for p in $(seq 32768 60999); do \
    (timeout 0.2 bash -c "exec 3<>/dev/tcp/H/$p" 2>/dev/null && echo "OPEN $p") & \
    if (( p % 1000 == 0 )); then wait; fi; done; wait
  adb connect H:PORT
  ```
  Keep the watch **on the charger with the screen on the Wireless debugging
  page** during transfers or its Wi-Fi sleeps and drops.

## Your job

1. **Add a reusable `reinstall.sh`** (and/or a Gradle task) in the project that
   runs `:app:assembleDebug`, applies the shrink+sign recipe above, finds the
   watch, and installs — with clear failure messages. This is the highest-value
   fix because the manual dance is fragile. Keep it out of the APK and document
   it in `README.md`.

2. **Finish end-to-end verification *if and only if* the user supplies an
   endpoint.** Then: set it via Settings → HiFi endpoint, run a Search, play a
   track, and capture logcat. Report which route the endpoint serves
   (`/trackManifests/` vs `/track/`), whether the signed URL loads, and whether
   it decodes. If no endpoint is available, **do not invent one** — instead
   extend the Diagnostics screen if there's a cheap, honest way to show what's
   missing, and write down exactly what remains unverified.

3. **Harden the known risks** (small, well-tested changes):
   - Re-check the **final** resolved (signed/stream) URL against `EndpointPolicy`
     in the resolver, not just the base URL.
   - Make **cover art optional / endpoint-served** instead of hardcoding
     `resources.tidal.com`; a missing image must not break a row.
   - Decide DASH explicitly: either add support or make the degradation to
     BTS/AAC obvious and tested.

4. **Keep the build and tests green:**
   ```bash
   export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
   ./gradlew --offline :app:assembleDebug :core:data:testDebugUnitTest
   ```
   Add tests for anything you change (the resolver/policy changes are pure
   functions — testable, so test them).

## Hard constraints

- Work **only** inside `/home/archlinux/Code/monochrome-watch`. **Do not read,
  modify, or build any other project under `/home/archlinux/Code`** — they are
  unrelated and must stay untouched.
- **Do not** port Monochrome's embedded Tidal client id/secret, **do not** use
  community HiFi instances, and **do not** add a default endpoint.
- Keep changes minimal and consistent with the existing structure/naming/style.
  New dependencies go in `gradle/libs.versions.toml`.
- `upstream/` is the Monochrome source for reference only — never copy its
  credentials or ship its assets.

## Output format

1. **Changes** — `path:line` and a one-line why for each.
2. **New tooling** — how to run `reinstall.sh` (or the Gradle task), with the
   exact command.
3. **Verification** — the actual `BUILD SUCCESSFUL` / test result, and (if you
   had an endpoint) the observed route + playback outcome. Paste real output;
   do not paraphrase a pass you did not see.
4. **Still unverified** — be blunt, and give the top 3 next steps.

# Claude Code verify prompt — Mono Watch (hardening round review + on-device install)

Copy everything below the divider into Claude Code. Run it from
`/home/archlinux/Code/monochrome-watch`.

---

## Context — what just happened

A previous Claude session completed the post-install hardening round on this
project and reported it **done**: a new `scripts/reinstall.sh`, resolved-URL
policy checks, an opt-in "Album art" setting, a DASH quality step-down, README
updates, and `BUILD SUCCESSFUL` with 61 tests / 0 failures.

Read `README.md` + `docs/DECISION.md` first. Your job is **verify and finish** —
not re-do the round. Do not trust the previous session's report or this file's
claims; check everything yourself and report what you actually saw.

Facts another agent *did* independently re-confirm (no need to re-litigate, but
you may):
- Gradle state is consistent (`:app:assembleDebug :core:data:testDebugUnitTest`
  → `BUILD SUCCESSFUL`, all tasks up-to-date, exit 0).
- The 61-test count matches the real result XMLs in
  `core/data/build/test-results/testDebugUnitTest/` (8 suites, 0 failures).

**The big gap: the new APK has never been installed on the watch**, and the
on-device findings from the earlier session came from the *previous* build.

## Task 1 — Fresh build + tests (force a real run)

Cached `UP-TO-DATE` results prove nothing. Re-run for real and paste the raw
tail of the output:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew --offline :core:data:testDebugUnitTest --rerun-tasks --console=plain
./gradlew --offline :app:assembleDebug --console=plain
```

Report the actual test total (compare with the XMLs) and the APK size.

## Task 2 — Install the new build on the watch and walk it

Use the new script — that's the point of it:

```bash
scripts/reinstall.sh --connect <ip>:<connect-port>   # or: scripts/reinstall.sh --scan <ip>
```

Watch facts learned the hard way:
- The watch uses a **randomized MAC and ephemeral IP/port** and adb 1.0.41 has
  **no `mdns`**. Last seen at `192.168.1.12:36107` (pairing port was `42875`);
  it **will change** if Wireless debugging toggles or the watch rejoins Wi-Fi.
  Re-discover: ping-sweep the /24, then `scripts/reinstall.sh --scan <ip>` for
  the ephemeral port. The user can read the exact "IP address & Port" off the
  watch's Wireless debugging screen.
- **Keep the watch on the charger with the screen on the Wireless debugging
  page** during transfer or Wi-Fi sleeps → `device offline`.
- The plain 63 MB debug APK fails to install on this flaky link; the script's
  shrink+re-sign is required. `--no-incremental` is already handled.

Then verify on-device (screenshots are fine for your report, but also do
`adb shell uiautomator dump` + `logcat -d` and quote the text):
1. App launches; `topResumedActivity` = `.MainActivity`; **no `FATAL EXCEPTION`**.
2. Home renders (Now playing / Search / Settings).
3. Settings shows the new **"Album art"** toggle defaulting to **Off** with
   secondary label "Off (no 3rd-party requests)". Toggle it on and back off;
   confirm the UI updates and the setting **persists** across a process restart
   (`am force-stop` + relaunch).
4. Diagnostics screen still reads correctly (device, `FEATURE_WEBVIEW: no`,
   decoders AAC/FLAC/MP3/Opus, E-AC-3 not available).

Best-effort check that covers-off makes no image-CDN requests: with covers OFF,
play a demo track and watch logcat for any `resources.tidal.com` / image fetch
attempts. If you can't prove it from logcat, say so plainly.

## Task 3 — Review the hardening changes (fix what is clearly wrong)

Follow the previous `REVIEW_PROMPT.md` style (severity / `path:line` / problem /
fix). Specific things to scrutinize:

1. **`HifiCatalog.resolvePlayable`** — it catches only `HifiException` when
   stepping down; network/HTTP errors fail fast (documented as intended — agree
   or disagree). When every quality is DASH, the final message names the **last
   (lowest)** quality, e.g. "Only a DASH manifest at LOW (unsupported)" — is
   that misleading enough to fix (report the highest DASH quality instead)?
   Check `dropWhile { it != quality }` against `HifiQuality.entries` order.
2. **Policy application** — `checkResolvedUrl` runs in `HifiUri.resolve` (final
   URL) and in `viaTrackManifests` (signed manifest link) but **not** on the
   `/track/` Direct URL before it leaves `HifiCatalog` (deferred to
   `HifiUri.resolve`). Confirm that is safe in every call path, including
   `resolvePlayable` used by `PlaybackService`. Verify a public-host `http://`
   stream URL is refused end-to-end.
3. **`StreamManifest.pickUrl`** — drops anything that isn't an absolute
   `http(s)` URL. Some endpoints may hand back **relative** URLs; decide whether
   that is acceptable or worth a documented limitation (do not add a resolver
   without a test).
4. **Cover art plumbing** — `TrackMapping` sets artwork only when non-null;
   `NowPlayingScreen` guards blank; search rows show no art at all. Confirm
   covers-off truly prevents any CDN request, and covers-on with a dead CDN
   cannot crash a row (AsyncImage error handling / placeholder).
5. **`scripts/reinstall.sh`** — re-read it. Nit to check: device-state check
   takes `adb devices | head -1` (wrong if an emulator/second device is
   attached); `adb reconnect` in the retry loop; `set -euo pipefail` behavior
   around `tee`. Keep the `--scan` single-host opt-in as is.
6. **README** — does it match the implemented behavior, including "covers off by
   default" and the policy side effect (a CDN handing out `http://` stream URLs
   on a public host is now refused)?

Keep fixes minimal and tested. Pure logic changes (policy, manifest, resolver)
must come with unit tests. New deps go in `gradle/libs.versions.toml`.

## Task 4 — Live playback (only if an endpoint is supplied)

If the user has given you a HiFi endpoint they're entitled to use: set it in
Settings → HiFi endpoint, run a Search, play a track, and capture logcat.
Report which route served it (`/trackManifests/` vs `/track/`), whether the
signed URL loaded and passed the policy, and whether it decoded. **If no
endpoint is available, do not invent one, do not reuse Monochrome's embedded
credentials, do not use community instances.** Just record playback as
unverified.

## Hard constraints

- Work **only** inside `/home/archlinux/Code/monochrome-watch`. Do not read,
  modify, or build any other project under `/home/archlinux/Code`.
- No porting of Monochrome's Tidal client id/secret; no community HiFi
  instances; no default endpoint.
- Keep changes consistent with existing style; `upstream/` is reference-only.

## Output format

1. **Verification** — raw Gradle tail, test totals, APK size.
2. **On-device result** — install output (`Success`), every UI check with the
   text you dumped, logcat proof of no crash, and the cover-request check
   (or "could not prove").
3. **Review findings** — severity/location/problem/fix table; what you changed
   and what you deliberately left.
4. **Still unverified** — be blunt; top 3 next steps.
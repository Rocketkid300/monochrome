# Claude Code release-build prompt — Mono Watch (small APK + DASH decision + final docs)

Copy everything below the divider into Claude Code. Run it from
`/home/archlinux/Code/monochrome-watch`.

---

## Where things stand

Mono Watch is functionally complete and code-reviewed. Verified (not by you —
confirm anything you rely on, but do not redo completed work):

- **63 unit tests / 0 failures** on a forced `--rerun-tasks` run; 8 suites.
- **Debug APK is ~63 MB** because AGP stores dex uncompressed. A previously
  shrunk+re-signed copy (~18 MB) **installed `Success` on the physical watch**
  and the UI was walked: Home renders, Settings shows the **"Album art"** toggle
  defaulting to **Off** with the honest label *"Off (no cover requests)"*, and
  toggling to On shows *"On (loads from image CDN)"*. The off-by-default covers,
  the `enforce()` stream-policy checks (all three paths), the DASH step-down,
  the improved `scripts/reinstall.sh`, and the fixed Settings label are all in
  the code and covered by tests.
- The watch's Wi-Fi is **flaky** (randomized MAC, ephemeral IP:port, drops
  mid-transfer). **The APK size is the remaining pain point** — that is this
  round's target.

**Do not attempt any adb/device work in your session — you have no adb access
and the device session owns that.** Your job is code + build + docs.

## Task 1 — A small, installable release APK (highest value)

`app/build.gradle.kts` already declares a `release` build type with
`isMinifyEnabled = true`, `isShrinkResources = true`, and a `proguard-rules.pro`
— but it has **no signing config**, so `assembleRelease` yields an unsigned APK
and nobody uses it. Fix that:

1. Add `signingConfig = signingConfigs.getByName("debug")` to the `release`
   build type (this is a personal-use app; the debug keystore is already the one
   used everywhere here). Document why in a comment.
2. **Verify R8 completeness for real** by building:
   ```bash
   export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
   ./gradlew --offline :app:assembleRelease --console=plain
   ```
   The app uses kotlinx-serialization, Retrofit/OkHttp, Coil, Hilt,
   androidx.media3 (session + proxy/playback), and Compose for Wear. Expect the
   shrinker to need rules; fix `proguard-rules.pro` until the build succeeds and
   is **not obviously broken** (e.g., no reflection-based classes stripped that
   the code clearly needs). A build that succeeds is not proof the APK runs —
   say so plainly. You may not run it on the device.
3. Report sizes: `app/build/outputs/apk/release/app-release.apk` vs the ~63 MB
   debug APK. If R8 yields something small (target: well under 20 MB), adapt
   `scripts/reinstall.sh` to install the **release** APK by default:
   - `--variant debug|release` (default `release`).
   - Keep the existing shrink+re-sign fallback for `debug`.
   - The shrink step should be **skipped** when installing release (R8 already
     compressed it); keep alignment/signing if the release APK is already
     properly signed by Gradle.
   - Update the script header usage block and `--help` text to match.
4. If R8 cannot produce a sound small APK (state why), fall back to extending
   the debug-shrink path and report that decision.

## Task 2 — DASH: make a firm decision (not an open question)

Today `resolvePlayable` steps quality down and fails clearly when only DASH
remains; `media3-exoplayer-dash` is NOT a dependency. Two acceptable outcomes:

- **A. Support DASH:** add the dependency, teach `StreamSource.Dash` to resolve
  to a real DASH URL, feed it to ExoPlayer. This means resolving the MPD URL
  (not raw XML) and passing `checkResolvedUrl`. Add unit tests for the new
  resolution path.
- **B. Confirm the step-down as design:** document in `README.md` and in the
  `HifiCatalog` kdoc that DASH playback is intentionally not supported (watch
  hardware / FLAC-first) and that `resolvePlayable` degrades automatically.

Pick one, implement it, and make the docs say the decision decisively. Do not
leave "decide whether DASH is worth adding" as an open TODO.

## Task 3 — Final docs

Update `README.md` and `docs/DECISION.md` to match reality exactly:
- The install workflow: release APK by default, shrink fallback, the watch must
  be awake on the Wireless debugging page (Wi-Fi sleeps otherwise).
- The on-device verification achieved so far (launch, Home, Album-art default
  Off + label, toggle behavior) and what remains (Diagnostics walk, demo-play
  check, persistence across `force-stop`, covers-off logcat probe, live
  playback without an endpoint).
- The covers and stream-policy behaviors (cleartext refused to public hosts).
- Anything in the current README that is stale — fix it; flag anything you
  cannot confirm.

## Constraints & output format

- Work only in `/home/archlinux/Code/monochrome-watch`; do not read/modify/build
  any other project under `/home/archlinux/Code`. `upstream/` is reference-only;
  never copy credentials. No community instances; no default endpoint.
- New dependencies go in `gradle/libs.versions.toml`. Keep style consistent.
- Keep the full test suite green; if you touch pure logic, add/adjust tests.
- Report: sizes before/after, exact `proguard-rules.pro` changes with reasons,
  the DASH decision and why, docs changes, and exactly what remains unverified
  (be blunt — "the release APK has not run on a device; R8 success ≠ runtime
  proof").
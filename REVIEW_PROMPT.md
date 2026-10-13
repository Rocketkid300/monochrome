# Claude Code review prompt — Mono Watch

Copy everything below the line into Claude Code, run from `/home/archlinux/Code/monochrome-watch`.

---

You are reviewing a brand-new Android project. Work only inside
`/home/archlinux/Code/monochrome-watch`. **Do not read, modify, or build anything
in `/home/archlinux/Code/GalaxyWatchCalculatorVault` or any other project in
`/home/archlinux/Code`** — those are unrelated and must stay untouched. You may
read them only if you need to confirm a version number, and even then do not edit.

## What this project is

A native Wear OS music player for a Samsung Galaxy Watch 4 (Wear OS 3, API 30+,
450x450 round display). It is a from-scratch client inspired by Monochrome
(https://github.com/monochrome-music/monochrome), a JavaScript/Vite web music
player. Monochrome's streaming API only accepts official instances and a
self-hosted copy cannot stream without a private HiFi endpoint, so this app
defaults to a bundled demo catalog and offers a remote backend plus a
Diagnostics screen (the earlier experimental WebView screen was removed).

Modules:
- `:app` — Wear entry point, navigation, permissions, notification permission.
- `:core:data` — `Track`, `MusicCatalog`, `DemoCatalog`, `MonochromeCatalog`
  (Retrofit), `CatalogRepository`, `SettingsStore`, Hilt `DataModule`.
- `:feature:player` — `PlaybackService` (Media3 `MediaSessionService` +
  ExoPlayer), `PlayerViewModel` (owns a `MediaController`), Wear Compose screens.

Key files to read:
- `gradle/libs.versions.toml`, `settings.gradle.kts`, all `build.gradle.kts`
- `core/data/src/main/kotlin/io/github/rocketkid300/monochromewatch/core/data/**`
- `feature/player/src/main/kotlin/io/github/rocketkid300/monochromewatch/feature/player/**`
- `feature/player/src/main/AndroidManifest.xml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/kotlin/io/github/rocketkid300/monochromewatch/MainActivity.kt`
- `README.md`

## Your task

Review the code for correctness and build it. Then report findings.

1. Establish a baseline first:
   ```bash
   export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
   ./gradlew :app:assembleDebug :core:data:test
   ```
   If it fails, treat the first compile error as finding #1 and keep going —
   report every distinct error you can find by reading code, do not just stop.

2. Review specifically for these risks (this is not an exhaustive list):

   **Media3 / playback (highest priority)**
   - `PlaybackService`: is the `MediaSession`/`ExoPlayer` created and released
     correctly? Is `onTaskRemoved` correct? Is the service's foreground type and
     manifest declaration correct for API 30–35?
   - `PlayerViewModel`: is the `MediaController` connection lifecycle correct
     (connect on init, `releaseFuture`/`release` on clear, listener removal)?
     Are `MediaController` calls made on the right thread? `viewModelScope`
     runs on `Dispatchers.Main.immediate` — confirm that is sufficient.
   - Is the 1-second progress ticker in `init` wasteful when nothing is playing?
     Propose a cheaper update strategy.
   - Does playback survive the UI process being backgrounded? Does the
     notification get a valid small icon without `media3-ui`?
   - `setWakeMode`, `setHandleAudioBecomingNoisy`, audio attributes: correct?

   **Correctness / bugs**
   - `CatalogRepository` swallows `Throwable`, which also catches
     `CancellationException`. Is that a real problem here? Propose the minimal fix.
   - `MonochromeCatalog` caches a Retrofit instance keyed by base URL and mutates
     fields without synchronization. Is that safe given how it is used?
   - `TrackDto.toTrack()` id/url fallbacks: any way to produce an unusable
     `Track` (blank `streamUrl`)?
   - `PlayerViewModel.publish` reads `player.duration`; consider `C.TIME_UNSET`.

   **Compose for Wear OS 1.4.1 API correctness**
   - Verify every Wear component used actually exists with the parameters passed
     in version 1.4.1: `Scaffold`, `TimeText`, `ScalingLazyColumn` +
     `rememberScalingLazyListState` + `items(count)`, `Chip` (`label`,
     `secondaryLabel`), `ToggleChip`, `Slider` (`valueRange`), `IconButton`,
     `Icon`, `Text` (`textAlign`), `MaterialTheme.typography.title2/body2/caption1`.
     Flag anything that moved or does not exist.
   - Round-display concerns: content clipping on the 450x450 circle, `TimeText`
     overlap, and whether the Now Playing `Column` fits.

   **Hilt / DI**
   - Are all bindings satisfiable (both catalogs, Json, OkHttpClient,
     MusicCatalog, SettingsStore)? Any duplicate-binding or missing-binding risk?
   - `@AndroidEntryPoint` on the `MediaSessionService` subclass: supported?

   **Manifest / permissions / platform**
   - `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `WAKE_LOCK`,
     `INTERNET`: present, correct, and correctly gated by API level?
   - Starting the media foreground service from an API 34+ background context.
   - `android:allowBackup` / `DataExtractionSafety` and any security notes.

   **Networking / release**
   - Retrofit + kotlinx-serialization converter usage is correct for the pinned
     versions. Any R8/ProGuard rules needed (serialization, Retrofit, Media3)?
   - `HttpLoggingInterceptor` at BASIC is fine, but confirm it is not leaking
     headers.

   **Code quality**
   - Module boundaries, naming, dead code, `README` accuracy vs. reality.
   - Test coverage gaps (there are currently tests only for `DemoCatalog` and
     `TrackDto` mapping).

3. Make the fixes you are confident about, then re-run the build and tests. Keep
   changes minimal and consistent with the existing style. Do not add
   dependencies unless necessary, and if you do, add them to
   `gradle/libs.versions.toml`.

## Output format

For each issue:
- **Severity**: BLOCKER / HIGH / MEDIUM / LOW / NIT
- **Location**: `path:line`
- **Problem**: one or two sentences
- **Fix**: what you changed, or why you left it alone

Then a short verdict: what still does not work or is unverifiable, and the top 3
things to do next. Be blunt about uncertainty — do not claim something is fixed
unless the build/tests confirm it.

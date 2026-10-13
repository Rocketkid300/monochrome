# Claude Code decision prompt — Monochrome on Galaxy Watch 4

Copy everything below the divider into Claude Code. Run it from `/home/archlinux`.

---

## Your job

Make a technical decision and then act on it. The user wants to **fork Monochrome
(a web music player) and turn it into an app for their Samsung Galaxy Watch 4**
(Wear OS 3, API 30+, 450x450 round, touch-only).

You must:
1. Independently verify the facts below (do not take them on faith — clone and
   inspect the real repo, and check the platform constraints).
2. Choose ONE approach and justify it against the alternatives.
3. Produce a concrete, ordered implementation plan (files, modules, dependencies,
   risks). Start implementing only after the decision is stated.

Be decisive. If the most honest answer is "you cannot literally fork it onto the
watch; here is the closest realistic thing," say that plainly and pick that.

## Hard constraints

- Work only inside `/home/archlinux/Code/monochrome-watch` and, for cloning
  upstream, a subdirectory such as `/home/archlinux/Code/monochrome-watch/upstream/`.
- **Do not read, modify, or build `/home/archlinux/Code/GalaxyWatchCalculatorVault`
  or any other project in `/home/archlinux/Code`.** They are unrelated.
- Android: JDK 17 (`/usr/lib/jvm/java-17-openjdk`), SDK at
  `/home/archlinux/Code/android-sdk`, Gradle 8.9 (cached), target the Watch 4.
- If you add dependencies, add them to `gradle/libs.versions.toml`.

## What already exists (built by another agent)

`/home/archlinux/Code/monochrome-watch` is a **from-scratch native Kotlin/Compose
Wear OS client** that reuses Monochrome's *concept and backend*, not its code:

- `:app` — Wear entry point, navigation, permissions.
- `:core:data` — `Track`, `MusicCatalog`, `DemoCatalog`, `MonochromeCatalog`
  (Retrofit, **placeholder endpoints**), `CatalogRepository`, `SettingsStore`.
- `:feature:player` — `PlaybackService` (Media3 `MediaSessionService`), 
  `PlayerViewModel` (owns a `MediaController`), Wear Compose screens
  (Browse / Now Playing / Settings / Diagnostics screens).

Two unit tests exist (`DemoCatalog`, `TrackDto` mapping). The user is not satisfied
that this is a "fork" — they expected the actual Monochrome code to be reused.

## Facts to verify (and correct if wrong)

1. **Monochrome is a web app.** Repo: https://github.com/monochrome-music/monochrome
   (forked from edideaur/monochrome; Apache-2.0). Stack: TypeScript/JavaScript,
   Vite, Bun, PWA. No Android/Kotlin/Wear code.
2. **Wear OS does not support WebView.** Google's docs for
   `WebViewCompat.getCurrentWebViewPackage()` state it can return null for a
   device that *"doesn't support using WebView, such as a Wear OS device"*
   (https://developer.android.com/develop/ui/views/layout/webapps/managing-webview).
   Verify whether Galaxy Watch 4 / Wear OS 3 specifically ships any WebView
   provider, and whether there is any sanctioned workaround.
3. **The backend is gated.** Monochrome's README states a self-hosted copy cannot
   stream; its API only accepts official instances unless you provide your own
   paid HiFi API endpoint (Settings > Instances > Dev Mode).

## Candidate approaches (choose, or propose better)

A. **Fork the repo and port its real logic** into the Kotlin client — read
   Monochrome's actual search/stream code and replace the placeholder Retrofit
   endpoints and DTOs with what the code really calls. "Fork as source."
B. **Fork for phone, watch as remote** — adapt/run Monochrome on an Android
   phone, and make the watch app a controller for it. Most faithful to reusing
   their code, and it sidesteps the WebView problem.
C. **Keep the native client and wire it to a real/self-hosted instance** — least
   work; no code reuse.
D. **WebView wrapper** — attempt to run the web app in a WebView on the watch.
   Probably impossible on Wear OS; only pursue if you can prove a provider exists
   on this device.

Evaluate each on: does it actually reuse Monochrome's code, does it work on a
Watch 4, effort, and legal/ToS risk (the catalog is Tidal-backed and legally
gray; the user should not distribute a piracy client).

## Deliverable

1. **Decision**: the chosen approach, in one paragraph, with the reasoning and why
   the others were rejected.
2. **Verification**: what you actually checked (commands run, files read) and what
   you could not verify.
3. **Plan**: ordered steps with the specific files/modules to create or change.
4. **Risks / unknowns**: especially anything you cannot test without the physical
   watch.
5. Then begin step 1 of the plan and report the result.

Do not stop at analysis if the decision is clear — implement the first step.

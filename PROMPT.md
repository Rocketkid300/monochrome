# Prompt for Claude Code

Copy everything below the divider. Run it from `/home/archlinux`.

---

# Idea

I want to take **Monochrome** (https://github.com/monochrome-music/monochrome) —
an open-source, ad-free, privacy-respecting music player — fork it, and turn it
into an app I can use on my **Samsung Galaxy Watch 4**.

Build me that app. Figure out the best realistic way to do it, explain your
reasoning, and then start building.

# The device

- **Samsung Galaxy Watch 4** (Wear OS 3 / One UI Watch, Android 11 based, API 30+)
- 450x450 round AMOLED, ~340 dpi, ~212x212 dp
- **Touch only** — no rotating bezel or crown on this model
- **No web browser**

# What Monochrome actually is

- An open-source **web app / PWA**, written in **JavaScript/TypeScript**, built
  with **Vite**, using **Bun**. It runs in a browser.
- Features: high-res/lossless streaming, local files, offline PWA, Media Session
  API, themes, lyrics, library sync, Docker self-hosting. Apache-2.0 licensed.
- **Streaming backend is gated:** its README says a self-hosted copy cannot
  stream — the API only accepts official instances unless you supply your own
  paid HiFi API endpoint (Settings > Instances > Dev Mode).
- The catalog it streams is Tidal-backed and legally gray. Keep any build
  personal / self-hosted; do not plan to distribute a piracy client.

# The core problem you must solve

The obvious plan — fork Monochrome and run it on the watch — **does not work
directly**, and you need to confirm this yourself and then route around it:

1. Monochrome is **web code** (JS/HTML/CSS). There is **no Android/Kotlin/Wear
   code in it to fork into a watch app.**
2. The watch has no browser, and **Wear OS does not support WebView** — Google's
   own docs say `WebViewCompat.getCurrentWebViewPackage()` can return null for a
   device that *"doesn't support using WebView, such as a Wear OS device"*
   (https://developer.android.com/develop/ui/views/layout/webapps/managing-webview).
   Verify whether the Galaxy Watch 4 specifically ships any WebView provider and
   whether any workaround exists.
3. The streaming API is restricted and may break at any time.

So "fork it and put it on the watch" is not a real path. Your job is to find the
**closest realistic thing that actually works on a Watch 4**, and build it.

# Approaches to weigh (pick one, or propose better)

A. **Port its real logic** into a native Kotlin/Compose Wear OS client — read
   Monochrome's actual search/stream/auth code and reproduce what it really calls,
   instead of guessing. "Fork as source."
B. **Fork for the phone, watch as remote** — run/adapt Monochrome on an Android
   phone and make the watch a controller. Most faithful to reusing the code and
   avoids the WebView problem.
C. **Native client against a real/self-hosted instance** — least code reuse, most
   reliable.
D. **WebView wrapper** — only if you can prove a WebView provider exists on this
   device. Otherwise reject it.

Score each on: does it actually reuse Monochrome's code, does it work on a
Watch 4, effort, maintainability, and legal/ToS risk.

# Constraints

- Build in `/home/archlinux/Code/monochrome-watch` (create it if needed). For
  inspecting upstream, clone into a subfolder like `upstream/`.
- **Do not read, modify, or build any other project under `/home/archlinux/Code`.**
  They are unrelated and must stay untouched.
- Android toolchain on this machine: JDK 17 at `/usr/lib/jvm/java-17-openjdk`,
  SDK at `/home/archlinux/Code/android-sdk`, Gradle 8.9 (cached). Target the
  Watch 4 (minSdk 30, targetSdk 35).
- Prefer verified, working code over theory. If a dependency or API is uncertain,
  say so.

# Deliverable

1. **Decision** — the approach you chose, in one paragraph, and why the others
   were rejected. Be blunt if the honest answer is "you can't literally fork it
   onto the watch; here's the closest real thing."
2. **Verification** — what you actually checked (commands run, files/repos read),
   and what you could not verify without the physical watch.
3. **Plan** — ordered implementation steps with specific files/modules.
4. **Risks / unknowns.**
5. **Start step 1** of the plan and report the result.

Do not stop at analysis if the decision is clear — begin building.

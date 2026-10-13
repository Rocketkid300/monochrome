# Mono on iPhone (no jailbreak)

Both options load the upstream Monochrome web UI (`upstream/`) from a server you host.
You still supply your own HiFi-API endpoint inside the web UI's settings.

## A. PWA (Add to Home Screen)

1. `scripts/build-web.sh` builds `upstream/dist`.
2. Serve `dist/` over **HTTPS** (iOS will not install a PWA from plain http):
   - Tailscale: `cd upstream && npx vite preview --host &` then
     `tailscale serve --bg --https=443 http://localhost:4173`
   - or Cloudflare Pages / GitHub Pages / any nginx (`upstream/nginx.conf`, `upstream/DOCKER.md`).
3. iPhone Safari -> open the URL -> Share -> **Add to Home Screen**.

Caveat: iOS may pause PWA audio when the screen locks. If so, use B.

## B. Sideloaded IPA (native background audio)

`ios/` is a tiny SwiftUI app that opens the official Monochrome site (https://monochrome.st) in a WKWebView,
with `AVAudioSession.playback` + the `audio` background mode. Nothing to host.

1. Download `Monochrome.ipa` from Releases (or run the **iOS IPA** workflow).
2. Install with **Sideloadly** or **AltStore** using your Apple ID (free ID: re-sign every 7 days).
3. Optional: iOS **Settings -> Monochrome -> Server URL** points the app at a different Monochrome instance.

Needs internet (it is a streaming site). Not tested on a physical device by the author.

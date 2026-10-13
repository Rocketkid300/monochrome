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

## B. Sideloaded IPA (self-contained, native background audio)

`ios/` is a tiny SwiftUI app. CI builds the upstream web UI and **bundles it inside the IPA**, shown in a
WKWebView (served from `mono://app/`), with `AVAudioSession.playback` + the `audio` background mode.
**No web hosting needed.** You only enter your HiFi endpoint inside the Monochrome UI.

1. Download `Monochrome.ipa` from Releases (or run the **iOS IPA** workflow).
2. Install with **Sideloadly** or **AltStore** using your Apple ID (free ID: re-sign every 7 days;
   paid developer account: 1 year).
3. Optional: to load a hosted web UI instead, set iOS **Settings -> Monochrome -> Server URL**.

Not tested on a device. Bundled mode needs your HiFi endpoint to allow cross-origin requests (the same
requirement as the hosted web app).

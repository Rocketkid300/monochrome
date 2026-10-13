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

## B. Sideloaded IPA (WKWebView wrapper, native background audio)

`ios/` is a tiny SwiftUI app that shows your hosted web UI in a WKWebView with
`AVAudioSession.playback` + the `audio` background mode.

1. Put this project on GitHub (the repo is not a git repo yet: `git init`, push).
2. Actions -> **iOS IPA** -> Run workflow (optionally enter your HTTPS server URL).
3. Download the `Monochrome-ipa` artifact (contains the unsigned `Monochrome.ipa`).
4. Install with **Sideloadly** or **AltStore** using your Apple ID (free ID: expires every
   7 days, re-sign weekly; paid developer account: 1 year). They sign it for you.
5. Server URL: if not baked in at build time, the app prompts on first launch; change it later in
   iOS **Settings -> Monochrome -> Server URL**.

Not tested on a device: this machine has no Xcode, so the IPA has never been built or run.
Expect to fix small compile issues on the first CI run.

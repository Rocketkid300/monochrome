# Monochrome for iPhone

Runs the [Monochrome](https://github.com/monochrome-music/monochrome) web UI (`upstream/`) on iPhone
**without a jailbreak**, either as a PWA or as a sideloaded IPA with native background audio.
You host the web UI over HTTPS and supply your own HiFi-API endpoint inside it.

- `upstream/` – Monochrome web UI (Vite PWA)
- `ios/` – SwiftUI + WKWebView wrapper (XcodeGen project)
- `.github/workflows/ios.yml` – builds an unsigned `Monochrome.ipa` on a macOS runner
- `scripts/build-web.sh` – builds `upstream/dist`

**Install:** grab `Monochrome.ipa` from [Releases](../../releases) and sideload it with Sideloadly or AltStore. More detail in [docs/IOS.md](docs/IOS.md).

The Galaxy Watch app lives in a separate repo: `monochrome-watch`.

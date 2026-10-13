# Claude Code on-device prompt — Mono Watch (install + UI walk, final round)

Copy everything below the divider into Claude Code. Run it from
`/home/archlinux/Code/monochrome-watch`.

---

## Where things stand — code is done; the physical round is the only gap

The hardening round is complete and independently verified by another agent
(`scripts/reinstall.sh` with `--serial` selection; policy enforcement via
`enforce()` on `/track/`, `OriginalTrackUrl`, and manifest paths; the DASH
step-down listing every quality tried; "Album art" setting defaulting to off;
honest Settings label; relative-URL limitation documented). A forced run of the
tests just passed: **`--rerun-tasks` → `BUILD SUCCESSFUL`, 63 tests across 8
suites, 0 failures, 0 skipped** (result XMLs timestamped from that run). The
debug APK is ~63 MB; the shrunk+re-signed APK is ~18 MB.

**Do not rebuild, do not re-run the test suite, and do not re-review the code
unless you change something.** Your job now is purely on the physical watch.

## Device facts (learned the hard way)

- The watch is currently **on the 192.168.1.0/24 network** but its adb port is
  **closed at scan time** (its Wireless debugging port changes every time the
  setting toggles, and it sleeps the radio when idle). Its Wi-Fi MAC is
  randomized (`56:50:4a:83:73:85`), so it is identified by IP + port, not MAC.
- adb is `1.0.41` with **no `mdns`**; auto-discovery does not work.
- The unshrunk 63 MB APK will keep failing to install on this link; always use
  `scripts/reinstall.sh`, which shrinks, re-signs with the debug key, and
  installs with retries. `--no-incremental` is handled inside the script.

**Ask the user (this is the unlock, not a failure):** put the watch on the
charger, ensure **Settings → Developer options → Wireless debugging** is ON,
keep the **screen on the Wireless debugging page** (it must stay awake during
the transfer), and read the exact **"IP address & Port"** line.

Then install with:
```bash
scripts/reinstall.sh --connect <ip>:<port>
# fallback if you know the IP but not the port:
scripts/reinstall.sh --scan <ip>
```

## Task A — Install and walk the UI (the deliverable)

1. Install via `scripts/reinstall.sh --connect <ip>:<port>`; report the raw
   `Success`/failure output and the APK sizes.
2. Launch `am start -n io.github.rocketkid300.monochromewatch/.MainActivity`;
   confirm `topResumedActivity` = `.MainActivity`, **no `FATAL EXCEPTION`** in
   logcat, and grab `uiautomator dump` text for each screen:
   - Home: *Mono Watch / Now playing / Search / Settings*.
   - Settings: **"Album art" toggle defaults to Off**, label "Off (no cover
     requests)". Toggle On → label "On (loads from image CDN)" → toggle back.
     **Prove persistence:** `adb shell am force-stop
     io.github.rocketkid300.monochromewatch`, relaunch, confirm it is still Off.
   - Diagnostics: device line, `FEATURE_WEBVIEW: no`, decoders AAC/FLAC/MP3/Opus
     present, E-AC-3 not available.
3. **Playback pipeline:** play a **demo** track (no hifi:// URI involved) and
   confirm: playback starts (position advances), the media notification / media
   session appears, and no crash. This exercises `PlaybackService` +
   `PlayerViewModel` on the real device, which has never been done on this build.
4. **Cover-request check (best effort):** with covers Off (which is default),
   after the walk check logcat for any attempt to reach `resources.tidal.com` /
   image CDN hosts. If you cannot prove it from logcat, say exactly that — do
   not claim it.

Do not exceed a couple of screen interactions per step; the watch is touch-only
and slow. Keep it on the charger and awake throughout.

## Task B — Live playback (only if the user also supplies an endpoint)

If the user gives you a HiFi endpoint they are entitled to use AND the watch is
connected: set it via Settings → HiFi endpoint, toggle Remote backend on, Search
for a track, play it, and capture logcat. Report: which route served
(`/trackManifests/` vs `/track/`), whether the signed URL loaded and passed the
policy, and whether it decoded (which decoder). **If no endpoint is supplied,
record playback as unverified and stop there.** Never invent an endpoint, reuse
Monochrome's embedded credentials, or touch community instances.

## Output format

1. **Install** — raw output + sizes.
2. **UI walk** — each screen's dumped text, toggle-persistence proof, logcat
   excerpt for "no crash".
3. **Playback** — what you observed on the demo track.
4. **Cover-request check** — proven or "could not prove".
5. **Live playback** — result or "unverified, blocked on endpoint".
6. **Blocked?** — if the device is unavailable, say exactly what information and
   user action you need, and nothing else is outstanding.
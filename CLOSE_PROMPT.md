# Claude Code close-out prompt — Mono Watch (acceptance round)

Copy everything below the divider into Claude Code. Run it from
`/home/archlinux/Code/monochrome-watch`.

---

## Acceptance — the code/build/docs round is done, and an independent agent verified it

Read `README.md` and `docs/DECISION.md` (both were rewritten last round) to
orient yourself, then confirm the following independently (do not redo the work,
just confirm the artifacts exist and match):

- **APK** — `app/build/outputs/apk/release/app-release.apk` is **~4.2 MB**
  (debug APK ~63 MB), R8-minified + resource-shrunk, **signed with the Android
  Debug certificate**, and `apksigner verify` passes.
- **Signing** — `app/build.gradle.kts` release build type wires
  `signingConfig = signingConfigs.getByName("debug")`.
- **Installer** — `scripts/reinstall.sh` defaults to `--variant release`
  (no re-packing; alignment + signature checks), keeps `--variant debug`
  (shrink + re-sign), validates the variant, and has `--serial`/`ANDROID_SERIAL`
  device selection.
- **Tests** — 63 tests / 0 failures across 8 suites.
- **ProGuard** — the explicit `JsonElement`/`Companion` keep rule is present in
  `app/proguard-rules.pro` and lands in R8's configuration/seeds.
- **DASH decision** — recorded as "not supported, by design" (step-down), in
  `HifiCatalog`, `StreamSource.Dash`, README and DECISION doc.

If any of those do not hold, fix them narrowly. If they all hold, **stop**.

## Your standing order

Make **no further code, build, or doc changes**. The only work that remains is
physical-device verification and live playback, and both are outside your
reach (you have no adb access; the adb-capable session owns the watch; live
playback needs an endpoint the user must supply).

**The one caveat to keep on record:** the release APK's R8 output was inspected
via checksums, seeds, mapping and dexdump, but **has never executed on a
device**. A clean build is not runtime proof. That proof is the device
session's job, in this order:

1. Install the release APK with `scripts/reinstall.sh --connect <ip>:<port>` —
   ~4.2 MB should transfer in seconds even on the watch's weak Wi-Fi.
2. Launch; confirm no `FATAL EXCEPTION`; dump Home/Settings/Diagnostics text.
3. Confirm the "Album art" setting survives `am force-stop` + relaunch.
4. Play a demo track; confirm the player advances and no crash.
5. Best-effort: logcat shows no `resources.tidal.com` requests with covers off
   (say "could not prove" if logcat can't show it).
6. Only if the user supplies a HiFi endpoint: set it, search, play, and report
   which route served plus whether it decoded.

**When do you act again?** Only if the device session reports a concrete
runtime failure of the release APK (crash, missing class/method, dead UI). Then:
fix narrowly, rebuild release, re-run the forced test suite, and state exactly
what failed vs. what you changed. Otherwise, produce the acceptance artifact
below and stop.

## Output format

A single acceptance entry (append to the top of `docs/DECISION.md` is fine, or
paste it plainly):

- Confirmation each artifact above exists and matches (with sizes/hash if handy).
- The explicit caveat: "release APK has not been executed on a device."
- The pending checklist above, marked with what the device session has/hasn't
  done.
- "No further changes made." (or the narrow fix + why, if something was broken)
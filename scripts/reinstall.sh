#!/usr/bin/env bash
# Build the app and install it on the watch over (flaky) wireless debugging.
#
# Default: the R8-minified RELEASE APK (~4 MB), signed by Gradle with the debug keystore, so
# no post-processing is needed. Fallback --variant debug: the debug APK is ~63 MB because AGP
# stores classes*.dex uncompressed, and wireless transfers to the watch drop ("device offline");
# the script recompresses the dex and re-signs it (~18 MB). resources.arsc and lib/*.so MUST stay
# stored (uncompressed) and 4-byte aligned on Android 11+ or the install is rejected.
#
# Usage:
#   scripts/reinstall.sh                         build release, install on the connected device
#   scripts/reinstall.sh --variant debug         build debug, shrink + re-sign, install
#   scripts/reinstall.sh --connect IP:PORT       adb connect first (port from Wireless debugging)
#   scripts/reinstall.sh --scan IP               find the adb port on ONE host (opt-in port scan)
#   scripts/reinstall.sh --serial SERIAL         pick a device when several are attached
#   scripts/reinstall.sh --no-build              reuse the last built APK of the variant
#   scripts/reinstall.sh --no-install            only build/verify; print the APK path
#
# Tip: keep the watch on the charger with the screen on the Wireless debugging page while it
# transfers, or its Wi-Fi sleeps and drops the connection. The pairing port is not the connect port.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

die() { echo "reinstall: $*" >&2; exit 1; }

BUILD=1; INSTALL=1; CONNECT=""; SCAN=""; SERIAL="${ANDROID_SERIAL:-}"; VARIANT=release
while [[ $# -gt 0 ]]; do
  case "$1" in
    --variant) VARIANT="${2:?--variant needs debug or release}"; shift ;;
    --no-build) BUILD=0 ;;
    --no-install) INSTALL=0 ;;
    --connect) CONNECT="${2:?--connect needs IP:PORT}"; shift ;;
    --scan) SCAN="${2:?--scan needs an IP}"; shift ;;
    --serial) SERIAL="${2:?--serial needs a device serial}"; shift ;;
    -h|--help) sed -n '2,21p' "$0"; exit 0 ;;
    *) die "unknown option: $1 (try --help)" ;;
  esac
  shift
done
[[ "$VARIANT" == "release" || "$VARIANT" == "debug" ]] || die "--variant must be debug or release (got '$VARIANT')"

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"
[[ -x "$JAVA_HOME/bin/java" ]] || die "JAVA_HOME ($JAVA_HOME) has no java; need JDK 17"

SDK="${ANDROID_SDK_ROOT:-$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null || true)}"
[[ -d "$SDK" ]] || die "Android SDK not found; set ANDROID_SDK_ROOT or sdk.dir in local.properties"
BT="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)"
[[ -n "$BT" && -x "$BT/zipalign" && -x "$BT/apksigner" ]] || die "build-tools with zipalign/apksigner not found under $SDK/build-tools"
ADB="${ADB:-$SDK/platform-tools/adb}"
[[ -x "$ADB" ]] || die "adb not found at $ADB (set ADB=...)"
KEYSTORE="${DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}"
[[ -f "$KEYSTORE" ]] || die "debug keystore not found at $KEYSTORE (set DEBUG_KEYSTORE=...)"

TASK="assemble${VARIANT^}"
SRC="app/build/outputs/apk/$VARIANT/app-$VARIANT.apk"
if [[ $BUILD -eq 1 ]]; then
  echo "==> gradle :app:$TASK"
  ./gradlew --offline ":app:$TASK" --console=plain || die "gradle build failed (see output above)"
fi
[[ -f "$SRC" ]] || die "$SRC missing; run without --no-build (an unsigned release would be app-release-unsigned.apk: check signingConfig)"

W="$ROOT/build/reinstall"
rm -rf "$W"; mkdir -p "$W"

if [[ "$VARIANT" == "release" ]]; then
  # R8 already minified and Gradle already aligned + signed it: only verify, never re-pack.
  "$BT/zipalign" -c -p 4 "$SRC" >/dev/null || die "$SRC is not 4-byte aligned"
  "$BT/apksigner" verify "$SRC" || die "$SRC is not validly signed (check signingConfig in app/build.gradle.kts)"
  APK="$SRC"
  echo "==> release APK verified: $(du -h "$APK" | cut -f1)"
else
  command -v zip >/dev/null && command -v unzip >/dev/null || die "need zip and unzip for the debug shrink"
  mkdir -p "$W/x"
  echo "==> shrink + re-sign debug APK ($(du -h "$SRC" | cut -f1) -> ?)"
  ( cd "$W/x" && unzip -q "$ROOT/$SRC" && rm -rf META-INF
    zip -r -9 -q "$W/small.apk" . -x 'lib/*' 'resources.arsc'
    # stored entries; lib/ only exists when native libs are present
    to_store=(resources.arsc); [[ -d lib ]] && to_store+=(lib)
    zip -r -0 -q "$W/small.apk" "${to_store[@]}" )
  "$BT/zipalign" -f -p 4 "$W/small.apk" "$W/aligned.apk"
  "$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
    --ks-key-alias androiddebugkey --out "$W/signed.apk" "$W/aligned.apk"
  "$BT/apksigner" verify "$W/signed.apk" || die "apksigner verify failed"
  APK="$W/signed.apk"
  echo "    signed.apk: $(du -h "$APK" | cut -f1) (original $(du -h "$SRC" | cut -f1))"
fi

[[ $INSTALL -eq 1 ]] || { echo "==> done (not installing): $APK"; exit 0; }

if [[ -n "$SCAN" ]]; then
  echo "==> scanning $SCAN for an open ephemeral port (32768-60999); this takes a while"
  found=""
  for p in $(seq 32768 60999); do
    ( timeout 0.2 bash -c "exec 3<>/dev/tcp/$SCAN/$p" 2>/dev/null && echo "$p" >>"$W/open_ports" ) &
    (( p % 1000 == 0 )) && wait
  done; wait
  [[ -s "$W/open_ports" ]] || die "no open port found on $SCAN; is Wireless debugging on and the screen awake?"
  echo "    open ports: $(tr '\n' ' ' <"$W/open_ports")"
  for p in $(cat "$W/open_ports"); do
    if "$ADB" connect "$SCAN:$p" 2>&1 | grep -q '^connected'; then found="$SCAN:$p"; break; fi
  done
  [[ -n "$found" ]] || die "none of the open ports on $SCAN accepted adb connect"
  CONNECT=""; echo "    connected: $found"
fi
if [[ -n "$CONNECT" ]]; then
  # capture instead of piping through tee: under pipefail a failed connect would exit before our message
  out="$("$ADB" connect "$CONNECT" 2>&1 || true)"; echo "    $out"
  grep -qE '^(connected|already connected)' <<<"$out" || die "adb connect $CONNECT failed; check the port on the watch (not the pairing port)"
fi

# Pick the target explicitly: the first line of `adb devices` is wrong when an emulator or a second device is attached.
mapfile -t ready < <("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
if [[ -n "${SERIAL:-}" ]]; then
  printf '%s\n' "${ready[@]:-}" | grep -qx "$SERIAL" || die "device '$SERIAL' is not connected and ready (ready: ${ready[*]:-none})"
elif [[ ${#ready[@]} -eq 1 ]]; then
  SERIAL="${ready[0]}"
elif [[ ${#ready[@]} -eq 0 ]]; then
  die "no adb device in 'device' state. Pair/connect the watch, or pass --connect IP:PORT"
else
  die "several adb devices are ready (${ready[*]}); choose one with --serial SERIAL (or ANDROID_SERIAL)"
fi
ADBS=("$ADB" -s "$SERIAL")
echo "==> installing to $SERIAL"

for attempt in 1 2 3; do
  if "${ADBS[@]}" install -r --no-incremental "$APK"; then
    echo "==> installed. Launch: ${ADBS[*]} shell am start -n io.github.rocketkid300.monochromewatch/.MainActivity"
    exit 0
  fi
  echo "    install attempt $attempt failed; retrying" >&2
  # only this device: a bare `adb reconnect` would drop every connected device
  "${ADBS[@]}" reconnect >/dev/null 2>&1 || true
  sleep 3
done
die "install failed 3 times; if you saw 'device offline', keep the watch awake on charger and retry"

#!/usr/bin/env bash
# Repack the CI-built unsigned Monochrome.ipa into a rootless (Dopamine) .deb.
# Usage: jb/build-deb.sh path/to/Monochrome.ipa [version]
set -euo pipefail
IPA=${1:?usage: build-deb.sh Monochrome.ipa [version]}
THEOS=${THEOS:-/home/archlinux/Code/theos}
export PATH="$THEOS/toolchain/linux/iphone/bin:/home/archlinux/Code/veiled/tools/dpkg-root/usr/bin:$PATH"
cd "$(dirname "$0")"
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
unzip -q "$IPA" -d "$W/ipa"
APP="$W/ipa/Payload/Monochrome.app"
VER=${2:-$(/usr/bin/env python3 -c "import plistlib,sys;print(plistlib.load(open('$APP/Info.plist','rb'))['CFBundleShortVersionString'])")}
ROOT="$W/pkg"; mkdir -p "$ROOT/DEBIAN" "$ROOT/var/jb/Applications"
cp -R "$APP" "$ROOT/var/jb/Applications/Monochrome.app"
ldid -S"$(pwd)/entitlements.plist" "$ROOT/var/jb/Applications/Monochrome.app/Monochrome"   # fake-sign with jailbreak-app entitlements
cat > "$ROOT/DEBIAN/control" <<CTL
Package: com.rocketkid300.monochrome
Name: Monochrome
Version: $VER
Architecture: iphoneos-arm64
Description: Monochrome music (monochrome.st) as a jailbreak app
Maintainer: Rocketkid300 <Rocketkid300@users.noreply.github.com>
Author: Rocketkid300 <Rocketkid300@users.noreply.github.com>
Section: Multimedia
Depends: firmware (>= 15.0)
Homepage: https://github.com/Rocketkid300/monochrome
CTL
printf '#!/bin/sh\nuicache -p /var/jb/Applications/Monochrome.app >/dev/null 2>&1 || uicache -a >/dev/null 2>&1\nexit 0\n' > "$ROOT/DEBIAN/postinst"
printf '#!/bin/sh\nuicache -u /var/jb/Applications/Monochrome.app >/dev/null 2>&1 || true\nexit 0\n' > "$ROOT/DEBIAN/prerm"
chmod 755 "$ROOT/DEBIAN/postinst" "$ROOT/DEBIAN/prerm"
mkdir -p dist
OUT="dist/com.rocketkid300.monochrome_${VER}_iphoneos-arm64.deb"
# build the ar archive by hand so every file is owned by root (this dpkg-deb has no --root-owner-group)
T=(--owner=0 --group=0 --numeric-owner --format=gnu)
( cd "$ROOT/DEBIAN" && tar "${T[@]}" -czf "$W/control.tar.gz" . )
( cd "$ROOT" && tar "${T[@]}" -czf "$W/data.tar.gz" --exclude=./DEBIAN . )
echo "2.0" > "$W/debian-binary"
rm -f "$OUT"; ( cd "$W" && /usr/bin/ar rc "$OLDPWD/$OUT" debian-binary control.tar.gz data.tar.gz )
echo "built jb/$OUT"

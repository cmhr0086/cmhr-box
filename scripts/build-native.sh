#!/usr/bin/env bash
# Fetches libv2ray.aar and builds hev-socks5-tunnel into native/libs, following
# upstream/v2rayNG/.github/workflows/build.yml. Nothing is written inside the submodule
# except initializing its nested hev-socks5-tunnel submodule at the pinned gitlink.
#
# Usage: scripts/build-native.sh [--aar-only]
# Env:   NDK_HOME (default: $ANDROID_HOME/ndk/$NDK_VERSION)
set -euo pipefail

NDK_VERSION="30.0.16248370"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UPSTREAM="$ROOT/upstream/v2rayNG"
OUT="$ROOT/native/libs"
AAR_ONLY=false
[[ "${1:-}" == "--aar-only" ]] && AAR_ONLY=true

mkdir -p "$OUT"

# 1) libv2ray.aar: release tag whose commit equals the AndroidLibXrayLite gitlink.
XRAY_COMMIT="$(git -C "$UPSTREAM" ls-tree HEAD AndroidLibXrayLite | awk '{print $3}')"
XRAY_REFS=""
for _ in 1 2 3 4 5; do
  XRAY_REFS="$(git ls-remote --tags https://github.com/2dust/AndroidLibXrayLite)" && break
  XRAY_REFS=""
  sleep 3
done
if [[ -z "$XRAY_REFS" ]]; then
  echo "Could not list AndroidLibXrayLite tags" >&2
  exit 1
fi
XRAY_TAG="$(awk -v c="$XRAY_COMMIT" '$1 == c { sub("refs/tags/", "", $2); sub("\\^\\{\\}", "", $2); print $2; exit }' <<< "$XRAY_REFS")"
if [[ -z "$XRAY_TAG" ]]; then
  echo "No AndroidLibXrayLite tag points at gitlink $XRAY_COMMIT" >&2
  exit 1
fi
STAMP="$OUT/.libv2ray-tag"
if [[ -f "$OUT/libv2ray.aar" && "$(cat "$STAMP" 2>/dev/null)" == "$XRAY_TAG" ]]; then
  echo "libv2ray.aar $XRAY_TAG already present"
else
  echo "Downloading libv2ray.aar $XRAY_TAG"
  curl -fsSL --retry 5 --retry-all-errors -o "$OUT/libv2ray.aar.tmp" \
    "https://github.com/2dust/AndroidLibXrayLite/releases/download/$XRAY_TAG/libv2ray.aar"
  mv "$OUT/libv2ray.aar.tmp" "$OUT/libv2ray.aar"
  echo "$XRAY_TAG" > "$STAMP"
fi

$AAR_ONLY && exit 0

# 2) hev-socks5-tunnel via upstream compile-hevtun.sh, run from a staging dir outside the submodule.
NDK_HOME="${NDK_HOME:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/ndk/$NDK_VERSION}"
if [[ ! -x "$NDK_HOME/ndk-build" ]]; then
  echo "NDK $NDK_VERSION not found at $NDK_HOME (sdkmanager --install \"ndk;$NDK_VERSION\")" >&2
  exit 1
fi
export NDK_HOME

git -C "$UPSTREAM" submodule update --init --recursive hev-socks5-tunnel
HEV_COMMIT="$(git -C "$UPSTREAM" ls-tree HEAD hev-socks5-tunnel | awk '{print $3}')"
HEV_STAMP="$OUT/.hev-commit"
if [[ "$(cat "$HEV_STAMP" 2>/dev/null)" == "$HEV_COMMIT-$NDK_VERSION" ]]; then
  echo "hev-socks5-tunnel $HEV_COMMIT already built"
  exit 0
fi

STAGE="$ROOT/native/.hev-stage"
rm -rf "$STAGE"
mkdir -p "$STAGE"
ln -s "$UPSTREAM/hev-socks5-tunnel" "$STAGE/hev-socks5-tunnel"
cp "$UPSTREAM/compile-hevtun.sh" "$STAGE/"
bash "$STAGE/compile-hevtun.sh"
rsync -a "$STAGE/libs/" "$OUT/"
rm -rf "$STAGE"
echo "$HEV_COMMIT-$NDK_VERSION" > "$HEV_STAMP"
echo "Native libraries in $OUT"

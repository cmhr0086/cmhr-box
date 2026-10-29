#!/usr/bin/env bash
# Copies upstream/v2rayNG/V2rayNG/app into .patched/ and applies patches/*.patch in order.
# The submodule itself is never modified, so `git status` inside it stays clean.
# Native libraries from native/libs (see build-native.sh) are copied into the patched libs/.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/upstream/v2rayNG/V2rayNG/app"
DST_REPO="$ROOT/.patched/v2rayNG"
DST="$DST_REPO/V2rayNG/app"

if [[ ! -f "$SRC/build.gradle.kts" ]]; then
  echo "upstream/v2rayNG is missing; run: git submodule update --init upstream/v2rayNG" >&2
  exit 1
fi

mkdir -p "$DST"
rsync -a --delete --exclude /build/ --exclude /libs/ "$SRC/" "$DST/"

shopt -s nullglob
for p in "$ROOT"/patches/*.patch; do
  echo "Applying $(basename "$p")"
  # -F 0: refuse fuzzy matches, so upstream drift fails loudly instead of patching the wrong spot.
  if ! patch -d "$DST_REPO" -p1 -F 0 -N -s --dry-run < "$p" > /dev/null; then
    echo "Patch does not apply cleanly: $p" >&2
    exit 1
  fi
  patch -d "$DST_REPO" -p1 -F 0 -N -s < "$p"
done

mkdir -p "$DST/libs"
if [[ -d "$ROOT/native/libs" ]]; then
  rsync -a --delete --exclude '.*' "$ROOT/native/libs/" "$DST/libs/"
else
  echo "Warning: native/libs not found; run scripts/build-native.sh" >&2
fi

echo "Patched v2rayNG app module: $DST"

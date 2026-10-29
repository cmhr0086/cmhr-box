#!/usr/bin/env bash
# Only engine-v2rayng may import com.v2ray.ang.*; app and cmhr-core (and engine-api) must not.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if hits="$(grep -rnE '^\s*import\s+com\.v2ray\.ang\b' "$ROOT/app/src" "$ROOT/cmhr-core/src" "$ROOT/engine-api/src" 2>/dev/null)"; then
  echo "Forbidden com.v2ray.ang import outside engine-v2rayng:" >&2
  echo "$hits" >&2
  exit 1
fi
echo "Module boundaries OK"

#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="${1:-$ROOT/work/DLsiteFloat-src}"
PIN="c8ca46a548115060ce0822e7e8b5ebb6b6d2b048"
rm -rf "$DEST"
git clone https://github.com/ariinyume/DLSiteSoundFloatingSubtitle.git "$DEST"
git -C "$DEST" checkout "$PIN"
python3 "$ROOT/scripts/patch_dlsitesfloat.py" "$DEST"
OVERLAY="$ROOT/dlsitefloat-overlay"
while IFS= read -r -d '' src; do
  rel="${src#"$OVERLAY"/}"
  dst="$DEST/$rel"
  mkdir -p "$(dirname "$dst")"
  cp "$src" "$dst"
done < <(find "$OVERLAY" -type f -print0)
echo "Prepared custom DLsiteFloat source at $DEST"

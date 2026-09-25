#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$ROOT/work"
UPSTREAM="$WORK/LSPatch"
LSPATCH_COMMIT="0dc50f42503711b14f5e2bb217c2bdd6321ce5be"
MODULE_URL="https://github.com/ariinyume/DLSiteSoundFloatingSubtitle/releases/download/v2.1.0/DLsiteFloat-2.1.0-debug.apk"
MODULE_SHA="c7c15e16f8afd7b3266ed6d38d08b0380e8ae9fc80a8a5e8a05a039fa86eede1"

rm -rf "$WORK"
mkdir -p "$WORK"
git clone https://github.com/JingMatrix/LSPatch.git "$UPSTREAM"
git -C "$UPSTREAM" checkout "$LSPATCH_COMMIT"
git -C "$UPSTREAM" submodule update --init --recursive

python3 "$ROOT/scripts/apply_overlay.py" "$UPSTREAM"

ASSET_DIR="$UPSTREAM/manager/src/main/assets/quickpatch"
mkdir -p "$ASSET_DIR"
curl -fL "$MODULE_URL" -o "$ASSET_DIR/DLsiteFloat-2.1.0-debug.apk"
echo "$MODULE_SHA  $ASSET_DIR/DLsiteFloat-2.1.0-debug.apk" | sha256sum -c -

python3 "$ROOT/scripts/verify_no_dlsitesound_apk.py" "$ROOT"

echo
echo "Prepared: $UPSTREAM"
echo "Build: cd '$UPSTREAM' && ./gradlew :manager:buildRelease"

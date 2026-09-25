#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$ROOT/work"
UPSTREAM="$WORK/LSPatch"
LSPATCH_COMMIT="0dc50f42503711b14f5e2bb217c2bdd6321ce5be"

rm -rf "$WORK"
mkdir -p "$WORK"
git clone https://github.com/JingMatrix/LSPatch.git "$UPSTREAM"
git -C "$UPSTREAM" checkout "$LSPATCH_COMMIT"
git -C "$UPSTREAM" submodule update --init core
git -C "$UPSTREAM/core" submodule update --init --recursive \
  external/apache/commons-lang \
  external/axml/manifest-editor \
  external/dobby \
  external/fmt \
  external/lsplant \
  external/lsplt \
  external/xz-embedded

python3 "$ROOT/scripts/patch_vector_libxposed.py" "$UPSTREAM"
python3 "$ROOT/scripts/apply_overlay.py" "$UPSTREAM"

bash "$ROOT/scripts/prepare_dlsitesfloat.sh" "$WORK/DLsiteFloat-src"
bash "$ROOT/scripts/build_dlsitesfloat.sh" "$WORK/DLsiteFloat-src" "$UPSTREAM/manager/src/main/assets/quickpatch"

python3 "$ROOT/scripts/verify_no_dlsitesound_apk.py" "$ROOT"

echo
echo "Prepared: $UPSTREAM"
echo "Embedded custom DLsiteFloat: Rootless Controls v1"
echo "Build: cd '$UPSTREAM' && ./gradlew :manager:buildRelease"

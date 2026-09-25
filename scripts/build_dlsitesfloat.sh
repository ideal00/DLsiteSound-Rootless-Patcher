#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="${1:-$ROOT/work/DLsiteFloat-src}"
ASSET_DIR="${2:-$ROOT/work/LSPatch/manager/src/main/assets/quickpatch}"
ASSET_NAME="DLsiteFloat-2.1.0-rootless-v1.apk"
test -f "$SRC/gradlew"
chmod +x "$SRC/gradlew"
(
  cd "$SRC"
  ./gradlew :app:assembleDebug
)
APK="$(find "$SRC/app/build/outputs/apk/debug" -name '*.apk' -type f | head -n 1)"
test -n "$APK"
mkdir -p "$ASSET_DIR"
cp "$APK" "$ASSET_DIR/$ASSET_NAME"
python3 - "$ASSET_DIR/$ASSET_NAME" <<'PY'
import sys, zipfile, hashlib
p=sys.argv[1]
with zipfile.ZipFile(p) as z:
    dex=z.read("classes.dex")
    for needle in (b"PlayerControlBridge", b"PlaybackControlsView", b"io.github.ariinyume.dlsitesoundfloat"):
        if needle not in dex:
            raise SystemExit(f"custom DLsiteFloat verification failed: missing {needle!r}")
print("Custom DLsiteFloat verified:", p)
print("sha256="+hashlib.sha256(open(p,"rb").read()).hexdigest())
PY

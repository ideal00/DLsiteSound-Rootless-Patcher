#!/usr/bin/env python3
"""Refuse accidental commits of DLsiteSound APK/APKS files."""
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
allowed_names = {"DLsiteFloat-2.1.0-debug.apk"}
problems = []

for p in root.rglob("*"):
    if not p.is_file() or ".git" in p.parts or "work" in p.parts:
        continue
    lower = p.name.lower()
    if p.name in allowed_names:
        continue
    if lower.endswith((".apk", ".apks", ".xapk", ".apkm")):
        problems.append(str(p.relative_to(root)))

if problems:
    print("Refusing proprietary/unknown Android package files:")
    for p in problems:
        print(" -", p)
    raise SystemExit(1)
print("No DLsiteSound/unknown package binaries are stored in the source repository.")

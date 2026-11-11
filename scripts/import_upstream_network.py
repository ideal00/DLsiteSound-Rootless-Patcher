#!/usr/bin/env python3
"""Vendor the fixed v2.4.4 network adapter, retaining the rootless v4 UI/data model.

The upstream cache prefetch, settings diagnostics and work-key repository API are
outside this backport. The response discovery/peek/gzip/filtering logic is kept.
Run with a local DLsiteFloat git checkout; no host APK is copied into this repo.
"""
from pathlib import Path
import re
import subprocess
import sys

PIN = "b339c65a8ec674cfa2c6aa6834d15a99957fad3a"
ROOT = Path(__file__).resolve().parents[1]
PREFIX = "app/src/main/java/io/github/ariinyume/dlsitesoundfloat/"
DEST = ROOT / "dlsitefloat-overlay" / PREFIX


def source(path):
    return subprocess.check_output(["git", "-C", sys.argv[1], "show", f"{PIN}:{PREFIX}{path}"]).decode("utf-8")


def replace_once(text, old, new):
    if text.count(old) != 1:
        raise SystemExit(f"upstream anchor mismatch: {old[:100]!r}")
    return text.replace(old, new, 1)


text = source("hook/NetworkHook.java")
for imp in ("config.RemoteConfig", "data.PlaylistKey", "util.DiagDump", "util.LogGate"):
    text = replace_once(text, f"import io.github.ariinyume.dlsitesoundfloat.{imp};\n", "")
text = text.replace("LogGate.debug(TAG, ", "XposedCompat.log(TAG + ")
text = re.sub(r"\s*AdapterHealth\.(?:miss|hit|degraded)\(.*?;", "", text, flags=re.S)
text = replace_once(text, "boolean diagTarget = DiagDump.isTarget(urlLc);", "boolean diagTarget = false;")
text = replace_once(text, "long peekLimit = diagTarget ? DiagDump.MAX_BYTES : PEEK_LIMIT;", "long peekLimit = PEEK_LIMIT;")
text = replace_once(text, '''        if (diagTarget) {
            if (RemoteConfig.debugLog()) {
                DiagDump.save("ziptree", url, text);
            } else {
                DiagDump.logSkippedOnce();
            }
        }
''', "")
text = replace_once(text, '''        if (ZiptreePrefetch.isZiptree(urlLc)) {
            ZiptreePrefetch.offer(url, text);
        }
''', "")
text = replace_once(text, 'String workKey = urlLc.contains(".json") ? PlaylistKey.workKeyOf(url) : null;', "String workKey = null; // rootless v4 repository has no work-key cache")
text = replace_once(text, "            repo.loadFromJson(text, workKey);", "            if (io.github.ariinyume.dlsitesoundfloat.data.LocalLrcManager.isOverrideActive()) return;\n            repo.loadFromJson(text);")
text = text.replace("{@link PlaylistKey#workKeyOf}", "upstream work-key extraction")
text = text.replace("package io.github.ariinyume.dlsitesoundfloat.hook;", "// Backported from DLsiteFloat v2.4.4 (b339c65); see scripts/import_upstream_network.py.\npackage io.github.ariinyume.dlsitesoundfloat.hook;", 1)
for path, content in (("hook/NetworkHook.java", text), ("util/Shape.java", source("util/Shape.java"))):
    out = DEST / path
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(content, encoding="utf-8")
print("Imported v2.4.4 network adapter and Shape helper")

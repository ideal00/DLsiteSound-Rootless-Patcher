#!/usr/bin/env python3
"""Update an existing integrated APK locally; preserve its original APK and loader.

Only use with the user's own patched APK. The output is not a release artifact.
The signing key must match the input, or Android cannot install it as an update.
"""
import argparse
from pathlib import Path
import subprocess
import tempfile
import zipfile

p = argparse.ArgumentParser()
p.add_argument("apk", type=Path)
p.add_argument("module", type=Path)
p.add_argument("output", type=Path)
p.add_argument("--build-tools", type=Path, required=True)
p.add_argument("--jdk", type=Path, required=True)
p.add_argument("--keystore", type=Path, required=True)
p.add_argument("--alias", default="key0")
p.add_argument("--store-pass", default="123456", help="default is LSPatch's public built-in key password")
p.add_argument("--allow-split-base", action="store_true", help="output is only a base APK; install with every matching signed split")
args = p.parse_args()
asset = "assets/lspatch/modules/io.github.ariinyume.dlsitesoundfloat.apk"
apk, module, out = args.apk.resolve(), args.module.resolve(), args.output.resolve()
if out in (apk, module) or out.exists():
    p.error("output must be a new file distinct from both inputs")
manifest = subprocess.check_output([str(args.build_tools / "aapt2.exe"), "dump", "xmltree", str(apk),
                                    "--file", "AndroidManifest.xml"], text=True, encoding="utf-8")
if not args.allow_split_base and ("requiredSplitTypes" in manifest or "com.android.vending.splits.required" in manifest):
    p.error("input requires split APKs; a standalone replacement will fail with INSTALL_FAILED_MISSING_SPLIT. "
            "Use the phone patcher to read/install the full set, or explicitly allow a split-base output.")
out.parent.mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryDirectory(prefix="dlsite-module-update-", dir=out.parent) as temp:
    unsigned, aligned = Path(temp) / "unsigned.apk", Path(temp) / "aligned.apk"
    with zipfile.ZipFile(apk) as source, zipfile.ZipFile(unsigned, "w") as dest:
        if asset not in source.namelist() or "assets/lspatch/origin.apk" not in source.namelist():
            p.error("input must already contain an integrated module and origin.apk")
        # LSPatch deliberately reuses the original ZIP's resource offsets inside
        # origin.apk. Python 3.12+ flags that one spanning entry as overlapping.
        # Permit only the known origin entry; zipfile still validates its CRC.
        source.getinfo("assets/lspatch/origin.apk")._end_offset = None
        for info in source.infolist():
            name = info.filename.upper()
            if name.startswith("META-INF/") and (name.endswith((".SF", ".RSA", ".DSA", ".EC"))
                    or name in ("META-INF/MANIFEST.MF", "META-INF/STAMP-CERT-SHA256")):
                continue
            dest.writestr(info, module.read_bytes() if info.filename == asset else source.read(info.filename))
    subprocess.run([str(args.build_tools / "zipalign.exe"), "-P", "16", "-f", "4", str(unsigned), str(aligned)], check=True)
    subprocess.run([str(args.jdk / "bin/java.exe"), "-jar", str(args.build_tools / "lib/apksigner.jar"), "sign",
                    "--ks", str(args.keystore), "--ks-key-alias", args.alias, "--ks-pass", "pass:" + args.store_pass,
                    "--key-pass", "pass:" + args.store_pass, "--out", str(out), str(aligned)], check=True)
print("Updated embedded module locally:", out)

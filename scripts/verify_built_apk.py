#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import struct
import sys
import zipfile
from pathlib import Path

EXPECTED_APP_ID = b"io.github.dlsitesound.rootlesspatcher"
EXPECTED_VERSION = "0.1.0-alpha.1".encode("utf-16le")
EXPECTED_ACTIVITY = b"QuickPatchActivity"
EXPECTED_TARGET = b"jp.co.eisys.dlsitesound"
EXPECTED_MODULE_PACKAGE = b"io.github.ariinyume.dlsitesoundfloat"
EXPECTED_MODULE_SHA256 = "c7c15e16f8afd7b3266ed6d38d08b0380e8ae9fc80a8a5e8a05a039fa86eede1"
MODULE_ASSET = "assets/quickpatch/DLsiteFloat-2.1.0-rootless-v1.apk"
V2_BLOCK_ID = 0x7109871A


def fail(message: str) -> None:
    raise SystemExit("VERIFY FAILED: " + message)


def apk_signing_ids(blob: bytes) -> set[int]:
    eocd = blob.rfind(b"PK\x05\x06")
    if eocd < 0 or eocd + 22 > len(blob):
        fail("ZIP EOCD not found")
    central_dir_offset = struct.unpack_from("<I", blob, eocd + 16)[0]
    if central_dir_offset < 24:
        fail("central directory offset is too small")
    if blob[central_dir_offset - 16 : central_dir_offset] != b"APK Sig Block 42":
        fail("APK Signing Block magic not found")
    block_size = struct.unpack_from("<Q", blob, central_dir_offset - 24)[0]
    block_start = central_dir_offset - (block_size + 8)
    if block_start < 0:
        fail("invalid APK Signing Block size")
    first_size = struct.unpack_from("<Q", blob, block_start)[0]
    if first_size != block_size:
        fail("APK Signing Block size fields disagree")

    ids: set[int] = set()
    pos = block_start + 8
    end = central_dir_offset - 24
    while pos < end:
        if pos + 8 > end:
            fail("truncated APK Signing Block pair")
        pair_len = struct.unpack_from("<Q", blob, pos)[0]
        pos += 8
        if pair_len < 4 or pos + pair_len > end:
            fail("invalid APK Signing Block pair length")
        pair_id = struct.unpack_from("<I", blob, pos)[0]
        ids.add(pair_id)
        pos += pair_len
    if pos != end:
        fail("APK Signing Block pair parsing did not end cleanly")
    return ids


def main() -> None:
    if len(sys.argv) not in (2, 3):
        raise SystemExit("usage: verify_built_apk.py APK [full|release]")
    apk = Path(sys.argv[1])
    mode = sys.argv[2] if len(sys.argv) == 3 else "full"
    if not apk.is_file():
        fail(f"APK does not exist: {apk}")

    blob = apk.read_bytes()
    ids = apk_signing_ids(blob)
    if V2_BLOCK_ID not in ids:
        fail("APK Signature Scheme v2 block is missing")

    with zipfile.ZipFile(apk) as zf:
        names = set(zf.namelist())
        for required in (
            "AndroidManifest.xml",
            "classes.dex",
            "assets/lspatch/loader.dex",
            "assets/lspatch/metaloader.dex",
            MODULE_ASSET,
        ):
            if required not in names:
                fail(f"missing required APK entry: {required}")

        module = zf.read(MODULE_ASSET)
        actual_module_sha = hashlib.sha256(module).hexdigest()
        try:
            import io
            with zipfile.ZipFile(io.BytesIO(module)) as module_zip:
                module_dex = b"".join(
                    module_zip.read(name)
                    for name in module_zip.namelist()
                    if name.startswith("classes") and name.endswith(".dex")
                )
        except Exception as exc:
            fail(f"embedded DLsiteFloat is not a readable APK: {exc}")
        for needle in (
            b"io.github.ariinyume.dlsitesoundfloat",
            b"PlayerControlBridge",
            b"PlaybackControlsView",
            b"panel_opacity_pct",
            b"control_opacity_pct",
            b"subtitle_font_size_sp",
        ):
            if needle not in module_dex:
                fail(f"embedded custom DLsiteFloat is missing {needle!r}")

        manifest = zf.read("AndroidManifest.xml")
        dex = zf.read("classes.dex")
        # Binary AXML keeps its string pool in UTF-16/UTF-8. Check both where useful.
        if EXPECTED_APP_ID not in manifest and EXPECTED_APP_ID.decode().encode("utf-16le") not in manifest:
            fail("patcher applicationId is not present in AndroidManifest.xml")
        if EXPECTED_VERSION not in manifest and b"0.1.0-alpha.1" not in manifest:
            fail("patcher versionName 0.1.0-alpha.1 is not present in AndroidManifest.xml")

        for needle, label in (
            (EXPECTED_ACTIVITY, "QuickPatchActivity"),
            (EXPECTED_TARGET, "DLsiteSound target package"),
            (EXPECTED_MODULE_PACKAGE, "DLsiteFloat package"),
        ):
            if needle not in dex:
                fail(f"{label} string is not present in classes.dex")

        lspatch_sos = sorted(
            name for name in names
            if name.startswith("assets/lspatch/so/") and name.endswith("/liblspatch.so")
        )
        if not lspatch_sos:
            fail("no LSPatch native loader is bundled")
        if mode in ("full", "release"):
            expected_abis = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}
            actual_abis = {name.split("/")[3] for name in lspatch_sos}
            if actual_abis != expected_abis:
                fail(f"{mode} APK loader ABIs are incomplete: {sorted(actual_abis)}")
        else:
            fail(f"unknown verification mode: {mode}")

        # This tool must never redistribute the proprietary target APK.
        suspicious = [
            name for name in names
            if "dlsitesound" in name.lower()
            and name != MODULE_ASSET
            and not name.startswith("META-INF/")
        ]
        if suspicious:
            fail(f"unexpected DLsiteSound-named payloads in patcher APK: {suspicious}")

    print(f"APK verified: {apk}")
    print(f"sha256={hashlib.sha256(blob).hexdigest()}")
    print(f"signing_block_ids={','.join(hex(i) for i in sorted(ids))}")
    print(f"embedded_module_sha256={actual_module_sha}")
    print(f"mode={mode}")


if __name__ == "__main__":
    main()

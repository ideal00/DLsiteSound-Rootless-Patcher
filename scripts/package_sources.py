#!/usr/bin/env python3
"""Package corresponding source without private signing material or build caches."""
from __future__ import annotations

import argparse
import io
import os
from pathlib import Path
import re
import tarfile

SKIP_DIRS = {".git", ".gradle", ".kotlin", "build", "out", "__pycache__"}
PRIVATE_SUFFIXES = {".jks", ".keystore", ".bks", ".p12", ".pfx", ".pem", ".key"}
SIGNING_PROPERTY = re.compile(
    rb"^[ \t]*(?:android(?:StorePassword|KeyAlias|KeyPassword|StoreFile)"
    rb"|KEY_STORE(?:_PASSWORD)?|KEY_ALIAS|KEY_PASSWORD)[ \t]*[=:].*(?:\r?\n|$)",
    re.IGNORECASE | re.MULTILINE,
)
PROJECT_DIRS = (".github", "overlay", "dlsitefloat-overlay", "scripts", "docs")
PROJECT_FILES = ("README.md", "THIRD_PARTY.md", "LICENSE", "CHANGELOG.md", "PROJECT_STATUS.md", ".gitignore")


def source_files(root: Path):
    for current, directories, files in os.walk(root, followlinks=False):
        directories[:] = sorted(d for d in directories if d not in SKIP_DIRS and not (Path(current) / d).is_symlink())
        for name in sorted(files):
            path = Path(current) / name
            if name == ".git" or path.is_symlink() or path.suffix.lower() in PRIVATE_SUFFIXES:
                continue
            yield path


def package_sources(project: Path, work: Path, output: Path) -> int:
    roots = [(work / "LSPatch", "LSPatch"), (work / "DLsiteFloat-src", "DLsiteFloat-src")]
    for path, _ in roots:
        if not path.is_dir():
            raise ValueError(f"source tree is missing: {path}")
    output.parent.mkdir(parents=True, exist_ok=True)
    count = 0
    with tarfile.open(output, "w:gz") as archive:
        def add(path: Path, name: str):
            nonlocal count
            data = path.read_bytes()
            if path.name == "gradle.properties":
                data = SIGNING_PROPERTY.sub(b"", data)
            info = archive.gettarinfo(str(path), arcname=name)
            info.size = len(data)
            info.uid = info.gid = 0
            info.uname = info.gname = ""
            archive.addfile(info, io.BytesIO(data))
            count += 1

        for root, prefix in roots:
            for path in source_files(root):
                add(path, prefix + "/" + path.relative_to(root).as_posix())
        for directory in PROJECT_DIRS:
            root = project / directory
            if root.is_dir():
                for path in source_files(root):
                    add(path, "Rootless-Patcher-src/" + path.relative_to(project).as_posix())
        for name in PROJECT_FILES:
            path = project / name
            if path.is_file():
                add(path, "Rootless-Patcher-src/" + name)
    return count


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("work", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--project-root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    total = package_sources(args.project_root.resolve(), args.work.resolve(), args.output.resolve())
    print(f"Packaged {total} source files without private signing files or signing properties: {args.output}")

#!/usr/bin/env python3
"""Verify the source archive excludes signing secrets while retaining build source."""
from pathlib import Path
import tarfile
import tempfile
import unittest

from package_sources import package_sources


class SourcePackageTest(unittest.TestCase):
    def test_private_files_and_properties_are_not_published(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            project, work = root / "project", root / "work"
            project.mkdir()
            (project / "LICENSE").write_text("GPL-3.0", encoding="utf-8")
            (project / "docs").mkdir()
            (project / "docs/usage.md").write_text("build instructions", encoding="utf-8")
            fixtures = {
                "LSPatch/gradle.properties": (
                    "android.useAndroidX=true\nandroidStorePassword=private-store-value\n"
                    "androidKeyAlias=private-alias-value\nandroidKeyPassword=private-key-value\n"
                    "androidStoreFile=quickpatch-release.jks\norg.gradle.jvmargs=-Xmx2g\n"
                ),
                "LSPatch/quickpatch-release.jks": "private-keystore-bytes",
                "LSPatch/key.p12": "private-p12-bytes",
                "LSPatch/build/cache.txt": "private-build-cache",
                "LSPatch/.gradle/state.txt": "private-gradle-cache",
                "LSPatch/.git": "private-worktree-reference",
                "LSPatch/manager/src/main/assets/keystore": "upstream-public-default-keystore",
                "LSPatch/manager/src/main/App.kt": "application source",
                "DLsiteFloat-src/app/src/main/Module.java": "module source",
            }
            for name, text in fixtures.items():
                path = work / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text, encoding="utf-8")
            archive = root / "source.tar.gz"
            package_sources(project, work, archive)
            with tarfile.open(archive) as source:
                names = set(source.getnames())
                self.assertIn("Rootless-Patcher-src/docs/usage.md", names)
                self.assertIn("Rootless-Patcher-src/LICENSE", names)
                self.assertIn("DLsiteFloat-src/app/src/main/Module.java", names)
                self.assertIn("LSPatch/manager/src/main/assets/keystore", names)
                self.assertNotIn("LSPatch/quickpatch-release.jks", names)
                self.assertNotIn("LSPatch/key.p12", names)
                self.assertNotIn("LSPatch/build/cache.txt", names)
                self.assertNotIn("LSPatch/.gradle/state.txt", names)
                self.assertNotIn("LSPatch/.git", names)
                props = source.extractfile("LSPatch/gradle.properties").read()
                self.assertIn(b"android.useAndroidX=true", props)
                self.assertIn(b"org.gradle.jvmargs=-Xmx2g", props)
                all_bytes = b"".join(source.extractfile(entry).read() for entry in source if entry.isfile())
                self.assertNotIn(b"private-", all_bytes)


if __name__ == "__main__":
    unittest.main()

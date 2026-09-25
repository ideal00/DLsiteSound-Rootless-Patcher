#!/usr/bin/env python3
from pathlib import Path
import shutil
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_overlay.py /path/to/LSPatch")

root = Path(sys.argv[1]).resolve()
project = Path(__file__).resolve().parents[1]
overlay = project / "overlay"

if not (root / "manager/src/main/AndroidManifest.xml").exists():
    raise SystemExit(f"not an LSPatch tree: {root}")

# Copy additive overlay files.
for src in overlay.rglob("*"):
    if src.is_file():
        dst = root / src.relative_to(overlay)
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)

manifest_path = root / "manager/src/main/AndroidManifest.xml"
manifest = manifest_path.read_text(encoding="utf-8")
old_activity = '''        <activity
            android:name=".ui.activity.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />

                <data android:scheme="file" />
                <data android:scheme="content" />
                <data android:mimeType="application/vnd.android.package-archive" />
            </intent-filter>
        </activity>'''
new_activity = '''        <activity
            android:name=".ui.activity.MainActivity"
            android:exported="false" />

        <activity
            android:name=".ui.activity.QuickPatchActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>'''
if old_activity not in manifest:
    raise SystemExit("upstream MainActivity manifest block changed; refusing to patch blindly")
manifest = manifest.replace(old_activity, new_activity)
manifest = manifest.replace('android:label="@string/app_name"', 'android:label="@string/quickpatch_app_name"', 1)
manifest_path.write_text(manifest, encoding="utf-8")

build_path = root / "manager/build.gradle.kts"
build = build_path.read_text(encoding="utf-8")
old = 'applicationId = defaultManagerPackageName'
new = '''applicationId = "io.github.dlsitesound.rootlesspatcher"
        versionCode = 2
        versionName = "0.2.0"'''
if old not in build:
    raise SystemExit("upstream manager applicationId line changed; refusing to patch blindly")
build_path.write_text(build.replace(old, new, 1), encoding="utf-8")

print("Overlay applied successfully")

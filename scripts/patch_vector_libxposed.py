#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_vector_libxposed.py /path/to/LSPatch/core")

root = Path(sys.argv[1]).resolve()

xposed = root / "xposed/build.gradle.kts"
daemon = root / "services/daemon-service/build.gradle.kts"
if not xposed.exists() or not daemon.exists():
    raise SystemExit(f"not a compatible pinned Vector tree: {root}")

x = xposed.read_text(encoding="utf-8")
old_x_sources = '''    sourceSets {
        named("main") {
            java.directories.addAll(listOf("src/main/kotlin", "libxposed/api/src/main/java"))
        }
    }
'''
new_x_sources = '''    sourceSets {
        named("main") {
            java.directories.add("src/main/kotlin")
        }
    }
'''
if old_x_sources not in x:
    raise SystemExit("Vector xposed sourceSets changed; refusing to patch blindly")
x = x.replace(old_x_sources, new_x_sources, 1)

old_x_deps = '''dependencies {
    implementation(projects.external.axml)
'''
new_x_deps = '''dependencies {
    implementation("io.github.libxposed:api:102.0.0")
    implementation(projects.external.axml)
'''
if old_x_deps not in x:
    raise SystemExit("Vector xposed dependencies changed; refusing to patch blindly")
x = x.replace(old_x_deps, new_x_deps, 1)
xposed.write_text(x, encoding="utf-8")

d = daemon.read_text(encoding="utf-8")
old_d_sources = '''    sourceSets {
        named("main") {
            java.directories.addAll(listOf("src/main/java", "../libxposed/service/src/main"))
            aidl.directories.addAll(listOf("src/main/aidl", "../libxposed/interface/src/main/aidl"))
        }
    }

'''
if old_d_sources not in d:
    raise SystemExit("Vector daemon-service sourceSets changed; refusing to patch blindly")
d = d.replace(old_d_sources, "", 1)

old_d_deps = '''dependencies {
    compileOnly(libs.androidx.annotation)
'''
new_d_deps = '''dependencies {
    implementation("io.github.libxposed:service:102.0.0")
    compileOnly(libs.androidx.annotation)
'''
if old_d_deps not in d:
    raise SystemExit("Vector daemon-service dependencies changed; refusing to patch blindly")
d = d.replace(old_d_deps, new_d_deps, 1)
daemon.write_text(d, encoding="utf-8")

print("Vector libxposed source submodules replaced with official Maven Central 102.0.0 artifacts")

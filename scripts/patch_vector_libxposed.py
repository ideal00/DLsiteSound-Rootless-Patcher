#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_vector_libxposed.py /path/to/LSPatch")

lspatch = Path(sys.argv[1]).resolve()
vector = lspatch / "core"

xposed = vector / "xposed/build.gradle.kts"
daemon = vector / "services/daemon-service/build.gradle.kts"
legacy = vector / "legacy/build.gradle.kts"
share_android = lspatch / "share/android/build.gradle.kts"
manager = lspatch / "manager/build.gradle.kts"
patch_loader = lspatch / "patch-loader/build.gradle.kts"

if not xposed.exists() or not daemon.exists() or not legacy.exists() or not share_android.exists() or not manager.exists() or not patch_loader.exists():
    raise SystemExit(f"not a compatible pinned LSPatch/Vector tree: {lspatch}")

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
    implementation("io.github.libxposed:interface:102.0.0")
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


l = legacy.read_text(encoding="utf-8")
old_l_deps = '''dependencies {
    api(projects.xposed)
'''
new_l_deps = '''dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    api(projects.xposed)
'''
if old_l_deps not in l:
    raise SystemExit("Vector legacy dependencies changed; refusing to patch blindly")
l = l.replace(old_l_deps, new_l_deps, 1)
legacy.write_text(l, encoding="utf-8")

s = share_android.read_text(encoding="utf-8")
old_s_deps = '''dependencies {
    implementation("vector:daemon-service")
}
'''
new_s_deps = '''dependencies {
    implementation("io.github.libxposed:interface:102.0.0")
    implementation("vector:daemon-service")
}
'''
if old_s_deps not in s:
    raise SystemExit("LSPatch share/android dependencies changed; refusing to patch blindly")
s = s.replace(old_s_deps, new_s_deps, 1)
share_android.write_text(s, encoding="utf-8")


m = manager.read_text(encoding="utf-8")
old_m_deps = '''dependencies {
    implementation(projects.patch)
'''
new_m_deps = '''dependencies {
    implementation("io.github.libxposed:interface:102.0.0")
    implementation(projects.patch)
'''
if old_m_deps not in m:
    raise SystemExit("LSPatch manager dependencies changed; refusing to patch blindly")
m = m.replace(old_m_deps, new_m_deps, 1)
manager.write_text(m, encoding="utf-8")


pl = patch_loader.read_text(encoding="utf-8")
old_pl_deps = '''dependencies {
    compileOnly("vector:stubs")
'''
new_pl_deps = '''dependencies {
    implementation("io.github.libxposed:interface:102.0.0")
    compileOnly("vector:stubs")
'''
if old_pl_deps not in pl:
    raise SystemExit("LSPatch patch-loader dependencies changed; refusing to patch blindly")
pl = pl.replace(old_pl_deps, new_pl_deps, 1)
patch_loader.write_text(pl, encoding="utf-8")

print("Historical libxposed source submodules replaced with official Maven Central 102.0.0 artifacts")

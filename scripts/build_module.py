#!/usr/bin/env python3
"""Build the Java-only module with Android SDK tools, without Gradle cache state."""
import argparse
from pathlib import Path
import subprocess
import tempfile
import zipfile

p = argparse.ArgumentParser()
p.add_argument("source", type=Path)
p.add_argument("--sdk", type=Path, required=True)
p.add_argument("--jdk", type=Path, required=True)
p.add_argument("--api-aar", type=Path, required=True)
p.add_argument("--output", type=Path, required=True)
args = p.parse_args()
src, out = args.source.resolve(), args.output.resolve()
out.parent.mkdir(parents=True, exist_ok=True)
work = Path(tempfile.mkdtemp(prefix="module-build-v5-", dir=out.parent))
sdk = args.sdk.resolve()
bt = sdk / "build-tools/34.0.0"
android = sdk / "platforms/android-34/android.jar"
java = args.jdk.resolve() / "bin/java.exe"
javac = args.jdk.resolve() / "bin/javac.exe"
api = work / "libxposed-api.jar"
with zipfile.ZipFile(args.api_aar) as z:
    api.write_bytes(z.read("classes.jar"))


def run(*cmd):
    subprocess.run([str(c) for c in cmd], check=True)


res = work / "resources.zip"
run(bt / "aapt2.exe", "compile", "--dir", src / "app/src/main/res", "-o", res)
gen = work / "generated"
gen.mkdir(exist_ok=True)
unsigned = work / "module-unsigned.apk"
run(bt / "aapt2.exe", "link", "-I", android, "--manifest", src / "app/src/main/AndroidManifest.xml",
    "--java", gen, "--min-sdk-version", "24", "--target-sdk-version", "34",
    "--version-code", "1111", "--version-name", "2.1.2-rootless-v5", "-o", unsigned, res)
build_config = gen / "io/github/ariinyume/dlsitesoundfloat/BuildConfig.java"
build_config.write_text('''package io.github.ariinyume.dlsitesoundfloat;
public final class BuildConfig {
 public static final boolean DEBUG = true;
 public static final String APPLICATION_ID = "io.github.ariinyume.dlsitesoundfloat";
 public static final String VERSION_NAME = "2.1.2-rootless-v5";
 public static final int VERSION_CODE = 1111;
}
''', encoding="utf-8")
classes = work / "classes"
classes.mkdir(exist_ok=True)
sources = sorted((src / "app/src/main/java").rglob("*.java")) + sorted(gen.rglob("*.java"))
argfile = work / "javac.args"
argfile.write_text("\n".join('"' + str(x).replace("\\", "/") + '"' for x in sources), encoding="utf-8")
run(javac, "-encoding", "UTF-8", "-source", "17", "-target", "17", "-classpath", f"{android};{api}",
    "-d", classes, "@" + str(argfile))
jar = work / "module-classes.jar"
with zipfile.ZipFile(jar, "w") as z:
    for f in sorted(classes.rglob("*.class")):
        z.write(f, f.relative_to(classes).as_posix())
dex = work / "dex"
dex.mkdir(exist_ok=True)
run(java, "-cp", bt / "lib/d8.jar", "com.android.tools.r8.D8", "--min-api", "24", "--lib", android,
    "--classpath", api, "--output", dex, jar)
with zipfile.ZipFile(unsigned, "a", compression=zipfile.ZIP_DEFLATED) as z:
    for base in (src / "app/src/main/resources", src / "app/src/main/assets"):
        if base.exists():
            for f in sorted(base.rglob("*")):
                if f.is_file():
                    prefix = "assets/" if base.name == "assets" else ""
                    z.write(f, prefix + f.relative_to(base).as_posix())
    for f in dex.glob("*.dex"):
        z.write(f, f.name)
aligned = work / "module-aligned.apk"
run(bt / "zipalign.exe", "-f", "4", unsigned, aligned)
keystore = Path.home() / ".android/debug.keystore"
run(java, "-jar", bt / "lib/apksigner.jar", "sign", "--ks", keystore, "--ks-key-alias", "androiddebugkey",
    "--ks-pass", "pass:android", "--key-pass", "pass:android", "--out", out, aligned)
run(java, "-jar", bt / "lib/apksigner.jar", "verify", out)
print("Built", out)

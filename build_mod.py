#!/usr/bin/env python3
"""Injeta o menu Lunar num APK de jogo Android, sem root.

Pipeline:
  1. javac + d8   -> classes.dex do menu
  2. apktool d -s -r  -> so o manifest (rapido, nao decodifica 1GB)
  3. injeta <provider> no AndroidManifest
  4. apktool b    -> apk repackado com o dex original intacto
  5. adiciona classesN.dex com o menu (multidex nativo, API 21+)
  6. zipalign + apksigner

Uso:
  python3 build_mod.py --base jogo.apk --out build
  python3 build_mod.py --base jogo.apk --out build --skip-apktool

Iteracao depois do primeiro build: nao precisa repackar.
Edita /sdcard/Android/data/<pkg>/files/mod.cfg e reinicia o jogo.
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
TOOLS = os.path.join(ROOT, "apks", "tools")
JDK = os.path.join(ROOT, "tools", "jdk-17.0.21.1+2")
JAVA = os.path.join(JDK, "bin", "java")
JAVAC = os.path.join(JDK, "bin", "javac")
os.environ["PATH"] = os.path.join(JDK, "bin") + os.pathsep + os.environ.get("PATH", "")
ANDROID_JAR = os.path.join(TOOLS, "android-34", "android.jar")
D8_JAR = os.path.join(TOOLS, "android-14", "lib", "d8.jar")
APKTOOL = os.path.join(TOOLS, "apktool.jar")
NDK = os.environ.get("LUNAR_NDK", "/tmp/ndk/android-ndk-r26d")
NATIVE_SRC = os.path.join(HERE, "native", "lunar.cpp")
NATIVE_OUT = "lib/arm64-v8a/liblunar.so"
BUILD_TOOLS = os.path.join(TOOLS, "android-14")
ZIPALIGN = os.path.join(BUILD_TOOLS, "zipalign")
APKSIGNER = os.path.join(BUILD_TOOLS, "apksigner")
KEYSTORE = os.path.join(ROOT, "mod", "build", "mod.keystore")
KS_PASS = "android"
KS_ALIAS = "mod"

PROVIDER = (
    '        <provider\n'
    '            android:name="com.lunar.mod.Boot"\n'
    '            android:authorities="com.lunar.mod.boot"\n'
    '            android:exported="false"\n'
    '            android:initOrder="999" />\n'
    '    </application>'
)


def run(cmd, **kwargs):
    printable = " ".join(cmd) if isinstance(cmd, list) else cmd
    print("  $", printable[:160])
    result = subprocess.run(cmd, capture_output=True, text=True, **kwargs)
    if result.returncode != 0:
        print(result.stdout[-2000:])
        print(result.stderr[-2000:])
        raise SystemExit("falhou: " + printable[:120])
    return result


def build_dex(out_dir):
    classes = os.path.join(out_dir, "classes")
    dex = os.path.join(out_dir, "dex")
    shutil.rmtree(classes, ignore_errors=True)
    shutil.rmtree(dex, ignore_errors=True)
    os.makedirs(classes)
    os.makedirs(dex)

    sources = []
    for root, _, files in os.walk(os.path.join(HERE, "src")):
        for name in files:
            if name.endswith(".java"):
                sources.append(os.path.join(root, name))

    run([JAVAC, "-source", "8", "-target", "8", "-bootclasspath", ANDROID_JAR,
         "-d", classes] + sources)
    class_files = []
    for root, _, files in os.walk(classes):
        for name in files:
            if name.endswith(".class"):
                class_files.append(os.path.join(root, name))
    run([JAVA, "-cp", D8_JAR, "com.android.tools.r8.D8", "--min-api", "24",
         "--output", dex] + class_files)
    return os.path.join(dex, "classes.dex")


def build_native(out_dir):
    """compila liblunar.so em arm64 com o NDK."""
    compiler = os.path.join(NDK, "toolchains", "llvm", "prebuilt", "linux-x86_64", "bin",
                            "aarch64-linux-android24-clang++")
    if not os.path.exists(compiler):
        print("  NDK ausente, seguindo sem motor nativo")
        return None
    target = os.path.join(out_dir, "liblunar.so")
    env = dict(os.environ)
    env["PATH"] = os.path.dirname(compiler) + os.pathsep + env.get("PATH", "")
    print("  $ clang++ -> liblunar.so")
    result = subprocess.run([compiler, "-std=c++17", "-O2", "-fPIC", "-shared",
                             "-o", target, NATIVE_SRC, "-llog", "-ldl",
                             "-Wl,-soname,liblunar.so"],
                            capture_output=True, text=True, env=env)
    if result.returncode != 0:
        print(result.stderr[-1500:])
        raise SystemExit("falha ao compilar liblunar.so")
    return target


def patch_manifest(work_dir):
    manifest = os.path.join(work_dir, "AndroidManifest.xml")
    with open(manifest, "r", encoding="utf-8") as handle:
        text = handle.read()

    if "com.lunar.mod.Boot" in text:
        print("  manifest ja tem o provider")
        return text

    if "</application>" not in text:
        raise SystemExit("manifest sem </application>")

    text = text.replace("</application>", PROVIDER, 1)
    with open(manifest, "w", encoding="utf-8") as handle:
        handle.write(text)

    package = re.search(r'package="([^"]+)"', text)
    print("  provider injetado em", package.group(1) if package else "?")
    return text


def add_multidex(apk_path, dex_path, out_path, native_path=None):
    with zipfile.ZipFile(apk_path, "r") as source:
        names = source.namelist()
        indexes = []
        for name in names:
            match = re.match(r"classes(\d*)\.dex", name)
            if match:
                suffix = match.group(1)
                indexes.append(int(suffix) if suffix else 1)
        target = "classes%d.dex" % ((max(indexes) + 1) if indexes else 2)

        with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as dest:
            for item in source.infolist():
                if item.filename == target:
                    continue
                dest.writestr(item, source.read(item.filename))
            dest.write(dex_path, target)
            if native_path and os.path.exists(native_path):
                info = zipfile.ZipInfo(NATIVE_OUT)
                info.compress_type = zipfile.ZIP_STORED
                with open(native_path, "rb") as handle:
                    dest.writestr(info, handle.read())
                print("  motor entrou como", NATIVE_OUT)
    print("  menu entrou como", target)


def out_dir_of(path):
    return os.path.dirname(os.path.abspath(path))


def sign(path, out_path):
    run([ZIPALIGN, "-p", "-f", "4", path, out_path + ".aligned"])
    run([APKSIGNER, "sign", "--ks", KEYSTORE, "--ks-pass", "pass:" + KS_PASS,
         "--ks-key-alias", KS_ALIAS, "--out", out_path, out_path + ".aligned"])
    try:
        os.remove(out_path + ".aligned")
    except OSError:
        pass


def patch_one(apk_path, out_path):
    """patch de um apk unico: compila, injeta provider, reassina."""
    work = out_path + ".work"
    shutil.rmtree(work, ignore_errors=True)
    dex = build_dex(out_path + ".dex")
    native = build_native(out_dir_of(out_path))
    run([JAVA, "-jar", APKTOOL, "d", "-s", "-f", apk_path, "-o", work])
    patch_manifest(work)
    repacked = out_path + ".repacked"
    run([JAVA, "-jar", APKTOOL, "b", work, "-o", repacked])
    merged = out_path + ".merged"
    add_multidex(repacked, dex, merged, native)
    sign(merged, out_path)
    for temp in (work, repacked, merged):
        if os.path.isdir(temp):
            shutil.rmtree(temp, ignore_errors=True)
        elif os.path.exists(temp):
            os.remove(temp)


def build_from_apks(apks_path, out_dir):
    """modo split: patch no base, reassina todos os splits com a mesma chave."""
    splits_dir = os.path.join(out_dir, "splits")
    shutil.rmtree(splits_dir, ignore_errors=True)
    os.makedirs(splits_dir)

    with zipfile.ZipFile(apks_path, "r") as archive:
        names = [n for n in archive.namelist() if n.lower().endswith(".apk")]
        print("  splits encontrados:", len(names))
        for name in names:
            target = os.path.join(splits_dir, os.path.basename(name))
            with open(target, "wb") as handle:
                handle.write(archive.read(name))

    signed_dir = os.path.join(out_dir, "signed")
    shutil.rmtree(signed_dir, ignore_errors=True)
    os.makedirs(signed_dir)

    for name in sorted(os.listdir(splits_dir)):
        source = os.path.join(splits_dir, name)
        target = os.path.join(signed_dir, name)
        if name.startswith("base") or "base-master" in name:
            print("  patch no", name)
            patch_one(source, target)
        else:
            print("  reassinando", name)
            sign(source, target)

    final = os.path.join(out_dir, "mod.apks")
    with zipfile.ZipFile(final, "w", zipfile.ZIP_DEFLATED) as archive:
        for name in sorted(os.listdir(signed_dir)):
            archive.write(os.path.join(signed_dir, name), name)

    print("\nok: %s (%.1f MB)" % (final, os.path.getsize(final) / (1024 * 1024)))
    print("instalar: abre o .apks no MT Manager -> Install")
    return final


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True, help="apk ou .apks original do jogo")
    parser.add_argument("--out", default="build")
    parser.add_argument("--name", default=None, help="nome do apk final")
    args = parser.parse_args()

    base = os.path.abspath(args.base)
    if not os.path.exists(base):
        raise SystemExit("apk nao encontrado: " + base)

    out_dir = os.path.abspath(args.out)
    os.makedirs(out_dir, exist_ok=True)

    if base.lower().endswith(".apks") or base.lower().endswith(".xapk"):
        print("[split] modo bundle: patch no base, reassina todos os splits")
        build_from_apks(base, out_dir)
        return

    work = os.path.join(out_dir, "work")
    shutil.rmtree(work, ignore_errors=True)

    print("[1/6] compilando menu")
    dex = build_dex(out_dir)
    native = build_native(out_dir)

    print("[2/6] decodificando manifest e recursos (sem fontes)")
    run([JAVA, "-jar", APKTOOL, "d", "-s", "-f", base, "-o", work])

    print("[3/6] injetando provider")
    patch_manifest(work)

    print("[4/6] repackando")
    repacked = os.path.join(out_dir, "repacked.apk")
    run([JAVA, "-jar", APKTOOL, "b", work, "-o", repacked])

    print("[5/6] adicionando dex do menu")
    merged = os.path.join(out_dir, "merged.apk")
    add_multidex(repacked, dex, merged, native)

    print("[6/6] alinhando e assinando")
    aligned = os.path.join(out_dir, "aligned.apk")
    run([ZIPALIGN, "-p", "-f", "4", merged, aligned])
    final = os.path.join(out_dir, args.name or ("mod-" + os.path.basename(base)))
    run([APKSIGNER, "sign", "--ks", KEYSTORE, "--ks-pass", "pass:" + KS_PASS,
         "--ks-key-alias", KS_ALIAS, "--out", final, aligned])
    run([APKSIGNER, "verify", "--print-certs", final])

    size = os.path.getsize(final) / (1024 * 1024)
    print("\nok: %s (%.1f MB)" % (final, size))
    print("instalar: adb install -r %s" % final)


if __name__ == "__main__":
    main()

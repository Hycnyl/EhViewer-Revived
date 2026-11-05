#!/usr/bin/env python3
"""Package CI builds, sign locally, and verify the five release APKs."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import tomllib
import zipfile

try:
    from .apk_signature import verify_content_digests
except ImportError:
    from apk_signature import verify_content_digests

ROOT = Path(__file__).resolve().parents[1]
CERTIFICATE = "67afb9dab5d2234ed83bc3dd8c13faaeadf4940be0a6749e0aafb9ae9e8e143f"
ABIS = ("universal", "arm64-v8a", "armeabi-v7a", "x86_64", "x86")
PACKAGE = "moe.hycnyl.ehviewer"


def run(*args, env=None):
    result = subprocess.run(args, env=env, capture_output=True)
    if result.returncode:
        # Never echo a signing command, credential, or tool exception.
        raise RuntimeError(f"{Path(str(args[0])).name} failed (exit {result.returncode})")
    return result.stdout


def sha256(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def version():
    script = (ROOT / "app/build.gradle.kts").read_text()
    name = re.search(r'^\s*versionName = "([^"]+)"', script, re.M).group(1)
    code = int(re.search(r"^\s*versionCode = (\d+)", script, re.M).group(1))
    return name, code


def target_sdk():
    settings = (ROOT / "settings.gradle.kts").read_text()
    return int(re.search(r"^\s*targetSdk = (\d+)", settings, re.M).group(1))


def tools():
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
    return sdk / "build-tools/37.0.0"


def kotlin_tooling_metadata(archive):
    info = json.loads(archive.read("kotlin-tooling-metadata.json"))
    versions = tomllib.loads((ROOT / "gradle/libs.versions.toml").read_text())["versions"]
    wrapper = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text()
    gradle = re.search(r"/gradle-([0-9.]+)-bin\.zip", wrapper).group(1)
    if (info["buildSystem"], info["buildSystemVersion"], info["buildPluginVersion"]) != ("Gradle", gradle, versions["kotlin"]):
        raise RuntimeError("Kotlin tooling metadata does not match the build configuration")
    target = next((t for t in info["projectTargets"] if t["target"] == "org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget"), None)
    if target is None or target["platformType"] != "androidJvm":
        raise RuntimeError("Kotlin Android target metadata is missing")
    java = target["extras"]["android"]
    if not all(isinstance(java.get(key), str) and java[key].isdecimal() for key in ("sourceCompatibility", "targetCompatibility")):
        raise RuntimeError("Java compatibility metadata is missing")
    return info


def metadata(directory, filename, signed):
    name, code = version()
    info = {
        "repository": "Hycnyl/EhViewer-Revived",
        "sourceCommit": run("git", "-C", str(ROOT), "rev-parse", "HEAD").decode().strip(),
        "buildRunId": os.environ.get("GITHUB_RUN_ID"),
        "versionName": name,
        "versionCode": code,
        "applicationId": PACKAGE,
        "minimumSdk": 26,
        "targetSdk": target_sdk(),
        "files": {p.name: sha256(p) for p in sorted(directory.glob("*.apk"))},
    }
    with zipfile.ZipFile(next(directory.glob("*.apk"))) as archive:
        info["kotlinToolingMetadata"] = kotlin_tooling_metadata(archive)
    if signed:
        info.update(certificateSha256=CERTIFICATE, signatureSchemes=["v2", "v3"])
    (directory / filename).write_text(json.dumps(info, indent=2) + "\n")
    return info


def package(source, output):
    name, _ = version()
    output.mkdir(parents=True, exist_ok=False)
    for abi in ABIS:
        path = source / f"app-default-{abi}-release-unsigned.apk"
        if not path.is_file():
            raise RuntimeError(f"Missing unsigned APK for {abi}")
        shutil.copyfile(path, output / f"EhViewer-{name}-{abi}.apk")
    metadata(output, "build-info.json", signed=False)


def verify(directory, manifest, signed):
    info = json.loads((directory / manifest).read_text())
    name, code = version()
    commit = run("git", "-C", str(ROOT), "rev-parse", "HEAD").decode().strip()
    if (info["sourceCommit"], info["versionName"], info["versionCode"]) != (commit, name, code):
        raise RuntimeError("Build metadata does not match the checked-out source")
    if (info["applicationId"], info["minimumSdk"], info["targetSdk"]) != (PACKAGE, 26, target_sdk()):
        raise RuntimeError("Package/SDK manifest does not match the build configuration")
    expected = {f"EhViewer-{name}-{abi}.apk" for abi in ABIS}
    if set(info["files"]) != expected or {p.name for p in directory.glob("*.apk")} != expected:
        raise RuntimeError("Exactly one APK per supported ABI and one universal APK are required")
    sdk_tools = tools()
    for filename, digest in info["files"].items():
        apk = directory / filename
        if sha256(apk) != digest:
            raise RuntimeError(f"Checksum mismatch: {filename}")
        with zipfile.ZipFile(apk) as archive:
            if archive.testzip() is not None:
                raise RuntimeError(f"Corrupt APK: {filename}")
            if kotlin_tooling_metadata(archive) != info["kotlinToolingMetadata"]:
                raise RuntimeError(f"Inconsistent Kotlin tooling metadata: {filename}")
            for profile in ("assets/dexopt/baseline.prof", "assets/dexopt/baseline.profm"):
                if profile not in archive.namelist() or archive.getinfo(profile).file_size == 0:
                    raise RuntimeError(f"Missing compiled Baseline Profile: {filename}: {profile}")
            for dex in (p for p in archive.namelist() if re.fullmatch(r"classes\d*\.dex", p)):
                if b"Lcom/hippo/ehviewer/benchmark/" in archive.read(dex):
                    raise RuntimeError(f"Benchmark fixture leaked into release: {filename}")
            native_abis = {p.split("/")[1] for p in archive.namelist() if p.startswith("lib/") and p.endswith(".so")}
            for path in archive.namelist():
                if not path.startswith("lib/") or not path.endswith(".so"):
                    continue
                arch = path.split("/")[1]
                data = archive.read(path)
                elf_class, machine = {"arm64-v8a": (2, 183), "armeabi-v7a": (1, 40), "x86_64": (2, 62), "x86": (1, 3)}[arch]
                if data[:4] != b"\x7fELF" or data[4] != elf_class or data[5] != 1 or struct.unpack_from("<H", data, 18)[0] != machine:
                    raise RuntimeError(f"Incorrect native ELF architecture: {path}")
                if elf_class == 2:
                    phoff = struct.unpack_from("<Q", data, 32)[0]
                    entry_size, count = struct.unpack_from("<HH", data, 54)
                    for index in range(count):
                        offset = phoff + entry_size * index
                        if struct.unpack_from("<I", data, offset)[0] == 1 and struct.unpack_from("<Q", data, offset + 48)[0] < 16384:
                            raise RuntimeError(f"Native ELF lacks 16 KiB load alignment: {path}")
        abi = filename.removeprefix(f"EhViewer-{name}-").removesuffix(".apk")
        if native_abis != (set(ABIS[1:]) if abi == "universal" else {abi}):
            raise RuntimeError(f"Unexpected native architectures: {filename}")
        badging = run(str(sdk_tools / "aapt"), "dump", "badging", str(apk)).decode()
        for value in (f"name='{PACKAGE}'", f"versionCode='{code}'", f"versionName='{name}'", "sdkVersion:'26'", f"targetSdkVersion:'{target_sdk()}'", "application-label:'EhViewer'"):
            if value not in badging:
                raise RuntimeError(f"Unexpected package metadata ({value}): {filename}")
        if "application-debuggable" in badging:
            raise RuntimeError("Release APK must not be debuggable")
        run(str(sdk_tools / "zipalign"), "-c", "-P", "16", "4", str(apk))
        if signed:
            verify_content_digests(apk)
            result = run(str(sdk_tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk)).decode()
            # Build Tools 37 labels this signer "V3.0 Signer" instead of "Signer #1".
            certificates = re.findall(r"certificate SHA-256 digest: ([0-9a-f]{64})", result)
            if certificates != [CERTIFICATE] or "Number of signers: 1" not in result:
                raise RuntimeError(f"Release certificate verification failed: {filename}")
            for value in ("Verified using v2 scheme (APK Signature Scheme v2): true", "Verified using v3 scheme (APK Signature Scheme v3): true"):
                if value not in result:
                    raise RuntimeError(f"Release signing verification failed: {filename}")
            # Exercise verification at the minimum supported Android version too.
            run(str(sdk_tools / "apksigner"), "verify", "--min-sdk-version", "26", "--max-sdk-version", "26", str(apk))
        print(f"Verified {filename}")
    if signed:
        expected_sums = "".join(f"{digest}  {filename}\n" for filename, digest in info["files"].items())
        if (directory / "SHA256SUMS.txt").read_text() != expected_sums:
            raise RuntimeError("Published checksum file differs from the verified APKs")
    return info


def sign(source, output, vault):
    os.umask(0o077)
    vault = vault.resolve()
    if vault.is_relative_to(ROOT) or vault.stat().st_mode & 0o077:
        raise RuntimeError("Signing vault must be private (0700) and outside the checkout")
    for filename in ("credentials.json", "release.p12"):
        path = vault / filename
        if path.is_symlink() or path.stat().st_mode & 0o077:
            raise RuntimeError("Signing files must be private (0600) regular files")
    info = verify(source, "build-info.json", signed=False)
    credentials = json.loads((vault / "credentials.json").read_text())
    env = os.environ.copy()
    env.update(REVIVED_STORE_PASSWORD=credentials["storePassword"], REVIVED_KEY_PASSWORD=credentials["keyPassword"])
    certificate = run("keytool", "-exportcert", "-keystore", str(vault / "release.p12"), "-alias", credentials["keyAlias"], "-storepass:env", "REVIVED_STORE_PASSWORD", env=env)
    if hashlib.sha256(certificate).hexdigest() != CERTIFICATE:
        raise RuntimeError("The supplied keystore is not the pinned release identity")
    output.mkdir(parents=True, exist_ok=False)
    for filename in info["files"]:
        run(str(tools() / "apksigner"), "sign", "--ks", str(vault / "release.p12"), "--ks-key-alias", credentials["keyAlias"], "--ks-pass", "env:REVIVED_STORE_PASSWORD", "--key-pass", "env:REVIVED_KEY_PASSWORD", "--v1-signing-enabled", "false", "--v2-signing-enabled", "true", "--v3-signing-enabled", "true", "--v4-signing-enabled", "false", "--debuggable-apk-permitted", "false", "--lib-page-alignment", "16384", "--out", str(output / filename), str(source / filename), env=env)
    signed = metadata(output, "release-info.json", signed=True)
    signed["buildRunId"] = info["buildRunId"]
    (output / "release-info.json").write_text(json.dumps(signed, indent=2) + "\n")
    (output / "SHA256SUMS.txt").write_text("".join(f"{digest}  {filename}\n" for filename, digest in signed["files"].items()))
    verify(output, "release-info.json", signed=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    for command in ("package", "sign"):
        item = sub.add_parser(command)
        item.add_argument("--input", type=Path, required=True)
        item.add_argument("--output", type=Path, required=True)
        if command == "sign":
            item.add_argument("--vault", type=Path, required=True)
    item = sub.add_parser("verify")
    item.add_argument("--input", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "package":
        package(args.input, args.output)
    elif args.command == "sign":
        sign(args.input, args.output, args.vault)
    else:
        verify(args.input, "release-info.json", signed=True)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, RuntimeError) as error:
        raise SystemExit(str(error)) from None

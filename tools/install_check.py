#!/usr/bin/env python3
"""Check APK file integrity, universal/ABI installs, upgrades and target startup."""

import argparse
import hashlib
import json
from pathlib import Path

from smoke import adb, launch

PACKAGE = "moe.hycnyl.ehviewer"


def install(apk):
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    remote = f"/data/local/tmp/{apk.name}"
    adb("push", str(apk), remote)
    copied = adb("shell", "sha256sum", remote).decode().split()[0]
    if copied != digest:
        raise RuntimeError(f"Device received different APK bytes: {apk.name}")
    result = adb("shell", "pm", "install", "-r", remote).decode()
    if "Success" not in result:
        raise RuntimeError(f"File-based installation failed: {apk.name}: {result}")
    adb("shell", "rm", remote)
    print(f"PASS: device SHA-256 and file-based package install: {apk.name}", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apks", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    api = int(adb("shell", "getprop", "ro.build.version.sdk").strip())
    info = json.loads((args.apks / "release-info.json").read_text())
    if api >= 36:
        size = int(adb("shell", "getconf", "PAGE_SIZE").strip())
        if size != 16384:
            raise RuntimeError("Modern platform verification requires actual 16 KiB pages")
        print(f"PASS: API {api} uses 16 KiB pages", flush=True)
    adb("shell", "svc", "wifi", "disable")
    adb("shell", "svc", "data", "disable")
    baseline = Path("baseline")
    sentinel = f"/sdcard/Android/data/{PACKAGE}/files/upgrade-integrity.txt"
    try:
        universal = args.apks / f"EhViewer-{info['versionName']}-universal.apk"
        install(universal)
        launch(args.output, f"target-{info['targetSdk']}-api-{api}")
        matching_abi = args.apks / f"EhViewer-{info['versionName']}-x86_64.apk"
        install(matching_abi)
        if api == 33:
            # This emulator contains no user data. Check the previous release's
            # exact universal bytes too, then exercise the real upgrade path.
            adb("uninstall", PACKAGE)
            old_abi = list(baseline.glob("*x86_64.apk"))
            old_universal = list(baseline.glob("*universal.apk"))
            if len(old_abi) != 1 or len(old_universal) != 1:
                raise RuntimeError("Previous release universal and matching ABI are required")
            install(old_abi[0])
            install(old_universal[0])
            adb("shell", "mkdir", "-p", sentinel.rsplit("/", 1)[0])
            marker = args.output / "upgrade-integrity.txt"
            marker.write_text("upgrade-check\n")
            adb("push", str(marker), sentinel)
            install(universal)
            if adb("shell", "cat", sentinel).strip() != b"upgrade-check":
                raise RuntimeError("An upgrade did not preserve the installed app's data")
            adb("shell", "rm", sentinel)
            print("PASS: previous ABI/universal -> new universal upgrade preserves app data", flush=True)
        details = adb("shell", "dumpsys", "package", PACKAGE)
        (args.output / "package.txt").write_bytes(details)
        if f"targetSdk={info['targetSdk']}".encode() not in details:
            raise RuntimeError("Installed package does not use the intended target SDK")
        if f"versionCode={info['versionCode']}".encode() not in details:
            raise RuntimeError("Installed package does not use the intended version code")
        if PACKAGE in adb("logcat", "-d", "-b", "crash").decode():
            raise RuntimeError("Android recorded an app crash during install/startup checks")
    finally:
        (args.output / "logcat.txt").write_bytes(adb("logcat", "-d"))
        (args.output / "crashes.txt").write_bytes(adb("logcat", "-d", "-b", "crash"))


if __name__ == "__main__":
    main()

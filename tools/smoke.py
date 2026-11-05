#!/usr/bin/env python3
"""Exercise signed APK startup and Android 13 migration on a fresh emulator."""

import argparse
from pathlib import Path
import re
import struct
import subprocess
import time
import xml.etree.ElementTree as ET
import zipfile
import zlib

PACKAGE = "moe.hycnyl.ehviewer"
ACTIVITY = PACKAGE + "/com.hippo.ehviewer.ui.MainActivity"


class HierarchyNotReady(RuntimeError):
    pass


def adb(*args):
    result = subprocess.run(["adb", *args], stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if result.returncode:
        print(result.stdout.decode(errors="replace"), flush=True)
        result.check_returncode()
    return result.stdout


def hierarchy():
    adb("shell", "rm", "-f", "/sdcard/window.xml")
    result = adb("shell", "uiautomator", "dump", "/sdcard/window.xml")
    if b"dumped to:" not in result:
        raise HierarchyNotReady(result.decode(errors="replace"))
    return adb("shell", "cat", "/sdcard/window.xml")


def tap_text(text):
    for node in ET.fromstring(hierarchy()).iter("node"):
        if node.get("package") == PACKAGE and node.get("text", "").casefold() == text.casefold():
            left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
            adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
            time.sleep(2)
            return True
    return False


def prepare_guest():
    # Dismiss the normal first-run download-location warning, then use guest mode.
    for _ in range(3):
        if not tap_text("Open settings"):
            break
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(2)
    tap_text("Guest mode")


def offline_reader(output):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    color = bytes((0, 255, 0))
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 320, 480, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress((b"\0" + color * 320) * 480)) + chunk(b"IEND", b"")
    archive = output / "reader-smoke.cbz"
    with zipfile.ZipFile(archive, "w") as pages:
        pages.writestr("00000001.png", png)
        pages.writestr("00000002.png", png)
    remote = f"/sdcard/Android/data/{PACKAGE}/cache"
    adb("shell", "mkdir", "-p", remote)
    adb("push", str(archive), remote + "/reader-smoke.cbz")
    uri = f"content://{PACKAGE}.fileprovider/copy/reader-smoke.cbz"
    adb("shell", "am", "start", "-W", "-n", ACTIVITY, "-a", "android.intent.action.VIEW", "-d", uri, "-t", "application/vnd.comicbook+zip")
    for _ in range(12):
        time.sleep(2)
        raw = adb("exec-out", "screencap")
        width, height, pixel_format = struct.unpack_from("<III", raw)
        channels = 3 if pixel_format == 3 else 4
        pixels = raw[-width * height * channels:]
        if pixels.count(color) + pixels.count(color[::-1]) > width * height // 20:
            (output / "offline-reader.png").write_bytes(adb("exec-out", "screencap", "-p"))
            (output / "offline-reader.xml").write_bytes(hierarchy())
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
            print("PASS: local CBZ opens, native archive decoding and image rendering work")
            return
    (output / "offline-reader-failed.png").write_bytes(adb("exec-out", "screencap", "-p"))
    (output / "offline-reader-failed.xml").write_bytes(hierarchy())
    raise RuntimeError("Offline CBZ did not render the known page pixels")


def launch(output, label):
    adb("shell", "am", "force-stop", PACKAGE)
    result = adb("shell", "am", "start", "-W", "-n", ACTIVITY).decode()
    if "Status: ok" not in result and "Status: timeout" not in result:
        raise RuntimeError(f"Activity launch failed:\n{result}")
    if "Status: timeout" in result:
        print("ActivityManager's first-frame wait timed out; checking process and visible UI", flush=True)
    for _ in range(15):
        time.sleep(2)
        if not adb("shell", "pidof", PACKAGE).strip():
            raise RuntimeError("App process exited during startup")
        try:
            view = hierarchy()
        except HierarchyNotReady as error:
            print(f"Waiting for visible UI: {error}", flush=True)
            continue
        texts = [n.get("text") for n in ET.fromstring(view).iter("node") if n.get("package") == PACKAGE and n.get("text", "").strip()]
        if len(texts) >= 2:
            (output / f"{label}.xml").write_bytes(view)
            (output / f"{label}.png").write_bytes(adb("exec-out", "screencap", "-p"))
            break
    else:
        raise RuntimeError("App UI did not become visible")
    # Keep observing after the first frame to catch delayed startup failures.
    time.sleep(5)
    adb("shell", "pidof", PACKAGE)
    crashes = adb("logcat", "-d", "-b", "crash").decode()
    if PACKAGE in crashes:
        raise RuntimeError("Android recorded an app crash")
    print(f"PASS: {label}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apks", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    try:
        exercise(args)
    except Exception:
        # Preserve evidence if a cold emulator never produces a usable app UI.
        for name, command in (
            ("failure.png", ("exec-out", "screencap", "-p")),
            ("activity.txt", ("shell", "dumpsys", "activity", "activities")),
            ("window.txt", ("shell", "dumpsys", "window", "windows")),
        ):
            try:
                (args.output / name).write_bytes(adb(*command))
            except subprocess.CalledProcessError:
                pass
        try:
            pid = adb("shell", "pidof", PACKAGE).decode().strip().split()[0]
            adb("shell", "kill", "-3", pid)
            time.sleep(2)
            adb("pull", "/data/anr", str(args.output / "anr"))
        except (subprocess.CalledProcessError, IndexError):
            pass
        raise
    finally:
        (args.output / "logcat.txt").write_bytes(adb("logcat", "-d"))
        (args.output / "crashes.txt").write_bytes(adb("logcat", "-d", "-b", "crash"))


def exercise(args):
    api = int(adb("shell", "getprop", "ro.build.version.sdk").strip())
    adb("shell", "svc", "wifi", "disable")
    adb("shell", "svc", "data", "disable")
    if api == 36:
        page_size = int(adb("shell", "getconf", "PAGE_SIZE").strip())
        if page_size != 16384:
            raise RuntimeError("API 36 verification must use the 16 KiB page-size image")
        print("PASS: Android 16 emulator uses 16 KiB pages")
    # Each job starts a fresh AVD. Preserve its boot diagnostics too; Android 8
    # can reject clearing logd buffers after adbd switches to root.
    if api == 33:
        baseline = Path("baseline")
        previous = (baseline / "previous-revived-tag.txt").read_text().strip()
        if not re.fullmatch(r"v\d+\.\d+\.\d+", previous):
            raise RuntimeError("A previous stable Revived release is required")
        names = ["EhViewer-1.17.8-universal.apk", "EhViewer-1.17.9-universal.apk", f"EhViewer-{previous[1:]}-universal.apk"]
        if {p.name for p in baseline.glob("*universal.apk")} != set(names):
            raise RuntimeError("Original and previous Revived migration baselines are required on API 33")
        for old in (baseline / name for name in names):
            adb("install", "-r", "-g", str(old))
            launch(args.output, old.stem)
            prepare_guest()
    apk = list(args.apks.glob("*universal.apk"))
    if len(apk) != 1:
        raise RuntimeError("One universal release APK is required")
    adb("install", "-r", "-g", str(apk[0]))
    launch(args.output, f"revived-api-{api}")
    if api == 33:
        # Guest mode writes need_sign_in=false in the previous releases. Check
        # it before dismissing anything, so an upgrade cannot hide a reset.
        view = ET.fromstring(hierarchy())
        if any(n.get("package") == PACKAGE and n.get("text") == "Guest mode" for n in view.iter("node")):
            raise RuntimeError("Upgrade lost the existing guest-mode preference")
        print("PASS: existing guest-mode preference survives the upgrade")
    prepare_guest()
    offline_reader(args.output)
    # Test a normal background/foreground cycle with the same installed data.
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)
    time.sleep(3)
    adb("shell", "pidof", PACKAGE)
    details = adb("shell", "dumpsys", "package", PACKAGE)
    (args.output / "package.txt").write_bytes(details)
    (args.output / "logcat.txt").write_bytes(adb("logcat", "-d"))
    if PACKAGE in adb("logcat", "-d", "-b", "crash").decode():
        raise RuntimeError("App crashed after resuming")
    print(f"PASS: API {api} install, first frame, cold start and resume")


if __name__ == "__main__":
    main()

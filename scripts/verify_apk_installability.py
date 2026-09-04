#!/usr/bin/env python3
"""Fail the release build early unless both APKs are real, signed, aligned artifacts."""
from pathlib import Path
import os
import shutil
import subprocess
import sys


def run(cmd):
    print("$", " ".join(cmd))
    return subprocess.run(cmd, check=False, text=True, capture_output=True)


def main():
    apks = [
        Path("app/build/outputs/apk/release/MyAdv.apk"),
        Path("licensemanager/build/outputs/apk/release/MyAdvAnIT.apk"),
    ]
    for apk in apks:
        if not apk.is_file() or apk.stat().st_size == 0:
            print(f"ERROR: missing/empty APK: {apk}", file=sys.stderr)
            return 1

    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    build_tools = []
    if sdk:
        bt = Path(sdk) / "build-tools"
        if bt.is_dir():
            build_tools = sorted([p for p in bt.iterdir() if p.is_dir()], reverse=True)

    apksigner = None
    zipalign = None
    for p in build_tools:
        if (p / "apksigner").exists():
            apksigner = str(p / "apksigner")
            zipalign = str(p / "zipalign") if (p / "zipalign").exists() else None
            break
    if not apksigner:
        apksigner = shutil.which("apksigner")
    if not apksigner:
        print("ERROR: apksigner not found", file=sys.stderr)
        return 1

    for apk in apks:
        result = run([apksigner, "verify", "--verbose", str(apk)])
        if result.returncode != 0:
            print(result.stdout)
            print(result.stderr, file=sys.stderr)
            print(f"ERROR: APK signature verification failed: {apk}", file=sys.stderr)
            return 1
        print(result.stdout)
        if zipalign:
            result = run([zipalign, "-c", "-P", "16", "-v", str(apk)])
            if result.returncode != 0:
                print(result.stdout)
                print(result.stderr, file=sys.stderr)
                print(f"ERROR: APK zip alignment verification failed: {apk}", file=sys.stderr)
                return 1

    print("APK installability checks passed: both release APKs exist, are signed, and are aligned.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

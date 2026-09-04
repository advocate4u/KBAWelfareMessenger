from pathlib import Path

expected = {
    "MyAdv.apk": Path("app/build/outputs/apk/release/MyAdv.apk"),
    "MyAdvAnIT.apk": Path("licensemanager/build/outputs/apk/release/MyAdvAnIT.apk"),
}
for name, path in expected.items():
    if not path.is_file() or path.stat().st_size == 0:
        raise SystemExit(f"Missing or empty APK: {name}")
    print(f"Verified output: {name} ({path.stat().st_size} bytes)")

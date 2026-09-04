from pathlib import Path
import os
import subprocess

VERSION_FILE = Path("version.properties")
MAJOR_PATH_MARKERS = (
    "/Settings",
    "SettingsActivity.kt",
    "AppSettings",
    "activity_settings.xml",
    "settings.xml",
)


def read_state():
    values = {}
    for line in VERSION_FILE.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return int(values.get("MAJOR", "2")), int(values.get("MINOR", "6")), int(values.get("VERSION_CODE", "6"))


def changed_files():
    try:
        output = subprocess.check_output(
            ["git", "diff-tree", "--no-commit-id", "--name-only", "-r", "HEAD"],
            text=True,
        )
        return [line.strip() for line in output.splitlines() if line.strip()]
    except (subprocess.CalledProcessError, FileNotFoundError):
        return []


def is_major_change(path):
    normalized = path.replace("\\", "/")
    return any(marker in normalized for marker in MAJOR_PATH_MARKERS)


major, minor, version_code = read_state()
files = changed_files()
major_change = any(is_major_change(path) for path in files)

if major_change:
    major += 1
    minor = 0
else:
    minor += 1

version_code += 1
version_name = f"{major}.{minor}"

print(f"Changed files: {files}")
print(f"Major settings change: {major_change}")
print(f"Release version: {version_name}")
print(f"Version code: {version_code}")

Path("release-version.env").write_text(
    f"APP_VERSION={version_name}\nVERSION_CODE={version_code}\nMAJOR={major}\nMINOR={minor}\n",
    encoding="utf-8",
)

# Expose values to later GitHub Actions steps.
github_output = os.environ.get("GITHUB_OUTPUT")
if github_output:
    with open(github_output, "a", encoding="utf-8") as output:
        output.write(f"app_version={version_name}\n")
        output.write(f"version_code={version_code}\n")
        output.write(f"major={major}\n")
        output.write(f"minor={minor}\n")

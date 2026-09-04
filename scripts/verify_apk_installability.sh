#!/usr/bin/env bash
set -euo pipefail

verify_apk() {
  local apk="$1"
  test -s "$apk"
  command -v apksigner >/dev/null 2>&1 || { echo "apksigner not found"; exit 1; }
  apksigner verify --verbose --print-certs "$apk"
}

verify_apk app/build/outputs/apk/release/MyAdv.apk
verify_apk licensemanager/build/outputs/apk/release/MyAdvAnIT.apk

echo "Both release APKs passed apksigner verification."

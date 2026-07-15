#!/usr/bin/env bash
set -euo pipefail

# Repository root keeps report and Gradle paths stable regardless of the caller's working directory.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Device and build executables are injectable for tooling tests and non-standard SDK installations.
ADB_BIN="${PIXEL_ADB_BIN:-adb}"
GRADLEW_BIN="${PIXEL_GRADLEW_BIN:-$ROOT_DIR/gradlew}"
PYTHON_BIN="${PIXEL_PYTHON_BIN:-python3}"
SLEEP_BIN="${PIXEL_SLEEP_BIN:-sleep}"
# The M0 device baseline measures the existing demo launch path, not an app-specific business screen.
PACKAGE_NAME="com.purride.pixeldemo"
# Explicit component avoids launcher-selection ambiguity on devices with multiple HOME handlers.
COMPONENT_NAME="com.purride.pixeldemo/.DemoActivity"
# Stable report paths are consumed by collect_pixel_baseline.py.
REPORT_DIR="$ROOT_DIR/build/reports/device-baseline"
RAW_FRAMESTATS="$REPORT_DIR/gfxinfo-framestats.txt"
SUMMARY="$REPORT_DIR/summary.json"

cd "$ROOT_DIR"

if ! command -v "$ADB_BIN" >/dev/null 2>&1; then
  echo "adb is required for the device baseline." >&2
  exit 1
fi

# A caller-supplied serial wins; otherwise exactly one connected and authorized device is required.
if [[ -n "${ADB_SERIAL:-}" ]]; then
  ADB=("$ADB_BIN" -s "$ADB_SERIAL")
else
  DEVICES=()
  while IFS= read -r serial; do
    DEVICES+=("$serial")
  done < <("$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
  if [[ "${#DEVICES[@]}" -ne 1 ]]; then
    echo "Exactly one Android device is required; set ADB_SERIAL when multiple devices are connected." >&2
    exit 1
  fi
  ADB=("$ADB_BIN" -s "${DEVICES[0]}")
fi

mkdir -p "$REPORT_DIR"

# Build and install the exact demo variant whose launch frames are collected below.
"$GRADLEW_BIN" :pixel-demo:installDebug --no-daemon

# Wake the screen and request normal dismissal of a non-secure keyguard; this never bypasses a secure lock.
"${ADB[@]}" shell input keyevent KEYCODE_WAKEUP >/dev/null
"${ADB[@]}" shell wm dismiss-keyguard >/dev/null

# Device metadata makes later comparisons attributable instead of mixing unlike hardware.
DEVICE_MODEL="$("${ADB[@]}" shell getprop ro.product.model | tr -d '\r')"
API_LEVEL="$("${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
# The active SurfaceFlinger mode reflects the real display budget instead of the first supported mode.
REFRESH_RATE="$("${ADB[@]}" shell dumpsys display | sed -nE 's/.*mActiveSfDisplayMode=.*refreshRate=([0-9]+([.][0-9]+)?).*/\1/p' | head -n 1 | tr -d '\r')"
if [[ -z "$REFRESH_RATE" ]]; then
  REFRESH_RATE="60"
fi

"${ADB[@]}" shell am force-stop "$PACKAGE_NAME" >/dev/null
"${ADB[@]}" shell dumpsys gfxinfo "$PACKAGE_NAME" reset >/dev/null
"${ADB[@]}" shell am start -W -n "$COMPONENT_NAME" >"$REPORT_DIR/am-start.txt"
"$SLEEP_BIN" 5
"${ADB[@]}" shell dumpsys window >"$REPORT_DIR/window.txt"
if ! grep -Eq "mCurrentFocus=.*${PACKAGE_NAME}" "$REPORT_DIR/window.txt"; then
  echo "Demo is not the focused window; refusing to record a hidden/AOD frame baseline." >&2
  exit 1
fi
"${ADB[@]}" shell dumpsys gfxinfo "$PACKAGE_NAME" framestats >"$RAW_FRAMESTATS"

"$PYTHON_BIN" tools/parse_android_framestats.py \
  --input "$RAW_FRAMESTATS" \
  --output "$SUMMARY" \
  --package "$PACKAGE_NAME" \
  --model "$DEVICE_MODEL" \
  --api-level "$API_LEVEL" \
  --refresh-rate "$REFRESH_RATE"

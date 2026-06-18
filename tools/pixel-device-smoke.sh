#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is required for device smoke validation." >&2
  exit 1
fi

if [[ -n "${ADB_SERIAL:-}" ]]; then
  ADB=(adb -s "$ADB_SERIAL")
else
  DEVICES=()
  while IFS= read -r serial; do
    DEVICES+=("$serial")
  done < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
  if [[ "${#DEVICES[@]}" -eq 0 ]]; then
    echo "No Android device or emulator is connected. Set ADB_SERIAL or connect one device." >&2
    exit 1
  fi
  if [[ "${#DEVICES[@]}" -gt 1 ]]; then
    echo "Multiple Android devices are connected. Set ADB_SERIAL to choose one:" >&2
    printf '  %s\n' "${DEVICES[@]}" >&2
    exit 1
  fi
  ADB=(adb -s "${DEVICES[0]}")
fi

REPORT_DIR="build/reports/device-smoke"
mkdir -p "$REPORT_DIR"

echo "Using Android device: $("${ADB[@]}" get-serialno)"

./gradlew \
  :pixel-demo:installDebug \
  --no-daemon

run_case() {
  local name="$1"
  local package_name="$2"
  local component="$3"
  local start_log="$REPORT_DIR/${name}-am-start.txt"
  local window_log="$REPORT_DIR/${name}-window.txt"
  local screenshot="$REPORT_DIR/${name}.png"

  "${ADB[@]}" shell am force-stop "$package_name" >/dev/null
  "${ADB[@]}" shell am start -W -n "$component" >"$start_log"
  sleep 2
  "${ADB[@]}" shell dumpsys window >"$window_log"
  "${ADB[@]}" exec-out screencap -p >"$screenshot"

  if ! grep -Eq 'Status: ok|Status: OK|Complete' "$start_log"; then
    echo "Launch did not report success for $component. See $start_log" >&2
    exit 1
  fi
  if ! grep -q "$package_name" "$window_log"; then
    echo "Window dump does not show focused package $package_name. See $window_log" >&2
    exit 1
  fi

  echo "Launched $component"
  echo "  start: $start_log"
  echo "  window: $window_log"
  echo "  screenshot: $screenshot"
}

run_case "pixel-demo" "com.purride.pixeldemo" "com.purride.pixeldemo/.DemoActivity"

echo "Device smoke report directory: $REPORT_DIR"

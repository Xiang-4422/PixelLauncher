#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

./gradlew \
  :pixel-engine:checkPublicApi \
  :pixel-engine:testDebugUnitTest \
  :pixel-engine:lintDebug \
  :pixel-engine:assembleDebug \
  :pixel-demo:assembleDebug \
  :pixel-demo:testDebugUnitTest \
  :pixel-minimal-sample:assembleDebug \
  --no-daemon

./gradlew :pixel-engine:publishToMavenLocal --dry-run --no-daemon

if ! python3 -m mkdocs --version >/dev/null 2>&1; then
  python3 -m pip install --user mkdocs
fi
python3 -m mkdocs build --strict

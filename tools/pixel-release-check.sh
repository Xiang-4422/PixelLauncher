#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# --continue: run every gate task even after one fails, so a single run surfaces
# ALL failures instead of aborting at the first. Gradle still exits non-zero if
# any task failed, so the gate's pass/fail outcome is unchanged.
./gradlew \
  :pixel-engine:checkPublicApi \
  :pixel-engine:checkBinaryApi \
  :pixel-engine:checkKdocCoverage \
  :pixel-engine:testPixelGlyphPackConverter \
  :pixel-engine:testDebugUnitTest \
  :pixel-engine:lintDebug \
  :pixel-engine:assembleDebug \
  :pixel-demo:assembleDebug \
  :pixel-demo:testDebugUnitTest \
  --continue \
  --no-daemon

./gradlew :pixel-engine:publishToMavenLocal --dry-run --no-daemon

if ! python3 -m mkdocs --version >/dev/null 2>&1; then
  python3 -m pip install --user mkdocs
fi
python3 -m mkdocs build --strict

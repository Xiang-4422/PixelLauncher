#!/usr/bin/env bash
set -euo pipefail

# 仓库根目录用于定位全部 API baseline 与文档站。
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# CI 默认使用仓库 Wrapper，工具测试可注入失败替身。
GRADLEW_BIN="${PIXEL_GRADLEW_BIN:-$ROOT_DIR/gradlew}"
# Python 用于严格构建 MkDocs。
PYTHON_BIN="${PIXEL_PYTHON_BIN:-python3}"
# 固定文档依赖清单，避免 runner 预装版本影响严格构建结果。
DOC_REQUIREMENTS="$ROOT_DIR/requirements-docs.txt"
cd "$ROOT_DIR"

# API 报告必须由本次源码重新生成，不能读取上一次 build 目录。
"$GRADLEW_BIN" clean --no-build-cache --no-daemon
"$GRADLEW_BIN" \
  :pixel-engine:checkBinaryApi \
  :pixel-engine:checkMetalavaApi \
  :pixel-engine:checkStableApiBoundary \
  :pixel-engine:checkThemeTokenCoverage \
  :pixel-engine:checkUnicodeGraphemeDataGeneration \
  :pixel-engine:checkUnicodeBidiDataGeneration \
  :pixel-engine:checkKdocCoverage \
  --continue \
  --no-build-cache \
  --no-daemon

# 干净环境缺少固定 MkDocs 版本时按仓库清单安装，不依赖 runner 偶然预装。
if ! "$PYTHON_BIN" -c 'import importlib.metadata; raise SystemExit(importlib.metadata.version("mkdocs") != "1.6.1")'; then
  "$PYTHON_BIN" -m pip install --user --disable-pip-version-check -r "$DOC_REQUIREMENTS"
fi
"$PYTHON_BIN" tools/prepare_mkdocs_docs.py
"$PYTHON_BIN" -m mkdocs build --strict
echo "Pixel CI API and documentation gate passed."

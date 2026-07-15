#!/usr/bin/env bash
set -euo pipefail

# 仓库根目录统一解析清单、证据和报告路径。
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Python 可注入以验证 shell 对失败退出码的传播。
PYTHON_BIN="${PIXEL_PYTHON_BIN:-python3}"
# 默认清单固定本轮四档最终证据，后续重采集可显式传入新清单。
MATRIX_MANIFEST="${PIXEL_MACRO_COMPATIBILITY_MANIFEST:-pixel-engine/performance/macrobenchmark-compatibility-matrix.json}"
# 报告路径稳定供 CI、验收文档与发布审计收集。
OUTPUT_REPORT="${PIXEL_MACRO_COMPATIBILITY_REPORT:-build/reports/performance/matrix/macrobenchmark-compatibility-report.json}"

cd "$ROOT_DIR"
"$PYTHON_BIN" tools/check_macrobenchmark_compatibility.py \
  --manifest "$MATRIX_MANIFEST" \
  --output "$OUTPUT_REPORT"

echo "Macrobenchmark 兼容矩阵报告：$OUTPUT_REPORT"

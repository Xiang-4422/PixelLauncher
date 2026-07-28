#!/usr/bin/env bash
set -euo pipefail

# 仓库根目录用于定位所有独立消费者和兼容性报告。
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Bash 可注入以测试子门禁失败是否原样传播。
BASH_BIN="${PIXEL_BASH_BIN:-bash}"
cd "$ROOT_DIR"

# 每个 compatibility 脚本都使用隔离工程；矩阵会重建共享 file-Maven 仓库。
"$BASH_BIN" tools/pixel-render-spi-compatibility.sh
"$BASH_BIN" tools/pixel-route-entry-compatibility.sh
"$BASH_BIN" tools/pixel-consumer-compatibility-matrix.sh
PIXEL_SKIP_COMPATIBILITY_PUBLISH=1 "$BASH_BIN" tools/pixel-sdk-consumer-smoke.sh
# 同一隔离工程必须仅依据发布文档完成 Host、typed route、公开 SPI 和 PixelTester 接入。
PIXEL_SKIP_COMPATIBILITY_PUBLISH=1 "$BASH_BIN" tools/pixel-docs-consumer-smoke.sh

echo "Pixel CI consumer gate passed."

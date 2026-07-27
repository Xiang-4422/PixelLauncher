#!/usr/bin/env bash
set -euo pipefail

# 仓库根目录用于定位 Gradle producer、配置、工具和证据目录。
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Gradle Wrapper 可在失败传播测试中注入替代命令。
GRADLEW_BIN="${PIXEL_GRADLEW_BIN:-$ROOT_DIR/gradlew}"
# Python 解释器负责结构化生成、校验和 OSV 查询。
PYTHON_BIN="${PIXEL_PYTHON_BIN:-python3}"
# GnuPG 用于生成一次性演练密钥并验证 detached signatures。
GPG_BIN="${PIXEL_GPG_BIN:-gpg}"
# 本轮独占的已签名 Maven staging 仓库。
STAGING_REPOSITORY="${PIXEL_STAGING_REPOSITORY:-$ROOT_DIR/build/m9-staging-repository}"
# 依赖图、SBOM、provenance、签名和扫描报告的持久证据目录。
REPORT_DIR="${PIXEL_SUPPLY_CHAIN_REPORT_DIR:-$ROOT_DIR/build/reports/supply-chain/m9-2}"
# Gradle 解析图的固定输出路径。
DEPENDENCY_GRAPH="$ROOT_DIR/build/reports/supply-chain/release-dependency-graph.json"
# 发布元数据的唯一受审来源。
RELEASE_METADATA="$ROOT_DIR/pixel-engine/config/release-metadata.properties"
# 高危漏洞例外的唯一受审清单。
OSV_ALLOWLIST="$ROOT_DIR/pixel-engine/config/osv-allowlist.json"
# 是否要求已经完成许可证决策；正式候选默认必须要求。
REQUIRE_LICENSE="${PIXEL_REQUIRE_LICENSE:-1}"
# 是否执行临时 HTTP 仓库的最低/推荐消费者矩阵。
RUN_REMOTE_CONSUMERS="${PIXEL_RUN_REMOTE_CONSUMERS:-1}"
# 是否调用 OSV 官方 API；单元测试不通过这个脚本替代真实扫描。
RUN_OSV_SCAN="${PIXEL_RUN_OSV_SCAN:-1}"
# 一次性签名密钥口令只存在于当前进程环境和临时 GnuPG home。
SIGNING_PASSWORD="${PIXEL_EPHEMERAL_SIGNING_PASSWORD:-pixel-engine-ci-ephemeral}"
# GnuPG 临时 home，退出后无条件删除私钥。
GNUPG_HOME="$(mktemp -d "${TMPDIR:-/tmp}/pixel-engine-gpg.XXXXXX")"
# 临时 HTTP 服务进程号；未启动时为空。
HTTP_PID=""

# 无论成功失败都停止临时服务并销毁一次性私钥。
cleanup() {
  if [[ -n "$HTTP_PID" ]]; then
    kill "$HTTP_PID" >/dev/null 2>&1 || true
    wait "$HTTP_PID" >/dev/null 2>&1 || true
  fi
  rm -rf "$GNUPG_HOME"
}
trap cleanup EXIT

cd "$ROOT_DIR"
rm -rf "$STAGING_REPOSITORY" "$REPORT_DIR"
# 版本切换或坐标调整后不得复用上一轮依赖图，即使 Gradle cache 仍存在。
rm -f "$DEPENDENCY_GRAPH"
mkdir -p "$REPORT_DIR"
chmod 700 "$GNUPG_HOME"

# 一次性 RSA 密钥只证明发布流水线真的能签名/验签，不冒充正式发布身份。
"$GPG_BIN" \
  --batch \
  --homedir "$GNUPG_HOME" \
  --pinentry-mode loopback \
  --passphrase "$SIGNING_PASSWORD" \
  --quick-generate-key \
  "Pixel Engine Ephemeral CI <pixel-engine-ci@invalid.example>" \
  rsa2048 \
  sign \
  1d >/dev/null 2>&1

# 当前临时签名主密钥的完整 fingerprint。
SIGNING_FINGERPRINT="$(
  "$GPG_BIN" --batch --homedir "$GNUPG_HOME" --with-colons --fingerprint \
    | awk -F: '$1 == "fpr" { print $10; exit }'
)"
[[ ${#SIGNING_FINGERPRINT} -eq 40 ]] || {
  echo "Unable to create ephemeral signing fingerprint." >&2
  exit 1
}

# ASCII-armored 私钥仅通过 Gradle 内存属性传入，绝不写入仓库或证据目录。
SIGNING_KEY="$(
  "$GPG_BIN" \
    --batch \
    --homedir "$GNUPG_HOME" \
    --pinentry-mode loopback \
    --passphrase "$SIGNING_PASSWORD" \
    --armor \
    --export-secret-keys "$SIGNING_FINGERPRINT"
)"
# 公钥可以安全持久化，用于独立验签和证据复核。
PUBLIC_KEY="$REPORT_DIR/ephemeral-signing-public-key.asc"
"$GPG_BIN" --batch --homedir "$GNUPG_HOME" --armor --export "$SIGNING_FINGERPRINT" >"$PUBLIC_KEY"

# Gradle Signing Plugin 从 ORG_GRADLE_PROJECT 属性读取内存密钥和临时仓库。
export ORG_GRADLE_PROJECT_signingKey="$SIGNING_KEY"
export ORG_GRADLE_PROJECT_signingPassword="$SIGNING_PASSWORD"
export ORG_GRADLE_PROJECT_pixelRequireSigning=true
export ORG_GRADLE_PROJECT_pixelStagingRepositoryUrl="$STAGING_REPOSITORY"

# 锁文件必须已经存在且与 Release 编译/运行依赖图一致；这里不使用 --write-locks 自动放宽差异。
"$GRADLEW_BIN" \
  writePixelReleaseDependencyGraph \
  :pixel-engine:publishReleasePublicationToPixelStagingRepository \
  --no-build-cache \
  --no-daemon

# 根据本轮真实 Maven 文件生成 CycloneDX 1.7、SLSA/in-toto 来源和四种 checksum。
"$PYTHON_BIN" tools/generate_pixel_supply_chain.py \
  --dependency-graph "$DEPENDENCY_GRAPH" \
  --repository "$STAGING_REPOSITORY" \
  --version "1.0.0" \
  --metadata "$RELEASE_METADATA" \
  --repository-root "$ROOT_DIR" \
  --output-directory "$REPORT_DIR"

# 两个补充物也使用同一临时密钥生成 detached ASCII-armored signature。
for supplemental_file in \
  "$STAGING_REPOSITORY/com/purride/pixel-engine/1.0.0/pixel-engine-1.0.0-sbom.cdx.json" \
  "$STAGING_REPOSITORY/com/purride/pixel-engine/1.0.0/pixel-engine-1.0.0-provenance.intoto.json"; do
  "$GPG_BIN" \
    --batch \
    --homedir "$GNUPG_HOME" \
    --pinentry-mode loopback \
    --passphrase "$SIGNING_PASSWORD" \
    --local-user "$SIGNING_FINGERPRINT" \
    --armor \
    --detach-sign \
    --output "$supplemental_file.asc" \
    "$supplemental_file"
done

# 复用发布契约，验证统一坐标的 AAR metadata、sources、Javadoc 和 Gradle variants。
"$PYTHON_BIN" tools/check_pixel_publication.py \
  --repository "$STAGING_REPOSITORY" \
  --version "1.0.0" \
  --report "$REPORT_DIR/publication.json"

# 扫描 Maven payload、嵌套 classes.jar、签名和补充物，确认没有真实凭据进入发布仓库。
"$PYTHON_BIN" tools/check_secrets.py \
  --no-worktree \
  --path "$STAGING_REPOSITORY" \
  --report "$REPORT_DIR/artifact-secret-scan.json"

# 许可证要求由显式参数决定；默认正式门禁必须同时看到 CONFIRMED 和仓库 LICENSE。
LICENSE_ARGUMENTS=()
if [[ "$REQUIRE_LICENSE" == "1" ]]; then
  LICENSE_ARGUMENTS=(
    --require-license
    --repository-license "$ROOT_DIR/LICENSE"
    --repository-notice "$ROOT_DIR/NOTICE"
  )
fi

# 隔离 keyring 重验签名，并重算全部 checksum、POM、SBOM、来源和 AAR 内容边界。
"$PYTHON_BIN" tools/check_pixel_supply_chain.py \
  --repository "$STAGING_REPOSITORY" \
  --version "1.0.0" \
  --metadata "$RELEASE_METADATA" \
  --verification-metadata "$ROOT_DIR/gradle/verification-metadata.xml" \
  --lockfile "$ROOT_DIR/pixel-engine/gradle.lockfile" \
  --public-key "$PUBLIC_KEY" \
  --report "$REPORT_DIR/validation.json" \
  ${LICENSE_ARGUMENTS[@]+"${LICENSE_ARGUMENTS[@]}"}

if [[ "$RUN_OSV_SCAN" == "1" ]]; then
  # OSV 报告必须没有未解释 HIGH/CRITICAL 或未知严重度发现。
  "$PYTHON_BIN" tools/scan_pixel_osv.py \
    --dependency-graph "$DEPENDENCY_GRAPH" \
    --allowlist "$OSV_ALLOWLIST" \
    --report "$REPORT_DIR/osv.json"
fi

if [[ "$RUN_REMOTE_CONSUMERS" == "1" ]]; then
  # 选择当前回环地址的临时空闲端口供只读 Maven HTTP 服务使用。
  HTTP_PORT="$(
    "$PYTHON_BIN" -c 'import socket; server = socket.socket(); server.bind(("127.0.0.1", 0)); print(server.getsockname()[1]); server.close()'
  )"
  # 临时远程样式 Maven 根 URL。
  REMOTE_REPOSITORY_URL="http://127.0.0.1:$HTTP_PORT"
  "$PYTHON_BIN" -m http.server "$HTTP_PORT" \
    --bind 127.0.0.1 \
    --directory "$STAGING_REPOSITORY" \
    >"$REPORT_DIR/http-server.log" 2>&1 &
  HTTP_PID=$!
  # 主动探测根目录，避免消费者把服务启动竞争误报成依赖解析问题。
  "$PYTHON_BIN" -c \
    'import sys, time, urllib.request; url = sys.argv[1]; last = None
for _ in range(30):
    try:
        urllib.request.urlopen(url, timeout=1).read(1)
        raise SystemExit(0)
    except Exception as error:
        last = error
        time.sleep(0.1)
raise SystemExit(f"Temporary Maven HTTP server did not start: {last}")' \
    "$REMOTE_REPOSITORY_URL/"

  # 最低、推荐和两个明确不支持组合全部从 HTTP 坐标解析同一批已签名产物。
  PIXEL_SKIP_COMPATIBILITY_PUBLISH=1 \
  PIXEL_COMPATIBILITY_REPOSITORY="$STAGING_REPOSITORY" \
  PIXEL_COMPATIBILITY_REPOSITORY_URI="$REMOTE_REPOSITORY_URL" \
    bash tools/pixel-consumer-compatibility-matrix.sh
fi

echo "Pixel supply-chain gate passed: $REPORT_DIR (ephemeral fingerprint $SIGNING_FINGERPRINT)"

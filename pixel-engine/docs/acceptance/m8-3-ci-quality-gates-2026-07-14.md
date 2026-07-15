# M8-3 CI 与质量门禁验收

日期：2026-07-14
状态：通过
范围：PR required jobs、夜间设备/性能/soak、缓存边界、失败证据、干净环境复现与本地统一发布入口

## 1. 工作流拆分

`.github/workflows/pixel-engine.yml` 将原来的单一巨型 job 拆为七个可独立定位、并行执行的实体门禁，
再由一个只接受 `success` 的稳定聚合 job 收口：

| Job | 仓库内唯一入口 | 主要边界 | 超时 |
|---|---|---|---:|
| fast | `tools/pixel-ci-fast.sh` | secret、拆分模块/engine/demo/app JVM、golden/tooling、Compose sample | 25 min |
| api | `tools/pixel-ci-api.sh` | 九坐标源码/字节码/Metalava API、stable boundary、Unicode、KDoc、严格 MkDocs | 25 min |
| lint | `tools/pixel-ci-lint.sh` | 九个 SDK library 与 Launcher app 的 Android Lint | 25 min |
| instrumentation | Workflow 内显式命令 | API 29 独立模拟器的完整 `connectedDebugAndroidTest` | 35 min |
| consumer | `tools/pixel-ci-consumer.sh` | SPI、RouteEntry、旧二进制、最低/推荐矩阵、聚合与九坐标隔离 R8 consumer | 45 min |
| publication | `tools/pixel-publication-validation.sh` | 九坐标 AAR/POM/module/sources/Javadoc/metadata/依赖图 | 30 min |
| performance | `tools/pixel-ci-performance.sh` | 六场景 JVM 趋势与 Baseline Profile APK 打包 | 30 min |

`Required pixel-engine gate` 使用 `if: always()` 读取七个 `needs.*.result`，
`tools/pixel-ci-required-check.sh` 仅接受所有结果精确等于 `success`。`failure`、`cancelled`、`skipped`
和空输入均返回非零。分支保护只需绑定这个稳定聚合名称，不需要绑定会随矩阵变化的子 job 名称。

当前工作流使用 2026-07 已核对的主版本：`actions/checkout@v6`、`actions/setup-java@v5`、
`actions/setup-python@v6`、`gradle/actions/setup-gradle@v6`、`android-actions/setup-android@v4` 和
`actions/upload-artifact@v7`。七个实体 job 都显式安装 `platforms;android-36` 与
`platforms;android-36.1`；API job 使用 Python 3.12 和仓库固定的 `mkdocs==1.6.1`，不依赖 runner
偶然预装。

## 2. PR 与定时任务边界

PR、main/master 和开发分支执行七个有界 required job。`.github/workflows/pixel-engine-nightly.yml`
只承载较长场景：

- API 24/29/36 三档完整 instrumentation matrix；
- API 36 Macrobenchmark，显式 `PIXEL_BENCHMARK_SERIAL=emulator-5554`；
- clean 后连续三轮独立 lifecycle/resource soak；
- `Required nightly gate` 同样拒绝任一非 `success` 结果。

两份工作流都没有 retry、`continue-on-error` 或允许失败的矩阵配置。PR 的同分支旧 run 只在新的 PR
run 启动时取消；聚合器会把 cancellation 视为失败，不会把未执行项放行成候选发布。

## 3. 缓存与旧产物防护

Gradle Action 只缓存依赖和 Gradle User Home，不缓存工作区作为通过证据。具体门禁采用以下边界：

- fast、API 和 Lint 从 `clean` 开始，所有关键 Gradle 调用使用 `--no-build-cache`；
- instrumentation 先删除 XML/HTML/additional-output，再使用显式 emulator serial 和
  `--no-build-cache`；
- publication 先删除隔离 Maven 仓库和旧 JSON，再重新发布九个坐标并检查每个主文件 SHA-256；
- consumer matrix 使用一次性工程，重建共享 file-Maven 仓库并绑定本轮 publication report SHA；
- JVM performance 在运行前删除稳定报告，以本轮 `runId`、工作负载和七批中位值校验趋势；
- Baseline Profile 检查真实 release/benchmark APK 内条目，不以源码 profile 文件存在代替打包；
- golden 缺失或不一致只写 `build/reports` candidate/diff，不自动覆盖源码 baseline。

因此 Gradle/pip 下载缓存只能减少下载，不会恢复本应由当前源码生成的验收产物。

## 4. 失败证据与故障注入

七个实体 job 的 artifact step 都使用 `if: always()`，按职责上传：

- JVM/instrumentation XML、HTML 与安全报告；
- API/ABI/compatibility diff 和严格 MkDocs 站点；
- Lint 报告；
- golden candidate/diff、设备 additional-output 和 Perfetto trace；
- JVM/device benchmark、趋势与 Baseline Profile 报告；
- publication JSON 和实际隔离 Maven 仓库。

`tools/tests/test_pixel_ci_workflows.py` 锁定 job/needs 一一对应、七处 always-upload、证据路径、SDK
platform、API 24/29/36、Macrobenchmark、三轮 soak、无 `continue-on-error` 和统一发布入口。
`tools/tests/test_pixel_ci_entrypoints.py` 对六个本地分组注入固定退出码 23：四个 Gradle 型入口和两个
Bash 聚合入口均原样非零退出。`test_pixel_ci_required_check.py` 逐一验证 failure/cancelled/skipped 与空
结果被拒绝。最终 Python tooling 为 92/92，零 failure/error/skipped。

## 5. 本地正向复现

README 现已列出固定文档依赖、两个 SDK platform、六个无设备分组、API 29 模拟器命令、required job
语义、夜间边界、artifact 和最终统一入口。按 README 顺序，本轮从当前源码实际执行：

| 分组 | 结果 | 关键证据 |
|---|---|---|
| fast | 通过 | clean 后 286 tasks，单测/tooling/golden/sample 全部成功 |
| API/documentation | 通过 | clean 后 133 tasks 全部执行；九坐标 API、stable boundary、Unicode 17、KDoc、严格 MkDocs 成功 |
| Lint | 通过 | 385 tasks；九 SDK 模块与 app 无 error |
| publication | 通过 | 277 tasks；九坐标结构与 metadata 校验成功 |
| performance | 通过 | 六场景 `overallPass=true`；2 个 APK 的 Baseline Profile 打包成功 |
| consumer | 通过 | SPI/RouteEntry/旧二进制、两档工具链、聚合与九坐标隔离 consumer 全部成功 |

本轮 publication 报告为 14,183 bytes，SHA-256
`08ed32dc0e9a5df1bd8bb78189ccf2d8f159b6e4972e32fd0563ce82be6cd06c`。consumer 分组重建的
M8-2 publication/matrix 报告分别为 14,183/1,306 bytes，SHA-256
`6c0f2cc5a432907a183a99fb6d0c728587050d6a854ab2d419a64cc3d8816e23`、
`ca0702b1f55fb5829305cfb970b05a9e301c9e80ee567e7d70c11d17307f7c0b`。

性能趋势和 Baseline Profile 报告 SHA-256 分别为
`9f8d24f473b1e2cdd466e8e081d61ddc439b1411f41b6b246b5bcb11059c2e9e`、
`4d9e034d132e2c8ec68794d1667b740d79d05ea8e061786df2b3653b84d1682e`。

## 6. API 29 模拟器证据

本轮只为 CI instrumentation 启动 `Pixel_API_29`，确认 `ro.build.version.sdk=29` 后关闭动画，并执行：

```text
ANDROID_SERIAL=emulator-5556 ./gradlew \
  :pixel-engine:connectedDebugAndroidTest \
  --no-build-cache \
  --no-daemon
```

结果为 64/64 tests、0 failure、0 error、0 skipped。XML 为 11,126 bytes，SHA-256：
`f75dc8f00d8482154380ec0a7cdc471d01223f4f47046c2d0e34769eb3f06661`。测试完成后只通过
`adb -s emulator-5556 emu kill` 关闭该 AVD；没有向实体 Redmi 发送任何安装、启动或输入命令，原有
`emulator-5554` 也未被选为本轮目标。

## 7. 统一发布入口

`tools/pixel-release-check.sh` 保留并聚合全部无外部凭据门禁：安全/备份、API/ABI/Metalava/KDoc、
单测、Lint、Release、SPI、RouteEntry、旧二进制、发布物与最低/推荐消费者、九坐标隔离 consumer、
JVM performance、soak、Baseline Profile 和严格 MkDocs。文档依赖现在也从
`requirements-docs.txt` 固定安装，避免干净 clone 缺少 MkDocs 或使用不同版本。

在上述工作流、脚本、README 和本验收文档全部落地后，又从当前源码执行一次未删项的
`bash tools/pixel-release-check.sh`。Gradle 主批次 1,060 tasks 在 4m11s 内成功，随后 secret/backup、
SPI、RouteEntry、旧二进制、最低/推荐工具链矩阵、聚合与九坐标隔离 consumer、六场景 JVM
performance、soak、两个 APK 的 Baseline Profile 以及 `mkdocs build --strict` 全部通过；脚本最终
退出码为 0。这次复验是对分组证据的最终聚合确认，不依赖前一次构建的成功状态。

真实中央仓库发布、签名密钥和 GitHub 分支保护写入仍属于需要外部凭据/管理员权限的 M9 发布动作，
不纳入无凭据 CI 本身；工作流已提供唯一稳定的 `Required pixel-engine gate` 供管理员绑定。

## 8. 结论

M8-3 的五项任务和三项验收均满足：门禁已拆分，PR 与夜间职责分离，旧缓存不能制造通过，失败证据
始终上传，所有非成功状态和底层脚本失败都会阻断聚合结果，README 可以从干净环境复现。工作包范围
内 P0/P1 遗留为零；下一工作包为 M9-1 注释、KDoc 与文档站。

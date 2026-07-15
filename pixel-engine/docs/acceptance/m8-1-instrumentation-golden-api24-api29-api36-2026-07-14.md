# M8-1 Android instrumentation、Golden 与属性测试验收

- 日期：2026-07-14
- 工作包：M8-1
- 当前结论：已完成；Android 覆盖、确定性 golden、故障注入、属性测试、三档模拟器矩阵和未删项发布门禁全部通过
- 设备策略：只使用显式序列号的 AVD；本次没有向实体 Redmi 安装、启动或发送任何命令

## Android instrumentation 覆盖

现有 `pixel-engine/src/androidTest` 由 26 个 instrumentation suite/fixture 文件组成。M8-1 审计确认
以下真实 Android 路径都有直接行为测试，不以 JVM mock 代替：

- Host draw：legacy facade 精确像素、square-gap bitmap batch、frame diagnostics；
- 输入：tap/pressed/hover/drag cancellation、多 Host focus、精确文本分发；
- IME：默认隐藏编辑器、InputConnection、composition/batch/rebind、多 TextField 隔离；
- accessibility：virtual hierarchy、focus/hover、typed actions、character bounds、动态语义身份；
- lifecycle：attach/detach、pause/resume、Activity recreate、Fragment view recreate、多 Host 独立销毁；
- saved state：legacy/typed Navigator Bundle、多栈恢复和 Compose saved-state 回归；
- back：生产 Overlay/Navigator back、结果顺序和 Host 残留检查；
- predictive back：API 34+ start/progress/cancel/commit/discrete callback 和 stack mutation 中断。

predictive-back suite 使用 `@SdkSuppress(minSdkVersion = 34)`；WindowInsets、attributed text 和平台
capability 也按真实 SDK 分支由 runner 过滤。因此低 API 的执行总数较少，但 XML 的 `skipped=0`，
没有通过运行时 `Assume` 或删除测试隐藏失败。

## 确定性 exact-ARGB golden

新增 `M81DeterministicPixelGoldenTest`，固定：

- 仓库内置 `PixelBitmapFont.Default`，不读取系统字体；
- `PixelTester` 手动 scheduler/clock，动画帧严格位于 0、500、1000 ms；
- `ScreenProfile(48, 40, dotSizePx = 1)`，density 1、refresh 60Hz、零 inset；
- Light normal、Dark focused、HighContrast disabled、RTL，以及 2× textScale 五个环境。

输出按逻辑 y 坐标保存完整 `COUNT*ARGB` 行程，不做亮度分桶。已审阅源码基线为
`pixel-engine/src/test/resources/golden/m8-1-deterministic-pixels.txt`，225 行、13,398 bytes，SHA-256：

```text
e25d31ed306fd4fe68921a63c78d484253a6ce6f6d597a803e85e32ac6e73dd9
```

最终候选 `pixel-engine/build/reports/golden/m8-1/deterministic-pixels.actual.txt` 与基线 SHA-256 完全
相同，`build/reports/golden/` 下没有遗留 `.diff`。

## Golden 审阅边界和故障注入

新增 `ReviewedGoldenVerifier` 后，`EngineGoldenTest`、`AnimatedOpacityTest`、
`PixelThemeRenderGoldenTest`、`M52HighValueComponentSnapshotTest`、
`M53AdaptiveLocalizationGoldenTest` 和 M8-1 综合基线统一遵守以下规则：

1. 每次测试都只向 `build/reports/` 写 `.actual.txt`；
2. 不一致时生成带前后文、删除行和新增行的 `.diff` 并失败；
3. 基线缺失也失败，不在源码目录创建文件；
4. 完全一致时清理同一报告位的过期 diff；
5. 没有 `REGEN_GOLDEN`、`UPDATE_GOLDEN` 或无条件 accept 入口。

`ReviewedGoldenVerifierTest` 主动注入并验证三类故障：

| 故障 | 期望结果 | 实际结果 |
|---|---|---|
| 第二个像素 `FFFFFFFF → FFFFFF00` | 失败、生成逐行 diff、基线不变 | 通过故障探针 |
| 删除 semantics `SET_TEXT` action | 失败、生成 action diff、基线不变 | 通过故障探针 |
| `pause/destroy` 生命周期顺序互换 | 失败、生成顺序 diff、基线不变 | 通过故障探针 |

因此 Goal 要求的“故意改变一个像素、semantics action 或生命周期顺序时对应测试失败”由可重复的
负向单测直接证明，而不是只依赖人工说明。

## Property / fuzz

新增固定种子属性测试：

- `M81ResourceParserPropertyFuzzTest`：300 份合法全类型 catalog 摘要/二次解析等价，2,000 份任意
  manifest 文本 total outcome，300 份合法随机 PGLY 往返，2,000 份任意 glyph bytes total outcome；
- `M81NavigationPropertyFuzzTest`：250 个 1–20 层 typed route stack 参数/state/identity 往返，逐轮
  单字节破坏和截断都结构化拒绝且安全根对象身份不变；
- 同一导航 suite 再执行 5,000 步 push/pop/remove/replace/clear，与独立最小参考模型每步对照，
  结束时检查每个创建过的 entry 恰好 dispose 一次。

四个固定种子分别覆盖资源 JSON、glyph binary、typed snapshot 和 Navigator state machine；失败消息
包含 seed/iteration，可以在本机和 CI 精确复现。

## API 24 / 29 / 36 模拟器矩阵

三档都用同一最终工作树、冷启动 AVD、`ANDROID_SERIAL=emulator-5556` 显式绑定后执行：

```text
./gradlew :pixel-engine:connectedDebugAndroidTest
```

| AVD | Android/API | tests | failures | errors | skipped | XML time | XML SHA-256 |
|---|---|---:|---:|---:|---:|---:|---|
| `Pixel_API_24` | 7.0 / 24 | 62 | 0 | 0 | 0 | 15.528s | `e221093db49ef54149e501744cfa971d7afbd40a560c84c7f03951fc264ed4f4` |
| `Pixel_API_29` | 10 / 29 | 64 | 0 | 0 | 0 | 55.932s | `a611515f6394d9f300035ad03d8ee7234f8385ebdfe4ebb1b83fa145ceff8ca7` |
| `Pixel_API_36` | 16 / 36 | 65 | 0 | 0 | 0 | 66.862s | `ead05abce7c00b22b6faf8f1db3bbaf66c399519dd2216d52de07a61865c8478` |

原始 XML 分别归档在：

- `pixel-engine/build/reports/acceptance/m8-1/api24/`
- `pixel-engine/build/reports/acceptance/m8-1/api29/`
- `pixel-engine/build/reports/acceptance/m8-1/api36/`

矩阵首次运行确实发现并修复了两个平台边界问题：

- API 29 ICU 63 / Unicode 11 与 API 36 ICU 76 / Unicode 16 对 Unicode 17 corpus 的已知差异，按
  精确 SDK/ICU/Unicode profile、case id、双方 boundary 和人工理由加入只读差异表；任何 tuple 或
  boundary 漂移仍会失败；
- API 36 AVD 同时发布平台 cutout 和测试 source 注入的 fold/hinge，旧测试错误假定 feature 列表
  只有一个。生产聚合正确，测试改为按 feature type 精确选择目标，同时保留多 feature 输入。

两处都先由完整矩阵失败暴露，再执行定向复验和完整 suite 复验；没有降低断言或跳过平台分支。

## JVM 与最终发布门禁

最终 `:pixel-engine:testDebugUnitTest` 为 1,187 tests、0 failures、0 errors、0 skipped。随后显式设置
`ANDROID_SERIAL=emulator-5554`，执行未删项 `bash tools/pixel-release-check.sh`，退出码 0：

- 全模块 API/ABI、stable/ownership、KDoc、Lint、Debug/Release/R8；
- secret/backup、RenderObject SPI、RouteEntry、冻结旧二进制；
- legacy 聚合与 core/runtime/widgets/navigation/android/testing/debug/compose 隔离消费者；
- 六场景 JVM 性能趋势、soak、Benchmark target 和两个 APK Baseline Profile 打包；
- 严格 MkDocs。

JVM 性能报告 `overallPass=true`，趋势报告 `overallPassed=true`。M8-1 没有新增 public API，三份聚合
API/ABI baseline 保持通过。工作包范围内 P0/P1 遗留为零，下一工作包为 M8-2 外部消费者兼容矩阵。

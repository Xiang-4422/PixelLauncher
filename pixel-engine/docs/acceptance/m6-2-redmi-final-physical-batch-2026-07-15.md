# M6-2 Redmi API 31/60Hz 最终实体批次（2026-07-15）

## 结论

Redmi K20 Pro、Android 12/API 31、60.000004Hz 的完整最终批次已通过当前设备的绝对性能与
证据门禁：Macrobenchmark 7/7、七场景各 10 次、70 份 Perfetto；Microbenchmark 精确
11/11、11 份 Perfetto、9 份 Method Trace，另外两项由 AndroidX 因预计跟踪时长会触发 ANR
而主动跳过 Method Trace。机器报告为 `evidencePassed=true`、`absoluteFramePassed=true`，
且 Macro/Micro 全部 `thermalThrottleSleepSeconds=0`。

初始机器报告因当时尚无批准 baseline 而记录 `trendPassed=false`、`overallPassed=false`。用户随后
明确回复“批准”，候选已转为只适用于完全相同设备与构建配置的正式 baseline。批准后的独立复跑
没有通过 10% 趋势门禁；Perfetto 进一步证明拒收批次存在超大核频率环境失配，不能把它解释成
SDK 回退，也不能把失败批次挑选拼接为通过。API 24、29、36 代表实体设备、真实 120Hz 设备和
频率全程可比的同配置复跑仍未完成，M6-2 继续保持`进行中`。

## MIUI 设备前置条件闭环

首次 Microbenchmark 失败包含两个相互独立的 MIUI 限制，均保留失败证据，没有通过抑制
AndroidX 错误或降低测试范围放行：

1. 设备处于 Dozing/锁屏状态时，MIUI 日志明确记录
   `Permission Denied Activity KeyguardLocked`，`IsolationActivity` 最终超时；
2. 唤醒并解锁后，测试包仍受到 MIUI 后台 Activity AppOp 限制，日志记录
   `Abort background activity starts`。独立诊断证明测试包的 `MIUIOP(10021)` 从 `ignore`
   调整为 `allow` 后，官方 `AndroidBenchmarkRunner` 能创建、启动、恢复并显示
   `IsolationActivity`。

实体设备包装器因此在任何 Magisk 策略和 benchmark 动作前后验证：

- `mWakefulness=Awake`；
- `mStayOn=true` 与 `mStayOnWhilePluggedInSetting=7`；
- Keyguard 明确为未锁定状态。

Microbenchmark 使用 `PixelAndroidBenchmarkRunner`。它只在厂商为 Xiaomi 时，以
`UiAutomation` 临时 Shell 身份把当前测试包的 AppOp 10021 设为 `allow` 并回读验证；它不修改
SDK、目标应用或其他包，不抑制 AOT、环境、计时或 profiler 错误。UTP 结束后测试包正常卸载。

包装器与连接设备合同测试包含锁屏时必须在 Magisk/benchmark 前失败的负向用例；批准后又补充
运行中降频拒收与原始 Dozing 状态恢复用例。当前实体包装器和设备 checker 合计 20/20 通过，
相关脚本 `bash -n` 通过。

## Macrobenchmark 正式结果

JUnit 为 7/7、0 failure、0 error、0 skipped，测试总时间 288.021 秒。五个帧场景的固定
60Hz 门槛为 CPU p95 不超过 11.6667ms、CPU p99 不超过 16.6667ms、jank 严格低于 1%。

| 场景 | 帧数 | CPU p95 | CPU p99 | overrun p95 / p99 | jank | 结果 |
|---|---:|---:|---:|---:|---:|---|
| animation | 432 | 4.953ms | 7.268ms | -13.811 / -7.628ms | 0/432，0% | 通过 |
| list scroll | 1,040 | 11.571ms | 13.171ms | -5.746 / -1.381ms | 5/1,040，0.481% | 通过 |
| overlay | 32 | 8.311ms | 8.376ms | -6.013 / -5.765ms | 0/32，0% | 通过 |
| page transition | 51 | 5.949ms | 6.138ms | -6.791 / -6.599ms | 0/51，0% | 通过 |
| text input | 28 | 10.316ms | 10.527ms | -2.529 / -1.675ms | 0/28，0% | 通过 |

列表滚动是本批次最接近门槛的场景：CPU p95 仍有约 0.096ms 余量，5 个正 overrun 占
0.480769%，低于固定 1% 上限。该结果按原始 1,040 帧如实保留，不能用 p95/p99 为负 overrun
或历史零 jank 批次把这 5 帧隐藏掉。

启动指标不属于当前固定帧门槛，但已保留完整十次分布：冷启动 TTID median/p95 为
512.104/700.848ms，热启动为 219.862/237.652ms。

## Microbenchmark 与 ANR 安全证据

Microbenchmark JUnit 为 11/11、0 failure、0 error、0 skipped，测试总时间 153.667 秒；11 项
均保留一份 Perfetto。九项同时保留 Method Trace。两项 Canvas 最坏场景的 AndroidX 原始消息为：

- `fullBrightnessNoGapCanvasSubmit`：预计 5.875209 秒，跳过 Method Trace 以避免 ANR；
- `squareGapGridCanvasSubmit`：预计 14.094897 秒，跳过 Method Trace 以避免 ANR。

没有设置 `androidx.benchmark.profiling.skipWhenDurationRisksAnr=false` 强制关闭保护。设备性能
检查器只在“Perfetto 完整、Method Trace 描述符完全缺席、同类同方法消息文件匹配 AndroidX
完整固定安全提示”时接受该状态，并把它记录为 `skippedAnrRisk`。提示缺失、提示伪造、已有
Method Trace 描述符但文件缺失仍是证据失败。对应正负向工具测试均通过。机器报告分别记录
`capturedCount=9`、`skippedAnrRiskCount=2`，没有把两份消息冒充成 trace 文件。

## 初始机器门禁与候选边界

机器报告使用 measurement id
`redmi-k20-pro-api31-60hz-miui-runner-final-20260715`，并绑定以下身份：

- model/device：Redmi K20 Pro / `raphael`；
- fingerprint：`Xiaomi/raphael/raphael:12/SKQ1.220303.001/V14.0.23.2.6.DEV:user/release-keys`；
- Macro：`benchmarkRelease`、`CompilationMode.Partial(BaselineProfileMode.Require)`；
- target：`benchmark`；Micro：`releaseAndroidTest`、`speed`；
- 被测 target APK 的 Baseline/Startup Profile 打包验证为 `status=passed`。

报告结果：

| 门禁 | 结果 | 说明 |
|---|---|---|
| evidence | 通过 | 设备身份、精确场景、APK/Profile、70 Macro Perfetto、11 Micro Perfetto、9 Method Trace 与 2 份 ANR 安全消息完整 |
| absolute frame | 通过 | 五个关键帧场景同时满足 p95、p99 与 60Hz jank 固定阈值 |
| trend | 失败 | `baselineComparison.status=missing`，没有提供人工批准的同配置 baseline |
| overall | 失败 | 总门禁是 evidence、absolute 与 trend 的合取，不因生成候选而自动批准 |

同时生成的 `device-baseline-candidate.json` 保持候选状态；当前报告不把它回喂为 baseline，也不
用当前结果与自身比较制造假通过。

## 基线批准与独立复跑

用户在审阅设备身份、固定绝对阈值、70 份 Macro Perfetto、11 份 Micro Perfetto、9 份 Method
Trace 和 2 份 AndroidX ANR 安全跳过后明确批准该候选。正式 baseline 为：

`pixel-engine/performance/baselines/device/redmi-k20-pro-api31-60hz.json`

- 状态：`approval.status=approved`；
- 批准时间：`2026-07-15T15:57:46Z`；
- 批准人：`repository-owner (explicit Codex approval)`；
- SHA-256：`7d5288487d4138bae5db66f3ec9055dc82960397379ddb3cadb0b607ef3bbfcb`；
- Macro 测量源码 SHA-256：
  `f943dabd61df30f5fd5b9cece6dc475424b2f39284c2ea2732cfb3b9dbde515e`；
- 适用边界：同一 Redmi K20 Pro/raphael、API 31、60.000004Hz、相同 fingerprint、variant、
  compilation policy 和测量源码；回退上限仍为 10%。

批准后的第一次独立 Micro 复跑为 11/11、11 份 Perfetto、9 份 Method Trace，归档 76 个文件，
`SHA256SUMS` 摘要为
`974c3b4549cb4ac8567c78f945a256f39e7e3ed05a5c80eba31b6b9702a194e2`。第一次和第二次 Macro
尝试分别在 5/7 后及首项 `CompilationMode.Partial` 的 package compile 返回阶段挂起；失败证据
完整保留，摘要分别为
`9af9e8e44338d0916e7c9f1a079e40bdd69083d60f10096f6d065b68c2c18458` 和
`760497c16bfdca752b647eb82ebf8cbf497b9548f252c107ecee5fb46aee3e0c`，没有作为性能结果使用。
设备重启后直接 `cmd package compile` 在 7.4 秒返回，完整 Macro 复跑为 7/7、70 份 Perfetto。

完整复跑的证据和绝对门禁通过，但 39 项趋势比较中有 9 项超过 10%：冷/热启动 median 分别
`+12.598%/+34.354%`，Overlay p95/p99 为 `+14.138%/+25.414%`，页面转场 p95/p99 为
`+34.755%/+37.063%`，Micro 的 gap allocation、clipped overflow time 和 fast lazy list time
分别为 `+13.333%/+15.681%/+23.924%`。因此机器报告保持 `trendPassed=false`、
`overallPassed=false`，没有用绝对通过覆盖趋势失败。该批次归档 123 个文件，`SHA256SUMS`、
机器报告和 Profile 打包报告 SHA-256 分别为：

- `d0d0be59b60f1f64199ca8248790768c46406f28059494f11c43fad4b1fe329c`；
- `bf1ba77cdc3033162986f61c1282764c9070bd12bc3b1a73295cf02c15d8270b`；
- `788582e8311c5addd238441babfce864e0794441ce02bd8ce508aa43173314a8`。

## Perfetto 频率归因与采集门禁加固

第二次独立 Micro 复跑本身为 11/11，但 11 项 time median 全部比 baseline 慢
19.351%–54.013%，因此作为环境不一致批次拒收。两份 `pixelBufferOperations` trace 的
`BenchmarkRunner` 都主要运行在 CPU 7；baseline 的 CPU 7 时间加权频率为 `2946074.6kHz`，
拒收批次仅 `2005490.0kHz`。同一外层 Method Trace 从 `17.556ms` 增加到 `27.171ms`，与频率
比例一致。拒收批次的 `58.901ms` D 状态从被测调用结束后 `440852ns` 才开始，不与 SDK 被测
路径重叠；全局长 `BATTERY_CHANGED` slice 也不在 `BenchmarkRunner` 被测调用上。

AndroidX JSON 在两批中都报告 `cpuLocked=true`、`thermalThrottleSleepSeconds=0`，但慢批次实际
CPU 7 的 `scaling_max_freq` 只有 2.016GHz。因此仅依赖 AndroidX 环境字段不足以证明可比，根因
是 MIUI 运行中改写 cpufreq，而不是 SDK 路径回退。完整 Perfetto SQL 证据链位于拒收归档：

`build/reports/performance/m6-2/redmi-k20-pro-api31-60hz-approved-baseline-rerun-2-micro-2026-07-15/additional-output/PixelEngineMicrobenchmark_pixelBufferOperations_2026-07-15-16-28-13.perfetto-trace_analysis.md`

该拒收归档共 78 个文件，`SHA256SUMS` 摘要为
`3a231c320f41d3a17d1391cc207e4cad67eecad134016e900630e32d9962a668`。This concludes the trace
analysis：现有证据足以排除被测 SDK 路径阻塞，并把该批次归类为 CPU 频率环境不一致。

实体包装器据此新增以下 fail-closed 不变量：

1. 屏幕仍休眠时等待超大核 `scaling_max_freq == cpuinfo_max_freq`，并保存/临时切换/恢复
   MIUI 性能与均衡模式；
2. root 可用时只把 AndroidX 实际选择的超大核固定为 `userspace` 和硬件最高频率，保存并恢复
   原 governor/min/max；
3. 在唤醒后、Magisk settle 后、Gradle 启动前重复验证 governor、current、setspeed 和 max；
4. 在设备端每秒采样整个 Gradle 窗口，第一次不匹配即写入 `.violation`，即使 benchmark 本身
   成功也拒收；底层非零退出码仍优先保留；
5. EXIT trap 停止看门狗、恢复 Magisk/CPU/功耗/刷新率/常亮，并在设备原先非 Awake 时恢复
   Dozing，最后删除两类唯一运行临时文件。

20 项隔离合同测试全部通过，测试日志 SHA-256 为
`e34044d18083f90b9b44efccd052bccf4d60e1f0458a52b0b001af04a39acdd1`。Redmi 零构建真实烟测
也通过；日志 SHA-256 为
`1c15f4a8372fc6b96143c9eab454480f2c7fce84201f34d575a2dc19ef03c573`。恢复证据 SHA-256 为
`bfa139cc4b84a2f32b331d649364f4b4020902a4ef59ca39183b0fec1d9cd6c0`，复核 governor/min/max、
90Hz、均衡模式、常亮、Magisk root、Dozing 和临时文件均恢复原状。实体设备负向烟测又用故意
不匹配的目标频率验证 `/system/bin/sh` 会生成
`reason=prime-cpu-frequency-invariant-broken`、完整频率快照和 done 标记，并在最后删除临时文件；
日志 SHA-256 为 `f9f68377ba6cdd511fe76c40a78f3eed4ca6d3ff18440603aaa70946b516b84f`。

随后执行首个带看门狗的完整 Micro 独立复跑。instrumentation 为 11/11、Gradle
`BUILD SUCCESSFUL in 2m 54s`，但看门狗在 `2026-07-15T17:11:52Z` 捕获 CPU 7 的 current、
setspeed、max 同时从预期 `2956800kHz` 被改写为 `2841600kHz`，因此包装器最终退出码为 1。
这证明真实 11/11 也不能绕过频率门禁。本批次 80 文件拒收归档的 `SHA256SUMS` 摘要为
`56c26921dc68bfd95798efc95093afca43e40dbccf61f9f0085eb9b1fb934468`，清理后设备再次完整恢复。

## 原始产物与摘要

Macro 正式归档：

`build/reports/performance/m6-2/redmi-k20-pro-api31-60hz-macro-miui-runner-formal-2026-07-15/`

- 122 个文件，包含 70 份 Perfetto、7 份 AndroidX 消息、原始 JSON、JUnit/UTP/HTML、精确
  benchmark/target APK、测量源码、Baseline Profile 报告、设备状态、包装器、checker、机器
  门禁报告、未批准候选和全量摘要；
- `SHA256SUMS` SHA-256：
  `a62f7e8be435023bd0584db025c7e305bf538a363f19cbfa08daa72d51f95d76`；
- Macro JSON / JUnit XML SHA-256：
  `2c32eee25f3b72f4109b1353b4914cdd8f3cb60bb8692d45084c1b4e4b99b75c` /
  `c41c9e8c4f33fa9b0fac390c14353a34c4910e2bb67c44f09040a083c7054398`；
- 机器报告 / 未批准候选 SHA-256：
  `23dd314ecd5694b826a6d9a176d0875e64c6d9c49787ee871ff7a31b25d703d3` /
  `7e3ea8ed4e2955df4dd20c1032d4a81e2b1a9b090479ff2b71338bb102c450bf`；
- benchmark / target APK SHA-256：
  `35a987c6a737647ac1758942e03ff40186e5751b4b9dc719e706217ab24d24cf` /
  `9136d2172092f12450b01eea16e6d412103e49a68d6b1262ce914f473da27321`。

Micro 正式归档：

`build/reports/performance/m6-2/redmi-k20-pro-api31-60hz-micro-miui-runner-formal-2026-07-15/`

- 73 个文件，包含 11 份 Perfetto、9 份 Method Trace、11 份消息、原始 JSON、JUnit/UTP/HTML、
  精确测试 APK、runner/Gradle/包装器源码和设备状态；
- `SHA256SUMS` SHA-256：
  `7b204d371bcd7b4c560e90a020510e9ef1cd3f49a9cc88e0b4e6d4ff773a7dd8`；
- Micro JSON / JUnit XML / APK SHA-256：
  `0a48e64cb396005534559c06fc3fc4c7656c91fe003437710cfbb9ef1d3fa062` /
  `c0521854da52e3aee3aa0f26dae8c1f53b9bae0ca2d2cda58928170f09386f3b` /
  `9367c2ccf53055c1246d42ed0be5f82f91f422629172b5072584118c51c8a660`。

锁屏失败证据保留在
`build/reports/performance/m6-2/redmi-k20-pro-api31-60hz-micro-keyguard-locked-rejected-2026-07-15/`，
其 `SHA256SUMS` SHA-256 为
`6c631c03a5f3030bfc9bd610cd87e0ad5533407a92a2bccb966164e87a2633ef`。单项官方 runner 验证证据
保留在 `redmi-k20-pro-api31-60hz-micro-miui-isolation-targeted-2026-07-15/`，其清单摘要为
`ec2bf4fbbff1eb38ff1e4563bb0f6c0c7b885ac70692a2132a161bcc9e6ba0`。

## 设备恢复与剩余工作

正式批次与看门狗烟测结束后复核：Magisk root 为 `uid=0`；Shell policy 为
`logging=1|notification=1|policy=2|uid=2000|until=0`；刷新率恢复
`peak/min/user=90/90/90`；`stay_on_while_plugged_in=0`；设备端没有
`pixel-magisk-benchmark-*` 或 `pixel-cpu-frequency-*` 临时文件；原始屏幕状态恢复为 Dozing。

M6-2 剩余工作不再是 Redmi 当前绝对阈值优化，而是：

1. 在 API 24、29、36 代表实体设备上取得同样的完整正式分布；
2. 在真正 120Hz 代表实体设备上取得 120Hz 分布，不能用 60Hz 或模拟器替代；
3. 在当前已批准 baseline 的完全相同配置下，取得 CPU 频率不变量全程成立的独立 Macro/Micro
   完整复跑，并证明全部 39 项关键指标回退不超过 10%。

在上述三项闭环前，不勾选 M6-2，也不触发依赖它的 M9-3 正式候选冻结。

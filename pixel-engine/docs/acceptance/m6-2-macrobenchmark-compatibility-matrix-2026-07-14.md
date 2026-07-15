# M6-2 Macrobenchmark 系统兼容矩阵阶段证据

## 结论

2026-07-14，在不操作实体手机的最终安全夹具上，Macrobenchmark 七个关键旅程已分别在
API 24、29、36、37 模拟器通过；API 37 运行时页大小为 16,384 bytes。四档均为 7/7，
0 failure、0 error、0 skipped，并各自保留 7 份 AndroidX 指标消息与 7 条 trace。

本报告只证明系统兼容、真实 Android Host 行为和指标采集链路，不把单次 emulator dry-run
冒充代表性设备性能基线。2026-07-15 已另行完成 API 37 模拟器 30 分钟设备 soak 与精确
10,000 次 Host-like 压力，见
[设备长跑验收](m6-2-device-soak-api37-2026-07-15.md)。M6-2 仍保持`进行中`：120Hz 代表设备、
同配置批准 baseline、10 次正式分布和绝对/趋势门槛尚未通过；现有 Redmi 60Hz 正式采样
也明确未通过绝对性能门槛，不能用兼容矩阵或模拟器长跑覆盖。

## 2026-07-15 API 37 完整模拟器性能演练

按用户当前设备策略，仅对显式 `emulator-5554` 执行完整候选演练。设备为 API 37、Android 17、
16KB、60.000004Hz、4 vCPU；Macrobenchmark 七场景各 10 次，JUnit 7/7，保留 70 个 Perfetto
trace。Microbenchmark 11/11，保留 11 个 Perfetto 与 11 个 Method Trace；两个测试批次均无
thermal throttle sleep，Baseline Profile 打包校验通过。

机器报告使用显式 `--evidence-mode emulator-rehearsal --report-only`。报告中
`evidencePassed=true`，但 `representativePerformanceEvidence=false`、趋势状态为
`nonRepresentative`，因此总体状态保持 `failed`。五个帧场景中列表、Overlay、页面切换和文本
输入通过模拟器绝对阈值；动画首次分布 p95/p99 为 5.309/30.677ms，14/443 帧正 overrun，
jank 3.160%，没有隐藏这次失败。

Perfetto 对最慢动画 trace 的系统级审计显示，89.786ms 最慢帧窗口内 4 vCPU 非 idle 利用率为
89.04%，此前等长窗口为 19.49%；加权 runnable thread 数从 0.545 升至 4.586。目标进程、
SurfaceFlinger、ranchu graphics composer、SystemUI 和 system_server 同时增载；目标 UI 与
RenderThread 没有 D/I/O wait，也没有 GC/JIT 峰值。目标 `postAndWait` 的 39.873ms 中 UI 睡眠
39.148ms，同期仍有 RenderThread flush/swap、SurfaceFlinger composite/present 和 ranchu present。
证据只支持“模拟器客体范围瞬态 CPU/图形合成拥塞与目标渲染工作重叠”，不支持归因为持久的单一
引擎热路径，也没有足够证据声称是宿主机 vCPU deschedule。

保留首次失败后单独确认动画 10 次、444 帧：CPU p95/p99/最大值为
4.257/5.350/7.599ms，正 overrun 为 0。该结果证明首次拥塞未在紧接的确认批次复现，但不覆盖
首次失败，也不升级为发布证据。

原始目录为
`build/reports/performance/emulator-candidate/api37-60hz-2026-07-15/`。完整演练报告、Macro JSON、
Micro JSON、确认 JSON 的 SHA-256 分别为
`12199312cd236a3b12cf28d91a03f8d80bdbb9e8e126565f2c183e9cca9e97f2`、
`9847eda8eea9df4509140fd736bff71a331efe4adc03d6b02bca085ef7a6060d`、
`c818752e310a8d64092702f74d7ddc5657411d1ca3479c74dd38fde0b96ce41f`、
`68526d4ff000980628c018e6de323670829d30563790bbd4f0ff6b91d9c0194c`。最慢 trace 的逐步证据链与
SQL 结论保存在同目录的 `.perfetto-trace_analysis.md`。

门禁同时增加双向身份约束：默认实体模式拒绝 AVD，演练模式拒绝实体身份；演练候选即使被手工
标为 approved 也固定不能通过趋势门禁。116 个工具单测全部通过，其中包含这些负向合同。
M6-2/M6-3 状态没有变化，仍等待代表性实体设备证据。

## 设备隔离

所有 connected benchmark 现在必须经 `tools/pixel-connected-benchmark.sh`：

- `PIXEL_BENCHMARK_SERIAL` 必填，不允许 adb 默认选设备；
- `ANDROID_SERIAL` 与 AGP `android.injected.device.serial` 同时固定部署目标；
- 包装脚本从目标读取硬件序列号，AndroidJUnitRunner 在任何 `Home`、Activity 启动或输入动作前
  再次核对当前设备的 `ro.serialno`；
- `ro.kernel.qemu != 1` 默认在 Gradle 启动前失败，实体设备只有当前调用显式设置
  `PIXEL_BENCHMARK_ALLOW_PHYSICAL=1` 才能运行；
- 已移除每轮全局 `Back + Home`。目标 Activity 使用 `stateAlwaysHidden` 管理 IME，帧场景只
  `killProcess()` 目标包，不遍历或改变用户任务栈。

定向验证显示，模拟器测试前后 Redmi 均未安装 `com.purride.pixelbenchmark.target`，前台包未变化。
包装脚本四项单测覆盖缺少序列号、模拟器三重绑定、实体设备默认拒绝及显式实体授权。

## 最终矩阵

| 档位 | JUnit | 指标契约 | 原始证据 |
| --- | ---: | --- | --- |
| API 24 / Android 7.0 / 60Hz | 7/7 | 七项全部包含 `gfxFrameJankPercent`、p50/p90/p95/p99、`gfxFrameTotalCount` | `build/reports/performance/matrix/api24-final-2026-07-14/` |
| API 29 / Android 10 / 60Hz | 7/7 | 冷/热启动 TTID；五项帧旅程 `frameCount`、`frameDurationCpuMs` | `build/reports/performance/matrix/api29-final-2026-07-14/` |
| API 36 / Android 16 / 60Hz | 7/7 | 冷/热启动 TTID；五项帧旅程 CPU 与 overrun 分布 | `build/reports/performance/matrix/api36-final-2026-07-14/` |
| API 37 / Android 17 / 60Hz / 16KB | 7/7 | 冷/热启动 TTID；五项帧旅程 CPU 与 overrun 分布 | `build/reports/performance/matrix/api37-16kb-final-2026-07-14/` |

七个精确方法为 `coldStartup`、`hotStartup`、`listScroll`、`textInput`、`animation`、
`pageTransition`、`overlay`。列表旅程执行五次真实 touchscreen swipe，并在测量窗口内要求出现
行号至少为 20 的生产列表节点，不能以同名 API 或空 trace 代替行为验收。

API 24 的平台镜像未正确挂载完整 tracing 文件系统，因此按源码中的正式版本分支使用
`FrameTimingGfxInfoMetric`；API 29+ 使用 `StartupTimingMetric`/`FrameTimingMetric`。API 24
仍保留 AndroidX 生成的 trace 文件，但其性能语义以 gfxinfo 指标为准。

## 机器门禁

执行：

```bash
tools/pixel-macrobenchmark-compatibility-check.sh
```

`tools/check_macrobenchmark_compatibility.py` 会阻止以下失真：

- API 档位不是精确的 24/29/36/37；
- JUnit 不是精确七项或存在 failure/error/skipped；
- 设备名、dry-run/emulator 身份或 API 37 16KB 元数据不匹配；
- 任一 API/场景缺少该平台应有指标；
- 任一消息未引用恰好一条非空 trace；
- 证据目录混入另一轮消息或 trace。

机器报告：
`build/reports/performance/matrix/macrobenchmark-compatibility-report.json`，状态为 `pass`，
SHA-256 为 `9958f6b09aae73228dfa095fb045145f8fd3534d18910bfde108e5bbb2ad1733`。
报告明确写入 `representativePerformanceEvidence=false`。输入清单
`pixel-engine/performance/macrobenchmark-compatibility-matrix.json` 的 SHA-256 为
`64fea5bd70428bf90551f3c2c8d1ff9587dcf6f0b3bb58d80a8b46ee0fed2f5b`。

## 验证命令

```bash
python3 -m unittest \
  tools.tests.test_check_macrobenchmark_compatibility \
  tools.tests.test_pixel_connected_benchmark -v

tools/pixel-macrobenchmark-compatibility-check.sh
```

结果为 8/8 单测通过，四档矩阵报告通过。四次设备运行均使用同一最终源码和
`benchmarkRelease` 近 Release 目标，dry-run 只把迭代数降为一，不删除旅程、指标或行为断言。

## 后续工作

1. 在明确授权的 60Hz 与真正 120Hz 代表性实体设备上，以每场景 10 次采样建立候选基线；
2. 基于现有绝对门禁定位并优化 Redmi 失败场景，优化前后必须是同设备、同构建、同 profile；
3. 保持夜间 30 分钟模拟器 soak，并在代表性设备候选采集时复跑同一机器门禁；
4. M8 最终矩阵收口时把本兼容门禁与完整 instrumentation/consumer CI 组合，不以本文提前标记
   M6-2 或 M8 完成。

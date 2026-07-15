# M6-2/M6-3 Redmi API 31 60Hz 实体性能证据（2026-07-15）

## 结论

本轮在 Redmi K20 Pro、Android 12/API 31、60Hz 实体设备上完成可审计的完整性能采集。Macro
7/7、Micro 11/11、Baseline Profile 精确 APK 打包、trace 数量、设备身份和热状态证据均通过，
并补齐 M6-3 所需的同一设备优化前后 trace 与统计，因此 M6-3 可以标记完成。

M6-2 不通过：列表滚动与文本输入没有达到 Goal 固定的绝对帧阈值，同时没有批准 baseline，不能
执行有效的同配置回退比较。候选报告保持 `status=failed`，没有放宽 p95、p99、jank 或 10% 回退
阈值，也没有批准失败候选。

## 设备与采集边界

正式设备身份：

- model/device：Redmi K20 Pro / `raphael`；
- fingerprint：`Xiaomi/raphael/raphael:12/SKQ1.220303.001/V14.0.23.2.6.DEV:user/release-keys`；
- API：31；显示模式：1080×2340、60.000004Hz；
- 测量期间电量 100%、温度 31.7°C、Thermal Status 0；
- Macro 使用 `benchmarkRelease`、`CompilationMode.Partial(BaselineProfileMode.Require)`；
- Micro 使用 `releaseAndroidTest`、speed 编译，并显式保留全部 Method Trace。

正式采集前停止了同一序列号上的其他 adb/UI 自动化；此前受 AndroMeld 和另一自动化任务并发影响的
两轮运行继续作废。测量完成后设备刷新率恢复为原始 90Hz，`stay_on_while_plugged_in` 恢复为 0，
临时 shell/Magisk 策略恢复为原值。

## 完整性与原始产物

Macro 归档：

`build/reports/pixel-benchmark/physical-redmi-k20-pro-api31-60hz-20260715-macro/`

- 七个测试、每项 10 次、JUnit 7/7，70 份 Perfetto，thermal sleep 为 0；
- 原始 JSON SHA-256：`a64f554ab705b2b985093bcd61d605b2113aad38ec8f50c35fb8c8485de4190f`；
- JUnit XML SHA-256：`516e982e90b6b5c2c8f9c99b7874afd159089099808198a39b77fd47a08a0105`；
- 测量时 benchmark source SHA-256：`e11e0197b387decba2c3c2e108121dd2ffb92a90b83866935bef1221f80bdf76`；
- 被测 `benchmarkRelease` APK SHA-256：
  `ee4330af92473bb7311ec02c424b285ef3cb4d34726a359aee0d8dbf241d08f2`。

Micro 归档：

`build/reports/pixel-benchmark/physical-redmi-k20-pro-api31-60hz-20260715-micro/`

- 精确 11 项、JUnit 11/11，11 份 Perfetto、11 份 Method Trace，thermal sleep 为 0；
- 原始 JSON SHA-256：`18c5ed7b99a25af7e2033c79cc6385aa398d82e4510d89ea2b676b26321abad5`；
- JUnit XML SHA-256：`38bb82be8e2e070a94adc79bcb475919fb8550a508491906a5ab5338e7b7f42a`；
- 测试 APK SHA-256：`8060d904e0d2b3394867ee8b2ce6581de507964df0f5128db059fa09be4cf4ef`；
- `SHA256SUMS` 覆盖归档内除清单自身之外的全部文件。

精确被测 APK 的 Baseline/Startup Profile 检查为 `status=passed`。设备门禁报告和未批准候选位于：

`build/reports/performance/device-gate/redmi-k20-pro-api31-60hz-20260715/`

measurement id 为 `redmi-k20-pro-api31-60hz-20260715T0419Z`，报告明确记录
`evidenceMode=physical`、`representativePerformanceEvidence=true`、`evidencePassed=true`、
`overallPassed=false`。

## M6-2 绝对门禁结果

60Hz 的固定阈值是 p95 不超过 11.6667ms、p99 不超过 16.6667ms、jank 低于 1%。

| 场景 | CPU p95 | CPU p99 | jank | 结果 |
|---|---:|---:|---:|---|
| animation | 5.134ms | 7.891ms | 0/431，0% | 通过 |
| overlay | 9.159ms | 9.312ms | 0/37，0% | 通过 |
| page transition | 6.327ms | 6.783ms | 0/51，0% | 通过 |
| list scroll | 17.995ms | 19.884ms | 87/1005，8.657% | 失败 |
| text input | 14.101ms | 33.035ms | 4/49，8.163% | 失败 |

证据门禁、Macro/Micro profiler、Baseline Profile 和 thermal 检查通过；绝对帧门禁失败。由于没有
已批准 baseline，`baselineComparison.status=missing`、趋势门禁失败。当前候选不得批准，M6-2
保持进行中。

## 列表滚动：同机优化证据与剩余显示链限制

优化前同机原始数据：

`build/reports/performance/macro/redmi-k20-pro-api31-60hz-2026-07-14/com.purride.pixelbenchmark-benchmarkData.json`

优化后数据使用本页正式 Macro JSON。统计对比如下：

| 指标 | 优化前 | 优化后 | 变化 |
|---|---:|---:|---:|
| CPU frame p50 | 42.087ms | 9.097ms | -78.39% |
| CPU frame p95 | 46.575ms | 17.995ms | -61.36% |
| CPU frame p99 | 48.199ms | 19.884ms | -58.75% |
| frame overrun p95 | 49.801ms | 5.192ms | -89.57% |

优化前选定 trace 的 `Record View#draw()` actual CPU p95 为 11.594740ms；优化后十份列表 trace
的对应 p95 为 7.963–9.982ms。该结果在同一设备/API/60Hz、同一旅程和正式 10 次分布上证明
Canvas/文本热路径优化有效，满足 M6-3 的同机统计验收。

优化后最坏帧仍存在独立显示链反压：目标 RenderThread 在 `dequeueBuffer` 睡眠，唤醒链经过
SurfaceFlinger、`android.hardware.graphics.composer@2.4-service`、`DRMAtomicReq::Commit`，最终由
内核 `crtc_commit:132` 唤醒。十份 trace 中只有迭代 1/3/4 出现约 10.556–11.199ms 的 dequeue
p95 和 13.501–13.581ms 的 AtomicCommit，其余七份约为 0.059–0.067ms 与 0.315–0.366ms。
因此应用真实 draw CPU 收益与 Redmi vendor HWC 偶发整帧等待必须分开看；后者不能用于掩盖
M6-2 的失败，也不能反向否定已量化的 M6-3 收益。

完整 Perfetto evidence chain：

`build/reports/pixel-benchmark/physical-redmi-k20-pro-api31-60hz-20260715-macro/connected-output/benchmarkRelease/connected/Redmi K20 Pro - 12/PixelMacrobenchmark_listScroll_iter004_2026-07-15-04-21-52.perfetto-trace_analysis.md`

## 文本输入异常与拒绝的实验

正式文本输入最坏帧为 53.120ms；目标主线程只有约 6.97ms CPU，却在同步 Binder 中等待
41.492ms。system_server 证据显示它等待 `PackageManagerService` 的 `grantImplicitAccess()` 锁；
持锁的 `android.io` 线程同期有约 9.224ms D-state I/O。其余九份 trace 的目标主线程最大 Binder
仅为 1.979–7.030ms，因此该离群值是 MIUI/system PackageManager 在 IME 建连时的锁竞争，不是
Pixel Engine layout/render 热点。

完整 Perfetto evidence chain：

`build/reports/pixel-benchmark/physical-redmi-k20-pro-api31-60hz-20260715-macro/connected-output/benchmarkRelease/connected/Redmi K20 Pro - 12/PixelMacrobenchmark_textInput_iter004_2026-07-15-04-20-53.perfetto-trace_analysis.md`

曾探索把 focus/IME 建连移到计时区外，再只测文本替换。有效批次出现 3/10 个零帧迭代，并把 cursor
blink/全量 redraw 纳入 `waitForIdle`；CPU p95/p99 反而变为 32.643/49.057ms，最坏帧 61.266ms。
该方法改变了旅程语义且结果更不稳定，已明确拒绝，临时源码改动已删除并重新编译通过。归档仅用于
防止未来重复走这条错误路线：

`build/reports/pixel-benchmark/physical-redmi-k20-pro-api31-60hz-20260715-text-prepared-rejected/`

## 状态与后续

M6-3 的代码、正确性、预算、分配审计、partial repaint 决策和同机前后统计证据均完成。

M6-2 仍需：

1. 在代表性 API 24、29、36 与 60Hz/120Hz 设备组合上取得通过绝对门槛的正式分布；
2. 只对绝对门禁通过、设备/源码/APK/编译配置完整的候选执行人工批准；
3. 用批准 baseline 在同配置复跑中验证关键指标回退不超过 10%；
4. 保留失败 trace，继续区分引擎 CPU、系统服务和 vendor 显示链问题，不调整既定阈值。

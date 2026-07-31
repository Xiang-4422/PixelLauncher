# 性能

优化必须先有同一设备/AVD、同一 profile、同一场景的前后证据；任何一层证据都不能替代其他层。
golden 与性能分离：像素相同不代表帧时间合格，帧时间合格也不允许视觉回归。

## 当前工程内的性能保护

当前门禁只包含 JVM 层的确定性性能冒烟：

- `EnginePerformanceSmokeTest`（`src/test/kotlin/com/purride/pixelui/regression/`）
  测量固定场景批次、写入机器可读报告，并在任一场景超过阈值时失败；
  随 `:pixel-engine:testDebugUnitTest` 一起执行。它保护算法趋势，不代表真机帧时间。

运行时诊断能力随 SDK 发布、默认关闭，供手动分析使用：

- `PixelHostView.frameDiagnosticsObserver` / `latestFrameDiagnostics`：完整帧阶段
  （build/layout/paint/bufferSubmit/androidDraw）、工作量与丢帧归因。
- `PixelHostView.frameStatsObserver`：轻量帧间隔与滑动 FPS。
- Perfetto 可配合仓库根目录的 `trace_processor` 做 trace 分析。

帧阶段和归因 API 见 [API 手册](../使用说明与API手册.md)，资源内存见
[资源与内存](resources.md)。

## 已移出当前工程的部分

设备级 Microbenchmark / Macrobenchmark、Baseline Profile 验证、长时间 soak 及其
门禁脚本已从当前工程目标移除（见[架构与设计](../架构与设计.md)与[长期规划](../长期规划.md)），
nightly workflow 也不再运行性能 job。不要按旧文档寻找 `tools/pixel-ci-performance.sh`
等脚本；它们已不存在。

## 重建设备性能门禁的前置条件

恢复设备级性能趋势前必须先明确并记录以下口径，避免不可比的历史数据再次累积：

1. 代表性目标设备与刷新率（60/120Hz），模拟器结果只作演练、不作为发布证据。
2. 固定场景清单与驱动方式（冷启动、首帧、滚动、持续动画各自独立计量）。
3. 统计口径：p95 帧时间为主指标，批次数、丢弃规则与回归阈值一并冻结。
4. 首次失败样本与确认复跑分别保留，复跑不得覆盖失败样本。

在这些口径确定之前，设备性能数据只能作为一次性分析证据，不进入门禁。

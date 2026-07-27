# 性能

优化必须先有同一设备/AVD、同一 profile、同一场景的前后证据。JVM smoke 保护算法趋势，
Microbenchmark 隔离 layout/paint/submit，Macrobenchmark 观察冷启动、首帧和滚动，Perfetto 用于定位
主线程、RenderThread、调度和内存链路；任何一层都不能替代其他层。

本地无设备门禁：

```bash
bash tools/pixel-ci-performance.sh
```

发布前检查：

- 六个固定 JVM 场景 `overallPass=true`，七批中位数报告来自本轮 runId。
- Baseline Profile 确实打入 release/benchmark APK，而不只是源码存在。
- API 36 Macrobenchmark 与 Perfetto trace 由夜间 workflow 运行并上传原始证据。
- 30–60 分钟设备 soak 完成后 ticker、frame callback、listener、retained tree、focus、overlay、
  resource owner 回到零或稳定基线，且 PSS 首尾趋势有界。
- golden 与性能分离：像素相同不代表帧时间合格，帧时间合格也不允许视觉回归。

显式模拟器长跑入口（默认 30 分钟）：

```bash
PIXEL_BENCHMARK_SERIAL=emulator-5554 bash tools/pixel-device-soak.sh
```

短跑只能验证接线，`tools/check_device_soak.py --require-qualified` 会拒绝不足 30 分钟的报告。

## 模拟器性能演练与发布证据

模拟器可用于尽早验证完整 Macro/Microbenchmark、Perfetto、Method Trace、Baseline Profile 和机器
报告链路，但结果不代表实体设备性能。`tools/check_device_benchmarks.py` 默认使用 `physical` 模式，
会拒绝明显的 AVD 身份；模拟器必须显式传入：

```bash
python3 tools/check_device_benchmarks.py \
  --evidence-mode emulator-rehearsal \
  --report-only \
  ...
```

演练报告固定写入 `representativePerformanceEvidence=false`，趋势状态为 `nonRepresentative`，即使
手工把候选 baseline 改为 `approved` 也不能通过发布趋势门禁。Microbenchmark 的 AndroidX
`EMULATOR_` 方法名前缀只在该模式校验并规范化；实体模式不改写原始名称。

首次失败与后续确认复跑必须分别保留。确认复跑可判断模拟器瞬态调度或图形栈拥塞是否可复现，
但不能覆盖失败样本，也不能替代同一代表性 60/120Hz 实体设备上的批准 baseline、绝对阈值和
优化前后 trace。

帧阶段和归因 API 见 [API 手册](../使用说明与API手册.md)，资源内存见
[资源与内存](resources.md)。

# M6-2 API 37 模拟器 30 分钟设备长跑验收

## 结论

2026-07-15，显式绑定 `emulator-5554` 的 API 37 / Android 17 / 16KB / 60Hz AVD 完成
1,801.007 秒设备长跑。六类真实 SDK 旅程共完成 1,324 个 Host 周期，JUnit 为 1/1，
0 failure、0 error、0 skipped；每个周期都在目标进程主线程释放 Host，并验证 callback、listener、
ticker、Element、RenderObject、输入和手势目标的终态残留为零。

同一入口先运行确定性 JVM 压力测试，精确完成 10,000 次 `PixelUiRuntime + Navigator +
AsyncBuilder + Host frame scope + ticker` 生命周期。route create/enter/build/dispose、subscription/
unsubscription、ticker create/dispose 和终态资源计数全部配对。M6-2 的 30–60 分钟 soak、10,000 次
压力和有界 heap 验收已经成立。

这仍是模拟器长期稳定性证据，不是 60/120Hz 代表性性能 baseline。M6-2 继续等待代表性设备的
正式 10 次分布、批准 baseline 和 p95/p99/jank/回退阈值通过。

## 实现与隔离

- `PixelDeviceSoakInstrumentedTest` 只在 `pixel.soak.enabled=true` 且按类过滤时运行，普通
  Macrobenchmark 不会意外等待 30 分钟；
- `tools/pixel-device-soak.sh` 默认要求 1,800 秒，超过 3,600 秒会失败；短跑必须显式设置
  `PIXEL_SOAK_ALLOW_SHORT=1`，报告保持 `qualifiesForGoal=false`；
- `tools/pixel-connected-benchmark.sh` 同时固定 `ANDROID_SERIAL`、AGP 设备 serial 和目标硬件
  serial，默认拒绝实体设备；本次只操作 `emulator-5554`；
- benchmark target 的显式 Receiver 只存在于测试 APK，在目标应用主线程调用
  `PixelHostSetup.dispose()`，再读取基础类型终态快照；正式 SDK/消费者不会打包该 Receiver；
- `PixelHostView.frameScopeDiagnostics` 以只读公开属性暴露已有 frame scope 计数，不启动额外采样，
  不暴露 callback/listener/ticker 引用。聚合 SDK 三份和 `pixel-android` 两份 API/ABI baseline
  均只增加该 getter，零删除、零改签名，stable boundary 通过。

## 真实设备旅程

固定轮询顺序和完成次数如下：

| 旅程 | 完成次数 | 行为验收 |
| --- | ---: | --- |
| startup | 221 | 启动生产 Host 并等待 `STARTUP READY` |
| listScroll | 221 | 五次真实 touchscreen swipe，要求远端生产行出现 |
| textInput | 221 | 通过 Android accessibility 编辑生产 `TextField` |
| animation | 221 | 启动 Host ticker 驱动的 700ms retained 动画并观察 850ms |
| pageTransition | 220 | 执行真实 Navigator push 并等待详情页 |
| overlay | 220 | 展示真实模态 Overlay 并等待可访问标题 |

每轮旅程后，目标 Host 必须满足：

- lifecycle 为 `Destroyed` 且有效 destroy 次数恰好为 1；
- frame scope 为 disposed；pending callback、frame listener、active/live ticker 均为 0；
- 不再持有 source frame；Element root、RenderObject root 和输入/语义/手势 target 总数均为 0；
- pending build、focused text input、active Pager/List 均为 0。

1,324 次终态检查中，上述 12 类可计数残留的全程最大值全部为 0。

## 内存有界性

长跑采集 31 个目标进程 `dumpsys meminfo` 样本。不同旅程的常驻资源量不同，因此原始 PSS 会随固定
轮询在约 21–55 MiB 间变化；门禁使用首尾各三分之一的中位数，避免用单个低点或高点得出结论：

| 指标 | 结果 |
| --- | ---: |
| 起始三分位 PSS 中位数 | 48,585 KiB |
| 末尾三分位 PSS 中位数 | 24,557 KiB |
| 末尾相对起始变化 | -24,028 KiB |
| 允许增长 | 9,717 KiB（8 MiB 与起始 20% 取较大值） |
| 有界性 | 通过 |

`tools/check_device_soak.py` 会独立复算时长、设备身份、60.000004Hz 刷新率、六旅程精确集合、
逐周期终态检查数、内存样本单调性、PSS 增长与预算、`qualifiesForGoal`。只修改 `bounded` 或
`qualifiesForGoal` 布尔值不能绕过复算；工具负例覆盖短跑、非零 listener、伪造 heap 和缺失旅程。

## 10,000 次确定性资源压力

`EngineResourceLifecycleStressTest` 的本轮 XML 为 1/1、0 skipped、0 failure、0 error，并保留：

```text
PIXEL_RESOURCE_STRESS cycles=10000 elapsedMs=363
```

每次循环都创建真实 Navigator route、AsyncBuilder subscription、Host-owned ticker 和 retained/
render tree，派发一个真实手动帧后依次释放 runtime 与 frame scope。10,000 次结束后 retained Element、
Listenable dependency、dirty Element、attached RenderObject、active/live ticker、pending frame 和活动
subscription 均为 0。

## CI 与命令

夜间 workflow 的 soak job 已升级为 API 36 模拟器 30 分钟长跑；它先执行 10,000 次确定性压力，
再运行设备 instrumentation，并上传 JUnit、additional output 和主机验收摘要。普通夜间
Macrobenchmark 显式过滤为 `PixelMacrobenchmark`，不会混入长跑类。

本轮正式命令：

```bash
PIXEL_BENCHMARK_SERIAL=emulator-5554 \
PIXEL_SOAK_DURATION_SECONDS=1800 \
PIXEL_SOAK_SAMPLE_INTERVAL_SECONDS=60 \
bash tools/pixel-device-soak.sh
```

定向预检还包括 17 个 checker/wrapper/workflow 合同测试、聚合 SDK 三份 API/ABI、
`pixel-android` 两份 API/ABI、stable boundary 和 benchmark Release R8，全部通过。

## 原始证据与摘要

| 文件 | SHA-256 |
| --- | --- |
| `build/reports/performance/device-soak/pixel-device-soak-report.json` | `e6ad615887948bdfb8e716030aecfe30f1f56b190426c3aec021247be1653a22` |
| `build/reports/performance/device-soak/pixel-device-soak-check.json` | `d39d823f91867c2f5f28aa7e5707ac42da2c18b998ab6b18d5d73ca1ccff3738` |
| `pixel-benchmark/build/outputs/androidTest-results/connected/benchmarkRelease/TEST-Pixel_4(AVD) - 17-_pixel-benchmark-.xml` | `c4f0742b0b92c6b8a3b5bc02d622438282e4e668ee98b593c28cd54ab39727ba` |
| `pixel-engine/build/test-results/testDebugUnitTest/TEST-com.purride.pixelui.regression.EngineResourceLifecycleStressTest.xml` | `f69218576239f5d0afef5678b7f8f06fbd8e0cae51b048b5f2691f767903da80` |

机器摘要为 `status=pass`、`qualifiesForGoal=true`、`actualDurationMillis=1801007`、
`completedJourneyCycles=1324`、`memorySampleCount=31`、`pssGrowthKb=-24028`，12 类
`maximumTerminalResidue` 全部为 0。

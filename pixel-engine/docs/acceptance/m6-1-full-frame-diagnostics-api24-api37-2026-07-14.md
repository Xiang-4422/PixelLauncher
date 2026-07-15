# M6-1 完整帧诊断与阶段归因验收

## 结论

2026-07-14，M6-1 已完成。SDK 现在可在一次 Android 帧边界内分别观测 build、layout、paint、
buffer submit、Android draw 和总耗时，并同时报告 dirty Element/RenderObject、逻辑绘制/提交像素、
进程级 ART allocation/GC、buffer pool、render cache、missed vsync 和主要丢帧原因。

完整诊断默认关闭。关闭时 Host 不启动时钟、ART runtime stat 采样、dirty subtree 遍历或诊断快照
分配；现有 `PixelHostFrameStats` 构造器和 observer ABI 保持不变。开启时 observer 在 Host UI 线程、
真实 `onDraw` 帧边界末尾收到不可变快照，Inspector 和 debug overlay 可直接读取相同数据。

人为 build/layout/paint/buffer submit/Android draw 瓶颈归因、scheduler 延迟、GC 归因、60/120Hz
预算、关闭状态零采样，以及真实 API 24/37 Canvas 提交路径均已通过。最终完整 JVM、三份 API/ABI、
独立 Kotlin/Java consumer、Lint、Release、性能、soak 与严格文档门禁全部通过。

## 任务与验收映射

| Goal 要求 | 实现与证据 |
|---|---|
| 六段完整帧时间 | `PixelFrameDiagnosticsRecorder` 从 Host 帧开始计时；runtime phase sink 分别覆盖 build、layout、paint，Host 覆盖真实 `drawBuffer` submit、Android draw 与总帧边界。 |
| 修正 paint/submit 边界 | legacy `PixelHostFrameStats.paintTimeNanos` 现在覆盖完整 `onDraw`；新 API 单独暴露真实 Canvas buffer submit，不再把 buffer 构建误当作最终提交。 |
| 工作量、分配、缓存和丢帧 | `PixelFrameWorkload` 与 `PixelFrameDropReason` 提供 dirty node、像素、ART allocation/GC、pool hit/miss、render cache hit、missed vsync 和主要瓶颈。 |
| Inspector 可见、关闭低成本 | `PixelDebugOverlay`、`PixelInspectorPanel` 和 `PixelInspectorSnapshot.frameDiagnostics` 展示指标；10,000 次关闭路径测试确认 runtime sampler 调用为零且不产生 snapshot。 |
| 稳定数据结构与语义说明 | 新公开类型是 SDK 自有的不可变数据结构；API 手册、架构文档和 migration 说明 UI 线程、exclusive phase、采样成本、ART 指标可空与 refresh fallback。 |
| 人为瓶颈正确归因 | recorder 确定性时钟测试分别制造 build、layout、paint、submit、Android draw、scheduler 和 GC 瓶颈，逐项断言原因。 |
| 关闭 diagnostics 无显著额外分配 | disabled recorder 10,000 次 phase 调用不采样、不生成快照；runtime/Host 关闭路径不请求 buffer stats、不遍历 render subtree。 |

## 时间边界与数据语义

`PixelFrameTimings` 中的五个阶段耗时是互斥区间：

- build：dirty Element rebuild；
- layout：RenderObject layout；
- paint：逻辑像素 buffer 的生成或复用准备，不包含最终 Android Canvas 提交；
- buffer submit：`drawBuffer` 把像素 buffer 真正提交到 Android Canvas；
- Android draw：Host background、`super.onDraw`、accessibility/debug 等非引擎阶段；
- unattributed：总帧区间中无法归入上述阶段的剩余时间。

帧预算由当前 refresh rate 推导，未知或非法值回退到 60Hz。`missedVsyncCount` 由实际帧间隔和预算
计算；总帧超预算时优先归因到耗时最大的明确阶段，未被阶段解释的延迟归为 scheduler，伴随 GC
增长的 unattributed 延迟归为 garbage collection。

allocation 和 GC 来自 Android ART 进程级 runtime stat，因此不是单个 Host 的精确分配量，在不支持
该 stat 的运行时允许为 `null`。observer 和 `latestFrameDiagnostics` 只应在 Host UI 线程消费；公开
快照自身不可变，可安全转交给只读分析代码。

## JVM 与真实设备验证

新增或扩展的关键验证包括：

- `PixelFrameDiagnosticsRecorderTest`：完整阶段、workload、关闭零采样、五类阶段瓶颈、scheduler、
  GC、60/120Hz/fallback，共 6 项；
- `PixelHostFrameDiagnosticsTest`：公开数据不变量和 Inspector 既有 data-class ABI/value semantics，
  共 4 项；
- `PixelUiRuntimeFrameDiagnosticsTest`、`PipelineOwnerTest`：真实 build/layout/paint、dirty/pixel、
  buffer pool 与 render cache 路径；
- `PixelTesterDslTest`：debug overlay 和 Inspector panel 的阶段、工作量、allocation、cache、drop 展示；
- `PixelHostFrameDiagnosticsInstrumentedTest`：真实 attached Host/Canvas、开关、UI-thread observer、
  submit/Android draw、ART stats、cache-hit 第二帧、Inspector 暴露与 legacy stats 边界。

| 设备 | fingerprint | 结果 |
|---|---|---:|
| API 24 emulator | `google/sdk_google_phone_arm64/generic_arm64:7.0/NYC/8695085:userdebug/dev-keys` | 1/1 |
| API 37 emulator | `google/sdk_gphone16k_arm64/emu64a16k:17/CP21.260330.005/15181570:user/dev-keys` | 1/1 |

两项均为零失败、零错误、零跳过。原始 JUnit XML 保存于：

- `build/reports/device/m6-1-api24-frame-diagnostics.xml`；
- `build/reports/device/m6-1-api37-frame-diagnostics.xml`。

最终完整 JVM 结果为 engine 1,273/1,273、Demo 27/27、Launcher app 328/328、tooling/consumer
40/40，均为零失败、零错误、零跳过。隔离 Maven AAR consumer 同时从 Kotlin 和 Java 构造并读取
全部新增数据类型。

## API、发布与性能门禁

新 surface 只增加 `PixelFrameTimings`、`PixelFrameWorkload`、`PixelFrameDropReason`、
`PixelHostFrameDiagnostics` 以及 Host/Inspector 的 additive accessor/observer。旧
`PixelHostFrameStats` 和 `PixelInspectorSnapshot` 主构造器、`copy`、`componentN` 描述符保持不变。

| baseline | SHA-256 |
|---|---|
| public API | `6190888de8fb1836dac6a8fa9cf24c5ed3d1a6793f53531cb8ae110f89e0d91a` |
| JVM binary API | `35e8a7dbcb17232a7b678a3aa9a7514aaba761310ac3e7917d3f9d5ba9678357` |
| Metalava API | `b26b9d168bc63e652de2cd4552ceda9e3306cb95d225d83ebf486cb28bb35404` |

最终 `tools/pixel-release-check.sh` 通过，覆盖 secret/backup、API/ABI/Metalava/stable boundary、
Unicode、KDoc、完整 JVM、Lint、Release AAR/POM/sources/Javadoc、Render SPI、RouteEntry、旧二进制、
隔离 consumer、perf、soak 和严格 MkDocs。六场景 JVM perf smoke：列表滚动 2.735ms、文本输入
0.395ms、动画 0.037ms、图形 0.126ms、页面转场 0.320ms、Overlay 0.248ms，
`overallPass=true`。

Release AAR 为 3,232,281 bytes，SHA-256：
`cb6b9c970f36beb7c7e0e4bd2e8dd027561320b96408866709a44f8261589523`。
KDoc 为 1,249/2,209（56.54%，当前阶段门槛 35%）；本工作包新增或修改的公开声明和关键内部声明
已具备职责、线程、成本或不变量说明。全仓 100% 仍由 M9-1 收口。

## 后续边界

M6-1 不把 JVM smoke 冒充真实设备性能基准。M6-2 负责 Macrobenchmark、Microbenchmark、
Baseline Profile、API 24/29/36 与 60/120Hz 基线、趋势门禁、30–60 分钟 soak 和 10,000 次
生命周期压力；M6-3 再根据 trace 证据优化提交与缓存；M6-4 收口资源加载、校验和内存边界。

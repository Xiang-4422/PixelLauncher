# M6-3 方形 gap Bitmap 批处理阶段验收（2026-07-14）

## 结论

M6-3 已完成。本阶段完成 Canvas 提交优化、复杂文本/快速列表 CPU 与 allocation
热点优化、六类正式 Microbenchmark、Release 产物预算、完整热路径分配审计以及 partial repaint
决策。API 37/16KB 同一 AVD 配置的优化前后证据显示，复杂文本 median 时间/allocation count
分别下降 84.86%/88.90%，快速列表分别下降 30.85%/49.31%。后续 Redmi K20 Pro/API 31/60Hz
无并发自动化复测已补齐正式 Macro 7/7、Micro 11/11 和同机优化前后 trace/统计证据，满足本工作包
最后一项验收。实体机绝对帧门禁仍因列表滚动、文本输入和缺少批准 baseline 而失败；那属于 M6-2，
本页数据不得被误写成已批准的发布 baseline。完整补充证据见
`m6-2-m6-3-redmi-api31-60hz-physical-evidence-2026-07-15.md`。

## 优化前根因

优化前 Redmi K20 Pro/API 31/60Hz 的列表 trace 位于：

`build/reports/performance/macro/redmi-k20-pro-api31-60hz-2026-07-14/PixelMacrobenchmark_listScroll_iter000_2026-07-14-07-50-39.perfetto-trace`

完整 Perfetto 证据链位于同目录的：

`PixelMacrobenchmark_listScroll_iter000_2026-07-14-07-50-39.perfetto-trace_analysis.md`

已验证的关键事实：

- 目标进程 119 帧中有 113 次 Buffer Stuffing，而 App Deadline Missed 只有 6 次。
- SurfaceFlinger `HwcPresentOrValidateDisplay` 墙钟 p50/p95 为 16.664949/18.341304ms，
  几乎全部处于 Sleeping。
- vendor composer 的 `DisplayBuiltIn::Commit::RegisterVsync` Sleeping p50/p95 为
  13.451251/15.562450ms；内核 `crtc_commit:132` 反复产生约 11.5–11.9ms D-state。
- 目标 RenderThread `dequeueBuffer` 墙钟 p50/p95 为 14.115366/17.039532ms，证明
  SurfaceFlinger/HWC 消费变慢会反压应用 buffer。
- 独立于显示链等待，应用 `Record View#draw()` 实际 Running p50/p95/p99 为
  7.980470/11.594740/13.129481ms；源码同时显示 gap 路径会对每个点亮逻辑像素分别调用
  `Canvas.drawRect/drawCircle/drawPath`，因此方形逐像素 Canvas 录制是可优化的次级热点。

该 Redmi 采集窗口还包含 GMS 上传、小米应用商店并发 GC、较高 system_server/sensor HAL/adbd
负载，且 SurfaceFlinger refresh p50 为 21.911044ms，不是 60Hz 的 16.67ms 稳态。因此它只作为
根因与优化前证据，不是可批准 baseline，Goal 阈值保持不变。

## 实现边界

`PixelHostView` 的方形 gap 路径现在执行：

1. 把最终逻辑 ARGB `PixelBuffer` 写入尺寸匹配的复用 Bitmap。
2. 用一个 `Canvas.drawBitmap` 把完整逻辑位图提交到整数 viewport。
3. 继续使用既有缓存的熄灭点阵背景承接透明像素。
4. 继续使用既有 bezel overlay 覆盖内部横纵 gap，并保留 viewport 外边缘。

复用 Bitmap 只在逻辑分辨率变化时重建，普通帧不创建逐像素集合或装箱对象。圆形、菱形和
fractional viewport 保留原逐格路径，避免 Bitmap 缩放改变形状或子像素栅格化。没有引入
partial repaint，也没有改变公开 API、命中测试几何或 semantics 坐标来源。

## 正确性证据

新增 `PixelHostSquareGapBatchInstrumentedTest`，使用独立的优化前逐格算法构造完整物理 bitmap
对照，逐像素比较以下组合：

- 透明与熄灭点阵背景；
- 半透明 Src 像素；
- 相邻异色和跨透明区域重复色；
- 内部横纵 gap、非零 viewport 外边距和四周边缘像素。

验证结果：

- `:pixel-engine:compileDebugKotlin` 与 `compileDebugAndroidTestKotlin` 通过。
- engine JVM 1276/1276，0 failure、0 error、0 skipped。
- API 37/Android 17/16KB 完整 instrumentation 60/60，0 failure、0 error、0 skipped。
- API 24/Android 7.0 的新增完整 bitmap 对照 1/1 通过。
- API 37 优化后 `PixelMacrobenchmark#listScroll` 行为断言、采集和正式 10 次执行通过。

这些回归覆盖既有 pixel golden、retained/render 行为、输入命中和 semantics 测试；新增 Android
对照进一步证明最终 Canvas 输出没有像素差异。

## 六类最坏场景 Microbenchmark

新增 `PixelRenderWorstCaseMicrobenchmark`，使用 AndroidX `BenchmarkRule` 的正式预热、动态重复、
allocation、Perfetto 和 Method Trace 路径覆盖：

- `fullBrightnessNoGapCanvasSubmit`：270×600 全亮逻辑 buffer 提交到 1080×2400 软件画布；
- `squareGapGridCanvasSubmit`：同一全亮负载叠加方形 gap 和 bezel overlay；
- `nestedOpacityPaint`：三层 opacity 与 300 个高密度彩色节点；
- `clippedOverflowPaint`：超出 viewport 的 588 个节点经负向平移和 `ClipRect` 合成；
- `complexTextLayoutAndPaint`：32 个 span，覆盖 grapheme、emoji cluster、Bidi、多脚本、CRLF、
  fallback 和换行；
- `fastLazyListScrollPaint`：5,000 行定高懒列表从首屏跳到远端窗口并重建、布局和绘制。

API 37/Android 17/16KB AVD 先以未抑制环境错误的 dry-run 验证 6/6、0 failure、0 error、
0 skipped；AndroidX 源码明确在 dry-run 下跳过 `IsolationActivity` 启动成本。随后仅抑制
`EMULATOR` 环境错误执行完整计时，仍为 6/6，且正式日志不存在 `ACTIVITY-MISSING`，证明
`AndroidBenchmarkRunner` 隔离页面真实生效。该运行生成 6 份 Perfetto trace、6 份 Method Trace、
6 份消息、原始 Benchmark JSON、JUnit XML、设备元数据、摘要和覆盖 22 个文件的 SHA-256 清单。

稳定留存目录：

`build/reports/performance/m6-3/api37-worst-case-microbenchmark-2026-07-14/`

探索性正式计时摘要如下；模拟器数据只证明基准路径和指标完整性：

| 场景 | time median | allocation median |
|---|---:|---:|
| full brightness/no gap Canvas | 1.350ms | 1.027 |
| square gap Canvas | 2.613ms | 2.053 |
| nested opacity | 0.679ms | 13,712.286 |
| clipped overflow | 0.668ms | 25,398.255 |
| complex text | 8.264ms | 309,657.250 |
| fast lazy-list scroll | 2.589ms | 85,693.316 |

两个 Canvas fixture 在计时区间复用 `Bitmap` 和 `Canvas`，1–2 次分配证明本次 Host 提交改动没有
恢复逐像素集合分配。其余四项刻意把 `PixelTester`、Widget/runtime 构建和完整逻辑帧纳入计时，
因此这里的高分配不能直接归因于某一个生产热路径，也不能用来勾选“所有热路径复用”任务；它们
是后续稳态/冷态拆分审计的输入。

现有实体设备性能门禁已从五项升级为 11 项精确 Microbenchmark 集合，并逐方法校验真实测试类，
防止同名空实现替代上述六类行为。2026-07-15 的 Redmi 实体设备最终运行已完成精确 11/11，
并保留 11 份 Perfetto 和 11 份 Method Trace；本段 AVD 探索数据仍不进入批准 baseline。

## 稳态 trace 根因与文本热路径优化

稳态优化前证据目录为：

`build/reports/performance/m6-3/api37-worst-case-steady-state-2026-07-14/`

复杂文本 Perfetto 证据链确认：50 个正式 measurement 的总墙钟为 `4,607.459337ms`，CPU
为 `3,995.307309ms`（86.71%）；Method Trace 中 `buildClusters` inclusive `231.546ms`，
`remeasureClusterSequence` `137.892ms`，`measureStyledCluster` 2,304 次，
`PixelBitmapFont.measureText`/`Strings.lines` 各 3,179 次。目标进程 D-state 只有数毫秒，且 trace
不含 heap allocation callstack，因此结论是 CPU 与对象构建热点，不是 I/O 等待。

快速列表 Perfetto 证据链确认：50 个 measurement 总墙钟 `4,922.890754ms`，CPU
`4,230.648977ms`（85.94%）；被 profile 的列表帧包含 33 次 `RenderText.layout`、66 次旧
`remeasureClusterSequence`、2,224 次 `PixelBitmapFont.measureText` 与 2,672 次
`Strings.lines`。item builder lambda 虽调用 144 次，但 inclusive/self 仅 `4.922/0.376ms`，
所以没有把必要的 retained item 输出误判成主要热点。

对应生产实现完成以下修改：

- cluster 首次独立测量后保留 `standaloneWidth`，软换行只增量计算新相邻 pair，把候选测量从
  反复重算整行改为线性扫描；视觉重排只从独立宽度重放相邻修正；
- span 样式查找改用单调 span 游标，不再为每个 UTF-16 单元创建样式引用；Bidi level 直接写入
  精确 `IntArray`，视觉重排直接扫描最低奇数 level，不再创建 filter 中间集合；
- 无 CR/LF 文本直接调用字体引擎，不再经过 `String.lines()`；相邻簇 pair 测量扫描两个字符串片段，
  保留跨片段 glyph spacing 且不创建拼接字符串；
- 行高改为直接扫描 cluster；可见前缀与视觉重测使用一次目标集合分配，不再 `take/map` 后二次复制；
- 段落缩放 glyph 与 Host 文本缩放的原生 scratch buffer 均改为 `PixelBufferPool` 借还，并用
  `finally` 保证异常路径归还。

最终 post-optimization 证据目录为：

`build/reports/performance/m6-3/api37-worst-case-post-text-hotpath-2026-07-14/`

它与优化前稳态证据使用相同 `Pixel_4` AVD、API 37 镜像、16KB 页大小、`gpu.mode=auto`、正式
AndroidX runner 和相同六项 workload；唯一抑制项仍是 `EMULATOR` 环境标记。JUnit 6/6、零失败、
零错误、零跳过，并保留 6 份 Perfetto、6 份 Method Trace、6 份消息、原始 Benchmark JSON、
设备元数据、摘要和覆盖 22 个输入文件的 SHA-256 清单。

| 场景 | 优化前 median time | 优化后 median time | 时间变化 | 优化前 allocation count | 优化后 allocation count | 分配变化 |
|---|---:|---:|---:|---:|---:|---:|
| complex text | 8.906ms | 1.349ms | -84.86% | 316,119.600 | 35,090.254 | -88.90% |
| fast lazy list | 1.403ms | 0.970ms | -30.85% | 45,652.322 | 23,139.338 | -49.31% |

其余四项没有改动 workload：clip `0.329ms/2,863.101`、nested opacity
`0.457ms/1,747.075`、no-gap Canvas `1.402ms/1.028`、square-gap Canvas
`2.671ms/2.057`。这里的 allocation 指 Android `Debug.getGlobalAllocCount()` 对象计数，不是
字节数。结果证明本次文本优化没有用 Canvas/opacity/clip 回退换取局部收益。

## 热路径 buffer/临时对象审计

源码与行为测试逐项闭环如下：

- `PixelHostView` 复用逻辑 Bitmap、熄灭点阵背景和 bezel overlay；两个 Canvas 基准夹具也在计时区
  复用 Bitmap/Canvas，allocation count 稳定在约 1/2 个对象每 iteration；
- opacity、clip、constraint scratch、scroll/grid/custom viewport、pager、overlay、safe overlay、
  段落裁剪和缩放 glyph 的临时像素缓冲全部通过帧级或适配器级 `PixelBufferPool` 借还；Pager
  向兼容的 `AxisBufferComposer` 显式传入 pooled `out`；
- 对渲染 internal 与字体目录执行 `rg 'PixelBuffer\s*\('` 后，没有剩余的直接临时 buffer 构造；
  搜索结果只剩 pool 自身创建、公开 `PixelBuffer.copy()` 的所有权复制，以及兼容旧调用方的
  `AxisBufferComposer(out = null)` 分支，生产 Pager 不走该分支；
- 新增相邻测量等价测试覆盖窄字形、空格、fallback 与 supplementary scalar；新增缩放段落测试连续
  绘制两帧并断言 frame buffer pool 为 1 miss/3 hits，证明 scratch 实际复用而不是只更换 API；
- engine JVM 1276/1276、tooling 68/68、Release Kotlin/Java/AAR 与制品预算全部通过。

高 allocation workload 中剩余对象是交替 Widget 输入、retained Element/RenderObject、不可变段落
输出和远端列表窗口行本身；这些对象决定下一帧状态，不能作为临时集合池化。trace 与优化前后对象
计数已经证明已删除的是非必要测量/拼接/装箱，而不是通过跳过真实重建降低指标。因此“所有热路径
复用 buffer/临时集合，避免每帧非必要对象和装箱”任务已满足。

## tile/damage 与 partial repaint 决策

本阶段不引入 partial repaint，原因不是暂缓实现，而是 trace 与 workload 不支持其收益：

- 全亮/no-gap、square-gap、三层 opacity、超区 clip 和复杂文本变体都让完整逻辑帧受损；快速列表
  在两个远端窗口间跳转，新的可见/缓存窗口同样需要完整 build/layout/paint；
- Android Host 的已证实次级热点是大量逐像素 Canvas 命令，Bitmap 单次提交已经直接消除该成本；
- 复杂文本和列表的已证实热点是 CPU 布局、重复测量与临时对象，线性布局和无分配字体快路径已经
  分别带来 84.86%/30.85% 时间下降；damage region 不会减少这些工作；
- 在上述六类高 damage 场景中加入 tile bookkeeping 会新增脏区传播、tile 生命周期和合成复杂度，
  却没有可测量的可跳过区域。

因此条件任务“只有真实瓶颈证明收益后才引入 partial repaint”已经完成评估并决定不引入；既然没有
新增 partial repaint，就不触发其专属全帧对照测试条件。现有方形 Bitmap 独立旧算法逐像素对照、
pixel golden、hit-test 与 semantics 回归继续覆盖完整帧正确性。若未来新增低 damage、局部动画的
代表性 trace 并证明收益，再以新的工作包引入 damage region 和专属全帧 differential test。

## 探索性优化后数据

稳定留存目录：

`build/reports/performance/m6-3/api37-square-gap-bitmap-post-2026-07-14/`

目录包含 10 份 Perfetto trace、JUnit XML、Benchmark 消息、设备元数据和 SHA-256 清单。元数据
明确记录 `isEmulator=true`、`representativePerformanceEvidence=false`，且仅为获得探索性 10 次
分布抑制了 AndroidX Benchmark 的 `EMULATOR` 环境错误；该抑制没有进入任何发布门禁。

10 次列表滚动结果：

- frameCount：min 103、median 104、max 106；
- frameDurationCpuMs：p50 4.2ms、p90 6.4ms、p95 7.4ms、p99 9.5ms；
- frameOverrunMs：p50 -9.8ms、p90 -5.6ms、p95 -3.6ms、p99 -0.7ms。

同一 API 37/16KB AVD 的优化前 dry-run trace 与优化后 10 次中的 median iteration 005 对比：

| `Record View#draw()` | 优化前 | 优化后 | 变化 |
|---|---:|---:|---:|
| 均值 | 2.476882ms | 1.727619ms | -30.25% |
| p50 | 2.283375ms | 1.484208ms | -35.00% |
| p95 | 4.681125ms | 3.353875ms | -28.35% |
| p99 | 5.415416ms | 4.846958ms | -10.50% |

优化前来源：

`build/reports/performance/matrix/api37-16kb-final-2026-07-14/additional-output/PixelMacrobenchmark_listScroll_iter000_2026-07-14-10-40-15.perfetto-trace`

优化后来源：

`build/reports/performance/m6-3/api37-square-gap-bitmap-post-2026-07-14/additional-output/PixelMacrobenchmark_listScroll_iter005_2026-07-14-11-08-51.perfetto-trace`

该对比证明优化方向在同一 AVD 上降低了 Canvas 录制成本，但优化前只有一次 dry-run，不能代替
同一实体设备、同一构建和同一洁净状态的正式前后分布。

## Release 产物预算门禁

新增受审预算 `pixel-engine/config/release-artifact-budget.json` 和
`tools/check_pixel_artifact_budget.py`。检查器直接解析最终 AAR 的 `classes.jar` classfile，统计全部
`method_info`（包含构造器、private、synthetic），同时校验发布 POM 直接运行依赖与 Gradle 实际
解析后的完整运行时 artifact 精确集合。`checkReleaseArtifactBudget` 已接入 Gradle `check` 和
`tools/pixel-release-check.sh`，超限或任意依赖漂移都会失败。

当前机器报告为 `pixel-engine/build/reports/artifact-budget/release-artifact-budget.json`：

| 指标 | 当前值 | 固定预算 |
|---|---:|---:|
| Release AAR | 3,273,659 bytes | ≤ 3,500,000 bytes |
| `classes.jar` class | 1,470 | ≤ 1,600 |
| `classes.jar` method | 14,839 | ≤ 16,000 |
| 发布直接运行依赖 | 2 | ≤ 2，且精确白名单 |
| 解析后运行时 artifacts | 17 | ≤ 17，且精确白名单 |

当前 AAR SHA-256 为
`040987af352ec83b75b90d3f07c785df95e3bc9a9e225bcc73fd45433f30e2ea`。检查器正向、方法超限、
依赖集合漂移和损坏 classfile 四类单测通过；完整 Python tooling 68/68、预算 Gradle 门禁和
Microbenchmark AndroidTest 编译均通过。

## 实体设备最终闭环

2026-07-15 在同一 Redmi K20 Pro、API 31、60.000004Hz 上停止并发 adb/UI 自动化后完成复测：

- Macro 七场景各 10 次，7/7、70 份 Perfetto、零 thermal sleep；
- Micro 精确 11 项，11/11、11 份 Perfetto、11 份 Method Trace、零 thermal sleep；
- 同机列表 CPU 帧 p50/p95/p99 相对优化前分别下降 78.39%/61.36%/58.75%，overrun p95
  下降 89.57%；
- 十份优化后列表 trace 的 `Record View#draw()` p95 为 7.963–9.982ms，优化前选定 trace 为
  11.594740ms；
- 最慢列表帧的额外延迟由 RenderThread `dequeueBuffer` 追溯到 SurfaceFlinger、vendor composer
  与内核 `crtc_commit`，没有把显示链等待伪装成应用 Canvas 回归。

此前两次受并发占用影响的尝试仍保持作废，不进入统计。补充验收页记录完整原始路径、哈希、门禁
失败边界和 Perfetto evidence chain。M6-3 至此闭环；API 24/29/36、120Hz、绝对帧阈值、批准
baseline 与相对回退仍由 M6-2 继续负责。

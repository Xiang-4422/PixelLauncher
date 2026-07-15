# M6-2 列表布局与语义热路径优化验收（2026-07-15）

## 结论

Redmi K20 Pro、Android 12/API 31、60Hz 的 `listScroll` 已在不放宽 Goal 阈值的前提下通过正式
十轮绝对门禁：1,025 个采样帧的 CPU p50/p95/p99 为 7.946/11.107/12.512ms，正 overrun 为
0，jank 为 0%。p95 比 11.6667ms 门槛低 0.560ms（4.8%），p99 比 16.6667ms deadline 低
4.155ms（24.9%）。十轮均产生有效帧，`thermalThrottleSleepSeconds=0`。

这只关闭该设备/频率上的列表滚动绝对阈值缺口，不代表 M6-2 完成。文本输入、API 24/29/36
代表设备、120Hz、人工批准 baseline 和同配置回退不超过 10% 仍按原 Goal 保持未完成。

## 实现与因果链

本轮按 Perfetto 阶段归因依次落地以下优化：

1. lazy list 布局复用高度/offset 数组，并缓存渲染子节点、item stride 与单帧 scratch
   `PaintContext`，避免大型列表每帧重复集合和上下文分配；
2. 语义树、焦点与动作快照继续同步提交，把 Android accessibility event 转换移到 draw 后的
   主线程任务并按 Host 合并；测试 observer 保持同步，inactive/dispose 会取消任务并清空状态；
3. 单语义根直接附加 collection item info，避免没有歧义的列表项再次遍历子树；
4. `softWrap=false + CLIP` 单行段落只测量一次相邻 cluster pair；ELLIPSIS 因截断前需要宽度，
   继续保留预测量；
5. `PixelBuffer.blitRegion` 的 SrcOver 稀疏路径跳过 alpha 为 0 的源像素。该分支是严格的合成
   no-op，不改变目标颜色和 `nonOpaquePixelCount`。

所有新增或修改的类、变量和方法均按 Goal 约束补充了必要中文注释；公开 API 没有变化。

## 正式分布演进

所有批次使用同一 Redmi K20 Pro、API 31、60Hz、同一 `listScroll` 旅程和 10 次正式
Macrobenchmark。阈值始终为 CPU p95 ≤ 11.6667ms、p99 ≤ 16.6667ms、jank < 1%。

| 批次 | CPU p50 | CPU p95 | CPU p99 | 正 overrun / 帧 | 结果 |
|---|---:|---:|---:|---:|---|
| 原正式候选 | 9.097ms | 17.995ms | 19.884ms | 87/1,005（8.657%） | 失败 |
| layout array 复用 | — | 13.874ms | 15.506ms | 3/1,019（0.294%） | p95 失败 |
| 异步语义事件 | 8.378ms | 12.029ms | 13.141ms | 4/1,048（0.382%） | p95 失败 |
| context/child 缓存 | 8.464ms | 11.685ms | 13.795ms | 1/1,036（0.097%） | p95 失败 0.018ms |
| 语义根快路径 | 8.323ms | 11.855ms | 13.944ms | 2/1,037（0.193%） | p95 失败 |
| 文本单测量 + 稀疏 blit | 7.946ms | 11.107ms | 12.512ms | 0/1,025（0%） | 通过 |

最终结果相对原正式候选的 CPU p50/p95/p99 分别下降 12.65%/38.28%/37.07%，jank 从 8.657%
降为 0%。相对第一轮 layout array 复用结果，p95/p99 继续下降 19.94%/19.31%。

最终十轮每轮 CPU p95 分别为 11.941、10.581、10.881、11.028、11.316、11.090、11.182、
10.174、10.567、10.452ms。聚合门禁使用全部 1,025 帧的正式分布，不以最优单轮代替。

## Trace 与正确性证据

- 手工 Host trace v12 证明 107/107 个 `pixel.semantics_events` 位于 `pixel.frame` 外；异步事件后
  `pixel.frame` p95/p99 从 10.013/11.627ms 降到 8.108/9.082ms。
- v13 的 `pixel.frame` p95 为 7.557ms；build/paint p95 相对 v12 分别下降 14.8%/4.0%。
- v14 的 `pixel.layout` p95 从 3.039ms 降到 2.920ms；v15 的 `pixel.paint` 均值/p50 相对
  v14 下降 13.1%/22.3%。单批手工 trace 的尾部有噪声，因此只用于阶段归因，最终结论来自
  十轮 Macrobenchmark。
- 同一正式批次的低尾 iter000 与高尾 iter008 对比显示，高尾 `Choreographer#doFrame`、
  traversal、draw 和 `Record View#draw()` 均增加；main thread Running p95 从 7.309ms 增到
  8.683ms，Runnable p95 反而从 1.843ms 降到 1.090ms。尾部是应用主线程实际 CPU 工作，
  不是 scheduler wait、GC 或 RenderThread 大幅漂移。
- 受影响 JVM 测试覆盖 `PixelBufferAlphaTest`、`PixelBufferTest`、段落 Unicode 布局、
  retained renderer、M8-1 deterministic golden 与 M5-2 snapshot，Gradle 任务通过。
- API 36 模拟器重新执行 `PixelHostAccessibilityInstrumentedTest`、
  `StandardComponentsAccessibilityInstrumentedTest`、`PixelGraphemeInputConnectionInstrumentedTest`，
  共 19/19，零失败、零错误、零跳过；XML SHA-256 为
  `e38125a4ae5604f2b5364fc4bdb0dcda864634dd1f7d30f912fbbce93e9b1f2a`。

手工阶段证据链位于：

`build/reports/performance/m6-2/redmi-k20-pro-api31-60hz-phase-attribution-2026-07-15/`

最终正式归档位于：

`build/reports/performance/m6-2/redmi-k20-pro-api31-60hz-list-sparse-blit-formal-2026-07-15/`

归档包含原始 benchmark JSON、消息、JUnit/XML、设备信息、10 份 Perfetto、两个精确 APK 与覆盖
全部文件的 `SHA256SUMS`。关键摘要如下：

- benchmark JSON：`8358dee85186236092466fe5871bf79324fed73168c192b8257626df9cfee269`；
- JUnit XML：`5fc4a441af94985c57a9b9333403e1da0c9b316fbe5f6c89395b4a113b2451fa`；
- benchmark APK：`dfc8f2ffec7047336afd244995c03e7190a794fe2fb0859a1b04887c3e4b45f1`；
- target APK：`d40092003a256368480ea82e9883b35bdb7f5f8f3f07a23352eb8556a2b800ab`。

采集结束后已确认实体机 Magisk root 可用，刷新率恢复
`peak/min/user_refresh_rate=90/90/90`，`stay_on_while_plugged_in=0`，设备端临时恢复文件不存在。

## M6-2 剩余门禁

1. 解决或重新设计文本输入正式旅程的绝对阈值失败；现有最坏尾部已归因到 MIUI
   PackageManager/IME 建连锁竞争，但不能据此豁免 Goal；
2. 在 API 24、29、36 与 60/120Hz 代表设备组合上取得正式分布；
3. 只对绝对门禁通过且设备、源码、APK、编译配置完整的候选执行人工批准；
4. 用批准 baseline 在完全相同配置复跑，证明关键指标回退不超过 10%。

因此本页不勾选 M6-2，不触发 M9-3 候选冻结。

# M6-2 文本输入性能优化阶段验收（2026-07-15）

## 结论

Redmi K20 Pro、Android 12/API 31、60Hz 的 `textInput` 已从原正式候选 CPU p95/p99
14.101/33.035ms、jank 8.163%，改善到 Host 回写 batch 最终正式十轮 47 帧的
10.334/10.660ms、jank 0%。所有 frame overrun 都为负值，原先由 InputConnection 关闭触发的
同步 Binder 长尾、光标闪烁全量布局尾部和同一 Host 回写内的重复 `updateAfterEdit` 均已消除。

本子场景已通过 Goal 固定门槛：CPU p95 低于 11.6667ms，p99 低于 16.6667ms，jank 低于
1%。该结论来自完整 `PixelMacrobenchmark#textInput` 正式十轮，不使用阶段归因结果替代正式
Macrobenchmark。API 24/29/36 与 60/120Hz 代表设备、人工批准 baseline 和同配置回退比较尚未
完成，因此 M6-2 仍保持`进行中`。

## 保留的实现

1. `PixelHostView` 只把活跃非透明 Bitmap 边界提交给硬件 Canvas，透明边缘不再扩大纹理上传；
2. 引擎自有隐藏编辑器固定为 1×1，去除背景、光标和 Android 文本绘制，但继续保留完整
   Editable 与 InputConnection 契约；
3. Host 光标闪烁直接更新 retained `RenderSurface` 并只标记 paint，不再触发 Widget rebuild
   和 layout；公开 `stepCursorBlink` 的 listener 行为保持不变；
4. 同一 retained target 的 Host 文本替换改为原位 `Editable.replace`，不再通过 `setText`
   更换 buffer 和关闭当前 InputConnection；切换到不同 target 时仍显式退休旧 generation。
5. Host 向隐藏编辑器安装同一份文本、选区和 composition 时使用一次平台 batch edit，使
   `TextView` 只在结尾执行一次编辑后处理；`hostWriteDepth` 覆盖 `endBatchEdit()` 的同步回调阶段，
   防止平台 watcher 把 Host 自有回写误发布为 IME 输入。

新增或修改的类、变量和方法均补充必要中文注释。两个 Host 专用入口标记为内部 artifact API，
公开覆盖测试和 UI 静态规格测试均通过，没有把实现细节加入稳定 API 承诺。

## 正确性证据

- JVM 定向覆盖光标 listener 兼容、paint-only Pipeline 诊断和 TextInputBridge 更新路径；
- AndroidTest 编译通过；
- API 36 模拟器精确执行 Host 稀疏/空像素 Bitmap、隐藏编辑器和 grapheme InputConnection
  三个 suite，共 16/16、零失败、错误和跳过；
- 同一 target 的原连接在 Host 文本替换后继续执行 composition 与 malformed delete；不同 target
  和显式新建连接仍退休旧 generation；
- Host 回写 batch 候选在 API 36 重新执行完整 grapheme InputConnection suite，11/11、零失败、
  错误和跳过；其中覆盖嵌套 batch、delegate 拒绝 begin、并发 Host rebind、composition、selection、
  malformed delete、连接退休和跨 target 隔离；
- `PublicApiCoverageTest` 1/1、`PixelWidgetUiSpecStaticTest` 4/4、`checkPublicApi` 和
  `checkBinaryApi` 均通过。

## 性能演进

固定门槛始终为 CPU p95 ≤ 11.6667ms、p99 ≤ 16.6667ms、jank < 1%。

| 批次 | CPU p50 | CPU p95 | CPU p99 | jank | 结果 |
|---|---:|---:|---:|---:|---|
| 原正式候选 | — | 14.101ms | 33.035ms | 4/49（8.163%） | 失败 |
| 光标仅重绘归因十轮 | 4.055ms | 11.922ms | 14.308ms | 0 | p95 失败 0.255ms |
| 稳定 InputConnection 归因十轮 | 4.105ms | 11.547ms | 12.834ms | 0 | 归因批次通过 |
| 稳定 InputConnection 正式十轮 | 3.955ms | 12.148ms | 12.492ms | 0 | p95 失败 0.482ms |
| Host 回写 batch 归因十轮 | 5.340ms | 10.984ms | 12.412ms | 0 | 归因批次通过 |
| Host 回写 batch 正式十轮 | 3.846ms | 10.334ms | 10.660ms | 0 | 通过 |

Host 回写 batch 最终正式批次 frame overrun p50/p95/p99 为
-14.717/-2.495/-2.196ms，十轮每轮都有 2–6 个有效帧，合计 47 帧，
`thermalThrottleSleepSeconds=0`。相对上一版正式结果，CPU p50/p95/p99 分别下降
2.75%/14.93%/14.67%；相对原正式候选，p95/p99 下降 26.71%/67.73%，jank 从 8.163%
降为 0%。最终结论只使用新的完整正式批次，阶段归因仍只承担因果验证。

## Perfetto 因果链

光标仅重绘后，上一版约 40–54ms 的 cursor blink 尾部消失。剩余离群帧显示同一 target 的
`setText` 会在测量帧内产生 `InputConnection#closeConnection` 和同步 Binder；最坏客户端等待
7.630ms，system_server 内部由 IME/WMS monitor contention 主导。改为原位 Editable 后，测量动作
帧不再嵌套关闭连接或该 Binder 长等待。

最终正式最慢 iter007 的 Android frame timeline 对目标进程记录 6 帧、
`missed_app_frames=0`、`dropped_frames=0`。首帧 UI `Choreographer#doFrame` 为 8.400ms，
RenderThread `DrawFrames` 为 5.025ms；平台 traversal 在 draw 前加载 Lao、Hebrew、Emoji 字体。
对应诊断 trace 中 Pixel `pixel.frame` 约 3.465ms，build/layout/paint/buffer submit 分别约
0.518/0.827/0.564/0.890ms。剩余 p95 主要由冷进程输入首帧的 Android TextView 字体布局和
RenderThread 共同构成，不是新的 Pixel 阶段异常、同步 Binder、GC 或 I/O 阻塞。

Host 回写 batch 归因十轮进一步把 CPU p95 从上一归因批次的 11.547ms 降至 10.984ms。
候选最慢归因 trace 的测量帧没有同步 IME Binder、GC、I/O 或 D-state 阻塞；UI Running 比上一
对照减少约 0.509ms。对照批次的系统并发负载更高，因此不能把全部改善归因于 batch；随后独立
正式十轮仍把 p95 降到 10.334ms，作为通过门槛的决定性证据。

完整分析见：

`build/reports/performance/m6-2/redmi-k20-pro-api31-60hz-text-stable-input-connection-formal-2026-07-15/connected-output/PixelMacrobenchmark_textInput_iter007_2026-07-15-09-22-29.perfetto-trace_analysis.md`

Host 回写 batch 归因分析见：

`build/reports/performance/m6-2/redmi-k20-pro-api31-60hz-text-host-write-batch-attribution-10x-2026-07-15/connected-output/PixelFrameDiagnosticsMacrobenchmark_textInputPhaseTrace_iter007_2026-07-15-13-32-06.perfetto-trace_analysis.md`

## 拒绝的实验

曾尝试在隐藏编辑器首次布局后忽略 TextView 的 `requestLayout()`。API 36 功能测试通过，但实体机
归因测试在 100 秒内未完成，表现与每轮 `waitForIdle()` 耗尽超时一致。该实现可能破坏平台 IME
idle 契约，已立即撤回；失败批次不计性能结果，也不进入生产代码。

## 正式产物

最终通过的正式归档：

`build/reports/performance/m6-2/redmi-k20-pro-api31-60hz-text-host-write-batch-formal-2026-07-15/`

归档包含 JSON、JUnit、设备信息、十份 Perfetto、精确 benchmark APK，以及 API 36/JVM 回归
XML；`SHA256SUMS` 覆盖除清单自身外的全部 31 个文件：

- benchmark JSON SHA-256：`15aca140ec2ab3d65595b869dd90db7a639b5543f274b6824d4ad5b2567df469`；
- JUnit XML SHA-256：`5eb4a072a6e7cb77f9756879383cb279c93a928d8db23bf41524d9cb29be803d`；
- benchmark APK SHA-256：`3fe5217f0431d5dde4ec53d9721f4479e9b872464e0f3252e2048881bf2dd93b`；
- target APK SHA-256：`c9db5f3c837a2b712564d151aba1e2e2101710e7771da6c4924514163bcd6665`；
- API 36 InputConnection XML SHA-256：`7d58db7b2ab11806720689ba38384a4223a4ff2e864484e0fe0e0b57cd8c3bb6`。

采集结束后已复核实体机 Magisk root 可用，Shell policy 恢复为
`logging=1|notification=1|policy=2|uid=2000|until=0`，刷新率恢复
`peak/min/user=90/90/90`，`stay_on_while_plugged_in=0`，设备端临时恢复文件不存在。

## M6-2 剩余门禁

1. 在 API 24、29、36 与 60/120Hz 代表设备组合上取得正式分布；
2. 只对绝对门禁通过且设备、源码、APK、编译配置完整的候选执行人工批准；
3. 使用批准 baseline 在完全同配置复跑，证明关键指标回退不超过 10%。

因此本页不勾选 M6-2，也不触发依赖它的 M9-3 正式候选冻结。

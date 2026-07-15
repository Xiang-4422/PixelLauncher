# M6-2 硬件网格线批处理拒绝实验

## 结论

本实验已拒绝，生产实现已撤回，不计入 M6-2 或 M6-3 完成度。

候选版本在硬件加速 Canvas 上缓存内部 bezel 网格线坐标，并以一次
`Canvas.drawLines` 代替每帧数百次 `Canvas.drawRect`；软件 Canvas 继续使用原精确路径。
像素级回归通过，但实体 trace 没有证明应用侧 p95 改善。Macrobenchmark 表面上的大幅改善来自
Redmi vendor HWC/DRM 本轮没有出现旧批次的偶发阻塞，不能归功于候选代码。

## 测量配置

- 设备：Redmi K20 Pro，Android 12/API 31，60.000004Hz。
- 场景：`PixelMacrobenchmark.listScroll`，正式 `benchmarkRelease`，10 次迭代、10 份 Perfetto。
- 编译：`CompilationMode.Partial(BaselineProfileMode.Require)`，thermal sleep 为 0。
- 候选源 SHA-256：`1ef9055deed5f122973cdd8833a229ebb4753e037fa642da2634b194ad9ca801`。
- 候选 target APK SHA-256：`9136d2172092f12450b01eea16e6d412103e49a68d6b1262ce914f473da27321`。
- 对照：同一设备/API/60Hz 的 2026-07-15 正式 M6-2/M6-3 批次。

测试前把设备刷新率从原 90Hz 临时固定为 60Hz，并临时拒绝 Magisk 的 Shell 超级用户策略，
避免 AndroidX 的 `su root id` 环境探测挂起。采集完成后已验证恢复为
`peak/min/user=90/90/90`、`stay_on_while_plugged_in=0`，且 `su -c id` 重新返回 root。

## 表面帧指标

| 指标 | 正式对照 | 候选批次 |
| --- | ---: | ---: |
| CPU p50 | 9.097ms | 8.870ms |
| CPU p90 | 13.015ms | 12.501ms |
| CPU p95 | 17.995ms | 13.556ms |
| CPU p99 | 19.884ms | 15.533ms |
| overrun p95 | +5.192ms | -3.739ms |
| 正 overrun | 87/1,005，8.66% | 7/1,022，0.68% |

如果只看 AndroidX 汇总，候选似乎已经解决列表尾延迟；但这不足以建立代码因果关系。

## Perfetto 因果复核

把十份 trace 中目标进程的全部 `Record View#draw()` 聚合后，结果如下：

| 指标 | 正式对照 | 候选批次 | 变化 |
| --- | ---: | ---: | ---: |
| 样本数 | 1,085 | 1,064 | - |
| 平均值 | 6.107ms | 5.918ms | -3.10% |
| p50 | 5.878ms | 5.604ms | -4.66% |
| p95 | 9.453ms | 9.633ms | **+1.90%** |
| p99 | 12.024ms | 11.524ms | -4.16% |

候选没有降低应用绘制 p95，反而小幅退化。与此同时，系统图形路径发生了决定性变化：

| 系统 slice | 正式对照 p99 | 候选批次 p99 |
| --- | ---: | ---: |
| `DRMAtomicReq::Commit::` | 13.639ms | 0.516ms |
| 目标 RenderThread `dequeueBuffer` | 11.396ms | 0.184ms |

因此 8.66%→0.68% 的正 overrun 变化主要由 vendor HWC/DRM 阻塞是否出现决定；该批次不能作为
网格线批处理的收益证据，也不能成为批准 baseline。

## 正确性与拒绝处理

- API 37 模拟器两项完整 bitmap 对照均通过，整数和 fractional origin 均无像素差异。
- 定向 JVM 静态合同、AndroidTest 编译和两项 instrumentation 均通过。
- 软件 Canvas 的 bitmap、Picture、Path、`drawLines` 四种初步候选均出现退化或高波动，未纳入正式证据。
- 生产 `PixelHostView` 已恢复原内部网格线精确绘制；保留新增的整数 origin 完整 bitmap 对照测试。

机器报告和 10 份候选原始 Perfetto 位于：

`build/reports/performance/m6-2/redmi-k20-pro-api31-60hz-hardware-lines-rejected-2026-07-15/`

后续优化必须直接降低目标进程 `Record View#draw()` 的 p95/p99，并在同设备同配置的重复批次中
保持收益；不能用 vendor 阻塞恰好未出现的样本替代应用侧因果证据。

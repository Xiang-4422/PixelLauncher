# M4-3 生产级 Overlay 验收记录

## 结论

状态：**PASS**

M4-3 的四项任务与三项验收条件均已实现并通过行为测试。统一 `PixelPopupRoute<R>` 现在以
layer/insertion order 共同驱动绘制、Back/Escape、barrier、焦点、interaction、semantics 和 typed
outcome；Popover 系列使用真实 anchor bounds 与根 portal；Dialog/BottomSheet 共用 SafeArea/IME
安全视口；Toast/Snackbar 使用 active-time FIFO 队列。逻辑关闭、异常回调和多 Host 卸载路径均
不会留下 render、focus、listener、ticker 或 scheduler 残留。

## 环境

- 日期：2026-07-13，时区 `Asia/Shanghai`
- 分支：`main`
- 工作区基线提交：`a4f74b169c4a2b8dee7ca8ba36ba3ebfe343145d`
- 远端基线：`origin/main` 同为 `a4f74b169c4a2b8dee7ca8ba36ba3ebfe343145d`
- 设备：`Pixel_4(AVD) - 17`，序列号 `emulator-5554`
- SDK：37
- 型号：`sdk_gphone16k_arm64`
- 系统指纹：`google/sdk_gphone16k_arm64/emu64a16k:17/CP21.260330.005/15181570:user/dev-keys`
- AndroMeld Helper：`com.catchingnow.andfiles.helper` `1.10.194`，经用户授权保留安装
- 验收结束设置：`accessibility_enabled=0`、`enabled_accessibility_services=null`

AndroMeld Helper 只用于可视镜像辅助，不是 SDK 依赖或通过条件。正式结论来自确定性 JVM 测试、
真实 API 37 `PixelHostView` instrumentation 和完整发布门禁。

## 任务追踪矩阵

| 规格 | 实现与验证证据 | 结果 |
|---|---|---|
| 统一 OverlayEntry/PopupRoute 生命周期 | `PixelOverlayController`、`PixelOverlayEntry<R>`、layer、dismiss policy、barrier、typed outcome、多 Host presentation acknowledgement 与 close FIFO 由 `PixelOverlayLifecycleTest` 覆盖；Host Back 和 normalized Escape 使用同一 canonical 扫描。 | PASS |
| 真实 anchor 与碰撞定位 | `RenderAnchoredOverlayPortal` 使用全局 paint bounds；`AnchoredOverlayPlacementTest` 覆盖四角、上下翻转、RTL、滚动、insets、resize 和极值；`LiftedOverlayPolicyTest`/`LiftedOverlayPipelineOrderTest` 覆盖 route 总序及 Opacity、ClipRect、FittedBox×Translate scratch。 | PASS |
| Dialog/BottomSheet 安全视口与 modal 隔离 | `SafeOverlayComponentsTest` 覆盖 SafeArea/IME、居中/贴底、小窗口正文裁切和 footer 保留；`ModalOverlayIsolationTest` 覆盖 paint、hit、八类 target 与 semantics 隔离。 | PASS |
| Toast/Snackbar FIFO | `NotificationQueueTest` 覆盖 FIFO、一次性 action、active-time timeout、pause、reduce motion、controller 切换、listener 异常和 ticker/scheduler 清理；标准组件导出 live-region semantics。 | PASS |

## 三项验收

### 四角、IME、resize 与小窗口

API 37 的 `popoverFourCornersRelayoutInsideSafeViewportAndSurfaceActionCloses` 在同一真实 Host 上：

- 先使用 `48×72` 逻辑屏和 window/IME insets 验证四角 anchor 的 flip/collision；
- 再切换为 `72×48` 并改变两类 inset，验证 retained presentation 自动重新布局；
- 每个语义 bounds 均位于合并后的安全视口，右下角表面通过真实 `MotionEvent` 关闭；
- 其余三个 Popover 在局部关闭后仍保持安全位置。

Dialog/BottomSheet 的正文超过可用高度时只裁切弹性 body，footer actions 保持可见、可点击和可关闭；
隐藏正文不再导出点击、scrollbar thumb 或 semantics。

### 多层 Back、外部点击与结果顺序

`popupRoutesKeepOutcomeOrderAndLeaveNoHostResidueAfterBackBarrierAndCompletion` 同时挂载 lower modal、
typed modal 与 higher non-modal System route：

- System route 的 autofocus 按 canonical z-order 高于低层 modal；系统 Back 先关闭 System；
- 下一真实帧焦点恢复到 typed modal，表面按钮返回 typed result；
- 最后通过真实外部点击关闭 lower barrier；
- outcome 精确为 `system:Back -> typed:completed -> lower:Barrier`。

JVM 回归另覆盖 `Ignore` 继续扫描、`Consume` 拦截、同层 insertion order、不同退出时长 FIFO、
同一个异常对象被多个 outcome callback 复用、独立 modal 平台 Back，以及 route/Popover/Menu 合并后
不重复注册 handler。较高非模态 route 可以聚焦和接收键盘，但不会形成新的 traversal trap。

### 关闭后无资源残留

设备测试在全部 route 关闭后多绘一帧，并与空 Host 基线逐项比较：

- Element tree、render tree、target counts 恢复到基线；
- Overlay semantics、route focus 和 focused text input 全部消失；
- `pendingCallbackCount`、`frameListenerCount`、`activeTickerCount`、`liveTickerCount` 均为 0；
- source frame 与 `ManualFrameScheduler.pendingCount` 均为 0。

异常路径采用快照 fan-out 与 teardown failure aggregation：某个 listener、State dispose 或 outcome
callback 抛错时，兄弟卸载、Host watcher、ready outcome、焦点、dirty queue 和 ticker 清理仍会完成，
随后才报告首个异常及 suppressed failures。

## 自动化结果

最终执行命令：

```bash
./gradlew \
  :pixel-engine:testDebugUnitTest \
  :pixel-demo:testDebugUnitTest \
  :app:testDebugUnitTest \
  --no-daemon

ANDROID_SERIAL=emulator-5554 ./gradlew \
  :pixel-engine:connectedDebugAndroidTest \
  --no-daemon --no-parallel

./gradlew \
  :pixel-engine:checkPublicApi \
  :pixel-engine:checkMetalavaApi \
  :pixel-engine:checkBinaryApi \
  :pixel-engine:checkStableApiBoundary \
  :pixel-engine:checkKdocCoverage \
  --no-daemon

./tools/pixel-release-check.sh
```

| 套件 | 测试数 | failure | error | skipped |
|---|---:|---:|---:|---:|
| `pixel-engine` JVM | 997 | 0 | 0 | 0 |
| `pixel-demo` JVM | 17 | 0 | 0 | 0 |
| `app` JVM | 328 | 0 | 0 | 0 |
| API 37 connected | 24 | 0 | 0 | 0 |

M4-3 重点 JVM 定向集为 88 项，包含 lifecycle、focus、modal isolation、standalone Back、queue、
anchor、lifted plane、raw pipeline order 和外部 SPI；设备生产 Overlay 类为 2/2。完整 connected
报告还包含既有 Host lifecycle、interaction、accessibility、multi-Host focus、predictive back 和
multi-stack 回归。

## API、文档与发布门禁

- Public、Metalava、Binary 三份生成报告与 `pixel-engine/api/` baseline 逐字一致。
- stable API boundary 通过；新增 focus、portal 与 Back helper 均保持 internal。
- released Metalava compatibility 为 `SKIPPED/NO_RELEASED_BASELINE`：仓库尚无首次正式外部发布
  signature；真实旧消费者二进制运行检查已通过，首次正式发布时仍须冻结 released baseline。
- KDoc：931/1674，覆盖率 55.62%，高于 35% 门禁；新增/修改声明均补必要注释。
- Release AAR：2,432,162 bytes。
- Release AAR SHA-256：
  `1f217d629c4febdc7eb77469166cff0a670d88d5f517db47b3823d4a55f6e5f4`。
- 独立 SDK consumer、真实 SPI consumer、RouteEntry consumer、旧消费者二进制、安全、备份、
  Lint、Release publication、soak 和 `mkdocs build --strict` 均通过。
- 六个性能场景均通过，`overallPass=true`；本机平均值依次为 list scroll 1.716ms、text input
  0.204ms、animation 0.036ms、graphics primitives 0.128ms、page transition 0.188ms、Overlay
  0.321ms。

## 报告与产物

```text
pixel-engine/build/reports/androidTests/connected/debug/
pixel-engine/build/outputs/androidTest-results/connected/debug/
pixel-engine/build/reports/api/
pixel-engine/build/reports/kdoc/kdoc-coverage.txt
pixel-engine/build/reports/compatibility/stable-api-boundary.json
pixel-engine/build/reports/perf/pixel-engine-render-smoke.txt
pixel-engine/build/outputs/aar/pixel-engine-release.aar
build/reports/compatibility/
build/reports/security/
```

## 遗留与边界

- M4-3 的 P0/P1 代码、测试、文档和验收遗留为零。
- API 24/29/36 设备矩阵、真实低内存/窗口管理器组合和更长时间性能趋势属于全局 M8/M6 验收，
  不以本次单一 API 37 设备结果替代。
- AndroMeld Helper 经用户授权保留安装，但 SDK、Demo、消费者与发布产物均不依赖它。

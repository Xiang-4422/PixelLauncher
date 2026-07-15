# M5-2C 生产级 Overlay 公开组件契约验收

日期：2026-07-13

结论：通过

## 验收范围

本验收证明 `BottomSheet`、`Dialog`、`Menu`、`Popover`、`Dropdown`、`Tooltip` 六类公开组件实际消费 M4-3 的生产级 Overlay 能力，而不是仅存在同名 API。统一契约覆盖 typed route 所有权、真实 anchor、安全视口与 IME、modal 隔离、焦点与 Back、关闭顺序以及资源卸载。

## 公开入口与实现映射

| 公开组件 | 生产契约接入 | 关键行为 |
| --- | --- | --- |
| `Dialog` | `SafeOverlayViewportWidget`、`SafeOverlayBodyViewportWidget`、standalone/route modal coalescing | 居中安全视口、长正文裁切、footer 保留、焦点陷阱、Back 与 dismiss |
| `BottomSheet` | 与 Dialog 共用 safe-surface builder，使用 BottomCenter 对齐 | 填满安全宽度、IME 上移、正文裁切、焦点与 Back |
| `Popover` | `AnchoredOverlayPortalWidget`、`AnchoredOverlayFollowerWidget`、modal boundary | 真实全局 anchor、翻转/碰撞、RTL、resize/IME 重定位、逻辑关闭与视觉退出分离 |
| `Menu` | collection semantics、standalone modal boundary、嵌套 modal coalescing | 方向键/焦点、Dismiss、独立使用或嵌套 Popover 时只有一个 modal/Back owner |
| `Dropdown` | 受控 anchor + Dropdown token Menu + modal Popover | 展开/收起语义、真实 anchor、Menu 集合、外部点击与 Back 统一回到 `onToggle` |
| `Tooltip` | passive、non-modal Popover | 真实 anchor 与安全视口、无背景隔离、无可变操作或独立 Back owner |

六类组件位于 `PixelPopupRoute` 下时均读取 route 提供的 modal presence，公开组件不得注册第二个 canonical Back owner，也不得绕过 typed route outcome。

## JVM 行为证据

执行命令：

```bash
./gradlew :pixel-engine:testDebugUnitTest \
  --tests com.purride.pixelui.widgets.ProductionOverlayPublicContractTest \
  --tests com.purride.pixelui.widgets.SafeOverlayComponentsTest \
  --tests com.purride.pixelui.widgets.AnchoredOverlayPlacementTest \
  --tests com.purride.pixelui.widgets.ModalOverlayFocusTest \
  --tests com.purride.pixelui.widgets.ModalOverlayIsolationTest \
  --tests com.purride.pixelui.widgets.StandaloneModalBackTest \
  --tests com.purride.pixelui.widgets.LiftedOverlayPolicyTest \
  --tests com.purride.pixelui.widgets.OverlayThemeStateTest \
  --no-daemon
```

结果：43/43，通过；0 failure、0 error、0 skipped。

其中新增的 `ProductionOverlayPublicContractTest` 使用同一 typed `PixelPopupRoute<Unit>` 逐个挂载六类真实公开组件，并验证：

- 每个组件的真实 presentation semantics 已挂载；
- route 是唯一 canonical Back owner；
- Back 产生 `PixelOverlayOutcome.Dismissed(Back)`；
- 组件内部 dismiss/toggle 回调不会越过 route；
- entry 进入 `Disposed`，controller size 归零；
- Back registration、presentation semantics 与 live ticker 全部清零。

其余 42 项覆盖安全视口/IME、四角 anchor、翻转与碰撞、RTL、窗口 resize、local clip 逃逸、modal target/semantics 隔离、焦点进入/恢复、嵌套 coalescing、predictive/discrete Back、lifted plane 顺序以及主题状态。

报告目录：`pixel-engine/build/test-results/testDebugUnitTest/`

## API 37 Host 证据

当前 API 37 connected 报告为 28/28，通过；0 failure、0 error、0 skipped。`PixelHostProductionOverlayInstrumentedTest` 的两项真实 Host 用例验证：

- 四个屏幕角、真实 MotionEvent、SafeArea/IME 与窗口变化下 Popover 保持可见并可关闭；
- typed route 的 Back、barrier、completion、outcome FIFO、modal 焦点交接和最终 Element/render/target/focus/ticker/scheduler 清零。

设备报告：`pixel-engine/build/reports/androidTests/connected/debug/`

机器结果：`pixel-engine/build/outputs/androidTest-results/connected/debug/TEST-Pixel_4(AVD) - 17-_pixel-engine-.xml`

## 结论与遗留

M5-2C 没有新增第二套 Overlay 引擎；六类公开入口复用并被行为测试证明接入 M4-3 契约。当前 P0/P1 代码、测试、文档和验收遗留为零。M5-2 的 API 37 聚合复验与完整 release gate 仍由 M5-2E 执行，本子包完成不代表 M5-2 总里程碑完成。

# M4-2 每 Host 焦点与多输入方式验收记录

## 结论

状态：**PASS**

M4-2 的五项任务与三项验收条件均已实现并通过行为测试。焦点、快捷键和文本输入状态由
每个 `PixelUiRuntime`/Host 独立持有；标准组件共享键盘、DPAD、游戏手柄映射和 Android
无障碍 action 的业务回调；Dialog、Menu、Popover 在逻辑打开期间陷阱焦点并隔离背景，
关闭时在退出绘制完成前立即恢复正确焦点和交互所有权。

## 环境

- 日期：2026-07-13，时区 `Asia/Shanghai`
- 分支：`main`
- 工作区基线提交：`a4f74b169c4a2b8dee7ca8ba36ba3ebfe343145d`
- 设备：`Pixel_4(AVD) - 17`，序列号 `emulator-5554`
- SDK：37
- 型号：`sdk_gphone16k_arm64`
- 系统指纹：`google/sdk_gphone16k_arm64/emu64a16k:17/CP21.260330.005/15181570:user/dev-keys`
- TalkBack：`17.0.0.889642762`，验收结束时未启用
- Switch Access：`1.17.0.877181440`，验收结束时未启用
- AndroMeld Helper：`com.catchingnow.andfiles.helper` `1.10.194`，经用户授权保留安装
- 验收结束设置：`accessibility_enabled=0`、`enabled_accessibility_services=null`、
  `Bound services={}`

AndroMeld 只用于可视镜像辅助，不是 SDK 依赖或通过条件。本工作包没有把未执行的实体手柄、
真实 Switch Access 扫描体验或设备矩阵冒充为自动化通过。

## 任务追踪矩阵

| 规格 | 实现与验证证据 | 结果 |
|---|---|---|
| 每 Host/每 focus scope 所有权 | `PixelFocusOwnerIsolationTest` 覆盖两个 `PixelTester`、原始 runtime、默认节点 retained 与跨 runtime 重挂载；`PixelHostMultiHostFocusInstrumentedTest` 在同一 Activity 挂载两个真实 Host。 | PASS |
| 标准组件 focusability 与键盘行为 | `PixelFocusTest`、`StandardComponentKeyboardInputTest`、`SlidableKeyboardInputTest` 覆盖 disabled skip、Tab/Shift+Tab、方向键、Enter/Space，以及 Button、TextField、Slider、ValueAdjuster、Tabs、SegmentedControl、RefreshIndicator、Slidable。 | PASS |
| 键盘、DPAD、游戏手柄、Switch Access action 路由 | `PixelAndroidKeyMapperTest`、`PixelJoystickFocusRouterTest` 和 Host instrumentation 覆盖 Android key/joystick 映射、repeat/dead-zone 与统一 focus action；`StandardComponentsAccessibilityInstrumentedTest` 通过公共 `AccessibilityNodeInfo` action 验证 Switch Access 兼容路径。 | PASS |
| Dialog/Menu/Popover 模态契约 | `ModalOverlayFocusTest` 覆盖初始焦点、autofocus、陷阱、嵌套恢复、不可关闭模态和 retained exit；`ModalOverlayIsolationTest`、`ModalInteractionPipelineTest` 覆盖背景 semantics、命中和八类 target 隔离。 | PASS |
| 文本焦点、IME action、快捷键优先级 | `PixelFocusTest`、`StandardComponentKeyboardInputTest`、`PixelTextInputBridgeTest` 验证应用快捷键优先、组件默认动作次之、遍历最后；双 Host instrumentation 验证输入、IME `NEXT`、clear 和 destroy 不串扰。 | PASS |

## 三项验收

### 仅使用键盘或 DPAD 完成核心 Demo

`AccessibilityFlowKeyboardTest` 的两项真实 Demo 场景通过：

- `tabEnterAndSpaceCompleteTheCoreWorkflow` 从 Name 文本输入开始，依次完成 Slider 调整、
  动态列表添加/选择、Dropdown/Menu 选择、Dialog 打开/关闭、详情路由进入和返回。
- `logicalDpadKeysAdjustSliderAndNavigateMenu` 使用逻辑 DPAD 调整 Slider，并在 Menu 模态
  scope 内上下移动和激活。

流程没有调用 pointer 或 semantics action。`enterText` 只模拟物理文本输入，导航与动作均由
Tab、方向键、Enter 或 Space 完成。

### 两个 Host 不串扰

API 37 上的两个设备测试均通过：

- Host A 的 Enter、Tab、Space 不改变 Host B 的焦点和计数；Host B 的 Android DPAD 事件也
  不改变 Host A。
- 两个 TextField 各自拥有 IME show/update/hide 请求。Host A 更新、提交 `NEXT`、clear 和
  destroy 后，Host B 的文本、焦点、show/hide 计数保持不变，随后仍可独立编辑。

### 模态焦点打开与关闭

- Popover/Menu 打开后焦点进入首个 enabled descendant；显式 autofocus 优先。
- Tab、Shift+Tab 和方向遍历不会越过当前最上层 modal scope。
- Menu 嵌入 Popover 时共用同一 presentation；Menu 嵌入 Dialog 时保持独立顶层 modal，关闭
  后先恢复 Dialog opener，再在 Dialog 关闭时恢复背景 opener。
- Escape/Back 优先交给最上层 modal。不可关闭 modal 会消费 Back，避免背景误触发。
- 逻辑关闭的第一帧立即恢复焦点和背景 semantics/interaction；退出动画只保留绘制。

## 自动化结果

最终执行命令：

```bash
./gradlew \
  :pixel-engine:testDebugUnitTest \
  :pixel-demo:testDebugUnitTest \
  :app:testDebugUnitTest \
  :pixel-engine:dumpPublicApi \
  :pixel-engine:generateMetalavaApi \
  :pixel-engine:dumpBinaryApi \
  --rerun-tasks --no-daemon --no-parallel

ANDROID_SERIAL=emulator-5554 ./gradlew \
  :pixel-engine:connectedDebugAndroidTest \
  --no-daemon --no-parallel

./tools/pixel-release-check.sh
```

| 套件 | 测试数 | failure | error | skipped |
|---|---:|---:|---:|---:|
| `pixel-engine` JVM | 934 | 0 | 0 | 0 |
| `pixel-demo` JVM | 13 | 0 | 0 | 0 |
| `app` JVM | 328 | 0 | 0 | 0 |
| API 37 connected | 22 | 0 | 0 | 0 |

完整 connected 报告包含 `PixelHostMultiHostFocusInstrumentedTest` 2 项、
`StandardComponentsAccessibilityInstrumentedTest` 3 项，以及既有 Host 生命周期、交互、
Accessibility、predictive back 和 multi-stack 回归，总计 22/22。

## API、文档与发布门禁

- Public、Metalava、Binary 三份生成报告与 `pixel-engine/api/` baseline 逐字一致。
- internal `ModalFocusScope` 不出现在 binary baseline；stable API boundary
  `findingCount=0`。
- 本轮追加默认参数属于已登记的 `0.1.0-SNAPSHOT` 重编译边界，已写入 `CHANGELOG.md` 和
  `docs/migrations/1.0.0-modal-focus.md`。
- released Metalava compatibility 为 `SKIPPED/NO_RELEASED_BASELINE`：仓库尚无首次正式外部
  发布签名；真实旧消费者二进制运行检查已通过。首次正式发布时仍须冻结 released baseline。
- KDoc：881/1619，覆盖率 54.42%，高于 35% 门禁；M4-2 新增/修改声明已补必要注释。
- Release AAR：2,257,555 bytes。
- Release AAR SHA-256：
  `eb6023c3c4a116eee33b7fc1b1cb3ebbbb071fe3675fd447e0b05c93111e4078`。
- 六个性能场景均通过，`overallPass=true`；soak、独立 SDK consumer、真实 SPI consumer、
  RouteEntry consumer、旧消费者二进制、安全、备份、Lint 和 `mkdocs build --strict` 均通过。

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

- M4-2 的 P0/P1 代码、测试、文档和验收遗留为零。
- 实体游戏手柄矩阵和真实 Switch Access 扫描体验留给全局设备矩阵；当前结论只覆盖 Android
  映射、joystick 行为和公共 virtual-node action 路径。
- Popover 的 anchor bounds 定位、边缘翻转和碰撞规避属于后续 M4-3。本次位于 Host 底部的
  Dropdown 验收显式向上偏移，符合当前“组件不自动避让边缘”的公开契约。

> 后续状态：M4-3 已实现真实 anchor portal 与碰撞翻转，并从 instrumentation fixture 删除上述
> 负 offset 补偿。此段保留为 M4-2 当时验收边界的历史记录，不再代表 1.0.0 当前行为。

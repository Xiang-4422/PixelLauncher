# M5-2 高价值组件与 API 37 验收

## 结论

2026-07-13，M5-2A～E 已在同一最终集成态完成。当前公开主题图包含 25 个生产组件族，
`ProductionComponentStateMatrixTest` 对 25×8＝200 个状态格逐一构建真实公开工厂并检查像素、
焦点、capability 与语义动作。Radio、RadioGroup、IconButton、FormFieldDecoration、
NavigationBar、NavigationRail，以及 Tabs、SegmentedControl、Slidable 的加固契约均已进入
JVM、可审阅 snapshot/pixel golden、Demo、API baseline 和 API 37 Android virtual-node 路径。

M5-2 范围内没有 P0/P1 代码、测试、文档或验收遗留。M5-3 的国际化/Unicode/自适应，
M8 的 API 24/29/36 设备矩阵，以及 M9 的全仓 public KDoc 100% 和正式发布基线仍按 Goal
后续里程碑推进，不能由本次 API 37 结果替代。

## 最终实现契约

| 子包 | 最终行为 | 主要生产入口与证据 |
|---|---|---|
| M5-2A | 受控 Radio、稳定业务 ID 的 RadioGroup、强制非空语义名称的 IconButton；单 Tab stop、循环方向键、跳过 disabled、Loading 保焦但无 mutation、SINGLE collection/item metadata | `SelectionControls.kt`；新增 JVM 9/9；API 37 selection-controls virtual node 用例通过 |
| M5-2B | additive TextField overload 接入 label、helper/error、required 与调用方 counter；error 覆盖 helper，counter 保留；固定槽位保留输入 identity、focus、selection、composition 与 IME target | `FormFieldDecoration.kt`、`FormFieldDecorationWidget.kt`；新增 JVM 8/8，相关聚合回归 37/37；Android `isContentInvalid` 与 edit/selection action 通过 |
| M5-2C | BottomSheet、Dialog、Menu、Popover、Dropdown、Tooltip 统一消费 typed route、唯一 Back 所有权和完整卸载契约 | `ProductionOverlayPublicContractTest` 与 `m5-2c-overlay-public-contract-2026-07-13.md`；Overlay 定向 JVM 43/43 |
| M5-2D | 受控及 `PixelMultiStackNavigatorController` 绑定版 NavigationBar/Rail；稳定 destination ID、动态重排、重选回根、独立栈保持、Back fallback、横/纵方向键和 SINGLE collection | `NavigationControls.kt`；定向 JVM 6/6，相关导航聚合 17/17；动态重排 semantic ID 与 public key 均保持 |
| M5-2E | Tabs/SegmentedControl 的稳定 label identity、严格输入与集合元数据；Slidable 的 EXPAND/COLLAPSE/DISMISS；25 族、200 格最终集成 | `SingleSelectionCollectionContractTest` 2/2、`SlidableAccessibilityActionsTest` 2/2、生产矩阵 3/3、M5-2 snapshot/golden 2/2 |

`FormFieldDecoration` 复用 `components.textField`，不是第 26 个组件族。DataGrid、Tree 等复杂数据
组件没有在无真实消费场景时扩入 1.0 范围。

## JVM、Demo 与发布门禁

最终完整测试报告为：

| 范围 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `pixel-engine` JVM | 1116 | 0 | 0 | 0 |
| `pixel-demo` JVM | 26 | 0 | 0 | 0 |
| Launcher app JVM | 328 | 0 | 0 | 0 |
| Python tooling | 33 | 0 | 0 | 0 |

执行并通过：

```bash
./gradlew \
  :pixel-engine:testDebugUnitTest \
  :pixel-demo:testDebugUnitTest \
  :app:testDebugUnitTest

bash tools/pixel-release-check.sh
python3 -m mkdocs build --strict
```

完整 release gate 同时通过：worktree 与 APK/AAR secret scan、backup contract、三份 API/ABI
baseline、stable/internal boundary、25/25 component 与 10/10 foundation token 扫描、KDoc、Lint、
Release AAR、sources/Javadoc、隔离 file-Maven SDK consumer、外部 RenderObject SPI、RouteEntry 正负
consumer、冻结旧二进制 consumer、六场景性能和 lifecycle soak。性能报告
`pixel-engine/build/reports/perf/pixel-engine-render-smoke.txt` 为 `overallPass=true`。

项目尚未存在正式发布过的 `pixel-engine.released.metalava-api`，因此
`checkMetalavaReleasedCompatibility` 按既有预发布契约记录为 `SKIPPED/NO_RELEASED_BASELINE`；
current public、binary、Metalava 三份 reviewed baseline 与旧二进制真实运行均已通过。这不是测试跳过，
正式发布签名冻结继续属于 M9-3。

## Snapshot、pixel golden 与 Demo

`M52HighValueComponentSnapshotTest` 使用只读、byte-for-byte 基线：

- `src/test/resources/element-snapshots/m5-2-high-value-component-semantics.txt`：记录新组件层级、
  几何、value/hint/error、selection、SINGLE collection/item 与 Slidable 动作状态；
- `src/test/resources/golden/m5-2-high-value-components.txt`：183 行可人工审阅 ASCII 像素 golden，
  覆盖 Radio/RadioGroup/IconButton、decorated TextField、NavigationBar 与 NavigationRail；
- 两项测试均没有 `REGEN`、`writeText` 或自动接受路径。

Theme Showcase 已按 `PixelComponentTokens` 构造顺序升级为 25 个真实公开生产工厂与 200 个状态格，
并提供真实可编辑 FormFieldDecoration、Radio、IconButton、NavigationBar 与 NavigationRail。Demo
全量 26/26 通过，新增交互测试还验证受控状态和文本跨主题切换保持。

## API 37 Android Host

设备：

- 序列号：`emulator-5554`
- API：37 / Android 17
- 型号：`sdk_gphone16k_arm64`
- fingerprint：`google/sdk_gphone16k_arm64/emu64a16k:17/CP21.260330.005/15181570:user/dev-keys`

最终命令：

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew \
  :pixel-engine:connectedDebugAndroidTest \
  --rerun-tasks
```

结果：32/32，0 failure、0 error、0 skipped。报告：
`pixel-engine/build/outputs/androidTest-results/connected/debug/TEST-Pixel_4(AVD) - 17-_pixel-engine-.xml`。

其中 `M52ComponentAccessibilityInstrumentedTest` 的 4/4 真实 Activity-attached
`PixelHostAccessibilityNodeProvider` 用例覆盖：

- Radio、RadioGroup 与 IconButton 的 Android class、check/click、SINGLE metadata 和重排后 virtual ID；
- decorated TextField 的唯一 EditText、hint/error、`isContentInvalid`、set-text 与 set-selection；
- Tabs/SegmentedControl 的 Android SINGLE collection/item；
- Slidable expand→collapse/dismiss 以及 Loading 无 mutation action。

API 37 还安装并实际进入当前 `pixel-demo` 的 Theme Showcase。可复核截图：

| 截图 | SHA-256 | 观察结果 |
|---|---|---|
| `pixel-engine/build/reports/screenshots/m5-2-theme-showcase-api37.png` | `be1ba1c60cbd4dfb4d3c6d4efcd7d76d470cd6c8b96ca56ee4565cabdd7b4976` | 页面标题、五套主题入口和 `PRODUCTION COMPONENTS (25)` 实际绘制 |
| `pixel-engine/build/reports/screenshots/m5-2-theme-showcase-components-api37.png` | `ba5f101422b845077b7043abbc816ec24b117904ab77f7b5f28460b425b40682` | Checkbox 与新增 Radio 的真实像素表面和 Android virtual node 可见 |
| `pixel-engine/build/reports/screenshots/m5-2-theme-showcase-navigation-api37.png` | `962736aa5af8342302fcf18ef37151759d2c8773613248e6aec8f44232ae851b` | NavigationBar 三等分布局与 NavigationRail 纵向布局实际绘制 |

## API 与产物

三份 reviewed baseline 已更新并通过：

- `pixel-engine/api/pixel-engine.api`
- `pixel-engine/api/pixel-engine.binary-api`
- `pixel-engine/api/pixel-engine.metalava-api`

新增签名只包含 4 个 component token、Radio 系列、IconButton、FormFieldDecoration、
PixelNavigationDestination、NavigationBar/Rail 和两个 additive TextField overload；旧 TextField/
FormField 描述符与 `$default` bridge 的反射测试继续通过。

最终 Release AAR：`pixel-engine/build/outputs/aar/pixel-engine-release.aar`，SHA-256：
`4ca786e1407f667f5d8a44b444a967fb22a6a58c03e13e2af4588da54227fcc1`。

用户文档已同步 `README.md`、`docs/使用说明与API手册.md` 和
`docs/migrations/1.0.0-high-value-components.md`；严格 MkDocs 构建与全仓 whitespace 检查通过。

# M5-3E Viewport、Android capability、自适应布局与多 API 验收

## 结论

2026-07-14，M5-3E 已完成。Host 的 profile 选择与 viewport 投影已拆成两个正交层：profile
决定逻辑画布，viewport 决定逻辑画布如何以 Contain/Cover、Integer/Fractional 和九宫格 alignment
投影到物理窗口。旧 `ScaleMode.FIT_CENTER` 精确映射为 Contain + Integer + Center，继续保留
历史像素、触摸逆变换和旧 inset 行为。

Android Host 现在从一份生命周期受控的 capability source 原子发布 locale、layout direction、
text scale、density、高对比度、motion、refresh rate 和逻辑 display features；原始物理 system bar、
IME、cutout/display-feature 数据独立保留，并会在 resize、profile、viewport 或 density 变化后重新
投影。API 24/29/30/37 的 platform WindowInsets 分支均有定向设备证据，API 24 与 API 37 的完整
instrumentation 套件分别达到 53/53 和 58/58。

Text、RichText、TextField、RTL Flex、高对比度 theme helper 与 `AdaptiveBuilder` 已接入同一 Host
环境；窗口和环境更新不会重建 retained 业务身份。完整 JVM、API/ABI、旧二进制、外部 consumer、
Lint、Release AAR、性能、soak 和严格 MkDocs 门禁通过。M5-3E 范围内没有 P0/P1 代码、测试、
文档或兼容遗留。

## Profile 与 viewport 正交契约

`PixelHostProfilePolicy` 提供四种明确策略：

- `Fixed` 保留既有手工 `screenProfile` 的兼容语义；
- `AdaptivePixels` 从当前物理 viewport 与 dot size 重新计算逻辑像素 profile；
- `AdaptiveDp` 以 density-independent 目标选择 profile；
- `AdaptiveLogicalSize` 由调用方按照当前物理尺寸、density 和方向返回逻辑尺寸。

configured adaptive policy 在窗口尺寸、density、dot size 或 viewport strategy 改变时重新求值。
单纯求得相同 profile 不会发布冗余环境更新。`ScreenProfile` 与 `PixelHostSetupConfig` 的冻结构造器、
`copy`、`componentN` 和旧 JVM 描述符未改变。

`PixelViewportPolicy` 把三个维度正交组合：

| 维度 | 取值 | 契约 |
|---|---|---|
| fit | Contain / Cover | 等比 letterbox 或等比裁切，不默认 stretch |
| quantization | Integer / Fractional | 整数点缩放或连续缩放 |
| alignment | 9 个物理锚点 | 决定剩余空间或裁切 origin，不随 RTL 偷换物理方向 |

paint、pointer/touch inverse、semantics、Accessibility 和 inset 重投影均消费同一份
`PixelGridGeometry`。定向测试覆盖四种 fit/quantization 组合、九宫格 alignment、负 origin、
cover crop、resize 后旧 geometry 清除，以及 legacy mapping 精确相等。

## Android capability source 与生命周期

`AndroidPixelHostCapabilitiesSource` 在主线程读取 Android Configuration、Display 与 Accessibility
状态，再发布平台中立的 immutable `HostCapabilitiesData`。公开 capability 签名不包含
`android.*`、Android `Rect` 或 AndroidX Window 类型。

生命周期契约如下：

- attach 时注册 configuration、display 与 accessibility 观察，并发布当前完整快照；
- detach 时解除平台监听，暂停期间只保留最新待发布状态；
- resume/re-attach 后发布最新快照，不重放过期中间态；
- destroy 后不再接受平台回调；
- 测试/消费者 override 与自动 source 有明确优先级，清除 override 后恢复最新平台状态；
- capability、Directionality、MotionScope、MediaQuery 与 adaptive environment 从同一帧快照派生，
  不暴露 locale 已更新而 direction 仍陈旧的中间帧。

`PixelHostCapabilitiesSourceInstrumentedTest` 的 5 个 Android 用例覆盖主线程、attach/detach、
configuration、density/font scale/high contrast、refresh/display feature 和 override 合并；JVM
integration/lifecycle suites 另外覆盖 distinct 更新、暂停合并与 retained dependency rebuild。

## 原始 WindowInsets 与多 API 分支

Host 保存物理坐标中的 system bars、IME、cutout 和 display feature source，不把首次转换后的逻辑
值当作事实源。每次窗口、profile、policy 或 density 变化都会从原始物理数据重新计算
`viewPadding`、`viewInsets`、SafeArea 与逻辑 display features，因此不会遗留旧 geometry。

API 分支为：

- API 30+ 使用 typed `WindowInsets.Type.systemBars()`、`ime()` 与 `displayCutout()`；
- API 29 使用平台 Builder 的 current/stable inset 通道；
- API 24–28 使用 legacy current/stable platform getters，并从二者确定 system bar 与 IME 差值，
  不再无条件把 `viewInsets` 设为 Zero。

真实 `dispatchApplyWindowInsets` 定向用例统一注入物理 system bars `(8,16,24,32)` 与 IME bottom
`80`，在 dot size 8 下断言逻辑 bars `(1,2,3,4)` 与 IME bottom `10`。API 24 没有公开 Builder
可以分别写入 current/stable 通道，因此仅测试 APK 使用隔离的 reflection fixture 构造平台对象；
生产代码没有 hidden API 反射，且 fixture 会先验证平台 getter 的真实结果再 dispatch。

| 设备/API | 验证范围 | tests | failures | errors | skipped |
|---|---|---:|---:|---:|---:|
| API 24 / Android 7.0，`google/sdk_google_phone_arm64/generic_arm64:7.0/NYC/8695085:userdebug/dev-keys` | 完整 instrumentation，含 legacy current/stable/IME 分支 | 53 | 0 | 0 | 0 |
| API 29 / Android 10，`google/sdk_gphone64_arm64/emulator64_arm64:10/QSR1.211112.011/13135432:userdebug/dev-keys` | M5-3E platform Builder current/stable 定向分支 | 1 | 0 | 0 | 0 |
| API 30 / Android 11，`google/sdk_gphone_arm64/emulator_arm64:11/RSR1.240422.006/12134477:userdebug/dev-keys` | M5-3E typed systemBars/IME 定向分支 | 1 | 0 | 0 | 0 |
| API 37 / Android 17，`google/sdk_gphone16k_arm64/emu64a16k:17/CP21.260330.005/15181570:user/dev-keys` | 完整 instrumentation，含 typed insets/cutout/capability | 58 | 0 | 0 | 0 |

M5-3E 只要求 API 29/30 的差异分支证据；API 24/29/30/37 全量设备矩阵继续由 M8 收口，本报告
不把两个定向用例写成完整矩阵。

## Text scale、高对比度、RTL 与 AdaptiveBuilder

Text、RichText 和 TextField 在布局与绘制阶段消费 Host `textScale`，相同 capability 不触发重复
布局，scale 改变会使 paragraph、caret、selection 与 Accessibility bounds 一起失效并重算。
`PixelThemeTokens.forCapabilities` 与 `forHost` 按高对比度 capability 选择 normal/high-contrast
token，同时保留显式 theme 输入和旧默认 token 行为。

RTL Row/Flex 现在翻转真实 child 视觉排列与 focus/pointer geometry，而 retained child、声明顺序和
semantics traversal identity 保持稳定。START/END alignment 继续按照 direction 解析，不通过修改
源 child 列表制造状态错位。

`AdaptiveBuilder(builder = { environment -> ... })` 公开不可变的
`PixelAdaptiveEnvironment`/`PixelAdaptiveLayoutData`，包含 logical/physical size、window size class、
orientation、profile、viewport、insets 和 display features。SafeArea、IME、cutout、hinge/fold、
分屏、小窗口、density、portrait/landscape 与动态 resize 都由相同环境快照驱动。

定向 retained 测试在 profile、locale/direction、textScale、contrast、density、inset、window class
和 display feature 切换前后验证 State、focus、scroll offset、TextField 文本、selection/composition、
导航与 semantic/public identity 不丢失。

## JVM、API、consumer 与发布门禁

最终完整测试结果：

| 范围 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `pixel-engine` 完整 JVM | 1,259 | 0 | 0 | 0 |
| `pixel-demo` JVM | 26 | 0 | 0 | 0 |
| Launcher app JVM | 328 | 0 | 0 | 0 |
| Python tooling / SDK consumer | 40 | 0 | 0 | 0 |
| API 24 完整 instrumentation | 53 | 0 | 0 | 0 |
| API 37 完整 instrumentation | 58 | 0 | 0 | 0 |

主要 M5-3E 定向 JVM suites 包括：

- `PixelGridGeometryResolverTest` 7/7；
- `PixelHostProfilePolicyTest` 5/5；
- `PixelHostCapabilitiesIntegrationTest` 5/5；
- `AdaptiveTextContrastRtlTest` 4/4；
- `AdaptiveBuilderTest` 3/3；
- `PixelHostLifecycleCoordinatorTest` 的 legacy/raw inset 与 capability 生命周期分支。

三份 current baseline 只新增 adaptive Host API，没有删除或修改旧签名：

| baseline | 当前 SHA-256 |
|---|---|
| public API | `d5dbc19297af15af6e04e9584c8251a6df3d34612492143c5a74af75cc9ff74a` |
| JVM binary API | `495925d4f0810497bae404dff21f5b7f55cf887f8d696c25614c6236be947806` |
| Metalava API | `2ba16f4419b76f936c9e8f2c7d6055b6a9997e9b68700dd9308ed9efb06716a0` |

新增面包括 `PixelHostProfilePolicy` 四种策略、`PixelHostView.profilePolicy`、
`PixelWindowOrientation`、`PixelAdaptiveLayoutData`、`PixelAdaptiveEnvironment`、`AdaptiveBuilder`、
`PixelThemeTokens.forCapabilities/forHost`。隔离 Kotlin consumer 已真实构造这些 API 并组装独立
APK；Java/旧 AAR consumer、stable boundary、Render SPI 与 RouteEntry compatibility 同时通过。

最终 `tools/pixel-release-check.sh` 成功，覆盖 secret scan、backup contract、Unicode generators、
三份 API/ABI、stable boundary、KDoc、tooling/JVM、Lint、Release AAR/POM/sources/Javadoc、外部
consumer、25/25 component token、10/10 foundation group、六场景 perf、soak 和严格 MkDocs。
released Metalava 在正式 1.0 signature 尚未冻结前按既有预发布契约记录
`SKIPPED/NO_RELEASED_BASELINE`，不属于测试跳过；M9-3 负责冻结。

KDoc 为 1,216/2,176（55.88%，当前阶段门槛 35%）；全仓 public/protected 100% 由 M9-1
收口。Release AAR 为 3,205,125 bytes，SHA-256：
`38abc3685fe8cb9c2d5640e66971c96659e6cf9d80206718e62d4da05857ca23`。

## 文档与后续边界

README、使用手册、架构、CHANGELOG 和
`docs/migrations/1.0.0-adaptive-host-viewport.md` 已同步 profile/viewport 分层、自动/override
capability、raw inset 重投影、AdaptiveBuilder、textScale、高对比度与 RTL 行为。

- M5-3F 负责综合 Adaptive & Localization Demo、只读 golden、M5-3 最终集成和最终文档复核；
- M8 负责 API 24/29/30/37 的完整兼容矩阵与更广的真实设备覆盖；
- M9-1/M9-3 分别负责全仓 KDoc 100% 与正式 1.0 released signature 冻结。

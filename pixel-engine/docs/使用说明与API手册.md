# pixel-engine 使用说明与 API 手册

本文面向使用 Pixel Engine 1.0 构建像素 UI 的开发者。首次接入建议先读
[Quickstart](guides/quickstart.md)，再按需阅读 [Host](guides/host-integration.md)、
[主题](guides/theme-and-components.md)、[路由](guides/navigation.md)、[资源](guides/resources.md)、
[架构与 SPI](架构与设计.md)、[测试](guides/testing.md) 与 [性能](guides/performance.md)。
内部实现和维护规则见 [架构与设计](架构与设计.md)。

## 1. 快速接入

pixel-engine 是完整的单模块 Android library SDK，最低 `minSdk = 24`，当前 `compileSdk = 36`。
Launcher 与外部消费者都只依赖 `com.purride:pixel-engine`；核心、运行时、组件、路由、Android
Host、测试工具和调试能力由同一 AAR 提供，不再维护拆分 artifact。Compose Host 不进入主 Engine，
避免 Compose 编译器改写普通 SDK ABI；需要 Compose 时由应用使用 `AndroidView` 承载 `PixelHostView`。

### 消费者构建环境

1.0 发布物的消费者边界如下；这里描述的是“使用 SDK 的 app/library 构建”，不是本仓库 producer
自身的构建版本：

| 档位 | Kotlin | AGP | Gradle | compileSdk | JDK |
|---|---:|---:|---:|---:|---:|
| 最低支持 | `2.2.10` | `8.10.1` | `8.11.1` | `36` | `17+` |
| 推荐 | AGP 9 内置 `2.2.10` | `9.1.1` | `9.3.1` | `36.1` | `21` |

Pixel Engine AAR 显式声明 `minAndroidGradlePluginVersion=8.10.0` 和 `minCompileSdk=36`。AGP 低于
`8.10.0` 或 compileSdk 低于 `36` 时，`checkAarMetadata` 会在编译/R8 前给出升级提示；不应使用
`android.suppressUnsupportedCompileSdk` 绕过该边界。AGP 8 消费者必须显式应用 Kotlin
`2.2.10+`，并让 Kotlin/Java JVM target 保持一致；AGP 9 默认使用内置 Kotlin，无需再应用
`org.jetbrains.kotlin.android`。这些边界分别对应 Android 官方的
[Kotlin 2.2 D8/R8 最低版本](https://developer.android.com/build/kotlin-support)、
[AGP 8.10 / Gradle 8.11.1 兼容关系](https://developer.android.com/build/releases/agp-8-10-0-release-notes)、
[AGP 9.1 / Gradle 9.3.1 兼容关系](https://developer.android.com/build/releases/agp-9-1-0-release-notes)
与 [Android 36.1 compileSdk 配置](https://developer.android.com/about/versions/16/qpr2/setup-sdk)。

在 `settings.gradle.kts` 中包含模块：

```kotlin
include(":pixel-engine")
```

在 app 模块中依赖：

```kotlin
dependencies {
    implementation(project(":pixel-engine"))
}
```

公开基础类型包括 `PixelUiState`、`WidgetBuilder`、`RootWidgetProvider`、`StateSetter`、`PixelFocusDirection`、
`ReadingOrderFocusTraversalPolicy`、`FormValidator`、`PixelPagerSnapshot`、
`PixelColorResourceDefinition`、`PixelFontResourceDefinition`、`PixelResourceCacheSnapshot`、
`PixelSpriteFrameDefinition`、`PixelSpriteAtlasDefinition` 与 `PixelSpriteAtlas`。这些类型与 Host、
组件、路由、测试和调试 API 一样，都由唯一的 `pixel-engine` 坐标提供。

如果按 SDK 包消费发布产物：

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("com.purride:pixel-engine:1.0.0")
}
```

`PixelColor`、`PixelBuffer`、字体、组件、路由、Host、`PixelTester` 和诊断 API 均来自同一个
`pixel-engine` 坐标。源码包名用于表达职责，不对应额外 Maven artifact。

可单独复现完整消费者矩阵：

```bash
./tools/pixel-consumer-compatibility-matrix.sh
```

该命令先向 `build/compatibility-repository` 发布统一正式坐标，再校验 AAR、POM、Gradle
module metadata、sources、Dokka Javadoc/KDoc、consumer ProGuard rules 与依赖。随后在最低和
推荐两档独立工程中构建 debug、minified release，运行 Kotlin 自定义 RenderObject SPI 与 Java
调用测试；最后确认低 AGP、低 compileSdk 两种组合按预期提前失败。机读结果位于
`build/reports/compatibility/m8-2/`。

常用 import：

```kotlin
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.*
import com.purride.pixelui.state.*
import com.purride.pixelui.widgets.animated.*
```

手势与 advanced 扩展有少量子包；导航全部位于根包 `com.purride.pixelui`：

```kotlin
import com.purride.pixelui.gesture.*
import com.purride.pixelui.advanced.*
```

### 主流程覆盖矩阵

| 主流程 | 文档位置 |
|---|---|
| SDK 依赖与 import | `1. 快速接入` |
| Android Activity 宿主 | `2. 最小 Activity` |
| Engine 实例、服务注入、宿主配置与平台能力 | `3. 宿主配置` |
| 状态、Controller 和受控组件 | `4. 状态管理` |
| 颜色、字体、主题和字形包 | `5. 颜色、字体和主题` |
| 错误边界、Overlay、Back、Insets、Lifecycle、Saved State、Accessibility | `6. 常见页面模式` |
| 布局、文本、输入、表单、导航、动画和组件速查 | `7. API 速查` |
| bitmap、sprite、resource manifest、cache 和资源打包 | `7. API 速查 / pixelcore 常用类型` |
| 自定义 RenderObject | `8. 自定义 RenderObject` |
| SDK 离屏测试和发布 smoke | `9. 测试` |

## 2. 最小 Activity

```kotlin
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.purride.pixelui.Center
import com.purride.pixelui.PixelHostSetupConfig
import com.purride.pixelui.Text
import com.purride.pixelui.createPixelHostSetup

class HelloPixelActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val setup = createPixelHostSetup(
            context = this,
            config = PixelHostSetupConfig(
                content = {
                    Center(child = Text("HELLO PIXEL"))
                },
            ),
        )
        setContentView(setup.rootView)
    }
}
```

`createPixelHostSetup` 会创建：

- `PixelHostView`
- 默认 `PixelTextInputBridge`
- 包含宿主和隐藏输入框的 `FrameLayout`

### Compose 页面

当前 SDK 不发布 Compose wrapper。Compose 应用可以在自身代码中通过 `AndroidView` 承载
`PixelHostView`，并按普通 View 的规则管理 `PixelHostSetup.dispose()`。这样可避免 Compose 编译器
向引擎公开类型注入额外 ABI 字段。

## 3. 宿主配置

```kotlin
val setup = createPixelHostSetup(
    context = this,
    config = PixelHostSetupConfig(
        profilePolicy = PixelHostProfilePolicy.AdaptivePixels(
            dotSizePx = 8,
            pixelShape = PixelShape.SQUARE,
        ),
        bezelColor = PixelColor.Black,
        textDirection = TextDirection.LTR,
        content = { AppRoot() },
    ),
)

setup.hostView.offPixelColor = PixelColor.fromRgb(8, 37, 13)
setup.hostView.setPixelGapEnabled(true)
setup.hostView.setPixelGapRatio(0.6f)
```

关键配置：

| 配置 | 说明 |
|---|---|
| `profilePolicy` | 逻辑屏幕解析策略：`Fixed` / `AdaptivePixels` / `AdaptiveDp` / `AdaptiveLogicalSize` |
| `bezelColor` | 屏幕外框和画布背景色 |
| `textRasterizer` | 默认文本栅格器 |
| `textDirection` | LTR / RTL |
| `pagerGesturePolicy` | PageView 手势启动策略 |
| `nestedScrollPolicy` | 嵌套滚动仲裁策略 |
| `scrollPhysics` | 列表滚动物理 |
| `frameScheduler` | Host 私有 frame/ticker scope 的上游帧源；优先在创建 setup 时配置 |
| `backDispatcher` | widget back 栈调度器 |
| `onUnhandledBack` | back 未被 widget 消费时的 app fallback |
| `content` | 根 widget provider |

`createPixelHostSetup(context, engine, ...)` 中 Engine 是 Host 服务的唯一来源：setup 会把默认 Android
编辑器提供的 IME、剪贴板、震动能力作为 **fallback** 补进 `engine.services.hostServices`，调用方在
Engine 上显式声明的同名 capability 始终优先。

### PixelEngine 实例与服务注入

`PixelEngine` 是不可变的运行实例。Builder 可注入 clock、frame scheduler、ticker factory、结构化
error reporter、resource resolver/cache、logger、Host 环境、聚焦 Host capability 和主题。默认每次
`build()` 都创建独立 cache；多个 Engine 只有在应用显式传入同一服务对象时才共享状态。

```kotlin
/** 当前页面使用的独立 Engine。 */
val engine = PixelEngine.Builder()
    .theme(PixelThemeTokens.Dark)
    .errorReporter { event ->
        crashSink.record(event.cause, event.phase, event.recoveryResult)
    }
    .hostServices(
        PixelHostCapabilitySet(
            systemActions = PixelSystemActionCapability { action ->
                appSystemActions.dispatch(action)
            },
        ),
    )
    .build()

/** 创建时已经绑定 Engine 的标准 Host。 */
val setup = createPixelHostSetup(
    context = this,
    engine = engine,
    config = PixelHostSetupConfig(content = { AppRoot() }),
)
```

已有 Host 可以调用 `hostView.bindEngine(engine)`。切换实例会让后续帧读取新服务，并释放旧的
Host frame/ticker scope；不要继续持有切换前的 ticker provider。

`engine.theme` 始终返回完整 token。默认 Builder 不会自动安装 `PixelTheme` 作用域，组件此时解析
`PixelThemeTokens.Default`；调用 `.theme(...)` 才会把自定义 token 图注入整棵树。

纯 JVM 测试应注入 `ManualFrameScheduler` 和 fake `PixelClock`，不要读取 Android 默认调度器。完整
默认值、共享规则和迁移步骤见 [Android Host 指南](guides/host-integration.md)。

### Android 宿主契约

pixel-engine 当前只承诺 Android 宿主。默认路径使用 `createPixelHostSetup`；只有自定义宿主或
测试宿主才需要直接操作 `PixelHostView` 并自行组装 `PixelHostCapabilitySet`。

| 层级 | SDK 负责 | App 负责 |
|---|---|---|
| `PixelHostSetup` | 创建 `PixelHostView`、默认 `PixelTextInputBridge` 和根 `FrameLayout` | 把 `rootView` 放进 Activity / Fragment |
| `PixelHostView` | 渲染、输入、insets、accessibility、ViewTree lifecycle、Host 私有 ticker provider 和调试 dump | 在真实终态释放 setup；需要时提供显式 owner 或 lifecycle |
| `PixelTextInputBridge` | 引擎自有隐藏编辑器、IME 映射、selection / composition 同步，并实现 `PixelImeCapability` / `PixelClipboardCapability` / `PixelHapticCapability` | 业务侧决定何时聚焦、提交、保存文本状态 |
| `PixelBackDispatcher` | 维护 widget 内 back 栈，并按栈顶优先派发 | 提供 `onUnhandledBack` 或回落到系统 back |
| saved state helpers | 提供 navigator / list / pager / text field 的 `Bundle` 保存恢复 API | 在 Activity / Fragment 生命周期里显式调用保存恢复 |

自定义宿主按需实现聚焦 capability：

- `PixelImeCapability`
- `PixelClipboardCapability`
- `PixelHapticCapability`
- `PixelBackCapability`
- `PixelAccessibilityCapability`
- `PixelSavedStateCapability`
- `PixelSystemActionCapability`

它们由 `PixelHostCapabilitySet` 组合并通过 `PixelEngine.Builder.hostServices(...)` 注入。缺失能力返回
`Unsupported`，能力异常返回带原始 cause 的 `Failed`，不会用静默空操作伪装成功。系统动作使用
`PixelOpenUriAction`、`PixelNavigateBackAction`、`PixelOpenAppSettingsAction` 和
`PixelRequestPermissionAction`，新代码不应新增字符串协议。

typed capability set 是唯一的宿主能力模型：`PixelHostView` 只从所绑定 Engine 读取
`services.hostServices`，不存在第二个 Host 级注入入口。帧调度不属于 capability，由
`PixelFrameScheduler`（`PixelEngine.Builder.frameScheduler` 或 `PixelHostView.frameScheduler`）单独负责。

边界：

- `PixelHostView` attach 时自动绑定 `ViewTreeLifecycleOwner`；普通 detach 只暂停并保留 retained tree，
  `destroy()` / `dispose()` 才是不可逆释放。
- SDK 不自动保存 Android `savedInstanceState`；业务 controller/state 由 app 显式保存恢复。
- SDK 不封装权限、Intent、通知、文件选择等 Android 业务能力；这类能力通过 app 层或
  `PixelSystemActionCapability` 自行接线。
- SDK 不提供 Material / Cupertino 组件库；宿主契约只保证像素 UI 引擎和 Android 系统能力的连接。

### Host-owned ticker 与帧源

需要 vsync 的 Navigator 和动画应使用当前 Host 的 provider：

```kotlin
/** Builds one Navigator with the ticker provider owned by its Host. */
fun buildNavigator(hostView: PixelHostView): PixelNavigator {
    // Re-read this property after replacing the Host frame scheduler.
    val hostTickerProvider = hostView.tickerProvider
    return PixelNavigator(
        initialRequest = PixelRouteRequest(homeDestination, Unit),
        vsync = hostTickerProvider,
    )
}
```

每个 `PixelHostView` 都拥有独立 `PixelHostFrameScope`。pause、stop 或 detach 时 active time
冻结且不再排上游帧；resume 从暂停前值继续；destroy 取消 callback、ticker 和 listener。

运行时替换 `hostView.frameScheduler` 会 dispose 旧 scope 和旧 `tickerProvider`，不会迁移正在运行的
动画。换源后必须重新读取 `hostView.tickerProvider` 并重建持有旧 provider 的 Navigator/controller。
通常应通过 `PixelHostSetupConfig.frameScheduler` 在创建内容前一次性配置。

## 4. 状态管理

### `ValueNotifier`

适合轻量状态。

```kotlin
private val counter = ValueNotifier(0)

content = {
    Column(
        children = listOf(
            ValueListenableBuilder(counter) { _, value ->
                Text("COUNT $value")
            },
            OutlinedButton(
                text = "+1",
                onPressed = { counter.value += 1 },
            ),
        ),
    )
}
```

不要在 `content` lambda 内创建长期状态对象，否则每次重建都会重置。

### `StatefulWidget`

适合封装可复用状态组件。

```kotlin
class CounterWidget : StatefulWidget() {
    override fun createState(): State<out StatefulWidget> = CounterState()
}

class CounterState : State<CounterWidget>() {
    private var count = 0

    override fun build(context: BuildContext): Widget =
        OutlinedButton(
            text = "COUNT $count",
            onPressed = { setState { count += 1 } },
        )
}
```

### Controller + State

滚动、分页、输入等使用 controller + state：

```kotlin
private val listController = ScrollController()
private val listState = listController.create()

ListViewBuilder(
    itemCount = 100,
    itemBuilder = { Text("ITEM $it") },
    state = listState,
    controller = listController,
    itemExtent = 8,
)
```

## 5. 颜色、字体和主题

engine 使用 ARGB `PixelColor`。`PixelTheme` 向 widget 子树提供完整且不可变的 `PixelThemeTokens`；
它不接管 Android host 配置，`bezelColor`、`offPixelColor` 仍通过 `PixelHostSetupConfig` /
`PixelHostView` 设置。

| 根属性 | 内容 |
|---|---|
| `brightness` / `contrast` | 明暗与标准/高对比度元数据 |
| `colors` | `PixelColorScheme` 的 22 个语义颜色角色 |
| `typography` | body、label、title、caption、button、input 六个排版 token |
| `spacing` / `sizes` | 6 档间距与 10 个标准组件尺寸 |
| `radii` / `borders` / `elevations` | 5 档阶梯圆角、4 档整数边框、4 档硬阴影 |
| `motion` | `PixelMotionThemeData` 的 8 个 motion spec |
| `components` | 25 个标准组件 token 族 |
| `labels` | 29 个可本地化的标准文案 |

```kotlin
/** Complete consumer theme shared by every standard component. */
val theme = PixelThemeTokens.Dark.copy(
    colors = PixelColorScheme.Dark.copy(
        primary = PixelColor.fromRgb(151, 255, 167),
        outline = PixelColor.fromRgb(255, 220, 120),
        focus = PixelColor.fromRgb(0, 224, 255),
    ),
    spacing = PixelSpacingTokens.Default.copy(small = 3),
    labels = PixelLabelTokens.Default.copy(confirm = "确定", cancel = "取消"),
)

setup.hostView.bezelColor = PixelColor.Black
setup.hostView.offPixelColor = PixelColor.fromRgb(8, 37, 13)

PixelTheme(
    tokens = theme,
    child = Column(
        children = listOf(
            Text("HOME"),
            OutlinedButton(text = "OPEN", onPressed = {}),
        ),
    ),
)
```

内置 `Dark`、`Light`、`HighContrastDark`、`HighContrastLight` 四组主题；`Default` 等同于 `Dark`。
组件保留 `PixelColorRole` 并在 build 时解析当前 scheme，所以只复制一组颜色或 foundation token 就能
同步改变全部消费者，不需要逐组件覆写。

`PixelTheme(tokens = ..., child = ...)` 是唯一的主题提供入口，只承载 `PixelThemeTokens`。
全部组件统一使用 `PixelTheme.of(context)` / `PixelTheme.maybeOf(context)` 读取该 token 图；
`of` 在没有 provider 时回退到 `PixelThemeTokens.Default`，`maybeOf` 返回 `null`。局部显式
`PixelMotionTheme` 比 `PixelThemeTokens.motion` 更近时，以前者为准。

### 八状态解析

所有 state-aware 标准组件接受 `PixelControlStateSet`。`Normal` 是空集合；其他状态可以组合。固定
单值优先级为
`Disabled > Loading > Error > Pressed > Focused > Hovered > Selected > Normal`；focus indicator 为
独立叠加层，不会替换 Error/Pressed/Selected 的底色。

优先级按属性逐项解析：`PixelStateMap` 从高到低寻找“当前激活且该属性配置了 override”的第一个
状态，没有匹配才返回 `normal`。因此最高状态没有该属性的 override 时，会继续寻找较低状态，而不是
构造一个不存在的值。focus indicator 只在 Focused 激活且组件的 `focusIndicator` 非空时叠加；其标准
`width = 1` 会解析为当前 `borders.focus`，其他正整数保持字面逻辑像素。

Loading 删除 pointer、keyboard 和 semantics mutation，对本身可聚焦的控件保留已有焦点；Disabled
还会清除焦点资格。运行时自行合入真实 Hovered、Pressed 和 Focused，业务通常只需提供 Selected、
Error、Loading 或 Disabled。回调为 `null` 或 `enabled=false` 也会归一化为 Disabled；Error 只改变
状态呈现，不会自动禁用修复动作。

### 25 个标准组件 token 族

下表是 `PixelComponentTokens` 属性与公开生产工厂的一一映射。每个族都能消费
`PixelControlStateSet`；既有组件通过 `states` 必填的 state-aware 重载与简洁入口并存，而 1.0
新增工厂可直接以默认 `Normal` 调用并按需显式传入 `states`。

| Token 属性 | 标准生产工厂 | 共享该族的相关消费者 |
|---|---|---|
| `button` | `OutlinedButton` | — |
| `textButton` | `TextButton` | — |
| `iconButton` | `IconButton` | — |
| `textField` | `TextField` | `FormFieldDecoration` 复用此族，不另增 token |
| `listTile` | `ListTile` | — |
| `checkbox` | `Checkbox` | — |
| `radio` | `Radio` | `RadioGroup` |
| `switch` | `Switch` | — |
| `slider` | `Slider` | — |
| `tabs` | `Tabs` | — |
| `segmented` | `SegmentedControl` | — |
| `navigationBar` | `NavigationBar` | `PixelNavigationDestination` |
| `navigationRail` | `NavigationRail` | `PixelNavigationDestination` |
| `valueAdjuster` | `ValueAdjuster` | `Stepper`、`ShortcutHint` |
| `menu` | `Menu` | `PixelMenuItem` |
| `dropdown` | `Dropdown` | — |
| `slidable` | `Slidable` | `SlidableAction` |
| `dialog` | `Dialog` | `ConfirmDialog` |
| `bottomSheet` | `BottomSheet` | — |
| `toast` | `Toast` | `ToastQueue`、`Badge` |
| `snackbar` | `Snackbar` | `SnackbarQueue` |
| `tooltip` | `Tooltip` | — |
| `progress` | `ProgressBar` | `PixelLoadingBar`、`AnimatedPixelLoadingBar`、`ActivityIndicator` |
| `refresh` | `RefreshIndicator` | `SwipeRefreshScaffold` |
| `scrollbar` | `Scrollbar` | — |

`PixelComponentColorTokens` 为每个族提供 `containerColor`、`contentColor`、`borderColor` 三个
`PixelStateProperty`，以及独立 `focusIndicator`、padding、最小尺寸、边框、圆角和 elevation role。
标准 spacing、size、radius、border 编码会读取当前 foundation token；非标准的非负整数保留为字面
逻辑像素。

### 显式参数与简洁 API

某个公开覆写通道按“该通道适用的显式参数 > component token > foundation token / color
scheme”解析。它不是“任意显式参数压过所有状态”的全局规则：例如 Checkbox/Switch 的
active/inactive 颜色只定义 Normal/Selected 基础通道，Error、Loading、Disabled 等状态仍按组件
契约覆盖。

`OutlinedButton`、`Checkbox`、`Dialog`、`Toast`、`Snackbar`、`ProgressBar`、`Badge`、`Divider`、
`AppScaffold`、`Scrollbar`、`RefreshIndicator`、`TextField`、`Slider` 等简洁 API 全部保留，并统一
委托到同一套 state-aware token 实现。简洁入口的可选视觉参数一律是 nullable 且默认 `null`：
`null` 表示由 token 解析，非 null 则保持调用方精确值。因此不再需要用“默认具体值”当作省略
sentinel，也不存在“省略默认值”与“显式传入与默认相同的值”无法区分的问题。

简洁入口与 state-aware 重载在 Kotlin 中共用工厂名，state-aware 重载要求传 `states`，JVM 侧以稳定
`@JvmName` 区分，例如 `OutlinedButtonWithControlStates`。两条入口在同一输入下构建完全相同的
widget 树，无论是否存在 `PixelTheme` provider；缺少 provider 时二者都解析
`PixelThemeTokens.Default`。

可选的无障碍名称遵循同一规则：`semanticLabel` 一律是 nullable 且默认 `null`。`null` 表示省略，
按“本地化 provider > 主题 label token > 内置英文”解析；任意显式字符串都是最高优先级，包括与
内置英文兜底完全相同的文本。`NavigationBar` / `NavigationRail` 额外要求集合名称非空白，显式空白
值会在构建时抛出 `IllegalArgumentException`；其余组件保留调用方显式传入的空白值。

### 主题验证

主题变更由 `PixelThemeTokens`、高对比度和组件状态矩阵的 JVM 测试验证。需要产品级可视化时，
在 `app` 中增加真实使用场景，不再维护独立 demo 模块。

```bash
./gradlew :pixel-engine:testDebugUnitTest
./gradlew :app:assembleDebug
```

主题边界：

- 不接管 `PixelHostView` 的 `bezelColor` / `offPixelColor` / screen profile。
- 不提供 Material / Cupertino token，也不追求通用设计系统。
- 只接受整数逻辑像素；标准 surface 使用阶梯圆角、整数边框和无模糊硬阴影。

主题、组件状态与测试方法见[主题与组件指南](guides/theme-and-components.md)。

字体优先级：

1. `TextStyle(textRasterizer = ...)`
2. `DefaultTextRasterizer`
3. `PixelHostView.textRasterizer`
4. `PixelBitmapFont.Default`

字体包加载与 fallback：

- `PixelGlyphPackAssetLoader(context, cache).load(assetDirectory, manifestSha256, binarySha256)` 会从 `assets/<assetDirectory>/manifest.json` 和 `assets/<assetDirectory>/glyphs.bin` 有界加载一个 `PixelGlyphPack`；缓存由调用方提供或使用 loader 的默认有界缓存，不再创建无容量私有 map。
- `PixelGlyphPackParser.parseManifest(json, expectedSha256)` 只解析字形包元数据；`parseBinary(manifest, inputStream, expectedSha256)` 会先限制输入总字节并校验 SHA-256，再校验 magic、version、`cellHeight`、glyph count、Unicode scalar、尺寸、精确压缩长度、重复 code point、截断和尾随数据。
- `BitmapGlyphSource(packs)` 按 pack 顺序查找完整 Unicode scalar `Int`，只消费与当前 `GlyphStyle.cellHeight` 匹配的 pack；supplementary glyph 不会被拆成 surrogate。
- `CompositeGlyphProvider(sources)` 按 source 顺序查找 scalar；全部 source 都缺字时，会返回 engine 内建兜底字形。
- scalar 兜底字形按 ASCII / 非 ASCII 选择 `narrowAdvanceWidth` 或 `wideAdvanceWidth`；普通缺字会绘制可见方框，空白和控制字符只保留 advance、不绘制墨迹。段落层的多 code-point cluster fallback 另见下文。
- SDK 不在字体层自动读取 `PixelResourceCatalog.fonts`；catalog 只提供索引，调用方仍需要用 `PixelGlyphPackAssetLoader` 或自定义加载器创建 `PixelGlyphPack`。

`GlyphSource.findGlyph(Int, ...)` 和 `GlyphProvider.rasterizeGlyph(Int, ...)` 是 1.0 主路径；冻结的
`Char` 方法继续保留给旧源码和旧二进制消费者。只实现旧入口的 source 对 supplementary scalar
返回缺字，只实现旧入口的 provider 会收到一次 U+FFFD 请求，不会收到两个 surrogate。公开 `Int`
入口只接受 Unicode scalar，拒绝负数、U+10FFFF 以上及 surrogate 范围。

多 code-point grapheme 只有在 rasterizer 实现 `PixelClusterTextRasterizer` 且
`canRasterizeCluster(exactCluster)` 返回 `true` 时才按原始 cluster 测量和绘制。内置
`PixelStyledTextRasterizer` 只声明单 scalar cluster；unsupported 多 scalar cluster 在 paragraph
层只生成一个 U+FFFD fallback 单元。

字形包 manifest 当前稳定字段：

```json
{
  "packId": "ui8",
  "displayName": "UI 8px",
  "cellHeight": 16,
  "baseline": 13,
  "defaultAdvance": 8,
  "supportedRanges": ["0020-007E", "4E00-9FFF"]
}
```

`glyphs.bin` 当前稳定格式：

1. magic：`0x50474C59`，即 `PGLY`。
2. version：当前为 `1`。
3. `cellHeight`：必须等于 manifest 的 `cellHeight`。
4. `glyphCount`。
5. 每个 glyph 依次写入 `codePoint`、`advanceWidth`、`width`、`dataLength`、按位压缩的像素数据。

`codePoint` 是完整 Unicode scalar 的 32-bit 整数，不是 UTF-16 `Char`。旧字体实现的兼容注意事项见
[统一迁移指南](guides/migration.md)。

## 6. 常见页面模式

### 错误边界

`PixelErrorBoundary` 可以捕获后代 widget build / render 异常，并用像素后备界面替换失败子树。
没有边界时，异常仍会继续抛给宿主。

```kotlin
PixelErrorBoundary(
    onError = { error -> println(error.message) },
    errorBuilder = { error -> PixelErrorPanel(error.message ?: "UNKNOWN") },
    child = AppRoot(),
)
```

`PixelErrorPanel` 是 `PixelErrorBoundary` 的默认可见 fallback，只用于呈现 SDK 子树异常。
业务态的错误页、空数据页或重试页应使用 `EmptyState` 或业务自定义 widget，不要把
`PixelErrorPanel` 当成通用错误页面组件。

约束：

- `onError` 用于记录日志或上报崩溃系统；不要在里面直接修改 widget tree。
- `errorBuilder` 应保持简单、确定、低依赖，避免后备界面再次触发异常。
- 如果 `errorBuilder` 自身在 render 阶段失败，fallback 异常会继续抛给宿主，原异常会作为 suppressed error 保留。

实例级可观测性通过 `PixelEngine.Builder.errorReporter(...)` 注入。`PixelErrorEvent` 保留原始
`cause`、build/render/fallback 阶段、widget/element/render 上下文、恢复结果和 Engine 单调时间。
Reporter 或 logger 自身抛异常时会被隔离，不会掩盖原始引擎异常。局部 `PixelErrorBoundary.onError`
负责子树恢复，实例 reporter 负责统一记录，两者可以同时使用。

### Overlay

`PixelOverlayHost` 在页面顶部承载统一的 `PixelPopupRoute<R>`。controller 由业务持有；每个 route
都有永久 identity、明确 layer、Back/遮罩 dismiss policy、可选 barrier、modal 隔离、motion 和
typed outcome。`PixelOverlayEntry<R>` 的状态依次为 `Active -> Removing -> Disposed`：逻辑关闭会
立即退出 Back、焦点和交互栈，但 outcome 只在所有已挂载 Host 的 keyed presentation 真正卸载后
交付；多个关闭结果始终按逻辑关闭顺序 FIFO 交付，不会被不同退出动画时长重排。
如果 outcome 回调抛出异常，retained tree 仍会完成 State detach、兄弟卸载、焦点/监听/dirty queue
清理，再把异常交回本次 build/render 调用；回调因此不得把“抛异常”当成关闭或重试协议。

```kotlin
val overlay = PixelOverlayController()

PixelOverlayHost(
    controller = overlay,
    child = AppRoot(),
)

val picker = overlay.show(
    PixelPopupRoute<String>(
        content = Dialog(content = Text("CHOOSE"), modal = false),
        layer = PixelOverlayLayer.Modal,
        dismissPolicy = PixelOverlayDismissPolicy.Dismissible,
        barrier = PixelOverlayBarrier(),
        modal = true,
        motion = PixelOverlayMotion.Dialog,
        onOutcome = { outcome -> saveOutcome(outcome) },
    ),
)
picker.complete("A")

// 兼容便捷入口仍可用；通知层默认 Passive，不会抢占 Back。
overlay.showToast("SAVED")
overlay.showSnackbar(
    message = "QUEUED",
    action = TextButton(text = "UNDO", onPressed = {}),
)
```

layer 从低到高为 `Popup`、`Notification`、`Modal`、`System`，同层按插入顺序绘制。
`PixelOverlayDismissAction` 对每个来源选择 `Ignore`（继续扫描下层）、`Consume`（拦截但不关闭）
或 `Dismiss`（关闭当前 entry）。不可关闭 modal 应使用 `PixelOverlayDismissPolicy.Locked`，防止 Back
穿透到 Navigator；Toast/Snackbar 使用 `Passive`。`showDialog`、`showToast` 和 `showSnackbar` 是
统一 route API 的兼容适配器；便捷通知不会自动超时，需要自动 FIFO 时使用下文的队列组件。
系统 Back 与焦点系统的 Escape/gamepad Back 共用同一条从顶到底的扫描链；全部为 `Ignore` 时，
统一 route 不会因为仍有 modal focus trap 就吞掉按键。独立使用且没有 dismiss callback 的
Dialog/BottomSheet/Popover/Menu 会注册平台 Back 并保持不可关闭模态层的按键陷阱；放入统一
route 或已合并的 Popover/Menu presentation 后复用外层 handler，不会重复关闭。
带 barrier 的自定义 route 应用 `PixelOverlaySurface` 包住真实表面；它只在该 measured bounds 内
吸收空白/disabled 区点击，真实子控件 target 仍位于其上，表面外点击才会到达 barrier。标准
Dialog、BottomSheet 和 Popover 已自动应用该边界。

### Back

默认 `createPixelHostSetup` 会创建 `PixelBackDispatcher` 并给 `content` 包一层
`PixelBackHost`。宿主收到 Android back 时调用 `hostView.handleBackPressed()`。
处理顺序固定为：文本输入 blur -> 顶层 eligible overlay policy -> navigator pop -> app fallback。
`Ignore` 的通知不会截获返回键；`Consume` 的锁定模态层会阻止返回键继续下传。

```kotlin
lateinit var setup: PixelHostSetup

setup = createPixelHostSetup(
    context = this,
    config = PixelHostSetupConfig(
        onUnhandledBack = {
            finish()
            true
        },
        content = {
            PixelOverlayHost(
                controller = overlay,
                child = PixelNavigator(rootRoute, tickerProvider),
            )
        },
    ),
)

onBackPressedDispatcher.addCallback(
    this,
    object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (!setup.hostView.handleBackPressed()) {
                isEnabled = false
                try {
                    onBackPressedDispatcher.onBackPressed()
                } finally {
                    isEnabled = true
                }
            }
        }
    },
)
```

子树需要拦截 back 时使用 `PixelBackHandler`。返回 `true` 表示已经消费，
返回 `false` 会继续交给下一个 handler 或 app fallback。

```kotlin
PixelBackHandler(
    enabled = panelOpen,
    onBack = {
        panelOpen = false
        true
    },
    child = settingsPanel,
)
```

### Window Insets

`PixelHostView` 会把 Android system bars 和 IME inset 转换成 pixel-engine 逻辑像素，
并通过 `MediaQuery` 注入 widget 树。

| API | 含义 |
|---|---|
| `MediaQuery.of(context).viewPadding` | 系统栏等稳定安全区 |
| `MediaQuery.of(context).viewInsets` | IME 等临时遮挡 |
| `MediaQuery.of(context).padding` | `viewPadding` 扣除 `viewInsets` 后的安全区 |
| `SafeArea` | 把 `padding` 转成普通布局 padding |
| `ImeAvoidingView` / `KeyboardAvoidingView` | 把 `viewInsets` 转成普通布局 padding |
| `PixelHostView.setWindowInsets` | 测试或自定义宿主手动注入稳定安全区 |
| `PixelHostView.setViewInsets` | 测试或自定义宿主手动注入临时遮挡 |

普通 Activity 不需要手动设置 inset；系统回调会自动更新。自定义宿主或测试才使用
`setWindowInsets` / `setViewInsets`。

```kotlin
val setup = createPixelHostSetup(
    context = this,
    config = PixelHostSetupConfig(
        content = {
            SafeArea(
                minimum = PixelWindowInsets(bottom = 1),
                child = Column(
                    children = listOf(
                        Text("TITLE"),
                        Expanded(child = AppRoot()),
                    ),
                ),
            )
        },
    ),
)
setContentView(setup.rootView)
```

输入页通常把系统栏交给 `SafeArea`，把键盘遮挡交给 `KeyboardAvoidingView`。二者都只做
padding，不负责显示/隐藏软键盘，也不自动滚动聚焦输入框。

```kotlin
SafeArea(
    child = KeyboardAvoidingView(
        child = Column(
            children = listOf(
                Expanded(child = messages),
                TextField(state = inputState, controller = inputController),
            ),
        ),
    ),
)
```

Fragment 接入时同样把 `setup.rootView` 作为返回 view；`PixelHostView` 会继续接收
Android window inset 回调。

```kotlin
override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
): View {
    setup = createPixelHostSetup(
        context = requireContext(),
        config = PixelHostSetupConfig(
            content = {
                SafeArea(child = AppRoot())
            },
        ),
    )
    return setup.rootView
}
```

### Viewport、Host capability 与自适应布局

物理 View 到逻辑点阵的映射由三个互不替代的维度组成：

- `PixelViewportPolicy`：Contain/Cover、Integer/Fractional 和九宫格 alignment；
- `PixelHostProfilePolicy`：固定 profile、按物理 px、按 dp 或固定逻辑尺寸；
- `PixelAdaptiveLayoutData`：当前物理/dp/逻辑尺寸、inset、density、orientation、size class 和
  display features 的同帧快照。

`PixelHostView.viewportPolicy` 非空，默认值 `PixelViewportPolicy()` 即 canonical 默认策略
（Contain + Integer + Center）；paint、touch 和 inset 投影共用同一份策略解析出的几何。
`PixelHostView.screenProfile` 只读，始终由 `profilePolicy` 与当前视口、密度、`viewportPolicy` 解析得到；
需要固定网格时赋 `PixelHostProfilePolicy.Fixed(profile)`。

```kotlin
/** Configures a density-aware logical grid and an exact fractional contain viewport. */
fun configureResponsivePixels(hostView: PixelHostView) {
    hostView.profilePolicy = PixelHostProfilePolicy.AdaptiveDp(dotSizeDp = 3f)
    hostView.viewportPolicy = PixelViewportPolicy(
        fit = PixelViewportFit.CONTAIN,
        quantization = PixelViewportQuantization.FRACTIONAL,
        alignment = PixelViewportAlignment.CENTER,
    )
}
```

普通 Android Host 会自动采集 locale、direction、fontScale、density、contrast、motion、refresh 和
cutout。`capabilitiesOverride` 是测试/调试用的完整快照覆盖；`layoutDirectionOverride = null` 表示
恢复 Android 自动方向。所有 Host capability source 操作都属于主线程。

`AdaptiveBuilder` 的 `builder` 不是构造器最后一个参数，因此使用显式命名参数：

```kotlin
AdaptiveBuilder(
    builder = { _, window ->
        when (window.widthSizeClass) {
            PixelWindowSizeClass.COMPACT -> compactContent
            PixelWindowSizeClass.MEDIUM,
            PixelWindowSizeClass.EXPANDED,
            -> expandedContent
        }
    },
    key = "application-adaptive-layout",
)
```

环境变化只重建依赖者。保持 type/key/slot 稳定时，State、焦点、滚动位置、TextField selection 和
Navigation 栈不会因为 resize、IME、density 或 fold 更新而丢失。`Text`、`RichText` 和
`TextField` 自动消费 `textScaleFactor`；RTL Row 反转视觉顺序但保持声明/semantics 顺序。主题可用
`PixelThemeTokens.forHost(context, brightness)` 自动选择高对比度 preset。

完整 Host 与 viewport 规则见 [Android Host 指南](guides/host-integration.md)。

#### 自适应与只读 golden

自适应、RTL、文本缩放、高对比度、reduce motion、density、逻辑 hinge 和状态保持由 JVM golden、
Host instrumentation 与 `app` 集成测试共同覆盖。

对应 JVM 基线为
`src/test/resources/element-snapshots/m5-3-adaptive-localization.txt` 和
`src/test/resources/golden/m5-3-adaptive-localization.txt`。普通测试只会把候选差异写入
`build/reports/golden/`，不会创建或修改源码基线，也没有 `REGEN` 自动接受入口。

### Lifecycle

`PixelHostView` 把 Window attachment 与 owner lifecycle 分开保存。普通
`onDetachedFromWindow` 只会令 Host 非 interactive：暂停动态帧、输入、手势和 back，但继续保留
retained/render 状态。重新 attach 后，如果 owner 仍是 resumed 或没有 owner，原状态会继续使用。

attach 时会自动发现 `ViewTreeLifecycleOwner`。标准 `ComponentActivity` / Fragment view tree
通常不需要手工转发 lifecycle；需要覆盖自动 owner 时使用 `bindLifecycleOwner(owner)`，解除后用
`unbindLifecycleOwner()` 恢复 ViewTree owner 或 unmanaged 兼容模式。

Fragment 每次 view 重建都应创建新 setup，并在明确的视图终态调用 `PixelHostSetup.dispose()`：

```kotlin
/** Releases the setup owned by the current Fragment view generation. */
override fun onDestroyView() {
    setup.dispose()
    // The next Fragment view must create a new setup and Host.
    _binding = null
    super.onDestroyView()
}
```

没有 `LifecycleOwner` 的自定义宿主使用 `start()`、`resume()`、`pause()`、`stop()` 和
`destroy()` 显式驱动。`onHostResume()` / `onHostPause()` 仍兼容并分别委托给 resume/pause。

`destroy()` 是不可逆终态，会释放 observer、retained/render tree、frame scope、ticker、输入会话
和缓存。旧 `PixelHostView.dispose()` 等价于 `destroy()`；`PixelHostSetup.dispose()` 还会先隐藏
默认输入桥。两者都不负责从父 ViewGroup 移除 Android view 层级。

可通过 `hostView.lifecycleDiagnostics` 检查 attachment、owner 状态、owner 来源、
`isInteractive` 和各阶段计数；通过 `hostView.tickerProvider.diagnostics()` 检查 active/live ticker、
pending frame、active time 及 pause/dispose 状态。完整规则见
[Android Host 指南](guides/host-integration.md)。

### Saved State

pixel-engine 不自动接管 Android `savedInstanceState`。业务侧持有 controller/state，并在
Activity / Fragment 的生命周期里显式保存和恢复。

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    savedInstanceState?.getPixelPagerSavedState("home.pager")?.let {
        pagerController.restoreState(pagerState, it, pageCount = 3)
    }
    savedInstanceState?.getPixelTextFieldSavedState("home.search")?.let {
        searchController.restoreState(searchState, it)
    }
    savedInstanceState?.getPixelListSavedState("home.list")?.let {
        listController.restoreState(listState, it)
    }
}

override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    pagerController.saveState(pagerState).saveToBundle(outState, "home.pager")
    searchController.saveState(searchState).saveToBundle(outState, "home.search")
    listController.saveState(listState).saveToBundle(outState, "home.list")
}
```

Navigator 只提供版本化 typed 恢复：用 `PixelRouteSnapshotAdapter` 注册 destination，再通过
`savePersistentSnapshotToBundle / restorePersistentSnapshotFromBundle` 保存并恢复 typed
destination、参数、entry ID 和允许落盘的 bucket 值。result callback 不跨进程恢复。路由内滚动
位置可以由 adapter 明确纳入 typed route state；`PixelRouteScrollRestoration` 只在 entry 留在
当前 Navigator stack 内时生效，不跨进程。

### Accessibility

`PixelHostView` 会把最近一帧的 semantics tree 暴露成 Android virtual accessibility nodes。
业务侧使用 `Text`、`TextField`、按钮、选择控件或显式 `Semantics(...)` 时，不需要额外接
Android `AccessibilityNodeProvider`。

每个 retained semantics render node 拥有一个稳定 `PixelSemanticsNode.id`；Android Host 再把它
映射为本 Host 内单调递增且不复用的 virtual node ID。带 `key` 的列表项在前插、删除和重排后
仍保持原 ID，TalkBack 焦点不会因为展示下标变化而指向另一个项目。`label` 相同不代表身份
相同；动态集合必须为逻辑项提供业务稳定的 widget/`Semantics` key。

`PixelSemanticsNode` 可以表达：

- `parentId` 与真实父子层级；
- `label`、`value`、`hint`、`error`；
- `role`、`enabled`、`focused`、`selected`、`checked`、`expanded`；
- 文本 selection、`PixelSemanticsRangeInfo`、live region；
- collection 与 collection-item 的行列、span、heading 和选择信息；
- click、long click、前后滚动、set text、set selection、set progress、dismiss、expand、
  collapse 和带稳定 ID/本地化 label 的 custom action。

显式节点使用 `PixelSemanticsActions` 直接持有动作回调。Android bridge 和 `PixelTester` 都按
稳定 node ID 调用这份回调，不会再从节点中心坐标重新 hit-test：

```kotlin
Semantics(
    label = "Volume",
    value = "${(volume * 100).toInt()}%",
    role = PixelSemanticRole.SLIDER,
    rangeInfo = PixelSemanticsRangeInfo(current = volume),
    actions = PixelSemanticsActions(
        onSetProgress = { next ->
            volume = next.coerceIn(0f, 1f)
            true
        },
    ),
    mergeDescendants = true,
    key = "settings-volume",
    child = Slider(value = volume),
)
```

`mergeDescendants = true` 把装饰文字和后代动作折叠进当前控件节点，适合 Button、Checkbox、
Switch、Tab 等单一朗读单元；相同文案只保留一次。`excludeDescendants = true` 保留当前节点、
隐藏所有后代语义，适合完全由父节点描述的自绘内容。两者不能同时开启。默认值均为 `false`，
因此自定义复合控件仍会导出真实子树。

Android bridge 为每个虚拟节点维护独立的 accessibility focus，并支持 touch exploration、
`findFocus`、真实 parent/child、role/class、range、collection、selection 和 action 映射。节点或
状态变化会按稳定 ID 发送内容、焦点、选择、文本、滚动和窗口事件；一个节点被移除后，旧 ID
不会落到同位置的新节点。

JVM 测试可以直接读取结构化快照并执行同一份动作：

```kotlin
val volumeNode = tester.semanticsNodesByLabel("Volume").single()
check(volumeNode.parentId != null)
check(
    tester.performSemanticsAction(
        volumeNode.id,
        PixelSemanticsAction.SET_PROGRESS,
        PixelSemanticsActionArguments(progress = 0.75f),
    ),
)
```

测试与兼容要求见[测试指南](guides/testing.md)。键盘/DPAD 的 input focus
继续与 Android accessibility focus 分离；Dialog、Menu 和模态 Popover 会在各自 runtime 内
建立闭环焦点域，逻辑关闭时立即恢复打开前的 input focus。

### 基础面板

```kotlin
Container(
    width = 80,
    height = 32,
    padding = EdgeInsets.all(2),
    borderColor = PixelColor.White,
    child = Column(
        spacing = 2,
        children = listOf(
            Text("STATUS"),
            Text("READY", color = PixelColor.fromRgb(120, 255, 160)),
        ),
    ),
)
```

### 长列表

```kotlin
ListViewBuilder(
    itemCount = apps.size,
    itemBuilder = { index -> Text(apps[index].label) },
    state = listState,
    controller = listController,
    itemExtent = 8,
)
```

长列表必须提供 `itemExtent` 或 `estimatedItemExtent`，否则会走 eager 构建路径。

### 分页

```kotlin
val pager = PageController()
val pagerState = pager.create(pageCount = 3)

PageView(
    axis = Axis.HORIZONTAL,
    controller = pager,
    state = pagerState,
    pages = listOf(HomePage(), DrawerPage(), SettingsPage()),
)
```

### 文本和富文本

`Text` 用于普通像素文本，默认单行裁剪；需要多行时显式开启 `softWrap` 并设置
`maxLines`。`color` 是 `style.color` 的快捷覆盖。

```kotlin
Text(
    data = "SYSTEM READY",
    color = PixelColor.fromRgb(120, 255, 160),
    overflow = TextOverflow.ELLIPSIS,
)
```

`RichText` 只负责 span 样式切换和统一段落布局；它不会引入 Material/Cupertino 的富文本手势、
链接识别或文本编辑行为。

```kotlin
RichText(
    spans = listOf(
        PixelTextSpan("HP ", style = TextStyle(color = PixelColor.White)),
        PixelTextSpan("42", style = TextStyle(color = PixelColor.fromRgb(255, 220, 120))),
    ),
    maxLines = 1,
    overflow = TextOverflow.ELLIPSIS,
)
```

### 按钮

`OutlinedButton` 是像素风描边按钮，适合主命令；`TextButton` 是无边框文字按钮，适合对话框、
toast/snackbar action 和轻量命令。`onPressed = null` 或 `enabled = false` 时按钮不会导出可点击目标。

```kotlin
OutlinedButton(
    text = "SAVE",
    onPressed = { save() },
    borderColor = PixelColor.fromRgb(255, 220, 120),
)

TextButton(
    text = "CANCEL",
    onPressed = { close() },
)
```

`TextButton` 默认零 padding，尺寸由文字自然决定；需要更大的触摸区域时通过
`TextButtonStyle(padding = EdgeInsets.all(2))` 显式扩大。

### 列表项、选择控件和图标按钮

`ListTile` 是像素列表行容器，负责 leading/title/subtitle/trailing 排列；只有提供 `onTap`
且 `enabled = true` 时才是可点击语义。`Checkbox` / `Switch` 是受控组件，当前状态由调用方传入，
点击后通过 `onChanged` 返回下一状态。

```kotlin
ListTile(
    leading = Checkbox(
        checked = app.enabled,
        onChanged = { enabled -> updateApp(app.id, enabled) },
        semanticLabel = "${app.name} enabled",
    ),
    title = Text(app.name),
    subtitle = Text(app.packageName, color = PixelColor.fromRgb(160, 160, 160)),
    trailing = Switch(
        checked = app.pinned,
        onChanged = { pinned -> pinApp(app.id, pinned) },
        semanticLabel = "${app.name} pinned",
    ),
    onTap = { openApp(app.id) },
    semanticLabel = "Open ${app.name}",
)
```

`Checkbox` / `Switch` 的 `onChanged = null` 或 `enabled = false` 表示只读展示；这时不会导出
可点击目标，颜色也会降级为 disabled 状态。

`Radio` 是一个受控单选指示器；`RadioGroup` 用 `PixelRadioOption<T>.id` 作为选择、回调和 retained
identity。非空 group 要求 option id 唯一且 `selectedId` 恰好命中一项；只有空 group 才能把
`selectedId` 设为 `null`。可交互且至少有一项 enabled 时，整组只有一个 Tab stop，方向键循环跳过
disabled option，Enter/Space 重新请求当前项；所有动作只调用 `onSelected`，组件不会自行改写选择。

```kotlin
/** Stable business options; their ids do not depend on current visual order. */
val deliveryOptions = listOf(
    PixelRadioOption(id = "standard", label = "STANDARD"),
    PixelRadioOption(id = "express", label = "EXPRESS"),
)

RadioGroup(
    options = deliveryOptions,
    selectedId = selectedDeliveryId,
    onSelected = { id -> selectedDeliveryId = id },
    semanticLabel = "DELIVERY SPEED",
    key = "delivery-speed",
)
```

`IconButton` 接受 `PixelIconData` 并用当前 `iconButton` token 为位图 alpha mask 着色。它不从图形
猜测含义，因此 `semanticLabel` 必须是非空文本；图标后代会合并为一个按钮语义节点。`selected`
仍由调用方控制，`onPressed = null` 或 `enabled = false` 会归一化为 Disabled。

```kotlin
IconButton(
    icon = saveIcon,
    onPressed = { save() },
    semanticLabel = "SAVE DOCUMENT",
    selected = isSaved,
    key = "save-document",
)
```

### 滑块、标签页和分段选择

`Slider` 是受控滑块，`value` 由调用方保存并限制在 `0.0f..1.0f` 的业务含义内；渲染和手势会把
实际输出钳位到 `0.0f..1.0f`。`onDrag` 在拖动中实时返回新比例，`onRelease` 在释放时返回最终比例。

```kotlin
Slider(
    value = volume,
    onDrag = { next -> volume = next },
    onRelease = { next -> saveVolume(next) },
)
```

`Tabs` 和 `SegmentedControl` 都是受控的水平 `SINGLE` collection：`selectedIndex` 由调用方保存，
`onSelected` 只回传请求选择的当前下标。`labels` 同时是可见内容和 retained item identity，因此必须
非空、互不重复，并在同一逻辑项重排时保持稳定。非空集合要求 `selectedIndex` 指向恰好一项；空集合
只接受 `selectedIndex = -1`，并导出 0×0 collection。`Tabs` 适合页面/区域切换；
`SegmentedControl` 适合同一位置内的紧凑模式切换。

```kotlin
Tabs(
    labels = listOf("APPS", "WIDGETS"),
    selectedIndex = page,
    onSelected = { index -> page = index },
)

SegmentedControl(
    labels = listOf("ALL", "PINNED"),
    selectedIndex = filter,
    onSelected = { index -> filter = index },
)
```

两者在非空且可交互时整组各占一个 Tab stop，Left/Right 循环选择；重排后语义位置会更新，但相同
label 的 retained identity、焦点和状态不会被旧下标重定向。若业务允许重复或频繁变化的显示文案，
应先在业务层提供稳定且唯一的 label，当前 API 没有独立的 item id 参数。

`SelectionList` 用于受控单选列表，`OptionList` 是字符串选项的轻量包装。二者不内置滚动；
长列表应放入现有滚动容器。

```kotlin
OptionList(
    options = listOf("LOW", "MID", "HIGH"),
    selectedIndex = level,
    onSelected = { index -> level = index },
)
```

`SectionList` 用于把任意 widget 分成多个纵向区段。它只做布局，不提供吸顶 header、折叠、
懒加载或滚动；这些策略应由外层滚动容器或业务状态管理。

```kotlin
SectionList(
    sections = listOf(
        SectionListSection(
            header = Text("SYSTEM"),
            children = listOf(
                ListTile(title = Text("AUDIO"), onTap = {}),
                ListTile(title = Text("DISPLAY"), onTap = {}),
            ),
        ),
    ),
)
```

`ValueAdjuster` 提供通用的减 / 值 / 加三段式布局，不保存数值、不判断范围；边界由调用方通过
传入或置空两侧回调控制。它是一个 Tab stop，Left/Down 调用减值、Right/Up 调用加值；对应边界
回调为 null 时仍消费该方向键，避免焦点意外离开控件。`Stepper` 是整数范围包装，会按 `step`
调整并把结果钳位到 `range`。

```kotlin
Stepper(
    value = size,
    range = 0..10,
    step = 2,
    onChanged = { next -> size = next },
)
```

### 反馈组件

`Dialog`、`ConfirmDialog` 和 `BottomSheet` 会把 stable `viewPadding` 与临时 `viewInsets` 按边
取最大值，限制并裁切到 SafeArea/IME 之外；paint、pointer、滚动/输入 target 和 semantics 共用
同一个安全矩形。Dialog 居中，BottomSheet 填满安全宽度并贴安全底边。它们默认建立模态焦点域并
隔离背景 interaction/semantics；统一显示队列、barrier、motion 和 Android Back 由
`PixelOverlayHost` / `PixelOverlayController` 管理。`Toast` 和 `Snackbar` 保持非模态普通 widget。
空间不足时，标题/正文进入弹性裁切区，footer actions 先获得布局空间；被裁掉的正文像素、点击、
滚动/输入目标与 semantics 会同步移除，关闭按钮不会只剩屏幕外目标。

```kotlin
Dialog(
    title = Text("DELETE"),
    content = Text("ARE YOU SURE"),
    actions = listOf(
        TextButton(text = "CANCEL", onPressed = { close() }),
        OutlinedButton(text = "OK", onPressed = { confirm() }),
    ),
)

ConfirmDialog(
    title = "DELETE APP",
    message = "THIS CANNOT BE UNDONE",
    onCancel = { close() },
    onConfirm = { deleteApp() },
    confirmText = "DELETE",
)

BottomSheet(
    content = Text("OPTIONS"),
    actions = listOf(TextButton(text = "DONE", onPressed = { close() })),
)

Toast("SAVED")

Snackbar(
    message = "QUEUED",
    action = TextButton(text = "UNDO", onPressed = { undo() }),
)
```

`Dialog` 和 `BottomSheet` 的 `actions` 会排列在内容下方并右对齐；`ConfirmDialog` 是受控组合组件，
取消/确认后的关闭策略由回调显式处理；`Toast` 支持多行文本；`Snackbar` 的 `action`
由调用方传入任意 widget，常用 `TextButton`。

`ModalBarrier` 是填满父级 `Stack` 的遮罩，可选点击关闭；它不单独持有 focus scope、back、
overlay 生命周期或动画。模态焦点所有权属于包含它的 Dialog/Popover presentation。
`ToastQueue` 和 `SnackbarQueue` 分别渲染 controller 的 FIFO 队首。每条消息从成为队首起使用
Host active-time ticker 计时：Host pause 期间不流逝，手动 dismiss、action、clear 或 widget
dispose 会同步取消/转移 ticker；reduce motion 和 animator scale 不会把可读停留时间缩成零。
没有 `PixelMotionScope` 时不会偷偷创建时钟，队列保持手动控制。

```kotlin
val toasts = PixelToastQueueController()
val snackbars = PixelSnackbarQueueController()

PixelOverlayHost(
    controller = overlay,
    child = Stack(
        children = listOf(
            AppRoot(),
            ModalBarrier(dismissible = true, onDismiss = { closeDialog() }),
            ToastQueue(controller = toasts),
            Positioned(
                left = 0,
                right = 0,
                bottom = 0,
                child = SnackbarQueue(controller = snackbars),
            ),
        ),
    ),
)

toasts.enqueue("SAVED", timeout = 2.seconds)
snackbars.enqueue(
    message = "DELETED",
    actionLabel = "UNDO",
    onAction = { undo() },
    timeout = 4.seconds,
)
```

`Popover`、`Menu`、`Dropdown` 和 `Tooltip` 都是受控组件。展开状态、选中值、关闭时机和定位 offset
由调用方维护。Popover portal 从 anchor 的真实全局 paint bounds 定位，独立于父级 layout size，并
把 presentation 提升到 Host 根层，因此可以逃离 List/ClipRect 的局部裁切。内容先在
`max(viewPadding, viewInsets) + viewportMargin` 的安全视口内测量，再按 `placement` 自动上下翻转、
按 `alignment`（含 RTL Start/End）对齐并做水平/垂直 collision shift；滚动、IME、旋转或 resize
都会在下一帧用同一 paint/hit/semantics 变换重定位。`Popover`、`Menu` 和 `Dropdown` 默认是
模态层：初始焦点进入弹层，Tab/方向遍历不会逃到背景，关闭时恢复 opener，并且背景不再导出
交互目标或 semantics。`Tooltip` 固定为非模态，不抢焦点也不隔离背景。

自定义非模态 Popover 可显式传 `modal = false`。默认 Menu 直接作为 Popover content 时会自动
复用外层 modal owner；标准 `Dropdown` 也只持有一个 owner。只有业务自建了框架无法识别的
modal presentation 时，才为内层 Menu 传 `modal = false`。Dialog 内再打开的 Menu 默认是独立
顶层 modal，关闭后恢复 Dialog 内 opener。

根 portal 仍服从 route 总序：较低 Popup route 内的 Popover 不会覆盖较高 Modal/System route；
较高的非模态 route 也会在较低 modal 之上保留像素、点击、semantics 和焦点资格，同时应用背景
仍被隔离；该 route 关闭后焦点恢复到低层 modal。Opacity、ClipRect、Translate 或缩放 scratch
中的较高 sibling 会保留原 alpha、裁切和全局变换，paint/target/semantics/raw hit 顺序保持一致。

```kotlin
Dropdown(
    label = "MODE",
    selectedText = mode,
    expanded = menuOpen,
    onToggle = { menuOpen = !menuOpen },
    items = listOf(
        PixelMenuItem("A", onSelected = { mode = "A"; menuOpen = false }),
        PixelMenuItem("B", onSelected = { mode = "B"; menuOpen = false }),
    ),
)

Popover(
    anchor = anchor,
    content = popup,
    expanded = open,
    placement = PixelPopoverPlacement.Auto,
    alignment = PixelPopoverAlignment.End,
    viewportMargin = 2,
)
```

`EmptyState` 是居中的空状态组合组件，只负责标题、说明、可选图标和 action 的像素布局；
空数据判断、加载状态和重试动作由调用方维护。

```kotlin
EmptyState(
    title = "NO APPS",
    message = "PIN OR INSTALL APPS",
    action = TextButton(text = "RETRY", onPressed = { reload() }),
)
```

`LoadStateView` 复用 `PixelAsyncSnapshot`，统一 loading、empty、error 和 content 的像素展示；
它不发起请求，也不订阅数据源。

```kotlin
LoadStateView(
    snapshot = snapshot,
    isEmpty = { apps -> apps.isEmpty() },
    content = { apps -> AppList(apps) },
)
```

### 进度和加载指示

`ProgressBar` 是固定尺寸的水平进度条，`progress` 会在绘制时钳位到 `0.0f..1.0f`。调用方负责
维护真实任务进度；组件只根据当前传入值绘制。

```kotlin
ProgressBar(
    progress = downloadProgress,
    width = 48,
    height = 5,
)
```

`ActivityIndicator` 不会自己启动 ticker，也不会持有动画状态；它只根据 `frame` 高亮四个点中的一个。
调用方可用 `AnimationController`、业务定时器或测试里的 `pumpFrame` 推进 frame。

```kotlin
ActivityIndicator(
    frame = loadingFrame,
    color = PixelColor.White,
)
```

### 滑动行和页面骨架

`Slidable` 是像素行容器：向右滑打开 `startActionPane`，向左滑打开 `endActionPane`。它只负责滑动
偏移、action pane 命中和 dismiss 回调，不会删除数据；业务应在 `onDismissed` 中更新列表状态。

```kotlin
Slidable(
    child = ListTile(title = Text("MAIL")),
    endActionPane = SlidableActionPane(
        children = listOf(
            SlidableAction(
                label = "DELETE",
                backgroundColor = PixelColor.fromRgb(180, 60, 60),
                foregroundColor = PixelColor.White,
                onPressed = { deleteMail() },
            ),
        ),
        motion = SlidableMotion.BEHIND,
        dismissible = true,
        dismissThreshold = 0.4f,
    ),
    onDismissed = { direction -> removeFromList(direction) },
)
```

`SlidableActionPane.children` 会均分面板宽度；`extentRatio` 会限制在 `0.1f..1.0f` 后换算宽度。
`dismissible = true` 且滑动距离达到 `dismissThreshold` 时才触发 `onDismissed`。
键盘路径把 Slidable 行作为一个 Tab stop：Enter/Space 触发 `onTap`，Left 打开 end pane，Right
打开 start pane；打开后 Tab 进入 `SlidableAction`，Action 用 Enter/Space 激活。关闭 pane 会立即
隐藏其 semantics、阻止旧 Action 再请求焦点，并把焦点恢复到行；视觉 settle 可以继续完成。

无障碍服务使用同一套 pane 状态机：关闭时导出 `EXPAND`（同时存在两侧时优先打开 end pane），
打开时导出 `COLLAPSE`；只有当前 pane 的 `dismissible = true` 且提供 `onDismissed` 时才导出
`DISMISS`。语义 dismiss 到达终点后调用一次 `onDismissed`，不会替业务删除数据。Loading 会保留
行节点和已有焦点，但撤销 expand/collapse/dismiss 等所有变更动作；Disabled 还会移除遍历资格。

`AppScaffold` 是一个轻量页面骨架：`title` 是顶部描边区域，`body` 占据剩余空间，`bottomBar`
固定在底部。它不提供导航、系统 inset、overlay 或 Material 风格 app bar；这些由宿主或更高层组件组合。

```kotlin
AppScaffold(
    title = Text("SETTINGS"),
    body = SettingsList(),
    bottomBar = Text("READY"),
)
```

### 输入框

```kotlin
val input = TextEditingController()
val inputState = input.create(initialText = "")

TextField(
    state = inputState,
    controller = input,
    placeholder = "SEARCH",
    inputType = PixelInputType.TEXT,
    textInputAction = TextInputAction.SEARCH,
    onChanged = { query -> search(query) },
)
```

`textInputAction` 的公开类型是 `TextInputAction`，它是 `PixelTextInputAction` 的别名。
widget 层用它表达键盘动作，自定义宿主在 `PixelTextInputRequest.action` 中接收同一组值。

### 表单

```kotlin
/** Form owner coordinating validation and submission. */
val form = FormController()
/** Generic business value and validation state for the name field. */
val nameField = FormFieldState("")
/** Editable text controller kept separate from generic form validation state. */
val nameInput = TextEditingController()
/** Retained text, selection, composition, and IME state. */
val nameInputState = nameInput.create(initialText = "")

Form(
    controller = form,
    child = Column(
        children = listOf(
            FormField(
                state = nameField,
                fieldId = "name",
                validator = { value -> if (value.isBlank()) "REQUIRED" else null },
                builder = { _, field ->
                    TextField(
                        state = nameInputState,
                        controller = nameInput,
                        decoration = FormFieldDecoration(
                            label = "NAME",
                            helper = "PUBLIC PROFILE NAME",
                            error = field.errorText,
                            required = true,
                            // The application owns its counting and display policy.
                            counter = formatNameCounter(field.value),
                        ),
                        placeholder = "ENTER NAME",
                        onChanged = { field.setValue(it) },
                    )
                },
            ),
            OutlinedButton(text = "SAVE", onPressed = { form.validate() }),
        ),
    ),
)
```

`FormFieldDecoration` 只描述展示和语义，不执行校验，也不统计字符。非空 `error` 会替换 `helper`，
但 `counter` 继续保留；`required` 只给可见与朗读 label 添加 `*` 标记，不会自动生成 validator。
`counter` 是调用方已经格式化好的字符串，SDK 不代替业务选择 UTF-16、Unicode code point 或
grapheme cluster 计数规则。装饰文本会合入唯一的 TextField 语义节点，error 也会映射为 Android
content-invalid 状态；不要再把错误塞进 `placeholder`。更新 decoration 不会替换输入 identity、焦点、
selection、composition 或 IME target。

## 7. API 速查

### Host API

| API | 用途 | 关键参数 |
|---|---|---|
| `PixelEngine.Builder` | 创建不可变、可注入的 Engine 实例 | clock、frame/ticker、error/logger、resource、Host capability、theme |
| `PixelEngineServices` | 当前实例的只读服务集合 | cache 默认按 build 隔离 |
| `createPixelHostSetup` | 创建默认 Android 宿主 | `context`、可选 `engine`、`hostView`、`config` |
| `PixelHostSetup` | 宿主装配结果 | `rootView`、`hostView`、`textInputBridge` |
| `PixelHostSetupConfig` | 宿主配置 | 背景、字体、手势策略、滚动物理、根内容 |
| `PixelHostProfilePolicy` | 逻辑屏幕解析策略 | `Fixed` / `AdaptivePixels` / `AdaptiveDp` / `AdaptiveLogicalSize` |
| `PixelHostProfilePolicy` | 固定或自适应逻辑 profile | `Fixed`、`AdaptivePixels`、`AdaptiveDp`、`AdaptiveLogicalSize` |
| `PixelViewportPolicy` | 物理到逻辑的正交映射 | fit、quantization、alignment |
| `PixelHostView` | Android 像素宿主 | `setContent`、lifecycle、`tickerProvider`、insets、debug stats |
| `HostCapabilitiesData` | immutable Host 环境 | locale、direction、textScale、contrast、density、motion、refresh、features |
| `PixelAdaptiveLayoutData` / `AdaptiveBuilder` | 自适应布局快照与依赖 builder | px/dp/logical size、class、orientation、insets、features |
| `PixelHostLifecycleDiagnostics` | Host 生命周期快照 | attachment、owner、interactive、transition 计数 |
| `PixelHostFrameScope` | 自定义宿主的 frame/ticker 所有权边界 | pause、resume、dispose、diagnostics |
| `PixelFrameScheduler` | 上游一次性帧源 | `scheduleFrame` |
| `PixelCancellableFrameScheduler` | 可移除 pending callback 的 additive capability | `scheduleCancellableFrame` |
| `PixelHostCapabilitySet` / `PixelHostServices` | 组合并在 widget 树读取聚焦平台能力 | Unsupported / Failed / success 明确分支 |
| `PixelTypedSystemAction` | 封闭的类型安全系统动作 | URI、back、app settings、permission |
| `PixelErrorEvent` / `PixelErrorReporter` | 实例级结构化错误通道 | cause、phase、context、recovery、timestamp |

### 基础类型

| API | 用途 |
|---|---|
| `Widget` | 所有 UI 组件根接口 |
| `BuildContext` | build 期间访问环境和依赖 |
| `StatefulWidget` / `State` | 有状态组件 |
| `StatelessWidget` | 无状态组件 |
| `InheritedWidget` / `InheritedNotifier` | 环境传播和依赖通知 |
| `Builder` / `StatefulBuilder` | 局部构建和局部状态 |
| `ListenableBuilder` / `ValueListenableBuilder` | 监听状态并重建 |
| `AsyncBuilder` | 订阅 `PixelAsyncSource` |

### 主题与组件状态

| API | 用途 | 关键成员 / 行为 |
|---|---|---|
| `PixelTheme` | 向子树提供唯一 token 图 | `tokens`、`of`、`maybeOf` |
| `PixelThemeTokens` | 完整不可变主题根节点 | presets、`forCapabilities`、`forHost`、`Default` |
| `PixelThemeBrightness` / `PixelThemeContrast` | 主题明暗与对比度元数据 | `Dark` / `Light`；`Standard` / `High` |
| `PixelColorRole` / `PixelColorScheme` | 22 个语义角色及其 ARGB 解析 | `resolve(role)`、`copy(...)` |
| `PixelTypographyToken` / `PixelTypographyTokens` | 单个及六类标准排版 token | `resolve(colors)`、body、label、title、caption、button、input |
| `PixelSpacingTokens` / `PixelSizeTokens` | 共享间距和标准组件尺寸 | 标准组件编码在 build 时解析 |
| `PixelRadiusTokens` / `PixelBorderTokens` | 阶梯圆角与整数边框 | 非标准非负整数保持字面值 |
| `PixelElevationRole` / `PixelElevationTokens` | 无模糊硬阴影的语义等级 | `resolve(role)` |
| `PixelMotionThemeData` / `PixelMotionTheme` | 全局或局部 Motion 策略 | 最近的 `PixelMotionTheme` 覆盖主题根节点 motion |
| `PixelComponentTokens` | 25 个标准组件族 | `button` 至 `scrollbar`，完整映射见第 5 节 |
| `PixelComponentColorTokens` | 单族状态颜色和像素几何 | 三个 state property、focus、padding、size、border、radius、elevation |
| `PixelFocusIndicatorTokens` | Focused 时独立叠加的边框 | `colorRole`、`width`、`inset` |
| `PixelLabelTokens` | 29 个可本地化默认文案 | 对标准 label 使用 `copy(...)` 覆写 |
| `PixelControlState` | 八种标准状态枚举 | Normal、Hovered、Pressed、Focused、Selected、Disabled、Error、Loading |
| `PixelControlStateSet` | 可组合、不可变状态集合 | `Normal` / `Empty`、`of(...)`、`highestPriority()` |
| `PixelStateProperty<T>` | 状态属性解析接口 | `resolve(states)`、`constant(value)` |
| `PixelStateMap<T>` | Normal fallback 加显式 override | 按固定优先级寻找第一个已配置且激活的 override |

带简洁入口的 state-aware 工厂在 Kotlin 中与简洁入口同名，并用必填 `states` 区分；JVM 调用方
使用对应 `...WithControlStates` 名称。只提供单一入口的工厂可以让 `states` 默认取 `Normal`。
简洁入口的可选视觉参数一律 nullable 且默认 `null`，表示交由 token 解析。

### 布局组件

| 组件 | 用途 | 关键参数 |
|---|---|---|
| `Row` | 横向线性布局 | `children`、`spacing`、`mainAxisAlignment`、`crossAxisAlignment` |
| `Column` | 纵向线性布局 | 同 `Row` |
| `Wrap` | 自动换行布局 | `children`、`spacing`、`runSpacing` |
| `Stack` | 层叠布局 | `children`、`alignment` |
| `Positioned` | Stack 内定位 | `left`、`top`、`right`、`bottom`、`width`、`height` |
| `PositionedDirectional` | 方向感知定位 | `start`、`end`、`top`、`bottom` |
| `PositionedFill` | 填满 Stack | `left`、`top`、`right`、`bottom` |
| `Padding` | 内边距 | `padding`、`all`、`horizontal`、`vertical` |
| `PaddingDirectional` | 方向感知内边距 | `EdgeInsetsDirectional` |
| `Align` | 对齐 child | `alignment` |
| `AlignDirectional` | 方向感知对齐 | `AlignmentDirectional` |
| `Center` | 居中 child | `child` |
| `SizedBox` | 固定尺寸/占位 | `width`、`height`、`child` |
| `Visibility` | 显示 child 或 replacement | `visible`、`child`、`replacement` |
| `Expanded` | flex 紧约束填充 | `child`、`flex` |
| `Flexible` | flex 松/紧约束 | `child`、`flex`、`fit` |
| `Spacer` | flex 空白 | `flex` |
| `AspectRatio` | 固定宽高比 | `aspectRatio`、`child` |
| `ConstrainedBox` | 施加盒约束 | `PixelBoxConstraints`、`child` |
| `FittedBox` | contain 缩放并居中 | `child` |
| `SafeArea` | 避让宿主 insets | `child`、`minimum` |
| `ImeAvoidingView` | 避让 IME viewInsets | `child`、`bottom`、`minimum` |
| `KeyboardAvoidingView` | `ImeAvoidingView` 别名 | `child`、`bottom`、`minimum` |

### 内容与装饰

| 组件 | 用途 | 关键参数 |
|---|---|---|
| `Text` | 单行/多行文本 | `data`、`style`、`color`、`softWrap`、`maxLines`、`overflow`、`textAlign` |
| `RichText` | 多 span 富文本 | `spans`、`softWrap`、`maxLines`、`overflow` |
| `Container` | 尺寸、边距、内距、填充、边框 | `width`、`height`、`padding`、`margin`、`fillColor`、`borderColor` |
| `ContainerDirectional` | 方向感知容器 | `paddingDirectional`、`marginDirectional`、`alignment` |
| `DecoratedBox` | 装饰盒 | `fillColor`、`borderColor`、`padding`、`alignment` |
| `Opacity` | 透明度效果 | `opacity`、`child` |
| `ClipRect` | 矩形裁剪 | `child` |
| `Transform.translate` | 平移 | `offset`、`child` |
| `Image` | 绘制 `PixelBitmap` | `bitmap` |
| `Sprite` | 绘制 sprite sheet 单帧 | `sheet`、`frameIndex` |
| `Icon` | 绘制 `PixelIconData` | `icon` |

`Opacity` 会把输入规范化到 `0f..1f`，非有限值按 `0f` 处理。透明度恰为 `0f` 时，child
仍参与布局并保留 retained State，但不会绘制、参与命中/交互 target 或暴露 semantics；任意
正透明度都会保留命中和 semantics，只缩放绘制像素的 alpha。`AnimatedOpacity` 使用相同契约，
中途替换 target 时从当前已经绘制的视觉值继续，不会跳回上一段动画起点。

### 图形原语

| 组件 | 用途 | 关键参数 |
|---|---|---|
| `Line` | 直线 | 坐标、颜色、尺寸 |
| `Circle` | 圆/圆环 | 半径、颜色、填充 |
| `Polygon` | 多边形 | `points`、颜色、填充 |
| `Path` | 路径 | `PixelPath`、颜色 |
| `CustomPaint` | 自定义绘制闭包 | `width`、`height`、`painter` |

### 选择、反馈和组合组件

| 组件 | 用途 | 关键参数 |
|---|---|---|
| `TextButton` | 无边框文字按钮，默认零 padding | `text`、`onPressed`、`style`、`enabled` |
| `OutlinedButton` | 描边按钮 | `text`、`onPressed`、`style`、`enabled` |
| `IconButton` | 带必填无障碍名称的图标按钮 | `icon`、`onPressed`、`semanticLabel`、`selected`、`states` |
| `ListTile` | 列表行 | `title`、`subtitle`、`leading`、`trailing`、`onTap` |
| `SelectionList` | 受控单选列表 | `items`、`selectedIndex`、`onSelected`、`itemLabel` |
| `OptionList` | 字符串选项列表 | `options`、`selectedIndex`、`onSelected` |
| `SectionList` | 分组列表布局 | `sections`、`itemSpacing`、`sectionSpacing` |
| `SectionListSection` | 分组列表区段数据 | `children`、`header`、`footer` |
| `ValueAdjuster` | 减 / 值 / 加调节器 | `valueText`、`onDecrease`、`onIncrease` |
| `Stepper` | 整数范围步进器 | `value`、`range`、`step`、`onChanged` |
| `Checkbox` | 复选框 | `checked`、`onChanged`、`enabled` |
| `Radio` | 受控单个单选指示器 | `selected`、`onSelected`、`semanticLabel`、`states` |
| `PixelRadioOption<T>` | RadioGroup 的稳定业务项 | `id`、`label`、`enabled` |
| `RadioGroup` | 稳定 id 的受控单选集合 | `options`、`selectedId`、`onSelected`、`semanticLabel`、`states` |
| `Switch` | 开关 | `checked`、`onChanged`、`enabled` |
| `Slider` | 水平滑块 | `value`、`onDrag`、`onRelease`、`enabled` |
| `Tabs` | 标签页按钮组 | `labels`、`selectedIndex`、`onSelected`、`enabled` |
| `SegmentedControl` | 分段选择 | `labels`、`selectedIndex`、`onSelected`、`enabled` |
| `PixelNavigationDestination` | 稳定 id 的应用导航目的地 | `id`、`label`、`icon`、`selectedIcon`、`enabled` |
| `NavigationBar` | 水平应用导航；可受控或绑定多栈 controller | `destinations`、`selectedId` / `controller`、`popToRootOnReselect` |
| `NavigationRail` | 垂直应用导航；可受控或绑定多栈 controller | `destinations`、`selectedId` / `controller`、`popToRootOnReselect` |
| `ShortcutHint` | 快捷键提示文本 | `shortcut`、`label` |
| `Slidable` | 可滑出操作面板的行容器 | `child`、`startActionPane`、`endActionPane`、`onDismissed`、`semanticLabel` |
| `Dialog` | 居中对话框 | `title`、`content`、`actions` |
| `BottomSheet` | SafeArea/IME 内贴底表面 | `title`、`content`、`actions`、`modal` |
| `ConfirmDialog` | 确认对话框组合组件 | `title`、`message`、`onConfirm`、`onCancel` |
| `Toast` | 中央短提示 | `message` |
| `ToastQueue` | 队首 toast 渲染器 | `controller` |
| `PixelToastQueueController` | toast 队列状态 | `enqueue`、`dismissCurrent`、`dismiss` |
| `PixelToastQueueItem` | toast 队列条目 | `id`、`message`、`fillColor`、`textStyle` |
| `Snackbar` | 底部/容器内提示条 | `message`、`action` |
| `SnackbarQueue` | 队首 snackbar 渲染器 | `controller` |
| `PixelSnackbarQueueController` | snackbar FIFO/timeout/action 状态 | `enqueue`、`performAction`、`dismiss`、`clear` |
| `PixelSnackbarQueueItem` | snackbar 队列条目 | `id`、`message`、`actionLabel`、`fillColor`、`textStyle` |
| `ModalBarrier` | Stack 内模态遮罩 | `color`、`dismissible`、`onDismiss` |
| `PixelOverlaySurface` | 阻止表面内空白点击穿透 barrier | `child`、`key` |
| `Popover` | 根级 anchor 弹出层 | `anchor`、`content`、`expanded`、`placement`、`alignment`、`viewportMargin` |
| `PixelPopoverPlacement` | Popover 垂直侧策略 | `Auto`、`Below`、`Above` |
| `PixelPopoverAlignment` | 支持 RTL 的 anchor 横向对齐 | `Start`、`Center`、`End` |
| `Menu` | 纵向菜单 | `items`、`enabled` |
| `PixelMenuItem` | 菜单条目 | `label`、`onSelected`、`enabled`、`shortcut` |
| `Dropdown` | 受控下拉菜单 | `label`、`selectedText`、`expanded`、`items` |
| `Tooltip` | 受控提示浮层 | `message`、`visible`、`child` |
| `EmptyState` | 居中空状态 | `title`、`message`、`icon`、`action` |
| `LoadStateView` | loading/empty/error/content 组合视图 | `snapshot`、`content`、`isEmpty` |
| `PixelErrorPanel` | 错误边界 fallback | `message`、`color` |
| `ProgressBar` | 进度条 | `progress`、`width`、`height` |
| `ActivityIndicator` | 简单加载动画 | `frame` |
| `Badge` | 角标 | `child`、`label` |
| `Divider` | 分隔线 | `color`、`thickness` |
| `Gap` | 空白间隔 | `width`、`height` |
| `AppScaffold` | 简单页面脚手架 | `title`、`body`、`bottomBar` |

### 滚动、网格和分页

`SwipeRefreshScaffold` 是 `RefreshIndicator` 的页面级组合；调用方仍然显式持有
`PixelRefreshIndicatorState` 和 `PixelRefreshIndicatorController`。它只负责可选上下栏和刷新区域布局，
不会自动完成刷新或创建隐藏状态。除下拉手势外，RefreshIndicator 也是一个可命名的 Tab stop；
Enter/Space 与 Switch Access click 会进入同一个 refreshing 状态并调用 `onRefresh`，刷新完成后仍由
业务调用 `controller.completeRefresh(state)`。

```kotlin
SwipeRefreshScaffold(
    state = refreshState,
    controller = refreshController,
    onRefresh = { reload() },
    topBar = Text("FEED"),
    body = ListViewBuilder(
        itemCount = rows.size,
        itemBuilder = { index -> Text(rows[index]) },
        state = listState,
        controller = listController,
    ),
)
```

| 组件 | 用途 | 关键参数 |
|---|---|---|
| `ListView` | eager 列表 | `items`、`state`、`controller`、`spacing` |
| `ListViewBuilder` | builder 列表 | `itemCount`、`itemBuilder`、`itemExtent`、`estimatedItemExtent` |
| `ListViewSeparated` | eager 分隔列表 | `itemBuilder`、`separatorBuilder` |
| `ListViewSeparatedBuilder` | lazy 分隔列表 | item/separator 固定或估算高度 |
| `GridView` | eager 网格 | `items`、`cellWidth`、`cellHeight` |
| `GridViewBuilder` | lazy 网格 | `itemCount`、`itemBuilder`、`cellWidth`、`cellHeight` |
| `SingleChildScrollView` | 单子节点滚动 | `child`、`state`、`controller` |
| `PageView` | 页面列表 | `axis`、`controller`、`state`、`pages` |
| `PageViewBuilder` | builder 分页 | `itemCount`、`itemBuilder` |
| `Scrollbar` | 滚动条包装 | `child`、`state`、`thumbColor` |
| `RefreshIndicator` | 下拉/键盘刷新包装 | `child`、`state`、`controller`、`onRefresh`、`semanticLabel` |
| `SwipeRefreshScaffold` | 带上下栏的下拉刷新骨架 | `body`、`state`、`controller`、`onRefresh`、`semanticLabel` |
| `CustomScrollView` | sliver 滚动容器 | `slivers`、`state`、`controller` |
| `SliverList` | eager sliver list | `items` |
| `SliverListBuilder` | lazy sliver list | `itemCount`、`itemExtent` 或 `estimatedItemExtent` |
| `SliverPinnedHeader` | 固定头部 | `child` |
| `SliverAppBar` | 可折叠头部 | `expandedHeight`、`collapsedHeight`、`floating`、`snap`、`stretch` |

### 输入、表单和焦点

| API | 用途 | 关键参数 |
|---|---|---|
| `TextField` | 文本输入 | `state`、`controller`、`decoration`、`placeholder`、`enabled`、`readOnly`、`minLines`、`maxLines` |
| `TextFieldStyle` | 输入框样式 | 边框、光标、选区、composition、文本样式 |
| `FormFieldDecoration` | TextField 的 label、helper/error、required 与调用方 counter | `label`、`helper`、`error`、`required`、`counter` |
| `Form` | 表单容器 | `controller`、`child` |
| `FormField` | 表单字段 | `controller`、`state`、`fieldId`、`validator`、`builder` |
| `FormController` | 表单状态 | `validate`、`validateAsync`、`submit`、`reset` |
| `FormFieldState` | 单字段值和错误 | `value`、`errorText` |
| `FocusNode` | 单点焦点 | `requestFocus`、`unfocus` |
| `FocusScope` / `FocusScopeNode` | 焦点域 | 方向遍历、IME next |
| `FocusTraversalGroup` | 局部焦点遍历策略域 | `child`、`traversalPolicy`、`node` |
| `PixelFocusManager` | 旧版 detached/single-tree 兼容入口 | 新 Host 代码改用实例级分发 |

`FocusTraversalGroup` 适合给一块控件设置局部遍历策略；默认内部持有 `FocusScopeNode`，
需要跨页面保存焦点状态时再把 node 提升到业务层。`ShortcutHint` 只展示快捷键提示，
实际按键处理仍放在 `Focus(onKeyEvent = ...)` 或宿主级分发里。

每个 `PixelHostView` 和 `PixelTester` 现在各自拥有独立 focus owner。Android 自定义桥接应调用
`PixelHostView.dispatchPixelKeyEvent(event)`，测试应调用 `PixelTester.pressKey(...)`；
`PixelFocusManager` 只保留给尚未挂载到 Runtime 的旧版单树代码，不能用它选择“当前 Host”。

标准交互组件自动创建独立 FocusNode；一个祖先 `Focus` 包住多个控件时，每个控件仍是独立
Tab stop，disabled 子项不会阻断兄弟控件。应用快捷键会沿当前节点向祖先冒泡，并在任何组件
默认 Enter/Space/方向动作之前执行。分发顺序如下：

| 优先级 | 处理者 |
|---|---|
| 1 | 最上层 modal 的 Escape/Back；不可关闭 modal 也会消费事件 |
| 2 | 当前 Overlay Host 的 canonical route 扫描；不要求 route 已取得焦点 |
| 3 | 当前 FocusNode 到祖先 FocusNode 的 `onKeyEvent` |
| 4 | 当前标准组件的默认激活或调值动作 |
| 5 | Tab/Shift+Tab 和 reading/grid 方向遍历 |

模态层优先使用后代 `autofocus`，否则选择 reading order 中第一个 enabled 控件作为初始焦点。
逻辑关闭会立即恢复打开前的节点；该节点已卸载或 disabled 时，回退到原 scope
内第一个有效节点，否则清除焦点。退出动画期间只保留绘制，不继续占有 input focus、IME、
pointer target 或 semantics。嵌套 Dialog/Menu 按 LIFO 恢复，且不会影响另一个 Host/runtime。
低层 modal 仍负责背景隔离，但 canonical 顺序更高的非模态 route 可以直接聚焦或 autofocus；其
Tab traversal 不会形成新的 trap，逻辑关闭后会恢复低层 modal 的 opener/首个可用节点。

### 输入法与 TextField 宿主契约

默认 Android `PixelTextInputBridge` 使用引擎自有隐藏编辑器接入系统 IME，并实现
`PixelImeCapability`。普通 Activity 通过 `createPixelHostSetup` 接入时不需要手动处理输入法；
自定义宿主才需要自行实现 `PixelImeCapability.showTextInput` / `updateTextInput` / `hideTextInput`。

隐藏 editor 固定为 engine-owned 实现，在平台 InputConnection 外增加 grapheme guard，因此不接受
外部替换成普通 `EditText`——那类编辑器无法保证 selection-only/composition-only 回写，也无法保证
任意子类的 InputConnection 命令经过 grapheme 规范化。

| 契约 | 行为 |
|---|---|
| `PixelInputType` | 映射到 Android 输入面板类型，包括文本、ASCII、数字、邮箱、电话、URL 和密码 |
| `TextInputAction` / `PixelTextInputAction` | 映射到 IME action，包括 `DONE`、`NEXT`、`GO`、`SEARCH`、`SEND` |
| `PixelHostView.updateFocusedTextInput` | 宿主把文本、selection 和 composition 同步回当前聚焦的 `TextField` |
| `PixelHostView.submitFocusedTextInput` | 宿主触发提交；所有 action 都会触发 `onSubmitted`，`NEXT` 会额外发起焦点遍历 |
| `readOnly` | 宿主回传文本会被忽略；`COPY` / `SELECT_ALL` 这类只读编辑动作仍可使用 |
| `PixelTextEditingSession` | 唯一的编辑会话契约：`id`（会话身份）+ `request`（编辑器配置）+ `value`（完整 text/selection/composition） |

`PixelTextInputBridge` 只在会话身份或 editor config 改变时重启 IME；普通文本、selection 和
composition 更新会走 `updateTextInput`，避免输入过程中反复重建键盘。`PixelTextEditingSession.id`
在同一 TextField 的连续更新中保持引用相等，切换到另一个字段时一定不同，宿主据此作废上一代
InputConnection，防止跨字段串写。

TextField 聚焦时，字符、物理键盘方向键与 Enter 由隐藏 editor 优先处理；Tab、Escape，以及
DPAD/游戏手柄来源的方向、确认和取消键转发给当前 Host 的 focus owner。IME `NEXT` 使用同一
Host 的 Tab 路由，因此两个同时存在的 Host 不会交换文本、快捷键或 IME 目标。

### Unicode 与 grapheme 编辑边界

selection、composition 和 Accessibility 继续使用 Android 兼容的 UTF-16 offset；光标移动、
删除、选择和用户可见编辑必须落在 Unicode 17.0.0 extended grapheme boundary。SDK 不执行 NFC
或其他 Unicode normalization。

`PixelGraphemeBoundaryMap` 提供 `previous`、`next`、`floor`、`ceil`、`nearest`、`expand` 和
`isBoundary`。非空范围向外扩展；折叠 caret 取最近边界，等距时固定选择更大的 UTF-16 offset：

```kotlin
/** Expands one Android UTF-16 range to complete visible graphemes. */
fun normalizeVisibleSelection(text: String, start: Int, end: Int): PixelUtf16Range {
    /** Fixed Unicode 17 authority built from the exact, non-normalized text. */
    val boundaries = PixelGraphemeBoundaryMap(text)
    return boundaries.expand(start, end)
}
```

`e\u0301`、supplementary emoji、肤色序列、ZWJ family、variation selector、keycap、RI flag 和
CRLF 都只能整体编辑。已有非配对 surrogate 会作为隔离恢复单元原样保留；新的平台 commit 不允许
制造孤立 surrogate，code-point surrounding delete 遇到 malformed traversal 时 no-op。

补充平面和多 code-point 输入使用 `PixelTextInputEvent(text)`、`Focus(onTextInput = ...)`、
`PixelHostView.dispatchPixelTextInput` 或 `PixelTester.pressText`。旧 `PixelKeyEvent.character` 只为
一个非 surrogate BMP `Char` 保留，不能承载 emoji surrogate pair。

详细兼容边界和旧实现调整见[统一迁移指南](guides/migration.md)。字体、段落和 Bidi 已使用
同一 cluster 边界，但编辑安全、Bidi 排序和真实 glyph/shaping 覆盖仍是三个独立能力。

### Code-point 字体、cluster 段落与 Bidi 几何

`Text`、`RichText` 和 `TextField` 的 paragraph 以 Unicode 17 extended grapheme cluster 作为
wrap、ellipsis、letter spacing、caret、hit test、selection/composition rect 和 handle 的最小
单位。LF、CR、CRLF、NEL、LS、PS、连续空行和首尾空行保留精确 source range。RichText span
边界位于一个 cluster 内部时，完整 cluster 使用其首个 code unit 对应的 style。

视觉顺序由引擎固定 Unicode 17.0.0、UAX #9 revision 51 解析；不调用设备 ICU、Android
`java.text.Bidi` 或桌面 JDK。纯 RTL、mixed Bidi 数字/标点和 paired punctuation mirroring 只改变
视觉 cluster 与 paint payload，backing text 和 UTF-16 offset 保持不变。同一逻辑边界有两个
Bidi caret 时保留 upstream/downstream affinity，selection start/end 和 handle 使用相应视觉边。

unsupported 非 ignorable 多 scalar cluster 只产生一个 U+FFFD 单元。纯 default-ignorable、ZWJ、
variation selector 或 Bidi control cluster 留在 backing text 与 Bidi 输入中，但宽度为零且不绘制
tofu。消费者只有实现 `PixelClusterTextRasterizer` 并真实原子绘制 exact cluster 时，才可声明
该 cluster 受支持。

Android API 26+ 通过 `AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY` 请求字符
位置时，普通 Text 与 TextField 都返回同一 paragraph 几何转换后的屏幕坐标 `RectF[]`。数组按
UTF-16 code unit 索引：一个 grapheme 中的组合 code unit、supplementary surrogate pair 和 CRLF
共享 rect；被 ellipsis 截掉或不可见的 source 位置返回 `null`。API 24–25 保留 node bounds 和
既有 Accessibility action 兼容路径。

1.0 不承诺内置 Arabic/Indic contextual shaping、彩色 emoji、全部 ZWJ ligature 资产或脚本专属
hyphenation。旧字体和文本实现的迁移步骤见[统一迁移指南](guides/migration.md)。

### 剪贴板与文本编辑动作

默认 Android `PixelTextInputBridge` 实现 `PixelClipboardCapability`，把读写接到系统
`ClipboardManager`。页面或宿主可以通过
`PixelHostView.performFocusedTextEditAction` 对当前聚焦的 `TextField` 执行标准动作。

| Action | 行为 |
|---|---|
| `COPY` | 复制当前选区，不修改文本 |
| `CUT` | 复制当前选区并删除；`readOnly` 字段不会执行 |
| `PASTE` | 从宿主剪贴板粘贴；空剪贴板不会执行 |
| `SELECT_ALL` | 选中当前字段全部文本 |

```kotlin
val handled = setup.hostView.performFocusedTextEditAction(PixelTextEditAction.PASTE)
```

自定义宿主只需要实现 `PixelClipboardCapability`；不支持剪贴板时不装配该 capability 即可，
调用方会收到明确的 `Unsupported` 结果。

### Haptic

默认 Android `PixelTextInputBridge` 实现 `PixelHapticCapability`，把 `PixelHapticType.TAP` /
`PixelHapticType.LONG_PRESS` 映射到系统 haptic feedback。widget 树需要主动震动时，
通过 build context 调用 `PixelHapticFeedback.perform`。

```kotlin
Builder { context ->
    GestureDetector(
        onTap = {
            PixelHapticFeedback.perform(context, PixelHapticType.TAP)
            submit()
        },
        child = Text("SAVE"),
    )
}
```

`perform` 返回 `false` 表示当前宿主没有装配 `PixelHapticCapability`，或该 capability 执行失败。
内置长按手势会自动触发
`LONG_PRESS`，普通点击不自动触发，避免替业务决定交互强度。

### Controller

| Controller | State | 用途 |
|---|---|---|
| `ScrollController` / `PixelListController` | `PixelListState` | List/Grid/ScrollView/CustomScrollView 滚动 |
| `PageController` / `PixelPagerController` | `PixelPagerState` | PageView 翻页 |
| `TextEditingController` / `PixelTextFieldController` | `PixelTextFieldState` | TextField 文本、光标、选区 |
| `PixelRefreshIndicatorController` | `PixelRefreshIndicatorState` | RefreshIndicator 状态 |
| `PixelAnimationController` | controller 自身 | 动画进度 |
| `PixelMultiStackNavigatorController` | controller 自身 | active stack、独立子栈选择、重选回根与 Back fallback |

常用扩展：

```kotlin
pageController.jumpToPage(pageState, 2)
pageController.nextPage(pageState)
scrollController.showItem(listState, 20)
scrollController.jumpToStart(listState)
scrollController.jumpToEnd(listState)
```

### 导航

| API | 用途 |
|---|---|
| `PixelNavigator` | route stack widget |
| `PixelNavigatorState` | entry stack；push / pop / complete / cancel / replace / remove / clear |
| `PixelRouteDestination<A, R>` / `pixelRouteDestination` | 可复用的 typed destination 定义 |
| `PixelRouteRequest<A, R>` | 一次带类型参数的入栈请求 |
| `PixelRouteEntry<A, R>` / `PixelRouteEntryScope<A, R>` | 独立 stack entry 及其 typed 操作 |
| `PixelRouteOutcome<R>` | 显式区分 `Success(value)` 与 `Cancelled(reason)` |
| `PixelRouteSuccess<R>` / `PixelRouteCancelled` | 根包可直接构造并匹配的 outcome 分支别名 |
| `PixelRouteStateKey<T>` / `PixelRouteStateBucket` | entry 私有的内存状态 |
| `PixelRouteEntryInspection` / `PixelRouteTransitionInspection` | entry 与转场的无参数诊断快照 |
| `PixelRouteTransition` | None/Fade/SlideHorizontal/SlideVertical |
| `PixelRouteTransitionBuilder` | 自定义 transition |
| `PixelNavigationObserver` / `PixelNavigationFailure` | 有序事件与结构化失败诊断 |
| `PixelNavigatorInspectionSnapshot` | 只读、仅内存的 entry stack 检查快照 |
| `PixelNavigator` | 以 `PixelRouteRequest` 作为可恢复 root entry 的导航宿主 |
| `PixelNavigatorSnapshotCodec` / `PixelRouteSnapshotRegistry` | 版本化 entry bytes 与 destination allowlist |
| `PixelRouteSnapshotAdapter<A, R>` / `PixelRoutePayloadCodec<A>` | 参数、迁移和允许恢复的局部状态协议 |
| `PixelMultiStackNavigator` / `PixelTypedNavigatorStack` | 始终挂载且 back 隔离的多返回栈 |
| `PixelMultiStackNavigatorController` | 多栈 active id、子 Navigator 与 Back 策略所有者 |
| `NavigationBar` / `NavigationRail` | 稳定 destination id 的受控或 controller-bound 应用导航 |
| `PixelNavigationDestination` | 导航 id、label、普通/选中 icon 与单项 enabled 状态 |
| `PixelNestedNavigator` | 最近 Navigator 语义和 parent inactive back 隔离 |
| `PixelTypedDeepLinkResolver` / `PixelTypedDeepLinkRoute` | matcher 与 typed 参数 decoder |
| `PixelPredictiveBackEvent` / `PixelPredictiveBackHandler` | start/progress/cancel/commit 返回手势 |
| `PixelDeepLink` | deep link 解析 |
| `PixelRouteScrollRestoration` | route 内滚动位置恢复 |
| `PixelListSavedState` | 列表/网格滚动位置保存 |
| `PixelPagerSavedState` | PageView 当前页保存 |
| `PixelTextFieldSavedState` | TextField 文本和选区保存 |

#### Typed RouteEntry 快速开始

`PixelRouteDestination<A, R>` 是可复用定义，`PixelRouteEntry<A, R>` 才是一次真实的栈位置。
即使连续 push 同一个 destination，每次也会获得独立的 entry ID、参数、状态桶、结果通道和
生命周期。

下面示例定义一个接收 `EditorArgs`、返回 `String?` 的编辑页。成功返回 `null` 仍然是
`Success(null)`；只有 `cancel()` 或移除类操作才产生 `Cancelled`。

```kotlin
import com.purride.pixelui.Column
import com.purride.pixelui.Text
import com.purride.pixelui.TextButton
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.PixelNavigator
import com.purride.pixelui.PixelRouteCancellationReason
import com.purride.pixelui.PixelRouteDestination
import com.purride.pixelui.PixelRouteEntry
import com.purride.pixelui.PixelRouteOutcome
import com.purride.pixelui.PixelRouteRequest
import com.purride.pixelui.PixelRouteStateKey
import com.purride.pixelui.pixelRouteDestination

/** Typed arguments captured independently by each editor entry. */
data class EditorArgs(
    /** Stable document identifier displayed by the editor page. */
    val documentId: String,
)

/** Builds an entry-based navigation example around the host-provided ticker. */
class RouteEntryExample(
    /** Ticker provider used by the Navigator's route transitions. */
    private val tickerProvider: PixelTickerProvider,
) {
    /** Identity-based key reused for this entry's in-memory draft. */
    private val draftKey = PixelRouteStateKey<String>("editor.draft")

    /** Reusable editor definition; every request still creates a fresh entry. */
    private val editorDestination: PixelRouteDestination<EditorArgs, String?> =
        pixelRouteDestination(
            id = "editor",
            maintainState = true,
        ) { _, scope ->
            // A stable key object reads only from this concrete entry's state bucket.
            val currentDraft = scope.stateBucket.read(draftKey).orEmpty()
            Column(
                children = listOf(
                    Text("EDIT ${scope.arguments.documentId}"),
                    Text("DRAFT $currentDraft"),
                    TextButton(
                        text = "SAVE",
                        onPressed = {
                            scope.stateBucket.write(draftKey, "saved draft")
                            scope.complete("revision-7")
                        },
                    ),
                    TextButton(
                        text = "SAVE NULL",
                        onPressed = { scope.complete(null) },
                    ),
                    TextButton(
                        text = "CANCEL",
                        onPressed = {
                            scope.cancel(PixelRouteCancellationReason.Explicit)
                        },
                    ),
                ),
            )
        }

    /** Typed root destination whose entry stays persistable across process death. */
    private val homeDestination: PixelRouteDestination<Unit, Unit> =
        pixelRouteDestination(id = "home") { context, _ ->
            // The mounted Navigator state owns all entry identities and operations.
            val navigator = PixelNavigator.of(context)
            TextButton(
                text = "OPEN EDITOR",
                onPressed = {
                    // One request captures typed arguments for one new entry.
                    val request = PixelRouteRequest(
                        destination = editorDestination,
                        arguments = EditorArgs(documentId = "doc-42"),
                    )
                    // The returned handle identifies exactly the entry just pushed.
                    val entry: PixelRouteEntry<EditorArgs, String?> =
                        navigator.push(request, ::handleEditorOutcome)
                    println("Opened entry ${entry.id.value}")
                },
            )
        }

    /** Produces the root Navigator widget for the host content provider. */
    fun build(): Widget = PixelNavigator(
        initialRequest = PixelRouteRequest(homeDestination, Unit),
        vsync = tickerProvider,
    )

    /** Handles the editor's explicit success or cancellation outcome. */
    private fun handleEditorOutcome(outcome: PixelRouteOutcome<String?>) {
        when (outcome) {
            is PixelRouteOutcome.Success -> {
                // This branch also receives Success(null); null is a valid value.
                println("Editor success: ${outcome.value}")
            }
            is PixelRouteOutcome.Cancelled -> {
                // Cancellation carries the operation-specific terminal reason.
                println("Editor cancelled: ${outcome.reason}")
            }
        }
    }
}
```

在 entry 自己的 builder 内，`PixelRouteEntryScope` 提供三类受 entry 身份保护的操作：

- `complete(result)`：仅当前 active entry 能以 typed success 退出。
- `cancel(reason)`：取消并移除该 entry；默认 reason 是 `Explicit`。
- `replaceWith(PixelRouteRequest<NextA, R>)`：创建 replacement entry。参数类型可以改变，
  但结果类型 `R` 必须与当前 entry 相同，因此调用链不会悄悄改变返回协议。

scope 在 entry 离栈后可能成为 stale；上述方法用 `Boolean` 或 nullable replacement 返回值说明
操作是否被接受。外部持有 entry 时也可以调用 `navigator.complete(entry, result)`、
`navigator.cancel(entry)`、`navigator.remove(entry)` 或 `navigator.remove(entry.id)`。

#### Success(null) 与 Cancelled

不要用 nullable result 表示取消。对于 `R = String?`，下面两个终态不同：

```kotlin
// 业务成功，返回值恰好为 null。
val successfulNull: PixelRouteOutcome<String?> = PixelRouteOutcome.Success(null)

// 没有业务返回值；原因是调用方显式取消。
val explicitCancellation: PixelRouteOutcome<String?> =
    PixelRouteOutcome.Cancelled(PixelRouteCancellationReason.Explicit)
```

每个 `PixelRouteResultChannel<R>` 只能从 `Pending` 进入一次 `Succeeded` 或 `Cancelled`。
Navigator 先完成离栈、转场和 `onDispose`，再投递 outcome callback；回调抛错不会让通道回到
pending，也不会阻断后续 entry 的清理。

#### Entry 状态与 maintainState

`PixelRouteStateBucket` 属于一个具体 entry，而不属于 destination。`PixelRouteStateKey<T>` 按
key 对象身份寻址，不按 `name` 字符串寻址；应把 key 声明为稳定属性并重复使用。对 nullable
值需要用 `key in bucket` 区分“没有值”和“存了 null”。entry 永久销毁时，bucket 一定清空。

| `maintainState` | inactive entry 的真实行为 |
|---|---|
| `true`（默认） | 真实 `StatefulWidget` subtree 继续挂载，State 实例和值保持；entry host 被隐藏，后台不 layout、不绘制、不命中，也不暴露给 widget finder；state bucket 保留 |
| `false` | 进入 inactive 时立即清空并暂停 bucket retention，离场转场结束后卸载 subtree；再次回到前台时创建新的 State subtree |

自定义 `PixelRouteTransitionBuilder` 收到的 `outgoing` / `incoming` 是转场展示副本。真实的
maintained entry host 仍在稳定位置挂载，只是在自定义转场期间隐藏；不要把展示副本的 State
身份当成 entry 的长期状态，也不要在 transition builder 中持有这些 widget。

#### 生命周期与操作时序

生命周期状态先提交，再调用对应 callback。`onDispose` 对每个 entry 最多一次；结果 callback
在 `Disposed` 之后最多一次。

| 变化 | 触发场景 | callback 与顺序 |
|---|---|---|
| `Created -> Active` | root 初始化、push、replacement 成为前台 | 状态改为 `Active`，再调用 `onEnter` |
| `Active -> Inactive` | push 新 entry 覆盖当前 entry | 状态改为 `Inactive`，再调用 `onExit` |
| `Inactive -> Active` | pop/remove/clear 后露出下层 entry | 恢复 retention、改为 `Active`，再调用 `onEnter` |
| `Active -> Removing` | 当前 entry 被 pop、cancel、replace、remove、clear | 先改为 `Removing`，再调用 `onExit` |
| `Inactive -> Removing` | 非当前 entry 被 remove/clear/reset | 不重复调用 `onExit` |
| `Removing -> Disposed` | 离场转场结束，或无动画立即 settle | 改为 `Disposed`、清 bucket、调用一次 `onDispose`，然后才交付 result |

主要操作的结果协议：

| 操作 | stack / lifecycle 行为 | pending typed outcome |
|---|---|---|
| `push(request)` | 总是分配新 entry；原 active entry 变为 inactive | 新 entry 保持 pending |
| `complete(entry, value)` / `scope.complete(value)` | 只接受 exact active entry；按 pop 路径露出下层 entry | `Success(value)`，包括 `Success(null)` |
| legacy `pop()` / `maybePop()` | 移除当前 entry；最后一个 root 不可移除 | 为兼容旧签名，等价于 `pop(null)`，即 `Success(null)` |
| Host discrete / predictive back | start/progress/cancel 不改栈；commit 才移除当前 entry | typed channel 为 `Cancelled(Back)`；不伪造 `Success(null)` |
| `cancel(entry, reason)` | 移除指定 entry；默认 reason 为 `Explicit` | `Cancelled(reason)` |
| typed `replace(request)` / `replaceWith(request)` | 旧 entry 销毁，新 entry 使用新 ID、bucket 和 channel | 旧 channel 为 `Cancelled(Replaced)`；新 channel 独立 pending |
| `remove(entry/id)` | inactive entry 立即 settle；active entry 可执行离场转场；最后一个 root 不可移除 | `Cancelled(Removed)` |
| `clear()` | 保留 root；其余 entry 按 bottom-to-top 顺序销毁；所有 entry 销毁完后才按同序交付结果 | `Cancelled(Cleared)` |
| Navigator host dispose | 先保留已有 pending finalization 顺序，再把仍在 stack 的 entry 按 top-to-bottom 入队；全部销毁后交付仍 pending 的结果 | `Cancelled(NavigatorDisposed)` |
| restore / deep-link stack reset | 旧 entry 被完整替换并销毁 | `Cancelled(StackReset)` |

动画操作会把离场 entry 保持在 `Removing`，直到转场 settle。下一次导航操作会先 settle 被打断的
旧转场；过期的转场完成回调不会重复 dispose 或重复投递结果。

`pop()` 与 typed `complete(...)` 都会执行 destination 的 `canPop(entry)`；返回 false 或 callback
抛错时不改变 stack。`cancel(...)`、`remove(...)`、`replace(...)` 与 `clear(...)` 是显式管理
操作，不经过 `canPop`。

#### 重复入栈与 replace 结果语义

同一个 `PixelRouteRequest` 或 destination 重复入栈时，每个 stack 位置都会分配独立 entry ID、
state bucket、result channel 与生命周期，绝不共享。

- `replace(request)` 取消旧 entry 的 channel（`Cancelled(Replaced)`），并为 replacement 创建
  独立 callback；旧 callback 不会被转移到新 entry。
- `pop()` / `pop(result)` 沿用未类型化的 `Any?` 结果；需要区分「成功返回 null」与「取消」时使用
  typed `complete(entry, result)` 与 `cancel(entry, reason)`。
- stack 状态只通过 `currentEntry` / `entries` 读取。

#### Observer、失败与 inspection

`addObserver` / `removeObserver` 管理 `PixelNavigationObserver`。事件带单 Navigator 内单调递增的
`sequence`、`PixelNavigationAction`、`Started` / `Completed` / `Failed` 阶段以及相关 entry ID。
observer 抛错会被隔离并记录为 `ObserverCallbackFailed`，不会撤销已经接受的导航操作。

被拒绝的 pop/remove、stale entry、destination callback 异常和 result callback 异常会写入
`lastFailure`。`inspectionSnapshot()` 返回 entry 顺序、destination ID、生命周期、result state、
state key 名、当前 entry、转场和最近失败；它故意不持有参数或 widget。

`PixelNavigatorInspectionSnapshot`、observer 事件和 `lastFailure` 都只用于当前进程内调试与测试，
不可写入 Bundle 或作为恢复格式。持久化只走下面的版本化 typed codec。

#### 版本化 typed snapshot

需要恢复的 root 由 `PixelNavigator(initialRequest = PixelRouteRequest(...))` 声明。每个
destination 用 `PixelRouteSnapshotAdapter<A, R>` 注册：

- `PixelRoutePayloadCodec<A>` 写入自己的参数 schema，并在 `decode` 中显式迁移旧版本；
- `encodeRouteState` 只选择允许落盘的 bucket 值；
- `decodeRouteState` 先校验，再返回 `PixelRouteStateRestorer`；
- `PixelRouteSnapshotRegistry` 拒绝未知或重复 destination ID。

`persistentSnapshot` / `savePersistentSnapshotToBundle` 保存 entry ID、destination、参数、当前页和
允许恢复的局部状态。`restorePersistentSnapshot` 会先完成 checksum、长度、stack invariant、
destination 和 payload 校验，再一次性替换 live stack。未知 route、未来 schema、重复 ID、损坏
bytes 或迁移拒绝都返回结构化 `Rejected`，原 stack 不变。

pending callback、Widget/State、listener 和 ticker 不跨进程恢复。恢复后的 allocator 从最大 entry
ID 继续递增，避免新 push 与旧 ID 碰撞。完整 adapter 示例和 Bundle 流程见
[路由与恢复指南](guides/navigation.md)。

#### 嵌套和多返回栈

`PixelMultiStackNavigator` 同时挂载所有 child Navigator；inactive stack 保持 Element/State 和
route history，但不 paint、hit-test、导出 semantics、出现在 finder 或接收 back。controller：

- `selectStack` 切换而不清栈；可选 `popToRootOnReselect`；
- active secondary root 的 back 先回 initial stack；
- discrete 和 predictive session 只转发到 start 时锁定的 active stack；
- `persistentSnapshot(registries)` 批量捕获全部 typed child stack 和 active selection；
- `PixelMultiStackSnapshot.saveToBundle` / `getPixelMultiStackSnapshot` 使用独立 outer schema；恢复前
  会先解码所有 child plan。

`NavigationBar` / `NavigationRail` 可以直接绑定这个 controller。每个
`PixelNavigationDestination.id` 必须稳定、唯一，并在 Host 已挂载后对应一个真实 stack id；label 也必须
非空且唯一。选择另一目的地只激活已挂载子栈，不清理 inactive history；当
`popToRootOnReselect = true` 时，重选 active 目的地只清理该子栈 root 以上的 entry。Back 仍由
`PixelMultiStackNavigatorController` 负责：先 pop active 子栈，再从 secondary root 回到 initial stack。

```kotlin
/** Stable destinations whose ids match PixelTypedNavigatorStack ids. */
val appDestinations = listOf(
    PixelNavigationDestination(id = "home", label = "HOME", icon = homeIcon),
    PixelNavigationDestination(id = "settings", label = "SETTINGS", icon = settingsIcon),
)

NavigationBar(
    destinations = appDestinations,
    controller = navigationController,
    popToRootOnReselect = true,
    animated = true,
    key = "primary-navigation",
)
```

受控重载则传 `selectedId` 与 `onSelected(String)`，不需要 controller。两种重载都按 destination id
保留 identity：插入或重排只更新当前 collection 位置，不会把旧焦点或回调重定向到相同下标的新项。
Bar 使用 Left/Right，Rail 使用 Up/Down，都会循环跳过 disabled destination；可交互且至少有一个
enabled destination 时整组是一个 Tab stop，并始终导出结构化 `SINGLE` collection。

每个 tab root 都声明为 `PixelTypedNavigatorStack<A, R>`，因此多栈天然支持进程恢复。
`PixelNestedNavigator` 位于 maintained route 中时传入 `parentEntry`，parent inactive 后
nested back bridge 自动禁用；需要版本化恢复时额外传入 `PixelNestedNavigatorController`。

#### Typed Deep Link

`PixelTypedDeepLinkRoute<A, R>` 把一个 URI matcher、`PixelDeepLinkArgumentDecoder<A>` 和合法
destination 绑定。matcher 命中后 decoder 返回 `PixelDeepLinkDecoded(arguments)` 或带
Missing/Invalid 参数详情的 `PixelDeepLinkDecodeRejected`。malformed、unmatched、missing、invalid
和 consumer callback exception 都不修改 stack；成功操作以 `PixelNavigationAction.DeepLink`
通知 observer。多栈 overload 只有在目标 stack 导航成功后才切 tab。

#### Predictive Back

`PixelHostView` 默认在确有输入、widget handler 或 app fallback 时注册平台 callback：API 33
接离散 `OnBackInvokedCallback`，API 34+ 接完整 `OnBackAnimationCallback`，API 24–32 继续调用
`handleBackPressed()`。自定义 Host 可以直接调用 `handlePredictiveBackStarted / Progressed /
Cancelled / Committed`。

Navigator 的 start/progress 只建立 presentation；cancel 不触发 exit/dispose/result；commit 才按
最新 progress 完成 pop，并对 typed entry 产生 `Cancelled(Back)`。并发 push/replace 会使旧手势
的迟到 commit 失效。inactive nested/multi-stack handler 不会收到 progress。

### 动画

| API | 用途 |
|---|---|
| `PixelTickerProvider` | ticker 创建、pause/resume/dispose 与 active-time diagnostics |
| `PixelTickerProviderDiagnostics` | active/live ticker、pending frame、active time 与累计计数 |
| `PixelHostFrameScopeDiagnostics` | callback、listener、source frame 与 ticker 所有权计数 |
| `PixelHostView.frameScopeDiagnostics` | 从真实 Android Host 按需读取 frame scope 的只读基础类型快照 |
| `PixelMotionTheme` / `PixelMotionThemeData` | 标准组件的 feedback、selection、Overlay、Slidable 和 route token |
| `PixelMotionSpec` / `PixelResolvedMotion` | duration、curve、delay、preset、spring、role 及系统设置解析结果 |
| `PixelMotionScope` / `PixelMotionSettings` | Host ticker、animator scale 和 reduce-motion inherited 环境 |
| `PixelAnimationController` | 动画控制器 |
| `CurvedAnimation` | 曲线包装 |
| `Curves` / `Curve` / `Interval` | 曲线 |
| `Tween` | 补间基类 |
| `IntTween` / `EdgeInsetsTween` / `OffsetTween` / `PixelColorTween` / `PixelGradientTween` | 常用补间 |
| `TweenAnimationBuilder` | builder 动画 |
| `AnimatedBuilder` | 监听动画并重建局部子树 |
| `AnimatedContainer` | 容器属性动画 |
| `AnimatedOpacity` | 透明度动画 |
| `AnimatedPadding` / `AnimatedAlign` / `AnimatedPositioned` | 布局动画 |
| `AnimatedSwitcher` | child 切换动画 |
| `AnimatedVisibility` | child / replacement 显隐切换动画 |
| `AnimatedSprite` | sprite 帧动画 |

#### MotionTheme 与标准组件

`PixelHostView` 会自动在根树注入 `PixelMotionScope`，标准组件无需额外传入 vsync。应用只需在
Widget 树需要覆盖默认 token 的位置提供 `PixelMotionTheme`：

```kotlin
/** Applies one shared selection token without creating a second ticker provider. */
fun motionThemedContent(child: Widget): Widget {
    /** Selection token used by Switch, Slider, and Tabs. */
    val selection = PixelMotionSpec(
        duration = 160.milliseconds,
        delay = 20.milliseconds,
        curve = Curves.EaseInOut,
        transition = PixelMotionTransitionPreset.Fade,
        role = PixelMotionRole.Selection,
    )
    return PixelMotionTheme(
        data = PixelMotionThemeData.Default.copy(selection = selection),
        child = child,
    )
}
```

标准 token 消费关系：

| Token | 消费者与视觉通道 |
|---|---|
| `feedback` | Button/Switch/Slider/Tabs 的 pressed、hover、focus 颜色反馈 |
| `selection` | Switch thumb/颜色、Slider 程序值、Tabs 交叉选择 |
| `slidableSettle` | Slidable release 后的 snap/dismiss；拖动仍直接跟手，支持 spring |
| `dialogEnter/Exit` | `PixelOverlayController.showDialog` 的 retained opacity enter/exit |
| `popoverEnter/Exit` | Popover retained opacity enter/exit 与 paint-only 退出 |
| `route` | Navigator 与 MultiStack 的 curve、delay、系统策略；`None` 同步关闭路由转场 |

Android animator scale 会同时缩放 duration 与 delay。scale 0 同步提交终态；reduce motion 按 role
替换策略：feedback/selection/continuous 立即完成，spatial 变为无 delay、最长 80ms 的线性 fade，
而不是粗暴延迟逻辑状态。需要设置页预览或截图模式时可设置
`PixelHostView.motionSettingsOverride`；设为 null 恢复跟随系统。

Navigator 的具体非 `None` 通道由 `PixelRouteDestination.transition` / `defaultTransition` 决定；
Motion route preset 为 `None` 时优先同步关闭内置和自定义转场，其余 preset 允许 Navigator 使用
该公开路由配置。

运行中切换 theme、scale 或 reduce motion 会从当前视觉帧 retarget。目标在 pointer down 后被移除、
禁用或变为 opacity 0/paint-only 时，Host 与 `PixelTester` 会取消原 owner；up 不会触发旧 callback，
也不会落到同坐标背景控件。完整行为和测试方式见[主题与组件指南](guides/theme-and-components.md)。

#### PixelAnimationController 时间与取消契约

`duration` 表示从 `0f` 到 `1f` 的完整 active-time 时长；从中间值出发时，实际 segment
按剩余距离等比缩短。`forward(from)` / `reverse(from)` 会先校验 `NaN`，再把显式 `from`
clamp 到 `0f..1f` 并立即应用；不传 `from` 时从当前视觉值平滑重定向。第一帧只建立时间锚点，
不会把 value 跳回端点。超大 frame delta 会直接 clamp 到目标，并让 status 与 value 在同一帧
稳定为 `Completed/1f` 或 `Dismissed/0f`。

`stop()` 是可重复调用的取消操作：它移除待处理 ticker frame、保留当前 value 和
`Forward/Reverse` 方向 status，同时 `isAnimating == false`；之后再次 forward/reverse 会从保留值
开始一条新的 segment。Host pause 不等同 stop：pause 保留运行状态并冻结 active time，resume 的
首帧重新锚定，因此后台 wall-clock 时间不会造成跳帧。

边界输入遵循以下固定规则：

- 负 `duration` 在构造时抛出 `IllegalArgumentException`，且不会先创建 ticker；零或不足 1ns 的
  duration 同步到目标，不安排 frame。
- 零时长 `repeat(false)` 稳定在 `Completed/1f`；`repeat(true)` 代表完成一个往返周期，稳定在
  `Dismissed/0f`，两者都不会形成同帧死循环。
- `initialValue`、`forward(from)`、`reverse(from)` 和 `setValue` 拒绝 `NaN`；有限值和无穷值按
  `0f..1f` clamp。
- controller/provider dispose 后，新的动画或赋值命令抛出 `IllegalStateException`；`stop()` 与
  `dispose()` 自身保持幂等。

`TweenAnimationBuilder` 和基于它的隐式动画在 target 变化时，会把新 `Tween.begin`
重定基到最后一次实际 build 的视觉值，再从零进度启动新 segment。因此
`Tween.begin` / `end` 现为可变属性；不要在多个正在运行的 builder 之间共享同一个
Tween 实例，每个声明式 target 应提供独立实例。

`AnimatedSwitcher` 使用稳定 `Stack` 同时挂载 outgoing 与 incoming，并为每个视觉
entry 保留独立 keyed `Opacity`。只有 runtime type 和 key 都相同时才原地更新；快速
连续切换会保留仍有视觉贡献的所有 outgoing，切回旧 key 则提升原 entry 而不重建
State。旧动画实现迁移和虚拟时钟验证见[统一迁移指南](guides/migration.md)。

### 调试组件

| API | 用途 |
|---|---|
| `PixelDebugOverlay` | 显示 FPS、帧时间、target 统计 |
| `PixelInspectorPanel` | 展示 element/render/semantics/target 树 |
| `PixelInspectorBoundsOverlay` | 在画面上绘制 target bounds |
| `PixelHostView.frameStatsObserver` | 监听帧统计 |
| `PixelHostView.frameDiagnosticsObserver` | 在 UI 线程监听完整帧阶段、工作量与丢帧归因 |
| `PixelHostView.frameDiagnosticsEnabled` | 无 observer 时仍保留最近一帧完整诊断，默认关闭 |
| `PixelHostView.latestFrameDiagnostics` | 读取最近一次已完成的完整帧诊断 |
| `PixelHostView.inspect()` | 采样 frame、target、element、render、semantics 诊断快照 |
| `PixelHostView.dumpElementTree()` | dump element tree |
| `PixelHostView.dumpRenderTree()` | dump render tree |
| `PixelHostView.dumpSemanticsTree()` | dump semantics tree |

`PixelHostFrameStats` 是兼容入口：`paintTimeNanos` 现在覆盖完整 `onDraw`，包括真实
`drawBuffer` Canvas 提交，但不能用它区分瓶颈阶段。需要性能定位时使用
`PixelHostFrameDiagnostics`：

```kotlin
hostView.frameDiagnosticsObserver = { frame ->
    val timings = frame.timings
    logger.log(
        "build=${timings.buildNanos} layout=${timings.layoutNanos} " +
            "paint=${timings.paintNanos} submit=${timings.bufferSubmitNanos} " +
            "android=${timings.androidDrawNanos} total=${timings.totalFrameNanos}",
    )
    if (frame.isOverBudget) {
        logger.log("drop=${frame.dropReason} missed=${frame.missedVsyncCount}")
    }
}
```

五个 phase 都是互斥时间：build 是 Element reconcile/rebuild，layout 是 RenderObject
layout，paint 是逻辑 `PixelBuffer` 绘制与 target 导出，buffer submit 是
`PixelBuffer → Android Canvas`，Android draw 是 submit 之外的 View/background/accessibility
工作。`unattributedNanos` 包含 ticker/controller step、callback、采样和框架开销；五个
phase 加上 unattributed 必须恰好等于 total。

`PixelFrameWorkload` 同帧记录 dirty Element、owner-wide dirty RenderObject、逻辑 paint/submit
像素、buffer pool hit/miss 和完整 render cache hit。`allocatedBytes`、
`garbageCollectionCount` 是 `Debug.getRuntimeStat` 的进程级边界差，不是 UI 线程独占值；运行时
不提供对应 ART key 时为 null。帧预算优先使用 Host capability 的 refresh rate，缺失时才回退
60 Hz。observer 在 `onDraw` 尾部同步运行，不能执行 IO、sleep 或递归修改 Host。

默认 `frameDiagnosticsEnabled == false` 且 observer 为 null，此时不读取 ART counter、不创建
完整诊断快照。只想在 Inspector 中按需读取时可以启用 `frameDiagnosticsEnabled`，再通过
`inspect().frameDiagnostics` 或 `latestFrameDiagnostics` 读取；完成诊断后应重新关闭。完整迁移和
线程/成本边界见[性能指南](guides/performance.md)。

### pixelcore 常用类型

| API | 用途 |
|---|---|
| `PixelColor` | ARGB 颜色 |
| `PixelBuffer` | 每帧像素缓冲 |
| `PixelBitmap` | 可复用位图资源 |
| `PixelBitmapAssetLoader` / `PixelBitmapResourceLoader` | 加载 Android bitmap |
| `PixelSpriteSheet` / `PixelSpriteSheetJsonLoader` | sprite sheet |
| `PixelResourceManifestJsonLoader` / `PixelResourceCache` | 资源 manifest 与缓存 |
| `ScreenProfile` / `ScreenProfileFactory` | 逻辑屏幕档位 |
| `PixelGridGeometryResolver` | 逻辑像素到屏幕几何映射 |
| `PixelTextRasterizer` | 文本测量和绘制协议 |
| `PixelGlyphPackAssetLoader` | 字形包加载 |
| `PixelFontEngine` | 字形查询、缓存和绘制 |

#### Sprite sheet schema

`PixelSpriteSheetJsonLoader` 读取 SDK 侧 sprite sheet JSON。它不负责加载 bitmap 文件；调用方需要先通过 `PixelBitmapAssetLoader`、`PixelBitmapResourceLoader` 或自定义加载器拿到 `PixelBitmap`，再调用 `load(json, bitmap)` 或 `loadAtlas(json, bitmap)`。

所有 sprite JSON 入口都有 `expectedSha256` 重载。解析器是严格有界 JSON，而不是正则提取：重复 key、尾随垃圾、超深嵌套、过长字符串、浮点/溢出整数、超量 frame 和坐标加法溢出都会在构建对象前失败。

v1 简单 sheet schema：

```json
{
  "version": 1,
  "bitmap": "sprites/runner.png",
  "metadata": {
    "name": "runner",
    "fps": "8"
  },
  "frames": [
    { "left": 0, "top": 0, "width": 8, "height": 8 },
    { "left": 8, "top": 0, "width": 8, "height": 8 }
  ]
}
```

v2 atlas schema：

```json
{
  "version": 2,
  "bitmap": "sprites/runner.png",
  "scale": 2,
  "frames": [
    {
      "left": 0,
      "top": 0,
      "width": 6,
      "height": 8,
      "sourceWidth": 10,
      "sourceHeight": 12,
      "trimLeft": 2,
      "trimTop": 3,
      "pivotX": 5,
      "pivotY": 12
    }
  ]
}
```

字段约束：

- `version` 支持 1 或 2；未声明时按 1 处理。
- `bitmap` 是调用方资源路径或资源 id，不能为空。
- `metadata` 只接受字符串键值对；engine 不解释这些字段。
- `frames` 必须非空。
- 每帧 `left/top` 必须大于等于 0，`width/height` 必须大于 0。
- `PixelSpriteSheet` 构建时会校验每帧不能越过传入 bitmap 的边界。
- v2 atlas 的 `sourceWidth/sourceHeight` 默认等于裁剪后的 `width/height`，用于记录裁剪前原始帧尺寸。
- `trimLeft/trimTop` 默认 0，且裁剪区域不能越过 `sourceWidth/sourceHeight`。
- `pivotX/pivotY` 默认 0，允许落在 `0..sourceWidth` 和 `0..sourceHeight` 范围内。
- `scale` 默认 1，必须大于 0。
- 解析或构建失败统一抛出 `PixelSpriteSheetLoadException`。

`parseDefinition(json)` 会把 atlas metadata 降级成 `PixelSpriteSheetDefinition.frames` 的矩形列表；需要保留 trim、pivot、scale 时使用 `parseAtlasDefinition(json)` 或 `loadAtlas(json, bitmap)`。

#### 资源 manifest 与 catalog

`PixelResourceManifestJsonLoader` 是 SDK 侧资源清单入口，用于把调用方打包在 assets 或 raw 目录中的 JSON 转成稳定的资源定义。当前有两个解析入口：

- `parse(json)`：解析基础 manifest，只包含 bitmap、sprite sheet 和 metadata。
- `parseCatalog(json)`：解析完整 catalog，在基础 manifest 之外增加 color 和 font。

基础 manifest 支持 `version` 1 或 2。未声明 `version` 时按 1 处理。`metadata` 只接受字符串键值对，用于记录 pack、revision、locale 等调用方自定义信息；engine 不解释这些字段。

```json
{
  "version": 1,
  "metadata": {
    "pack": "demo",
    "revision": "1"
  },
  "bitmaps": [
    {
      "id": "runner",
      "path": "sprites/runner.png",
      "sha256": "<64 lowercase or uppercase hex chars>"
    }
  ],
  "spriteSheets": [
    {
      "id": "runnerRun",
      "path": "sprites/runner.sheet.json",
      "bitmap": "runner",
      "sha256": "<64 hex chars>"
    }
  ]
}
```

字段约束：

- `bitmaps[].id` 是资源唯一标识，不能为空；`bitmaps[].path` 必须是无 `.`/`..` 的安全相对路径；可选 `sha256` 校验编码文件。
- `spriteSheets[].id` 不能为空；`spriteSheets[].path` 指向 sprite sheet JSON；`spriteSheets[].bitmap` 必须引用同一 manifest 中已声明的 bitmap id。
- `spriteSheets[].sha256` 可记录 sheet JSON 摘要；`parse(json, expectedSha256)` / `parseCatalog(json, expectedSha256)` 还可以校验整个清单文件的外部摘要。
- 同一类型内不允许重复 id。
- `parse(json)` 不加载真实 bitmap 或 sprite sheet 文件，只完成清单解析和结构校验。
- 解析失败统一抛出 `PixelResourceManifestLoadException`。

完整 catalog 必须使用 `version: 2`，用于把颜色和字体也纳入同一个资源目录。

```json
{
  "version": 2,
  "bitmaps": [
    { "id": "icons", "path": "images/icons.png" }
  ],
  "colors": [
    { "id": "accent", "value": "#22AAFF" },
    { "id": "overlay", "value": "#80224466" }
  ],
  "fonts": [
    {
      "id": "ui8",
      "manifest": "glyphpacks/ui8/manifest.json",
      "binary": "glyphpacks/ui8/glyphs.bin",
      "manifestSha256": "<64 hex chars>",
      "binarySha256": "<64 hex chars>"
    }
  ]
}
```

catalog 额外约束：

- `colors[].value` 支持 `#RRGGBB` 和 `#AARRGGBB`。
- `fonts[].manifest` 指向 glyph pack manifest；`fonts[].binary` 指向 glyph 二进制文件。
- bitmap、sprite sheet、color、font 的 id 在整个 catalog 内必须唯一。
- 如果声明 `colors` 或 `fonts`，`version` 必须是 2。

建议调用方把 manifest/catalog 当成资源索引，不要把它当成运行时状态存储。真实资源加载仍由 `PixelBitmapAssetLoader`、`PixelBitmapResourceLoader`、`PixelSpriteSheetJsonLoader` 和 `PixelGlyphPackAssetLoader` 完成；缓存复用由 `PixelResourceCache` 管理。

#### Resource cache 行为

`PixelResourceCache` 是线程安全、按字节受限的强引用 LRU，用于 SDK consumer 在 app 生命周期内复用 `PixelBitmap`、`PixelSpriteSheet` 和 `PixelGlyphPack`。它不持有 Android `Context`/资源句柄，也不创建后台线程。

key 行为：

- key 由调用方提供，必须非空白。
- bitmap、sprite sheet 和 glyph pack 使用独立命名空间；同一个 key 可以同时存在于三类缓存。
- `remove(key)` 会移除三类同名条目，并阻止此前开始的同名在途加载重新写回；如果 key 不存在，不增加 `removeCount`。

读取行为：

- `getBitmap`、`getSpriteSheet` 和 `getGlyphPack` 命中时直接返回已缓存对象，不执行 loader。
- 同类型、同 key 的并发未命中共享一次 loader；loader 在缓存锁外执行，其他 key 不被 IO 阻塞。
- 结果按保守字节估算写入；单条超过逐类或总预算时仍返回当前调用方，但不驻留缓存。
- key 校验发生在 loader 之前，空白 key 不会触发资源加载。

clear 行为：

- `clear()` 清空当前缓存条目、不重置统计，并阻止清理前已经开始的在途结果重新写回。
- 只有缓存非空时才增加 `clearCount`；对空缓存重复调用不会增加计数。

snapshot 行为：

- `snapshot()` 保留旧的 bitmap/sprite 计数、命中率和生命周期计数契约。
- `detailedSnapshot()` 额外返回逐类/总字节、glyph 数量、全局命中/未命中、淘汰、过大拒绝、在途数量和按 LRU 顺序排列的无引用条目快照。
- `PixelResourceEvictionListener` 在缓存锁外收到 `ENTRY_TOO_LARGE`、逐类字节/条目预算和总字节预算原因；监听器异常不会破坏缓存。

memory 边界：

- 默认 `PixelResourceCacheLimits` 为总计 128 MiB、bitmap/sprite 各 96 MiB、glyph 48 MiB，并同时限制每类条目数；宿主应按进程预算显式下调，而不是依赖默认值承担所有应用缓存。
- 超过逐类预算时淘汰该类最久未访问条目；超过总预算时按三类共享访问序淘汰全局最久未访问条目，行为可预测。
- 资源包切换、页面销毁、`onTrimMemory` 或主题/字体资源变化时仍应调用 `remove` / `clear`，及时释放强引用。

#### 正式同步/异步资源加载

`PixelResourceLoader` 把线程策略、缓存单飞、订阅取消、预取和短期失败缓存统一到一个入口。executor 的生命周期归宿主持有，SDK 不创建或关闭全局线程池。

```kotlin
val cache = PixelResourceCache(
    limits = PixelResourceCacheLimits(maxTotalBytes = 48L * 1024L * 1024L),
    evictionListener = PixelResourceEvictionListener { event ->
        logger.log("resource eviction: ${event.kind}/${event.key}/${event.reason}")
    },
)
val resourceLoader = PixelResourceLoader(
    cache = cache,
    executor = resourceExecutor,
    policy = PixelResourceLoadingPolicy(failureCacheDurationMillis = 5_000L),
)
val bitmapLoader = PixelBitmapAssetLoader(context.assets)

val handle = resourceLoader.loadBitmapAsync("runner@v3") {
    bitmapLoader.load(
        path = "sprites/runner.png",
        expectedSha256 = manifest.bitmaps.single().sha256,
    )
}
```

- `load*` 同步入口默认拒绝 Android 主线程；只有已经在后台线程的调用方才应使用。确需特殊测试环境时，可显式设置 `allowSynchronousMainThread`，生产 UI 不建议开启。
- `load*Async` 在调用方 executor 执行 IO/校验/解析；同类型、同 key 的订阅共享任务。
- `PixelResourceLoadHandle.cancel()` 只取消当前订阅等待，不取消共享 IO，也不影响其他订阅者和成功后的缓存写入。
- `prefetch*` 使用相同异步契约，返回可观察句柄。
- loader 失败默认缓存 5 秒，避免损坏文件或临时 IO 故障造成重试风暴；`clearFailure` / `clearFailures` 可显式解除。
- 未完成句柄禁止在 Android 主线程 `await()`；已经完成的结果可以无阻塞读取。

完整行为和迁移方式见[资源与内存指南](guides/resources.md)。

#### 资源打包工具

当前仓库只提供 glyph pack 打包工具：`tools/generate_pixel_glyph_packs.py`。它把 TTF、OTF 或 BDF 字体转换成 `PixelGlyphPackAssetLoader` 可读取的目录结构。

输出结构：

```text
<output>/<pack-id>/manifest.json
<output>/<pack-id>/glyphs.bin
```

生成 app 内置 Fusion Pixel 字形包：

```bash
./gradlew :pixel-engine:generatePixelGlyphPacks
```

这个 Gradle task 不接收参数，默认读取 `app/src/main/assets/fonts` 下的 Fusion Pixel 字体，并写入 `app/src/main/assets/glyphpacks`。这是当前 Launcher app 的内置资源生成入口，不是 SDK consumer 的通用输出目录。

生成自定义 BDF 字形包：

```bash
python3 tools/generate_pixel_glyph_packs.py \
  --input path/to/font.bdf \
  --output app/src/main/assets/glyphpacks \
  --pack-id ui8 \
  --display-name "UI 8px" \
  --cell-height 8 \
  --baseline 7 \
  --default-advance 8 \
  --ranges 0020-007E,4E00-9FFF
```

生成自定义 TTF / OTF 字形包：

```bash
python3 tools/generate_pixel_glyph_packs.py \
  --input path/to/font.ttf \
  --output app/src/main/assets/glyphpacks \
  --pack-id ui8 \
  --display-name "UI 8px" \
  --cell-height 8 \
  --baseline 7 \
  --default-advance 8 \
  --font-size 8 \
  --ranges 0020-007E
```

TTF / OTF 路径依赖 Python 的 Pillow；BDF 路径只依赖标准库。转换器会跳过无法渲染或非空白但没有像素的 glyph，并把实际写入的 code point 汇总到 `manifest.json.supportedRanges`。

验证打包器：

```bash
./gradlew :pixel-engine:testPixelGlyphPackConverter
```

接入运行时：

```kotlin
val pack = PixelGlyphPackAssetLoader(context).load("glyphpacks/ui8")
val source = BitmapGlyphSource(listOf(pack))
val rasterizer = PixelStyledTextRasterizer(
    engine = PixelFontEngine(CompositeGlyphProvider(listOf(source))),
    style = GlyphStyle(
        cellHeight = 8,
        narrowAdvanceWidth = 8,
        wideAdvanceWidth = 16,
        oversampleFactor = 1,
        narrowMinimumSampleRatio = 1f,
        wideMinimumSampleRatio = 1f,
        narrowTextSizeRatio = 1f,
        wideTextSizeRatio = 1f,
        narrowFontWeight = PixelFontWeight.NORMAL,
        wideFontWeight = PixelFontWeight.NORMAL,
        narrowFontFamily = PixelFontFamily.MONOSPACE,
        wideFontFamily = PixelFontFamily.DEFAULT,
    ),
)
```

`PixelResourceCatalog.fonts` 可以记录 `manifest` 和 `binary` 路径，但它不会自动生成或加载 glyph pack。sprite sheet JSON 和 resource manifest/catalog 当前也没有独立打包器，按本手册 schema 手写并用对应 loader 校验。

## 8. 自定义 RenderObject

用于内置 widget 不够表达的场景。

```kotlin
class HollowSquareWidget(
    private val side: Int,
    private val color: PixelColor,
    override val key: Any? = null,
) : PixelLeafRenderObjectWidget(key = key) {
    override fun createRenderObject(context: BuildContext): PixelRenderObject =
        HollowSquareRender(side, color)

    override fun updateRenderObject(context: BuildContext, renderObject: PixelRenderObject) {
        (renderObject as HollowSquareRender).update(side, color)
    }
}

private class HollowSquareRender(
    private var side: Int,
    private var color: PixelColor,
) : PixelRenderBox() {
    override fun layout(constraints: PixelRenderConstraints) {
        size = PixelRenderSize(
            width = constraints.constrainWidth(side),
            height = constraints.constrainHeight(side),
        )
    }

    override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
        for (y in 0 until size.height) {
            for (x in 0 until size.width) {
                val edge = x == 0 || y == 0 || x == size.width - 1 || y == size.height - 1
                if (edge) context.setColor(offsetX + x, offsetY + y, color)
            }
        }
    }

    fun update(nextSide: Int, nextColor: PixelColor) {
        val sizeChanged = side != nextSide
        val colorChanged = color != nextColor
        if (!sizeChanged && !colorChanged) return
        side = nextSide
        color = nextColor
        if (sizeChanged) markNeedsLayout() else markNeedsPaint()
    }
}
```

规则：

- 字段无变化时不要标脏
- 尺寸变化用 `markNeedsLayout`
- 纯绘制变化用 `markNeedsPaint`
- `paint` 热路径避免大对象分配
- 只在主线程调用 runtime 标脏 API

## 9. 测试

`PixelTester` 是发布在 SDK 里的离屏测试工具，可在普通单元测试中 pump widget、
发送输入、推进帧，并读取像素和 semantics 结果。

示例：

```kotlin
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find

val tester = PixelTester()

tester.pumpWidget(
    widget = OutlinedButton(text = "OK", onPressed = { clicked = true }),
    logicalWidth = 32,
    logicalHeight = 12,
)

tester.tap(find.byText("OK"))
tester.drag(find.byKey("list"), dx = 0, dy = -12)
tester.enterText(find.byKey("field"), "hello")
tester.pumpAndSettle()

check(tester.hasPixel(PixelColor.White))
check(tester.dumpSemanticsTree().contains("BUTTON"))
val goldenText = tester.dumpPixelsAsAscii()
```

Finder：

- `find.byText("OK")`
- `find.byType(SomeWidget::class)`
- `find.byKey("key")`
- `find.byText("OK").nth(1)`

可用能力：

- 渲染：`pumpWidget` 渲染根 widget，`pumpFrame` / `pumpAndSettle` 推进动画、ticker 和滚动 settling。
- 交互：`tap`、`doubleTap`、`longPress`、`drag`、`fling`、`cancelDrag` 覆盖常见点击和滚动手势。
- 手势流：`startGesture` 返回 `PixelTestGesture`，可用 `moveBy`、`up`、`cancel` 验证分步拖动、多指和取消分支。
- 文本输入：`enterText`、`pressText`、`composeText`、`updateComposition`、`submitTextInput`、`performTextEditAction` 覆盖 supplementary/multi-code-point 输入、composition、提交、复制、剪切、粘贴和全选。
- 键盘：`pressKey` 向当前 focus tree 发送按键，适合验证 focus 和快捷键路径。
- 调试树：`dumpElementTree`、`dumpRenderTree`、`dumpSemanticsTree` 用于定位 widget / render / semantics 状态。
- 生命周期：测试结束后调用 `dispose`，清理 runtime、手动帧调度器和 focus 状态。

像素验证：

- `tester.pixelAt(x, y)`
- `tester.hasPixel(color)`
- `tester.dumpPixelsAsAscii()`

#### 确定性 golden 与审阅流程

M8-1 的综合像素基线使用仓库内置 `PixelBitmapFont.Default`、`PixelTester` 手动时钟和固定
`ScreenProfile`。标准组件状态、Light/Dark/HighContrast 主题、LTR/RTL、1×/2× textScale，以及
动画 0/500/1000 ms 关键帧，都编码为保留坐标与完整颜色的 exact-ARGB 行程，不使用亮度分桶或
设备字体。

源码基线是只读评审输入。`ReviewedGoldenVerifier` 每次都把实际结果写到 `build/reports/golden/`
下的 `.actual.txt`；不一致时还会生成带共同上下文、删除行和新增行的 `.diff`，然后让测试失败。
测试代码没有 `REGEN_GOLDEN`、`UPDATE_GOLDEN` 或其他自动接受分支，也不会在基线缺失时创建源码
文件。

更新流程：

1. 运行受影响的定向 golden 测试，确认失败报告同时给出源码基线、候选和 diff 路径。
2. 人工检查 `.diff` 中每个像素、semantics action 和生命周期顺序变化，确认它与预期设计一致。
3. 通过可审阅的源码 patch 修改对应 `src/test/resources/` 基线；不要直接把候选无条件复制为基线。
4. 重跑定向测试和完整 `:pixel-engine:testDebugUnitTest`。匹配后候选仍保留用于追溯，过期 `.diff`
   必须被删除。

M8-1 综合基线位于 `src/test/resources/golden/m8-1-deterministic-pixels.txt`，其候选默认位于
`build/reports/golden/m8-1/deterministic-pixels.actual.txt`。`ReviewedGoldenVerifierTest` 还会主动注入
单像素变化、删除 semantics action、交换生命周期顺序，验证三类错误都失败且源码基线不变。

边界：

- `PixelTester` 是 JVM 内离屏测试工具，用于验证 widget、layout、输入分发、滚动和像素输出。
- 它不启动 Android `Activity`，不覆盖真实 `PixelHostView` 接线、Android IME、系统剪贴板、无障碍服务和设备 GPU/刷新率行为。
- 宿主接线使用 host 单测、instrumentation 和 SDK consumer smoke 验证；性能和渲染节奏留给后续独立性能目标。
- golden 文本快照应优先覆盖小尺寸、确定性 UI；复杂动效只断言关键状态和收敛结果。

常用验证：

```bash
./gradlew :pixel-engine:testDebugUnitTest :pixel-engine:assembleDebug :app:assembleDebug --no-daemon
./tools/pixel-release-check.sh
./tools/pixel-sdk-consumer-smoke.sh
```

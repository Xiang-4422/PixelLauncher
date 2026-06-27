# pixel-engine 使用说明与 API 手册

本文面向使用 pixel-engine 构建像素 UI 的开发者。内部实现和维护规则见 [架构与技术实现](架构与技术实现.md)。

## 1. 快速接入

pixel-engine 是 Android library 模块，最低 `minSdk = 24`，当前 `compileSdk = 36`。

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

如果按 SDK 包消费本地发布产物：

```kotlin
repositories {
    mavenLocal()
}

dependencies {
    implementation("com.purride:pixel-engine:0.1.0-SNAPSHOT")
}
```

常用 import：

```kotlin
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.*
import com.purride.pixelui.state.*
import com.purride.pixelui.widgets.animated.*
```

导航、手势、advanced 扩展有少量子包：

```kotlin
import com.purride.pixelui.gesture.*
import com.purride.pixelui.advanced.*
import com.purride.pixelui.widgets.navigation.*
```

### 主流程覆盖矩阵

| 主流程 | 文档位置 |
|---|---|
| SDK 依赖与 import | `1. 快速接入` |
| Android Activity 宿主 | `2. 最小 Activity` |
| 宿主配置、背景、字体和手势策略 | `3. 宿主配置` |
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

## 3. 宿主配置

```kotlin
val setup = createPixelHostSetup(
    context = this,
    config = PixelHostSetupConfig(
        profilePreference = PixelHostProfilePreference(
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
| `profilePreference` | 点大小、像素形状偏好 |
| `bezelColor` | 屏幕外框和画布背景色 |
| `textRasterizer` | 默认文本栅格器 |
| `textDirection` | LTR / RTL |
| `pagerGesturePolicy` | PageView 手势启动策略 |
| `nestedScrollPolicy` | 嵌套滚动仲裁策略 |
| `scrollPhysics` | 列表滚动物理 |
| `frameScheduler` | 帧调度器 |
| `backDispatcher` | widget back 栈调度器 |
| `onUnhandledBack` | back 未被 widget 消费时的 app fallback |
| `content` | 根 widget provider |

### Android 宿主契约

pixel-engine 当前只承诺 Android 宿主。默认路径使用 `createPixelHostSetup`；只有自定义宿主、
测试宿主或替代输入桥时才直接操作 `PixelHostView` / `PixelHostBridge`。

| 层级 | SDK 负责 | App 负责 |
|---|---|---|
| `PixelHostSetup` | 创建 `PixelHostView`、默认 `PixelTextInputBridge` 和根 `FrameLayout` | 把 `rootView` 放进 Activity / Fragment |
| `PixelHostView` | 渲染、触摸、按键、手柄拖动、insets 注入、accessibility 和调试 dump | 在 Android back 回调里调用 `handleBackPressed()` |
| `PixelTextInputBridge` | 隐藏 `EditText`、IME 映射、selection / composition 同步、剪贴板和 haptic 默认桥接 | 业务侧决定何时聚焦、提交、保存文本状态 |
| `PixelBackDispatcher` | 维护 widget 内 back 栈，并按栈顶优先派发 | 提供 `onUnhandledBack` 或回落到系统 back |
| saved state helpers | 提供 navigator / list / pager / text field 的 `Bundle` 保存恢复 API | 在 Activity / Fragment 生命周期里显式调用保存恢复 |

自定义宿主需要实现的桥接入口集中在 `PixelHostBridge`：

- `showTextInput` / `updateTextInput` / `hideTextInput`
- `performHapticFeedback`
- `requestFrame`
- `dispatchSystemAction`
- `readClipboardText` / `writeClipboardText`

边界：

- SDK 不接管 Activity / Fragment 生命周期；`PixelHostView` 只在 detach 时自动释放自身 runtime。
- SDK 不自动保存 Android `savedInstanceState`；业务 controller/state 由 app 显式保存恢复。
- SDK 不封装权限、Intent、通知、文件选择等 Android 业务能力；这类能力通过 app 层或 `PixelSystemAction` 自行接线。
- SDK 不提供 Material / Cupertino 组件库；宿主契约只保证像素 UI 引擎和 Android 系统能力的连接。

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

engine 使用 ARGB `PixelColor`。`PixelTheme` 只提供 widget 树内的像素默认样式，
不接管 Android host 配置；`bezelColor`、`offPixelColor` 仍通过
`PixelHostSetupConfig` / `PixelHostView` 设置。

```kotlin
val theme = PixelThemeData(
    colors = PixelThemeColors.Default.copy(
        text = PixelColor.fromRgb(151, 255, 167),
        border = PixelColor.fromRgb(255, 220, 120),
        selection = PixelColor.fromRgb(255, 220, 120),
    ),
)

setup.hostView.bezelColor = PixelColor.Black
setup.hostView.offPixelColor = PixelColor.fromRgb(8, 37, 13)

PixelTheme(
    data = theme,
    child = Column(
        children = listOf(
            Text("HOME"),
            OutlinedButton(text = "OPEN", onPressed = {}),
        ),
    ),
)
```

显式参数优先级高于主题，例如 `Text("HOME", color = PixelColor.White)` 不会使用
`PixelThemeData.textStyle.color`。

当前 `PixelThemeData` 边界：

- `textStyle`：`Text` / `RichText` 的默认文字样式。
- `buttonStyle`：`OutlinedButton` 的默认边框、文字样式和对齐；disabled 默认使用 `colors.disabled`。
- `textButtonStyle`：`TextButton` 的默认文字样式、padding 和对齐；disabled 默认使用 `colors.disabled`。
- `textFieldStyle`：`TextField` 的默认边框、文字、placeholder、cursor、selection 和 composition 样式。

不在边界内：

- 不接管 `PixelHostView` 的 `bezelColor` / `offPixelColor` / screen profile。
- 不为纯函数组件强插 `Builder` 或额外 wrapper；后续组件如果自然重构为 context-aware widget，再顺手接入主题。
- 不提供 Material / Cupertino token，也不追求通用设计系统。

字体优先级：

1. `TextStyle(textRasterizer = ...)`
2. `DefaultTextRasterizer`
3. `PixelHostView.textRasterizer`
4. `PixelBitmapFont.Default`

字体包加载与 fallback：

- `PixelGlyphPackAssetLoader(context).load(assetDirectory)` 会从 `assets/<assetDirectory>/manifest.json` 和 `assets/<assetDirectory>/glyphs.bin` 加载一个 `PixelGlyphPack`，同一路径会在 loader 内缓存。
- `PixelGlyphPackParser.parseManifest(json)` 只解析字形包元数据；`parseBinary(manifest, inputStream)` 解析二进制字形并校验二进制 `cellHeight` 与 manifest 一致。
- `BitmapGlyphSource(packs)` 按 pack 顺序查找字符，只消费与当前 `GlyphStyle.cellHeight` 匹配的 pack。
- `CompositeGlyphProvider(sources)` 按 source 顺序查找字符；全部 source 都缺字时，会返回 engine 内建兜底字形。
- 兜底字形按 ASCII / 非 ASCII 选择 `narrowAdvanceWidth` 或 `wideAdvanceWidth`；普通缺字会绘制可见方框，空白和控制字符只保留 advance、不绘制墨迹。
- SDK 不在字体层自动读取 `PixelResourceCatalog.fonts`；catalog 只提供索引，调用方仍需要用 `PixelGlyphPackAssetLoader` 或自定义加载器创建 `PixelGlyphPack`。

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

### Overlay

`PixelOverlayHost` 在页面顶部承载 toast、dialog 和 snackbar。controller 由业务持有。
当页面接入 `PixelBackHost` 时，back 会优先关闭最上层 overlay。
overlay 只维护当前 item 列表，不内置自动超时、遮罩动画或模态焦点锁定；需要这些策略时由业务层调度
handle 或自定义 wrapper。

```kotlin
val overlay = PixelOverlayController()

PixelOverlayHost(
    controller = overlay,
    child = AppRoot(),
)

overlay.showToast("SAVED")

val dialog = overlay.showDialog(
    title = Text("DELETE"),
    content = Text("ARE YOU SURE"),
    actions = listOf(TextButton(text = "CANCEL", onPressed = {})),
)
dialog.dismiss()

overlay.showSnackbar(
    message = "QUEUED",
    action = TextButton(text = "UNDO", onPressed = {}),
)
```

`showDialog` 返回的 handle 是关闭对话框的明确所有权；`showToast` 不会自动消失；`showSnackbar`
会把 `Snackbar` 放到底部，直接使用 `Snackbar` widget 时它只是一个普通条形容器。

### Back

默认 `createPixelHostSetup` 会创建 `PixelBackDispatcher` 并给 `content` 包一层
`PixelBackHost`。宿主收到 Android back 时调用 `hostView.handleBackPressed()`。
处理顺序固定为：文本输入 blur -> overlay dismiss -> navigator pop -> app fallback。

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

### Lifecycle

`PixelHostView` 会在 Android `onDetachedFromWindow` 时自动释放 retained widget tree、
render tree 和像素缓存。Activity 直接把 `setup.rootView` 作为 content view 时，通常不需要
额外处理。

Fragment 或自定义宿主如果有明确的视图销毁点，可以调用 `PixelHostSetup.dispose()`。

```kotlin
override fun onDestroyView() {
    setup.dispose()
    _binding = null
    super.onDestroyView()
}
```

`dispose()` 只释放 pixel-engine runtime 和隐藏输入桥接，不移除 Android view 层级；
view 层级仍交给 Activity / Fragment 自身管理。

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

`PixelNavigatorState.saveToBundle` / `restoreFromBundle` 保存 route stack。路由内滚动位置优先用
`PixelRouteScrollRestoration`，它只在 route 还留在 navigator stack 内时生效；跨 Activity
重建仍使用上面的 `PixelListSavedState`。

### Accessibility

`PixelHostView` 会把最近一帧的 semantics tree 暴露成 Android virtual accessibility nodes。
业务侧使用 `Text`、`TextField`、按钮、选择控件或显式 `Semantics(...)` 时，不需要额外接
Android `AccessibilityNodeProvider`。

当前桥接范围是 label、role、enabled、focused 和 bounds；可点击语义会复用已有 click target，
`TextField` 语义会复用已有输入焦点目标。

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

### 列表项和选择控件

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

`Tabs` 和 `SegmentedControl` 都使用 `labels` 的位置作为 index，`selectedIndex` 由调用方保存。
`Tabs` 适合页面/区域切换；`SegmentedControl` 适合同一位置内的紧凑模式切换。调用方应保证
`selectedIndex` 指向有效 label；`onSelected` 只回传被点击项的 index，不维护内部状态。

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
传入或置空两侧回调控制。`Stepper` 是整数范围包装，会按 `step` 调整并把结果钳位到 `range`。

```kotlin
Stepper(
    value = size,
    range = 0..10,
    step = 2,
    onChanged = { next -> size = next },
)
```

### 反馈组件

`Dialog`、`ConfirmDialog`、`Toast` 和 `Snackbar` 本身都是普通 widget，只负责像素风视觉结构，不负责显示队列、
自动关闭、遮罩、动画或 Android back。需要页面级浮层时使用 `PixelOverlayHost` 和
`PixelOverlayController`。

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

Toast("SAVED")

Snackbar(
    message = "QUEUED",
    action = TextButton(text = "UNDO", onPressed = { undo() }),
)
```

`Dialog` 的 `actions` 会排列在内容下方并右对齐；`ConfirmDialog` 是受控组合组件，
取消/确认后的关闭策略由回调显式处理；`Toast` 支持多行文本；`Snackbar` 的 `action`
由调用方传入任意 widget，常用 `TextButton`。

`ModalBarrier` 是填满父级 `Stack` 的遮罩，可选点击关闭；它不接管 back、焦点锁定、
overlay 生命周期或动画。`ToastQueue` 渲染 `PixelToastQueueController` 的队首消息；
控制器只保存队列，不内置自动超时。

```kotlin
val queue = PixelToastQueueController()

PixelOverlayHost(
    controller = overlay,
    child = Stack(
        children = listOf(
            AppRoot(),
            ModalBarrier(dismissible = true, onDismiss = { closeDialog() }),
            ToastQueue(controller = queue),
        ),
    ),
)

queue.enqueue("SAVED")
queue.dismissCurrent()
```

`Popover`、`Menu`、`Dropdown` 和 `Tooltip` 都是受控组件。展开状态、选中值、关闭时机和定位 offset
由调用方维护；组件不做 hover、自动测量、屏幕边缘避让或全局 overlay 管理。

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
val form = FormController()
val nameField = FormFieldState("")
val nameInput = TextEditingController()
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
                        placeholder = field.errorText ?: "NAME",
                        onChanged = { field.setValue(it) },
                    )
                },
            ),
            OutlinedButton(text = "SAVE", onPressed = { form.validate() }),
        ),
    ),
)
```

## 7. API 速查

### Host API

| API | 用途 | 关键参数 |
|---|---|---|
| `createPixelHostSetup` | 创建默认 Android 宿主 | `context`、`hostView`、`config` |
| `PixelHostSetup` | 宿主装配结果 | `rootView`、`hostView`、`textInputBridge` |
| `PixelHostSetupConfig` | 宿主配置 | 背景、字体、手势策略、滚动物理、根内容 |
| `PixelHostProfilePreference` | 点阵显示偏好 | `dotSizePx`、`pixelShape` |
| `PixelHostView` | Android 像素宿主 | `setContent`、背景、格栅、insets、debug stats |

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
| `ListTile` | 列表行 | `title`、`subtitle`、`leading`、`trailing`、`onTap` |
| `SelectionList` | 受控单选列表 | `items`、`selectedIndex`、`onSelected`、`itemLabel` |
| `OptionList` | 字符串选项列表 | `options`、`selectedIndex`、`onSelected` |
| `SectionList` | 分组列表布局 | `sections`、`itemSpacing`、`sectionSpacing` |
| `SectionListSection` | 分组列表区段数据 | `children`、`header`、`footer` |
| `ValueAdjuster` | 减 / 值 / 加调节器 | `valueText`、`onDecrease`、`onIncrease` |
| `Stepper` | 整数范围步进器 | `value`、`range`、`step`、`onChanged` |
| `Checkbox` | 复选框 | `checked`、`onChanged`、`enabled` |
| `Switch` | 开关 | `checked`、`onChanged`、`enabled` |
| `Slider` | 水平滑块 | `value`、`onDrag`、`onRelease` |
| `Tabs` | 标签页按钮组 | `labels`、`selectedIndex`、`onSelected` |
| `SegmentedControl` | 分段选择 | `labels`、`selectedIndex`、`onSelected` |
| `ShortcutHint` | 快捷键提示文本 | `shortcut`、`label` |
| `Slidable` | 可滑出操作面板的行容器 | `child`、`startActionPane`、`endActionPane` |
| `Dialog` | 居中对话框 | `title`、`content`、`actions` |
| `ConfirmDialog` | 确认对话框组合组件 | `title`、`message`、`onConfirm`、`onCancel` |
| `Toast` | 中央短提示 | `message` |
| `ToastQueue` | 队首 toast 渲染器 | `controller` |
| `PixelToastQueueController` | toast 队列状态 | `enqueue`、`dismissCurrent`、`dismiss` |
| `PixelToastQueueItem` | toast 队列条目 | `id`、`message`、`fillColor`、`textStyle` |
| `Snackbar` | 底部/容器内提示条 | `message`、`action` |
| `ModalBarrier` | Stack 内模态遮罩 | `color`、`dismissible`、`onDismiss` |
| `Popover` | 受控弹出层 | `anchor`、`content`、`expanded`、`contentOffset` |
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
不会自动完成刷新或创建隐藏状态。

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
| `RefreshIndicator` | 下拉刷新包装 | `child`、`state`、`controller`、`onRefresh` |
| `SwipeRefreshScaffold` | 带上下栏的下拉刷新骨架 | `body`、`state`、`controller`、`onRefresh` |
| `CustomScrollView` | sliver 滚动容器 | `slivers`、`state`、`controller` |
| `SliverList` | eager sliver list | `items` |
| `SliverListBuilder` | lazy sliver list | `itemCount`、`itemExtent` 或 `estimatedItemExtent` |
| `SliverPinnedHeader` | 固定头部 | `child` |
| `SliverAppBar` | 可折叠头部 | `expandedHeight`、`collapsedHeight`、`floating`、`snap`、`stretch` |

### 输入、表单和焦点

| API | 用途 | 关键参数 |
|---|---|---|
| `TextField` | 文本输入 | `state`、`controller`、`placeholder`、`enabled`、`readOnly`、`minLines`、`maxLines` |
| `TextFieldStyle` | 输入框样式 | 边框、光标、选区、composition、文本样式 |
| `Form` | 表单容器 | `controller`、`child` |
| `FormField` | 表单字段 | `controller`、`state`、`fieldId`、`validator`、`builder` |
| `FormController` | 表单状态 | `validate`、`validateAsync`、`submit`、`reset` |
| `FormFieldState` | 单字段值和错误 | `value`、`errorText` |
| `FocusNode` | 单点焦点 | `requestFocus`、`clearFocus` |
| `FocusScope` / `FocusScopeNode` | 焦点域 | 方向遍历、IME next |
| `FocusTraversalGroup` | 局部焦点遍历策略域 | `child`、`traversalPolicy`、`node` |

`FocusTraversalGroup` 适合给一块控件设置局部遍历策略；默认内部持有 `FocusScopeNode`，
需要跨页面保存焦点状态时再把 node 提升到业务层。`ShortcutHint` 只展示快捷键提示，
实际按键处理仍放在 `Focus(onKeyEvent = ...)` 或宿主级分发里。

### 输入法与 TextField 宿主契约

默认 Android `PixelTextInputBridge` 使用隐藏 `EditText` 接入系统 IME。普通 Activity 通过
`createPixelHostSetup` 接入时不需要手动处理输入法；自定义宿主才需要实现
`PixelHostBridge.showTextInput`、`PixelHostBridge.updateTextInput` 和
`PixelHostBridge.hideTextInput`。

| 契约 | 行为 |
|---|---|
| `PixelInputType` | 映射到 Android 输入面板类型，包括文本、ASCII、数字、邮箱、电话、URL 和密码 |
| `TextInputAction` / `PixelTextInputAction` | 映射到 IME action，包括 `DONE`、`NEXT`、`GO`、`SEARCH`、`SEND` |
| `PixelHostView.updateFocusedTextInput` | 宿主把文本、selection 和 composition 同步回当前聚焦的 `TextField` |
| `PixelHostView.submitFocusedTextInput` | 宿主触发提交；所有 action 都会触发 `onSubmitted`，`NEXT` 会额外发起焦点遍历 |
| `readOnly` | 宿主回传文本会被忽略；`COPY` / `SELECT_ALL` 这类只读编辑动作仍可使用 |

`PixelTextInputBridge` 只在焦点目标或 editor config 改变时重启 IME；普通文本、selection 和
composition 更新会走 `updateTextInput`，避免输入过程中反复重建键盘。

### 剪贴板与文本编辑动作

默认 Android `PixelTextInputBridge` 会把 `PixelHostBridge.readClipboardText` /
`writeClipboardText` 接到系统 `ClipboardManager`。页面或宿主可以通过
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

自定义宿主只需要实现 `PixelHostBridge.readClipboardText` 和
`PixelHostBridge.writeClipboardText`；不支持剪贴板时保留默认实现即可。

### Haptic

默认 Android `PixelTextInputBridge` 会把 `PixelHapticType.TAP` /
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

`perform` 返回 `false` 表示当前宿主没有提供 `PixelHostBridge`。内置长按手势会自动触发
`LONG_PRESS`，普通点击不自动触发，避免替业务决定交互强度。

### Controller

| Controller | State | 用途 |
|---|---|---|
| `ScrollController` / `PixelListController` | `PixelListState` | List/Grid/ScrollView/CustomScrollView 滚动 |
| `PageController` / `PixelPagerController` | `PixelPagerState` | PageView 翻页 |
| `TextEditingController` / `PixelTextFieldController` | `PixelTextFieldState` | TextField 文本、光标、选区 |
| `PixelRefreshIndicatorController` | `PixelRefreshIndicatorState` | RefreshIndicator 状态 |
| `PixelAnimationController` | controller 自身 | 动画进度 |

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
| `PixelNavigatorState` | push/pop/replace/popToRoot |
| `PixelRoute` | route 定义 |
| `PixelRouteTransition` | None/Fade/SlideHorizontal/SlideVertical |
| `PixelRouteTransitionBuilder` | 自定义 transition |
| `PixelNavigatorSnapshot` | route stack 保存 |
| `PixelDeepLink` | deep link 解析 |
| `PixelDeepLinkResolver` | deep link 到 route stack |
| `PixelRouteScrollRestoration` | route 内滚动位置恢复 |
| `PixelListSavedState` | 列表/网格滚动位置保存 |
| `PixelPagerSavedState` | PageView 当前页保存 |
| `PixelTextFieldSavedState` | TextField 文本和选区保存 |

### 动画

| API | 用途 |
|---|---|
| `PixelTickerProvider` | ticker 创建与生命周期 |
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

### 调试组件

| API | 用途 |
|---|---|
| `PixelDebugOverlay` | 显示 FPS、帧时间、target 统计 |
| `PixelInspectorPanel` | 展示 element/render/semantics/target 树 |
| `PixelInspectorBoundsOverlay` | 在画面上绘制 target bounds |
| `PixelHostView.frameStatsObserver` | 监听帧统计 |
| `PixelHostView.inspect()` | 采样 frame、target、element、render、semantics 诊断快照 |
| `PixelHostView.dumpElementTree()` | dump element tree |
| `PixelHostView.dumpRenderTree()` | dump render tree |
| `PixelHostView.dumpSemanticsTree()` | dump semantics tree |

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
    { "id": "runner", "path": "sprites/runner.png" }
  ],
  "spriteSheets": [
    {
      "id": "runnerRun",
      "path": "sprites/runner.sheet.json",
      "bitmap": "runner"
    }
  ]
}
```

字段约束：

- `bitmaps[].id` 是资源唯一标识，不能为空；`bitmaps[].path` 是调用方资源路径，不能为空。
- `spriteSheets[].id` 不能为空；`spriteSheets[].path` 指向 sprite sheet JSON；`spriteSheets[].bitmap` 必须引用同一 manifest 中已声明的 bitmap id。
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
      "binary": "glyphpacks/ui8/glyphs.bin"
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

`PixelResourceCache` 是一个轻量内存缓存，用于 SDK consumer 在 app 生命周期内复用已加载的 `PixelBitmap` 和 `PixelSpriteSheet`。它不拥有 Android 资源句柄，也不做后台清理。

key 行为：

- key 由调用方提供，必须非空白。
- bitmap 和 sprite sheet 使用独立命名空间；同一个 key 可以同时缓存一张 bitmap 和一个 sprite sheet。
- `remove(key)` 会同时移除该 key 下的 bitmap 和 sprite sheet；如果 key 不存在，不增加 `removeCount`。

读取行为：

- `getBitmap(key) { ... }` 和 `getSpriteSheet(key) { ... }` 命中时直接返回已缓存对象，不执行 loader。
- 未命中时执行 loader，并把返回对象按 key 保存。
- key 校验发生在 loader 之前，空白 key 不会触发资源加载。

clear 行为：

- `clear()` 只清空当前缓存条目，不重置命中/未命中统计。
- 只有缓存非空时才增加 `clearCount`；对空缓存重复调用不会增加计数。

snapshot 行为：

- `snapshot()` 返回当前条目数量、命中/未命中次数、remove 次数和 clear 次数。
- snapshot 是统计快照，不包含缓存对象引用，不估算内存字节数。

memory 边界：

- cache 持有强引用，没有容量限制，也没有 LRU 驱逐。
- 调用方必须在资源包切换、页面销毁、低内存回调或主题/字体资源变化时主动调用 `remove(key)` 或 `clear()`。
- 如果需要按字节数或帧数做淘汰，应在 SDK consumer 层包装 `PixelResourceCache`，不要依赖 engine 自动释放。

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
- 文本输入：`enterText`、`composeText`、`updateComposition`、`submitTextInput`、`performTextEditAction` 覆盖输入法 composition、提交、复制、剪切、粘贴和全选。
- 键盘：`pressKey` 向当前 focus tree 发送按键，适合验证 focus 和快捷键路径。
- 调试树：`dumpElementTree`、`dumpRenderTree`、`dumpSemanticsTree` 用于定位 widget / render / semantics 状态。
- 生命周期：测试结束后调用 `dispose`，清理 runtime、手动帧调度器和 focus 状态。

像素验证：

- `tester.pixelAt(x, y)`
- `tester.hasPixel(color)`
- `tester.dumpPixelsAsAscii()`

边界：

- `PixelTester` 是 JVM 内离屏测试工具，用于验证 widget、layout、输入分发、滚动和像素输出。
- 它不启动 Android `Activity`，不覆盖真实 `PixelHostView` 接线、Android IME、系统剪贴板、无障碍服务和设备 GPU/刷新率行为。
- 宿主接线使用 host 单测和 SDK consumer smoke 验证；真实设备交互、性能和渲染节奏使用 `pixel-device-smoke.sh` 与 `pixel-perf-smoke.sh` 验证。
- golden 文本快照应优先覆盖小尺寸、确定性 UI；复杂动效只断言关键状态和收敛结果。

常用验证：

```bash
./gradlew :pixel-engine:testDebugUnitTest :pixel-engine:assembleDebug :pixel-demo:assembleDebug --no-daemon
./tools/pixel-release-check.sh
./tools/pixel-sdk-consumer-smoke.sh
```

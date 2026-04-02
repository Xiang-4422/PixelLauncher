# 像素 UI 引擎组件接入与使用指南（第一版）

这份文档的目标很直接：

> 让后续业务模块可以把 `pixel-ui` 当成一个可直接依赖的库来接入，而不是必须先读完全部源码。

当前这份指南基于仓库里的真实实现编写，适用于：

- `:pixel-core`
- `:pixel-ui`
- `:pixel-demo`

不适用于当前的 `:app`，因为 Launcher 还没有开始迁移到新框架。

---

## 1. 当前推荐依赖方式

如果新建一个业务模块，并希望直接搭建像素页面，当前推荐只依赖 `:pixel-ui`：

```kotlin
dependencies {
    implementation(project(":pixel-ui"))
}
```

原因是：

- `pixel-ui` 已经通过 `api(project(":pixel-core"))` 暴露 `pixel-core`
- 页面层通常不应该直接长期操作 `PixelBuffer`
- 大多数业务页面只需要 `PixelHostView + Widget + 公开组件 + 状态控制器`

只有在你明确要做以下事情时，才建议直接依赖 `:pixel-core`：

- 自定义字形包或文本栅格器
- 自己写更底层的像素显示宿主
- 做不经过 `pixel-ui` 组件树的纯底层实验

### 当前模块基础要求

- `minSdk = 24`
- Java / Kotlin 目标版本为 11
- Android `compileSdk` 当前为 36

参考文件：

- [pixel-core/build.gradle.kts](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/build.gradle.kts)
- [pixel-ui/build.gradle.kts](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/build.gradle.kts)

---

## 2. 最小接入链路

一个最小可运行页面，当前需要这几步：

1. 创建 `PixelHostView`
2. 设置 `screenProfile`
   或者优先设置 `profilePreference`
3. 设置 `palette`
4. 可选设置 `textRasterizer`
5. 可选设置 `themeData`
6. 用 `setContent { ... }` 提供组件树
6. 如果页面里有 `TextField`，优先使用默认的 `PixelTextInputBridge`

### 最小示例

```kotlin
val hostView = PixelHostView(this).apply {
    profilePreference = PixelHostProfilePreference(
        dotSizePx = 8,
    )
    setPalette(PixelPalette.terminalGreen())
    themeData = ThemeData(
        textStyle = TextStyle.Accent,
        buttonStyle = ButtonStyle.Accent,
    )
    setContent {
        Column(
            modifier = PixelModifier.Empty.fillMaxSize().padding(4),
            spacing = 4,
            children = listOf(
                Text("HELLO PIXEL"),
                OutlinedButton(
                    text = "CLICK",
                    onPressed = { requestRender() },
                    modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                ),
            ),
        )
    }
}

setContentView(hostView)
```

关键类型：

- [PixelHostView.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/PixelHostView.kt)
- [PixelHostProfilePreference.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/PixelHostProfilePreference.kt)
- [PixelThemeData.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/PixelThemeData.kt)
- [ScreenProfileFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/main/kotlin/com/purride/pixelcore/ScreenProfileFactory.kt)
- [PixelPalette.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/main/kotlin/com/purride/pixelcore/PixelPalette.kt)

---

## 3. 页面应该怎么组织

当前推荐的页面组织方式是：

- 页面状态放在宿主或页面层
- 组件树通过 `setContent { ... }` 返回
- 交互通过控制器或 `onClick` 修改状态
- 修改状态后调用 `hostView.requestRender()`

### 当前推荐结构

```kotlin
class ExampleActivity : AppCompatActivity() {

    private lateinit var hostView: PixelHostView
    private var counter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hostView = PixelHostView(this).apply {
            screenProfile = ScreenProfileFactory.create(
                widthPx = resources.displayMetrics.widthPixels,
                heightPx = resources.displayMetrics.heightPixels,
                dotSizePx = 8,
            )
            setPalette(PixelPalette.terminalGreen())
            setContent { renderScene() }
        }

        setContentView(hostView)
    }

    private fun renderScene(): Widget {
        return Column(
            modifier = PixelModifier.Empty.fillMaxSize().padding(4),
            spacing = 4,
            children = listOf(
                Text("COUNT $counter"),
                OutlinedButton(
                    text = "PLUS",
                    onPressed = {
                        counter += 1
                        hostView.requestRender()
                    },
                    modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                ),
            ),
        )
    }
}
```

### 当前不推荐的写法

- 在 `setContent` 内每次新建宿主级对象
- 在组件内部偷偷直接改外部状态但不触发 `requestRender()`
- 业务层直接长期绘制 `PixelBuffer`

特别注意：

> `PixelPagerState`、`PixelListState`、`PixelTextFieldState` 这类状态对象，必须在 `content lambda` 外稳定持有，不要在每次重绘时重新创建。

这是当前这套框架最重要的使用约束之一。

如果页面已经开始出现大量重复的 `TextStyle / ButtonStyle / TextFieldStyle / Container` 视觉配置，
当前推荐先收成轻量主题对象：

```kotlin
val pageTheme = ThemeData(
    textStyle = TextStyle.Accent,
    buttonStyle = ButtonStyle.Accent,
    disabledButtonStyle = ButtonStyle(
        fillTone = PixelTone.OFF,
        borderTone = PixelTone.ACCENT,
        textStyle = TextStyle(tone = PixelTone.OFF),
    ),
    textFieldStyle = TextFieldStyle(
        borderTone = PixelTone.ACCENT,
        focusedBorderTone = PixelTone.ACCENT,
        textStyle = TextStyle.Accent,
        placeholderStyle = TextStyle.Default,
    ),
    readOnlyTextFieldStyle = TextFieldStyle(
        borderTone = PixelTone.ACCENT,
        focusedBorderTone = PixelTone.ACCENT,
        readOnlyBorderTone = PixelTone.ACCENT,
        textStyle = TextStyle.Accent,
        placeholderStyle = TextStyle.Default,
    ),
    containerStyle = ContainerStyle(
        fillTone = PixelTone.OFF,
        borderTone = PixelTone.ACCENT,
        alignment = Alignment.CENTER,
    ),
)
```

当前这一层主题已经能统一收这些默认分支：

- `textStyle`
- `accentTextStyle`
- `buttonStyle`
- `accentButtonStyle`
- `disabledButtonStyle`
- `textFieldStyle`
- `readOnlyTextFieldStyle`
- `disabledTextFieldStyle`
- `containerStyle`
- `accentContainerStyle`

如果一整段子树都共享同一套默认样式，当前推荐直接用 `Theme(data, child)` 包起来，
而不是给每个 `Text / Container / OutlinedButton / TextField` 都重复传一次 `theme`：

```kotlin
Theme(
    data = pageTheme,
    child = Column(
        children = listOf(
            Container(
                width = 32,
                height = 12,
                child = Center(child = Text("THEMED")),
            ),
            OutlinedButton(
                text = "CONFIRM",
                onPressed = { hostView.requestRender() },
            ),
        ),
    ),
)
```

当前更推荐把“同一块表单区”或“同一块操作区”作为 `Theme(...)` 的边界，
而不是整页所有组件都塞进一个非常大的主题里。这样主题作用范围更清晰，
后面做局部强调区、局部反色区也更容易管理。

如果整页大部分组件都共享同一套默认主题，当前还可以直接把它挂在宿主上：

```kotlin
hostView.themeData = pageTheme
```

这样 `setContent { ... }` 返回的整棵页面树都会自动吃到这套默认主题。  
推荐用法是：

- 整页统一默认风格：用 `hostView.themeData`
- 某一块表单区或操作区局部覆盖：用 `Theme(data, child)`
- 某个单独组件临时覆写：继续直接传 `theme = ...`

---

## 4. 基础组件使用方式

当前对外主推的是 Flutter 风格公开组件名。旧的 `PixelText`、`PixelButton`、`PixelList`、`PixelPager` 等还存在，但现在主要作为兼容层保留，不建议新页面继续直接使用。

### 4.1 文本

使用 [Text](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/FlutterWidgetAliases.kt)：

```kotlin
Text("DEFAULT")

Text(
    data = "ACCENT",
    style = TextStyle.Accent,
)
```

如果只是单个组件临时使用页面主题，也可以直接把默认文本样式交给 `ThemeData`：

```kotlin
Text(
    data = "THEMED",
    theme = pageTheme,
)
```

如果要切换文字风格，可以通过 [PixelTextStyle.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/PixelTextStyle.kt) 指定，公开层推荐直接使用 `TextStyle`：

- `tone`
- `textRasterizer`
- `lineSpacing`

示例：

```kotlin
Text(
    data = "中文标题",
    style = TextStyle(
        tone = PixelTone.ACCENT,
        textRasterizer = titleRasterizer,
    ),
)
```

如果需要长文案换行或省略，当前 `Text` 已支持最小可用参数：

```kotlin
Text(
    data = "这是一段用于验证中文换行与省略的像素文本内容。",
    softWrap = true,
    maxLines = 3,
    overflow = TextOverflow.ELLIPSIS,
)
```

当前规则是：

- 默认还是单行 `softWrap = false`
- `maxLines` 至少为 1
- `overflow` 当前支持 `CLIP` 和 `ELLIPSIS`

### 4.2 表面容器

使用 [DecoratedBox](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/FlutterWidgetAliases.kt)：

```kotlin
DecoratedBox(
    modifier = PixelModifier.Empty.fillMaxWidth().height(16),
    fillTone = PixelTone.OFF,
    borderTone = PixelTone.ACCENT,
    child = Text("CARD"),
)
```

适合做：

- 卡片
- 边框块
- 面板
- 输入框或按钮的基础底座

如果页面只是想表达一个“带尺寸、边框、内边距”的常用容器，公开层现在更推荐直接用 `Container`：

```kotlin
Container(
    width = 32,
    height = 18,
    padding = EdgeInsets.all(3),
    fillTone = PixelTone.OFF,
    borderTone = PixelTone.ACCENT,
    child = Center(
        child = Text("CARD"),
    ),
)
```

如果传入 `theme = pageTheme`，并且没有显式再传 `style`，`Container` 会直接使用
`ThemeData.containerStyle` 作为默认视觉样式。

但更推荐的写法，还是把整段区域包到 `Theme(data, child)` 里，让容器自动吃到默认样式。

如果需要表达容器外边距，当前 `Container` 也支持 `margin`：

```kotlin
Container(
    width = 32,
    height = 18,
    margin = EdgeInsets.only(top = 2),
    fillTone = PixelTone.OFF,
    borderTone = PixelTone.ACCENT,
    child = Text("CARD"),
)
```

### 4.3 间距包装

当前公开层已经补了 `EdgeInsets`，推荐优先用它表达 Flutter 风格内边距：

```kotlin
Padding(
    padding = EdgeInsets.symmetric(horizontal = 4, vertical = 2),
    child = Text("PADDED"),
)
```

### 4.4 布局容器

当前可用容器包括：

- `Row`
- `Column`
- `Align`
- `Center`
- `DecoratedBox`

示例：

```kotlin
Row(
    modifier = PixelModifier.Empty.fillMaxWidth().height(18),
    spacing = 2,
    mainAxisAlignment = MainAxisAlignment.CENTER,
    crossAxisAlignment = CrossAxisAlignment.CENTER,
    children = listOf(
        Text("LEFT"),
        Text("RIGHT"),
    ),
)
```

当前支持：

- `spacing`
- 主轴排布 `START / CENTER / END`
- 交叉轴对齐 `START / CENTER / END`
- `Modifier.weight(...)`

### 4.5 按钮

使用 [OutlinedButton](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/FlutterWidgetAliases.kt)：

```kotlin
OutlinedButton(
    text = "SUBMIT",
    onPressed = { hostView.requestRender() },
    modifier = PixelModifier.Empty.fillMaxWidth().height(14),
    enabled = true,
)
```

同样地，如果按钮处在统一页面主题里，也可以直接写：

```kotlin
OutlinedButton(
    text = "THEMED",
    onPressed = { hostView.requestRender() },
    theme = pageTheme,
)
```

如果是整段按钮区都共享同一套主题，优先使用上面的 `Theme(data, child)` 包装方式。

当前按钮视觉风格用 `ButtonStyle` 控制：

- `Default`
- `Accent`
- `Disabled`

如果想写成更贴近 Flutter 的禁用语义，当前也支持直接传 `onPressed = null`：

```kotlin
OutlinedButton(
    text = "DISABLED",
    onPressed = null,
)
```

---

## 5. 滚动与分页

### 5.1 `PageView`

当前分页使用：

- [PixelPagerState.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/state/PixelPagerState.kt)
- [PixelPagerController.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/state/PixelPagerController.kt)
- [FlutterControllerAliases.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/FlutterControllerAliases.kt)
- [FlutterControllerExtensions.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/FlutterControllerExtensions.kt)
- [PageView](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/FlutterWidgetAliases.kt)
- [PageViewBuilder](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/FlutterWidgetAliases.kt)

推荐写法：

```kotlin
private val pagerController = PageController()
private val pagerState = pagerController.create(
    pageCount = 3,
    currentPage = 0,
    axis = Axis.HORIZONTAL,
)
```

然后在页面里使用：

```kotlin
PageView(
    axis = Axis.HORIZONTAL,
    state = pagerState,
    controller = pagerController,
    onPageChanged = { page ->
        currentPage = page
        hostView.requestRender()
    },
    modifier = PixelModifier.Empty.fillMaxSize(),
    pages = listOf(
        Text("PAGE 1"),
        Text("PAGE 2"),
        Text("PAGE 3"),
    ),
)
```

当前支持：

- 横向分页
- 纵向分页
- 拖拽翻页
- 阈值翻页
- 速度翻页
- 分页吸附
- `onPageChanged(page)`
- `jumpToPage / nextPage / previousPage`
- `PageViewBuilder(itemCount, itemBuilder)`

### 5.2 `ListView`

当前列表使用：

- [PixelListState.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/state/PixelListState.kt)
- [PixelListController.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/state/PixelListController.kt)
- [FlutterControllerAliases.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/FlutterControllerAliases.kt)
- [FlutterControllerExtensions.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/FlutterControllerExtensions.kt)
- [ListView](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/FlutterWidgetAliases.kt)
- [ListViewBuilder](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/FlutterWidgetAliases.kt)
- [ListViewSeparated](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/FlutterWidgetAliases.kt)

推荐写法：

```kotlin
private val listController = ScrollController()
private val listState = listController.create()
```

```kotlin
ListView(
    state = listState,
    controller = listController,
    modifier = PixelModifier.Empty.fillMaxWidth().height(40),
    spacing = 3,
    items = List(8) { index ->
        OutlinedButton(
            text = "ITEM ${index + 1}",
            onPressed = { hostView.requestRender() },
            modifier = PixelModifier.Empty.fillMaxWidth().height(14),
        )
    },
)
```

当前支持：

- 纵向单列滚动
- 基础惯性滚动
- 裁剪视口
- 程序化滚动到某一项
- 与外层分页做手势仲裁
- `ListViewBuilder(itemCount, itemBuilder)`
- `ListViewSeparated` 分隔列表
- `showItem / jumpToStart / jumpToEnd`

如果你要表达“列表项 + 分隔块”的模式，公开层现在可以直接用：

```kotlin
ListViewSeparated(
    itemCount = 5,
    state = listState,
    controller = listController,
    modifier = PixelModifier.Empty.fillMaxWidth().height(40),
    itemBuilder = { index ->
        OutlinedButton(
            text = "ITEM ${index + 1}",
            onPressed = { hostView.requestRender() },
            modifier = PixelModifier.Empty.fillMaxWidth().height(14),
        )
    },
    separatorBuilder = {
        SizedBox(height = 2)
    },
)
```

当前不支持：

- 回弹
- 多列或瀑布流
- 虚拟化

### 5.3 `SingleChildScrollView`

如果你不是要做“很多离散项”的列表，而是要做“一个很长的页面”，用 [SingleChildScrollView](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/FlutterWidgetAliases.kt)：

```kotlin
private val scrollController = ScrollController()
private val scrollState = scrollController.create()
```

```kotlin
SingleChildScrollView(
    state = scrollState,
    controller = scrollController,
    modifier = PixelModifier.Empty.fillMaxSize().padding(4),
    child = Column(
        modifier = PixelModifier.Empty.fillMaxWidth(),
        spacing = 4,
        children = longFormChildren,
    ),
)
```

适合：

- 表单页
- 说明页
- 信息详情页
- 混合组件长页面

当前 `SingleChildScrollView` 复用了同一套列表滚动控制器，因此也具备基础惯性滚动。

---

## 6. 文本输入接入方式

`TextField` 当前不是自己实现完整 IME，而是通过宿主桥接。

核心类型：

- [PixelTextField.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/PixelTextField.kt)
- [PixelTextFieldController.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/state/PixelTextFieldController.kt)
- [FlutterControllerAliases.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/FlutterControllerAliases.kt)
- [PixelHostBridge.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/PixelHostBridge.kt)

最小接法参考：

- [DemoSceneActivity.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-demo/src/main/kotlin/com/purride/pixeldemo/app/DemoSceneActivity.kt)

当前推荐优先使用默认桥接：

- [PixelTextInputBridge.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/PixelTextInputBridge.kt)

最小接法：

```kotlin
val hostView = PixelHostView(this)
val textInputBridge = PixelTextInputBridge(
    context = this,
    hostView = hostView,
)
hostView.hostBridge = textInputBridge

setContentView(
    FrameLayout(this).apply {
        addView(hostView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        addView(textInputBridge.inputView, FrameLayout.LayoutParams(1, WRAP_CONTENT))
    },
)
```

如果后面要接更复杂的 IME 行为，仍然可以自定义实现 `PixelHostBridge`。  
但对大多数普通页面来说，默认桥接已经足够把单行输入跑起来。

当前 `TextField` 还支持两项很实用的页面行为：

1. 程序化请求焦点
2. 提交动作回调
3. 实时文本变化回调
4. 禁用态
5. 只读态
6. 首次自动聚焦
7. 输入动作配置

示例：

```kotlin
private val textController = TextEditingController()
private val nameState = textController.create()

TextField(
    state = nameState,
    controller = textController,
    placeholder = "TYPE NAME",
    onChanged = { text ->
        hostView.requestRender()
    },
    onSubmitted = { text ->
        hostView.requestRender()
    },
)

OutlinedButton(
    text = "FOCUS NAME",
    onPressed = {
        textController.requestFocus(nameState)
        hostView.requestRender()
    },
)
```

当前控制器可用动作：

- `requestFocus(state)`
- `requestBlur(state)`
- `updateText(state, text, selectionStart, selectionEnd)`
- `setSelection(state, selectionStart, selectionEnd)`
- `clear(state)`
- `selectAll(state)`

当前输入事件可用回调：

- `onChanged(text)`
- `onSubmitted(text)`

禁用态示例：

```kotlin
TextField(
    state = secondaryState,
    controller = secondaryController,
    placeholder = "WAIT PRIMARY",
    enabled = primaryController.text.isNotEmpty(),
)
```

禁用时当前行为：

- 不导出输入目标，不能聚焦和编辑
- 不显示输入光标
- 使用 `TextFieldStyle` 中的禁用边框、禁用文本和禁用占位样式

只读态示例：

```kotlin
TextField(
    state = readOnlyState,
    controller = controller,
    placeholder = "READ ONLY",
    readOnly = true,
)
```

只读时当前行为：

- 仍然可以聚焦，页面能感知焦点状态
- 不弹出键盘
- 不接受宿主输入回写
- 不显示输入光标

自动聚焦示例：

```kotlin
TextField(
    state = primaryState,
    controller = controller,
    placeholder = "TYPE PRIMARY",
    autofocus = true,
    textInputAction = TextInputAction.NEXT,
    onSubmitted = {
        controller.requestFocus(secondaryState)
        hostView.requestRender()
    },
)
```

自动聚焦当前规则：

- 只在该状态对象第一次出现在页面里时自动聚焦一次
- 不会因为后续 `requestRender()` 重绘反复抢焦点

输入动作示例：

```kotlin
TextField(
    state = secondaryState,
    controller = controller,
    placeholder = "TYPE SECONDARY",
    textInputAction = TextInputAction.DONE,
    onSubmitted = { text ->
        submit(text)
    },
)
```

当前支持的动作：

- `TextInputAction.DONE`
- `TextInputAction.NEXT`
- `TextInputAction.GO`
- `TextInputAction.SEND`

当前 `TextField` 仍然是单行显示，超出可用宽度的文本和占位内容会按输入框内容区宽度裁剪，不会直接画出边框。

---

## 7. 中文与自定义字体接入

如果页面需要中文，当前推荐直接通过 `PixelTextRasterizer` 注入。

相关底座：

- [PixelTextRasterizer.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/main/kotlin/com/purride/pixelcore/PixelTextRasterizer.kt)
- [PixelGlyphPackAssetLoader.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/main/kotlin/com/purride/pixelcore/PixelGlyphPackAssetLoader.kt)
- [PixelFontEngine.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/main/kotlin/com/purride/pixelcore/PixelFontEngine.kt)

当前 demo 的真实参考实现：

- [DemoTextRasterizers.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-demo/src/main/kotlin/com/purride/pixeldemo/app/DemoTextRasterizers.kt)

推荐模式是：

- 宿主先准备一套默认 `PixelTextRasterizer`
- `PixelHostView.textRasterizer` 作为全局默认字体
- 某些节点如果需要不同文本风格，再通过 `TextStyle(textRasterizer = ...)` 局部覆盖

---

## 8. 当前最重要的使用约束

### 8.1 状态对象要稳定持有

不要这样写：

```kotlin
setContent {
    val controller = ScrollController()
    val state = controller.create()
    ...
}
```

因为这样每次重绘都会重建状态，滚动、分页、输入都会丢。

正确写法是：

```kotlin
private val controller = ScrollController()
private val state = controller.create()
```

然后在 `setContent` 里只消费。

### 8.2 修改页面状态后要触发重新渲染

当前第一版是轻量运行时，不是完整响应式框架。  
因此业务状态变化后，通常要显式调用：

```kotlin
hostView.requestRender()
```

### 8.3 优先在 `pixel-demo` 验证新组件

不要直接把新组件先塞进 `:app`。  
当前正确顺序是：

1. 先在 `pixel-ui` 实现
2. 再在 `pixel-demo` 做真实页面验证
3. 验证稳定后，再讨论迁移到 Launcher

---

## 9. 当前适合作为“直接依赖开发”的能力范围

如果现在就要基于这套引擎开发一个新页面，当前比较适合的页面类型是：

- 设置页
- 信息展示页
- 静态卡片页
- 简单表单页
- 轻量列表页
- 轻量分页页

当前还不建议直接拿来做的页面类型：

- 强依赖复杂滚动物理效果的页面
- 大数据量虚拟化列表
- 复杂多行文本阅读页
- 富文本或复杂编辑器

---

## 10. 推荐阅读顺序

如果要真正开始基于这个库开发，建议按这个顺序读：

1. [像素 UI 引擎当前进度与接手建议（第一版）](./像素UI引擎当前进度与接手建议-第一版.md)
2. [像素 UI 引擎架构与实施计划（第一版）](./像素UI引擎架构与实施计划-第一版.md)
3. [DemoSceneActivity.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-demo/src/main/kotlin/com/purride/pixeldemo/app/DemoSceneActivity.kt)
4. [DemoScenes.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-demo/src/main/kotlin/com/purride/pixeldemo/app/DemoScenes.kt)

如果要看“真实能跑的页面怎么写”，优先看 `pixel-demo`，因为它已经是当前这套引擎最接近正式使用方式的样板。

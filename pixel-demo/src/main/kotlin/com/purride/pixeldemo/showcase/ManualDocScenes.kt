package com.purride.pixeldemo.showcase

import com.purride.pixelcore.*
import com.purride.pixelui.*
import com.purride.pixelui.advanced.*
import com.purride.pixelui.animation.IntOffset
import com.purride.pixelui.animation.IntTween
import com.purride.pixelui.animation.PixelAnimationController
import com.purride.pixelui.state.*
import com.purride.pixelui.widgets.animated.*
import com.purride.pixelui.widgets.navigation.*
import com.purride.pixelui.widgets.navigation.PixelNavigator as NavWidget
import com.purride.pixelui.widgets.navigation.PixelRoute as NavRoute
import com.purride.pixelui.widgets.navigation.PixelRouteScrollRestoration as NavRouteScrollRestoration
import com.purride.pixelui.widgets.navigation.PixelRouteTransition as NavTransition
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoCategory
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.Accent
import com.purride.pixeldemo.scaffold.Blue
import com.purride.pixeldemo.scaffold.ComponentShowcaseScaffold
import com.purride.pixeldemo.scaffold.Cyan
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.Green
import com.purride.pixeldemo.scaffold.Muted
import com.purride.pixeldemo.scaffold.Pink
import com.purride.pixeldemo.scaffold.Purple
import com.purride.pixeldemo.scaffold.Yellow
import com.purride.pixeldemo.scaffold.apiTags
import com.purride.pixeldemo.scaffold.samplePanel
import com.purride.pixeldemo.scaffold.sectionTitle
import kotlin.time.Duration.Companion.milliseconds

open class ManualDocScene(
    override val id: String,
    override val title: String,
    override val summary: String,
    override val category: DemoCategory,
    override val apis: Set<String>,
    private val bodyBuilder: (DemoEnv) -> Widget,
) : DemoScene {
    override val tags: Set<String> = setOf("manual", category.id) + apis.map { it.lowercase() }
    override val isFullScreen: Boolean = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = bodyBuilder(env))
}

object ManualQuickStartScene : ManualDocScene(
    id = "manual_quick_start",
    title = "快速接入",
    summary = "模块依赖、常用 import 与子包入口",
    category = DemoCatalog.quickStart,
    apis = setOf("pixel-engine", "PixelColor", "com.purride.pixelui.*"),
    bodyBuilder = { _ ->
        docBody(
            sectionTitle("Gradle"),
            codeBlock("include(\":pixel-engine\")", "implementation(project(\":pixel-engine\"))"),
            sectionTitle("Imports"),
            codeBlock(
                "import com.purride.pixelcore.PixelColor",
                "import com.purride.pixelui.*",
                "import com.purride.pixelui.widgets.animated.*",
                "import com.purride.pixelui.widgets.navigation.*",
                "import com.purride.pixelui.advanced.*",
            ),
        )
    },
)

object ManualMinimalActivityScene : ManualDocScene(
    id = "manual_minimal_activity",
    title = "最小 Activity",
    summary = "createPixelHostSetup + Center/Text 的最小宿主",
    category = DemoCatalog.minimalActivity,
    apis = setOf("createPixelHostSetup", "PixelHostSetupConfig", "PixelHostView", "Center", "Text"),
    bodyBuilder = { _ ->
        docBody(
            sectionTitle("Preview"),
            samplePanel(
                title = "Center(child = Text(...))",
                color = Cyan,
                child = Container(
                    height = 42,
                    borderColor = Cyan,
                    child = Center(child = Text("HELLO PIXEL", style = TextStyle(color = Cyan))),
                ),
            ),
            codeBlock(
                "val setup = createPixelHostSetup(context, PixelHostSetupConfig(content = {",
                "    Center(child = Text(\"HELLO PIXEL\"))",
                "}))",
                "setContentView(setup.rootView)",
            ),
        )
    },
)

object ManualHostConfigScene : ManualDocScene(
    id = "manual_host_config",
    title = "宿主配置",
    summary = "profile、背景、字体、手势、滚动和 frameScheduler",
    category = DemoCatalog.hostConfig,
    apis = setOf(
        "PixelHostSetupConfig",
        "PixelHostProfilePreference",
        "PixelHostView",
        "PixelFrameScheduler",
        "PagerGesturePolicy",
        "PixelScrollPhysics",
    ),
    bodyBuilder = { env ->
        val preference = PixelHostProfilePreference(dotSizePx = 8, pixelShape = PixelShape.SQUARE)
        docBody(
            sectionTitle("Runtime host"),
            samplePanel(
                title = "PixelHostView snapshot",
                color = Yellow,
                child = Column(
                    children = listOf(
                        metric("dotSize", "${preference.dotSizePx}"),
                        metric("shape", "${preference.pixelShape}"),
                        metric("current", "${env.currentSettings.dotSizePx}px ${env.currentSettings.pixelShape}"),
                        metric("ticker", "${env.vsync.activeTickerCount}"),
                    ),
                    spacing = 1,
                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                ),
            ),
            apiList("Config keys", listOf("bezelColor", "textDirection", "pagerGesturePolicy", "nestedScrollPolicy", "scrollPhysics", "frameScheduler", "content")),
        )
    },
)

object ManualStateValueNotifierScene : ManualDocScene(
    id = "manual_state_value_notifier",
    title = "ValueNotifier",
    summary = "轻量 listenable 状态与 ValueListenableBuilder",
    category = DemoCatalog.stateManagement,
    apis = setOf("ValueNotifier", "ValueListenableBuilder", "Listenable", "ValueListenable"),
    bodyBuilder = { _ -> docBody(sectionTitle("ValueNotifier"), ValueNotifierExample()) },
)

object ManualStateStatefulWidgetScene : ManualDocScene(
    id = "manual_state_stateful_widget",
    title = "StatefulWidget",
    summary = "封装可复用状态组件",
    category = DemoCatalog.stateManagement,
    apis = setOf("StatefulWidget", "State", "setState", "BuildContext"),
    bodyBuilder = { _ -> docBody(sectionTitle("StatefulWidget"), CounterExample()) },
)

object ManualStateControllerStateScene : ManualDocScene(
    id = "manual_state_controller_state",
    title = "Controller + State",
    summary = "滚动、分页、输入等 controller/state 配对",
    category = DemoCatalog.stateManagement,
    apis = setOf("ScrollController", "PixelListState", "PageController", "PixelPagerState", "TextEditingController", "PixelTextFieldState"),
    bodyBuilder = { _ -> docBody(sectionTitle("Controller + State"), controllerStatePreview()) },
)

object ManualThemeScene : ManualDocScene(
    id = "manual_theme",
    title = "颜色、字体和主题",
    summary = "PixelColor、TextStyle、显式主题对象和字体优先级",
    category = DemoCatalog.theme,
    apis = setOf("PixelColor", "TextStyle", "PixelTextStyle", "PixelTextRasterizer", "DefaultTextRasterizer", "PixelBitmapFont"),
    bodyBuilder = { _ ->
        docBody(
            samplePanel(
                title = "Explicit theme values",
                color = Accent,
                child = Column(
                    children = listOf(
                        Container(
                            padding = EdgeInsets.all(3),
                            fillColor = PixelColor.Black,
                            borderColor = Accent,
                            child = Text("HOME", style = TextStyle(color = Green)),
                        ),
                        Row(
                            children = listOf(
                                swatch("bg", PixelColor.Black),
                                swatch("grid", PixelColor.fromRgb(8, 37, 13)),
                                swatch("text", Green),
                                swatch("accent", Accent),
                            ),
                            spacing = 2,
                        ),
                    ),
                    spacing = 3,
                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                ),
            ),
            apiList("Font priority", listOf("TextStyle.textRasterizer", "DefaultTextRasterizer", "PixelHostView.textRasterizer", "PixelBitmapFont.Default")),
        )
    },
)

object ManualPatternPanelScene : ManualDocScene(
    id = "manual_pattern_panel",
    title = "基础面板",
    summary = "Container + Column 构建像素状态面板",
    category = DemoCatalog.patterns,
    apis = setOf("Container", "Column", "EdgeInsets", "Text", "PixelColor"),
    bodyBuilder = { _ ->
        docBody(
            samplePanel(
                title = "基础面板",
                color = Green,
                child = Container(
                    width = 80,
                    height = 32,
                    padding = EdgeInsets.all(2),
                    borderColor = PixelColor.White,
                    child = Column(
                        spacing = 2,
                        children = listOf(
                            Text("STATUS", style = TextStyle(color = PixelColor.White)),
                            Text("READY", color = Green),
                        ),
                    ),
                ),
            ),
        )
    },
)

object ManualPatternLongListScene : ManualDocScene(
    id = "manual_pattern_long_list",
    title = "长列表",
    summary = "ListViewBuilder 必须提供 itemExtent 或 estimatedItemExtent",
    category = DemoCatalog.patterns,
    apis = setOf("ListViewBuilder", "ScrollController", "PixelListState", "itemExtent", "estimatedItemExtent"),
    bodyBuilder = { _ -> docBody(longListPreview(itemCount = 120, label = "app")) },
)

object ManualPatternPagerScene : ManualDocScene(
    id = "manual_pattern_pager",
    title = "分页",
    summary = "PageController + PageView 构建水平页面",
    category = DemoCatalog.patterns,
    apis = setOf("PageController", "PageView", "PixelPagerState", "Axis.HORIZONTAL"),
    bodyBuilder = { _ -> docBody(pagerPreview()) },
)

object ManualPatternTextFieldScene : ManualDocScene(
    id = "manual_pattern_text_field",
    title = "输入框",
    summary = "TextEditingController、TextField、输入动作和 onChanged",
    category = DemoCatalog.patterns,
    apis = setOf("TextField", "TextEditingController", "PixelTextFieldState", "PixelInputType", "TextInputAction"),
    bodyBuilder = { _ -> docBody(textFieldPreview()) },
)

object ManualPatternFormScene : ManualDocScene(
    id = "manual_pattern_form",
    title = "表单",
    summary = "FormController、FormFieldState、validator 和提交校验",
    category = DemoCatalog.patterns,
    apis = setOf("Form", "FormController", "FormField", "FormFieldState", "FormValidator", "TextField"),
    bodyBuilder = { _ -> docBody(formPreview()) },
)

object ManualApiHostScene : ManualDocScene(
    id = "manual_api_host",
    title = "Host API",
    summary = "Android 宿主、配置和帧统计入口",
    category = DemoCatalog.apiReference,
    apis = setOf("createPixelHostSetup", "PixelHostSetup", "PixelHostSetupConfig", "PixelHostProfilePreference", "PixelHostView"),
    bodyBuilder = { env ->
        docBody(
            apiList("Host API", listOf("createPixelHostSetup", "PixelHostSetup", "PixelHostSetupConfig", "PixelHostProfilePreference", "PixelHostView")),
            PixelDebugOverlay(
                stats = PixelHostFrameStats(deltaMs = 16, fpsAvg = 60f, paintTimeNanos = 1_200_000, frameCount = 42),
                activeTickerCount = env.vsync.activeTickerCount,
            ),
        )
    },
)

object ManualApiFoundationScene : ManualDocScene(
    id = "manual_api_foundation",
    title = "基础类型",
    summary = "Widget、BuildContext、State、Builder、Listenable 和 AsyncBuilder",
    category = DemoCatalog.apiReference,
    apis = setOf("Widget", "BuildContext", "StatefulWidget", "State", "StatelessWidget", "InheritedWidget", "InheritedNotifier", "Builder", "StatefulBuilder", "ListenableBuilder", "ValueListenableBuilder", "AsyncBuilder"),
    bodyBuilder = { _ -> docBody(foundationPreview()) },
)

object ManualApiLayoutScene : ManualDocScene(
    id = "manual_api_layout",
    title = "布局组件",
    summary = "Row、Column、Wrap、Stack、Flex、约束和安全区",
    category = DemoCatalog.apiReference,
    apis = setOf("Row", "Column", "Wrap", "Stack", "Positioned", "PositionedDirectional", "PositionedFill", "Padding", "PaddingDirectional", "Align", "AlignDirectional", "Center", "SizedBox", "Expanded", "Flexible", "Spacer", "AspectRatio", "ConstrainedBox", "FittedBox", "SafeArea"),
    bodyBuilder = { _ -> docBody(layoutApiPreview()) },
)

object ManualApiContentScene : ManualDocScene(
    id = "manual_api_content_decoration",
    title = "内容与装饰",
    summary = "Text、RichText、Container、DecoratedBox、Image、Sprite 和 Icon",
    category = DemoCatalog.apiReference,
    apis = setOf("Text", "RichText", "Container", "ContainerDirectional", "DecoratedBox", "Opacity", "ClipRect", "Transform.translate", "Image", "Sprite", "Icon"),
    bodyBuilder = { _ -> docBody(contentApiPreview()) },
)

object ManualApiGraphicsScene : ManualDocScene(
    id = "manual_api_graphics",
    title = "图形原语",
    summary = "Line、Circle、Polygon、Path、CustomPaint",
    category = DemoCatalog.apiReference,
    apis = setOf("Line", "Circle", "Polygon", "Path", "CustomPaint", "PixelCanvas", "PixelPath", "PixelShapeStyle"),
    bodyBuilder = { _ -> docBody(graphicsApiPreview()) },
)

object ManualApiControlsScene : ManualDocScene(
    id = "manual_api_controls_feedback",
    title = "选择、反馈和组合组件",
    summary = "按钮、选择控件、反馈组件和页面脚手架",
    category = DemoCatalog.apiReference,
    apis = setOf("OutlinedButton", "ButtonStyle", "PixelButtonStyle", "ListTile", "Checkbox", "Switch", "Slider", "Tabs", "SegmentedControl", "Dialog", "Toast", "Snackbar", "ProgressBar", "ActivityIndicator", "Badge", "Divider", "Gap", "AppScaffold"),
    bodyBuilder = { _ -> docBody(controlsApiPreview()) },
)

object ManualApiScrollScene : ManualDocScene(
    id = "manual_api_scroll_pager",
    title = "滚动、网格和分页",
    summary = "列表、网格、分页、滚动条、刷新和 sliver",
    category = DemoCatalog.apiReference,
    apis = setOf("ListView", "ListViewBuilder", "ListViewSeparated", "ListViewSeparatedBuilder", "GridView", "GridViewBuilder", "SingleChildScrollView", "PageView", "PageViewBuilder", "Scrollbar", "RefreshIndicator", "CustomScrollView", "SliverList", "SliverListBuilder", "SliverPinnedHeader", "SliverAppBar", "PixelSliverList", "PixelSliverListBuilder", "PixelSliverPinnedHeader", "PixelSliverAppBar"),
    bodyBuilder = { _ -> docBody(scrollApiPreview()) },
)

object ManualApiInputFocusScene : ManualDocScene(
    id = "manual_api_input_form_focus",
    title = "输入、表单和焦点",
    summary = "TextField、Form、FormField、FocusNode 与 FocusScope",
    category = DemoCatalog.apiReference,
    apis = setOf("TextField", "TextFieldStyle", "Form", "FormField", "FormController", "FormFieldState", "FocusNode", "FocusScope", "FocusScopeNode", "PixelFocusDirection", "ReadingOrderFocusTraversalPolicy"),
    bodyBuilder = { _ -> docBody(inputFocusApiPreview()) },
)

object ManualApiControllerScene : ManualDocScene(
    id = "manual_api_controller",
    title = "Controller",
    summary = "List/Pager/TextField/Refresh/Animation 控制器",
    category = DemoCatalog.apiReference,
    apis = setOf("ScrollController", "PixelListController", "PixelListState", "PixelListSavedState", "PageController", "PixelPagerController", "PixelPagerState", "PixelPagerSavedState", "PixelPagerSnapshot", "TextEditingController", "PixelTextFieldController", "PixelTextFieldState", "PixelRefreshIndicatorController", "PixelRefreshIndicatorState", "PixelAnimationController", "jumpToPage", "nextPage", "showItem", "jumpToStart", "jumpToEnd"),
    bodyBuilder = { env -> docBody(controllerApiPreview(env)) },
)

object ManualApiNavigationScene : ManualDocScene(
    id = "manual_api_navigation",
    title = "导航",
    summary = "PixelNavigator、route stack、transition、snapshot 和 deep link",
    category = DemoCatalog.apiReference,
    apis = setOf("PixelNavigator", "PixelNavigatorState", "PixelRoute", "PixelRouteTransition", "PixelRouteTransitionBuilder", "PixelNavigatorSnapshot", "PixelDeepLink", "PixelDeepLinkResolver", "PixelRouteScrollRestoration"),
    bodyBuilder = { env -> docBody(navigationApiPreview(env)) },
)

object ManualApiAnimationScene : ManualDocScene(
    id = "manual_api_animation",
    title = "动画",
    summary = "Ticker、AnimationController、Tween、Animated* 和 AnimatedSprite",
    category = DemoCatalog.apiReference,
    apis = setOf("PixelTickerProvider", "PixelAnimationController", "CurvedAnimation", "Curves", "Curve", "Interval", "Tween", "IntTween", "EdgeInsetsTween", "OffsetTween", "PixelColorTween", "PixelGradientTween", "TweenAnimationBuilder", "AnimatedBuilder", "AnimatedContainer", "AnimatedOpacity", "AnimatedPadding", "AnimatedAlign", "AnimatedPositioned", "AnimatedSwitcher", "AnimatedSprite"),
    bodyBuilder = { env -> docBody(animationApiPreview(env)) },
)

object ManualApiDebugScene : ManualDocScene(
    id = "manual_api_debug",
    title = "调试组件",
    summary = "Debug overlay、Inspector panel、bounds overlay 和 frame stats",
    category = DemoCatalog.apiReference,
    apis = setOf("PixelDebugOverlay", "PixelInspectorPanel", "PixelInspectorBoundsOverlay", "PixelHostView.frameStatsObserver", "PixelHostView.dumpElementTree"),
    bodyBuilder = { env -> docBody(debugApiPreview(env)) },
)

object ManualApiPixelcoreScene : ManualDocScene(
    id = "manual_api_pixelcore",
    title = "pixelcore 常用类型",
    summary = "颜色、缓冲、位图、资源、屏幕档位和字体引擎",
    category = DemoCatalog.apiReference,
    apis = setOf("PixelColor", "PixelBuffer", "PixelBitmap", "PixelBitmapAssetLoader", "PixelBitmapResourceLoader", "PixelSpriteSheet", "PixelSpriteSheetJsonLoader", "PixelResourceManifestJsonLoader", "PixelResourceCache", "ScreenProfile", "ScreenProfileFactory", "PixelGridGeometryResolver", "PixelTextRasterizer", "PixelGlyphPackAssetLoader", "PixelFontEngine"),
    bodyBuilder = { _ -> docBody(pixelcoreApiPreview()) },
)

object ManualCustomRenderObjectScene : ManualDocScene(
    id = "manual_custom_render_object",
    title = "自定义 RenderObject",
    summary = "advanced alias、layout、paint、hitTest 和脏标记规则",
    category = DemoCatalog.customRenderObject,
    apis = setOf("PixelLeafRenderObjectWidget", "PixelSingleChildRenderObjectWidget", "PixelMultiChildRenderObjectWidget", "PixelRenderObject", "PixelRenderBox", "PixelRenderConstraints", "PixelRenderSize", "PixelPaintContext", "PixelHitTestResult", "markNeedsLayout", "markNeedsPaint"),
    bodyBuilder = { _ -> docBody(sectionTitle("HollowSquareWidget"), HollowSquareWidget(side = 20, color = Accent), apiList("Rules", listOf("unchanged fields: skip dirty mark", "size change: markNeedsLayout", "paint-only change: markNeedsPaint", "paint hot path: avoid allocation"))) },
)

object ManualTestingScene : ManualDocScene(
    id = "manual_testing",
    title = "测试",
    summary = "PixelTester DSL、finder 和常用验证命令",
    category = DemoCatalog.testing,
    apis = setOf("PixelTester", "pumpWidget", "tap", "drag", "enterText", "pumpAndSettle", "find.byText", "find.byType", "find.byKey"),
    bodyBuilder = { _ ->
        docBody(
            codeBlock(
                "val tester = PixelTester()",
                "tester.pumpWidget(OutlinedButton(text = \"OK\", onPressed = {}))",
                "tester.tap(find.byText(\"OK\"))",
                "tester.drag(find.byKey(\"list\"), dx = 0, dy = -12)",
                "tester.enterText(find.byKey(\"field\"), \"hello\")",
            ),
            apiList("Gradle", listOf(":pixel-engine:testDebugUnitTest", ":pixel-engine:assembleDebug", ":pixel-demo:assembleDebug")),
        )
    },
)

private fun docBody(vararg children: Widget): Widget =
    Column(
        children = children.toList(),
        spacing = 4,
        mainAxisSize = MainAxisSize.MIN,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
    )

private fun codeBlock(vararg lines: String): Widget =
    samplePanel(
        title = "Code",
        color = Muted,
        child = Column(
            children = lines.map { line ->
                Text(line, style = TextStyle(color = PixelColor.White), softWrap = false, overflow = PixelTextOverflow.CLIP)
            },
            spacing = 1,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        ),
    )

private fun apiList(title: String, apis: List<String>): Widget =
    samplePanel(title = title, color = Purple, child = apiTags(apis, color = Purple))

private fun metric(label: String, value: String): Widget =
    Row(
        children = listOf(
            Container(width = 44, child = Text(label, style = TextStyle(color = Muted))),
            Text(value, style = TextStyle(color = PixelColor.White), softWrap = false, overflow = PixelTextOverflow.ELLIPSIS),
        ),
        spacing = 2,
        crossAxisAlignment = CrossAxisAlignment.CENTER,
    )

private fun swatch(label: String, color: PixelColor): Widget =
    Row(
        children = listOf(Container(width = 8, height = 8, fillColor = color, borderColor = PixelColor.White), Text(label, style = TextStyle(color = color))),
        spacing = 1,
        crossAxisAlignment = CrossAxisAlignment.CENTER,
    )

private class ValueNotifierExample(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = ValueNotifierState()

    private class ValueNotifierState : State<ValueNotifierExample>() {
        private val counter = ValueNotifier(0)

        override fun build(context: BuildContext): Widget =
            samplePanel(
                title = "ValueListenableBuilder",
                color = Green,
                child = Column(
                    children = listOf(
                        ValueListenableBuilder(counter) { _, value -> Text("COUNT $value", style = TextStyle(color = Green)) },
                        OutlinedButton(text = "+1", onPressed = { counter.value += 1 }, borderColor = Green),
                    ),
                    spacing = 3,
                    crossAxisAlignment = CrossAxisAlignment.START,
                ),
            )
    }
}

private class CounterExample(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = CounterState()

    private class CounterState : State<CounterExample>() {
        private var count = 0

        override fun build(context: BuildContext): Widget =
            samplePanel(
                title = "setState",
                color = Cyan,
                child = OutlinedButton(
                    text = "COUNT $count",
                    onPressed = { setState { count += 1 } },
                    borderColor = Cyan,
                ),
            )
    }
}

private fun controllerStatePreview(): Widget {
    val listController = ScrollController()
    val listState = listController.create()
    return samplePanel(
        title = "List controller",
        color = Pink,
        child = Container(
            height = 40,
            borderColor = Pink,
            child = ListViewBuilder(
                itemCount = 24,
                itemBuilder = { index -> Text("ITEM $index", style = TextStyle(color = PixelColor.White)) },
                state = listState,
                controller = listController,
                itemExtent = 8,
            ),
        ),
    )
}

private fun longListPreview(itemCount: Int, label: String): Widget {
    val controller = ScrollController()
    val state = controller.create()
    return samplePanel(
        title = "ListViewBuilder(itemExtent)",
        color = Pink,
        child = Container(
            height = 44,
            borderColor = Pink,
            child = ListViewBuilder(
                itemCount = itemCount,
                itemBuilder = { index -> Text("$label $index", style = TextStyle(color = if (index % 8 == 0) Accent else PixelColor.White)) },
                state = state,
                controller = controller,
                itemExtent = 8,
            ),
        ),
    )
}

private fun pagerPreview(): Widget {
    val controller = PageController()
    val state = controller.create(pageCount = 3, axis = Axis.HORIZONTAL)
    return samplePanel(
        title = "PageView",
        color = Blue,
        child = Container(
            height = 34,
            borderColor = Blue,
            child = PageView(
                axis = Axis.HORIZONTAL,
                controller = controller,
                state = state,
                pages = listOf(page("HOME", Cyan), page("DRAWER", Purple), page("SETTINGS", Yellow)),
            ),
        ),
    )
}

private fun page(label: String, color: PixelColor): Widget =
    Container(fillColor = color, child = Center(child = Text(label, style = TextStyle(color = PixelColor.Black))))

private fun textFieldPreview(): Widget {
    val input = TextEditingController()
    val state = input.create(initialText = "")
    return samplePanel(
        title = "TextField",
        color = Cyan,
        child = TextField(
            state = state,
            controller = input,
            placeholder = "SEARCH",
            inputType = PixelInputType.TEXT,
            textInputAction = TextInputAction.SEARCH,
            onChanged = {},
            borderColor = Cyan,
        ),
    )
}

private fun formPreview(): Widget {
    val form = FormController()
    val field = FormFieldState("")
    val input = TextEditingController()
    val inputState = input.create(initialText = "")
    return samplePanel(
        title = "Form",
        color = Green,
        child = Form(
            controller = form,
            child = Column(
                children = listOf(
                    FormField(
                        state = field,
                        fieldId = "name",
                        validator = { value -> if (value.isBlank()) "REQUIRED" else null },
                    ) { _, formField ->
                        TextField(
                            state = inputState,
                            controller = input,
                            placeholder = formField.errorText ?: "NAME",
                            onChanged = { formField.setValue(it) },
                            borderColor = if (formField.hasError) Pink else Green,
                        )
                    },
                    OutlinedButton(text = "SAVE", onPressed = { form.validate() }, borderColor = Green),
                ),
                spacing = 3,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            ),
        ),
    )
}

private fun foundationPreview(): Widget {
    val notifier = ValueNotifier(1)
    val source = pixelAsyncSourceOf("READY")
    return samplePanel(
        title = "Builders",
        color = Accent,
        child = Column(
            children = listOf(
                Builder { Text("Builder / WidgetBuilder", style = TextStyle(color = Accent)) },
                ListenableBuilder(notifier) { Text("ListenableBuilder", style = TextStyle(color = Green)) },
                ValueListenableBuilder(notifier) { _, value -> Text("ValueListenableBuilder $value", style = TextStyle(color = Cyan)) },
                StatefulBuilder { _, setLocalState ->
                    OutlinedButton(text = "StatefulBuilder", onPressed = { setLocalState {} }, borderColor = Yellow)
                },
                AsyncBuilder(source = source) { _, snapshot -> Text("AsyncBuilder $snapshot", style = TextStyle(color = Pink)) },
            ),
            spacing = 2,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        ),
    )
}

private fun layoutApiPreview(): Widget =
    samplePanel(
        title = "Layout matrix",
        color = Yellow,
        child = SafeArea(
            child = Column(
                children = listOf(
                    Row(
                        children = listOf(
                            Expanded(child = Container(height = 8, fillColor = Cyan)),
                            Flexible(child = Container(height = 8, fillColor = Green)),
                            Spacer(),
                            SizedBox(width = 12, height = 8, child = Container(fillColor = Pink)),
                        ),
                        spacing = 2,
                    ),
                    Wrap(
                        spacing = 2,
                        runSpacing = 1,
                        children = listOf(
                            Padding(all = 1, child = Text("Padding", style = TextStyle(color = Accent))),
                            PaddingDirectional(
                                padding = EdgeInsetsDirectional.only(start = 2, end = 1),
                                child = Text("Directional", style = TextStyle(color = Cyan)),
                            ),
                            Align(alignment = Alignment.CENTER, child = Text("Align", style = TextStyle(color = Green))),
                            AlignDirectional(alignment = AlignmentDirectional.CENTER_START, child = Text("AlignDirectional", style = TextStyle(color = Pink))),
                        ),
                    ),
                    Container(
                        height = 28,
                        borderColor = Purple,
                        child = Stack(
                            children = listOf(
                                PositionedFill(child = Container(borderColor = Muted)),
                                Positioned(left = 3, top = 3, child = Container(width = 20, height = 8, fillColor = Blue)),
                                PositionedDirectional(start = 28, top = 11, child = Container(width = 20, height = 8, fillColor = Green)),
                            ),
                        ),
                    ),
                    Row(
                        children = listOf(
                            AspectRatio(aspectRatio = 2f, child = Container(fillColor = Accent)),
                            ConstrainedBox(constraints = PixelBoxConstraints(minWidth = 18, maxWidth = 18, minHeight = 8, maxHeight = 8), child = Container(fillColor = Cyan)),
                            FittedBox(child = Text("FIT", style = TextStyle(color = Yellow))),
                            Center(child = Text("CENTER", style = TextStyle(color = Muted))),
                        ),
                        spacing = 2,
                    ),
                ),
                spacing = 3,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            ),
        ),
    )

private fun contentApiPreview(): Widget {
    val bitmap = demoBitmap()
    val sheet = demoSpriteSheet()
    return samplePanel(
        title = "Content",
        color = Cyan,
        child = Column(
            children = listOf(
                Text("Text", color = PixelColor.White),
                RichText(
                    spans = listOf(
                        PixelTextSpan("Rich", style = TextStyle(color = Accent)),
                        PixelTextSpan("Text", style = TextStyle(color = Green)),
                    ),
                ),
                ContainerDirectional(
                    paddingDirectional = EdgeInsetsDirectional.only(start = 3, end = 1, top = 1, bottom = 1),
                    borderColor = Cyan,
                    child = DecoratedBox(
                        fillColor = PixelColor.fromRgb(12, 18, 22),
                        borderColor = Blue,
                        child = Opacity(
                            opacity = 0.85f,
                            child = ClipRect(child = Transform.translate(offset = IntOffset(1, 0), child = Text("Decorated", color = Blue))),
                        ),
                    ),
                ),
                Row(
                    children = listOf(
                        Image(bitmap = bitmap),
                        Sprite(sheet = sheet, frameIndex = 0),
                        Icon(icon = PixelIconData(bitmap)),
                    ),
                    spacing = 4,
                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                ),
            ),
            spacing = 3,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        ),
    )
}

private fun graphicsApiPreview(): Widget =
    samplePanel(
        title = "Primitives",
        color = Purple,
        child = Row(
            children = listOf(
                Container(width = 26, height = 22, child = Line(0, 18, 24, 2, PixelShapeStyle(color = Accent, strokeWidth = 2))),
                Container(width = 22, height = 22, child = Circle(radius = 8, color = Cyan, filled = false)),
                Container(width = 28, height = 22, child = Polygon(points = listOf(PixelPoint(2, 18), PixelPoint(14, 2), PixelPoint(26, 18)), color = Green)),
                Container(
                    width = 28,
                    height = 22,
                    child = Path(
                        path = PixelPath(listOf(PixelPathCommand.MoveTo(PixelPoint(2, 18)), PixelPathCommand.QuadraticTo(PixelPoint(12, 2), PixelPoint(25, 18)))),
                        color = Pink,
                        strokeWidth = 2,
                    ),
                ),
                CustomPaint(width = 30, height = 22) {
                    drawRect(1, 1, 28, 20, PixelColor.White)
                    drawLine(4, 17, 25, 5, Yellow, strokeWidth = 2)
                },
            ),
            spacing = 2,
            crossAxisAlignment = CrossAxisAlignment.CENTER,
        ),
    )

private fun controlsApiPreview(): Widget {
    val style: PixelButtonStyle = ButtonStyle(fillColor = PixelColor.fromRgb(20, 16, 4), borderColor = Yellow, textStyle = TextStyle(color = Yellow))
    return samplePanel(
        title = "Controls",
        color = Green,
        child = Column(
            children = listOf(
                Row(
                    children = listOf(
                        OutlinedButton(text = "OK", onPressed = {}, style = style),
                        Badge(child = OutlinedButton(text = "MAIL", onPressed = {}, borderColor = Pink), label = Text("3", color = PixelColor.White)),
                        ActivityIndicator(frame = 1, color = Accent),
                    ),
                    spacing = 2,
                ),
                ListTile(title = Text("ListTile", color = Cyan), subtitle = Text("subtitle", color = Muted), trailing = Text(">", color = Accent), onTap = {}),
                Row(
                    children = listOf(
                        Checkbox(checked = true, onChanged = {}, activeColor = Green),
                        Switch(checked = true, onChanged = {}, activeColor = Green),
                        Slider(value = 0.56f, onDrag = {}, onRelease = {}, activeColor = Accent),
                    ),
                    spacing = 3,
                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                ),
                Tabs(labels = listOf("A", "B"), selectedIndex = 0, onSelected = {}),
                SegmentedControl(labels = listOf("DAY", "NIGHT"), selectedIndex = 1, onSelected = {}),
                ProgressBar(progress = 0.68f, width = 64, color = Accent),
                Dialog(
                    title = Text("Dialog", color = Yellow),
                    content = Text("content", color = PixelColor.White),
                    actions = listOf(OutlinedButton(text = "OK", onPressed = {}, borderColor = Yellow)),
                    borderColor = Yellow,
                ),
                Toast("Toast", textStyle = TextStyle(color = Green)),
                Snackbar(message = "Snackbar", action = OutlinedButton(text = "UNDO", onPressed = {}, borderColor = Accent), textStyle = TextStyle(color = PixelColor.White)),
                Divider(color = Muted),
                Gap(height = 2),
                Container(height = 28, borderColor = Blue, child = AppScaffold(title = Text("AppScaffold", color = Blue), body = Text("body", color = PixelColor.White), bottomBar = Text("bottom", color = Muted))),
            ),
            spacing = 3,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        ),
    )
}

private fun scrollApiPreview(): Widget {
    val listController = ScrollController()
    val listState = listController.create()
    val gridController = ScrollController()
    val gridState = gridController.create()
    val pagerController = PageController()
    val pagerState = pagerController.create(pageCount = 3)
    val refreshController = PixelRefreshIndicatorController()
    val refreshState = refreshController.create()
    return samplePanel(
        title = "Scroll surfaces",
        color = Pink,
        child = Column(
            children = listOf(
                Container(
                    height = 34,
                    borderColor = Pink,
                    child = Scrollbar(
                        state = listState,
                        thumbColor = Pink,
                        child = ListViewBuilder(
                            itemCount = 30,
                            itemBuilder = { Text("lazy $it", color = PixelColor.White) },
                            state = listState,
                            controller = listController,
                            itemExtent = 8,
                        ),
                    ),
                ),
                Container(
                    height = 28,
                    borderColor = Green,
                    child = GridViewBuilder(
                        itemCount = 16,
                        itemBuilder = { Container(fillColor = if (it % 2 == 0) Green else Blue) },
                        cellWidth = 12,
                        cellHeight = 8,
                        state = gridState,
                        controller = gridController,
                        spacing = 1,
                    ),
                ),
                Container(
                    height = 26,
                    borderColor = Cyan,
                    child = PageViewBuilder(
                        axis = Axis.HORIZONTAL,
                        controller = pagerController,
                        state = pagerState,
                        itemCount = 3,
                        itemBuilder = { page("P$it", listOf(Cyan, Purple, Yellow)[it]) },
                    ),
                ),
                Container(
                    height = 30,
                    borderColor = Accent,
                    child = RefreshIndicator(
                        state = refreshState,
                        controller = refreshController,
                        onRefresh = { refreshController.completeRefresh(refreshState) },
                        child = ListViewSeparatedBuilder(
                            itemCount = 8,
                            itemBuilder = { Text("sep $it", color = PixelColor.White) },
                            separatorBuilder = { Container(height = 1, fillColor = Muted) },
                            state = ScrollController().create(),
                            controller = ScrollController(),
                            itemExtent = 8,
                            separatorExtent = 1,
                        ),
                    ),
                ),
                Container(
                    height = 34,
                    borderColor = Blue,
                    child = CustomScrollView(
                        state = PixelListState(),
                        controller = ScrollController(),
                        slivers = listOf(
                            SliverAppBar(expandedHeight = 14, collapsedHeight = 8, child = Container(fillColor = Blue, child = Text("APP", color = PixelColor.Black))),
                            SliverPinnedHeader(child = Container(fillColor = Accent, child = Text("PIN", color = PixelColor.Black))),
                            SliverList(items = listOf(Text("one", color = PixelColor.White), Text("two", color = PixelColor.White))),
                            SliverListBuilder(itemCount = 6, itemBuilder = { Text("sliver $it", color = PixelColor.White) }, itemExtent = 8),
                        ),
                    ),
                ),
                SingleChildScrollView(state = PixelListState(), controller = ScrollController(), child = Text("SingleChildScrollView / ListView / GridView", color = Muted)),
                ListView(items = listOf(Text("ListView", color = Muted)), state = PixelListState(), controller = ScrollController()),
                GridView(items = listOf(Text("GridView", color = PixelColor.Black)), cellWidth = 32, cellHeight = 8, state = PixelListState(), controller = ScrollController()),
                ListViewSeparated(itemCount = 2, itemBuilder = { Text("eager $it", color = Muted) }, separatorBuilder = { Divider(color = Muted) }, state = PixelListState(), controller = ScrollController()),
            ),
            spacing = 3,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        ),
    )
}

private fun inputFocusApiPreview(): Widget {
    val node = FocusNode(debugLabel = "manual")
    return samplePanel(
        title = "Focus + Form",
        color = Cyan,
        child = FocusScope(
            node = FocusScopeNode(),
            traversalPolicy = ReadingOrderFocusTraversalPolicy,
            child = Column(
                children = listOf(
                    Focus(node = node, autofocus = true, child = Container(padding = EdgeInsets.all(2), borderColor = Cyan, child = Text("FocusNode focused=${node.isFocused}", color = Cyan))),
                    textFieldPreview(),
                    formPreview(),
                    Text("direction=${PixelFocusDirection.NEXT}", color = Muted),
                ),
                spacing = 3,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            ),
        ),
    )
}

private fun controllerApiPreview(env: DemoEnv): Widget {
    val scroll = ScrollController()
    val listState = scroll.create()
    val pager = PageController()
    val pagerState = pager.create(pageCount = 3)
    val input = TextEditingController()
    val inputState = input.create(initialText = "CTRL")
    val refresh = PixelRefreshIndicatorController()
    val refreshState = refresh.create()
    val animation = PixelAnimationController(duration = 300.milliseconds, vsync = env.vsync)
    val pageSnapshot = pager.snapshot(pagerState)
    return samplePanel(
        title = "Controller snapshot",
        color = Yellow,
        child = Column(
            children = listOf(
                metric("list", "offset=${listState.scrollOffsetPx.toInt()}"),
                metric("pager", "page=${pageSnapshot.anchorPage}"),
                metric("text", inputState.text),
                metric("refresh", "armed=${refreshState.isArmed} active=${refreshState.isRefreshing}"),
                metric("anim", "${(animation.value * 100).toInt()}%"),
                OutlinedButton(text = "NEXT", onPressed = { pager.nextPage(pagerState); scroll.jumpToStart(listState); scroll.jumpToEnd(listState); scroll.showItem(listState, 0) }, borderColor = Yellow),
            ),
            spacing = 2,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        ),
    )
}

private fun navigationApiPreview(env: DemoEnv): Widget {
    val snapshot = PixelNavigatorSnapshot(listOf("root", "details"))
    val deepLink = PixelDeepLink.parse("pixel://manual/details?id=7")
    return samplePanel(
        title = "Navigator",
        color = Blue,
        child = Column(
            children = listOf(
                Container(
                    height = 52,
                    borderColor = Blue,
                    child = NavWidget(
                        initialRoute = NavRoute(
                            name = "root",
                            transition = NavTransition.Fade,
                            builder = { context ->
                                Column(
                                    children = listOf(
                                        Text("route root", color = Blue),
                                        OutlinedButton(
                                            text = "PUSH",
                                            onPressed = { NavWidget.of(context).push(NavRoute(name = "details", transition = NavTransition.SlideHorizontal, builder = { Text("details", color = Green) })) },
                                            borderColor = Blue,
                                        ),
                                    ),
                                    spacing = 2,
                                )
                            },
                        ),
                        defaultTransition = NavTransition.SlideHorizontal,
                        transitionDuration = 150.milliseconds,
                        vsync = env.vsync,
                    ),
                ),
                metric("snapshot", snapshot.routeNames.joinToString(">")),
                metric("deeplink", "${deepLink.host}/${deepLink.pathSegments.joinToString("/")}"),
                metric("restored", NavRouteScrollRestoration::class.simpleName ?: "PixelRouteScrollRestoration"),
            ),
            spacing = 3,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        ),
    )
}

private fun animationApiPreview(env: DemoEnv): Widget =
    samplePanel(
        title = "Animated widgets",
        color = Accent,
        child = Column(
            children = listOf(
                AnimatedContainer(duration = 300.milliseconds, vsync = env.vsync, width = 70, height = 14, borderColor = Accent, child = AnimatedOpacity(opacity = 0.75f, duration = 300.milliseconds, vsync = env.vsync, child = Text("AnimatedContainer", color = Accent))),
                Container(width = 90, height = 18, borderColor = Blue, child = AnimatedAlign(alignment = Alignment.CENTER_END, duration = 300.milliseconds, vsync = env.vsync, child = AnimatedPadding(padding = EdgeInsets.all(2), duration = 300.milliseconds, vsync = env.vsync, child = Container(width = 12, height = 6, fillColor = Blue)))),
                Container(width = 90, height = 20, borderColor = Pink, child = Stack(children = listOf(AnimatedPositioned(duration = 300.milliseconds, vsync = env.vsync, left = 48, top = 6, child = Container(width = 18, height = 7, fillColor = Pink))))),
                AnimatedSwitcher(duration = 250.milliseconds, vsync = env.vsync, child = Text("AnimatedSwitcher", color = Green)),
                TweenAnimationBuilder(tween = IntTween(0, 80), duration = 300.milliseconds, vsync = env.vsync) { _, value -> ProgressBar(progress = value / 80f, width = 80, color = Yellow) },
                AnimatedBuilder(animation = PixelAnimationController(duration = 300.milliseconds, vsync = env.vsync)) { _, _ -> Text("AnimatedBuilder", color = Muted) },
                AnimatedSprite(sheet = demoSpriteSheet(), fps = 4, vsync = env.vsync),
                apiList("Tweens", listOf("CurvedAnimation", "Curves", "Curve", "Interval", "Tween", "EdgeInsetsTween", "OffsetTween", "PixelColorTween", "PixelGradientTween")),
            ),
            spacing = 3,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        ),
    )

private fun debugApiPreview(env: DemoEnv): Widget {
    val snapshot = PixelInspectorSnapshot(
        frameStats = PixelHostFrameStats(deltaMs = 16, fpsAvg = 60f, paintTimeNanos = 1_500_000, frameCount = 99),
        allocationSample = PixelInspectorAllocationSample(usedHeapBytes = 1024 * 512, totalHeapBytes = 1024 * 2048, maxHeapBytes = 1024 * 8192),
        targetCounts = PixelInspectorTargetCounts(click = 1, pager = 0, list = 1, scrollbar = 0, refresh = 0, textInput = 0, slider = 0, semantics = 1),
        targetSnapshots = listOf(PixelInspectorTargetSnapshot(PixelInspectorTargetKind.CLICK, left = 4, top = 4, width = 28, height = 12, detail = "button")),
        elementTree = "Root\n  DebugScene",
        renderTree = "RenderRoot\n  RenderBox",
        semanticsTree = "button Debug",
        hasPendingBuild = false,
        focusedTextInput = false,
        activePagerCount = 0,
        activeListCount = 1,
        activeSlider = false,
        activeScrollbar = false,
        activeRefresh = false,
    )
    return samplePanel(
        title = "Inspector",
        color = Purple,
        child = Column(
            children = listOf(
                PixelDebugOverlay(stats = snapshot.frameStats, inspector = snapshot, activeTickerCount = env.vsync.activeTickerCount),
                Container(width = 86, height = 28, borderColor = Purple, child = PixelInspectorBoundsOverlay(snapshot = snapshot, width = 86, height = 28)),
                PixelInspectorPanel(snapshot = snapshot, maxTreeLines = 4),
            ),
            spacing = 3,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        ),
    )
}

private fun pixelcoreApiPreview(): Widget {
    val bitmap = demoBitmap()
    val buffer = PixelBuffer(width = 8, height = 8)
    val cache = PixelResourceCache()
    cache.getBitmap("demo") { bitmap }
    val sheet = demoSpriteSheet()
    val atlas = PixelSpriteSheetJsonLoader.loadAtlas(spriteSheetJson, bitmap)
    val catalog = PixelResourceManifestJsonLoader.parseCatalog(resourceCatalogJson)
    val profile = ScreenProfileFactory.create(widthPx = 320, heightPx = 240, dotSizePx = 8)
    return samplePanel(
        title = "pixelcore",
        color = Green,
        child = Column(
            children = listOf(
                Row(children = listOf(Image(bitmap), Sprite(sheet, frameIndex = 0)), spacing = 4),
                metric("buffer", "${buffer.width}x${buffer.height}"),
                metric("cache", "bitmaps=${cache.snapshot().bitmapCount}"),
                metric("atlas", "frames=${atlas.frames.size}"),
                metric("catalog", "colors=${catalog.colors.size} fonts=${catalog.fonts.size}"),
                metric("profile", "${profile.logicalWidth}x${profile.logicalHeight}"),
                apiList("Loaders / fonts", listOf("PixelBitmapAssetLoader", "PixelBitmapResourceLoader", "PixelGridGeometryResolver", "PixelTextRasterizer", "PixelGlyphPackAssetLoader", "PixelFontEngine")),
            ),
            spacing = 3,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        ),
    )
}

private class HollowSquareWidget(
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

    override fun hitTest(localX: Int, localY: Int, result: PixelHitTestResult) {
        if (localX in 0 until size.width && localY in 0 until size.height) result.add(this)
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

private fun demoBitmap(): PixelBitmap {
    val pixels = IntArray(8 * 8) { index ->
        val x = index % 8
        val y = index / 8
        when {
            x == y || x + y == 7 -> Accent.argb
            (x + y) % 2 == 0 -> Cyan.argb
            else -> PixelColor.Transparent.argb
        }
    }
    return PixelBitmap(width = 8, height = 8, pixels = pixels)
}

private fun demoSpriteSheet(): PixelSpriteSheet {
    val bitmap = PixelBitmap(
        width = 16,
        height = 8,
        pixels = IntArray(16 * 8) { index ->
            val x = index % 16
            val y = index / 16
            when {
                x < 8 && y in 2..5 -> Green.argb
                x >= 8 && (x + y) % 2 == 0 -> Pink.argb
                else -> PixelColor.Transparent.argb
            }
        },
    )
    return PixelSpriteSheet(
        bitmap = bitmap,
        frames = listOf(
            PixelBitmapRegion(left = 0, top = 0, width = 8, height = 8),
            PixelBitmapRegion(left = 8, top = 0, width = 8, height = 8),
        ),
    )
}

private val spriteSheetJson = """
    {
      "version": 2,
      "bitmap": "demo",
      "scale": 1,
      "frames": [
        { "left": 0, "top": 0, "width": 8, "height": 8, "sourceWidth": 8, "sourceHeight": 8 }
      ]
    }
""".trimIndent()

private val resourceCatalogJson = """
    {
      "version": 2,
      "bitmaps": [{ "id": "demo", "path": "inline/demo.png" }],
      "spriteSheets": [{ "id": "demo_sheet", "path": "inline/demo.json", "bitmap": "demo" }],
      "colors": [{ "id": "accent", "value": "#FFB040" }],
      "fonts": [{ "id": "mono", "manifest": "fonts/mono.json", "binary": "fonts/mono.bin" }]
    }
""".trimIndent()

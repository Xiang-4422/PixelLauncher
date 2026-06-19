package com.purride.pixeldemo.showcase

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Focus
import com.purride.pixelui.FocusNode
import com.purride.pixelui.FocusScope
import com.purride.pixelui.Form
import com.purride.pixelui.FormController
import com.purride.pixelui.FormField
import com.purride.pixelui.FormFieldState
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelDebugOverlay
import com.purride.pixelui.PixelFocusDirection
import com.purride.pixelui.PixelHostFrameStats
import com.purride.pixelui.PixelInspectorBoundsOverlay
import com.purride.pixelui.PixelInspectorPanel
import com.purride.pixelui.PixelInspectorSnapshot
import com.purride.pixelui.PixelInspectorTargetCounts
import com.purride.pixelui.PixelInspectorTargetKind
import com.purride.pixelui.PixelInspectorTargetSnapshot
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.ReadingOrderFocusTraversalPolicy
import com.purride.pixelui.Row
import com.purride.pixelui.Semantics
import com.purride.pixelui.Stack
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.Accent
import com.purride.pixeldemo.scaffold.Blue
import com.purride.pixeldemo.scaffold.Cyan
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.Green
import com.purride.pixeldemo.scaffold.Muted
import com.purride.pixeldemo.scaffold.Pink
import com.purride.pixeldemo.scaffold.Purple
import com.purride.pixeldemo.scaffold.Yellow
import com.purride.pixeldemo.scaffold.samplePanel

val NavigationInputOfficialComponentScenes: List<DemoScene> = listOf(
    ComponentExampleScene("nav_focus_scope", "FocusScope", "焦点遍历范围和 traversal policy", DemoCatalog.input, setOf("component", "focus"), setOf("FocusScope", "ReadingOrderFocusTraversalPolicy", "PixelFocusDirection")) { FocusScopeOfficialBody() },
    ComponentExampleScene("nav_focus", "Focus", "单个可聚焦节点", DemoCatalog.input, setOf("component", "focus"), setOf("Focus", "FocusNode")) { FocusOfficialBody() },
    ComponentExampleScene("nav_form", "Form", "表单控制器和校验范围", DemoCatalog.input, setOf("component", "form"), setOf("Form", "FormController", "FormValidator")) { FormOfficialBody(showFieldOnly = false) },
    ComponentExampleScene("nav_form_field", "FormField", "表单字段状态和 validator", DemoCatalog.input, setOf("component", "formfield"), setOf("FormField", "FormFieldState")) { FormOfficialBody(showFieldOnly = true) },
    componentScene("nav_semantics", "Semantics", "为子树导出语义标签和角色", DemoCatalog.input, "Semantics", extraApis = setOf("PixelSemanticRole", "PixelSemanticsNode")) {
        Semantics(
            label = "demo button",
            role = PixelSemanticRole.BUTTON,
            child = Container(padding = EdgeInsets.all(2), borderColor = Purple, child = Text("SEMANTICS", style = TextStyle(color = Purple))),
        )
    },
)

val DebugOfficialComponentScenes: List<DemoScene> = listOf(
    componentScene("debug_overlay", "PixelDebugOverlay", "显示 FPS、帧耗时、Inspector 和 ticker 摘要", DemoCatalog.debug, "PixelDebugOverlay", extraApis = setOf("PixelHostFrameStats")) { env ->
        PixelDebugOverlay(stats = debugSnapshot().frameStats, inspector = debugSnapshot(), activeTickerCount = env.vsync.activeTickerCount)
    },
    componentScene("debug_inspector_panel", "PixelInspectorPanel", "交互式 inspector 信息面板", DemoCatalog.debug, "PixelInspectorPanel", extraApis = setOf("PixelInspectorSnapshot")) {
        PixelInspectorPanel(snapshot = debugSnapshot(), maxTreeLines = 4)
    },
    componentScene("debug_inspector_bounds_overlay", "PixelInspectorBoundsOverlay", "在画面上叠加 inspector target bounds", DemoCatalog.debug, "PixelInspectorBoundsOverlay", extraApis = setOf("PixelInspectorTargetSnapshot", "PixelInspectorTargetKind")) {
        Container(
            width = 96,
            height = 42,
            borderColor = Yellow,
            child = Stack(
                children = listOf(
                    Container(padding = EdgeInsets.all(2), child = Text("TARGETS", style = TextStyle(color = Muted))),
                    PixelInspectorBoundsOverlay(snapshot = debugSnapshot(), width = 96, height = 42),
                ),
            ),
        )
    },
)

private class FocusScopeOfficialBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = FocusScopeOfficialState()

    private class FocusScopeOfficialState : State<FocusScopeOfficialBody>() {
        private val one = FocusNode(debugLabel = "one")
        private val two = FocusNode(debugLabel = "two")
        private val scope = com.purride.pixelui.FocusScopeNode()

        override fun build(context: BuildContext): Widget =
            officialNavBody(
                title = "FocusScope",
                color = Cyan,
                child = FocusScope(
                    node = scope,
                    traversalPolicy = ReadingOrderFocusTraversalPolicy,
                    child = Column(
                        children = listOf(
                            focusBox("ONE", one, Cyan),
                            focusBox("TWO", two, Pink),
                            OutlinedButton(text = "NEXT", onPressed = { scope.focusInDirection(PixelFocusDirection.NEXT); setState {} }, borderColor = Accent),
                        ),
                        spacing = 3,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    ),
                ),
            )
    }
}

private class FocusOfficialBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = FocusOfficialState()

    private class FocusOfficialState : State<FocusOfficialBody>() {
        private val node = FocusNode(debugLabel = "single")
        override fun build(context: BuildContext): Widget =
            officialNavBody(
                title = "Focus",
                color = Blue,
                child = Column(
                    children = listOf(
                        Focus(node = node, child = focusBox("FOCUS TARGET", node, Blue)),
                        OutlinedButton(text = "REQUEST", onPressed = { node.requestFocus(); setState {} }, borderColor = Blue),
                    ),
                    spacing = 3,
                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                ),
            )
    }
}

private class FormOfficialBody(
    private val showFieldOnly: Boolean,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = FormOfficialState()

    private class FormOfficialState : State<FormOfficialBody>() {
        private val controller = FormController(
            validators = listOf { values ->
                if ((values["name"] as? String).orEmpty().length < 3) mapOf("name" to "min 3") else emptyMap()
            },
        )
        private val fieldState = FormFieldState("PX")
        private var last = "READY"

        override fun build(context: BuildContext): Widget {
            val field = FormField(
                state = fieldState,
                fieldId = "name",
                validator = { value -> if (value.length < 3) "min 3" else null },
            ) { _, state ->
                Container(
                    padding = EdgeInsets.all(2),
                    borderColor = if (state.hasError) Pink else Green,
                    child = Text("${state.value} ${state.errorText.orEmpty()}", style = TextStyle(color = if (state.hasError) Pink else Green)),
                )
            }
            val content = Column(
                children = listOf(
                    field,
                    Row(
                        children = listOf(
                            OutlinedButton(text = "FIX", onPressed = { fieldState.setValue("PIXEL"); last = "fixed"; setState {} }, borderColor = Green),
                            OutlinedButton(text = "VALID", onPressed = { last = "valid=${controller.validate()}"; setState {} }, borderColor = Accent),
                        ),
                        spacing = 2,
                    ),
                    Text(last, style = TextStyle(color = Muted)),
                ),
                spacing = 3,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
            return officialNavBody(
                title = if (widget.showFieldOnly) "FormField" else "Form",
                color = Green,
                child = if (widget.showFieldOnly) content else Form(controller = controller, child = content),
            )
        }
    }
}

private fun componentScene(
    id: String,
    title: String,
    summary: String,
    category: com.purride.pixeldemo.catalog.DemoCategory,
    api: String,
    extraApis: Set<String> = emptySet(),
    body: (DemoEnv) -> Widget,
): DemoScene =
    ComponentExampleScene(
        id = id,
        title = title,
        summary = summary,
        category = category,
        tags = setOf("component", title.lowercase()),
        apis = setOf(api) + extraApis,
        bodyBuilder = { env ->
            Column(
                children = listOf(
                    samplePanel(title = "Example", color = categoryColor(category.id), child = body(env)),
                ),
                spacing = 4,
                mainAxisSize = MainAxisSize.MIN,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        },
    )

private fun officialNavBody(title: String, color: PixelColor, child: Widget): Widget =
    Column(
        children = listOf(
            samplePanel(title = "Example", color = color, child = child),
        ),
        spacing = 4,
        mainAxisSize = MainAxisSize.MIN,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
    )

private fun focusBox(label: String, node: FocusNode, color: PixelColor): Widget =
    Focus(
        node = node,
        child = Container(
            padding = EdgeInsets.all(2),
            borderColor = if (node.isFocused) Accent else color,
            child = Text("$label focused=${node.isFocused}", style = TextStyle(color = if (node.isFocused) Accent else color)),
        ),
    )

private fun debugSnapshot(): PixelInspectorSnapshot {
    val targets = listOf(
        PixelInspectorTargetSnapshot(PixelInspectorTargetKind.CLICK, left = 8, top = 8, width = 28, height = 12, detail = "button"),
        PixelInspectorTargetSnapshot(PixelInspectorTargetKind.LIST, left = 42, top = 12, width = 36, height = 22, detail = "list"),
    )
    return PixelInspectorSnapshot(
        frameStats = PixelHostFrameStats(deltaMs = 16, fpsAvg = 60f, paintTimeNanos = 1_200_000, frameCount = 120),
        allocationSample = null,
        targetCounts = PixelInspectorTargetCounts(click = 1, pager = 0, list = 1, scrollbar = 0, refresh = 0, textInput = 0, slider = 0, semantics = 1),
        targetSnapshots = targets,
        elementTree = "Demo\n  Row\n  Text",
        renderTree = "RenderRoot\n  RenderFlex\n  RenderText",
        semanticsTree = "button: demo",
        hasPendingBuild = false,
        focusedTextInput = false,
        activePagerCount = 0,
        activeListCount = 1,
        activeSlider = false,
        activeScrollbar = false,
        activeRefresh = false,
    )
}

private fun categoryColor(id: String): PixelColor = when (id) {
    DemoCatalog.input.id -> Blue
    DemoCatalog.debug.id -> Yellow
    else -> Accent
}

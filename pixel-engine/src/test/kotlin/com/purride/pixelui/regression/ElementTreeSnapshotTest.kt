package com.purride.pixelui.regression

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Alignment
import com.purride.pixelui.AppScaffold
import com.purride.pixelui.Badge
import com.purride.pixelui.Center
import com.purride.pixelui.Checkbox
import com.purride.pixelui.ClipRect
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.DecoratedBox
import com.purride.pixelui.Divider
import com.purride.pixelui.Dialog
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.Gap
import com.purride.pixelui.animation.IntOffset
import com.purride.pixelui.Opacity
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.PixelNavigator
import com.purride.pixelui.PixelRoute
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelTextSpan
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.Positioned
import com.purride.pixelui.RichText
import com.purride.pixelui.Row
import com.purride.pixelui.Semantics
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Snackbar
import com.purride.pixelui.Stack
import com.purride.pixelui.Switch
import com.purride.pixelui.Text
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.TextField
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Toast
import com.purride.pixelui.Transform
import com.purride.pixelui.Widget
import com.purride.pixelui.Wrap
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.host.ManualFrameScheduler
import com.purride.pixelui.state.PixelTextFieldState
import com.purride.pixelui.internal.ElementDiagnosticsNode
import com.purride.pixelui.internal.ElementTreeBuildRuntimeFactory
import com.purride.pixelui.internal.UnsupportedWidgetAdapter
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 引擎级 retained Element 树结构快照。
 *
 * 与 [EngineGoldenTest] 的像素快照互补：像素快照捕获"画出来长什么样"，
 * 这套快照捕获"构建出来的 Element/Widget/RenderObject 树形结构是什么"，
 * 让后续重构能同时校验视觉与树形两个维度。
 *
 * 重新生成基线：设置环境变量 REGEN_ELEMENT_SNAPSHOT=1 再跑该测试。
 */
class ElementTreeSnapshotTest {

    /**
     * 一个用于结构快照的场景：稳定的 widget 工厂。
     */
    private data class Scene(
        val name: String,
        val build: () -> Widget,
    )

    private val scenes: List<Scene> = listOf(
        Scene(name = "single_line_text") {
            Center(child = Text("HELLO"))
        },
        Scene(name = "row_with_padding") {
            Padding(
                child = Row(
                    children = listOf(
                        Text("LEFT"),
                        SizedBox(width = 6),
                        Text("RIGHT"),
                    ),
                ),
                all = 2,
            )
        },
        Scene(name = "column_aligned") {
            Column(
                children = listOf(
                    Text("FIRST"),
                    Text("SECOND"),
                    Text("THIRD"),
                ),
                spacing = 2,
                crossAxisAlignment = CrossAxisAlignment.CENTER,
            )
        },
        Scene(name = "container_filled_bordered") {
            Container(
                child = Text("OK"),
                width = 28,
                height = 12,
                padding = EdgeInsets.all(2),
                borderColor = PixelColor.White,
                alignment = Alignment.CENTER,
            )
        },
        Scene(name = "stack_with_positioned") {
            Stack(
                children = listOf(
                    Container(width = 30, height = 20, borderColor = PixelColor.White),
                    Positioned(child = Text("NW"), left = 2, top = 2),
                    Positioned(child = Text("SE"), right = 2, bottom = 2),
                ),
            )
        },
        Scene(name = "decorated_box") {
            DecoratedBox(
                child = Text("CORE"),
                borderColor = PixelColor.fromRgb(200, 100, 0),
                padding = 3,
            )
        },
        Scene(name = "gesture_detector_target") {
            GestureDetector(
                child = Container(
                    child = Text("TAP"),
                    width = 22,
                    height = 9,
                    borderColor = PixelColor.White,
                ),
                onTap = { /* no-op for structure-only baseline */ },
            )
        },
        Scene(name = "rich_text_two_spans") {
            RichText(
                spans = listOf(
                    PixelTextSpan(text = "RED ", style = TextStyle(color = PixelColor.fromRgb(200, 100, 0))),
                    PixelTextSpan(text = "BLUE", style = TextStyle.Default),
                ),
            )
        },
        Scene(name = "nested_column_row") {
            Container(
                child = Padding(
                    child = Column(
                        children = listOf(
                            Text("TITLE", style = TextStyle(color = PixelColor.fromRgb(200, 100, 0))),
                            SizedBox(height = 2),
                            Row(
                                children = listOf(
                                    Text("A"),
                                    SizedBox(width = 4),
                                    Text("B"),
                                    SizedBox(width = 4),
                                    Text("C"),
                                ),
                            ),
                            SizedBox(height = 2),
                            Text("BODY"),
                        ),
                        crossAxisAlignment = CrossAxisAlignment.START,
                    ),
                    all = 2,
                ),
                width = 48,
                height = 22,
                borderColor = PixelColor.White,
                alignment = Alignment.TOP_START,
            )
        },
        Scene(name = "components_controls") {
            AppScaffold(
                title = Text("SETTINGS"),
                body = Column(
                    children = listOf(
                        Row(
                            children = listOf(
                                Checkbox(checked = true, onChanged = null),
                                Text("SYNC"),
                                Gap(width = 4),
                                Switch(checked = false, onChanged = null),
                            ),
                            spacing = 2,
                            crossAxisAlignment = CrossAxisAlignment.CENTER,
                        ),
                        Divider(color = PixelColor.fromRgb(120, 120, 120)),
                        ProgressBar(progress = 0.55f, width = 42),
                        Badge(child = Text("MAIL"), label = Text("3")),
                    ),
                    spacing = 2,
                    crossAxisAlignment = CrossAxisAlignment.START,
                ),
                bottomBar = Text("READY"),
            )
        },
        Scene(name = "navigator_route_scope") {
            PixelNavigator(
                initialRoute = PixelRoute(
                    name = "home",
                    builder = { context ->
                        AppScaffold(
                            title = Text("NAV"),
                            body = Column(
                                children = listOf(
                                    Text("HOME"),
                                    OutlinedButton("DETAILS", onPressed = {}),
                                ),
                                spacing = 2,
                                crossAxisAlignment = CrossAxisAlignment.START,
                            ),
                            bottomBar = Text(if (PixelNavigator.maybeOf(context) != null) "SCOPED" else "MISSING"),
                        )
                    },
                ),
                vsync = PixelTickerProvider(ManualFrameScheduler()),
            )
        },
        Scene(name = "overlay_feedback") {
            Stack(
                children = listOf(
                    Dialog(
                        title = Text("CONFIRM"),
                        content = Text("DELETE ITEM"),
                        actions = listOf(
                            OutlinedButton("CANCEL", onPressed = {}),
                            OutlinedButton("OK", onPressed = {}),
                        ),
                    ),
                    Positioned(
                        child = SizedBox(
                            width = 44,
                            height = 12,
                            child = Toast("SAVED", fillColor = PixelColor.fromRgb(40, 40, 40)),
                        ),
                        left = 32,
                        top = 2,
                    ),
                    Positioned(
                        child = Snackbar("QUEUED", action = OutlinedButton("UNDO", onPressed = {})),
                        left = 3,
                        right = 3,
                        bottom = 2,
                    ),
                ),
            )
        },
        Scene(name = "wrap_layout") {
            Wrap(
                children = listOf(
                    Container(child = Text("ONE"), padding = EdgeInsets.all(1), borderColor = PixelColor.White),
                    Container(child = Text("TWO"), padding = EdgeInsets.all(1), borderColor = PixelColor.White),
                    Container(child = Text("THREE"), padding = EdgeInsets.all(1), borderColor = PixelColor.White),
                ),
                spacing = 2,
                runSpacing = 1,
            )
        },
        Scene(name = "textfield_multiline_selection_handles") {
            val state = PixelTextFieldState(initialText = "AA\nBBBB\nCC", selectionStart = 0, selectionEnd = 7)
            val controller = TextEditingController()
            controller.focus(state)
            TextField(
                state = state,
                controller = controller,
                minLines = 3,
                maxLines = 3,
            )
        },
        Scene(name = "opacity_clip_translate_semantics") {
            Semantics(
                label = "SHIFTED",
                role = PixelSemanticRole.BUTTON,
                child = ClipRect(
                    child = Transform.translate(
                        offset = IntOffset(4, 2),
                        child = Opacity(
                            opacity = 0.5f,
                            child = OutlinedButton("GO", onPressed = {}),
                        ),
                    ),
                ),
            )
        },
    )

    /**
     * 顺序构建并捕获每个场景的 retained tree 结构。
     */
    @Test
    fun snapshotAllScenesMatchGolden() {
        val regen = System.getenv("REGEN_ELEMENT_SNAPSHOT") == "1"
        val snapshotDir = File(SNAPSHOT_DIR_PATH)
        if (regen) {
            snapshotDir.mkdirs()
        }
        val failures = mutableListOf<String>()
        scenes.forEach { scene ->
            val actual = buildAndDumpTree(scene)
            val snapshotFile = File(snapshotDir, "${scene.name}.txt")
            if (regen) {
                snapshotFile.writeText(actual)
                return@forEach
            }
            assertTrue(
                "缺少 element 快照文件 ${snapshotFile.absolutePath}，请先以 REGEN_ELEMENT_SNAPSHOT=1 跑一次生成基线。",
                snapshotFile.exists(),
            )
            val expected = snapshotFile.readText()
            if (expected != actual) {
                failures += buildString {
                    append("scene='").append(scene.name).append("' element tree 与 snapshot 不一致。\n")
                    append("--- expected (")
                    append(snapshotFile.name)
                    append(") ---\n")
                    append(expected)
                    append("\n--- actual ---\n")
                    append(actual)
                }
            }
        }
        if (failures.isNotEmpty()) {
            throw AssertionError(failures.joinToString(separator = "\n\n"))
        }
    }

    /**
     * 用 retained build runtime 构建一棵 element 树并把诊断节点列表格式化为多行文本。
     */
    private fun buildAndDumpTree(scene: Scene): String {
        val buildRuntime = ElementTreeBuildRuntimeFactory.createDefault(
            onVisualUpdate = { },
            widgetAdapter = UnsupportedWidgetAdapter,
            // Structural snapshots exclude focus gained only as a runtime modal side effect.
            automaticallyFocusModalDescendants = false,
        )
        try {
            buildRuntime.resolveElementTree(scene.build())
            val diagnostics = buildRuntime.collectDiagnostics()
            return formatDiagnostics(diagnostics.elementDiagnostics)
        } finally {
            buildRuntime.dispose()
        }
    }

    /**
     * 把 element diagnostics 列表格式化为缩进树文本。
     *
     * 输出包含 element 类型、widget 类型、深度、子节点数、render object 类型，
     * 但不包含运行期才会变的状态字段（如 dirty 标记），避免快照对状态机抖动敏感。
     */
    private fun formatDiagnostics(nodes: List<ElementDiagnosticsNode>): String {
        if (nodes.isEmpty()) {
            return "<empty>\n"
        }
        val builder = StringBuilder()
        nodes.forEach { node ->
            repeat(node.depth) { builder.append("  ") }
            builder.append(node.name)
            builder.append("(widget=").append(node.widgetName)
            builder.append(", children=").append(node.childCount)
            builder.append(", listened=").append(node.listenedObjectCount)
            if (node.renderObjectName != null) {
                builder.append(", render=").append(node.renderObjectName)
            }
            builder.append(")\n")
        }
        return builder.toString()
    }

    companion object {
        /**
         * element tree 快照落盘的相对路径（相对模块根 = pixel-engine/）。
         */
        private const val SNAPSHOT_DIR_PATH = "src/test/resources/element-snapshots"
    }
}

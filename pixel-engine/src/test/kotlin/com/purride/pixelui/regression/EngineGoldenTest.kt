package com.purride.pixelui.regression

import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelBitmapRegion
import com.purride.pixelcore.PixelSpriteSheet
import com.purride.pixelui.Alignment
import com.purride.pixelui.AppScaffold
import com.purride.pixelui.Badge
import com.purride.pixelui.Center
import com.purride.pixelui.Checkbox
import com.purride.pixelui.ClipRect
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.CustomScrollView
import com.purride.pixelui.DecoratedBox
import com.purride.pixelui.Divider
import com.purride.pixelui.Dialog
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.Gap
import com.purride.pixelui.GridViewBuilder
import com.purride.pixelui.ListTile
import com.purride.pixelui.animation.IntOffset
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.Opacity
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.Path
import com.purride.pixelui.PixelNavigator
import com.purride.pixelui.PixelPath
import com.purride.pixelui.PixelPoint
import com.purride.pixelui.testRouteRequest
import com.purride.pixelui.Polygon
import com.purride.pixelui.PixelTextSpan
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.Positioned
import com.purride.pixelui.RefreshIndicator
import com.purride.pixelui.RichText
import com.purride.pixelui.Row
import com.purride.pixelui.Scrollbar
import com.purride.pixelui.Semantics
import com.purride.pixelui.SegmentedControl
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Snackbar
import com.purride.pixelui.SliverAppBar
import com.purride.pixelui.SliverList
import com.purride.pixelui.SliverPinnedHeader
import com.purride.pixelui.Sprite
import com.purride.pixelui.Stack
import com.purride.pixelui.Switch
import com.purride.pixelui.Tabs
import com.purride.pixelui.Text
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.TextField
import com.purride.pixelui.TextStyle
import com.purride.pixelui.PixelTextFieldStyle
import com.purride.pixelui.Toast
import com.purride.pixelui.Transform
import com.purride.pixelui.Widget
import com.purride.pixelui.Wrap
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.host.ManualFrameScheduler
import com.purride.pixelui.internal.PixelUiRuntime
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelRefreshIndicatorController
import com.purride.pixelui.state.PixelTextFieldState
import org.junit.Test
import java.io.File

/**
 * 引擎级金像素回归基线。
 *
 * 每个 scene 用公开 widget API 构造一棵代表性的组件树，渲染成 PixelBuffer 后
 * 序列化为 ASCII art 并与 src/test/resources/golden 下的 .txt 基线比对。
 *
 * 当后续重构（Phase 1+）发生时，若任一像素发生变化都会立刻失败，作为视觉回归探测器。
 *
 * 失败时只在 `build/reports/golden/engine` 生成候选和差异；基线必须经人工审阅后显式修改。
 */
class EngineGoldenTest {

    /**
     * 一个回归场景：固定逻辑尺寸 + 一棵 widget 树。
     */
    private data class Scene(
        val name: String,
        val width: Int,
        val height: Int,
        val build: () -> Widget,
    )

    private val scenes: List<Scene> = listOf(
        Scene(name = "single_line_text", width = 40, height = 9) {
            Center(child = Text("HELLO"))
        },
        Scene(name = "wrapped_text", width = 24, height = 30) {
            Text(
                data = "WRAP THIS LONG SENTENCE INTO MULTIPLE LINES",
                softWrap = true,
                maxLines = 6,
            )
        },
        Scene(name = "row_with_padding", width = 60, height = 12) {
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
        Scene(name = "column_aligned", width = 50, height = 30) {
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
        Scene(name = "container_filled_bordered", width = 32, height = 16) {
            Container(
                child = Text("OK"),
                width = 28,
                height = 12,
                padding = EdgeInsets.all(2),
                borderColor = PixelColor.White,
                alignment = Alignment.CENTER,
            )
        },
        Scene(name = "stack_with_positioned", width = 30, height = 20) {
            Stack(
                children = listOf(
                    Container(width = 30, height = 20, borderColor = PixelColor.White),
                    Positioned(child = Text("NW"), left = 2, top = 2),
                    Positioned(child = Text("SE"), right = 2, bottom = 2),
                ),
            )
        },
        Scene(name = "decorated_box", width = 40, height = 14) {
            DecoratedBox(
                child = Text("CORE"),
                borderColor = PixelColor.fromRgb(200, 100, 0),
                padding = 3,
            )
        },
        Scene(name = "gesture_detector_target", width = 26, height = 12) {
            GestureDetector(
                child = Container(
                    child = Text("TAP"),
                    width = 22,
                    height = 9,
                    borderColor = PixelColor.White,
                ),
                onTap = { /* no-op for render-only baseline */ },
            )
        },
        Scene(name = "rich_text_two_spans", width = 40, height = 9) {
            RichText(
                spans = listOf(
                    PixelTextSpan(text = "RED ", style = TextStyle(color = PixelColor.fromRgb(200, 100, 0))),
                    PixelTextSpan(text = "BLUE", style = TextStyle.Default),
                ),
            )
        },
        Scene(name = "nested_column_row", width = 48, height = 22) {
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
        Scene(name = "components_controls", width = 96, height = 34) {
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
        Scene(name = "component_state_matrix", width = 92, height = 42) {
            val focusedController = TextEditingController()
            val focusedState = focusedController.create(initialText = "FOCUS")
            focusedController.focus(focusedState)
            val disabledController = TextEditingController()
            val disabledState = disabledController.create(initialText = "OFF")
            val readOnlyController = TextEditingController()
            val readOnlyState = readOnlyController.create(initialText = "LOCK")
            val accent = PixelColor.fromRgb(200, 100, 0)
            val dim = PixelColor.fromRgb(80, 80, 80)
            val fieldStyle = PixelTextFieldStyle.Default.copy(
                borderColor = PixelColor.White,
                focusedBorderColor = accent,
                disabledBorderColor = dim,
                readOnlyBorderColor = PixelColor.fromRgb(230, 180, 60),
                padding = 0,
            )
            Column(
                children = listOf(
                    Row(
                        children = listOf(
                            OutlinedButton("OK", onPressed = {}, borderColor = PixelColor.White),
                            OutlinedButton("SEL", onPressed = {}, borderColor = accent),
                            OutlinedButton("OFF", onPressed = {}, enabled = false, borderColor = dim),
                        ),
                        spacing = 2,
                    ),
                    TextField(
                        state = focusedState,
                        controller = focusedController,
                        style = fieldStyle,
                    ),
                    TextField(
                        state = disabledState,
                        controller = disabledController,
                        enabled = false,
                        style = fieldStyle,
                    ),
                    TextField(
                        state = readOnlyState,
                        controller = readOnlyController,
                        readOnly = true,
                        style = fieldStyle,
                    ),
                ),
                spacing = 2,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        },
        Scene(name = "component_controls_state_matrix", width = 96, height = 48) {
            val accent = PixelColor.fromRgb(80, 180, 110)
            val dim = PixelColor.fromRgb(80, 80, 80)
            val scrollController = PixelListController()
            val scrollState = scrollController.create(initialScrollOffsetPx = 9f)
            Column(
                children = listOf(
                    ListTile(
                        leading = Checkbox(checked = true, onChanged = null, activeColor = accent),
                        title = Text("Enabled tile"),
                        trailing = Switch(checked = true, onChanged = null, activeColor = accent),
                        onTap = {},
                    ),
                    ListTile(
                        leading = Checkbox(checked = true, onChanged = null, enabled = false),
                        title = Text("Disabled tile"),
                        trailing = Switch(checked = false, onChanged = null, enabled = false),
                        enabled = false,
                    ),
                    Row(
                        children = listOf(
                            Checkbox(checked = false, onChanged = {}, inactiveColor = PixelColor.White),
                            Checkbox(checked = true, onChanged = {}, activeColor = accent),
                            Checkbox(checked = true, onChanged = {}, enabled = false),
                            Switch(checked = false, onChanged = {}, inactiveColor = PixelColor.White),
                            Switch(checked = true, onChanged = {}, activeColor = accent),
                            Switch(checked = true, onChanged = {}, enabled = false),
                        ),
                        spacing = 2,
                        crossAxisAlignment = CrossAxisAlignment.CENTER,
                    ),
                    Tabs(labels = listOf("A", "B", "C"), selectedIndex = 1, onSelected = {}),
                    SegmentedControl(labels = listOf("LOW", "MID", "HI"), selectedIndex = 2, onSelected = {}),
                    SizedBox(
                        height = 10,
                        child = Scrollbar(
                            state = scrollState,
                            width = 2,
                            thumbColor = accent,
                            trackColor = dim,
                            child = GridViewBuilder(
                                itemCount = 12,
                                itemBuilder = { index ->
                                    Container(
                                        child = Text("${index % 10}"),
                                        borderColor = PixelColor.White,
                                        alignment = Alignment.CENTER,
                                    )
                                },
                                cellWidth = 12,
                                cellHeight = 5,
                                spacing = 1,
                                runSpacing = 1,
                                state = scrollState,
                                controller = scrollController,
                            ),
                        ),
                    ),
                ),
                spacing = 1,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        },
        Scene(name = "navigator_route_scope", width = 64, height = 22) {
            PixelNavigator(
                initialRequest = testRouteRequest(
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
        Scene(name = "overlay_feedback", width = 108, height = 46) {
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
        Scene(name = "wrap_layout", width = 34, height = 18) {
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
        Scene(name = "textfield_multiline_selection_handles", width = 72, height = 26) {
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
        Scene(name = "scrollbar_grid_thumb", width = 42, height = 20) {
            val controller = PixelListController()
            val state = controller.create(initialScrollOffsetPx = 18f)
            Scrollbar(
                state = state,
                width = 2,
                thumbColor = PixelColor.White,
                trackColor = PixelColor.fromArgb(90, 120, 120, 120),
                child = GridViewBuilder(
                    itemCount = 24,
                    itemBuilder = { index ->
                        Container(
                            child = Text("${index % 10}"),
                            borderColor = PixelColor.White,
                            alignment = Alignment.CENTER,
                        )
                    },
                    cellWidth = 10,
                    cellHeight = 6,
                    spacing = 1,
                    runSpacing = 1,
                    state = state,
                    controller = controller,
                ),
            )
        },
        Scene(name = "refresh_indicator_armed", width = 42, height = 20) {
            val listController = PixelListController()
            val listState = listController.create()
            val refreshController = PixelRefreshIndicatorController()
            val refreshState = refreshController.create()
            refreshController.startPull(refreshState)
            refreshController.updatePull(refreshState, distancePx = 12f, thresholdPx = 10)
            RefreshIndicator(
                state = refreshState,
                controller = refreshController,
                thresholdPx = 10,
                armedColor = PixelColor.fromRgb(200, 100, 0),
                onRefresh = {},
                child = GridViewBuilder(
                    itemCount = 16,
                    itemBuilder = { index ->
                        Container(
                            child = Text("${index % 10}"),
                            borderColor = PixelColor.White,
                            alignment = Alignment.CENTER,
                        )
                    },
                    cellWidth = 10,
                    cellHeight = 6,
                    spacing = 1,
                    runSpacing = 1,
                    state = listState,
                    controller = listController,
                ),
            )
        },
        Scene(name = "custom_scroll_pinned_header", width = 44, height = 22) {
            val controller = PixelListController()
            val state = controller.create(initialScrollOffsetPx = 14f)
            CustomScrollView(
                state = state,
                controller = controller,
                slivers = listOf(
                    SliverPinnedHeader(
                        child = Container(
                            child = Text("PIN"),
                            height = 7,
                            fillColor = PixelColor.fromRgb(230, 180, 60),
                            borderColor = PixelColor.White,
                            alignment = Alignment.CENTER,
                        ),
                    ),
                    SliverList(
                        spacing = 1,
                        items = List(8) { index ->
                            Container(
                                child = Text("ROW $index"),
                                height = 6,
                                borderColor = PixelColor.fromArgb(120, 255, 255, 255),
                            )
                        },
                    ),
                ),
            )
        },
        Scene(name = "custom_scroll_sliver_app_bar", width = 44, height = 22) {
            val controller = PixelListController()
            val state = controller.create(initialScrollOffsetPx = 10f)
            CustomScrollView(
                state = state,
                controller = controller,
                slivers = listOf(
                    SliverAppBar(
                        expandedHeight = 14,
                        collapsedHeight = 6,
                        child = Container(
                            child = Column(
                                children = listOf(
                                    Text("HERO"),
                                    SizedBox(height = 2),
                                    Text("BAR"),
                                ),
                                crossAxisAlignment = CrossAxisAlignment.CENTER,
                            ),
                            fillColor = PixelColor.fromRgb(230, 180, 60),
                            borderColor = PixelColor.White,
                        ),
                    ),
                    SliverList(
                        spacing = 1,
                        items = List(8) { index ->
                            Container(
                                child = Text("ROW $index"),
                                height = 6,
                                borderColor = PixelColor.fromArgb(120, 255, 255, 255),
                            )
                        },
                    ),
                ),
            )
        },
        Scene(name = "sprite_polygon_path", width = 42, height = 18) {
            Row(
                children = listOf(
                    Sprite(sheet = sampleSpriteSheet(), frameIndex = 1),
                    Polygon(
                        points = listOf(PixelPoint(0, 7), PixelPoint(7, 0), PixelPoint(14, 7)),
                        color = PixelColor.White,
                        filled = true,
                    ),
                    Path(
                        path = PixelPath.rect(left = 0, top = 0, width = 12, height = 8),
                        color = PixelColor.fromRgb(200, 100, 0),
                        strokeWidth = 2,
                    ),
                ),
                spacing = 3,
            )
        },
        Scene(name = "translucent_border_single_blend", width = 12, height = 8) {
            // 半透明描边回归探测：alpha=150 白色叠在黑色填充上，单次混合 = 150（'*'），
            // 历史 drawRect 的四角双重混合 = 212（'#'）。任何重复混合回归都会翻转角字符。
            Container(
                width = 12,
                height = 8,
                fillColor = PixelColor.fromRgb(0, 0, 0),
                borderColor = PixelColor.fromArgb(150, 255, 255, 255),
            )
        },
        Scene(name = "opacity_clip_translate_semantics", width = 34, height = 16) {
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
     * 顺序渲染所有场景，缺失 golden 时给出明确提示。
     */
    @Test
    fun renderAllScenesMatchGolden() {
        /** 已审阅源码基线所在目录。 */
        val goldenDir = File(GOLDEN_DIR_PATH)
        /** 收集全部场景差异，避免第一次失败掩盖后续场景。 */
        val failures = mutableListOf<String>()
        scenes.forEach { scene ->
            /** 当前场景渲染出的确定性 ASCII 像素。 */
            val actual = renderToAscii(scene)
            /** 当前场景只读的源码基线。 */
            val goldenFile = File(goldenDir, "${scene.name}.txt")
            /** 候选和 diff 固定写入 build 报告目录。 */
            val comparison = ReviewedGoldenVerifier.compare(
                baselineFile = goldenFile,
                actual = actual,
                reportStem = File(GOLDEN_REPORT_DIR_PATH, scene.name),
            )
            if (!comparison.matches) {
                failures += "scene='${scene.name}': ${comparison.failureMessage}"
            }
        }
        if (failures.isNotEmpty()) {
            throw AssertionError(failures.joinToString(separator = "\n\n"))
        }
    }

    /**
     * 把一个场景渲染成 ASCII 文本快照。
     */
    private fun renderToAscii(scene: Scene): String {
        val runtime = PixelUiRuntime()
        try {
            val result = runtime.render(
                root = scene.build(),
                logicalWidth = scene.width,
                logicalHeight = scene.height,
            )
            return bufferToAscii(result.buffer)
        } finally {
            runtime.dispose()
        }
    }

    /**
     * 把 PixelBuffer 转成可读字符画，便于人工 diff 和 golden 文件 review。
     * 亮度 ≥ 200 → '#'，亮度 ≥ 50 → '*'，完全透明 → '.'。
     */
    private fun bufferToAscii(buffer: com.purride.pixelcore.PixelBuffer): String {
        val builder = StringBuilder()
        builder.append("size=").append(buffer.width).append('x').append(buffer.height).append('\n')
        for (y in 0 until buffer.height) {
            for (x in 0 until buffer.width) {
                builder.append(colorChar(buffer.getPixel(x, y)))
            }
            builder.append('\n')
        }
        return builder.toString()
    }

    private fun colorChar(color: PixelColor): Char {
        val argb = color.argb
        val a = (argb ushr 24) and 0xFF
        if (a == 0) return '.'
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        val brightness = (r * 299 + g * 587 + b * 114) / 1000
        return when {
            brightness >= 200 -> '#'
            brightness >= 50 -> '*'
            else -> '.'
        }
    }

    private fun sampleSpriteSheet(): PixelSpriteSheet {
        val red = PixelColor.fromRgb(220, 60, 60).argb
        val yellow = PixelColor.fromRgb(230, 200, 60).argb
        val blue = PixelColor.fromRgb(60, 100, 220).argb
        val pixels = IntArray(8 * 4)
        for (y in 0 until 4) {
            for (x in 0 until 4) pixels[y * 8 + x] = if ((x + y) % 2 == 0) red else yellow
            for (x in 4 until 8) pixels[y * 8 + x] = if (x == 4 || y == 0 || x == 7 || y == 3) blue else PixelColor.White.argb
        }
        return PixelSpriteSheet(
            bitmap = PixelBitmap(width = 8, height = 4, pixels = pixels),
            frames = listOf(
                PixelBitmapRegion(left = 0, top = 0, width = 4, height = 4),
                PixelBitmapRegion(left = 4, top = 0, width = 4, height = 4),
            ),
        )
    }

    companion object {
        /**
         * golden 文件落盘的相对路径（相对模块根 = pixel-engine/）。
         *
         * Gradle 默认以模块目录作为单元测试工作目录，所以这个相对路径同时适用于 R/W。
         */
        private const val GOLDEN_DIR_PATH = "src/test/resources/golden"

        /** ordinary tests 只能写入的候选和差异报告目录。 */
        private const val GOLDEN_REPORT_DIR_PATH = "build/reports/golden/engine"
    }
}

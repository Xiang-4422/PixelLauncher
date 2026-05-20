package com.purride.pixelui.regression

import com.purride.pixelcore.MonoPixelBuffer
import com.purride.pixelcore.PixelTone
import com.purride.pixelui.Alignment
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.DecoratedBox
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.Padding
import com.purride.pixelui.PixelTextSpan
import com.purride.pixelui.Positioned
import com.purride.pixelui.RichText
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Stack
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.PixelUiRuntime
import org.junit.Assert.assertTrue
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
 * 重新生成基线：设置环境变量 REGEN_GOLDEN=1 再跑该测试，会覆盖磁盘上的 golden 文件。
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
                fillTone = PixelTone.OFF,
                borderTone = PixelTone.ON,
                alignment = Alignment.CENTER,
            )
        },
        Scene(name = "stack_with_positioned", width = 30, height = 20) {
            Stack(
                children = listOf(
                    Container(width = 30, height = 20, borderTone = PixelTone.ON),
                    Positioned(child = Text("NW"), left = 2, top = 2),
                    Positioned(child = Text("SE"), right = 2, bottom = 2),
                ),
            )
        },
        Scene(name = "decorated_box", width = 40, height = 14) {
            DecoratedBox(
                child = Text("CORE"),
                fillTone = PixelTone.OFF,
                borderTone = PixelTone.ACCENT,
                padding = 3,
            )
        },
        Scene(name = "gesture_detector_target", width = 26, height = 12) {
            GestureDetector(
                child = Container(
                    child = Text("TAP"),
                    width = 22,
                    height = 9,
                    borderTone = PixelTone.ON,
                ),
                onTap = { /* no-op for render-only baseline */ },
            )
        },
        Scene(name = "rich_text_two_spans", width = 40, height = 9) {
            RichText(
                spans = listOf(
                    PixelTextSpan(text = "RED ", style = TextStyle.Accent),
                    PixelTextSpan(text = "BLUE", style = TextStyle.Default),
                ),
            )
        },
        Scene(name = "nested_column_row", width = 48, height = 22) {
            Container(
                child = Padding(
                    child = Column(
                        children = listOf(
                            Text("TITLE", style = TextStyle.Accent),
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
                borderTone = PixelTone.ON,
                alignment = Alignment.TOP_START,
            )
        },
    )

    /**
     * 顺序渲染所有场景，缺失 golden 时给出明确提示。
     */
    @Test
    fun renderAllScenesMatchGolden() {
        val regen = System.getenv("REGEN_GOLDEN") == "1"
        val goldenDir = File(GOLDEN_DIR_PATH)
        if (regen) {
            goldenDir.mkdirs()
        }
        val failures = mutableListOf<String>()
        scenes.forEach { scene ->
            val actual = renderToAscii(scene)
            val goldenFile = File(goldenDir, "${scene.name}.txt")
            if (regen) {
                goldenFile.writeText(actual)
                return@forEach
            }
            assertTrue(
                "缺少 golden 文件 ${goldenFile.absolutePath}，请先以 REGEN_GOLDEN=1 跑一次生成基线。",
                goldenFile.exists(),
            )
            val expected = goldenFile.readText()
            if (expected != actual) {
                failures += buildString {
                    append("scene='").append(scene.name).append("' 像素与 golden 不一致。\n")
                    append("--- expected (")
                    append(goldenFile.name)
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
     */
    private fun bufferToAscii(buffer: com.purride.pixelcore.PixelBuffer): String {
        val mono = buffer as MonoPixelBuffer
        val builder = StringBuilder()
        builder.append("size=").append(mono.width).append('x').append(mono.height).append('\n')
        for (y in 0 until mono.height) {
            for (x in 0 until mono.width) {
                builder.append(toneChar(mono.getPixel(x, y)))
            }
            builder.append('\n')
        }
        return builder.toString()
    }

    private fun toneChar(value: PixelTone): Char {
        return when (value) {
            PixelTone.ON -> '#'
            PixelTone.ACCENT -> '*'
            PixelTone.OFF -> '.'
        }
    }

    companion object {
        /**
         * golden 文件落盘的相对路径（相对模块根 = pixel-engine/）。
         *
         * Gradle 默认以模块目录作为单元测试工作目录，所以这个相对路径同时适用于 R/W。
         */
        private const val GOLDEN_DIR_PATH = "src/test/resources/golden"
    }
}

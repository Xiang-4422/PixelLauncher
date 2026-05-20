package com.purride.pixelui.regression

import com.purride.pixelcore.ColorPixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelColorMode
import com.purride.pixelui.Container
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Text
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.PixelUiRuntime
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 彩色模式渲染回归基线。
 *
 * 与 [EngineGoldenTest] 类似，但走 Color 路径：
 * - buffer 类型为 [ColorPixelBuffer]
 * - golden 序列化为 ARGB hex 文本（每行一行像素，空格分隔）
 *
 * 重新生成基线：设置环境变量 REGEN_GOLDEN=1 再跑该测试。
 */
class ColorModeGoldenTest {

    private data class ColorScene(
        val name: String,
        val width: Int,
        val height: Int,
        val build: () -> Widget,
    )

    private val scenes: List<ColorScene> = listOf(
        ColorScene(name = "container_red_fill", width = 8, height = 8) {
            Container(
                width = 8,
                height = 8,
                fillColor = PixelColor.fromRgb(255, 0, 0),
                borderTone = null,
            )
        },
        ColorScene(name = "text_blue", width = 40, height = 9) {
            Text(
                data = "HI",
                color = PixelColor.fromRgb(0, 0, 255),
            )
        },
        ColorScene(name = "button_green_border", width = 20, height = 7) {
            OutlinedButton(
                text = "OK",
                onPressed = null,
                borderColor = PixelColor.fromRgb(0, 255, 0),
            )
        },
    )

    @Test
    fun renderAllColorScenesMatchGolden() {
        val regen = System.getenv("REGEN_GOLDEN") == "1"
        val goldenDir = File(COLOR_GOLDEN_DIR_PATH)
        if (regen) {
            goldenDir.mkdirs()
        }
        val failures = mutableListOf<String>()
        scenes.forEach { scene ->
            val actual = renderToHex(scene)
            val goldenFile = File(goldenDir, "${scene.name}.txt")
            if (regen) {
                goldenFile.writeText(actual)
                return@forEach
            }
            assertTrue(
                "缺少彩色 golden 文件 ${goldenFile.absolutePath}，请先以 REGEN_GOLDEN=1 跑一次生成基线。",
                goldenFile.exists(),
            )
            val expected = goldenFile.readText()
            if (expected != actual) {
                failures += buildString {
                    append("scene='").append(scene.name).append("' 彩色像素与 golden 不一致。\n")
                    append("--- expected ---\n").append(expected)
                    append("\n--- actual ---\n").append(actual)
                }
            }
        }
        if (failures.isNotEmpty()) {
            throw AssertionError(failures.joinToString(separator = "\n\n"))
        }
    }

    private fun renderToHex(scene: ColorScene): String {
        val runtime = PixelUiRuntime()
        try {
            val result = runtime.render(
                root = scene.build(),
                logicalWidth = scene.width,
                logicalHeight = scene.height,
                colorMode = PixelColorMode.Color,
            )
            return bufferToHex(result.buffer as ColorPixelBuffer)
        } finally {
            runtime.dispose()
        }
    }

    private fun bufferToHex(buffer: ColorPixelBuffer): String {
        val sb = StringBuilder()
        sb.append("size=").append(buffer.width).append('x').append(buffer.height).append('\n')
        for (y in 0 until buffer.height) {
            for (x in 0 until buffer.width) {
                if (x > 0) sb.append(' ')
                sb.append(buffer.getPixel(x, y).argb.toUInt().toString(16).padStart(8, '0'))
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    companion object {
        private const val COLOR_GOLDEN_DIR_PATH = "src/test/resources/golden/color"
    }
}

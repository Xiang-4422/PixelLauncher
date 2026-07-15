package com.purride.pixelui

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.internal.PixelUiRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证 `pixel-widgets` 只依赖 core/runtime 时仍可独立构建并完成真实渲染。 */
public class PixelWidgetsArtifactTest {
    /** 标准 widgets 组合应通过独立 runtime 输出确定背景与非空文字像素。 */
    @Test
    public fun standardWidgetsRenderWithoutNavigationOrAndroidArtifacts(): Unit {
        /** 本测试独占且不依赖平台 Host 的 retained runtime。 */
        val runtime = PixelUiRuntime()
        try {
            /** 仅由 widgets/core/runtime 公开类型组成的标准组件树。 */
            val root = DecoratedBox(
                fillColor = PixelColor.White,
                child = Text(data = "OK", color = PixelColor.Black),
            )
            /** 独立 artifact 完成布局和绘制后的像素结果。 */
            val result = runtime.render(root = root, logicalWidth = 24, logicalHeight = 12)
            /** 背景区域应保持组件声明的白色。 */
            val whitePixelCount = result.buffer.countPixels { color -> color == PixelColor.White }
            /** 文字字形应在白色背景上写入黑色像素。 */
            val blackPixelCount = result.buffer.countPixels { color -> color == PixelColor.Black }

            assertTrue(whitePixelCount > 0)
            assertTrue(blackPixelCount > 0)
            assertEquals(24, result.buffer.width)
            assertEquals(12, result.buffer.height)
        } finally {
            runtime.dispose()
        }
    }

    /** 统计像素缓冲区中满足谓词的像素数量。 */
    private fun com.purride.pixelcore.PixelBuffer.countPixels(
        predicate: (PixelColor) -> Boolean,
    ): Int {
        /** 当前缓冲区中满足条件的累计像素数。 */
        var count = 0
        repeat(height) { y ->
            repeat(width) { x ->
                if (predicate(getPixel(x, y))) count += 1
            }
        }
        return count
    }
}

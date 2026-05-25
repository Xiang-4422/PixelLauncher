package com.purride.pixeldemo.showcase.foundation

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Image
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

/**
 * 演示 [Image] widget：把不可变 [PixelBitmap] 1:1 blit 到目标 buffer。
 *
 * 本场景手工构造 16x16 的格子图案与 8x4 的彩色棋盘，展示：
 *  - intrinsic 尺寸 = bitmap 自身宽高
 *  - 一行 [System.arraycopy] 拷贝，超出 layout size 自动裁剪
 *  - ARGB 像素被原样保留
 */
object ImageBlitScene : DemoScene {
    override val id = "image_blit"
    override val title = "IMAGE BLIT"
    override val description = "PixelBitmap → Image widget 1:1 blit"

    override fun build(env: DemoEnv): Widget {
        val checker = buildCheckerboard()
        val gradient = buildGradient()
        val labelColor = PixelColor.fromRgb(200, 200, 200)
        return Center(
            child = Column(
                mainAxisAlignment = MainAxisAlignment.CENTER,
                crossAxisAlignment = CrossAxisAlignment.CENTER,
                children = listOf(
                    Text("CHECKER 16x16", style = TextStyle(color = labelColor)),
                    SizedBox(height = 2),
                    Image(bitmap = checker),
                    SizedBox(height = 6),
                    Text("GRADIENT 8x4", style = TextStyle(color = labelColor)),
                    SizedBox(height = 2),
                    Image(bitmap = gradient),
                ),
            ),
        )
    }

    private fun buildCheckerboard(): PixelBitmap {
        val w = 16
        val h = 16
        val on = PixelColor.fromRgb(0xFF, 0xC0, 0x40).argb
        val off = PixelColor.fromRgb(0x40, 0x40, 0xC0).argb
        val pixels = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            if (((x / 2) + (y / 2)) and 1 == 0) on else off
        }
        return PixelBitmap(width = w, height = h, pixels = pixels)
    }

    private fun buildGradient(): PixelBitmap {
        val w = 8
        val h = 4
        val pixels = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            // X 走 R，Y 走 G，固定 B
            val r = (x * 255 / (w - 1)).coerceIn(0, 255)
            val g = (y * 255 / (h - 1)).coerceIn(0, 255)
            PixelColor.fromRgb(r, g, 0x80).argb
        }
        return PixelBitmap(width = w, height = h, pixels = pixels)
    }
}

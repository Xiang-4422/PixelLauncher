package com.purride.pixeldemo.showcase.foundation

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Column
import com.purride.pixelui.CustomPaint
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.PixelPath
import com.purride.pixelui.PixelPoint
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object CustomPaintScene : DemoScene {
    override val id = "custom_paint"
    override val title = "CustomPaint"
    override val description = "Canvas-style batched pixel drawing"

    override fun build(env: DemoEnv): Widget {
        return Column(
            mainAxisAlignment = MainAxisAlignment.CENTER,
            spacing = 4,
            children = listOf(
                Text("PIXEL CANVAS", style = TextStyle(color = PixelColor.fromRgb(180, 180, 180))),
                CustomPaint(width = 42, height = 28) {
                    fillRect(0, 0, 42, 28, PixelColor.fromRgb(20, 20, 20))
                    drawRect(0, 0, 42, 28, PixelColor.White, strokeWidth = 2)
                    drawLine(2, 24, 39, 3, PixelColor.fromRgb(255, 192, 64), strokeWidth = 2)
                    drawCircle(11, 9, 5, PixelColor.fromRgb(80, 180, 110), filled = false, strokeWidth = 2)
                    drawPolygon(
                        points = listOf(PixelPoint(24, 20), PixelPoint(31, 8), PixelPoint(38, 20)),
                        color = PixelColor.fromRgb(120, 160, 255),
                        filled = true,
                    )
                    drawPath(
                        path = PixelPath.rect(left = 5, top = 16, width = 14, height = 7),
                        color = PixelColor.White,
                        strokeWidth = 2,
                    )
                },
            ),
        )
    }
}

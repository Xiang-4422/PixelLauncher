package com.purride.pixeldemo.showcase.foundation

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Center
import com.purride.pixelui.Circle
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Line
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.PixelShapeStyle
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

/**
 * 展示 [Line] / [Circle] 图形原语 widget。
 *
 * 三组样例：
 *  - 水平 / 垂直 / 对角线（同 Bresenham 路径）
 *  - 填充 / 描边圆，半径 2 / 4 / 6
 *  - 圆 + 对角线的组合（演示 layout intrinsic size 自适配）
 */
object ShapePrimitivesScene : DemoScene {
    override val id = "shape_primitives"
    override val title = "SHAPES"
    override val description = "Line / Circle 图形原语 widget"

    override fun build(env: DemoEnv): Widget {
        val ink = PixelColor.fromRgb(0xE0, 0xE0, 0xE0)
        val accent = PixelColor.fromRgb(0xFF, 0xC0, 0x40)
        val outline = PixelShapeStyle(color = ink, filled = false, strokeWidth = 2)
        val accentOutline = PixelShapeStyle(color = accent, filled = false, strokeWidth = 2)
        val labelStyle = TextStyle(color = PixelColor.fromRgb(180, 180, 180))
        return Center(
            child = Column(
                mainAxisAlignment = MainAxisAlignment.CENTER,
                crossAxisAlignment = CrossAxisAlignment.CENTER,
                children = listOf(
                    Text("LINES", style = labelStyle),
                    SizedBox(height = 2),
                    Row(
                        mainAxisAlignment = MainAxisAlignment.CENTER,
                        children = listOf(
                            Line(startX = 0, startY = 0, endX = 7, endY = 0, style = outline),
                            SizedBox(width = 4),
                            Line(startX = 0, startY = 0, endX = 0, endY = 7, style = outline),
                            SizedBox(width = 4),
                            Line(startX = 0, startY = 0, endX = 7, endY = 7, style = outline),
                        ),
                    ),
                    SizedBox(height = 6),
                    Text("CIRCLES (fill)", style = labelStyle),
                    SizedBox(height = 2),
                    Row(
                        mainAxisAlignment = MainAxisAlignment.CENTER,
                        children = listOf(
                            Circle(radius = 2, color = ink),
                            SizedBox(width = 3),
                            Circle(radius = 4, color = ink),
                            SizedBox(width = 3),
                            Circle(radius = 6, color = accent),
                        ),
                    ),
                    SizedBox(height = 6),
                    Text("CIRCLES (stroke)", style = labelStyle),
                    SizedBox(height = 2),
                    Row(
                        mainAxisAlignment = MainAxisAlignment.CENTER,
                        children = listOf(
                            Circle(radius = 3, style = outline),
                            SizedBox(width = 3),
                            Circle(radius = 5, style = outline),
                            SizedBox(width = 3),
                            Circle(radius = 7, style = accentOutline),
                        ),
                    ),
                ),
            ),
        )
    }
}

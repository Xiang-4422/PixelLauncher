package com.purride.pixeldemo.showcase.foundation

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Column
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.Path
import com.purride.pixelui.PixelPath
import com.purride.pixelui.PixelPathCommand
import com.purride.pixelui.PixelPoint
import com.purride.pixelui.Polygon
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object PolygonPathScene : DemoScene {
    override val id = "polygon_path"
    override val title = "POLYGON/PATH"
    override val description = "scanline polygon fill + MoveTo/LineTo path"

    override fun build(env: DemoEnv): Widget {
        val amber = PixelColor.fromRgb(255, 192, 64)
        val cyan = PixelColor.fromRgb(80, 220, 255)
        return Column(
            mainAxisAlignment = MainAxisAlignment.CENTER,
            spacing = 4,
            children = listOf(
                Text("POLYGON", style = TextStyle(color = PixelColor.White)),
                Row(
                    spacing = 4,
                    children = listOf(
                        Polygon(
                            points = listOf(PixelPoint(0, 6), PixelPoint(4, 0), PixelPoint(8, 6)),
                            color = amber,
                            filled = true,
                        ),
                        Polygon(
                            points = listOf(PixelPoint(0, 0), PixelPoint(8, 0), PixelPoint(8, 6), PixelPoint(0, 6)),
                            color = cyan,
                            filled = false,
                        ),
                    ),
                ),
                SizedBox(height = 2),
                Text("PATH", style = TextStyle(color = PixelColor.fromRgb(180, 180, 180))),
                Path(
                    path = PixelPath(
                        listOf(
                            PixelPathCommand.MoveTo(PixelPoint(0, 5)),
                            PixelPathCommand.LineTo(PixelPoint(3, 0)),
                            PixelPathCommand.LineTo(PixelPoint(6, 5)),
                            PixelPathCommand.LineTo(PixelPoint(9, 0)),
                        ),
                    ),
                    color = PixelColor.White,
                ),
            ),
        )
    }
}

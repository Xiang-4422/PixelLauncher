package com.purride.pixeldemo.showcase

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Circle
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.CustomPaint
import com.purride.pixelui.Image
import com.purride.pixelui.Line
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Path
import com.purride.pixelui.PixelGradient
import com.purride.pixelui.PixelGradientStop
import com.purride.pixelui.PixelPath
import com.purride.pixelui.PixelPathCommand
import com.purride.pixelui.PixelPoint
import com.purride.pixelui.PixelShapeStyle
import com.purride.pixelui.Polygon
import com.purride.pixelui.Row
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.Accent
import com.purride.pixeldemo.scaffold.Blue
import com.purride.pixeldemo.scaffold.ComponentShowcaseScaffold
import com.purride.pixeldemo.scaffold.Cyan
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.Green
import com.purride.pixeldemo.scaffold.Pink
import com.purride.pixeldemo.scaffold.Purple
import com.purride.pixeldemo.scaffold.Yellow
import com.purride.pixeldemo.scaffold.samplePanel
import com.purride.pixeldemo.scaffold.sectionTitle

object PaintMediaShowcaseScene : DemoScene {
    override val id = "components_paint_media"
    override val title = "绘制媒体"
    override val summary = "位图、图形原语、Path、CustomPaint 与 PixelCanvas"
    override val category = DemoCatalog.paint
    override val tags = setOf("paint", "image", "bitmap", "sprite", "canvas", "shape", "path")
    override val apis = setOf(
        "Image",
        "Sprite",
        "Line",
        "Circle",
        "Polygon",
        "Path",
        "CustomPaint",
        "PixelCanvas",
        "PixelBitmap",
        "PixelGradient",
    )
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = body())

    private fun body(): Widget =
        Column(
            children = listOf(
                sectionTitle("图形原语"),
                samplePanel(
                    title = "Line / Circle / Polygon / Path",
                    color = Purple,
                    child = Row(
                        children = listOf(
                            Container(width = 30, height = 24, child = Line(0, 20, 28, 2, PixelShapeStyle(color = Accent, strokeWidth = 2))),
                            Container(width = 24, height = 24, child = Circle(radius = 9, color = Cyan, filled = false)),
                            Container(
                                width = 30,
                                height = 24,
                                child = Polygon(
                                    points = listOf(PixelPoint(4, 18), PixelPoint(14, 3), PixelPoint(26, 18), PixelPoint(16, 14)),
                                    color = Green,
                                ),
                            ),
                            Container(
                                width = 34,
                                height = 24,
                                child = Path(
                                    path = PixelPath(
                                        listOf(
                                            PixelPathCommand.MoveTo(PixelPoint(2, 18)),
                                            PixelPathCommand.QuadraticTo(PixelPoint(12, 2), PixelPoint(22, 18)),
                                            PixelPathCommand.LineTo(PixelPoint(32, 8)),
                                        ),
                                    ),
                                    color = Pink,
                                    strokeWidth = 2,
                                ),
                            ),
                        ),
                        spacing = 3,
                    ),
                ),
                sectionTitle("位图和 Canvas"),
                samplePanel(
                    title = "Image / PixelBitmap",
                    color = Cyan,
                    child = Row(
                        children = listOf(
                            Image(bitmap = checkerBitmap()),
                            Text("1:1 blit", style = TextStyle(color = Cyan)),
                        ),
                        spacing = 4,
                        crossAxisAlignment = CrossAxisAlignment.CENTER,
                    ),
                ),
                samplePanel(
                    title = "CustomPaint / PixelCanvas",
                    color = Accent,
                    child = CustomPaint(width = 104, height = 34) {
                        fillGradientRect(
                            left = 0,
                            top = 0,
                            width = 104,
                            height = 34,
                            gradient = PixelGradient.Linear(
                                start = PixelPoint(0, 0),
                                end = PixelPoint(104, 0),
                                stops = listOf(
                                    PixelGradientStop(0f, Blue),
                                    PixelGradientStop(0.5f, Purple),
                                    PixelGradientStop(1f, Pink),
                                ),
                            ),
                        )
                        drawRect(2, 2, 100, 30, PixelColor.White)
                        drawCircle(18, 17, 8, Yellow, filled = false, strokeWidth = 2)
                        drawLine(34, 8, 94, 26, PixelColor.White, strokeWidth = 2)
                        drawPolygon(
                            points = listOf(PixelPoint(62, 5), PixelPoint(76, 16), PixelPoint(68, 28), PixelPoint(54, 22)),
                            color = Green,
                            filled = false,
                        )
                    },
                ),
                samplePanel(
                    title = "Sprite",
                    color = Green,
                    child = Text("Sprite 使用 PixelSpriteSheet；此页保留 API 搜索入口", style = TextStyle(color = Green)),
                ),
            ),
            spacing = 4,
            mainAxisSize = MainAxisSize.MIN,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        )

    private fun checkerBitmap(): PixelBitmap {
        val pixels = IntArray(16 * 16) { index ->
            val x = index % 16
            val y = index / 16
            when {
                (x + y) % 4 == 0 -> Accent.argb
                x in 5..10 && y in 5..10 -> Cyan.argb
                else -> PixelColor.fromRgb(24, 28, 32).argb
            }
        }
        return PixelBitmap(width = 16, height = 16, pixels = pixels)
    }
}

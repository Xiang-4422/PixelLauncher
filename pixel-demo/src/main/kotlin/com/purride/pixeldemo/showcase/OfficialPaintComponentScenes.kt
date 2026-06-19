package com.purride.pixeldemo.showcase

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelBitmapRegion
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelSpriteSheet
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
import com.purride.pixelui.Sprite
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.widgets.animated.AnimatedSprite
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.Accent
import com.purride.pixeldemo.scaffold.Blue
import com.purride.pixeldemo.scaffold.Cyan
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.Green
import com.purride.pixeldemo.scaffold.Pink
import com.purride.pixeldemo.scaffold.Purple
import com.purride.pixeldemo.scaffold.Yellow
import com.purride.pixeldemo.scaffold.samplePanel

val PaintOfficialComponentScenes: List<DemoScene> = listOf(
    paintScene("paint_line", "Line", "绘制一条像素直线", setOf("Line", "PixelShapeStyle")) {
        Container(width = 72, height = 28, borderColor = Accent, child = Line(4, 22, 66, 4, PixelShapeStyle(color = Accent, strokeWidth = 2)))
    },
    paintScene("paint_circle", "Circle", "绘制填充或描边圆形", setOf("Circle", "PixelShapeStyle")) {
        Row(children = listOf(Circle(radius = 9, color = Cyan), Circle(radius = 9, color = Pink, filled = false)), spacing = 6)
    },
    paintScene("paint_polygon", "Polygon", "按点列表绘制多边形", setOf("Polygon", "PixelPoint", "PixelShapeStyle")) {
        Container(width = 52, height = 28, borderColor = Green, child = Polygon(points = listOf(PixelPoint(4, 22), PixelPoint(22, 3), PixelPoint(46, 20), PixelPoint(24, 15)), color = Green))
    },
    paintScene("paint_path", "Path", "绘制 line/quadratic/cubic path", setOf("Path", "PixelPath", "PixelPathCommand")) {
        Container(
            width = 58,
            height = 28,
            borderColor = Pink,
            child = Path(
                path = PixelPath(
                    listOf(
                        PixelPathCommand.MoveTo(PixelPoint(3, 22)),
                        PixelPathCommand.QuadraticTo(PixelPoint(18, 3), PixelPoint(34, 22)),
                        PixelPathCommand.LineTo(PixelPoint(54, 8)),
                    ),
                ),
                color = Pink,
                strokeWidth = 2,
            ),
        )
    },
    paintScene("paint_custom_paint", "CustomPaint", "在 PixelCanvas 中批量绘制", setOf("CustomPaint", "PixelCanvas", "PixelGradient")) {
        CustomPaint(width = 104, height = 34) {
            fillGradientRect(
                left = 0,
                top = 0,
                width = 104,
                height = 34,
                gradient = PixelGradient.Linear(
                    start = PixelPoint(0, 0),
                    end = PixelPoint(104, 0),
                    stops = listOf(PixelGradientStop(0f, Blue), PixelGradientStop(0.5f, Purple), PixelGradientStop(1f, Pink)),
                ),
            )
            drawRect(2, 2, 100, 30, PixelColor.White)
            drawCircle(18, 17, 8, Yellow, filled = false, strokeWidth = 2)
            drawLine(34, 8, 94, 26, PixelColor.White, strokeWidth = 2)
        }
    },
    ComponentExampleScene(
        id = "paint_image",
        title = "Bitmap & Sprite",
        summary = "PixelBitmap、SpriteSheet 与精灵动画",
        category = DemoCatalog.paint,
        tags = setOf("component", "paint", "bitmap", "sprite", "animation"),
        apis = setOf("Image", "PixelBitmap", "Sprite", "PixelSpriteSheet", "PixelBitmapRegion", "AnimatedSprite", "PixelTickerProvider"),
        bodyBuilder = { env ->
            paintBody(
                listOf(
                    samplePanel(
                        title = "Image",
                        color = Cyan,
                        child = Row(children = listOf(Image(bitmap = officialBitmap()), Text("PixelBitmap", style = TextStyle(color = Cyan))), spacing = 4, crossAxisAlignment = CrossAxisAlignment.CENTER),
                    ),
                    samplePanel(
                        title = "Sprite",
                        color = Green,
                        child = Row(children = listOf(Sprite(sheet = officialSpriteSheet(), frameIndex = 0), Sprite(sheet = officialSpriteSheet(), frameIndex = 1)), spacing = 4),
                    ),
                    samplePanel(
                        title = "AnimatedSprite",
                        color = Accent,
                        child = AnimatedSprite(sheet = officialSpriteSheet(), fps = 4, vsync = env.vsync),
                    ),
                ),
            )
        },
    ),
)

private fun paintScene(
    id: String,
    title: String,
    summary: String,
    apis: Set<String>,
    body: (DemoEnv) -> Widget,
): DemoScene =
    ComponentExampleScene(
        id = id,
        title = title,
        summary = summary,
        category = DemoCatalog.paint,
        tags = setOf("component", "paint", title.lowercase()),
        apis = apis,
        bodyBuilder = { env ->
            paintBody(
                listOf(
                    samplePanel(title = "Example", color = Purple, child = body(env)),
                ),
            )
        },
    )

private fun paintBody(children: List<Widget>): Widget =
    Column(
        children = children,
        spacing = 4,
        mainAxisSize = MainAxisSize.MIN,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
    )

private fun officialBitmap(): PixelBitmap {
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

private fun officialSpriteSheet(): PixelSpriteSheet {
    val bitmap = PixelBitmap(
        width = 16,
        height = 8,
        pixels = IntArray(16 * 8) { index ->
            val x = index % 16
            val y = index / 16
            when {
                x < 8 && y in 2..5 -> Green.argb
                x >= 8 && (x + y) % 2 == 0 -> Accent.argb
                else -> PixelColor.Transparent.argb
            }
        },
    )
    return PixelSpriteSheet(
        bitmap = bitmap,
        frames = listOf(
            PixelBitmapRegion(left = 0, top = 0, width = 8, height = 8),
            PixelBitmapRegion(left = 8, top = 0, width = 8, height = 8),
        ),
    )
}

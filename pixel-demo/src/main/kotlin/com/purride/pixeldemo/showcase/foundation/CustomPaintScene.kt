package com.purride.pixeldemo.showcase.foundation

import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelBlendMode
import com.purride.pixelui.Column
import com.purride.pixelui.CustomPaint
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.PixelGradient
import com.purride.pixelui.PixelGradientStop
import com.purride.pixelui.PixelPath
import com.purride.pixelui.PixelPoint
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.animation.PixelGradientTween
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.host.PixelFrameScheduler
import com.purride.pixelui.widgets.animated.TweenAnimationBuilder
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import kotlin.time.Duration.Companion.milliseconds

object CustomPaintScene : DemoScene {
    override val id = "custom_paint"
    override val title = "CustomPaint"
    override val description = "Canvas-style batched pixel drawing"

    override fun build(env: DemoEnv): Widget {
        val beginGradient = PixelGradient.Linear(
            start = PixelPoint(2, 2),
            end = PixelPoint(15, 2),
            stops = listOf(
                PixelGradientStop(0f, PixelColor.fromRgb(40, 80, 180)),
                PixelGradientStop(1f, PixelColor.fromRgb(255, 192, 64)),
            ),
        )
        val endGradient = PixelGradient.Linear(
            start = PixelPoint(2, 7),
            end = PixelPoint(15, 2),
            stops = listOf(
                PixelGradientStop(0f, PixelColor.fromRgb(255, 192, 64)),
                PixelGradientStop(1f, PixelColor.fromRgb(80, 180, 110)),
            ),
        )
        return Column(
            mainAxisAlignment = MainAxisAlignment.CENTER,
            spacing = 4,
            children = listOf(
                Text("PIXEL CANVAS", style = TextStyle(color = PixelColor.fromRgb(180, 180, 180))),
                TweenAnimationBuilder(
                    tween = PixelGradientTween(beginGradient, endGradient),
                    duration = 900.milliseconds,
                    vsync = PixelTickerProvider(PixelFrameScheduler.Default),
                    curve = Curves.Step(8),
                ) { _, gradient ->
                    CustomPaint(width = 42, height = 28) {
                        fillRect(0, 0, 42, 28, PixelColor.fromRgb(20, 20, 20))
                        fillGradientRect(
                            left = 2,
                            top = 2,
                            width = 14,
                            height = 6,
                            gradient = gradient,
                        )
                        fillRect(34, 2, 4, 4, PixelColor.White, blendMode = PixelBlendMode.Clear)
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
                    }
                },
            ),
        )
    }
}

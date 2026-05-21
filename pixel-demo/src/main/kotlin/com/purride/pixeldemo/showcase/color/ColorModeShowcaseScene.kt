package com.purride.pixeldemo.showcase.color

import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelColorMode
import com.purride.pixelui.Alignment
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.ListenableBuilder
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.PixelAnimationController
import com.purride.pixelui.animation.PixelColorTween
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.host.PixelFrameScheduler
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import kotlin.time.Duration.Companion.seconds

object ColorModeShowcaseScene : DemoScene {
    override val id = "color_mode_showcase"
    override val title = "Color Mode"
    override val description = "彩色模式：PixelColor 填充/边框/文本 + PixelColorTween 补间动画"
    override val colorMode = PixelColorMode.Color

    override fun build(env: DemoEnv): Widget =
        ColorModeShowcaseWidget(frameScheduler = env.hostView.frameScheduler)
}

private class ColorModeShowcaseWidget(
    internal val frameScheduler: PixelFrameScheduler,
) : StatefulWidget() {
    override val key: Any? = null
    override fun createState() = ColorModeShowcaseState()
}

private class ColorModeShowcaseState : State<ColorModeShowcaseWidget>() {

    private val colorTween = PixelColorTween(
        begin = PixelColor.fromRgb(200, 50, 50),
        end = PixelColor.fromRgb(50, 50, 200),
    )
    private lateinit var tickerProvider: PixelTickerProvider
    private lateinit var controller: PixelAnimationController

    override fun initState() {
        super.initState()
        tickerProvider = PixelTickerProvider(widget.frameScheduler)
        controller = PixelAnimationController(
            duration = 2.seconds,
            vsync = tickerProvider,
        )
        controller.repeat(reverse = true)
    }

    override fun dispose() {
        controller.dispose()
        super.dispose()
    }

    override fun build(context: BuildContext): Widget {
        return Column(
            mainAxisAlignment = MainAxisAlignment.CENTER,
            crossAxisAlignment = CrossAxisAlignment.CENTER,
            children = listOf(buildColorContent()),
        )
    }

    private fun buildColorContent(): Widget {
        return ListenableBuilder(listenable = controller) { _ ->
            val animColor = colorTween.lerp(controller.value)
            Column(
                mainAxisSize = MainAxisSize.MIN,
                crossAxisAlignment = CrossAxisAlignment.CENTER,
                children = listOf(
                    Container(
                        width = 48,
                        height = 16,
                        fillColor = animColor,
                        borderColor = PixelColor.fromRgb(255, 255, 0),
                        alignment = Alignment.CENTER,
                        child = Text(
                            data = "COLOR",
                            color = PixelColor.fromRgb(255, 255, 255),
                        ),
                    ),
                    SizedBox(height = 4),
                    Row(
                        mainAxisAlignment = MainAxisAlignment.CENTER,
                        children = listOf(
                            swatch(PixelColor.fromRgb(220, 60, 60), "R"),
                            SizedBox(width = 2),
                            swatch(PixelColor.fromRgb(60, 200, 60), "G"),
                            SizedBox(width = 2),
                            swatch(PixelColor.fromRgb(60, 60, 220), "B"),
                        ),
                    ),
                    SizedBox(height = 4),
                    OutlinedButton(
                        text = "GREEN BORDER",
                        onPressed = null,
                        borderColor = PixelColor.fromRgb(0, 200, 80),
                    ),
                ),
            )
        }
    }

    private fun swatch(color: PixelColor, label: String): Widget {
        return Container(
            width = 14,
            height = 10,
            fillColor = color,
            borderColor = PixelColor.fromRgb(200, 200, 200),
            alignment = Alignment.CENTER,
            child = Text(data = label, color = PixelColor.fromRgb(255, 255, 255)),
        )
    }
}

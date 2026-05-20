package com.purride.pixeldemo.showcase.animation

import com.purride.pixelcore.PixelTone
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.ListenableBuilder
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.IntTween
import com.purride.pixelui.animation.PixelAnimationController
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.animation.PixelToneTween
import com.purride.pixelui.host.PixelFrameScheduler
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import kotlin.time.Duration.Companion.seconds

object AnimationCoreScene : DemoScene {
    override val id = "animation_core"
    override val title = "Animation Core"
    override val description = "Layer 1：Controller / Tween / Curves 端到端演示"

    override fun build(env: DemoEnv): Widget =
        AnimationCoreWidget(frameScheduler = env.hostView.frameScheduler)
}

private class AnimationCoreWidget(
    private val frameScheduler: PixelFrameScheduler,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = AnimationCoreState()

    inner class AnimationCoreState : State<AnimationCoreWidget>() {
        private lateinit var vsync: PixelTickerProvider
        private lateinit var controller: PixelAnimationController
        private val widthTween = IntTween(begin = 8, end = 64)
        private val toneTween = PixelToneTween(begin = PixelTone.ON, end = PixelTone.ACCENT)

        override fun initState() {
            vsync = PixelTickerProvider(widget.frameScheduler)
            controller = PixelAnimationController(duration = 1.seconds, vsync = vsync)
        }

        override fun dispose() {
            controller.dispose()
        }

        override fun build(context: BuildContext): Widget {
            return Padding(
                all = 8,
                child = Column(
                    crossAxisAlignment = CrossAxisAlignment.START,
                    spacing = 8,
                    children = listOf(
                        Text("Animation Core Demo", style = TextStyle.Accent),
                        Text("width: IntTween(8→64)  tone: PixelToneTween(ON→ACCENT)", style = TextStyle.Default),
                        SizedBox(height = 4),
                        ListenableBuilder(listenable = controller) { ctx ->
                            val t = controller.value
                            val width = widthTween.lerp(t)
                            val tone = toneTween.lerp(t)
                            Container(
                                width = width,
                                height = 16,
                                fillTone = tone,
                                borderTone = PixelTone.ON,
                            )
                        },
                        SizedBox(height = 4),
                        ListenableBuilder(listenable = controller) { ctx ->
                            Text("status: ${controller.status}  value: ${"%.2f".format(controller.value)}", style = TextStyle.Default)
                        },
                        SizedBox(height = 4),
                        Row(
                            spacing = 4,
                            children = listOf(
                                OutlinedButton(
                                    text = "Forward",
                                    onPressed = { controller.forward() },
                                ),
                                OutlinedButton(
                                    text = "Reverse",
                                    onPressed = { controller.reverse() },
                                ),
                                OutlinedButton(
                                    text = "Repeat",
                                    onPressed = { controller.repeat(reverse = true) },
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }
    }
}

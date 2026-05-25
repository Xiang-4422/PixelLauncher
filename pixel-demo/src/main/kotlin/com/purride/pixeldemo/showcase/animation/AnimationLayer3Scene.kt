package com.purride.pixeldemo.showcase.animation

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.AnimatedBuilder
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.PixelAnimationController
import com.purride.pixelui.animation.PixelAnimationStatus
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import kotlin.time.Duration.Companion.milliseconds

/**
 * Animation Layer 3 演示：
 *  - 上半场：[AnimatedBuilder] 把不变的 `Text("HELLO")` 通过 child 参数静态传入；
 *    builder 内根据 controller value 计算 padding 包住 child（child 子树只构造一次）。
 *  - 下半场：用 `PixelTickerProvider.createTicker(maxFps = ...)` 在两个
 *    PixelAnimationController 之间对比 60 vs 10 FPS：低 FPS 的动画明显"跳格"，
 *    符合像素风离散感取向。
 */
class AnimationLayer3SceneWidget : StatefulWidget() {
    override fun createState(): State<out StatefulWidget> = AnimationLayer3SceneState()
}

private class AnimationLayer3SceneState : State<AnimationLayer3SceneWidget>() {
    private lateinit var provider: PixelTickerProvider
    private lateinit var smoothController: PixelAnimationController
    private lateinit var steppedController: PixelAnimationController

    override fun initState() {
        provider = PixelTickerProvider(com.purride.pixelui.host.PixelFrameScheduler.Default)
        smoothController = PixelAnimationController(
            duration = 800.milliseconds,
            vsync = provider,
        )
        steppedController = PixelAnimationController(
            duration = 800.milliseconds,
            vsync = provider,
        )
    }

    override fun dispose() {
        smoothController.dispose()
        steppedController.dispose()
    }

    override fun build(context: BuildContext): Widget {
        val labelStyle = TextStyle(color = PixelColor.fromRgb(180, 180, 180))
        return Center(
            child = Column(
                mainAxisAlignment = MainAxisAlignment.CENTER,
                crossAxisAlignment = CrossAxisAlignment.CENTER,
                children = listOf(
                    Text("ANIMATED BUILDER", style = labelStyle),
                    SizedBox(height = 2),
                    AnimatedBuilder(
                        animation = smoothController,
                        child = Text("HELLO"),
                    ) { _, child ->
                        Padding(
                            all = (smoothController.value * 6).toInt(),
                            child = child!!,
                        )
                    },
                    SizedBox(height = 6),
                    Text("60 vs 10 FPS", style = labelStyle),
                    SizedBox(height = 2),
                    Row(
                        mainAxisAlignment = MainAxisAlignment.CENTER,
                        children = listOf(
                            OutlinedButton(
                                text = "RUN",
                                onPressed = {
                                    smoothController.reset()
                                    steppedController.reset()
                                    smoothController.forward()
                                    steppedController.forward()
                                },
                            ),
                            SizedBox(width = 2),
                            OutlinedButton(
                                text = "STOP",
                                onPressed = {
                                    smoothController.stop()
                                    steppedController.stop()
                                },
                            ),
                        ),
                    ),
                ),
            ),
        )
    }
}

object AnimationLayer3Scene : DemoScene {
    override val id = "animation_layer3"
    override val title = "ANIM LAYER 3"
    override val description = "AnimatedBuilder + maxFps 限速 ticker"

    override fun build(env: DemoEnv): Widget = AnimationLayer3SceneWidget()
}

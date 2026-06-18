package com.purride.pixeldemo.showcase

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.AsyncBuilder
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.ListenableBuilder
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelAsyncSnapshot
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.Row
import com.purride.pixelui.State
import com.purride.pixelui.StatefulBuilder
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.ValueListenableBuilder
import com.purride.pixelui.ValueNotifier
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.animation.IntTween
import com.purride.pixelui.pixelAsyncSourceOf
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.Accent
import com.purride.pixeldemo.scaffold.Blue
import com.purride.pixeldemo.scaffold.ComponentShowcaseScaffold
import com.purride.pixeldemo.scaffold.Cyan
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.Green
import com.purride.pixeldemo.scaffold.Muted
import com.purride.pixeldemo.scaffold.Pink
import com.purride.pixeldemo.scaffold.Purple
import com.purride.pixeldemo.scaffold.Yellow
import com.purride.pixeldemo.scaffold.samplePanel
import com.purride.pixeldemo.scaffold.sectionTitle

object AnimationStateShowcaseScene : DemoScene {
    override val id = "components_animation_state"
    override val title = "动画状态"
    override val summary = "监听构建、异步快照、状态构建、Tween 和曲线"
    override val category = DemoCatalog.animation
    override val tags = setOf("animation", "state", "builder", "tween", "curve", "async")
    override val apis = setOf(
        "AnimatedBuilder",
        "TweenAnimationBuilder",
        "Tween",
        "Curves",
        "PixelAnimationController",
        "PixelTickerProvider",
        "AsyncBuilder",
        "ValueListenableBuilder",
        "StatefulBuilder",
    )
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = AnimationStateBody())
}

private class AnimationStateBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = AnimationState()

    private class AnimationState : State<AnimationStateBody>() {
        private val notifier = ValueNotifier(2)
        private val source = pixelAsyncSourceOf("READY")
        private var progress = 0.42f
        private var local = 0

        override fun build(context: BuildContext): Widget =
            Column(
                children = listOf(
                    sectionTitle("Builder 状态"),
                    samplePanel(
                        title = "ValueListenableBuilder / ListenableBuilder",
                        color = Yellow,
                        child = Column(
                            children = listOf(
                                Row(
                                    children = listOf(
                                        OutlinedButton(
                                            text = "-",
                                            onPressed = {
                                                notifier.value = (notifier.value - 1).coerceAtLeast(0)
                                                progress = notifier.value / 6f
                                                setState {}
                                            },
                                        ),
                                        OutlinedButton(
                                            text = "+",
                                            onPressed = {
                                                notifier.value = (notifier.value + 1).coerceAtMost(6)
                                                progress = notifier.value / 6f
                                                setState {}
                                            },
                                        ),
                                        ValueListenableBuilder(notifier) { _, value ->
                                            Text("value=$value", style = TextStyle(color = Yellow))
                                        },
                                    ),
                                    spacing = 2,
                                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                                ),
                                ListenableBuilder(notifier) {
                                    ProgressBar(progress = notifier.value / 6f, width = 72, color = Yellow)
                                },
                            ),
                            spacing = 3,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                    ),
                    samplePanel(
                        title = "AsyncBuilder / PixelAsyncSnapshot",
                        color = Cyan,
                        child = AsyncBuilder(source = source) { _, snapshot ->
                            when (snapshot) {
                                PixelAsyncSnapshot.Loading -> Text("LOADING", style = TextStyle(color = Muted))
                                is PixelAsyncSnapshot.Success -> Text("SUCCESS ${snapshot.value}", style = TextStyle(color = Cyan))
                                is PixelAsyncSnapshot.Failure -> Text("FAILURE", style = TextStyle(color = Pink))
                            }
                        },
                    ),
                    samplePanel(
                        title = "StatefulBuilder",
                        color = Green,
                        child = StatefulBuilder { _, setLocalState ->
                            Column(
                                children = listOf(
                                    OutlinedButton(
                                        text = "LOCAL +",
                                        onPressed = { setLocalState { local += 1 } },
                                        borderColor = Green,
                                    ),
                                    Text("local=$local", style = TextStyle(color = Green)),
                                ),
                                spacing = 2,
                            )
                        },
                    ),
                    sectionTitle("Tween / Curve 视觉矩阵"),
                    samplePanel(
                        title = "Curves / Tween",
                        color = Purple,
                        child = Column(
                            children = listOf(
                                tweenRow("Linear", Curves.Linear.transform(progress), Blue),
                                tweenRow("EaseIn", Curves.EaseIn.transform(progress), Purple),
                                tweenRow("EaseOut", Curves.EaseOut.transform(progress), Pink),
                                tweenRow("Step(4)", Curves.Step(4).transform(progress), Accent),
                                Text("IntTween 0..64 -> ${IntTween(0, 64).lerp(progress)}", style = TextStyle(color = Muted)),
                            ),
                            spacing = 2,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                    ),
                ),
                spacing = 4,
                mainAxisSize = MainAxisSize.MIN,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )

        private fun tweenRow(label: String, value: Float, color: PixelColor): Widget =
            Row(
                children = listOf(
                    Container(width = 36, child = Text(label, style = TextStyle(color = color))),
                    ProgressBar(progress = value, width = 64, color = color),
                ),
                spacing = 3,
                crossAxisAlignment = CrossAxisAlignment.CENTER,
            )
    }
}

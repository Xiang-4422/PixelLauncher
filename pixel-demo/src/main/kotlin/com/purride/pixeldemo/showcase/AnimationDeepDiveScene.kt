package com.purride.pixeldemo.showcase

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Alignment
import com.purride.pixelui.AnimatedBuilder
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.Row
import com.purride.pixelui.Stack
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.animation.IntTween
import com.purride.pixelui.animation.PixelAnimationController
import com.purride.pixelui.animation.PixelAnimationStatus
import com.purride.pixelui.widgets.animated.AnimatedAlign
import com.purride.pixelui.widgets.animated.AnimatedContainer
import com.purride.pixelui.widgets.animated.AnimatedOpacity
import com.purride.pixelui.widgets.animated.AnimatedPadding
import com.purride.pixelui.widgets.animated.AnimatedPositioned
import com.purride.pixelui.widgets.animated.AnimatedSwitcher
import com.purride.pixelui.widgets.animated.TweenAnimationBuilder
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
import kotlin.time.Duration.Companion.milliseconds

object AnimationDeepDiveScene : DemoScene {
    override val id = "deep_animation_runtime"
    override val title = "动画运行时"
    override val summary = "Animated*、TweenAnimationBuilder、AnimationController 与 ticker"
    override val category = DemoCatalog.animation
    override val tags = setOf("animation", "ticker", "animated", "tween", "runtime")
    override val apis = setOf(
        "AnimatedBuilder",
        "AnimatedContainer",
        "AnimatedOpacity",
        "AnimatedPadding",
        "AnimatedAlign",
        "AnimatedPositioned",
        "AnimatedSwitcher",
        "TweenAnimationBuilder",
        "PixelAnimationController",
        "PixelAnimationStatus",
        "PixelTickerProvider",
        "CurvedAnimation",
        "PixelColorTween",
        "PixelGradientTween",
        "OffsetTween",
        "EdgeInsetsTween",
    )
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = AnimationDeepDiveBody(env))
}

private class AnimationDeepDiveBody(
    private val env: DemoEnv,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = AnimationDeepDiveState()

    private class AnimationDeepDiveState : State<AnimationDeepDiveBody>() {
        private lateinit var controller: PixelAnimationController
        private var expanded = false
        private var switched = false
        private var ended = 0

        override fun initState() {
            controller = PixelAnimationController(
                duration = 700.milliseconds,
                vsync = widget.env.vsync,
                initialValue = 0f,
            )
            controller.addListener {
                if (controller.status == PixelAnimationStatus.Completed) ended += 1
                setState {}
            }
        }

        override fun dispose() {
            controller.dispose()
        }

        override fun build(context: BuildContext): Widget =
            Column(
                children = listOf(
                    Row(
                        children = listOf(
                            OutlinedButton(
                                text = "TOGGLE",
                                onPressed = {
                                    expanded = !expanded
                                    switched = !switched
                                    controller.forward(from = 0f)
                                    setState {}
                                },
                                borderColor = Accent,
                            ),
                            Text("ticker=${widget.env.vsync.activeTickerCount}", style = TextStyle(color = Muted)),
                            Text("end=$ended", style = TextStyle(color = Yellow)),
                        ),
                        spacing = 3,
                        crossAxisAlignment = CrossAxisAlignment.CENTER,
                    ),
                    sectionTitle("Animated widgets"),
                    samplePanel(
                        title = "Container / opacity / padding / align",
                        color = Cyan,
                        child = Column(
                            children = listOf(
                                AnimatedContainer(
                                    duration = 400.milliseconds,
                                    vsync = widget.env.vsync,
                                    curve = Curves.Step(6),
                                    width = if (expanded) 86 else 42,
                                    height = if (expanded) 18 else 12,
                                    padding = EdgeInsets.all(if (expanded) 4 else 1),
                                    borderColor = if (expanded) Accent else Cyan,
                                    child = AnimatedOpacity(
                                        opacity = if (expanded) 1f else 0.5f,
                                        duration = 300.milliseconds,
                                        vsync = widget.env.vsync,
                                        child = Text("ANIM BOX", style = TextStyle(color = PixelColor.White)),
                                    ),
                                ),
                                Container(
                                    width = 100,
                                    height = 22,
                                    borderColor = Blue,
                                    child = AnimatedAlign(
                                        alignment = if (expanded) Alignment.CENTER_END else Alignment.CENTER_START,
                                        duration = 300.milliseconds,
                                        vsync = widget.env.vsync,
                                        child = AnimatedPadding(
                                            padding = EdgeInsets.all(if (expanded) 3 else 0),
                                            duration = 300.milliseconds,
                                            vsync = widget.env.vsync,
                                            child = Container(width = 18, height = 8, fillColor = Pink),
                                        ),
                                    ),
                                ),
                            ),
                            spacing = 3,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                    ),
                    samplePanel(
                        title = "Positioned / Switcher / TweenAnimationBuilder",
                        color = Purple,
                        child = Column(
                            children = listOf(
                                Container(
                                    width = 110,
                                    height = 28,
                                    borderColor = Purple,
                                    child = Stack(
                                        children = listOf(
                                            AnimatedPositioned(
                                                duration = 350.milliseconds,
                                                vsync = widget.env.vsync,
                                                left = if (expanded) 74 else 4,
                                                top = if (expanded) 14 else 4,
                                                child = Container(width = 20, height = 8, fillColor = Green),
                                            ),
                                        ),
                                    ),
                                ),
                                AnimatedSwitcher(
                                    duration = 250.milliseconds,
                                    vsync = widget.env.vsync,
                                    child = Text(
                                        if (switched) "SWITCH B" else "SWITCH A",
                                        key = if (switched) "b" else "a",
                                        style = TextStyle(color = if (switched) Pink else Green),
                                    ),
                                ),
                                TweenAnimationBuilder(
                                    tween = IntTween(0, if (expanded) 88 else 24),
                                    duration = 400.milliseconds,
                                    vsync = widget.env.vsync,
                                ) { _, value ->
                                    ProgressBar(progress = value / 88f, width = 88, color = Accent)
                                },
                            ),
                            spacing = 3,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                    ),
                    samplePanel(
                        title = "AnimatedBuilder / PixelAnimationController",
                        color = Yellow,
                        child = Column(
                            children = listOf(
                                AnimatedBuilder(animation = controller, child = Text("STATIC CHILD", style = TextStyle(color = Muted))) { _, child ->
                                    Row(
                                        children = listOf(
                                            ProgressBar(progress = controller.value, width = 80, color = Yellow),
                                            child ?: Text(""),
                                        ),
                                        spacing = 2,
                                    )
                                },
                                Text("status=${controller.status} value=${(controller.value * 100).toInt()}%", style = TextStyle(color = Yellow)),
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
    }
}

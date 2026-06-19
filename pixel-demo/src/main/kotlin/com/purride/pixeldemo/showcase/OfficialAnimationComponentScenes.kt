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
import com.purride.pixeldemo.scaffold.Cyan
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.Green
import com.purride.pixeldemo.scaffold.Muted
import com.purride.pixeldemo.scaffold.Pink
import com.purride.pixeldemo.scaffold.Purple
import com.purride.pixeldemo.scaffold.Yellow
import com.purride.pixeldemo.scaffold.samplePanel
import kotlin.time.Duration.Companion.milliseconds

val AnimationOfficialComponentScenes: List<DemoScene> = listOf(
    animationScene(
        id = "animation_animated_container",
        title = "Animated Widgets",
        summary = "隐式动画组件集合",
        examples = listOf(
            "AnimatedContainer" to AnimationKind.Container,
            "AnimatedOpacity" to AnimationKind.Opacity,
            "AnimatedPadding" to AnimationKind.Padding,
            "AnimatedAlign" to AnimationKind.Align,
            "AnimatedPositioned" to AnimationKind.Positioned,
            "AnimatedSwitcher" to AnimationKind.Switcher,
        ),
        apis = setOf("AnimatedContainer", "AnimatedOpacity", "AnimatedPadding", "AnimatedAlign", "AnimatedPositioned", "AnimatedSwitcher", "Curves", "PixelTickerProvider"),
    ),
    animationScene(
        id = "animation_tween_animation_builder",
        title = "Animation Builders",
        summary = "Tween 与 controller 驱动的动画构建",
        examples = listOf(
            "TweenAnimationBuilder" to AnimationKind.TweenBuilder,
            "AnimatedBuilder" to AnimationKind.AnimatedBuilder,
        ),
        apis = setOf("TweenAnimationBuilder", "IntTween", "AnimatedBuilder", "PixelAnimationController", "PixelAnimationStatus"),
    ),
)

private enum class AnimationKind {
    Container,
    Opacity,
    Padding,
    Align,
    Positioned,
    Switcher,
    TweenBuilder,
    AnimatedBuilder,
}

private fun animationScene(
    id: String,
    title: String,
    summary: String,
    examples: List<Pair<String, AnimationKind>>,
    apis: Set<String>,
): DemoScene =
    ComponentExampleScene(
        id = id,
        title = title,
        summary = summary,
        category = DemoCatalog.animation,
        tags = setOf("component", "animation", title.lowercase()),
        apis = apis,
        bodyBuilder = { env -> AnimationOfficialBody(env = env, examples = examples) },
    )

private class AnimationOfficialBody(
    private val env: DemoEnv,
    private val examples: List<Pair<String, AnimationKind>>,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = AnimationOfficialState()

    private class AnimationOfficialState : State<AnimationOfficialBody>() {
        private lateinit var controller: PixelAnimationController
        private var expanded = false
        private var completed = 0

        override fun initState() {
            controller = PixelAnimationController(duration = 600.milliseconds, vsync = widget.env.vsync)
            controller.addListener {
                if (controller.status == PixelAnimationStatus.Completed) completed += 1
                setState {}
            }
        }

        override fun dispose() {
            controller.dispose()
        }

        override fun build(context: BuildContext): Widget {
            val controls = Row(
                children = listOf(
                    OutlinedButton(
                        text = "TOGGLE",
                        onPressed = {
                            expanded = !expanded
                            controller.forward(from = 0f)
                            setState {}
                        },
                        borderColor = Accent,
                    ),
                    Text("tick=${widget.env.vsync.activeTickerCount}", style = TextStyle(color = Muted)),
                    Text("end=$completed", style = TextStyle(color = Yellow)),
                ),
                spacing = 3,
                crossAxisAlignment = CrossAxisAlignment.CENTER,
            )
            val panels = widget.examples.map { (title, kind) ->
                samplePanel(title = title, color = Accent, child = example(kind))
            }
            return Column(
                children = listOf(controls) + panels,
                spacing = 4,
                mainAxisSize = MainAxisSize.MIN,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }

        private fun example(kind: AnimationKind): Widget = when (kind) {
            AnimationKind.Container -> AnimatedContainer(
                duration = 400.milliseconds,
                vsync = widget.env.vsync,
                curve = Curves.Step(6),
                width = if (expanded) 88 else 42,
                height = if (expanded) 18 else 12,
                padding = EdgeInsets.all(if (expanded) 4 else 1),
                borderColor = if (expanded) Accent else Cyan,
                child = Text("BOX", style = TextStyle(color = PixelColor.White)),
            )
            AnimationKind.Opacity -> AnimatedOpacity(
                opacity = if (expanded) 1f else 0.5f,
                duration = 350.milliseconds,
                vsync = widget.env.vsync,
                child = Container(width = 48, height = 14, fillColor = Pink),
            )
            AnimationKind.Padding -> AnimatedPadding(
                padding = EdgeInsets.all(if (expanded) 5 else 0),
                duration = 350.milliseconds,
                vsync = widget.env.vsync,
                child = Container(width = 42, height = 12, fillColor = Green),
            )
            AnimationKind.Align -> Container(
                width = 100,
                height = 22,
                borderColor = Blue,
                child = AnimatedAlign(
                    alignment = if (expanded) Alignment.CENTER_END else Alignment.CENTER_START,
                    duration = 350.milliseconds,
                    vsync = widget.env.vsync,
                    child = Container(width = 18, height = 8, fillColor = Blue),
                ),
            )
            AnimationKind.Positioned -> Container(
                width = 110,
                height = 30,
                borderColor = Purple,
                child = Stack(
                    children = listOf(
                        AnimatedPositioned(
                            duration = 350.milliseconds,
                            vsync = widget.env.vsync,
                            left = if (expanded) 76 else 4,
                            top = if (expanded) 16 else 4,
                            child = Container(width = 20, height = 8, fillColor = Green),
                        ),
                    ),
                ),
            )
            AnimationKind.Switcher -> AnimatedSwitcher(
                duration = 250.milliseconds,
                vsync = widget.env.vsync,
                child = Text(
                    if (expanded) "SWITCH B" else "SWITCH A",
                    key = if (expanded) "switch-b" else "switch-a",
                    style = TextStyle(color = if (expanded) Pink else Green),
                ),
            )
            AnimationKind.TweenBuilder -> TweenAnimationBuilder(
                tween = IntTween(0, if (expanded) 88 else 24),
                duration = 400.milliseconds,
                vsync = widget.env.vsync,
            ) { _, value ->
                ProgressBar(progress = value / 88f, width = 88, color = Accent)
            }
            AnimationKind.AnimatedBuilder -> AnimatedBuilder(animation = controller, child = Text("STATIC", style = TextStyle(color = Muted))) { _, child ->
                Row(
                    children = listOf(
                        ProgressBar(progress = controller.value, width = 80, color = Yellow),
                        child ?: Text(""),
                    ),
                    spacing = 2,
                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                )
            }
        }
    }
}

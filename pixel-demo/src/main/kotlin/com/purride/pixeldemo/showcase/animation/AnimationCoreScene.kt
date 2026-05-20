package com.purride.pixeldemo.showcase.animation

import com.purride.pixelcore.PixelTone
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.ListenableBuilder
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.Positioned
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Stack
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.Curve
import com.purride.pixelui.animation.CurvedAnimation
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.animation.IntTween
import com.purride.pixelui.animation.PixelAnimationController
import com.purride.pixelui.animation.PixelAnimationStatus
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.host.PixelFrameScheduler
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object AnimationCoreScene : DemoScene {
    override val id = "animation_core"
    override val title = "Animation Core"
    override val description = "六条曲线同帧对比：Linear / EaseIn / EaseOut / EaseInOut / Step(4) / Step(8)"

    override fun build(env: DemoEnv): Widget =
        AnimationCoreWidget(frameScheduler = env.hostView.frameScheduler)
}

private data class CurveEntry(val label: String, val curve: Curve)

private val CURVES = listOf(
    CurveEntry("LINEAR  ", Curves.Linear),
    CurveEntry("EASE IN ", Curves.EaseIn),
    CurveEntry("EASE OUT", Curves.EaseOut),
    CurveEntry("EASE I/O", Curves.EaseInOut),
    CurveEntry("STEP  4 ", Curves.Step(4)),
    CurveEntry("STEP  8 ", Curves.Step(8)),
)

private val DURATIONS = listOf(500.milliseconds, 1.seconds, 2.seconds)
private const val TRACK_W = 80
private const val DOT_W = 4
private val DOT_TWEEN = IntTween(0, TRACK_W - DOT_W)

private class AnimationCoreWidget(
    private val frameScheduler: PixelFrameScheduler,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = AnimationCoreState()

    inner class AnimationCoreState : State<AnimationCoreWidget>() {
        private lateinit var vsync: PixelTickerProvider
        private lateinit var controller: PixelAnimationController
        private var durationIndex = 1

        override fun initState() {
            vsync = PixelTickerProvider(widget.frameScheduler)
            controller = PixelAnimationController(duration = DURATIONS[durationIndex], vsync = vsync)
        }

        override fun dispose() {
            controller.dispose()
        }

        private fun setDuration(index: Int) {
            setState {
                durationIndex = index
                controller.dispose()
                controller = PixelAnimationController(duration = DURATIONS[index], vsync = vsync)
            }
        }

        override fun build(context: BuildContext): Widget {
            return Column(
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                children = listOf(
                    // ── curve tracks ─────────────────────────────────────────
                    ListenableBuilder(listenable = controller) { _ ->
                        val t = controller.value
                        val statusLabel = when (controller.status) {
                            PixelAnimationStatus.Forward -> "FWD"
                            PixelAnimationStatus.Reverse -> "REV"
                            PixelAnimationStatus.Completed -> "DONE"
                            PixelAnimationStatus.Dismissed -> "IDLE"
                        }
                        Column(
                            crossAxisAlignment = CrossAxisAlignment.START,
                            spacing = 2,
                            children = listOf(
                                Padding(
                                    child = Row(
                                        mainAxisSize = MainAxisSize.MAX,
                                        children = listOf(
                                            Text(
                                                "t=${"%.2f".format(t)}  $statusLabel",
                                                style = TextStyle.Accent,
                                            ),
                                        ),
                                    ),
                                    horizontal = 4,
                                    vertical = 2,
                                ),
                            ) + CURVES.map { entry ->
                                val curved = CurvedAnimation(controller, entry.curve)
                                val cv = curved.value
                                val dotOffset = DOT_TWEEN.lerp(cv)
                                val pct = (cv * 100).roundToInt()
                                Padding(
                                    child = Row(
                                        spacing = 3,
                                        mainAxisSize = MainAxisSize.MAX,
                                        children = listOf(
                                            Text(entry.label, style = TextStyle.Default),
                                            SizedBox(
                                                width = TRACK_W,
                                                height = 6,
                                                child = Stack(
                                                    children = listOf(
                                                        Positioned(
                                                            left = 0, top = 2, right = 0, bottom = 2,
                                                            child = Container(
                                                                fillTone = PixelTone.OFF,
                                                                borderTone = PixelTone.ON,
                                                            ),
                                                        ),
                                                        Positioned(
                                                            left = dotOffset, top = 0,
                                                            child = Container(
                                                                width = DOT_W, height = 6,
                                                                fillTone = PixelTone.ACCENT,
                                                                borderTone = null,
                                                            ),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                            Text("$pct%", style = TextStyle.Default),
                                        ),
                                    ),
                                    horizontal = 4,
                                    vertical = 1,
                                )
                            },
                        )
                    },

                    Expanded(child = SizedBox()),

                    // ── controls ──────────────────────────────────────────────
                    Padding(
                        child = Column(
                            spacing = 3,
                            crossAxisAlignment = CrossAxisAlignment.START,
                            children = listOf(
                                Row(
                                    spacing = 3,
                                    children = listOf(
                                        OutlinedButton(
                                            text = "FWD",
                                            onPressed = { controller.forward() },
                                        ),
                                        OutlinedButton(
                                            text = "REV",
                                            onPressed = { controller.reverse() },
                                        ),
                                        OutlinedButton(
                                            text = "LOOP",
                                            onPressed = { controller.repeat(reverse = true) },
                                        ),
                                        OutlinedButton(
                                            text = "STOP",
                                            onPressed = { controller.stop() },
                                        ),
                                    ),
                                ),
                                Row(
                                    spacing = 3,
                                    children = DURATIONS.mapIndexed { i, dur ->
                                        OutlinedButton(
                                            text = "${dur.inWholeMilliseconds}ms",
                                            onPressed = { setDuration(i) },
                                            selected = i == durationIndex,
                                        )
                                    },
                                ),
                            ),
                        ),
                        horizontal = 4,
                        vertical = 4,
                    ),
                ),
            )
        }
    }
}

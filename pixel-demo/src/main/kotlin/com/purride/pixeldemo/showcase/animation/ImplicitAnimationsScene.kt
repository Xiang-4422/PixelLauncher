package com.purride.pixeldemo.showcase.animation

import com.purride.pixelcore.PixelTone
import com.purride.pixelui.Alignment
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
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
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.host.PixelFrameScheduler
import com.purride.pixelui.widgets.animated.AnimatedContainer
import com.purride.pixelui.widgets.animated.AnimatedOpacity
import com.purride.pixelui.widgets.animated.AnimatedPositioned
import com.purride.pixelui.widgets.animated.AnimatedSwitcher
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import kotlin.time.Duration.Companion.milliseconds

object ImplicitAnimationsScene : DemoScene {
    override val id = "implicit_animations"
    override val title = "Implicit Animations"
    override val description = "Layer 2：AnimatedContainer / AnimatedOpacity / AnimatedSwitcher / AnimatedPositioned"

    override fun build(env: DemoEnv): Widget =
        ImplicitAnimationsWidget(frameScheduler = env.hostView.frameScheduler)
}

private class ImplicitAnimationsWidget(
    private val frameScheduler: PixelFrameScheduler,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = ImplicitAnimationsState()

    inner class ImplicitAnimationsState : State<ImplicitAnimationsWidget>() {
        private lateinit var vsync: PixelTickerProvider

        // AnimatedContainer state
        private var containerExpanded = false

        // AnimatedOpacity state
        private var visible = true

        // AnimatedSwitcher state
        private var switcherPage = 0

        // AnimatedPositioned state
        private var positionRight = false

        override fun initState() {
            vsync = PixelTickerProvider(widget.frameScheduler)
        }

        override fun build(context: BuildContext): Widget {
            return Padding(
                all = 8,
                child = Column(
                    crossAxisAlignment = CrossAxisAlignment.START,
                    spacing = 12,
                    children = listOf(
                        // ── AnimatedContainer ──────────────────────────────────
                        Text("AnimatedContainer", style = TextStyle.Accent),
                        AnimatedContainer(
                            duration = 400.milliseconds,
                            vsync = vsync,
                            width = if (containerExpanded) 80 else 20,
                            height = 16,
                            borderTone = if (containerExpanded) PixelTone.ACCENT else PixelTone.ON,
                        ),
                        OutlinedButton(
                            text = if (containerExpanded) "Shrink" else "Expand",
                            onPressed = { setState { containerExpanded = !containerExpanded } },
                        ),

                        SizedBox(height = 4),

                        // ── AnimatedOpacity ────────────────────────────────────
                        Text("AnimatedOpacity", style = TextStyle.Accent),
                        AnimatedOpacity(
                            opacity = if (visible) 1f else 0f,
                            duration = 500.milliseconds,
                            vsync = vsync,
                            child = Container(
                                width = 40,
                                height = 16,
                                fillTone = PixelTone.ON,
                                borderTone = PixelTone.ACCENT,
                            ),
                        ),
                        OutlinedButton(
                            text = if (visible) "Hide" else "Show",
                            onPressed = { setState { visible = !visible } },
                        ),

                        SizedBox(height = 4),

                        // ── AnimatedSwitcher ───────────────────────────────────
                        Text("AnimatedSwitcher", style = TextStyle.Accent),
                        AnimatedSwitcher(
                            duration = 300.milliseconds,
                            vsync = vsync,
                            child = when (switcherPage) {
                                0 -> Container(
                                    key = "page-a",
                                    width = 40, height = 16,
                                    fillTone = PixelTone.OFF,
                                    borderTone = PixelTone.ON,
                                    child = Center(child = Text("A", style = TextStyle.Default)),
                                )
                                else -> Container(
                                    key = "page-b",
                                    width = 40, height = 16,
                                    fillTone = PixelTone.ACCENT,
                                    borderTone = PixelTone.ON,
                                    child = Center(child = Text("B", style = TextStyle.Default)),
                                )
                            },
                        ),
                        OutlinedButton(
                            text = "Switch (page ${switcherPage + 1})",
                            onPressed = { setState { switcherPage = 1 - switcherPage } },
                        ),

                        SizedBox(height = 4),

                        // ── AnimatedPositioned ─────────────────────────────────
                        Text("AnimatedPositioned", style = TextStyle.Accent),
                        SizedBox(
                            width = 96,
                            height = 24,
                            child = Stack(
                                children = listOf(
                                    Positioned(
                                        left = 0, top = 0, right = 0, bottom = 0,
                                        child = Container(
                                            borderTone = PixelTone.ON,
                                        ),
                                    ),
                                    AnimatedPositioned(
                                        duration = 400.milliseconds,
                                        vsync = vsync,
                                        left = if (positionRight) 60 else 4,
                                        top = 4,
                                        child = Container(
                                            width = 20, height = 16,
                                            fillTone = PixelTone.ACCENT,
                                            borderTone = null,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        OutlinedButton(
                            text = if (positionRight) "Move Left" else "Move Right",
                            onPressed = { setState { positionRight = !positionRight } },
                        ),
                    ),
                ),
            )
        }
    }
}

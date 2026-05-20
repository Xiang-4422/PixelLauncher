package com.purride.pixeldemo.showcase.stress

import android.view.Choreographer
import com.purride.pixelcore.AxisMotionController
import com.purride.pixelcore.AxisMotionState
import com.purride.pixelcore.PixelTone
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.Positioned
import com.purride.pixelui.Row
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Stack
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListState
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.DemoMetricsOverlay
import kotlin.math.roundToInt

object StressAnimationFloodScene : DemoScene {
    override val id = "stress_animation_flood"
    override val title = "压测 · 动画洪流"
    override val description = "M 个 AxisMotionController 同帧推进（M=10 / 50 / 200）"

    override fun build(env: DemoEnv): Widget = StressAnimationFloodWidget()
}

private val tiers = listOf(10, 50, 200)
private const val TRACK_WIDTH = 120
private const val DOT_SIZE = 6
private const val MAX_OFFSET = (TRACK_WIDTH - DOT_SIZE).toFloat()

private class StressAnimationFloodWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = StressAnimationFloodState()

    class StressAnimationFloodState : State<StressAnimationFloodWidget>() {
        private var tierIndex = 0
        private var controllers: List<AxisMotionController> = emptyList()
        private var states: MutableList<AxisMotionState> = mutableListOf()
        private var lastFrameNs = 0L
        private var running = false
        private val listState = PixelListState()
        private val listCtrl = ScrollController()

        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!running) return
                val deltaMs = if (lastFrameNs == 0L) 16L else ((frameTimeNanos - lastFrameNs) / 1_000_000L).coerceAtLeast(1L)
                lastFrameNs = frameTimeNanos
                var changed = false
                for (i in states.indices) {
                    val c = controllers[i]
                    var s = states[i]
                    if (!c.isActive(s)) {
                        val target = if (s.dragOffsetPx >= MAX_OFFSET / 2f) 0f else MAX_OFFSET
                        s = c.settleTo(s, target)
                    }
                    s = c.step(s, deltaMs)
                    states[i] = s
                    changed = true
                }
                if (changed) setState { /* trigger rebuild */ }
                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        private fun reset(count: Int) {
            controllers = List(count) { AxisMotionController(settleDurationMs = 600L + (it % 4) * 150L) }
            states = MutableList(count) { i ->
                controllers[i].settleTo(controllers[i].create(), MAX_OFFSET * ((i % 5) + 1) / 6f)
            }
        }

        override fun initState() {
            super.initState()
            reset(tiers[tierIndex])
            running = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }

        override fun dispose() {
            running = false
            super.dispose()
        }

        override fun build(context: BuildContext): Widget {
            val m = states.size
            val controls = tiers.mapIndexed { i, n ->
                OutlinedButton(
                    text = "M=$n",
                    onPressed = {
                        setState {
                            tierIndex = i
                            reset(n)
                        }
                    },
                    selected = i == tierIndex,
                )
            }
            return Column(
                children = listOf(
                    DemoMetricsOverlay(extraSampler = { "M=$m" }),
                    SizedBox(height = 1),
                    Expanded(
                        child = ListViewBuilder(
                            state = listState,
                            controller = listCtrl,
                            itemCount = m,
                            itemBuilder = { i ->
                                val offset = controllers[i].visualOffsetPx(states[i]).roundToInt().coerceIn(0, (MAX_OFFSET).toInt())
                                Padding(
                                    child = Stack(
                                        children = listOf(
                                            Container(
                                                width = TRACK_WIDTH,
                                                height = DOT_SIZE + 2,
                                                fillTone = PixelTone.OFF,
                                                borderTone = PixelTone.ON,
                                            ),
                                            Positioned(
                                                left = offset, top = 1,
                                                child = Container(
                                                    width = DOT_SIZE, height = DOT_SIZE,
                                                    fillTone = PixelTone.ACCENT, borderTone = null,
                                                ),
                                            ),
                                        ),
                                    ),
                                    horizontal = 4,
                                    vertical = 1,
                                )
                            },
                        ),
                    ),
                    SizedBox(height = 2),
                    Padding(
                        child = Row(
                            children = controls,
                            spacing = 2,
                            mainAxisAlignment = MainAxisAlignment.CENTER,
                        ),
                        horizontal = 4,
                        vertical = 2,
                    ),
                ),
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }
    }
}

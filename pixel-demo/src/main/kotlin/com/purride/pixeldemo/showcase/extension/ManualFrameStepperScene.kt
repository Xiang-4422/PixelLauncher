package com.purride.pixeldemo.showcase.extension

import com.purride.pixelcore.AxisMotionController
import com.purride.pixelcore.AxisMotionState
import com.purride.pixelcore.PixelTone
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.host.ManualFrameScheduler
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
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import kotlin.math.roundToInt

object ManualFrameStepperScene : DemoScene {
    override val id = "manual_frame_stepper"
    override val title = "ManualFrameScheduler"
    override val description = "用手动帧调度器逐帧推进动画 — 截图回归 / 调试入口"

    override fun build(env: DemoEnv): Widget = ManualFrameStepperWidget()
}

private const val TRACK_WIDTH = 200
private const val DOT_SIZE = 12
private const val MAX_OFFSET = (TRACK_WIDTH - DOT_SIZE).toFloat()
private const val FRAME_MS = 16L

private class ManualFrameStepperWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = ManualFrameStepperState()

    class ManualFrameStepperState : State<ManualFrameStepperWidget>() {
        private val scheduler = ManualFrameScheduler()
        private val motion = AxisMotionController(settleDurationMs = 500L)
        private var state: AxisMotionState = AxisMotionState()
        private var frameIndex = 0
        private var nowNs = 0L

        override fun initState() {
            super.initState()
            state = motion.settleTo(motion.create(), MAX_OFFSET)
        }

        private fun stepOnce() {
            scheduler.scheduleFrame { ts ->
                nowNs = ts
                state = motion.step(state, FRAME_MS)
                if (!motion.isActive(state)) {
                    val target = if (state.dragOffsetPx >= MAX_OFFSET / 2f) 0f else MAX_OFFSET
                    state = motion.settleTo(state, target)
                }
                frameIndex++
            }
            scheduler.advanceFrame(nowNs + FRAME_MS * 1_000_000L)
        }

        private fun stepN(n: Int) {
            setState {
                repeat(n) { stepOnce() }
            }
        }

        override fun build(context: BuildContext): Widget {
            val offset = motion.visualOffsetPx(state).roundToInt().coerceIn(0, MAX_OFFSET.toInt())
            return Column(
                children = listOf(
                    Padding(
                        child = Column(
                            children = listOf(
                                Text("Frame: $frameIndex", style = TextStyle.Accent),
                                Text("Offset: $offset px", style = TextStyle.Default),
                                Text("Pending callbacks: ${scheduler.pendingCount}", style = TextStyle.Default),
                            ),
                            spacing = 1,
                            crossAxisAlignment = CrossAxisAlignment.START,
                        ),
                        all = 4,
                    ),
                    Expanded(
                        child = Center(
                            child = Stack(
                                children = listOf(
                                    Container(
                                        width = TRACK_WIDTH,
                                        height = DOT_SIZE + 4,
                                        fillTone = PixelTone.OFF,
                                        borderTone = PixelTone.ON,
                                    ),
                                    Positioned(
                                        left = offset, top = 2,
                                        child = Container(
                                            width = DOT_SIZE, height = DOT_SIZE,
                                            fillTone = PixelTone.ACCENT, borderTone = null,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                    SizedBox(height = 2),
                    Padding(
                        child = Row(
                            children = listOf(
                                OutlinedButton("step 1", onPressed = { stepN(1) }),
                                OutlinedButton("step 10", onPressed = { stepN(10) }),
                                OutlinedButton("step 60", onPressed = { stepN(60) }),
                            ),
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

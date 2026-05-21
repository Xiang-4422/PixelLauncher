package com.purride.pixeldemo.scaffold

import android.view.Choreographer
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelcore.PixelColor

fun DemoMetricsOverlay(extraSampler: (() -> String)? = null): Widget =
    DemoMetricsOverlayWidget(extraSampler = extraSampler)

private class DemoMetricsOverlayWidget(
    val extraSampler: (() -> String)?,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = DemoMetricsOverlayState()

    class DemoMetricsOverlayState : State<DemoMetricsOverlayWidget>() {
        private var fps = 0
        private var heapKb = 0L
        private var extraText: String = ""
        private var frameCount = 0
        private var windowStartNs = 0L
        private var running = false

        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!running) return
                frameCount++
                if (windowStartNs == 0L) windowStartNs = frameTimeNanos
                val elapsedNs = frameTimeNanos - windowStartNs
                if (elapsedNs >= 1_000_000_000L) {
                    val measuredFps = (frameCount * 1_000_000_000L / elapsedNs).toInt()
                    val rt = Runtime.getRuntime()
                    val heapBytes = rt.totalMemory() - rt.freeMemory()
                    val extra = widget.extraSampler?.invoke().orEmpty()
                    setState {
                        fps = measuredFps
                        heapKb = heapBytes / 1024
                        extraText = extra
                    }
                    frameCount = 0
                    windowStartNs = frameTimeNanos
                }
                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        override fun initState() {
            super.initState()
            running = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }

        override fun dispose() {
            running = false
            super.dispose()
        }

        override fun build(context: BuildContext): Widget {
            val children = mutableListOf<Widget>(
                Text("FPS: $fps", style = TextStyle(color = PixelColor.fromRgb(200, 100, 0))),
                SizedBox(width = 8),
                Text("Heap: ${heapKb} KB", style = TextStyle.Default),
            )
            if (extraText.isNotEmpty()) {
                children += SizedBox(width = 8)
                children += Text(extraText, style = TextStyle.Default)
            }
            return Padding(
                child = Row(children = children, spacing = 2),
                all = 4,
            )
        }
    }
}

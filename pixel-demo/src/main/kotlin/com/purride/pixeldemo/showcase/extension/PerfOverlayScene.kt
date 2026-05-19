package com.purride.pixeldemo.showcase.extension

import android.view.Choreographer
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.ScrollController
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object PerfOverlayScene : DemoScene {
    override val id = "perf_overlay"
    override val title = "性能 Overlay"
    override val description = "Choreographer FPS + 堆内存采样，5000 项列表负载"

    override fun build(env: DemoEnv): Widget = PerfOverlayWidget()
}

private class PerfOverlayWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = PerfOverlayState()

    class PerfOverlayState : State<PerfOverlayWidget>() {
        private val listState = PixelListState()
        private val listCtrl = ScrollController()

        private var fps = 0
        private var heapKb = 0L
        private var frameCount = 0
        private var windowStartNs = 0L
        private var running = false

        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!running) return
                frameCount++
                if (windowStartNs == 0L) {
                    windowStartNs = frameTimeNanos
                }
                val elapsedNs = frameTimeNanos - windowStartNs
                if (elapsedNs >= 1_000_000_000L) {
                    val measuredFps = (frameCount * 1_000_000_000L / elapsedNs).toInt()
                    val rt = Runtime.getRuntime()
                    val heapBytes = rt.totalMemory() - rt.freeMemory()
                    setState {
                        fps = measuredFps
                        heapKb = heapBytes / 1024
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
            return Column(
                children = listOf(
                    Padding(
                        child = Row(
                            children = listOf(
                                Text("FPS: $fps", style = TextStyle.Accent),
                                SizedBox(width = 8),
                                Text("Heap: ${heapKb} KB", style = TextStyle.Default),
                            ),
                            spacing = 2,
                        ),
                        all = 4,
                    ),
                    SizedBox(height = 1),
                    Expanded(
                        child = ListViewBuilder(
                            state = listState,
                            controller = listCtrl,
                            itemCount = 5000,
                            itemBuilder = { i ->
                                Padding(
                                    child = Center(
                                        child = Text("Item $i", style = if (i % 10 == 0) TextStyle.Accent else TextStyle.Default),
                                    ),
                                    all = 2,
                                )
                            },
                        ),
                    ),
                ),
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }
    }
}

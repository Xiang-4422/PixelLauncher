package com.purride.pixeldemo.showcase.stress

import android.view.Choreographer
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.DemoMetricsOverlay

object StressRebuildStormScene : DemoScene {
    override val id = "stress_rebuild_storm"
    override val title = "压测 · 重建风暴"
    override val description = "每帧 setState 触发重建；OFF / LEAF / TREE 三档对比"

    override fun build(env: DemoEnv): Widget = StressRebuildStormWidget()
}

private enum class StormMode { OFF, LEAF, TREE }

object StressRebuildSink {
    @Volatile
    var tick: Long = 0L
}

object StressRebuildBus {
    private val listeners = mutableListOf<() -> Unit>()
    fun subscribe(l: () -> Unit): () -> Unit {
        listeners += l
        return { listeners.remove(l) }
    }
    fun notifyTick() {
        listeners.toList().forEach { it.invoke() }
    }
}

private class StressRebuildStormWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = StressRebuildStormState()

    class StressRebuildStormState : State<StressRebuildStormWidget>() {
        private var mode = StormMode.OFF
        private var tick = 0L
        private var running = false

        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!running) return
                StressRebuildSink.tick = StressRebuildSink.tick + 1
                when (mode) {
                    StormMode.OFF -> Unit
                    StormMode.LEAF -> StressRebuildBus.notifyTick()
                    StormMode.TREE -> setState { tick++ }
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
            val modeLabel = mode.name
            val controls = StormMode.values().map { m ->
                OutlinedButton(
                    text = m.name,
                    onPressed = { setState { mode = m } },
                    selected = m == mode,
                )
            }
            return Column(
                children = listOf(
                    DemoMetricsOverlay(extraSampler = { "mode=$modeLabel" }),
                    SizedBox(height = 1),
                    Expanded(
                        child = Center(
                            child = Column(
                                children = listOf(
                                    Text("Tree tick: $tick", style = TextStyle.Accent),
                                    SizedBox(height = 2),
                                    LeafTicker(),
                                    SizedBox(height = 2),
                                    Text("OFF: 仅采样基线", style = TextStyle.Default, softWrap = true),
                                    Text("LEAF: 每帧仅叶子节点 setState", style = TextStyle.Default, softWrap = true),
                                    Text("TREE: 每帧整树 setState", style = TextStyle.Default, softWrap = true),
                                ),
                                spacing = 2,
                                crossAxisAlignment = CrossAxisAlignment.CENTER,
                            ),
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

private class LeafTicker(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = LeafTickerState()

    class LeafTickerState : State<LeafTicker>() {
        private var n = 0L
        private var unsubscribe: (() -> Unit)? = null

        override fun initState() {
            super.initState()
            unsubscribe = StressRebuildBus.subscribe {
                setState { n++ }
            }
        }

        override fun dispose() {
            unsubscribe?.invoke()
            unsubscribe = null
            super.dispose()
        }

        override fun build(context: BuildContext): Widget {
            return Text("Leaf tick: $n", style = TextStyle.Default)
        }
    }
}

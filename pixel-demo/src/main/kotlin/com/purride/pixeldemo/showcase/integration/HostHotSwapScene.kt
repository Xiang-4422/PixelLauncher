package com.purride.pixeldemo.showcase.integration

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.DemoScaffold

object HostHotSwapScene : DemoScene {
    override val id = "host_hot_swap"
    override val title = "Host 热替换"
    override val description = "直接调用 hostView.setContent 替换完整 widget 树"

    override fun build(env: DemoEnv): Widget = HostHotSwapWidget(env)
}

private class HostHotSwapWidget(
    private val env: DemoEnv,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = HostHotSwapState()

    inner class HostHotSwapState : State<HostHotSwapWidget>() {
        private var swapCount = 0

        private fun swapTo(label: String) {
            swapCount++
            val count = swapCount
            widget.env.hostView.setContent {
                DemoScaffold(
                    title = HostHotSwapScene.title,
                    description = HostHotSwapScene.description,
                    body = HostHotSwapWidget(widget.env),
                )
            }
        }

        override fun build(context: BuildContext): Widget {
            return Column(
                children = listOf(
                    Expanded(
                        child = Center(
                            child = Column(
                                children = listOf(
                                    Text("替换次数: $swapCount", style = TextStyle.Accent),
                                    SizedBox(height = 4),
                                    Text("点击下方按钮调用 hostView.setContent，", style = TextStyle.Default, softWrap = true),
                                    Text("整棵 widget 树将被热替换", style = TextStyle.Default, softWrap = true),
                                ),
                                spacing = 2,
                                crossAxisAlignment = CrossAxisAlignment.CENTER,
                            ),
                        ),
                    ),
                    SizedBox(height = 2),
                    Row(
                        children = listOf(
                            OutlinedButton("树 A", onPressed = { swapTo("A") }),
                            OutlinedButton("树 B", onPressed = { swapTo("B") }),
                            OutlinedButton("树 C", onPressed = { swapTo("C") }),
                        ),
                        spacing = 2,
                    ),
                ),
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }
    }
}

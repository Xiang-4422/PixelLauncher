package com.purride.pixeldemo.showcase.foundation

import com.purride.pixelcore.PixelTone
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Stack
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object LayoutPrimitivesScene : DemoScene {
    override val id = "layout_primitives"
    override val title = "布局原语"
    override val description = "切换 Row / Column / Stack 三种排列"

    override fun build(env: DemoEnv): Widget = LayoutWidget()
}

private val blockTones = listOf(PixelTone.ON, PixelTone.ACCENT, PixelTone.ON, PixelTone.ACCENT, PixelTone.ON)
private val modeLabels = listOf("ROW", "COLUMN", "STACK")

private class LayoutWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = LayoutState()

    class LayoutState : State<LayoutWidget>() {
        private var modeIndex = 0

        override fun build(context: BuildContext): Widget {
            val blocks = blockTones.map { tone ->
                Container(width = 20, height = 20, fillTone = tone, borderTone = null)
            }
            val content: Widget = when (modeIndex) {
                0 -> Row(
                    children = blocks,
                    spacing = 2,
                    mainAxisAlignment = MainAxisAlignment.CENTER,
                )
                1 -> Column(
                    children = blocks,
                    spacing = 2,
                    mainAxisAlignment = MainAxisAlignment.CENTER,
                )
                else -> Stack(children = blocks)
            }
            val controls = modeLabels.mapIndexed { i, label ->
                OutlinedButton(
                    text = label,
                    onPressed = { setState { modeIndex = i } },
                    selected = i == modeIndex,
                )
            }
            return Column(
                children = listOf(
                    Expanded(child = Center(child = content)),
                    SizedBox(height = 2),
                    Row(children = controls, spacing = 2),
                ),
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }
    }
}

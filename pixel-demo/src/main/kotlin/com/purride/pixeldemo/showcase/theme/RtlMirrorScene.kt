package com.purride.pixeldemo.showcase.theme

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextDirection
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object RtlMirrorScene : DemoScene {
    override val id = "rtl_mirror"
    override val title = "RTL 镜像"
    override val description = "切换 LTR / RTL，观察布局方向镜像效果"

    override fun build(env: DemoEnv): Widget = RtlMirrorWidget(env)
}

private class RtlMirrorWidget(
    private val env: DemoEnv,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = RtlMirrorState()

    inner class RtlMirrorState : State<RtlMirrorWidget>() {
        private var isRtl = false

        override fun build(context: BuildContext): Widget {
            val dirLabel = if (isRtl) "RTL" else "LTR"
            return Column(
                children = listOf(
                    Expanded(
                        child = Padding(
                            child = Column(
                                children = listOf(
                                    Text("方向: $dirLabel", style = TextStyle.Accent),
                                    SizedBox(height = 4),
                                    Text("左对齐文本 Left-aligned text", style = TextStyle.Default),
                                    SizedBox(height = 2),
                                    Text("مرحبا بالعالم — Arabic RTL", style = TextStyle.Default),
                                    SizedBox(height = 4),
                                    Row(
                                        children = listOf(
                                            OutlinedButton("按钮A", onPressed = {}),
                                            OutlinedButton("按钮B", onPressed = {}),
                                            OutlinedButton("按钮C", onPressed = {}),
                                        ),
                                        spacing = 2,
                                    ),
                                ),
                                spacing = 2,
                                crossAxisAlignment = CrossAxisAlignment.START,
                            ),
                            all = 8,
                        ),
                    ),
                    SizedBox(height = 2),
                    Row(
                        children = listOf(
                            OutlinedButton("LTR", onPressed = {
                                setState { isRtl = false }
                                widget.env.hostView.textDirection = TextDirection.LTR
                            }, selected = !isRtl),
                            OutlinedButton("RTL", onPressed = {
                                setState { isRtl = true }
                                widget.env.hostView.textDirection = TextDirection.RTL
                            }, selected = isRtl),
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

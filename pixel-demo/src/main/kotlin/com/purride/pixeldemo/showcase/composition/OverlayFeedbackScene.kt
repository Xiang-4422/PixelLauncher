package com.purride.pixeldemo.showcase.composition

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Dialog
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Snackbar
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Toast
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object OverlayFeedbackScene : DemoScene {
    override val id = "overlay_feedback"
    override val title = "Overlay Feedback"
    override val description = "Dialog / Toast / Snackbar 纯 widget 反馈"

    override fun build(env: DemoEnv): Widget = OverlayFeedbackWidget()
}

private class OverlayFeedbackWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = OverlayFeedbackState()

    class OverlayFeedbackState : State<OverlayFeedbackWidget>() {
        private var mode = 0

        override fun build(context: BuildContext): Widget {
            val content = when (mode % 3) {
                0 -> Dialog(
                    title = Text("Dialog", style = TextStyle(color = PixelColor.fromRgb(80, 180, 110))),
                    content = Text("Pure pixel widget overlay", style = TextStyle.Default),
                    actions = listOf(OutlinedButton("OK", onPressed = { setState { mode++ } })),
                )
                1 -> Toast("Saved")
                else -> Snackbar("Queued", action = OutlinedButton("UNDO", onPressed = { setState { mode = 0 } }))
            }
            return Column(
                children = listOf(
                    content,
                    OutlinedButton("NEXT", onPressed = { setState { mode++ } }),
                ),
                spacing = 4,
                mainAxisSize = MainAxisSize.MAX,
            )
        }
    }
}

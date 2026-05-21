package com.purride.pixeldemo.showcase.integration

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.PixelHapticType
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixelcore.PixelColor

object HapticFeedbackScene : DemoScene {
    override val id = "haptic_feedback"
    override val title = "Haptic 反馈"
    override val description = "调用 PixelHostBridge.performHapticFeedback — TAP / LONG_PRESS"

    override fun build(env: DemoEnv): Widget = HapticFeedbackWidget(env)
}

private class HapticFeedbackWidget(
    private val env: DemoEnv,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = HapticFeedbackState()

    inner class HapticFeedbackState : State<HapticFeedbackWidget>() {
        private var lastFired: String = "—"

        private fun fire(type: PixelHapticType) {
            widget.env.hostView.hostBridge?.performHapticFeedback(type)
            setState { lastFired = type.name }
        }

        override fun build(context: BuildContext): Widget {
            return Column(
                children = listOf(
                    Expanded(
                        child = Center(
                            child = Column(
                                children = listOf(
                                    Text("最近触发: $lastFired", style = TextStyle(color = PixelColor.fromRgb(200, 100, 0))),
                                    SizedBox(height = 4),
                                    Text("点击下方按钮触发对应 PixelHapticType", style = TextStyle.Default, softWrap = true),
                                    Text("需在真机上能感受到不同震动", style = TextStyle.Default, softWrap = true),
                                ),
                                spacing = 2,
                                crossAxisAlignment = CrossAxisAlignment.CENTER,
                            ),
                        ),
                    ),
                    SizedBox(height = 2),
                    Padding(
                        child = Row(
                            children = listOf(
                                OutlinedButton("TAP", onPressed = { fire(PixelHapticType.TAP) }),
                                OutlinedButton("LONG_PRESS", onPressed = { fire(PixelHapticType.LONG_PRESS) }),
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

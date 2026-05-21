package com.purride.pixeldemo.showcase.interaction

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixelcore.PixelColor

object GestureTapScene : DemoScene {
    override val id = "gesture_tap"
    override val title = "点击手势"
    override val description = "GestureDetector onTap 演示"

    override fun build(env: DemoEnv): Widget = GestureTapWidget()
}

private class GestureTapWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = GestureTapState()

    class GestureTapState : State<GestureTapWidget>() {
        private var tapCount = 0
        private var lastEvent = "—"

        override fun build(context: BuildContext): Widget = Center(
            child = Column(
                children = listOf(
                    Text("事件: $lastEvent", style = TextStyle(color = PixelColor.fromRgb(200, 100, 0))),
                    SizedBox(height = 4),
                    Text("点击次数: $tapCount", style = TextStyle.Default),
                    SizedBox(height = 8),
                    GestureDetector(
                        onTap = {
                            setState {
                                tapCount++
                                lastEvent = "onTap"
                            }
                        },
                        child = Padding(
                            child = OutlinedButton(
                                text = "点我",
                                onPressed = null,
                            ),
                            all = 0,
                        ),
                    ),
                    SizedBox(height = 4),
                    OutlinedButton(
                        text = "重置",
                        onPressed = { setState { tapCount = 0; lastEvent = "—" } },
                    ),
                ),
                spacing = 2,
                mainAxisSize = MainAxisSize.MIN,
                mainAxisAlignment = MainAxisAlignment.CENTER,
                crossAxisAlignment = CrossAxisAlignment.CENTER,
            ),
        )
    }
}

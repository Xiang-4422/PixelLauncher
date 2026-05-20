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
import com.purride.pixelui.PixelSystemAction
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object SystemActionDispatchScene : DemoScene {
    override val id = "system_action_dispatch"
    override val title = "SystemAction 派发"
    override val description = "调用 PixelHostBridge.dispatchSystemAction — 宿主侧 Toast 响应"

    override fun build(env: DemoEnv): Widget = SystemActionDispatchWidget(env)
}

private data class ActionPreset(val label: String, val type: String, val payload: String?)

private val presets = listOf(
    ActionPreset("OPEN", "open_url", "https://example.com"),
    ActionPreset("SHARE", "share", "hello from pixel-engine"),
    ActionPreset("CLOSE", "close_app", null),
)

private class SystemActionDispatchWidget(
    private val env: DemoEnv,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = SystemActionDispatchState()

    inner class SystemActionDispatchState : State<SystemActionDispatchWidget>() {
        private var lastDispatched = "—"

        private fun dispatch(p: ActionPreset) {
            widget.env.hostView.hostBridge?.dispatchSystemAction(
                PixelSystemAction(type = p.type, payload = p.payload),
            )
            setState { lastDispatched = p.type }
        }

        override fun build(context: BuildContext): Widget {
            val buttons = presets.map { p ->
                OutlinedButton(p.label, onPressed = { dispatch(p) })
            }
            return Column(
                children = listOf(
                    Expanded(
                        child = Center(
                            child = Column(
                                children = listOf(
                                    Text("最近派发: $lastDispatched", style = TextStyle.Accent),
                                    SizedBox(height = 4),
                                    Text("点击按钮派发自定义 PixelSystemAction，", style = TextStyle.Default, softWrap = true),
                                    Text("宿主 Activity 用 Toast 响应", style = TextStyle.Default, softWrap = true),
                                ),
                                spacing = 2,
                                crossAxisAlignment = CrossAxisAlignment.CENTER,
                            ),
                        ),
                    ),
                    SizedBox(height = 2),
                    Padding(
                        child = Row(
                            children = buttons,
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

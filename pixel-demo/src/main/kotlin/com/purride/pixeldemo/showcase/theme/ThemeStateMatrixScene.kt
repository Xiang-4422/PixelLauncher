package com.purride.pixeldemo.showcase.theme

import com.purride.pixelui.BuildContext
import com.purride.pixelui.ButtonStyle
import com.purride.pixelui.Checkbox
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.GridViewBuilder
import com.purride.pixelui.ListTile
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.Scrollbar
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SegmentedControl
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Switch
import com.purride.pixelui.Tabs
import com.purride.pixelui.Text
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.TextStyle
import com.purride.pixelui.TextField
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelTextFieldState
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixelcore.PixelColor

object ThemeStateMatrixScene : DemoScene {
    override val id = "theme_state_matrix"
    override val title = "组件状态矩阵"
    override val description = "观察 button / textfield / controls 的状态外观"

    override fun build(env: DemoEnv): Widget = ThemeStateMatrixWidget()
}

private class ThemeStateMatrixWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = ThemeStateMatrixState()

    class ThemeStateMatrixState : State<ThemeStateMatrixWidget>() {
        private var subjectIdx = 0
        private val scrollState = PixelListState()
        private val scrollCtrl = ScrollController()
        private val tfState = PixelTextFieldState()
        private val tfCtrl = TextEditingController()
        private val tfReadState = PixelTextFieldState(initialText = "只读内容")
        private val tfReadCtrl = TextEditingController()
        private val controlsScrollState = PixelListState()
        private val controlsScrollCtrl = ScrollController()

        override fun build(context: BuildContext): Widget {
            val subjects = listOf("Button", "TextField", "Controls")
            val controls = subjects.mapIndexed { i, s ->
                OutlinedButton(s, onPressed = { setState { subjectIdx = i } }, borderColor = if (i == subjectIdx) PixelColor.fromRgb(200, 100, 0) else PixelColor.White)
            }

            val content: Widget = when (subjectIdx) {
                0 -> buttonMatrix()
                1 -> textFieldMatrix()
                else -> controlsMatrix()
            }

            return Column(
                children = listOf(
                    Expanded(
                        child = SingleChildScrollView(
                            state = scrollState,
                            controller = scrollCtrl,
                            child = Padding(child = content, all = 6),
                        ),
                    ),
                    SizedBox(height = 2),
                    Row(children = controls, spacing = 2),
                ),
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }

        private fun buttonMatrix(): Widget = Column(
            children = listOf(
                row("Default", OutlinedButton("Default", onPressed = {}, style = ButtonStyle.Default)),
                row("Accent", OutlinedButton("Accent", onPressed = {}, borderColor = PixelColor.fromRgb(200, 100, 0))),
                row("Disabled", OutlinedButton("Disabled", onPressed = {}, enabled = false)),
                row("Selected", OutlinedButton("Selected", onPressed = {}, borderColor = PixelColor.fromRgb(200, 100, 0))),
                row("Pressed", OutlinedButton("Pressed", onPressed = {})),
            ),
            spacing = 4,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        )

        private fun textFieldMatrix(): Widget = Column(
            children = listOf(
                row("Default", TextField(state = tfState, controller = tfCtrl, placeholder = "Default")),
                row("Disabled", TextField(state = PixelTextFieldState(), controller = TextEditingController(), placeholder = "Disabled", enabled = false)),
                row("ReadOnly", TextField(state = tfReadState, controller = tfReadCtrl, readOnly = true)),
            ),
            spacing = 4,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        )

        private fun controlsMatrix(): Widget {
            val accent = PixelColor.fromRgb(80, 180, 110)
            return Column(
                children = listOf(
                    row(
                        "ListTile",
                        Column(
                            children = listOf(
                                ListTile(
                                    leading = Checkbox(checked = true, onChanged = null, activeColor = accent),
                                    title = Text("Enabled"),
                                    trailing = Switch(checked = true, onChanged = null, activeColor = accent),
                                    onTap = {},
                                ),
                                ListTile(
                                    leading = Checkbox(checked = true, onChanged = null, enabled = false),
                                    title = Text("Disabled"),
                                    trailing = Switch(checked = false, onChanged = null, enabled = false),
                                    enabled = false,
                                ),
                            ),
                            spacing = 1,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                    ),
                    row(
                        "Checkbox / Switch",
                        Row(
                            children = listOf(
                                Checkbox(checked = false, onChanged = {}),
                                Checkbox(checked = true, onChanged = {}, activeColor = accent),
                                Checkbox(checked = true, onChanged = {}, enabled = false),
                                Switch(checked = false, onChanged = {}),
                                Switch(checked = true, onChanged = {}, activeColor = accent),
                                Switch(checked = true, onChanged = {}, enabled = false),
                            ),
                            spacing = 2,
                            crossAxisAlignment = CrossAxisAlignment.CENTER,
                        ),
                    ),
                    row("Tabs", Tabs(labels = listOf("A", "B", "C"), selectedIndex = 1, onSelected = {})),
                    row("Segmented", SegmentedControl(labels = listOf("LOW", "MID", "HI"), selectedIndex = 2, onSelected = {})),
                    row(
                        "Scrollbar",
                        SizedBox(
                            height = 24,
                            child = Scrollbar(
                                state = controlsScrollState,
                                width = 2,
                                thumbColor = accent,
                                child = GridViewBuilder(
                                    itemCount = 24,
                                    itemBuilder = { index ->
                                        Container(
                                            child = Text("${index % 10}"),
                                            borderColor = PixelColor.White,
                                        )
                                    },
                                    cellWidth = 12,
                                    cellHeight = 6,
                                    spacing = 1,
                                    runSpacing = 1,
                                    state = controlsScrollState,
                                    controller = controlsScrollCtrl,
                                ),
                            ),
                        ),
                    ),
                ),
                spacing = 4,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }

        private fun row(label: String, widget: Widget) = Column(
            children = listOf(
                Text(label, style = TextStyle.Default),
                widget,
            ),
            spacing = 1,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        )
    }
}

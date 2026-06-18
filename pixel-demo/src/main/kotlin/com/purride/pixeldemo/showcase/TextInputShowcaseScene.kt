package com.purride.pixeldemo.showcase

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.PixelInputType
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelTextSpan
import com.purride.pixelui.RichText
import com.purride.pixelui.Row
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.TextField
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.Blue
import com.purride.pixeldemo.scaffold.ComponentShowcaseScaffold
import com.purride.pixeldemo.scaffold.Cyan
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.Green
import com.purride.pixeldemo.scaffold.Muted
import com.purride.pixeldemo.scaffold.Pink
import com.purride.pixeldemo.scaffold.Purple
import com.purride.pixeldemo.scaffold.Yellow
import com.purride.pixeldemo.scaffold.samplePanel
import com.purride.pixeldemo.scaffold.sectionTitle

object TextInputShowcaseScene : DemoScene {
    override val id = "components_text_input"
    override val title = "文本输入"
    override val summary = "Text、RichText、TextField、控制器、IME 与编辑命令"
    override val category = DemoCatalog.textInput
    override val tags = setOf("text", "input", "form", "ime", "selection")
    override val apis = setOf(
        "Text",
        "RichText",
        "TextField",
        "TextEditingController",
        "PixelTextFieldState",
        "PixelInputType",
        "PixelTextInputAction",
        "PixelTextEditAction",
    )
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = TextInputBody())
}

private class TextInputBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = TextInputState()

    private class TextInputState : State<TextInputBody>() {
        private val controller = TextEditingController()
        private val editableState = controller.create("TextField")
        private val multilineState = controller.create("one\ntwo\nthree")
        private var submitted = "DONE"

        override fun build(context: BuildContext): Widget =
            Column(
                children = listOf(
                    sectionTitle("文本渲染"),
                    samplePanel(
                        title = "Text overflow / align",
                        color = Cyan,
                        child = Column(
                            children = listOf(
                                Text("CLIP: ABCDEFGHIJKLMNOP", style = TextStyle(color = Cyan), overflow = PixelTextOverflow.CLIP),
                                Text("ELLIPSIS: ABCDEFGHIJKLMNOP", style = TextStyle(color = Green), overflow = PixelTextOverflow.ELLIPSIS),
                                Text("WRAP TEXT DEMO FOR SMALL PIXEL GRID", style = TextStyle(color = Pink), softWrap = true, maxLines = 2),
                            ),
                            spacing = 2,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                    ),
                    samplePanel(
                        title = "RichText spans",
                        color = Purple,
                        child = RichText(
                            spans = listOf(
                                PixelTextSpan("RICH ", TextStyle(color = Purple)),
                                PixelTextSpan("TEXT ", TextStyle(color = Yellow)),
                                PixelTextSpan("SPAN", TextStyle(color = Cyan)),
                            ),
                        ),
                    ),
                    sectionTitle("输入状态"),
                    samplePanel(
                        title = "TextField editable / readonly / disabled",
                        color = Blue,
                        child = Column(
                            children = listOf(
                                TextField(
                                    state = editableState,
                                    controller = controller,
                                    placeholder = "输入文本",
                                    inputType = PixelInputType.TEXT,
                                    textInputAction = PixelTextInputAction.DONE,
                                    onChanged = { value ->
                                        submitted = "typing ${value.length}"
                                        setState {}
                                    },
                                    onSubmitted = { value ->
                                        submitted = "submit ${value.length}"
                                        setState {}
                                    },
                                    fillColor = PixelColor.fromRgb(8, 16, 20),
                                    borderColor = Cyan,
                                ),
                                TextField(
                                    state = multilineState,
                                    controller = controller,
                                    placeholder = "多行",
                                    minLines = 2,
                                    maxLines = 3,
                                    fillColor = PixelColor.fromRgb(16, 12, 20),
                                    borderColor = Purple,
                                ),
                                TextField(
                                    state = controller.create("READ ONLY"),
                                    controller = controller,
                                    readOnly = true,
                                    borderColor = Yellow,
                                ),
                                TextField(
                                    state = controller.create("DISABLED"),
                                    controller = controller,
                                    enabled = false,
                                ),
                                Text(submitted, style = TextStyle(color = Muted)),
                            ),
                            spacing = 3,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                    ),
                    samplePanel(
                        title = "IME 类型",
                        color = Green,
                        child = Row(
                            children = listOf(
                                token("TEXT", Cyan),
                                token("NUMBER", Green),
                                token("PHONE", Yellow),
                                token("EMAIL", Pink),
                            ),
                            spacing = 2,
                        ),
                    ),
                ),
                spacing = 4,
                mainAxisSize = MainAxisSize.MIN,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )

        private fun token(text: String, color: PixelColor): Widget =
            Container(
                padding = EdgeInsets.symmetric(horizontal = 2, vertical = 1),
                borderColor = color,
                child = Text(text, style = TextStyle(color = color)),
            )
    }
}

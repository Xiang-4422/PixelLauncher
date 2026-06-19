package com.purride.pixeldemo.showcase

import com.purride.pixelcore.PixelColor
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

object TextRenderingScene : DemoScene {
    override val id = "text_rendering"
    override val title = "文本渲染"
    override val summary = "Text、overflow、softWrap 和 maxLines"
    override val category = DemoCatalog.textInput
    override val tags = setOf("text", "rendering", "overflow", "wrap")
    override val apis = setOf("Text", "TextStyle", "PixelTextOverflow")
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(
            item = this,
            env = env,
            body = textPageBody(
                listOf(
                    sectionTitle("单行文本"),
                    samplePanel(
                        title = "CLIP / ELLIPSIS",
                        color = Cyan,
                        child = Column(
                            children = listOf(
                                Text("CLIP: ABCDEFGHIJKLMNOP", style = TextStyle(color = Cyan), overflow = PixelTextOverflow.CLIP),
                                Text("ELLIPSIS: ABCDEFGHIJKLMNOP", style = TextStyle(color = Green), overflow = PixelTextOverflow.ELLIPSIS),
                            ),
                            spacing = 2,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                    ),
                    samplePanel(
                        title = "softWrap / maxLines",
                        color = Pink,
                        child = Text("WRAP TEXT DEMO FOR SMALL PIXEL GRID", style = TextStyle(color = Pink), softWrap = true, maxLines = 2),
                    ),
                ),
            ),
        )
}

object RichTextScene : DemoScene {
    override val id = "text_rich"
    override val title = "富文本"
    override val summary = "RichText 与 PixelTextSpan 多样式片段"
    override val category = DemoCatalog.textInput
    override val tags = setOf("text", "richtext", "span")
    override val apis = setOf("RichText", "PixelTextSpan", "TextStyle")
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(
            item = this,
            env = env,
            body = textPageBody(
                listOf(
                    sectionTitle("多样式文本"),
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
                ),
            ),
        )
}

object TextFieldInputScene : DemoScene {
    override val id = "text_field_input"
    override val title = "输入框"
    override val summary = "TextField、TextEditingController、输入类型与提交动作"
    override val category = DemoCatalog.textInput
    override val tags = setOf("text", "input", "textfield", "ime")
    override val apis = setOf("TextField", "TextEditingController", "PixelTextFieldState", "PixelInputType", "PixelTextInputAction")
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = TextFieldInputBody())
}

object TextEditStateScene : DemoScene {
    override val id = "text_edit_state"
    override val title = "编辑状态"
    override val summary = "多行、只读、禁用和编辑反馈"
    override val category = DemoCatalog.textInput
    override val tags = setOf("text", "state", "readonly", "disabled", "multiline")
    override val apis = setOf("TextField", "TextEditingController", "PixelTextEditAction")
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = TextEditStateBody())
}

private class TextFieldInputBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = TextFieldInputState()

    private class TextFieldInputState : State<TextFieldInputBody>() {
        private val controller = TextEditingController()
        private val fieldState = controller.create("TextField")
        private var submitted = "READY"

        override fun build(context: com.purride.pixelui.BuildContext): Widget =
            textPageBody(
                listOf(
                    sectionTitle("输入状态"),
                    samplePanel(
                        title = "TextField editable",
                        color = Blue,
                        child = Column(
                            children = listOf(
                                TextField(
                                    state = fieldState,
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
            )
    }
}

private class TextEditStateBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = TextEditStateState()

    private class TextEditStateState : State<TextEditStateBody>() {
        private val controller = TextEditingController()
        private val multilineState = controller.create("one\ntwo\nthree")

        override fun build(context: com.purride.pixelui.BuildContext): Widget =
            textPageBody(
                listOf(
                    sectionTitle("状态变体"),
                    samplePanel(
                        title = "multiline / readonly / disabled",
                        color = Purple,
                        child = Column(
                            children = listOf(
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
                            ),
                            spacing = 3,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                    ),
                ),
            )
    }
}

private fun textPageBody(children: List<Widget>): Widget =
    Column(
        children = children,
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

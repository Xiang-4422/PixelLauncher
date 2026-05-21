package com.purride.pixeldemo.showcase.foundation

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixelcore.PixelColor

object TextMatrixScene : DemoScene {
    override val id = "text_matrix"
    override val title = "文本参数矩阵"
    override val description = "softWrap / maxLines / overflow / textAlign 四维参数组合"

    override fun build(env: DemoEnv): Widget = TextMatrixWidget()
}

private val maxLinesOptions = listOf(1, 2, 4)
private val overflowOptions = listOf(PixelTextOverflow.CLIP, PixelTextOverflow.ELLIPSIS)
private val alignOptions = listOf(TextAlign.START, TextAlign.CENTER, TextAlign.END)

private val sampleText = "The quick brown fox jumps over the lazy dog. " +
    "敏捷的棕色狐狸跳过了懒狗。Mixed 中英文 text layout rendering test."

private class TextMatrixWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = TextMatrixState()

    class TextMatrixState : State<TextMatrixWidget>() {
        private var softWrap = false
        private var maxLinesIdx = 0
        private var overflowIdx = 0
        private var alignIdx = 0

        override fun build(context: BuildContext): Widget {
            // 标签独占一行，按钮放第二行，避免宽屏溢出
            fun controlRow(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) =
                Column(
                    children = listOf(
                        Text(label, style = TextStyle.Default),
                        Row(
                            children = options.mapIndexed { i, opt ->
                                OutlinedButton(
                                    text = opt,
                                    onPressed = { setState { onSelect(i) } },
                                    borderColor = if (i == selected) PixelColor.fromRgb(200, 100, 0) else PixelColor.White,
                                )
                            },
                            spacing = 2,
                        ),
                    ),
                    spacing = 1,
                    crossAxisAlignment = CrossAxisAlignment.START,
                )

            val controls = Column(
                children = listOf(
                    controlRow("softWrap", listOf("OFF", "ON"), if (softWrap) 1 else 0) {
                        softWrap = it == 1
                    },
                    controlRow("maxLines", maxLinesOptions.map { it.toString() }, maxLinesIdx) {
                        maxLinesIdx = it
                    },
                    controlRow(
                        "overflow",
                        listOf("CLIP", "ELLIPSIS"),
                        overflowIdx,
                    ) { overflowIdx = it },
                    controlRow(
                        "textAlign",
                        listOf("START", "CENTER", "END"),
                        alignIdx,
                    ) { alignIdx = it },
                ),
                spacing = 2,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )

            val preview = Padding(
                child = Text(
                    data = sampleText,
                    style = TextStyle.Default,
                    softWrap = softWrap,
                    maxLines = maxLinesOptions[maxLinesIdx],
                    overflow = overflowOptions[overflowIdx],
                    textAlign = alignOptions[alignIdx],
                ),
                all = 4,
            )

            return Column(
                children = listOf(
                    Expanded(child = preview),
                    SizedBox(height = 2),
                    Padding(child = controls, horizontal = 4, vertical = 2),
                ),
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }
    }
}

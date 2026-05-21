package com.purride.pixeldemo.showcase.foundation
import com.purride.pixelcore.PixelColor


import com.purride.pixelui.AlignDirectional
import com.purride.pixelui.AlignmentDirectional
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.ContainerDirectional
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsetsDirectional
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PaddingDirectional
import com.purride.pixelui.Padding
import com.purride.pixelui.Positioned
import com.purride.pixelui.PositionedDirectional
import com.purride.pixelui.Row
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Stack
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextDirection
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object DirectionalVariantsScene : DemoScene {
    override val id = "directional_variants"
    override val title = "方向感知原语"
    override val description = "PaddingDirectional / AlignDirectional / PositionedDirectional / ContainerDirectional 在 LTR ↔ RTL 下镜像"

    override fun build(env: DemoEnv): Widget = DirectionalVariantsWidget(env)
}

private fun rowLabel(label: String): Widget = Text(label, style = TextStyle(color = PixelColor.fromRgb(200, 100, 0)))

private fun frame(child: Widget, height: Int = 28): Widget =
    Container(
        width = 200,
        height = height,
        fillColor = PixelColor.Transparent,
        borderColor = PixelColor.White,
        child = child,
    )

private class DirectionalVariantsWidget(
    private val env: DemoEnv,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = DirectionalVariantsState()

    inner class DirectionalVariantsState : State<DirectionalVariantsWidget>() {
        private var isRtl = false
        private val scrollState = PixelListState()
        private val scrollController = ScrollController()

        override fun build(context: BuildContext): Widget {
            val dirLabel = if (isRtl) "RTL" else "LTR"

            val dot = Container(width = 10, height = 10, fillColor = PixelColor.fromRgb(200, 100, 0), borderColor = null)

            val rows: List<Widget> = listOf(
                Text("当前方向: $dirLabel  — 在 RTL 下 start↔end 自动镜像", style = TextStyle.Default, softWrap = true),
                SizedBox(height = 4),

                rowLabel("PaddingDirectional(start=20)"),
                frame(
                    PaddingDirectional(
                        padding = EdgeInsetsDirectional(start = 20, top = 4, end = 0, bottom = 4),
                        child = dot,
                    ),
                ),
                SizedBox(height = 2),

                rowLabel("AlignDirectional(CENTER_START)"),
                frame(
                    AlignDirectional(
                        alignment = AlignmentDirectional.CENTER_START,
                        child = dot,
                    ),
                ),
                SizedBox(height = 2),

                rowLabel("PositionedDirectional(start=8)"),
                frame(
                    Stack(
                        children = listOf(
                            PositionedDirectional(
                                start = 8, top = 8,
                                child = dot,
                            ),
                        ),
                    ),
                ),
                SizedBox(height = 2),

                rowLabel("ContainerDirectional(start padding=16)"),
                ContainerDirectional(
                    width = 200,
                    height = 28,
                    fillColor = PixelColor.Transparent,
                    borderColor = PixelColor.White,
                    paddingDirectional = EdgeInsetsDirectional(start = 16, top = 4, end = 2, bottom = 4),
                    alignment = AlignmentDirectional.CENTER_START,
                    child = Text("start=16", style = TextStyle.Default),
                ),

                SizedBox(height = 4),
                rowLabel("对照: Positioned(left=8) — 不随方向镜像"),
                frame(
                    Stack(
                        children = listOf(
                            Positioned(
                                left = 8, top = 8,
                                child = dot,
                            ),
                        ),
                    ),
                ),
            )

            return Column(
                children = listOf(
                    Expanded(
                        child = SingleChildScrollView(
                            state = scrollState,
                            controller = scrollController,
                            child = Padding(
                                child = Column(
                                    children = rows,
                                    spacing = 2,
                                    crossAxisAlignment = CrossAxisAlignment.START,
                                ),
                                all = 4,
                            ),
                        ),
                    ),
                    SizedBox(height = 2),
                    Padding(
                        child = Row(
                            children = listOf(
                                OutlinedButton(
                                    "LTR",
                                    onPressed = {
                                        setState { isRtl = false }
                                        widget.env.hostView.textDirection = TextDirection.LTR
                                    },
                                    borderColor = if (!isRtl) PixelColor.fromRgb(200, 100, 0) else PixelColor.White,
                                ),
                                OutlinedButton(
                                    "RTL",
                                    onPressed = {
                                        setState { isRtl = true }
                                        widget.env.hostView.textDirection = TextDirection.RTL
                                    },
                                    borderColor = if (isRtl) PixelColor.fromRgb(200, 100, 0) else PixelColor.White,
                                ),
                            ),
                            spacing = 2,
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

package com.purride.pixeldemo.showcase

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Align
import com.purride.pixelui.Alignment
import com.purride.pixelui.AspectRatio
import com.purride.pixelui.Column
import com.purride.pixelui.ConstrainedBox
import com.purride.pixelui.Container
import com.purride.pixelui.ContainerDirectional
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Directionality
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.EdgeInsetsDirectional
import com.purride.pixelui.Expanded
import com.purride.pixelui.FittedBox
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.PixelBoxConstraints
import com.purride.pixelui.Row
import com.purride.pixelui.SafeArea
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Stack
import com.purride.pixelui.Positioned
import com.purride.pixelui.Text
import com.purride.pixelui.TextDirection
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.Wrap
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.Accent
import com.purride.pixeldemo.scaffold.Blue
import com.purride.pixeldemo.scaffold.ComponentShowcaseScaffold
import com.purride.pixeldemo.scaffold.Cyan
import com.purride.pixeldemo.scaffold.Green
import com.purride.pixeldemo.scaffold.Muted
import com.purride.pixeldemo.scaffold.Pink
import com.purride.pixeldemo.scaffold.Purple
import com.purride.pixeldemo.scaffold.samplePanel
import com.purride.pixeldemo.scaffold.sectionTitle
import com.purride.pixeldemo.scaffold.swatch
import com.purride.pixeldemo.scaffold.DemoEnv

object LayoutShowcaseScene : DemoScene {
    override val id = "components_layout"
    override val title = "基础布局"
    override val summary = "尺寸、约束、排列、叠层、方向感知布局"
    override val category = DemoCatalog.layout
    override val tags = setOf("layout", "foundation", "directionality", "constraints")
    override val apis = setOf(
        "Padding",
        "Align",
        "SizedBox",
        "Row",
        "Column",
        "Stack",
        "Container",
        "SafeArea",
        "Wrap",
        "AspectRatio",
        "ConstrainedBox",
        "FittedBox",
        "Directionality",
    )
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = body())

    private fun body(): Widget =
        Column(
            children = listOf(
                sectionTitle("布局骨架"),
                samplePanel(
                    title = "Row / Column / Expanded",
                    color = Accent,
                    child = Column(
                        children = listOf(
                            Row(
                                children = listOf(
                                    swatch(Cyan, width = 14),
                                    Expanded(child = Container(height = 8, fillColor = Green)),
                                    swatch(Pink, width = 18),
                                ),
                                spacing = 2,
                                crossAxisAlignment = CrossAxisAlignment.CENTER,
                            ),
                            Row(
                                children = listOf(
                                    Container(width = 18, height = 8, fillColor = Accent),
                                    Container(width = 26, height = 8, fillColor = Blue),
                                    Container(width = 12, height = 8, fillColor = Purple),
                                ),
                                spacing = 4,
                                mainAxisAlignment = MainAxisAlignment.CENTER,
                            ),
                        ),
                        spacing = 3,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    ),
                ),
                samplePanel(
                    title = "Stack / Positioned",
                    color = Purple,
                    child = Container(
                        height = 32,
                        borderColor = Purple,
                        child = Stack(
                            children = listOf(
                                Positioned(left = 4, top = 4, child = Container(width = 32, height = 14, fillColor = Blue)),
                                Positioned(left = 22, top = 12, child = Container(width = 32, height = 14, fillColor = Pink)),
                                Positioned(right = 4, bottom = 3, child = Text("HUD", style = TextStyle(color = Accent))),
                            ),
                        ),
                    ),
                ),
                sectionTitle("约束与方向"),
                samplePanel(
                    title = "Wrap / AspectRatio / ConstrainedBox",
                    color = Green,
                    child = Column(
                        children = listOf(
                            Wrap(
                                spacing = 2,
                                runSpacing = 2,
                                children = listOf(
                                    pill("Padding", Cyan),
                                    pill("Align", Green),
                                    pill("SizedBox", Pink),
                                    pill("SafeArea", Accent),
                                ),
                            ),
                            Row(
                                children = listOf(
                                    ConstrainedBox(
                                        constraints = PixelBoxConstraints(minWidth = 28, maxWidth = 28, minHeight = 12, maxHeight = 12),
                                        child = Container(fillColor = Cyan),
                                    ),
                                    AspectRatio(
                                        aspectRatio = 2f,
                                        child = Container(fillColor = Green, borderColor = PixelColor.White),
                                    ),
                                    FittedBox(child = Text("FIT", style = TextStyle(color = Accent))),
                                ),
                                spacing = 3,
                                crossAxisAlignment = CrossAxisAlignment.CENTER,
                            ),
                        ),
                        spacing = 3,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    ),
                ),
                samplePanel(
                    title = "Directionality / ContainerDirectional",
                    color = Blue,
                    child = Directionality(
                        textDirection = TextDirection.RTL,
                        child = ContainerDirectional(
                            paddingDirectional = EdgeInsetsDirectional.only(start = 10, end = 2, top = 2, bottom = 2),
                            borderColor = Blue,
                            alignment = com.purride.pixelui.AlignmentDirectional.CENTER_START,
                            child = Text("RTL START", style = TextStyle(color = Blue)),
                        ),
                    ),
                ),
                SafeArea(
                    child = Align(
                        alignment = Alignment.CENTER,
                        child = SizedBox(
                            height = 10,
                            child = Text("SafeArea / Align / SizedBox", style = TextStyle(color = Muted)),
                        ),
                    ),
                ),
            ),
            spacing = 4,
            mainAxisSize = MainAxisSize.MIN,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        )

    private fun pill(text: String, color: PixelColor): Widget =
        Container(
            padding = EdgeInsets.symmetric(horizontal = 2, vertical = 1),
            borderColor = color,
            child = Text(text, style = TextStyle(color = color)),
        )
}

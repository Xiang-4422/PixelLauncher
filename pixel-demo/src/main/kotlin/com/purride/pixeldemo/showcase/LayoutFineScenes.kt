package com.purride.pixeldemo.showcase

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Align
import com.purride.pixelui.Alignment
import com.purride.pixelui.AspectRatio
import com.purride.pixelui.Center
import com.purride.pixelui.ClipRect
import com.purride.pixelui.Column
import com.purride.pixelui.ConstrainedBox
import com.purride.pixelui.Container
import com.purride.pixelui.ContainerDirectional
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.DecoratedBox
import com.purride.pixelui.Directionality
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.EdgeInsetsDirectional
import com.purride.pixelui.Expanded
import com.purride.pixelui.FittedBox
import com.purride.pixelui.Flexible
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Opacity
import com.purride.pixelui.Padding
import com.purride.pixelui.PixelBoxConstraints
import com.purride.pixelui.Positioned
import com.purride.pixelui.PositionedDirectional
import com.purride.pixelui.PositionedFill
import com.purride.pixelui.Row
import com.purride.pixelui.SafeArea
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Spacer
import com.purride.pixelui.Stack
import com.purride.pixelui.Text
import com.purride.pixelui.TextDirection
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Transform
import com.purride.pixelui.Widget
import com.purride.pixelui.Wrap
import com.purride.pixelui.animation.IntOffset
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.Accent
import com.purride.pixeldemo.scaffold.Blue
import com.purride.pixeldemo.scaffold.ComponentShowcaseScaffold
import com.purride.pixeldemo.scaffold.Cyan
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.Green
import com.purride.pixeldemo.scaffold.Muted
import com.purride.pixeldemo.scaffold.Panel
import com.purride.pixeldemo.scaffold.Pink
import com.purride.pixeldemo.scaffold.Purple
import com.purride.pixeldemo.scaffold.Yellow
import com.purride.pixeldemo.scaffold.samplePanel
import com.purride.pixeldemo.scaffold.sectionTitle
import com.purride.pixeldemo.scaffold.swatch

object LayoutFoundationScene : DemoScene {
    override val id = "layout_foundation"
    override val title = "基础布局"
    override val summary = "Padding、SafeArea、SizedBox、Center 和 Align"
    override val category = DemoCatalog.layout
    override val tags = setOf("layout", "foundation", "padding", "safe-area", "align")
    override val apis = setOf("Padding", "SafeArea", "SizedBox", "Center", "Align", "Container")
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = layoutPageBody(foundationPanels()))
}

object LayoutConstraintsScene : DemoScene {
    override val id = "layout_constraints"
    override val title = "约束"
    override val summary = "ConstrainedBox、AspectRatio 和 FittedBox"
    override val category = DemoCatalog.layout
    override val tags = setOf("layout", "constraints", "size", "fit", "aspect")
    override val apis = setOf("ConstrainedBox", "PixelBoxConstraints", "AspectRatio", "FittedBox", "SizedBox")
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = layoutPageBody(constraintPanels()))
}

object LayoutArrangementScene : DemoScene {
    override val id = "layout_arrangement"
    override val title = "排列"
    override val summary = "Row、Column、Expanded、Flexible、Spacer 和 Wrap"
    override val category = DemoCatalog.layout
    override val tags = setOf("layout", "row", "column", "flex", "wrap")
    override val apis = setOf("Row", "Column", "Expanded", "Flexible", "Spacer", "Wrap")
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = layoutPageBody(arrangementPanels()))
}

object LayoutStackScene : DemoScene {
    override val id = "layout_stack"
    override val title = "叠层"
    override val summary = "Stack、Positioned、Opacity、ClipRect、Transform 和 DecoratedBox"
    override val category = DemoCatalog.layout
    override val tags = setOf("layout", "stack", "positioned", "overlay", "transform", "clip")
    override val apis = setOf("Stack", "Positioned", "PositionedFill", "Opacity", "ClipRect", "Transform.translate", "DecoratedBox")
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = layoutPageBody(stackPanels()))
}

object LayoutDirectionalityScene : DemoScene {
    override val id = "layout_directionality"
    override val title = "方向感知"
    override val summary = "Directionality、ContainerDirectional、PositionedDirectional 和 EdgeInsetsDirectional"
    override val category = DemoCatalog.layout
    override val tags = setOf("layout", "directionality", "rtl", "directional")
    override val apis = setOf("Directionality", "TextDirection", "ContainerDirectional", "EdgeInsetsDirectional", "PositionedDirectional")
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = layoutPageBody(directionalityPanels()))
}

private fun layoutPageBody(panels: List<Widget>): Widget =
    Column(
        children = panels,
        spacing = 4,
        mainAxisSize = MainAxisSize.MIN,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
    )

private fun foundationPanels(): List<Widget> = listOf(
    sectionTitle("基础容器"),
    samplePanel(
        title = "Padding / Container",
        color = Accent,
        child = Padding(
            all = 3,
            child = Container(
                padding = EdgeInsets.all(2),
                borderColor = Accent,
                fillColor = PixelColor.fromRgb(24, 18, 8),
                child = Text("CONTENT", style = TextStyle(color = Accent)),
            ),
        ),
    ),
    samplePanel(
        title = "SafeArea / Center / Align / SizedBox",
        color = Cyan,
        child = SafeArea(
            child = SizedBox(
                height = 28,
                child = Container(
                    borderColor = Cyan,
                    child = Stack(
                        children = listOf(
                            Center(child = swatch(Green, width = 24, height = 8)),
                            Align(
                                alignment = Alignment.CENTER_END,
                                child = Text("BR", style = TextStyle(color = Pink)),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    ),
)

private fun constraintPanels(): List<Widget> = listOf(
    sectionTitle("尺寸约束"),
    samplePanel(
        title = "ConstrainedBox / PixelBoxConstraints",
        color = Green,
        child = Row(
            children = listOf(
                ConstrainedBox(
                    constraints = PixelBoxConstraints(minWidth = 34, maxWidth = 34, minHeight = 14, maxHeight = 14),
                    child = Container(fillColor = Green),
                ),
                ConstrainedBox(
                    constraints = PixelBoxConstraints(minWidth = 18, maxWidth = 46, minHeight = 10, maxHeight = 18),
                    child = Container(fillColor = Cyan, borderColor = PixelColor.White),
                ),
            ),
            spacing = 4,
            crossAxisAlignment = CrossAxisAlignment.CENTER,
        ),
    ),
    samplePanel(
        title = "AspectRatio / FittedBox",
        color = Yellow,
        child = Row(
            children = listOf(
                Expanded(
                    child = AspectRatio(
                        aspectRatio = 2f,
                        child = Container(fillColor = Yellow, borderColor = PixelColor.White),
                    ),
                ),
                Container(
                    width = 28,
                    height = 18,
                    borderColor = Yellow,
                    child = FittedBox(child = Text("FIT", style = TextStyle(color = Yellow))),
                ),
            ),
            spacing = 3,
            crossAxisAlignment = CrossAxisAlignment.CENTER,
        ),
    ),
)

private fun arrangementPanels(): List<Widget> = listOf(
    sectionTitle("主轴与弹性"),
    samplePanel(
        title = "Row / Expanded / Flexible / Spacer",
        color = Blue,
        child = Column(
            children = listOf(
                Row(
                    children = listOf(
                        swatch(Cyan, width = 12),
                        Expanded(child = Container(height = 8, fillColor = Green)),
                        Flexible(flex = 1, child = Container(height = 8, fillColor = Yellow)),
                        swatch(Pink, width = 16),
                    ),
                    spacing = 2,
                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                ),
                Row(
                    children = listOf(
                        swatch(Accent, width = 18),
                        Spacer(),
                        swatch(Blue, width = 24),
                    ),
                    spacing = 2,
                    mainAxisAlignment = MainAxisAlignment.CENTER,
                ),
            ),
            spacing = 3,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        ),
    ),
    samplePanel(
        title = "Column / Wrap",
        color = Pink,
        child = Column(
            children = listOf(
                Row(
                    children = listOf(
                        Container(width = 12, height = 22, fillColor = Pink),
                        Container(width = 18, height = 12, fillColor = Purple),
                        Container(width = 24, height = 8, fillColor = Accent),
                    ),
                    spacing = 3,
                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                ),
                Wrap(
                    spacing = 2,
                    runSpacing = 2,
                    children = listOf(
                        pill("Row", Cyan),
                        pill("Column", Green),
                        pill("Expanded", Yellow),
                        pill("Wrap", Pink),
                    ),
                ),
            ),
            spacing = 3,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        ),
    ),
)

private fun stackPanels(): List<Widget> = listOf(
    sectionTitle("覆盖与变换"),
    samplePanel(
        title = "Stack / Positioned / PositionedFill",
        color = Purple,
        child = Container(
            height = 36,
            borderColor = Purple,
            child = Stack(
                children = listOf(
                    PositionedFill(child = Opacity(opacity = 0.35f, child = Container(fillColor = PixelColor.fromRgb(12, 10, 20)))),
                    Positioned(left = 4, top = 4, child = Container(width = 34, height = 12, fillColor = Blue)),
                    Positioned(left = 20, top = 14, child = Container(width = 34, height = 14, fillColor = Pink)),
                    Positioned(right = 4, bottom = 3, child = Text("HUD", style = TextStyle(color = Accent))),
                ),
            ),
        ),
    ),
    samplePanel(
        title = "DecoratedBox / ClipRect / Transform.translate",
        color = Yellow,
        child = DecoratedBox(
            fillColor = Panel,
            borderColor = Yellow,
            child = ClipRect(
                child = Transform.translate(
                    offset = IntOffset(4, 1),
                    child = Text("SHIFTED", style = TextStyle(color = Yellow)),
                ),
            ),
        ),
    ),
)

private fun directionalityPanels(): List<Widget> = listOf(
    sectionTitle("方向感知布局"),
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
    samplePanel(
        title = "PositionedDirectional",
        color = Green,
        child = Directionality(
            textDirection = TextDirection.RTL,
            child = Container(
                height = 30,
                borderColor = Green,
                child = Stack(
                    children = listOf(
                        PositionedFill(child = Container(fillColor = PixelColor.fromRgb(8, 18, 12))),
                        PositionedDirectional(start = 4, top = 4, child = Container(width = 28, height = 10, fillColor = Green)),
                        PositionedDirectional(end = 4, bottom = 4, child = Text("END", style = TextStyle(color = Accent))),
                    ),
                ),
            ),
        ),
    ),
)

private fun pill(text: String, color: PixelColor): Widget =
    Container(
        padding = EdgeInsets.symmetric(horizontal = 2, vertical = 1),
        borderColor = color,
        child = Text(text, style = TextStyle(color = color)),
    )

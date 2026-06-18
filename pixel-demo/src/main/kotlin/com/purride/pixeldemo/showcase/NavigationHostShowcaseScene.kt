package com.purride.pixeldemo.showcase

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.AppScaffold
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Dialog
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelDebugOverlay
import com.purride.pixelui.PixelHostFrameStats
import com.purride.pixelui.Row
import com.purride.pixelui.Semantics
import com.purride.pixelui.Snackbar
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Toast
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.Accent
import com.purride.pixeldemo.scaffold.Blue
import com.purride.pixeldemo.scaffold.ComponentShowcaseScaffold
import com.purride.pixeldemo.scaffold.Cyan
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.Green
import com.purride.pixeldemo.scaffold.Muted
import com.purride.pixeldemo.scaffold.Pink
import com.purride.pixeldemo.scaffold.Purple
import com.purride.pixeldemo.scaffold.samplePanel
import com.purride.pixeldemo.scaffold.sectionTitle

object NavigationHostShowcaseScene : DemoScene {
    override val id = "components_navigation_host"
    override val title = "导航宿主"
    override val summary = "Navigator、反馈、焦点、表单、语义、宿主和调试"
    override val category = DemoCatalog.navigation
    override val tags = setOf("navigator", "host", "debug", "focus", "form", "semantics", "feedback")
    override val apis = setOf(
        "PixelNavigator",
        "PixelRoute",
        "Dialog",
        "Toast",
        "Snackbar",
        "Focus",
        "Form",
        "Semantics",
        "PixelHostView",
        "PixelHostBridge",
        "PixelDebugOverlay",
        "PixelInspectorPanel",
    )
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = body())

    private fun body(): Widget =
        Column(
            children = listOf(
                sectionTitle("导航与反馈"),
                samplePanel(
                    title = "AppScaffold / Dialog / Toast / Snackbar",
                    color = Blue,
                    child = Column(
                        children = listOf(
                            Container(
                                height = 52,
                                borderColor = Blue,
                                child = AppScaffold(
                                    title = Text("ROUTE / HOME", style = TextStyle(color = Blue)),
                                    body = Dialog(
                                        title = Text("Dialog", style = TextStyle(color = Accent)),
                                        content = Text("route result / guard", style = TextStyle(color = PixelColor.White)),
                                        actions = listOf(OutlinedButton(text = "OK", onPressed = {}, borderColor = Accent)),
                                        borderColor = Blue,
                                    ),
                                    bottomBar = Snackbar(
                                        message = "Snackbar",
                                        action = Text("ACTION", style = TextStyle(color = Accent)),
                                    ),
                                ),
                            ),
                            Toast(message = "Toast overlay", textStyle = TextStyle(color = Green)),
                        ),
                        spacing = 3,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    ),
                ),
                sectionTitle("宿主和语义"),
                samplePanel(
                    title = "Semantics / Focus / Form",
                    color = Green,
                    child = Column(
                        children = listOf(
                            Semantics(
                                label = "semantic action",
                                role = com.purride.pixelui.PixelSemanticRole.BUTTON,
                                child = Container(
                                    padding = EdgeInsets.all(2),
                                    borderColor = Green,
                                    child = Text("SEMANTIC BUTTON", style = TextStyle(color = Green)),
                                ),
                            ),
                            Row(
                                children = listOf(
                                    routeChip("Focus", Cyan),
                                    routeChip("Form", Pink),
                                    routeChip("HostBridge", Accent),
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
                    title = "PixelDebugOverlay",
                    color = Purple,
                    child = PixelDebugOverlay(
                        stats = PixelHostFrameStats(
                            deltaMs = 16,
                            fpsAvg = 60f,
                            paintTimeNanos = 1_200_000,
                            frameCount = 240,
                        ),
                        activeTickerCount = 2,
                    ),
                ),
            ),
            spacing = 4,
            mainAxisSize = MainAxisSize.MIN,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        )

    private fun routeChip(label: String, color: PixelColor): Widget =
        Container(
            padding = EdgeInsets.symmetric(horizontal = 2, vertical = 1),
            borderColor = color,
            child = Text(label, style = TextStyle(color = color)),
        )
}

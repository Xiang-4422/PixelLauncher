package com.purride.pixeldemo.scaffold

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.Wrap
import com.purride.pixelui.state.PixelListState
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.settings.DemoSettingsScene

fun ComponentShowcaseScaffold(
    item: DemoScene,
    env: DemoEnv,
    body: Widget,
): Widget = ComponentShowcaseFrame(item = item, env = env, body = body)

fun sectionTitle(text: String, color: PixelColor = Accent): Widget =
    Text(text, style = TextStyle(color = color))

fun samplePanel(
    title: String,
    child: Widget,
    color: PixelColor = Muted,
): Widget =
    Container(
        padding = EdgeInsets.all(3),
        borderColor = color,
        fillColor = Panel,
        child = child,
    )

fun apiTags(
    names: Iterable<String>,
    color: PixelColor = Accent,
): Widget =
    Wrap(
        spacing = 1,
        runSpacing = 1,
        children = names.map { name ->
            Container(
                padding = EdgeInsets.symmetric(horizontal = 1, vertical = 0),
                borderColor = color,
                child = Text(name, style = TextStyle(color = color)),
            )
        },
    )

fun swatch(color: PixelColor, width: Int = 12, height: Int = 8): Widget =
    Container(width = width, height = height, fillColor = color, borderColor = PixelColor.White)

val Accent: PixelColor = PixelColor.fromRgb(255, 176, 64)
val Cyan: PixelColor = PixelColor.fromRgb(92, 220, 255)
val Green: PixelColor = PixelColor.fromRgb(120, 245, 150)
val Pink: PixelColor = PixelColor.fromRgb(255, 120, 160)
val Purple: PixelColor = PixelColor.fromRgb(180, 150, 255)
val Yellow: PixelColor = PixelColor.fromRgb(255, 230, 100)
val Blue: PixelColor = PixelColor.fromRgb(120, 180, 255)
val Muted: PixelColor = PixelColor.fromRgb(140, 160, 170)
val Panel: PixelColor = PixelColor.fromRgb(14, 18, 22)

private class ComponentShowcaseFrame(
    private val item: DemoScene,
    private val env: DemoEnv,
    private val body: Widget,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = FrameState()

    inner class FrameState : State<ComponentShowcaseFrame>() {
        private val scrollState = PixelListState()
        private val scrollController = ScrollController()

        override fun build(context: com.purride.pixelui.BuildContext): Widget {
            return Column(
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                children = listOf(
                    header(),
                    Expanded(child = content()),
                    footer(),
                ),
            )
        }

        private fun header(): Widget =
            Padding(
                horizontal = 4,
                vertical = 2,
                child = Text(item.title, style = TextStyle(color = Accent)),
            )

        private fun content(): Widget =
            SingleChildScrollView(
                state = scrollState,
                controller = scrollController,
                child = Padding(
                    horizontal = 4,
                    vertical = 2,
                    child = body,
                ),
            )

        private fun footer(): Widget {
            val previous = DemoCatalog.previousItem(item.id)
            val next = DemoCatalog.nextItem(item.id)
            return Padding(
                horizontal = 4,
                vertical = 2,
                child = Row(
                    children = listOf(
                        OutlinedButton(text = "BACK", onPressed = { env.navigator.pop() }, borderColor = Muted),
                        OutlinedButton(
                            text = "PREV",
                            onPressed = previous?.let { { env.navigator.replace(it) } },
                            enabled = previous != null,
                            borderColor = if (previous != null) Accent else Muted,
                        ),
                        OutlinedButton(
                            text = "NEXT",
                            onPressed = next?.let { { env.navigator.replace(it) } },
                            enabled = next != null,
                            borderColor = if (next != null) Accent else Muted,
                        ),
                        Expanded(
                            child = Row(
                                children = listOf(
                                    OutlinedButton(
                                        text = "SETTINGS",
                                        onPressed = { env.navigator.push(DemoSettingsScene) },
                                        borderColor = Blue,
                                    ),
                                ),
                                mainAxisAlignment = MainAxisAlignment.END,
                            ),
                        ),
                    ),
                    spacing = 2,
                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                ),
            )
        }
    }
}

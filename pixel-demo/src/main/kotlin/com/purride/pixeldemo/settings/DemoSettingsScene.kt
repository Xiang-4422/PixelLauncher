package com.purride.pixeldemo.settings

import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelShape
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object DemoSettingsScene : DemoScene {
    override val id = "settings"
    override val title = "SETTINGS"
    override val description = "display / pixel / font"

    override fun build(env: DemoEnv): Widget = SettingsWidget(env)
}

private class SettingsWidget(
    private val env: DemoEnv,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = SettingsState()

    inner class SettingsState : State<SettingsWidget>() {
        private val scrollState = PixelListState()
        private val scrollController = ScrollController()
        private lateinit var settings: DemoAppSettings

        override fun initState() {
            super.initState()
            settings = widget.env.currentSettings
        }

        private fun update(newSettings: DemoAppSettings) {
            setState { settings = newSettings }
            widget.env.applySettings(newSettings)
        }

        override fun build(context: BuildContext): Widget {
            val s = settings
            return SingleChildScrollView(
                state = scrollState,
                controller = scrollController,
                child = Padding(
                    child = Column(
                        children = listOf(
                            sectionHeader("DISPLAY"),
                            settingRow(
                                label = "SHAPE",
                                children = listOf(
                                    optionBtn("SQ", s.pixelShape == PixelShape.SQUARE) {
                                        update(s.copy(pixelShape = PixelShape.SQUARE))
                                    },
                                    optionBtn("O", s.pixelShape == PixelShape.CIRCLE) {
                                        update(s.copy(pixelShape = PixelShape.CIRCLE))
                                    },
                                    optionBtn("◇", s.pixelShape == PixelShape.DIAMOND) {
                                        update(s.copy(pixelShape = PixelShape.DIAMOND))
                                    },
                                ),
                            ),
                            settingRow(
                                label = "DOT SIZE",
                                children = listOf(8, 10, 12, 16).map { px ->
                                    optionBtn("$px", s.dotSizePx == px) {
                                        update(s.copy(dotSizePx = px))
                                    }
                                },
                            ),
                            settingRow(
                                label = "PIXEL GAP",
                                children = listOf(
                                    optionBtn("ON", s.pixelGapEnabled) {
                                        update(s.copy(pixelGapEnabled = true))
                                    },
                                    optionBtn("OFF", !s.pixelGapEnabled) {
                                        update(s.copy(pixelGapEnabled = false))
                                    },
                                ),
                            ),
                            SizedBox(height = 4),
                            sectionHeader("FONT"),
                            settingRow(
                                label = "STYLE",
                                children = listOf(
                                    optionBtn("PROP", s.fontStyle == DemoFontStyle.PROPORTIONAL) {
                                        update(s.copy(fontStyle = DemoFontStyle.PROPORTIONAL))
                                    },
                                    optionBtn("MONO", s.fontStyle == DemoFontStyle.MONOSPACED) {
                                        update(s.copy(fontStyle = DemoFontStyle.MONOSPACED))
                                    },
                                ),
                            ),
                            settingRow(
                                label = "SIZE",
                                children = listOf(8, 10, 12).map { px ->
                                    optionBtn("${px}PX", s.fontSizePx == px) {
                                        update(s.copy(fontSizePx = px))
                                    }
                                },
                            ),
                            SizedBox(height = 4),
                        ),
                        spacing = 0,
                        mainAxisSize = MainAxisSize.MIN,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    ),
                    horizontal = 4,
                    vertical = 4,
                ),
            )
        }

        private fun sectionHeader(label: String): Widget =
            Padding(
                child = Text(label, style = TextStyle.Default),
                horizontal = 0,
                vertical = 2,
            )

        private fun settingRow(label: String, children: List<Widget>): Widget =
            Padding(
                child = Column(
                    children = listOf(
                        Text(label, style = TextStyle.Default),
                        SizedBox(height = 2),
                        Row(children = children, spacing = 2),
                    ),
                    spacing = 0,
                    mainAxisSize = MainAxisSize.MIN,
                    crossAxisAlignment = CrossAxisAlignment.START,
                ),
                horizontal = 0,
                vertical = 3,
            )

        private fun optionBtn(text: String, selected: Boolean, onTap: () -> Unit): Widget =
            OutlinedButton(
                text = text,
                onPressed = onTap,
                borderColor = if (selected) PixelColor.fromRgb(200, 100, 0) else PixelColor.White,
            )
    }
}

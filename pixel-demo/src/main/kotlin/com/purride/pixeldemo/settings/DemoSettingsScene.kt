package com.purride.pixeldemo.settings

import com.purride.pixelcore.PixelColorMode
import com.purride.pixelcore.PixelShape
import com.purride.pixelcore.PixelTheme
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
                                label = "COLOR MODE",
                                children = listOf(
                                    optionBtn("MONO", s.colorMode == PixelColorMode.Mono) {
                                        update(s.copy(colorMode = PixelColorMode.Mono))
                                    },
                                    optionBtn("COLOR", s.colorMode == PixelColorMode.Color) {
                                        update(s.copy(colorMode = PixelColorMode.Color))
                                    },
                                ),
                            ),
                            settingRow(
                                label = "MONO THEME",
                                children = themeOptions(s),
                            ),
                            settingRow(
                                label = "COLOR THEME",
                                children = colorThemeOptions(s),
                            ),
                            SizedBox(height = 4),
                            sectionHeader("PIXEL"),
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

        private fun colorThemeOptions(s: DemoAppSettings): List<Widget> = listOf(
            optionBtn("DARK", s.colorTheme == DemoColorTheme.DARK) {
                update(s.copy(colorTheme = DemoColorTheme.DARK))
            },
            optionBtn("LIGHT", s.colorTheme == DemoColorTheme.LIGHT) {
                update(s.copy(colorTheme = DemoColorTheme.LIGHT))
            },
            optionBtn("OCEAN", s.colorTheme == DemoColorTheme.OCEAN) {
                update(s.copy(colorTheme = DemoColorTheme.OCEAN))
            },
            optionBtn("AMBER", s.colorTheme == DemoColorTheme.AMBER) {
                update(s.copy(colorTheme = DemoColorTheme.AMBER))
            },
        )

        private fun themeOptions(s: DemoAppSettings): List<Widget> = listOf(
            optionBtn("GRN", s.monoTheme == PixelTheme.GREEN_PHOSPHOR) {
                update(s.copy(monoTheme = PixelTheme.GREEN_PHOSPHOR))
            },
            optionBtn("AMB", s.monoTheme == PixelTheme.AMBER_CRT) {
                update(s.copy(monoTheme = PixelTheme.AMBER_CRT))
            },
            optionBtn("ICE", s.monoTheme == PixelTheme.ICE_LCD) {
                update(s.copy(monoTheme = PixelTheme.ICE_LCD))
            },
            optionBtn("LCD", s.monoTheme == PixelTheme.MONO_LCD) {
                update(s.copy(monoTheme = PixelTheme.MONO_LCD))
            },
            optionBtn("NGT", s.monoTheme == PixelTheme.NIGHT_MONO) {
                update(s.copy(monoTheme = PixelTheme.NIGHT_MONO))
            },
        )

        private fun sectionHeader(label: String): Widget =
            Padding(
                child = Text(label, style = TextStyle.Accent),
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
                selected = selected,
            )
    }
}

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
import com.purride.pixelui.Slider
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import kotlin.math.roundToInt

// Dot-size slider range: 4 px … 24 px
private const val DOT_MIN_PX = 4
private const val DOT_MAX_PX = 24
private const val DOT_RANGE_F = (DOT_MAX_PX - DOT_MIN_PX).toFloat()

private fun dotSizeToPct(px: Int): Float =
    ((px - DOT_MIN_PX) / DOT_RANGE_F).coerceIn(0f, 1f)

private fun pctToDotSize(pct: Float): Int =
    (DOT_MIN_PX + pct * DOT_RANGE_F).roundToInt().coerceIn(DOT_MIN_PX, DOT_MAX_PX)

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

        // Slider draft for dot size — follows the thumb while dragging;
        // the committed dotSizePx only updates on release.
        private var dotSizeSliderValue: Float = 0f

        override fun initState() {
            super.initState()
            settings = widget.env.currentSettings
            dotSizeSliderValue = dotSizeToPct(settings.dotSizePx)
        }

        private fun update(newSettings: DemoAppSettings) {
            setState { settings = newSettings }
            widget.env.applySettings(newSettings)
        }

        override fun build(context: BuildContext): Widget {
            val s = settings
            val previewDotSizePx = pctToDotSize(dotSizeSliderValue)
            val gapPct = (s.pixelGapRatio * 100).roundToInt()
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
                            // DOT SIZE — commits on release, thumb follows drag visually
                            sliderRow(
                                label = "DOT SIZE  ${previewDotSizePx}PX",
                                value = dotSizeSliderValue,
                                onDrag = { ratio ->
                                    setState { dotSizeSliderValue = ratio }
                                },
                                onRelease = { ratio ->
                                    dotSizeSliderValue = ratio
                                    update(settings.copy(dotSizePx = pctToDotSize(ratio)))
                                },
                            ),
                            // PIXEL GAP — takes effect in real time while dragging
                            sliderRow(
                                label = "PIXEL GAP  $gapPct%",
                                value = s.pixelGapRatio,
                                onDrag = { ratio ->
                                    update(settings.copy(pixelGapRatio = ratio))
                                },
                                onRelease = { ratio ->
                                    update(settings.copy(pixelGapRatio = ratio))
                                },
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

        private fun sliderRow(
            label: String,
            value: Float,
            onDrag: (Float) -> Unit,
            onRelease: (Float) -> Unit,
        ): Widget =
            Padding(
                child = Column(
                    children = listOf(
                        Text(label, style = TextStyle.Default),
                        SizedBox(height = 2),
                        Slider(value = value, onDrag = onDrag, onRelease = onRelease),
                    ),
                    spacing = 0,
                    mainAxisSize = MainAxisSize.MIN,
                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
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

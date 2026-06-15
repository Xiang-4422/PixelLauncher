package com.purride.pixeldemo.showcase.theme

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Checkbox
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.ListTile
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Switch
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object AppLayerThemeScene : DemoScene {
    override val id = "app_layer_theme"
    override val title = "APP THEME OBJECT"
    override val description = "用业务层 data class 显式驱动 PixelColor 样式"

    override fun build(env: DemoEnv): Widget = AppLayerThemeWidget(env = env)
}

private data class DemoAppTheme(
    val name: String,
    val background: PixelColor,
    val grid: PixelColor,
    val panel: PixelColor,
    val text: PixelColor,
    val dim: PixelColor,
    val accent: PixelColor,
    val success: PixelColor,
)

private val appThemes = listOf(
    DemoAppTheme(
        name = "TERMINAL",
        background = PixelColor.Black,
        grid = PixelColor.fromRgb(8, 24, 12),
        panel = PixelColor.fromRgb(6, 18, 10),
        text = PixelColor.fromRgb(150, 255, 170),
        dim = PixelColor.fromRgb(72, 120, 82),
        accent = PixelColor.fromRgb(255, 220, 120),
        success = PixelColor.fromRgb(80, 180, 110),
    ),
    DemoAppTheme(
        name = "OCEAN",
        background = PixelColor.fromRgb(0, 6, 18),
        grid = PixelColor.fromRgb(0, 18, 36),
        panel = PixelColor.fromRgb(0, 14, 28),
        text = PixelColor.fromRgb(160, 230, 255),
        dim = PixelColor.fromRgb(60, 110, 150),
        accent = PixelColor.fromRgb(80, 180, 255),
        success = PixelColor.fromRgb(90, 210, 190),
    ),
    DemoAppTheme(
        name = "AMBER",
        background = PixelColor.fromRgb(18, 10, 0),
        grid = PixelColor.fromRgb(36, 20, 0),
        panel = PixelColor.fromRgb(28, 16, 0),
        text = PixelColor.fromRgb(255, 220, 150),
        dim = PixelColor.fromRgb(150, 95, 40),
        accent = PixelColor.fromRgb(255, 170, 60),
        success = PixelColor.fromRgb(230, 190, 80),
    ),
)

private class AppLayerThemeWidget(
    private val env: DemoEnv,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = AppLayerThemeState(env)

    private class AppLayerThemeState(
        private val env: DemoEnv,
    ) : State<AppLayerThemeWidget>() {
        private var themeIndex = 0
        private var enabled = true
        private var checked = true

        override fun build(context: BuildContext): Widget {
            val theme = appThemes[themeIndex]
            val controls = appThemes.mapIndexed { index, item ->
                OutlinedButton(
                    text = item.name,
                    onPressed = {
                        setState {
                            themeIndex = index
                        }
                    },
                    borderColor = if (index == themeIndex) theme.accent else theme.dim,
                    fillColor = if (index == themeIndex) theme.panel else null,
                )
            }
            return Column(
                children = listOf(
                    Expanded(
                        child = Padding(
                            all = 6,
                            child = Container(
                                padding = EdgeInsets.all(3),
                                fillColor = theme.panel,
                                borderColor = theme.accent,
                                child = Column(
                                    children = listOf(
                                        Text("APP THEME: ${theme.name}", style = TextStyle(color = theme.text)),
                                        Text("Plain Kotlin object -> explicit PixelColor", style = TextStyle(color = theme.dim)),
                                        SizedBox(height = 2),
                                        ProgressBar(
                                            progress = if (enabled) 0.72f else 0.28f,
                                            color = theme.success,
                                            trackColor = theme.dim,
                                        ),
                                        ListTile(
                                            leading = Checkbox(
                                                checked = checked,
                                                onChanged = { value -> setState { checked = value } },
                                                activeColor = theme.success,
                                                inactiveColor = theme.dim,
                                                enabled = enabled,
                                            ),
                                            title = Text("Semantic colors", style = TextStyle(color = theme.text)),
                                            subtitle = Text("text / dim / accent / success", style = TextStyle(color = theme.dim)),
                                            trailing = Switch(
                                                checked = enabled,
                                                onChanged = { value -> setState { enabled = value } },
                                                activeColor = theme.success,
                                                inactiveColor = theme.dim,
                                            ),
                                            onTap = { setState { checked = !checked } },
                                            enabled = enabled,
                                        ),
                                        Row(
                                            children = listOf(
                                                OutlinedButton(
                                                    text = "APPLY HOST",
                                                    onPressed = {
                                                        env.hostView.backgroundColor = theme.background
                                                        env.hostView.pixelGridColor = theme.grid
                                                    },
                                                    borderColor = theme.accent,
                                                ),
                                                OutlinedButton(
                                                    text = if (enabled) "ON" else "OFF",
                                                    onPressed = { setState { enabled = !enabled } },
                                                    borderColor = if (enabled) theme.success else theme.dim,
                                                ),
                                            ),
                                            spacing = 2,
                                        ),
                                    ),
                                    spacing = 2,
                                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                ),
                            ),
                        ),
                    ),
                    SizedBox(height = 2),
                    Row(children = controls, spacing = 2),
                ),
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }
    }
}

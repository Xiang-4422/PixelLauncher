package com.purride.pixellauncherv2.ui.theme

import android.content.Context
import com.purride.pixelcore.PixelColor
import com.purride.pixellauncherv2.launcher.PixelTheme
import org.json.JSONObject

data class LauncherTheme(
    val id: String,
    val label: String,
    val mode: LauncherThemeMode,
    val surface: SurfaceColors,
    val text: TextColors,
    val statusBar: StatusBarColors,
    val drawer: DrawerColors,
    val settings: SettingsColors,
    val button: ButtonColors,
    val sms: SmsColors,
    val semantic: SemanticColors,
)

enum class LauncherThemeMode {
    LIGHT,
    DARK,
}

data class SurfaceColors(
    val appBackground: PixelColor,
    val pixelGrid: PixelColor,
    val panel: PixelColor,
    val panelSubtle: PixelColor,
)

data class TextColors(
    val primary: PixelColor,
    val secondary: PixelColor,
    val muted: PixelColor,
    val inverse: PixelColor,
)

data class StatusBarColors(
    val text: PixelColor,
    val mutedText: PixelColor,
    val divider: PixelColor,
    val charging: PixelColor,
    val searchText: PixelColor,
    val searchPlaceholder: PixelColor,
)

data class DrawerColors(
    val itemText: PixelColor,
    val itemTextMuted: PixelColor,
    val searchText: PixelColor,
    val searchPlaceholder: PixelColor,
)

data class SettingsColors(
    val itemTitle: PixelColor,
    val itemValue: PixelColor,
)

data class ButtonColors(
    val text: PixelColor,
    val border: PixelColor,
    val pressedFill: PixelColor,
    val disabledText: PixelColor,
)

data class SmsColors(
    val sender: PixelColor,
    val timestamp: PixelColor,
    val body: PixelColor,
    val draftBorder: PixelColor,
)

data class SemanticColors(
    val success: PixelColor,
    val warning: PixelColor,
    val danger: PixelColor,
    val info: PixelColor,
)

object LauncherThemes {
    private val cache = mutableMapOf<PixelTheme, LauncherTheme>()

    fun from(context: Context, pixelTheme: PixelTheme): LauncherTheme =
        cache.getOrPut(pixelTheme) {
            context.assets.open("themes/${pixelTheme.fileName}").bufferedReader().use { reader ->
                parse(reader.readText())
            }
        }

    fun fallbackFrom(pixelTheme: PixelTheme): LauncherTheme =
        fallbackThemes.getValue(pixelTheme)

    private fun parse(rawJson: String): LauncherTheme {
        val json = JSONObject(rawJson)
        val colors = json.getJSONObject("colors")
        return LauncherTheme(
            id = json.getString("id"),
            label = json.getString("label"),
            mode = LauncherThemeMode.valueOf(json.getString("mode").uppercase()),
            surface = colors.getJSONObject("surface").let {
                SurfaceColors(
                    appBackground = it.color("appBackground"),
                    pixelGrid = it.color("pixelGrid"),
                    panel = it.color("panel"),
                    panelSubtle = it.color("panelSubtle"),
                )
            },
            text = colors.getJSONObject("text").let {
                TextColors(
                    primary = it.color("primary"),
                    secondary = it.color("secondary"),
                    muted = it.color("muted"),
                    inverse = it.color("inverse"),
                )
            },
            statusBar = colors.getJSONObject("statusBar").let {
                StatusBarColors(
                    text = it.color("text"),
                    mutedText = it.color("mutedText"),
                    divider = it.color("divider"),
                    charging = it.color("charging"),
                    searchText = it.color("searchText"),
                    searchPlaceholder = it.color("searchPlaceholder"),
                )
            },
            drawer = colors.getJSONObject("drawer").let {
                DrawerColors(
                    itemText = it.color("itemText"),
                    itemTextMuted = it.color("itemTextMuted"),
                    searchText = it.color("searchText"),
                    searchPlaceholder = it.color("searchPlaceholder"),
                )
            },
            settings = colors.getJSONObject("settings").let {
                SettingsColors(
                    itemTitle = it.color("itemTitle"),
                    itemValue = it.color("itemValue"),
                )
            },
            button = colors.getJSONObject("button").let {
                ButtonColors(
                    text = it.color("text"),
                    border = it.color("border"),
                    pressedFill = it.color("pressedFill"),
                    disabledText = it.color("disabledText"),
                )
            },
            sms = colors.getJSONObject("sms").let {
                SmsColors(
                    sender = it.color("sender"),
                    timestamp = it.color("timestamp"),
                    body = it.color("body"),
                    draftBorder = it.color("draftBorder"),
                )
            },
            semantic = colors.getJSONObject("semantic").let {
                SemanticColors(
                    success = it.color("success"),
                    warning = it.color("warning"),
                    danger = it.color("danger"),
                    info = it.color("info"),
                )
            },
        )
    }

    private fun JSONObject.color(name: String): PixelColor =
        parseColor(getString(name))

    private fun parseColor(hex: String): PixelColor {
        val value = hex.removePrefix("#")
        require(value.length == 6) { "Expected #RRGGBB color, got $hex" }
        return PixelColor.fromRgb(
            value.substring(0, 2).toInt(16),
            value.substring(2, 4).toInt(16),
            value.substring(4, 6).toInt(16),
        )
    }

    private val fallbackThemes: Map<PixelTheme, LauncherTheme> by lazy {
        PixelTheme.entries.associateWith { theme ->
            parse(FallbackThemeJson.byTheme.getValue(theme))
        }
    }
}

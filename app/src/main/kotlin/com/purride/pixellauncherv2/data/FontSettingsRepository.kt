package com.purride.pixellauncherv2.data

import android.content.Context
import com.purride.pixellauncherv2.launcher.DrawerListAlignment
import com.purride.pixellauncherv2.launcher.ChargeIdleEffect
import com.purride.pixellauncherv2.launcher.PixelFontCatalog
import com.purride.pixellauncherv2.launcher.PixelFontStyle
import com.purride.pixellauncherv2.render.PixelShape
import com.purride.pixellauncherv2.launcher.PixelTheme
import com.purride.pixellauncherv2.render.ScreenProfileFactory

class FontSettingsRepository(
    context: Context,
) {

    private val sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    data class AppearanceSettings(
        val fontStyle: PixelFontStyle,
        val pixelShape: PixelShape,
        val dotSizePx: Int,
        val pixelGapEnabled: Boolean,
        val pixelGapRatio: Float,
        val theme: PixelTheme,
    )

    data class UiBehaviorSettings(
        val drawerListAlignment: DrawerListAlignment,
        val isIdlePageEnabled: Boolean,
        val openDrawerInSearchMode: Boolean,
        val chargeIdleEffect: ChargeIdleEffect,
    )

    fun getAppearanceSettings(): AppearanceSettings {
        return AppearanceSettings(
            fontStyle = readStoredFontStyle(),
            pixelShape = readStoredPixelShape() ?: PixelShape.SQUARE,
            dotSizePx = readStoredDotSizePx(),
            pixelGapEnabled = readStoredPixelGapEnabled(),
            pixelGapRatio = readStoredPixelGapRatio(),
            theme = readStoredTheme(),
        )
    }

    fun getUiBehaviorSettings(): UiBehaviorSettings {
        return UiBehaviorSettings(
            drawerListAlignment = readStoredDrawerListAlignment(),
            isIdlePageEnabled = false,
            openDrawerInSearchMode = sharedPreferences.getBoolean(KEY_OPEN_DRAWER_IN_SEARCH_MODE, false),
            chargeIdleEffect = readStoredChargeIdleEffect(),
        )
    }

    fun setAppearanceSettings(
        fontStyle: PixelFontStyle,
        pixelShape: PixelShape,
        dotSizePx: Int,
        pixelGapEnabled: Boolean,
        pixelGapRatio: Float,
        theme: PixelTheme,
    ) {
        val safeDotSizePx = ScreenProfileFactory.supportedDotSizePxOptions.firstOrNull { it == dotSizePx }
            ?: ScreenProfileFactory.defaultDotSizePx
        sharedPreferences.edit()
            .putString(KEY_FONT_STYLE, fontStyle.name)
            .putString(KEY_PIXEL_SHAPE, pixelShape.name)
            .putInt(KEY_DOT_SIZE_PX, safeDotSizePx)
            .putBoolean(KEY_PIXEL_GAP_ENABLED, pixelGapEnabled)
            .putFloat(KEY_PIXEL_GAP_RATIO, pixelGapRatio.coerceIn(0f, 1f))
            .putString(KEY_THEME, theme.name)
            .apply()
    }

    fun setUiBehaviorSettings(
        drawerListAlignment: DrawerListAlignment,
        isIdlePageEnabled: Boolean,
        openDrawerInSearchMode: Boolean,
        chargeIdleEffect: ChargeIdleEffect,
    ) {
        sharedPreferences.edit()
            .putString(KEY_DRAWER_LIST_ALIGNMENT, drawerListAlignment.name)
            .putBoolean(KEY_IDLE_PAGE_ENABLED, isIdlePageEnabled)
            .putBoolean(KEY_OPEN_DRAWER_IN_SEARCH_MODE, openDrawerInSearchMode)
            .putString(KEY_CHARGE_IDLE_EFFECT, chargeIdleEffect.name)
            .apply()
    }

    private fun readStoredFontStyle(): PixelFontStyle {
        val storedValue = sharedPreferences.getString(KEY_FONT_STYLE, null)
        return PixelFontStyle.entries.firstOrNull { it.name == storedValue }
            ?: PixelFontCatalog.defaultFontStyle
    }

    private fun readStoredPixelShape(): PixelShape? {
        val storedValue = sharedPreferences.getString(KEY_PIXEL_SHAPE, null) ?: return null
        return PixelShape.entries.firstOrNull { it.name == storedValue }
    }

    private fun readStoredDotSizePx(): Int {
        val storedValue = sharedPreferences.getInt(KEY_DOT_SIZE_PX, Int.MIN_VALUE)
        if (storedValue == Int.MIN_VALUE) {
            return ScreenProfileFactory.defaultDotSizePx
        }
        return ScreenProfileFactory.supportedDotSizePxOptions.firstOrNull { it == storedValue }
            ?: ScreenProfileFactory.defaultDotSizePx
    }

    private fun readStoredTheme(): PixelTheme {
        val storedValue = sharedPreferences.getString(KEY_THEME, null)
        return PixelTheme.entries.firstOrNull { it.name == storedValue } ?: PixelTheme.DAY
    }

    private fun readStoredPixelGapEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_PIXEL_GAP_ENABLED, true)
    }

    private fun readStoredPixelGapRatio(): Float {
        return sharedPreferences.getFloat(KEY_PIXEL_GAP_RATIO, 1f).coerceIn(0f, 1f)
    }

    private fun readStoredDrawerListAlignment(): DrawerListAlignment {
        val storedValue = sharedPreferences.getString(KEY_DRAWER_LIST_ALIGNMENT, null)
        return DrawerListAlignment.entries.firstOrNull { it.name == storedValue } ?: DrawerListAlignment.LEFT
    }

    private fun readStoredChargeIdleEffect(): ChargeIdleEffect {
        val storedValue = sharedPreferences.getString(KEY_CHARGE_IDLE_EFFECT, null)
        return ChargeIdleEffect.entries.firstOrNull { it.name == storedValue } ?: ChargeIdleEffect.FLUID
    }

    private companion object {
        const val PREFERENCES_NAME = "pixel_launcher_prefs"
        const val KEY_FONT_STYLE = "selected_font_style"
        const val KEY_PIXEL_SHAPE = "selected_pixel_shape"
        const val KEY_DOT_SIZE_PX = "selected_dot_size_px"
        const val KEY_PIXEL_GAP_ENABLED = "pixel_gap_enabled"
        const val KEY_PIXEL_GAP_RATIO = "pixel_gap_ratio"
        const val KEY_THEME = "selected_theme"
        const val KEY_DRAWER_LIST_ALIGNMENT = "drawer_list_alignment"
        const val KEY_IDLE_PAGE_ENABLED = "idle_page_enabled"
        const val KEY_OPEN_DRAWER_IN_SEARCH_MODE = "open_drawer_in_search_mode"
        const val KEY_CHARGE_IDLE_EFFECT = "charge_idle_effect"
    }
}

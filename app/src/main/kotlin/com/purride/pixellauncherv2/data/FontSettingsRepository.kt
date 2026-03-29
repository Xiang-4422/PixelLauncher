package com.purride.pixellauncherv2.data

import android.content.Context
import com.purride.pixellauncherv2.launcher.DrawerListAlignment
import com.purride.pixellauncherv2.render.ChargeIdleEffect
import com.purride.pixellauncherv2.render.PixelFontCatalog
import com.purride.pixellauncherv2.render.PixelFontSize
import com.purride.pixellauncherv2.render.PixelFontStyle
import com.purride.pixellauncherv2.render.PixelShape
import com.purride.pixellauncherv2.render.PixelTheme
import com.purride.pixellauncherv2.render.ScreenProfileFactory

class FontSettingsRepository(
    context: Context,
) {

    private val sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    data class AppearanceSettings(
        val fontSize: PixelFontSize,
        val fontStyle: PixelFontStyle,
        val pixelShape: PixelShape,
        val dotSizePx: Int,
        val theme: PixelTheme,
    )

    data class UiBehaviorSettings(
        val drawerListAlignment: DrawerListAlignment,
        val isIdlePageEnabled: Boolean,
        val openDrawerInSearchMode: Boolean,
        val chargeIdleEffect: ChargeIdleEffect,
    )

    fun getAppearanceSettings(): AppearanceSettings {
        val storedFontSize = readStoredFontSize()
        val storedFontStyle = readStoredFontStyle()
        return AppearanceSettings(
            fontSize = storedFontSize,
            fontStyle = storedFontStyle,
            pixelShape = readStoredPixelShape() ?: PixelShape.SQUARE,
            dotSizePx = readStoredDotSizePx(),
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
        fontSize: PixelFontSize,
        fontStyle: PixelFontStyle,
        pixelShape: PixelShape,
        dotSizePx: Int,
        theme: PixelTheme,
    ) {
        val safeDotSizePx = ScreenProfileFactory.supportedDotSizePxOptions.firstOrNull { it == dotSizePx }
            ?: ScreenProfileFactory.defaultDotSizePx
        sharedPreferences.edit()
            .putString(KEY_FONT_SIZE, fontSize.name)
            .putString(KEY_FONT_STYLE, fontStyle.name)
            .putString(KEY_PIXEL_SHAPE, pixelShape.name)
            .putInt(KEY_DOT_SIZE_PX, safeDotSizePx)
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

    private fun readStoredFontSize(): PixelFontSize {
        val storedValue = sharedPreferences.getString(KEY_FONT_SIZE, null)
        return PixelFontSize.entries.firstOrNull { it.name == storedValue }
            ?: PixelFontCatalog.defaultFontSize
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
        return PixelTheme.entries.firstOrNull { it.name == storedValue } ?: PixelTheme.GREEN_PHOSPHOR
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
        const val KEY_FONT_SIZE = "selected_font_size"
        const val KEY_FONT_STYLE = "selected_font_style"
        const val KEY_PIXEL_SHAPE = "selected_pixel_shape"
        const val KEY_DOT_SIZE_PX = "selected_dot_size_px"
        const val KEY_THEME = "selected_theme"
        const val KEY_DRAWER_LIST_ALIGNMENT = "drawer_list_alignment"
        const val KEY_IDLE_PAGE_ENABLED = "idle_page_enabled"
        const val KEY_OPEN_DRAWER_IN_SEARCH_MODE = "open_drawer_in_search_mode"
        const val KEY_CHARGE_IDLE_EFFECT = "charge_idle_effect"
    }
}

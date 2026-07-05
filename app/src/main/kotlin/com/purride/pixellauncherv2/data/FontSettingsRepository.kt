package com.purride.pixellauncherv2.data

import android.content.Context
import com.purride.pixellauncherv2.launcher.DrawerListAlignment
import com.purride.pixellauncherv2.launcher.ChargeIdleEffect
import com.purride.pixellauncherv2.launcher.IdleSettings
import com.purride.pixellauncherv2.render.PixelShape
import com.purride.pixellauncherv2.launcher.PixelTheme
import com.purride.pixellauncherv2.render.ScreenProfileFactory

class FontSettingsRepository(
    context: Context,
) {

    private val sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    data class AppearanceSettings(
        val pixelShape: PixelShape,
        val dotSizePx: Int,
        val pixelGapEnabled: Boolean,
        val theme: PixelTheme,
    )

    data class UiBehaviorSettings(
        val drawerListAlignment: DrawerListAlignment,
        val isIdlePageEnabled: Boolean,
        val chargeAutoIdleEnabled: Boolean,
        val inactivityAutoIdleEnabled: Boolean,
        val idleTimeoutSeconds: Int,
        val openDrawerInSearchMode: Boolean,
        val chargeIdleEffect: ChargeIdleEffect,
        val pixelDustEasterEggEnabled: Boolean,
    )

    fun getAppearanceSettings(): AppearanceSettings {
        val dotSizePx = readStoredDotSizePx()
        return AppearanceSettings(
            pixelShape = readStoredPixelShape() ?: PixelShape.SQUARE,
            dotSizePx = dotSizePx,
            pixelGapEnabled = readStoredPixelGapEnabled(),
            theme = readStoredTheme(),
        )
    }

    fun getUiBehaviorSettings(): UiBehaviorSettings {
        return UiBehaviorSettings(
            drawerListAlignment = readStoredDrawerListAlignment(),
            isIdlePageEnabled = sharedPreferences.getBoolean(KEY_IDLE_PAGE_ENABLED, false),
            chargeAutoIdleEnabled = sharedPreferences.getBoolean(KEY_CHARGE_AUTO_IDLE_ENABLED, false),
            inactivityAutoIdleEnabled = sharedPreferences.getBoolean(KEY_INACTIVITY_AUTO_IDLE_ENABLED, true),
            idleTimeoutSeconds = readStoredIdleTimeoutSeconds(),
            openDrawerInSearchMode = sharedPreferences.getBoolean(KEY_OPEN_DRAWER_IN_SEARCH_MODE, false),
            chargeIdleEffect = readStoredChargeIdleEffect(),
            pixelDustEasterEggEnabled = sharedPreferences.getBoolean(KEY_PIXEL_DUST_EASTER_EGG_ENABLED, true),
        )
    }

    fun setAppearanceSettings(
        pixelShape: PixelShape,
        dotSizePx: Int,
        pixelGapEnabled: Boolean,
        theme: PixelTheme,
    ) {
        val safeDotSizePx = dotSizePx.coerceAtLeast(1)
        sharedPreferences.edit()
            .putString(KEY_PIXEL_SHAPE, pixelShape.name)
            .putInt(KEY_DOT_SIZE_PX, safeDotSizePx)
            .putBoolean(KEY_PIXEL_GAP_ENABLED, pixelGapEnabled)
            .putString(KEY_THEME, theme.name)
            .apply()
    }

    fun setUiBehaviorSettings(
        drawerListAlignment: DrawerListAlignment,
        isIdlePageEnabled: Boolean,
        chargeAutoIdleEnabled: Boolean,
        inactivityAutoIdleEnabled: Boolean,
        idleTimeoutSeconds: Int,
        openDrawerInSearchMode: Boolean,
        chargeIdleEffect: ChargeIdleEffect,
        pixelDustEasterEggEnabled: Boolean,
    ) {
        sharedPreferences.edit()
            .putString(KEY_DRAWER_LIST_ALIGNMENT, drawerListAlignment.name)
            .putBoolean(KEY_IDLE_PAGE_ENABLED, isIdlePageEnabled)
            .putBoolean(KEY_CHARGE_AUTO_IDLE_ENABLED, chargeAutoIdleEnabled)
            .putBoolean(KEY_INACTIVITY_AUTO_IDLE_ENABLED, inactivityAutoIdleEnabled)
            .putInt(KEY_IDLE_TIMEOUT_SECONDS, IdleSettings.normalizeTimeoutSeconds(idleTimeoutSeconds))
            .putBoolean(KEY_OPEN_DRAWER_IN_SEARCH_MODE, openDrawerInSearchMode)
            .putString(KEY_CHARGE_IDLE_EFFECT, chargeIdleEffect.name)
            .putBoolean(KEY_PIXEL_DUST_EASTER_EGG_ENABLED, pixelDustEasterEggEnabled)
            .apply()
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
        return storedValue.coerceAtLeast(1)
    }

    private fun readStoredTheme(): PixelTheme {
        val storedValue = sharedPreferences.getString(KEY_THEME, null)
        return PixelTheme.entries.firstOrNull { it.name == storedValue } ?: PixelTheme.DAY
    }

    private fun readStoredPixelGapEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_PIXEL_GAP_ENABLED, true)
    }

    private fun readStoredDrawerListAlignment(): DrawerListAlignment {
        val storedValue = sharedPreferences.getString(KEY_DRAWER_LIST_ALIGNMENT, null)
        return DrawerListAlignment.entries.firstOrNull { it.name == storedValue } ?: DrawerListAlignment.LEFT
    }

    private fun readStoredChargeIdleEffect(): ChargeIdleEffect {
        val storedValue = sharedPreferences.getString(KEY_CHARGE_IDLE_EFFECT, null)
        return ChargeIdleEffect.entries.firstOrNull { it.name == storedValue } ?: ChargeIdleEffect.FLUID
    }

    private fun readStoredIdleTimeoutSeconds(): Int {
        val storedValue = sharedPreferences.getInt(
            KEY_IDLE_TIMEOUT_SECONDS,
            IdleSettings.DEFAULT_TIMEOUT_SECONDS,
        )
        return IdleSettings.normalizeTimeoutSeconds(storedValue)
    }

    private companion object {
        const val PREFERENCES_NAME = "pixel_launcher_prefs"
        const val KEY_PIXEL_SHAPE = "selected_pixel_shape"
        const val KEY_DOT_SIZE_PX = "selected_dot_size_px"
        const val KEY_PIXEL_GAP_ENABLED = "pixel_gap_enabled"
        const val KEY_THEME = "selected_theme"
        const val KEY_DRAWER_LIST_ALIGNMENT = "drawer_list_alignment"
        const val KEY_IDLE_PAGE_ENABLED = "idle_page_enabled"
        const val KEY_CHARGE_AUTO_IDLE_ENABLED = "charge_auto_idle_enabled"
        const val KEY_INACTIVITY_AUTO_IDLE_ENABLED = "inactivity_auto_idle_enabled"
        const val KEY_IDLE_TIMEOUT_SECONDS = "idle_timeout_seconds"
        const val KEY_OPEN_DRAWER_IN_SEARCH_MODE = "open_drawer_in_search_mode"
        const val KEY_CHARGE_IDLE_EFFECT = "charge_idle_effect"
        const val KEY_PIXEL_DUST_EASTER_EGG_ENABLED = "pixel_dust_easter_egg_enabled"
    }
}

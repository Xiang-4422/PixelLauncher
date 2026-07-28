package com.purride.pixellauncherv2.data

import android.content.Context
import com.purride.pixellauncherv2.launcher.ChargeIdleEffect
import com.purride.pixellauncherv2.launcher.DrawerListAlignment
import com.purride.pixellauncherv2.launcher.IdleSettings
import com.purride.pixellauncherv2.launcher.LauncherFontFamily
import com.purride.pixellauncherv2.launcher.LauncherFontSelection
import com.purride.pixellauncherv2.launcher.LauncherFontWidthMode
import com.purride.pixellauncherv2.launcher.PixelFontCatalog
import com.purride.pixellauncherv2.launcher.PixelFontSize
import com.purride.pixellauncherv2.launcher.PixelMatterEffectMode
import com.purride.pixellauncherv2.launcher.PixelTheme
import com.purride.pixelcore.PixelShape
import com.purride.pixellauncherv2.layout.LauncherLayoutProfileFactory

class FontSettingsRepository(
    context: Context,
) {

    private val sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    data class AppearanceSettings(
        val pixelShape: PixelShape,
        val dotSizePx: Int,
        val pixelGapEnabled: Boolean,
        val theme: PixelTheme,
        /** 用户在设置页明确选择的字体家族、宽度模式和字号。 */
        val fontSelection: LauncherFontSelection,
    )

    data class UiBehaviorSettings(
        val drawerListAlignment: DrawerListAlignment,
        val isIdlePageEnabled: Boolean,
        val chargeAutoIdleEnabled: Boolean,
        val inactivityAutoIdleEnabled: Boolean,
        val idleTimeoutSeconds: Int,
        val openDrawerInSearchMode: Boolean,
        val chargeIdleEffect: ChargeIdleEffect,
        val pixelMatterEffectEnabled: Boolean,
        val pixelMatterEffectMode: PixelMatterEffectMode,
        val pixelMatterHandControlEnabled: Boolean,
        val pixelMatterHandDebugEnabled: Boolean,
    )

    fun getAppearanceSettings(): AppearanceSettings {
        val dotSizePx = readStoredDotSizePx()
        return AppearanceSettings(
            pixelShape = readStoredPixelShape() ?: PixelShape.SQUARE,
            dotSizePx = dotSizePx,
            pixelGapEnabled = readStoredPixelGapEnabled(),
            theme = readStoredTheme(),
            fontSelection = readStoredFontSelection(),
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
            pixelMatterEffectEnabled = readStoredPixelMatterEffectEnabled(),
            pixelMatterEffectMode = readStoredPixelMatterEffectMode(),
            pixelMatterHandControlEnabled = sharedPreferences.getBoolean(KEY_PIXEL_MATTER_HAND_CONTROL_ENABLED, false),
            pixelMatterHandDebugEnabled = sharedPreferences.getBoolean(KEY_PIXEL_MATTER_HAND_DEBUG_ENABLED, true),
        )
    }

    fun setAppearanceSettings(
        pixelShape: PixelShape,
        dotSizePx: Int,
        pixelGapEnabled: Boolean,
        theme: PixelTheme,
        fontSelection: LauncherFontSelection,
    ) {
        val safeDotSizePx = dotSizePx.coerceAtLeast(1)
        /** 防止外部调用把不存在的字体组合持久化。 */
        val normalizedFontSelection = PixelFontCatalog.normalize(fontSelection)
        sharedPreferences.edit()
            .putString(KEY_PIXEL_SHAPE, pixelShape.name)
            .putInt(KEY_DOT_SIZE_PX, safeDotSizePx)
            .putBoolean(KEY_PIXEL_GAP_ENABLED, pixelGapEnabled)
            .putString(KEY_THEME, theme.name)
            .putString(KEY_FONT_FAMILY, normalizedFontSelection.family.name)
            .putString(KEY_FONT_WIDTH_MODE, normalizedFontSelection.widthMode.name)
            .putString(KEY_FONT_SIZE, normalizedFontSelection.size.name)
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
        pixelMatterEffectEnabled: Boolean,
        pixelMatterEffectMode: PixelMatterEffectMode,
        pixelMatterHandControlEnabled: Boolean,
        pixelMatterHandDebugEnabled: Boolean,
    ) {
        sharedPreferences.edit()
            .putString(KEY_DRAWER_LIST_ALIGNMENT, drawerListAlignment.name)
            .putBoolean(KEY_IDLE_PAGE_ENABLED, isIdlePageEnabled)
            .putBoolean(KEY_CHARGE_AUTO_IDLE_ENABLED, chargeAutoIdleEnabled)
            .putBoolean(KEY_INACTIVITY_AUTO_IDLE_ENABLED, inactivityAutoIdleEnabled)
            .putInt(KEY_IDLE_TIMEOUT_SECONDS, IdleSettings.normalizeTimeoutSeconds(idleTimeoutSeconds))
            .putBoolean(KEY_OPEN_DRAWER_IN_SEARCH_MODE, openDrawerInSearchMode)
            .putString(KEY_CHARGE_IDLE_EFFECT, chargeIdleEffect.name)
            .putBoolean(KEY_PIXEL_MATTER_EFFECT_ENABLED, pixelMatterEffectEnabled)
            .putBoolean(KEY_PIXEL_DUST_EASTER_EGG_ENABLED, pixelMatterEffectEnabled)
            .putString(KEY_PIXEL_MATTER_EFFECT_MODE, pixelMatterEffectMode.name)
            .putBoolean(KEY_PIXEL_MATTER_HAND_CONTROL_ENABLED, pixelMatterHandControlEnabled)
            .putBoolean(KEY_PIXEL_MATTER_HAND_DEBUG_ENABLED, pixelMatterHandDebugEnabled)
            .apply()
    }

    private fun readStoredPixelShape(): PixelShape? {
        val storedValue = sharedPreferences.getString(KEY_PIXEL_SHAPE, null) ?: return null
        return PixelShape.entries.firstOrNull { it.name == storedValue }
    }

    private fun readStoredDotSizePx(): Int {
        val storedValue = sharedPreferences.getInt(KEY_DOT_SIZE_PX, Int.MIN_VALUE)
        if (storedValue == Int.MIN_VALUE) {
            return LauncherLayoutProfileFactory.defaultDotSizePx
        }
        return storedValue.coerceAtLeast(1)
    }

    private fun readStoredTheme(): PixelTheme {
        val storedValue = sharedPreferences.getString(KEY_THEME, null)
        return PixelTheme.entries.firstOrNull { it.name == storedValue } ?: PixelTheme.DAY
    }

    /** 读取完整字体选择，并兼容早期把 Fusion 宽度模式编码进家族名的设置。 */
    private fun readStoredFontSelection(): LauncherFontSelection {
        /** 旧版或新版保存的字体家族原始名称。 */
        val storedFamily = sharedPreferences.getString(KEY_FONT_FAMILY, null)
        /** 从旧版家族名称中恢复出的宽度模式。 */
        val legacyWidthMode = when (storedFamily) {
            LEGACY_FUSION_MONOSPACED -> LauncherFontWidthMode.MONOSPACED
            LEGACY_FUSION_PROPORTIONAL -> LauncherFontWidthMode.PROPORTIONAL
            else -> null
        }
        /** 新版家族值；旧版 Fusion 名称统一迁移到 FUSION。 */
        val family = LauncherFontFamily.entries.firstOrNull { family -> family.name == storedFamily }
            ?: if (legacyWidthMode != null) LauncherFontFamily.FUSION else PixelFontCatalog.defaultUiFontSelection.family
        /** 新版宽度模式，无值时优先采用旧版迁移结果。 */
        val widthMode = sharedPreferences.getString(KEY_FONT_WIDTH_MODE, null)
            ?.let { stored -> LauncherFontWidthMode.entries.firstOrNull { mode -> mode.name == stored } }
            ?: legacyWidthMode
            ?: PixelFontCatalog.defaultUiFontSelection.widthMode
        /** 新版默认字号，无值或非法值时采用应用默认字号。 */
        val size = sharedPreferences.getString(KEY_FONT_SIZE, null)
            ?.let { stored -> PixelFontSize.entries.firstOrNull { candidate -> candidate.name == stored } }
            ?: PixelFontCatalog.defaultUiFontSelection.size
        return PixelFontCatalog.normalize(
            LauncherFontSelection(family = family, widthMode = widthMode, size = size),
        )
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

    private fun readStoredPixelMatterEffectEnabled(): Boolean {
        if (sharedPreferences.contains(KEY_PIXEL_MATTER_EFFECT_ENABLED)) {
            return sharedPreferences.getBoolean(KEY_PIXEL_MATTER_EFFECT_ENABLED, true)
        }
        return sharedPreferences.getBoolean(KEY_PIXEL_DUST_EASTER_EGG_ENABLED, true)
    }

    private fun readStoredPixelMatterEffectMode(): PixelMatterEffectMode {
        val storedValue = sharedPreferences.getString(KEY_PIXEL_MATTER_EFFECT_MODE, null)
        return PixelMatterEffectMode.entries.firstOrNull { it.name == storedValue }
            ?: PixelMatterEffectMode.SAND
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
        /** 字体家族对应的 SharedPreferences 键。 */
        const val KEY_FONT_FAMILY = "selected_font_family"
        /** 字体宽度模式对应的 SharedPreferences 键。 */
        const val KEY_FONT_WIDTH_MODE = "selected_font_width_mode"
        /** 默认字体字号对应的 SharedPreferences 键。 */
        const val KEY_FONT_SIZE = "selected_font_size"
        /** 第一版设置中代表 Fusion 比例模式的旧家族名称。 */
        const val LEGACY_FUSION_PROPORTIONAL = "FUSION_PROPORTIONAL"
        /** 第一版设置中代表 Fusion 等宽模式的旧家族名称。 */
        const val LEGACY_FUSION_MONOSPACED = "FUSION_MONOSPACED"
        const val KEY_DRAWER_LIST_ALIGNMENT = "drawer_list_alignment"
        const val KEY_IDLE_PAGE_ENABLED = "idle_page_enabled"
        const val KEY_CHARGE_AUTO_IDLE_ENABLED = "charge_auto_idle_enabled"
        const val KEY_INACTIVITY_AUTO_IDLE_ENABLED = "inactivity_auto_idle_enabled"
        const val KEY_IDLE_TIMEOUT_SECONDS = "idle_timeout_seconds"
        const val KEY_OPEN_DRAWER_IN_SEARCH_MODE = "open_drawer_in_search_mode"
        const val KEY_CHARGE_IDLE_EFFECT = "charge_idle_effect"
        const val KEY_PIXEL_DUST_EASTER_EGG_ENABLED = "pixel_dust_easter_egg_enabled"
        const val KEY_PIXEL_MATTER_EFFECT_ENABLED = "pixel_matter_effect_enabled"
        const val KEY_PIXEL_MATTER_EFFECT_MODE = "pixel_matter_effect_mode"
        const val KEY_PIXEL_MATTER_HAND_CONTROL_ENABLED = "pixel_matter_hand_control_enabled"
        const val KEY_PIXEL_MATTER_HAND_DEBUG_ENABLED = "pixel_matter_hand_debug_enabled"
    }
}

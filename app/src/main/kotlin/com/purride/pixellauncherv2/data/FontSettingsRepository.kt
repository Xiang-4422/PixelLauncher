package com.purride.pixellauncherv2.data

import android.content.Context
import com.purride.pixellauncherv2.launcher.ChargeIdleEffect
import com.purride.pixellauncherv2.launcher.DrawerListAlignment
import com.purride.pixellauncherv2.launcher.IdleSettings
import com.purride.pixellauncherv2.launcher.LauncherFontFamily
import com.purride.pixellauncherv2.launcher.LauncherFontSelection
import com.purride.pixellauncherv2.launcher.LauncherFontWidthMode
import com.purride.pixellauncherv2.launcher.LauncherThemeFamily
import com.purride.pixellauncherv2.launcher.LauncherThemeMode
import com.purride.pixellauncherv2.launcher.PixelFontCatalog
import com.purride.pixellauncherv2.launcher.PixelFontSize
import com.purride.pixellauncherv2.launcher.PixelMatterEffectMode
import com.purride.pixelcore.PixelShape
import com.purride.pixellauncherv2.layout.LauncherLayoutProfileFactory
import org.json.JSONObject

/** 持久化外观、字体历史和 UI 行为设置。 */
class FontSettingsRepository(
    /** Launcher 私有设置与只读外观发布共同使用的应用上下文。 */
    private val context: Context,
    /**
     * 手势调试帧偏好是否允许生效；Release 构建必须传 false——读取时强制归一化为
     * 关闭且默认关闭，防止无开关可关的调试相机画面（隐私门禁的状态层）。
     */
    private val handDebugSettingAllowed: Boolean = true,
) {

    private val sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    data class AppearanceSettings(
        val pixelShape: PixelShape,
        val dotSizePx: Int,
        val pixelGapEnabled: Boolean,
        /** 用户选择的主题家族。 */
        val themeFamily: LauncherThemeFamily,
        /** 当前主题家族内部采用的亮暗模式。 */
        val themeMode: LauncherThemeMode,
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
            themeFamily = readStoredThemeFamily(),
            themeMode = readStoredThemeMode(),
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
            pixelMatterHandDebugEnabled = handDebugSettingAllowed &&
                sharedPreferences.getBoolean(KEY_PIXEL_MATTER_HAND_DEBUG_ENABLED, handDebugSettingAllowed),
        )
    }

    fun setAppearanceSettings(
        pixelShape: PixelShape,
        dotSizePx: Int,
        pixelGapEnabled: Boolean,
        themeFamily: LauncherThemeFamily,
        themeMode: LauncherThemeMode,
        fontSelection: LauncherFontSelection,
    ) {
        val safeDotSizePx = dotSizePx.coerceAtLeast(1)
        /** 防止外部调用把不存在的字体组合持久化。 */
        val normalizedFontSelection = PixelFontCatalog.normalize(fontSelection)
        val fontStateJson = updatedFontStateJson(normalizedFontSelection)
        sharedPreferences.edit()
            .putString(KEY_PIXEL_SHAPE, pixelShape.name)
            .putInt(KEY_DOT_SIZE_PX, safeDotSizePx)
            .putBoolean(KEY_PIXEL_GAP_ENABLED, pixelGapEnabled)
            .putString(KEY_THEME_FAMILY, themeFamily.name)
            .putString(KEY_THEME_MODE, themeMode.name)
            .putString(KEY_FONT_STATE_JSON, fontStateJson)
            .apply()
        ProductAppearanceExchange.publish(
            context = context,
            appearance = ProductAppearanceExchange.from(
                AppearanceSettings(
                    pixelShape = pixelShape,
                    dotSizePx = safeDotSizePx,
                    pixelGapEnabled = pixelGapEnabled,
                    themeFamily = themeFamily,
                    themeMode = themeMode,
                    fontSelection = normalizedFontSelection,
                ),
            ),
        )
    }

    /** 切回字体家族时恢复该家族上次成功的宽度和字号。 */
    fun selectionForFamily(family: LauncherFontFamily): LauncherFontSelection {
        val state = readFontStateJson() ?: return defaultSelectionForFamily(family)
        val familyState = state.optJSONObject(JSON_FAMILIES)?.optJSONObject(family.id)
            ?: return defaultSelectionForFamily(family)
        val width = widthModeFromId(familyState.optString(JSON_LAST_WIDTH))
            ?: return defaultSelectionForFamily(family)
        return selectionForWidth(family, width, state)
    }

    /** 切回宽度模式时恢复该 family/width 上次成功的字号。 */
    fun selectionForWidth(
        family: LauncherFontFamily,
        widthMode: LauncherFontWidthMode,
    ): LauncherFontSelection = selectionForWidth(family, widthMode, readFontStateJson())

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

    /** 读取主题家族；缺失或无效值使用 Midnight。 */
    private fun readStoredThemeFamily(): LauncherThemeFamily {
        val storedValue = sharedPreferences.getString(KEY_THEME_FAMILY, null)
        return LauncherThemeFamily.entries.firstOrNull { it.name == storedValue }
            ?: LauncherThemeFamily.MIDNIGHT
    }

    /** 读取亮暗模式；缺失或无效值使用 Night。 */
    private fun readStoredThemeMode(): LauncherThemeMode {
        val storedValue = sharedPreferences.getString(KEY_THEME_MODE, null)
        return LauncherThemeMode.entries.firstOrNull { it.name == storedValue } ?: LauncherThemeMode.NIGHT
    }

    /** 读取当前开发版字体状态；缺失、损坏或过期数据直接使用 catalog 默认值。 */
    private fun readStoredFontSelection(): LauncherFontSelection {
        readFontStateJson()?.let { state ->
            val familyId = state.optString(JSON_CURRENT_FAMILY)
            val family = LauncherFontFamily.entries.firstOrNull { candidate -> candidate.id == familyId }
            if (family != null) return selectionForFamily(family)
        }
        return PixelFontCatalog.defaultUiFontSelection
    }

    /** 返回合法 JSON 字体历史；损坏或未知版本视为无历史。 */
    private fun readFontStateJson(): JSONObject? {
        val raw = sharedPreferences.getString(KEY_FONT_STATE_JSON, null) ?: return null
        return runCatching { JSONObject(raw) }
            .getOrNull()
            ?.takeIf { state -> state.optInt(JSON_SCHEMA_VERSION) == FONT_STATE_SCHEMA_VERSION }
    }

    /** 把一次成功选择合并进版本化 family/width 历史。 */
    private fun updatedFontStateJson(selection: LauncherFontSelection): String {
        val root = readFontStateJson() ?: JSONObject().apply {
            put(JSON_SCHEMA_VERSION, FONT_STATE_SCHEMA_VERSION)
            put(JSON_FAMILIES, JSONObject())
        }
        root.put(JSON_CURRENT_FAMILY, selection.family.id)
        val families = root.optJSONObject(JSON_FAMILIES) ?: JSONObject().also { root.put(JSON_FAMILIES, it) }
        val familyState = families.optJSONObject(selection.family.id) ?: JSONObject().also {
            families.put(selection.family.id, it)
        }
        familyState.put(JSON_LAST_WIDTH, selection.widthMode.assetStyleName)
        val sizes = familyState.optJSONObject(JSON_SIZES) ?: JSONObject().also { familyState.put(JSON_SIZES, it) }
        sizes.put(selection.widthMode.assetStyleName, selection.size.px)
        return root.toString()
    }

    /** 从指定 JSON 恢复 width 历史，无效时使用 catalog 的该 width 默认字号。 */
    private fun selectionForWidth(
        family: LauncherFontFamily,
        widthMode: LauncherFontWidthMode,
        state: JSONObject?,
    ): LauncherFontSelection {
        val storedSize = state?.optJSONObject(JSON_FAMILIES)
            ?.optJSONObject(family.id)
            ?.optJSONObject(JSON_SIZES)
            ?.optInt(widthMode.assetStyleName, -1)
            ?: -1
        if (storedSize > 0) {
            val candidate = LauncherFontSelection(family, widthMode, PixelFontSize(storedSize))
            if (PixelFontCatalog.supports(candidate)) return candidate
        }
        val familyDefault = defaultSelectionForFamily(family)
        if (familyDefault.widthMode == widthMode) return familyDefault
        val size = PixelFontCatalog.fontSizeOptions(family, widthMode).firstOrNull()
            ?: return familyDefault
        return LauncherFontSelection(family, widthMode, size)
    }

    /** 返回 catalog 声明的家族默认设置 face。 */
    private fun defaultSelectionForFamily(family: LauncherFontFamily): LauncherFontSelection {
        val descriptor = PixelFontCatalog.familyDescriptor(family)
            ?: return PixelFontCatalog.defaultUiFontSelection
        return LauncherFontSelection(
            family = descriptor.defaultKey.family,
            widthMode = descriptor.defaultKey.widthMode,
            size = descriptor.defaultKey.size,
        )
    }

    /** 把稳定宽度 ID 恢复为枚举。 */
    private fun widthModeFromId(id: String): LauncherFontWidthMode? =
        LauncherFontWidthMode.entries.firstOrNull { mode -> mode.assetStyleName == id }

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
        /** 主题家族独立持久化键。 */
        const val KEY_THEME_FAMILY = "selected_theme_family"
        /** 亮暗模式独立持久化键。 */
        const val KEY_THEME_MODE = "selected_theme_mode"
        /** 当前版本化字体选择和 family/width 历史。 */
        const val KEY_FONT_STATE_JSON = "font_state_json_v3"
        const val FONT_STATE_SCHEMA_VERSION = 3
        const val JSON_SCHEMA_VERSION = "schemaVersion"
        const val JSON_CURRENT_FAMILY = "currentFamilyId"
        const val JSON_FAMILIES = "families"
        const val JSON_LAST_WIDTH = "lastWidth"
        const val JSON_SIZES = "sizes"
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

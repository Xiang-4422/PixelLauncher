package com.purride.pixellauncherv2.data

import android.content.Context
import com.purride.pixellauncherv2.launcher.DrawerListAlignment
import com.purride.pixellauncherv2.render.PixelFontCatalog
import com.purride.pixellauncherv2.render.PixelFontId
import com.purride.pixellauncherv2.render.PixelShape
import com.purride.pixellauncherv2.render.PixelTheme
import com.purride.pixellauncherv2.render.ScreenProfileFactory

class FontSettingsRepository(
    context: Context,
) {

    private val sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    data class AppearanceSettings(
        val fontId: PixelFontId,
        val pixelShape: PixelShape,
        val dotSizePx: Int,
        val theme: PixelTheme,
    )

    data class UiBehaviorSettings(
        val drawerListAlignment: DrawerListAlignment,
        val isIdlePageEnabled: Boolean,
        val openDrawerInSearchMode: Boolean,
    )

    fun getAppearanceSettings(): AppearanceSettings {
        val storedFontId = readStoredFontId()
        val storedPixelShape = readStoredPixelShape()
        val storedDotSizePx = readStoredDotSizePx()
        val storedTheme = readStoredTheme()

        return when (storedFontId) {
            PixelFontId.DOTTED_SONGTI_SQUARE -> AppearanceSettings(
                fontId = PixelFontId.WEN_QUAN_YI_BITMAP_SONG_16PX,
                pixelShape = storedPixelShape ?: PixelShape.SQUARE,
                dotSizePx = storedDotSizePx,
                theme = storedTheme,
            )

            PixelFontId.DOTTED_SONGTI_CIRCLE -> AppearanceSettings(
                fontId = PixelFontId.WEN_QUAN_YI_BITMAP_SONG_16PX,
                pixelShape = storedPixelShape ?: PixelShape.CIRCLE,
                dotSizePx = storedDotSizePx,
                theme = storedTheme,
            )

            PixelFontId.DOTTED_SONGTI_DIAMOND -> AppearanceSettings(
                fontId = PixelFontId.WEN_QUAN_YI_BITMAP_SONG_16PX,
                pixelShape = storedPixelShape ?: PixelShape.DIAMOND,
                dotSizePx = storedDotSizePx,
                theme = storedTheme,
            )

            else -> AppearanceSettings(
                fontId = storedFontId,
                pixelShape = storedPixelShape ?: PixelFontCatalog.definition(storedFontId).pixelShape,
                dotSizePx = storedDotSizePx,
                theme = storedTheme,
            )
        }
    }

    fun getSelectedFontId(): PixelFontId {
        return getAppearanceSettings().fontId
    }

    fun setSelectedFontId(fontId: PixelFontId) {
        sharedPreferences.edit()
            .putString(KEY_FONT_ID, fontId.name)
            .apply()
    }

    fun getSelectedPixelShape(): PixelShape {
        return getAppearanceSettings().pixelShape
    }

    fun getSelectedDotSizePx(): Int {
        return getAppearanceSettings().dotSizePx
    }

    fun getSelectedTheme(): PixelTheme {
        return getAppearanceSettings().theme
    }

    fun getUiBehaviorSettings(): UiBehaviorSettings {
        return UiBehaviorSettings(
            drawerListAlignment = readStoredDrawerListAlignment(),
            isIdlePageEnabled = sharedPreferences.getBoolean(KEY_IDLE_PAGE_ENABLED, true),
            openDrawerInSearchMode = sharedPreferences.getBoolean(KEY_OPEN_DRAWER_IN_SEARCH_MODE, false),
        )
    }

    fun setAppearanceSettings(fontId: PixelFontId, pixelShape: PixelShape, dotSizePx: Int, theme: PixelTheme) {
        val safeDotSizePx = ScreenProfileFactory.supportedDotSizePxOptions.firstOrNull { it == dotSizePx }
            ?: ScreenProfileFactory.defaultDotSizePx
        sharedPreferences.edit()
            .putString(KEY_FONT_ID, fontId.name)
            .putString(KEY_PIXEL_SHAPE, pixelShape.name)
            .putInt(KEY_DOT_SIZE_PX, safeDotSizePx)
            .putString(KEY_THEME, theme.name)
            .apply()
    }

    fun setUiBehaviorSettings(
        drawerListAlignment: DrawerListAlignment,
        isIdlePageEnabled: Boolean,
        openDrawerInSearchMode: Boolean,
    ) {
        sharedPreferences.edit()
            .putString(KEY_DRAWER_LIST_ALIGNMENT, drawerListAlignment.name)
            .putBoolean(KEY_IDLE_PAGE_ENABLED, isIdlePageEnabled)
            .putBoolean(KEY_OPEN_DRAWER_IN_SEARCH_MODE, openDrawerInSearchMode)
            .apply()
    }

    private fun readStoredFontId(): PixelFontId {
        val storedValue = sharedPreferences.getString(KEY_FONT_ID, null) ?: return PixelFontCatalog.defaultFontId
        val storedFontId = PixelFontId.entries.firstOrNull { it.name == storedValue } ?: return PixelFontCatalog.defaultFontId
        return if (storedFontId == LEGACY_DEFAULT_FONT_ID) PixelFontCatalog.defaultFontId else storedFontId
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

    private companion object {
        const val PREFERENCES_NAME = "pixel_launcher_prefs"
        const val KEY_FONT_ID = "selected_font_id"
        const val KEY_PIXEL_SHAPE = "selected_pixel_shape"
        const val KEY_DOT_SIZE_PX = "selected_dot_size_px"
        const val KEY_THEME = "selected_theme"
        const val KEY_DRAWER_LIST_ALIGNMENT = "drawer_list_alignment"
        const val KEY_IDLE_PAGE_ENABLED = "idle_page_enabled"
        const val KEY_OPEN_DRAWER_IN_SEARCH_MODE = "open_drawer_in_search_mode"
        val LEGACY_DEFAULT_FONT_ID: PixelFontId = PixelFontId.WEN_QUAN_YI_BITMAP_SONG_16PX
    }
}

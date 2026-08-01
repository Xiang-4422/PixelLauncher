package com.purride.pixellauncherv2.data

import android.content.Context
import android.net.Uri
import com.purride.pixeldesign.ProductAppearance
import com.purride.pixeldesign.ProductAppearanceContract
import com.purride.pixeldesign.ProductPixelCatalog

/** Launcher 内部负责持久化并发布跨进程产品外观快照的唯一边界。 */
internal object ProductAppearanceExchange {
    /** 设备保护区镜像使用的独立偏好文件名。 */
    private const val DEVICE_PREFERENCES_NAME: String = "product_appearance_exchange"

    /** 设备保护区中的协议版本键。 */
    private const val KEY_SCHEMA_VERSION: String = "schema_version"

    /** 设备保护区中的像素形状键。 */
    private const val KEY_PIXEL_SHAPE: String = "pixel_shape"

    /** 设备保护区中的物理像素尺寸键。 */
    private const val KEY_DOT_SIZE_PX: String = "dot_size_px"

    /** 设备保护区中的像素间隙键。 */
    private const val KEY_PIXEL_GAP_ENABLED: String = "pixel_gap_enabled"

    /** 设备保护区中的主题家族键。 */
    private const val KEY_THEME_FAMILY: String = "theme_family"

    /** 设备保护区中的主题模式键。 */
    private const val KEY_THEME_MODE: String = "theme_mode"

    /** 返回当前安装变体对应的外观 URI。 */
    fun contentUri(context: Context): Uri = Uri.parse(
        ProductAppearanceContract.contentUri("${context.packageName}.appearance"),
    )

    /** 把完整外观快照同步写入直接启动可读镜像，并通知 SystemUI 观察者。 */
    fun publish(context: Context, appearance: ProductAppearance) {
        /** 仅保存非敏感外观值的设备保护区上下文。 */
        val deviceContext = context.createDeviceProtectedStorageContext()
        deviceContext.getSharedPreferences(DEVICE_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SCHEMA_VERSION, ProductAppearanceContract.schemaVersion)
            .putString(KEY_PIXEL_SHAPE, appearance.pixelShape.name)
            .putInt(KEY_DOT_SIZE_PX, appearance.dotSizePx)
            .putBoolean(KEY_PIXEL_GAP_ENABLED, appearance.pixelGapEnabled)
            .putString(KEY_THEME_FAMILY, appearance.themeFamily.idPrefix)
            .putString(KEY_THEME_MODE, appearance.themeMode.name)
            .apply()
        context.contentResolver.notifyChange(contentUri(context), null)
    }

    /** 从设备保护区读取最后一次已发布快照；缺失或协议不匹配时返回 null。 */
    fun readPublished(context: Context): ProductAppearance? {
        /** 只读访问直接启动镜像的设备保护区上下文。 */
        val deviceContext = context.createDeviceProtectedStorageContext()
        /** 当前已发布的非敏感外观偏好。 */
        val preferences = deviceContext.getSharedPreferences(
            DEVICE_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        if (preferences.getInt(KEY_SCHEMA_VERSION, -1) != ProductAppearanceContract.schemaVersion) {
            return null
        }
        return ProductAppearance(
            pixelShape = ProductPixelCatalog.parsePixelShape(preferences.getString(KEY_PIXEL_SHAPE, null)),
            dotSizePx = ProductPixelCatalog.normalizeDotSize(preferences.getInt(KEY_DOT_SIZE_PX, -1)),
            pixelGapEnabled = preferences.getBoolean(
                KEY_PIXEL_GAP_ENABLED,
                ProductPixelCatalog.defaultPixelGapEnabled,
            ),
            themeFamily = ProductAppearanceContract.parseThemeFamily(
                preferences.getString(KEY_THEME_FAMILY, null),
            ),
            themeMode = ProductAppearanceContract.parseThemeMode(
                preferences.getString(KEY_THEME_MODE, null),
            ),
        )
    }

    /** 把 Launcher 仓库类型转换为不包含字体和行为设置的共享外观快照。 */
    fun from(settings: FontSettingsRepository.AppearanceSettings): ProductAppearance = ProductAppearance(
        pixelShape = settings.pixelShape,
        dotSizePx = ProductPixelCatalog.normalizeDotSize(settings.dotSizePx),
        pixelGapEnabled = settings.pixelGapEnabled,
        themeFamily = settings.themeFamily,
        themeMode = settings.themeMode,
    )
}

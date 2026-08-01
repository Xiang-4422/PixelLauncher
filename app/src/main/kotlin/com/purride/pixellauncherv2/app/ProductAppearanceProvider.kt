package com.purride.pixellauncherv2.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.purride.pixeldesign.ProductAppearance
import com.purride.pixeldesign.ProductAppearanceContract
import com.purride.pixellauncherv2.data.FontSettingsRepository
import com.purride.pixellauncherv2.data.ProductAppearanceExchange

/**
 * 向 SystemUI 公开当前产品外观快照的只读 Provider。
 *
 * 数据只包含主题和像素表现，不包含通知、联系人、短信或任何用户内容；所有写入口均明确拒绝。
 */
class ProductAppearanceProvider : ContentProvider() {
    /** Provider 无需预热资源，创建成功后按查询惰性读取。 */
    override fun onCreate(): Boolean = true

    /** 返回当前唯一外观快照；未知路径和自定义投影均被拒绝。 */
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        require(uri.lastPathSegment == ProductAppearanceContract.appearancePath) {
            "未知产品外观路径"
        }
        require(projection == null || projection.contentEquals(APPEARANCE_COLUMNS)) {
            "产品外观 Provider 只支持完整固定投影"
        }
        require(selection == null && selectionArgs == null && sortOrder == null) {
            "产品外观 Provider 不支持筛选或排序"
        }
        /** 已附着到 Provider 的 Launcher 上下文。 */
        val providerContext = checkNotNull(context) { "产品外观 Provider 尚未附着" }
        /** 优先读取直接启动镜像；首次查询时用 Launcher 当前设置建立镜像。 */
        val appearance = ProductAppearanceExchange.readPublished(providerContext)
            ?: FontSettingsRepository(providerContext, handDebugSettingAllowed = false)
                .getAppearanceSettings()
                .let(ProductAppearanceExchange::from)
                .also { current -> ProductAppearanceExchange.publish(providerContext, current) }
        return MatrixCursor(APPEARANCE_COLUMNS, 1).apply {
            addRow(appearanceRow(appearance))
            setNotificationUri(providerContext.contentResolver, uri)
        }
    }

    /** 返回单行产品外观 MIME 类型。 */
    override fun getType(uri: Uri): String =
        "vnd.android.cursor.item/vnd.purride.product-appearance"

    /** 外部和内部调用方都不得通过 Provider 插入外观。 */
    override fun insert(uri: Uri, values: ContentValues?): Uri = denyWrite()

    /** 外部和内部调用方都不得通过 Provider 删除外观。 */
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = denyWrite()

    /** 外部和内部调用方都不得通过 Provider 更新外观。 */
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = denyWrite()

    /** 统一拒绝全部 Provider 写入口。 */
    private fun <T> denyWrite(): T = throw SecurityException("产品外观 Provider 只允许读取")

    companion object {
        /** Provider 固定返回的完整列顺序。 */
        internal val APPEARANCE_COLUMNS: Array<String> = arrayOf(
            ProductAppearanceContract.columnSchemaVersion,
            ProductAppearanceContract.columnPixelShape,
            ProductAppearanceContract.columnDotSizePx,
            ProductAppearanceContract.columnPixelGapEnabled,
            ProductAppearanceContract.columnThemeFamily,
            ProductAppearanceContract.columnThemeMode,
        )

        /** 把共享外观快照编码成固定 Cursor 行。 */
        internal fun appearanceRow(appearance: ProductAppearance): Array<Any> = arrayOf(
            ProductAppearanceContract.schemaVersion,
            appearance.pixelShape.name,
            appearance.dotSizePx,
            if (appearance.pixelGapEnabled) 1 else 0,
            appearance.themeFamily.idPrefix,
            appearance.themeMode.name,
        )
    }
}

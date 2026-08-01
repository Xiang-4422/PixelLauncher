package com.purride.pixellockscreen

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.purride.pixeldesign.ProductAppearance
import com.purride.pixeldesign.ProductAppearanceContract
import com.purride.pixeldesign.ProductPixelCatalog

/**
 * 在 SystemUI 进程中只读观察 Launcher 外观快照的适配器。
 *
 * 适配器不持有 Launcher 组件，也不把任何凭据或锁屏状态写回 Provider；读取失败时保持最近一次
 * 有效快照，首次读取失败则使用共享默认值，确保配置同步永远不能阻断系统解锁链。
 */
internal class LauncherAppearanceAdapter(
    /** 用于访问跨进程 ContentProvider 的 SystemUI 上下文。 */
    context: Context,
    /** 有效外观快照发生变化时执行的主线程回调。 */
    private val onAppearanceChanged: (ProductAppearance) -> Unit,
) {
    /** 避免意外持有临时视图 Context 的应用级上下文。 */
    private val applicationContext: Context = context.applicationContext ?: context

    /** SystemUI 主线程处理器，统一串行配置读取和渲染刷新。 */
    private val mainHandler: Handler = Handler(Looper.getMainLooper())

    /** 正式版优先、Debug 版兜底的稳定只读 URI。 */
    private val candidateUris: List<Uri> = listOf(
        ProductAppearanceContract.releaseAuthority,
        ProductAppearanceContract.debugAuthority,
    ).map { authority -> Uri.parse(ProductAppearanceContract.contentUri(authority)) }

    /** 监听两个已知 authority 的配置变更；不存在的 Provider 不影响注册。 */
    private val contentObserver: ContentObserver = object : ContentObserver(mainHandler) {
        /** Provider 通知配置变化后重新读取完整原子快照。 */
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            refresh()
        }
    }

    /** 最近一次已接受的完整配置，任何读取故障都保持该安全值。 */
    var currentAppearance: ProductAppearance = ProductAppearance()
        private set

    /** 防止重复注册 ContentObserver。 */
    private var started: Boolean = false

    /** 注册只读观察并立即尝试读取 Launcher 当前外观。 */
    fun start() {
        check(Looper.myLooper() === Looper.getMainLooper()) { "appearance_adapter_main_thread" }
        if (started) return
        candidateUris.forEach { uri ->
            runCatching {
                applicationContext.contentResolver.registerContentObserver(uri, false, contentObserver)
            }
        }
        started = true
        refresh()
    }

    /** 注销全部观察；方法幂等且不会影响 Launcher Provider。 */
    fun stop() {
        if (!started) return
        runCatching {
            applicationContext.contentResolver.unregisterContentObserver(contentObserver)
        }
        started = false
    }

    /** 按优先级读取首个可用 Provider，只在完整协议有效时替换当前快照。 */
    private fun refresh() {
        if (!started) return
        /** 本轮读取到的首个有效外观快照。 */
        val resolved = candidateUris.firstNotNullOfOrNull(::queryAppearance) ?: return
        if (resolved == currentAppearance) return
        currentAppearance = resolved
        onAppearanceChanged(resolved)
    }

    /** 从单个 Provider URI 读取固定投影并关闭 Cursor。 */
    private fun queryAppearance(uri: Uri): ProductAppearance? = runCatching {
        applicationContext.contentResolver.query(
            uri,
            APPEARANCE_PROJECTION,
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            decodeProductAppearanceRecord(
                ProductAppearanceRecord(
                    schemaVersion = cursor.getInt(
                        cursor.getColumnIndexOrThrow(ProductAppearanceContract.columnSchemaVersion),
                    ),
                    pixelShape = cursor.getString(
                        cursor.getColumnIndexOrThrow(ProductAppearanceContract.columnPixelShape),
                    ),
                    dotSizePx = cursor.getInt(
                        cursor.getColumnIndexOrThrow(ProductAppearanceContract.columnDotSizePx),
                    ),
                    pixelGapEnabled = cursor.getInt(
                        cursor.getColumnIndexOrThrow(ProductAppearanceContract.columnPixelGapEnabled),
                    ),
                    themeFamily = cursor.getString(
                        cursor.getColumnIndexOrThrow(ProductAppearanceContract.columnThemeFamily),
                    ),
                    themeMode = cursor.getString(
                        cursor.getColumnIndexOrThrow(ProductAppearanceContract.columnThemeMode),
                    ),
                ),
            )
        }
    }.getOrNull()

    private companion object {
        /** Provider 查询使用的固定列顺序，禁止锁屏读取未声明字段。 */
        val APPEARANCE_PROJECTION: Array<String> = arrayOf(
            ProductAppearanceContract.columnSchemaVersion,
            ProductAppearanceContract.columnPixelShape,
            ProductAppearanceContract.columnDotSizePx,
            ProductAppearanceContract.columnPixelGapEnabled,
            ProductAppearanceContract.columnThemeFamily,
            ProductAppearanceContract.columnThemeMode,
        )
    }
}

/** 可脱离 Android Cursor 单独验证的 Provider 行快照。 */
internal data class ProductAppearanceRecord(
    /** Provider 返回的协议版本。 */
    val schemaVersion: Int,
    /** 外部像素形状名称。 */
    val pixelShape: String?,
    /** 外部物理像素尺寸。 */
    val dotSizePx: Int?,
    /** 外部间隙布尔整数，只接受零或一。 */
    val pixelGapEnabled: Int?,
    /** 外部主题家族稳定 ID。 */
    val themeFamily: String?,
    /** 外部主题模式名称。 */
    val themeMode: String?,
)

/** 把 Provider 行解码为受共享目录约束的安全外观；不兼容协议整体拒绝。 */
internal fun decodeProductAppearanceRecord(record: ProductAppearanceRecord): ProductAppearance? {
    if (record.schemaVersion != ProductAppearanceContract.schemaVersion) return null
    return ProductAppearance(
        pixelShape = ProductPixelCatalog.parsePixelShape(record.pixelShape),
        dotSizePx = ProductPixelCatalog.normalizeDotSize(record.dotSizePx),
        pixelGapEnabled = when (record.pixelGapEnabled) {
            1 -> true
            0 -> false
            else -> ProductPixelCatalog.defaultPixelGapEnabled
        },
        themeFamily = ProductAppearanceContract.parseThemeFamily(record.themeFamily),
        themeMode = ProductAppearanceContract.parseThemeMode(record.themeMode),
    )
}

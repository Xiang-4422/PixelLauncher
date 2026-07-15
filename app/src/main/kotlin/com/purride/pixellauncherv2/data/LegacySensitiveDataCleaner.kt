package com.purride.pixellauncherv2.data

import android.content.Context

/**
 * 删除旧版本遗留的本地敏感数据。
 *
 * 旧版 Launcher 曾把第三方凭据写入普通 SharedPreferences。当前产品不再提供该配置入口，
 * 因此升级时必须删除整个旧偏好文件，而不是迁移或继续使用其中的值。
 */
internal object LegacySensitiveDataCleaner {

    /** 旧版敏感偏好文件名；同时由备份规则永久排除。 */
    internal const val LEGACY_PREFERENCES_NAME: String = "pixel_launcher_ai_prefs"

    /**
     * 同步清空并删除旧偏好文件。
     *
     * 方法可以安全地重复执行。同步提交确保应用其他组件启动前，内存缓存和磁盘文件中的旧值
     * 都已被清除；删除文件失败但清空成功时仍视为安全完成。
     */
    fun clear(context: Context) {
        // 应用级 Context 避免清理过程意外持有 Activity。
        val applicationContext = context.applicationContext
        // 旧偏好实例只用于同步擦除，不会读取或迁移其中任何值。
        val legacyPreferences = applicationContext.getSharedPreferences(
            LEGACY_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        // 同步清空结果覆盖 SharedPreferences 已进入内存缓存的情况。
        val cleared = legacyPreferences.edit().clear().commit()
        // 文件删除结果覆盖磁盘上仍存在旧 XML 的情况。
        val deleted = applicationContext.deleteSharedPreferences(LEGACY_PREFERENCES_NAME)

        check(cleared || deleted) {
            "Unable to erase legacy sensitive preferences."
        }
    }
}

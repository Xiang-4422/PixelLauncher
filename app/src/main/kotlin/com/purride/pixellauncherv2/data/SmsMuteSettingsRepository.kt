package com.purride.pixellauncherv2.data

import android.content.Context

/**
 * 会话静音规则仓库：被静音的会话来信照常入库，只是不弹通知。
 *
 * 以 conversationKey 为粒度持久化（SharedPreferences），
 * 与消息流的会话归组口径一致——服务号聚合会话静音一次即全部生效。
 * SharedPreferences 本身线程安全，可被接收器工作线程与后台刷新线程并发访问。
 *
 * 会话键内含手机号，因此该偏好文件被备份规则排除（见 SECURITY.md 与 backup_rules.xml）：
 * 静音规则不跨设备恢复，换机后需重新设置。
 */
class SmsMuteSettingsRepository(
    context: Context,
) {

    private val preferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 当前被静音的全部会话键。 */
    fun mutedConversationKeys(): Set<String> =
        preferences.getStringSet(KEY_MUTED_CONVERSATIONS, emptySet()).orEmpty()

    /** 某会话是否被静音。 */
    fun isMuted(conversationKey: String): Boolean =
        conversationKey.isNotBlank() && conversationKey in mutedConversationKeys()

    /** 设置某会话的静音状态；返回更新后的静音集合。 */
    fun setMuted(conversationKey: String, muted: Boolean): Set<String> {
        if (conversationKey.isBlank()) {
            return mutedConversationKeys()
        }
        val next = mutedConversationKeys().toMutableSet().apply {
            if (muted) add(conversationKey) else remove(conversationKey)
        }
        preferences.edit().putStringSet(KEY_MUTED_CONVERSATIONS, next).apply()
        return next
    }

    private companion object {
        const val PREFS_NAME = "sms_mute_settings"
        const val KEY_MUTED_CONVERSATIONS = "muted_conversation_keys"
    }
}

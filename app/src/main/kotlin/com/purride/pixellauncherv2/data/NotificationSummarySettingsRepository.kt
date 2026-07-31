package com.purride.pixellauncherv2.data

import android.content.Context
import com.purride.pixellauncherv2.launcher.NotificationSummaryRules

class NotificationSummarySettingsRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun rules(): NotificationSummaryRules {
        return NotificationSummaryRules(
            allowedSourceIds = prefs.getStringSet(KEY_ALLOWED, emptySet()).orEmpty().sanitize(),
        )
    }

    /** 切换指定来源是否属于 Launcher 通知白名单。 */
    fun toggleSource(sourceId: String): NotificationSummaryRules {
        val normalized = sourceId.trim()
        if (normalized.isEmpty()) return rules()

        val currentRules = rules()
        val allowed = currentRules.allowedSourceIds.toMutableSet()
        if (!allowed.add(normalized)) {
            allowed.remove(normalized)
        }

        val nextRules = NotificationSummaryRules(allowedSourceIds = allowed)
        prefs.edit()
            .putStringSet(KEY_ALLOWED, nextRules.allowedSourceIds)
            .apply()
        return nextRules
    }

    private fun Set<String>.sanitize(): Set<String> {
        return mapNotNull { value ->
            value.trim().takeIf(String::isNotEmpty)
        }.toSet()
    }

    private companion object {
        const val PREFS_NAME = "notification_summary_settings"
        const val KEY_ALLOWED = "allowed_source_ids"
    }
}

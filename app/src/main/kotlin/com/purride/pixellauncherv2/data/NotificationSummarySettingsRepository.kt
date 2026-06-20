package com.purride.pixellauncherv2.data

import android.content.Context
import com.purride.pixellauncherv2.launcher.NotificationSourceMode
import com.purride.pixellauncherv2.launcher.NotificationSettingsModel
import com.purride.pixellauncherv2.launcher.NotificationSummaryRules

class NotificationSummarySettingsRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun rules(): NotificationSummaryRules {
        return NotificationSummaryRules(
            mutedSourceIds = prefs.getStringSet(KEY_MUTED, emptySet()).orEmpty().sanitize(),
            prioritySourceIds = prefs.getStringSet(KEY_PRIORITY, emptySet()).orEmpty().sanitize(),
        ).withoutConflicts()
    }

    fun cycleSource(sourceId: String): NotificationSummaryRules {
        val normalized = sourceId.trim()
        if (normalized.isEmpty()) return rules()

        val currentRules = rules()
        val currentMode = when {
            normalized in currentRules.mutedSourceIds -> NotificationSourceMode.MUTED
            normalized in currentRules.prioritySourceIds -> NotificationSourceMode.PRIORITY
            else -> NotificationSourceMode.NORMAL
        }
        val nextMode = NotificationSettingsModel.nextMode(currentMode)
        val muted = currentRules.mutedSourceIds.toMutableSet()
        val priority = currentRules.prioritySourceIds.toMutableSet()

        muted -= normalized
        priority -= normalized
        when (nextMode) {
            NotificationSourceMode.NORMAL -> Unit
            NotificationSourceMode.PRIORITY -> priority += normalized
            NotificationSourceMode.MUTED -> muted += normalized
        }

        val nextRules = NotificationSummaryRules(
            mutedSourceIds = muted,
            prioritySourceIds = priority,
        ).withoutConflicts()
        prefs.edit()
            .putStringSet(KEY_MUTED, nextRules.mutedSourceIds)
            .putStringSet(KEY_PRIORITY, nextRules.prioritySourceIds)
            .apply()
        return nextRules
    }

    private fun Set<String>.sanitize(): Set<String> {
        return mapNotNull { value ->
            value.trim().takeIf(String::isNotEmpty)
        }.toSet()
    }

    private fun NotificationSummaryRules.withoutConflicts(): NotificationSummaryRules {
        return copy(prioritySourceIds = prioritySourceIds - mutedSourceIds)
    }

    private companion object {
        const val PREFS_NAME = "notification_summary_settings"
        const val KEY_MUTED = "muted_source_ids"
        const val KEY_PRIORITY = "priority_source_ids"
    }
}

package com.purride.pixellauncherv2.launcher

enum class NotificationSourceMode {
    NORMAL,
    PRIORITY,
    MUTED,
}

data class NotificationSettingsRow(
    val sourceId: String,
    val label: String,
    val mode: NotificationSourceMode,
)

object NotificationSettingsModel {

    fun rows(
        sources: List<NotificationSourceInfo>,
        mutedSourceIds: Set<String>,
        prioritySourceIds: Set<String>,
    ): List<NotificationSettingsRow> {
        val byId = linkedMapOf<String, String>()
        sources.forEach { source ->
            byId[source.sourceId] = source.sourceLabel
        }
        mutedSourceIds.forEach { sourceId ->
            byId.putIfAbsent(sourceId, fallbackLabel(sourceId))
        }
        prioritySourceIds.forEach { sourceId ->
            byId.putIfAbsent(sourceId, fallbackLabel(sourceId))
        }

        return byId.entries
            .sortedBy { it.value }
            .map { (sourceId, label) ->
                NotificationSettingsRow(
                    sourceId = sourceId,
                    label = label,
                    mode = modeFor(
                        sourceId = sourceId,
                        mutedSourceIds = mutedSourceIds,
                        prioritySourceIds = prioritySourceIds,
                    ),
                )
            }
    }

    fun summary(
        mutedSourceIds: Set<String>,
        prioritySourceIds: Set<String>,
    ): String {
        val mutedCount = mutedSourceIds.size
        val priorityCount = prioritySourceIds.size
        return if (mutedCount == 0 && priorityCount == 0) {
            "DEFAULT"
        } else {
            "M$mutedCount P$priorityCount"
        }
    }

    fun nextMode(current: NotificationSourceMode): NotificationSourceMode {
        return when (current) {
            NotificationSourceMode.NORMAL -> NotificationSourceMode.PRIORITY
            NotificationSourceMode.PRIORITY -> NotificationSourceMode.MUTED
            NotificationSourceMode.MUTED -> NotificationSourceMode.NORMAL
        }
    }

    fun modeLabel(mode: NotificationSourceMode): String {
        return when (mode) {
            NotificationSourceMode.NORMAL -> "NORMAL"
            NotificationSourceMode.PRIORITY -> "PRIORITY"
            NotificationSourceMode.MUTED -> "MUTED"
        }
    }

    private fun modeFor(
        sourceId: String,
        mutedSourceIds: Set<String>,
        prioritySourceIds: Set<String>,
    ): NotificationSourceMode {
        return when {
            sourceId in mutedSourceIds -> NotificationSourceMode.MUTED
            sourceId in prioritySourceIds -> NotificationSourceMode.PRIORITY
            else -> NotificationSourceMode.NORMAL
        }
    }

    private fun fallbackLabel(sourceId: String): String {
        return sourceId.substringAfterLast('.').uppercase().ifBlank { sourceId }
    }
}

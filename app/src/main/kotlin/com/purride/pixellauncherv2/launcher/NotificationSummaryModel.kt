package com.purride.pixellauncherv2.launcher

enum class NotificationSignalPriority {
    LOW,
    DEFAULT,
    HIGH,
}

data class NotificationSignal(
    val sourceId: String,
    val sourceLabel: String,
    val title: String = "",
    val priority: NotificationSignalPriority = NotificationSignalPriority.DEFAULT,
    val isOngoing: Boolean = false,
    val isSilent: Boolean = false,
    val postedAtMillis: Long = 0L,
)

data class NotificationSummaryRules(
    val mutedSourceIds: Set<String> = emptySet(),
    val prioritySourceIds: Set<String> = emptySet(),
    val maxItems: Int = 2,
)

data class NotificationSummary(
    val count: Int,
    val text: String,
)

object NotificationSummaryModel {

    fun summarize(
        signals: List<NotificationSignal>,
        rules: NotificationSummaryRules = NotificationSummaryRules(),
    ): NotificationSummary {
        val candidates = signals
            .asSequence()
            .filter { signal -> signal.sourceId.isNotBlank() }
            .filterNot { signal -> signal.sourceId in rules.mutedSourceIds }
            .filter { signal -> signal.isPriorityFor(rules) }
            .sortedWith(
                compareByDescending<NotificationSignal> { signal -> signal.sourceId in rules.prioritySourceIds }
                    .thenByDescending { signal -> signal.priority == NotificationSignalPriority.HIGH }
                    .thenByDescending { signal -> signal.postedAtMillis },
            )
            .toList()

        if (candidates.isEmpty()) {
            return NotificationSummary(count = 0, text = "")
        }

        val visible = candidates
            .take(rules.maxItems.coerceAtLeast(1))
            .map(NotificationSummaryModel::summaryToken)
        val hiddenCount = candidates.size - visible.size
        val suffix = if (hiddenCount > 0) " +$hiddenCount" else ""
        return NotificationSummary(
            count = candidates.size,
            text = visible.joinToString("  ") + suffix,
        )
    }

    private fun NotificationSignal.isPriorityFor(rules: NotificationSummaryRules): Boolean {
        if (isSilent) return false
        if (isOngoing) return false
        if (sourceId in rules.prioritySourceIds) return true
        if (priority == NotificationSignalPriority.HIGH) return true
        return false
    }

    private fun summaryToken(signal: NotificationSignal): String {
        val source = signal.sourceLabel.trim().ifEmpty { signal.sourceId.trim() }
        val title = signal.title.trim()
        return if (title.isEmpty()) {
            source
        } else {
            "$source $title"
        }
    }
}

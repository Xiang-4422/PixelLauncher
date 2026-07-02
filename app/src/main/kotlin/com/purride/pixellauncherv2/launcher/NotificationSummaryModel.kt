package com.purride.pixellauncherv2.launcher

enum class NotificationSignalPriority {
    LOW,
    DEFAULT,
    HIGH,
}

data class NotificationSignal(
    val sourceId: String,
    val sourceLabel: String,
    val key: String = "",
    val title: String = "",
    val text: String = "",
    val subText: String = "",
    val bigText: String = "",
    val summaryText: String = "",
    val textLines: List<String> = emptyList(),
    val category: String = "",
    val channelId: String = "",
    val isMediaStyle: Boolean = false,
    val priority: NotificationSignalPriority = NotificationSignalPriority.DEFAULT,
    val isOngoing: Boolean = false,
    val isSilent: Boolean = false,
    val isClearable: Boolean = true,
    val progress: NotificationProgressInfo = NotificationProgressInfo(),
    val actions: List<NotificationActionInfo> = emptyList(),
    val postedAtMillis: Long = 0L,
)

data class NotificationProgressInfo(
    val max: Int = 0,
    val value: Int = 0,
    val indeterminate: Boolean = false,
)

data class NotificationActionInfo(
    val index: Int,
    val title: String,
    val requiresInput: Boolean = false,
)

data class NotificationSummaryRules(
    val mutedSourceIds: Set<String> = emptySet(),
    val prioritySourceIds: Set<String> = emptySet(),
    val maxItems: Int = 2,
)

data class NotificationSourceInfo(
    val sourceId: String,
    val sourceLabel: String,
)

data class NotificationSummary(
    val count: Int,
    val text: String,
    val sources: List<NotificationSourceInfo> = emptyList(),
    val items: List<NotificationSignal> = emptyList(),
)

object NotificationSummaryModel {

    fun summarize(
        signals: List<NotificationSignal>,
        rules: NotificationSummaryRules = NotificationSummaryRules(),
    ): NotificationSummary {
        val sources = signals
            .asSequence()
            .filter { signal -> signal.sourceId.isNotBlank() }
            .map { signal ->
                NotificationSourceInfo(
                    sourceId = signal.sourceId.trim(),
                    sourceLabel = signal.sourceLabel.trim().ifEmpty { signal.sourceId.trim() },
                )
            }
            .distinctBy(NotificationSourceInfo::sourceId)
            .sortedBy(NotificationSourceInfo::sourceLabel)
            .toList()
        val candidates = signals
            .asSequence()
            .filter { signal -> signal.sourceId.isNotBlank() }
            .filterNot { signal -> signal.sourceId in rules.mutedSourceIds }
            .filterNot(NotificationSummaryModel::isMediaControl)
            .sortedWith(
                compareByDescending<NotificationSignal> { signal -> signal.sourceId in rules.prioritySourceIds }
                    .thenByDescending { signal -> signal.priority == NotificationSignalPriority.HIGH }
                    .thenByDescending { signal -> signal.postedAtMillis },
            )
            .toList()

        if (candidates.isEmpty()) {
            return NotificationSummary(count = 0, text = "", sources = sources)
        }

        val visible = candidates
            .take(rules.maxItems.coerceAtLeast(1))
            .map(NotificationSummaryModel::summaryToken)
        val hiddenCount = candidates.size - visible.size
        val suffix = if (hiddenCount > 0) " +$hiddenCount" else ""
        return NotificationSummary(
            count = candidates.size,
            text = visible.joinToString("  ") + suffix,
            sources = sources,
            items = candidates,
        )
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

    private fun isMediaControl(signal: NotificationSignal): Boolean {
        return signal.isMediaStyle || signal.category == MEDIA_TRANSPORT_CATEGORY
    }

    private const val MEDIA_TRANSPORT_CATEGORY = "transport"
}

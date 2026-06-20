package com.purride.pixellauncherv2.data

enum class ScreenUsageEventType {
    INTERACTIVE,
    NON_INTERACTIVE,
}

data class ScreenUsageEvent(
    val type: ScreenUsageEventType,
    val timestampMillis: Long,
)

object ScreenUsageSummaryModel {

    fun summarize(
        events: List<ScreenUsageEvent>,
        nowMillis: Long,
    ): ScreenUsageSnapshot {
        var interactiveStartedAt: Long? = null
        var interactiveDurationMillis = 0L
        var screenOpenCount = 0

        events.sortedBy(ScreenUsageEvent::timestampMillis).forEach { event ->
            when (event.type) {
                ScreenUsageEventType.INTERACTIVE -> {
                    screenOpenCount += 1
                    interactiveStartedAt = event.timestampMillis
                }

                ScreenUsageEventType.NON_INTERACTIVE -> {
                    val startedAt = interactiveStartedAt
                    if (startedAt != null && event.timestampMillis > startedAt) {
                        interactiveDurationMillis += event.timestampMillis - startedAt
                    }
                    interactiveStartedAt = null
                }
            }
        }

        val activeStart = interactiveStartedAt
        if (activeStart != null && nowMillis > activeStart) {
            interactiveDurationMillis += nowMillis - activeStart
        }

        return ScreenUsageSnapshot(
            usageTimeText = ScreenUsageRepository.formatDurationText(interactiveDurationMillis),
            openCountText = screenOpenCount.toString(),
        )
    }
}

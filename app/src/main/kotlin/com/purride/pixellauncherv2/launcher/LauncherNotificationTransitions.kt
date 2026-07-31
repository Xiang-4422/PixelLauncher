package com.purride.pixellauncherv2.launcher

/**
 * Notification 域的纯状态转换（ADR-0001 阶段 2 拆分，来源 LauncherStateTransitions）。
 *
 * 承载通知实时摘要快照与持久静音/优先规则的写入。对外入口仍是
 * [LauncherStateTransitions] facade；行为与拆分前逐字节等价。
 */
object LauncherNotificationTransitions {

    /** 写入通知实时摘要快照：文本、数量、来源目录与通知项。 */
    fun updateNotificationSummary(
        state: LauncherState,
        notificationSummaryText: String,
        notificationCount: Int,
        notificationSources: List<NotificationSourceInfo> = state.notificationSources,
        notificationItems: List<NotificationSignal> = state.notificationItems,
    ): LauncherState {
        return state.copy(
            notificationSummaryText = notificationSummaryText.trim(),
            notificationCount = notificationCount.coerceAtLeast(0),
            notificationSources = notificationSources,
            notificationItems = notificationItems,
        )
    }

    /** 写入持久通知规则；muted 与 priority 冲突时 muted 优先。 */
    fun updateNotificationRules(
        state: LauncherState,
        mutedSourceIds: Set<String>,
        prioritySourceIds: Set<String>,
    ): LauncherState {
        val muted = mutedSourceIds.sanitizeSourceIds()
        return state.copy(
            mutedNotificationSourceIds = muted,
            priorityNotificationSourceIds = prioritySourceIds.sanitizeSourceIds() - muted,
        )
    }

    private fun Set<String>.sanitizeSourceIds(): Set<String> {
        return mapNotNull { value ->
            value.trim().takeIf(String::isNotEmpty)
        }.toSet()
    }
}

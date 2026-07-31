package com.purride.pixellauncherv2.launcher

import java.text.Collator
import java.util.Locale

/** 通知白名单页面展示的一行应用或通知来源。 */
data class NotificationSettingsRow(
    val sourceId: String,
    val label: String,
    /** 当前来源是否已经加入 Launcher 通知白名单。 */
    val isAllowed: Boolean,
)

/** 负责合并应用与通知来源，并复用 Drawer 规则完成白名单搜索和排序。 */
object NotificationSettingsModel {

    /**
     * 生成可主动配置的通知来源列表。
     *
     * Drawer 应用作为主要候选，实际通知来源负责补齐没有桌面入口的应用；白名单中的历史
     * 来源继续保留，确保用户始终可以主动关闭。ON 项优先，同组按搜索相关度和 Drawer
     * 字母序排列。
     */
    fun rows(
        apps: List<AppEntry>,
        sources: List<NotificationSourceInfo>,
        allowedSourceIds: Set<String>,
        query: String = "",
    ): List<NotificationSettingsRow> {
        val byId = linkedMapOf<String, AppEntry>()
        apps.forEach { app ->
            val sourceId = app.packageName.trim()
            if (sourceId.isNotEmpty()) {
                byId.putIfAbsent(sourceId, app)
            }
        }
        sources.forEach { source ->
            val sourceId = source.sourceId.trim()
            if (sourceId.isNotEmpty()) {
                byId.putIfAbsent(
                    sourceId,
                    sourceApp(
                        sourceId = sourceId,
                        label = source.sourceLabel,
                    ),
                )
            }
        }
        allowedSourceIds.forEach { sourceId ->
            val normalizedSourceId = sourceId.trim()
            if (normalizedSourceId.isNotEmpty()) {
                byId.putIfAbsent(
                    normalizedSourceId,
                    sourceApp(
                        sourceId = normalizedSourceId,
                        label = fallbackLabel(normalizedSourceId),
                    ),
                )
            }
        }

        val normalizedQuery = DrawerSearchSupport.normalizeForSearch(query)
        val labelCollator = Collator.getInstance(Locale.getDefault())
        return byId.entries.asSequence()
            .mapNotNull { (sourceId, app) ->
                val metadata = DrawerSearchSupport.buildMetadata(app)
                val searchScore = DrawerSearchSupport.searchScoreForNormalizedQuery(
                    metadata = metadata,
                    normalizedQuery = normalizedQuery,
                ) ?: return@mapNotNull null
                NotificationSettingsCandidate(
                    sourceId = sourceId,
                    app = app,
                    metadata = metadata,
                    searchScore = searchScore,
                    isAllowed = sourceId in allowedSourceIds,
                )
            }
            .sortedWith(notificationCandidateComparator(labelCollator))
            .map { candidate ->
                NotificationSettingsRow(
                    sourceId = candidate.sourceId,
                    label = candidate.app.label.trim().ifEmpty { fallbackLabel(candidate.sourceId) },
                    isAllowed = candidate.isAllowed,
                )
            }
            .toList()
    }

    /** 以紧凑文本显示当前白名单来源数量。 */
    fun summary(allowedSourceIds: Set<String>): String = "${allowedSourceIds.size} ON"

    /** 为没有 Drawer 入口的通知来源创建可参与统一搜索的轻量应用信息。 */
    private fun sourceApp(sourceId: String, label: String): AppEntry {
        return AppEntry(
            label = label.trim().ifEmpty { fallbackLabel(sourceId) },
            packageName = sourceId,
            activityName = "",
        )
    }

    /** 从包名尾段生成通知来源的兜底显示名。 */
    private fun fallbackLabel(sourceId: String): String {
        return sourceId.substringAfterLast('.').uppercase().ifBlank { sourceId }
    }

    /** 候选项排序使用的稳定比较器：ON、相关度、字母索引、拼音/名称、包名。 */
    private fun notificationCandidateComparator(
        labelCollator: Collator,
    ): Comparator<NotificationSettingsCandidate> {
        return Comparator { left, right ->
            compareValues(right.isAllowed, left.isAllowed)
                .takeIf { it != 0 }
                ?: left.searchScore.compareTo(right.searchScore).takeIf { it != 0 }
                ?: left.metadata.letterIndex.compareTo(right.metadata.letterIndex).takeIf { it != 0 }
                ?: labelCollator.compare(left.metadata.sortKey, right.metadata.sortKey).takeIf { it != 0 }
                ?: labelCollator.compare(left.app.label, right.app.label).takeIf { it != 0 }
                ?: left.sourceId.compareTo(right.sourceId)
        }
    }

    /** 合并后的内部候选项及其一次性计算的搜索元数据。 */
    private data class NotificationSettingsCandidate(
        val sourceId: String,
        val app: AppEntry,
        val metadata: DrawerSearchMetadata,
        val searchScore: Int,
        val isAllowed: Boolean,
    )
}

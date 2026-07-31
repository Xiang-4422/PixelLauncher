package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.model.LauncherStatsSnapshot
import java.text.Collator
import java.util.Locale

/**
 * AppCatalog / Drawer 域的纯状态转换（ADR-0001 阶段 2 拆分，来源 LauncherStateTransitions）。
 *
 * 承载应用目录、抽屉列表/搜索/窗口、应用编辑草稿与启动统计的写入；Drawer 搜索
 * metadata 与排序 helper 按 ADR 一并归入本组。对外入口仍是 [LauncherStateTransitions]
 * facade；行为与拆分前逐字节等价。
 */
object LauncherAppCatalogTransitions {

    fun showAppActionMenu(state: LauncherState, selectedIndex: Int): LauncherState {
        if (state.mode != LauncherMode.APP_DRAWER || state.apps.isEmpty()) {
            return state.copy(isAppActionMenuVisible = false)
        }
        val safeIndex = selectedIndex.coerceIn(0, state.apps.lastIndex)
        val selectedApp = state.apps[safeIndex]
        return state.copy(
            isAppActionMenuVisible = true,
            isDrawerSearchFocused = false,
            isDrawerRailSliding = false,
            appEditorSelectedIndex = safeIndex,
            appEditorNameDraft = selectedApp.label,
            appEditorAliasDraft = selectedApp.aliases.joinToString(" "),
        )
    }

    fun hideAppActionMenu(state: LauncherState): LauncherState {
        return state.copy(isAppActionMenuVisible = false)
    }

    /**
     * 主 Pager 开始拖动时关闭 Drawer 的输入焦点与应用操作浮层。
     *
     * Rail 动画状态不属于本手势回调的既有写入面，因此保持原值。
     */
    fun dismissDrawerOverlaysForPagerDrag(state: LauncherState): LauncherState {
        return state.copy(
            isDrawerSearchFocused = false,
            isAppActionMenuVisible = false,
        )
    }

    fun showAppManagement(state: LauncherState, selectedIndex: Int = state.appEditorSelectedIndex): LauncherState {
        val apps = state.apps
        val safeIndex = selectedIndex.coerceIn(0, (apps.size - 1).coerceAtLeast(0))
        val selectedApp = apps.getOrNull(safeIndex)
        val returnMode = if (state.mode == LauncherMode.APP_MANAGEMENT) state.returnMode else state.mode
        return state.copy(
            mode = LauncherMode.APP_MANAGEMENT,
            returnMode = returnMode,
            isAppActionMenuVisible = false,
            appEditorSelectedIndex = safeIndex,
            appEditorNameDraft = selectedApp?.label.orEmpty(),
            appEditorAliasDraft = selectedApp?.aliases.orEmpty().joinToString(" "),
        )
    }

    fun hideAppManagement(state: LauncherState): LauncherState {
        val returnMode = when (state.returnMode) {
            LauncherMode.HOME,
            LauncherMode.APP_DRAWER,
            LauncherMode.SETTINGS,
            LauncherMode.MORE_SETTINGS -> state.returnMode

            LauncherMode.APP_MANAGEMENT,
            LauncherMode.DATA_HEALTH,
            LauncherMode.NOTIFICATION_SETTINGS,
            LauncherMode.LOADING_PREVIEW,
            LauncherMode.DIAGNOSTICS,
            LauncherMode.IDLE,
            LauncherMode.SMS_ROLE_PROMPT,
            LauncherMode.SMS_THREADS,
            LauncherMode.SMS_THREAD_DETAIL,
            LauncherMode.DIALER,
            LauncherMode.CONTACT_DETAIL,
            LauncherMode.CONTACT_EDITOR,
            LauncherMode.SNAKE -> LauncherMode.SETTINGS
        }
        return state.copy(mode = returnMode)
    }

    fun moveAppEditorSelection(state: LauncherState, direction: Int): LauncherState {
        val apps = state.apps
        if (apps.isEmpty()) {
            return state.copy(
                appEditorSelectedIndex = 0,
                appEditorNameDraft = "",
                appEditorAliasDraft = "",
            )
        }
        val nextIndex = wrapIndex(state.appEditorSelectedIndex + direction, apps.size)
        val selectedApp = apps[nextIndex]
        return state.copy(
            appEditorSelectedIndex = nextIndex,
            appEditorNameDraft = selectedApp.label,
            appEditorAliasDraft = selectedApp.aliases.joinToString(" "),
        )
    }

    fun updateAppEditorNameDraft(state: LauncherState, nameDraft: String): LauncherState {
        return state.copy(appEditorNameDraft = nameDraft)
    }

    fun updateAppEditorAliasDraft(state: LauncherState, aliasDraft: String): LauncherState {
        return state.copy(appEditorAliasDraft = aliasDraft)
    }

    /**
     * 以默认状态打开抽屉，并把焦点和窗口重置到第一项。
     */
    fun showAppDrawer(state: LauncherState, visibleRows: Int): LauncherState {
        val stateWithDrawerApps = if (state.apps.isNotEmpty() && state.drawerQuery.isBlank()) {
            state.copy(drawerVisibleApps = orderDefaultApps(state.apps, state.recentApps))
        } else {
            state
        }
        val drawerApps = currentDrawerApps(stateWithDrawerApps)
        if (drawerApps.isEmpty()) {
            return state.copy(
                mode = LauncherMode.APP_DRAWER,
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
            )
        }

        return syncDrawerWindow(
            state = stateWithDrawerApps.copy(
                mode = LauncherMode.APP_DRAWER,
                selectedIndex = 0,
                listStartIndex = 0,
            ),
            visibleRows = visibleRows,
        )
    }

    /**
     * 完成 Drawer 入场后的搜索焦点同步，并结束可能残留的 Rail 滑动状态。
     *
     * 非 Drawer 页面不会写入隐藏的输入焦点。
     */
    fun prepareDrawerEntryFocus(
        state: LauncherState,
        focusSearch: Boolean,
    ): LauncherState {
        if (state.mode != LauncherMode.APP_DRAWER) {
            return state
        }
        return state.copy(
            isDrawerSearchFocused = focusSearch,
            isDrawerRailSliding = false,
        )
    }

    /**
     * 让 Drawer 搜索输入接管焦点，并结束 Rail 滑动。
     *
     * 该事件只在 Drawer 可见时生效，不能用来预写其他页面的隐藏状态。
     */
    fun focusDrawerSearchInput(state: LauncherState): LauncherState {
        if (state.mode != LauncherMode.APP_DRAWER) {
            return state
        }
        return state.copy(
            isDrawerSearchFocused = true,
            isDrawerRailSliding = false,
        )
    }

    /**
     * 应用目录没有可用缓存时开始首轮加载。
     */
    fun beginAppCatalogLoading(state: LauncherState): LauncherState {
        return state.copy(isLoading = true)
    }

    /**
     * 在应用仓库重新加载后重建抽屉列表，并尽量保留合理的当前选择。
     */
    fun withApps(previous: LauncherState, apps: List<AppEntry>, visibleRows: Int): LauncherState {
        val orderedApps = orderDefaultApps(apps, previous.recentApps)
        val drawerApps = filterDrawerApps(
            orderedApps = orderedApps,
            query = previous.drawerQuery,
            recentApps = previous.recentApps,
        )
        if (orderedApps.isEmpty()) {
            return previous.copy(
                apps = emptyList(),
                drawerVisibleApps = emptyList(),
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
                isLoading = false,
            )
        }

        val preservedApp = currentDrawerApps(previous).getOrNull(previous.selectedIndex)
        val selectedIndex = preservedApp?.let { selected ->
            drawerApps.indexOfFirst { candidate ->
                candidate.packageName == selected.packageName &&
                    candidate.activityName == selected.activityName
            }
        }?.takeIf { it >= 0 }
            ?: previous.recentApps.firstNotNullOfOrNull { recentPackage ->
                drawerApps.indexOfFirst { it.packageName == recentPackage }.takeIf { it >= 0 }
            }
            ?: 0

        return syncDrawerWindow(
            state = previous.copy(
                apps = apps,
                drawerVisibleApps = drawerApps,
                selectedIndex = selectedIndex,
                isLoading = false,
            ),
            visibleRows = visibleRows,
        )
    }

    /** 按相对行数移动抽屉焦点，并同步重排可视窗口。 */
    fun moveSelection(state: LauncherState, delta: Int, visibleRows: Int): LauncherState {
        val drawerApps = currentDrawerApps(state)
        if (drawerApps.isEmpty()) {
            return state.copy(
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
            )
        }

        val newSelectedIndex = (state.selectedIndex + delta).coerceIn(0, drawerApps.lastIndex)
        return syncDrawerWindow(
            state = state.copy(selectedIndex = newSelectedIndex),
            visibleRows = visibleRows,
        )
    }

    /**
     * 按相对行数滚动抽屉可视窗口，并让选中项保持在新的可视范围内。
     */
    fun scrollDrawerWindow(state: LauncherState, delta: Int, visibleRows: Int): LauncherState {
        val drawerApps = currentDrawerApps(state)
        if (drawerApps.isEmpty()) {
            return state.copy(
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
            )
        }

        val safeRows = visibleRows.coerceAtLeast(1)
        val maxStartIndex = (drawerApps.size - safeRows).coerceAtLeast(0)
        val previousStartIndex = state.listStartIndex.coerceIn(0, maxStartIndex)
        val newStartIndex = (previousStartIndex + delta).coerceIn(0, maxStartIndex)
        val visibleEndIndex = (newStartIndex + safeRows - 1).coerceAtMost(drawerApps.lastIndex)
        val relativeSelectionRow = (state.selectedIndex - previousStartIndex).coerceIn(0, safeRows - 1)
        val newSelectedIndex = (newStartIndex + relativeSelectionRow).coerceIn(newStartIndex, visibleEndIndex)

        return state.copy(
            selectedIndex = newSelectedIndex,
            listStartIndex = newStartIndex,
            drawerPageIndex = AppDrawerIndexModel.create(
                apps = drawerApps,
                visibleRows = visibleRows,
                selectedIndex = newSelectedIndex,
            ).currentPageIndex,
            drawerFocus = DrawerFocus.LIST,
        )
    }

    /**
     * 以当前顶部项为基准，按一个 viewport 的大小向前或向后翻动抽屉。
     */
    fun pageSelection(state: LauncherState, direction: Int, visibleRows: Int): LauncherState {
        val drawerApps = currentDrawerApps(state)
        if (drawerApps.isEmpty()) {
            return state.copy(
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
            )
        }

        val pageSize = visibleRows.coerceAtLeast(1)
        val currentTopIndex = state.listStartIndex.coerceIn(0, drawerApps.lastIndex)
        val targetIndex = (currentTopIndex + (direction * pageSize)).coerceIn(0, drawerApps.lastIndex)
        return syncDrawerWindow(
            state = state.copy(
                selectedIndex = targetIndex,
                listStartIndex = targetIndex,
            ),
            visibleRows = visibleRows,
        )
    }

    /** 选中抽屉中的绝对索引，并把顶对齐窗口同步到该项。 */
    fun selectIndex(state: LauncherState, index: Int, visibleRows: Int): LauncherState {
        val drawerApps = currentDrawerApps(state)
        if (drawerApps.isEmpty()) {
            return state.copy(
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
            )
        }

        val newSelectedIndex = index.coerceIn(0, drawerApps.lastIndex)
        return syncDrawerWindow(
            state = state.copy(selectedIndex = newSelectedIndex),
            visibleRows = visibleRows,
        )
    }

    fun selectDrawerPage(state: LauncherState, pageIndex: Int, visibleRows: Int): LauncherState {
        val drawerApps = currentDrawerApps(state)
        if (drawerApps.isEmpty()) {
            return state.copy(
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
            )
        }

        val indexModel = AppDrawerIndexModel.create(
            apps = drawerApps,
            visibleRows = visibleRows,
            selectedIndex = state.selectedIndex,
        )
        if (indexModel.pageCount == 0) {
            return state
        }

        val safePageIndex = pageIndex.coerceIn(0, indexModel.pageCount - 1)
        val pageStartIndex = indexModel.pageStartIndices[safePageIndex]
        return syncDrawerWindow(
            state = state.copy(
                selectedIndex = pageStartIndex,
                listStartIndex = pageStartIndex,
                drawerPageIndex = safePageIndex,
                drawerFocus = DrawerFocus.LIST,
            ),
            visibleRows = visibleRows,
        )
    }

    fun selectByPackageName(state: LauncherState, packageName: String, visibleRows: Int): LauncherState {
        val selectedIndex = currentDrawerApps(state).indexOfFirst { it.packageName == packageName }
        return if (selectedIndex >= 0) {
            selectIndex(state, selectedIndex, visibleRows)
        } else {
            state
        }
    }

    fun selectByLetterIndex(state: LauncherState, letterIndex: Int, visibleRows: Int): LauncherState {
        val drawerApps = currentDrawerApps(state)
        if (drawerApps.isEmpty()) {
            return state.copy(
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
            )
        }
        val alphaIndexModel = DrawerAlphaIndexModel.create(
            apps = drawerApps,
            selectedIndex = state.selectedIndex,
        )
        val targetIndex = alphaIndexModel.resolveNearestLetterAppIndex(letterIndex)
            ?.coerceIn(0, drawerApps.lastIndex)
            ?: return state
        return selectIndex(
            state = state,
            index = targetIndex,
            visibleRows = visibleRows,
        )
    }

    /**
     * 根据新的 query 重新计算抽屉过滤结果，并把焦点重置到第一条结果。
     */
    fun updateDrawerQuery(state: LauncherState, query: String, visibleRows: Int): LauncherState {
        val safeQuery = DrawerAsciiInputSanitizer.filter(query).take(maxDrawerQueryLength)
        val orderedApps = orderBlankQueryApps(state.apps, state.recentApps)
        val drawerApps = filterDrawerApps(
            orderedApps = orderedApps,
            query = safeQuery,
            recentApps = state.recentApps,
        )

        return syncDrawerWindow(
            state = state.copy(
                drawerQuery = safeQuery,
                drawerVisibleApps = drawerApps,
                selectedIndex = 0,
                listStartIndex = 0,
            ),
            visibleRows = visibleRows,
        )
    }

    /** 把已经过过滤的搜索文本追加到当前抽屉 query 后。 */
    fun appendDrawerQuery(state: LauncherState, text: String, visibleRows: Int): LauncherState {
        if (text.isEmpty()) {
            return state
        }
        return updateDrawerQuery(
            state = state,
            query = state.drawerQuery + text,
            visibleRows = visibleRows,
        )
    }

    /** 删除最后一个搜索字符，并保持过滤结果和窗口状态同步。 */
    fun backspaceDrawerQuery(state: LauncherState, visibleRows: Int): LauncherState {
        if (state.drawerQuery.isEmpty()) {
            return state
        }
        return updateDrawerQuery(
            state = state,
            query = state.drawerQuery.dropLast(1),
            visibleRows = visibleRows,
        )
    }

    /** 清空当前抽屉 query，并恢复默认排序后的抽屉列表。 */
    fun clearDrawerQuery(state: LauncherState, visibleRows: Int): LauncherState {
        return updateDrawerQuery(
            state = state,
            query = "",
            visibleRows = visibleRows,
        )
    }

    /**
     * 退出抽屉搜索，并尽量把搜索态当前焦点保留为默认列表中的顶部项。
     */
    fun exitDrawerSearch(state: LauncherState, visibleRows: Int): LauncherState {
        val preservedApp = currentDrawerApps(state).getOrNull(state.selectedIndex)
        val defaultDrawerApps = orderDefaultApps(state.apps, state.recentApps)
        val clearedState = syncDrawerWindow(
            state = state.copy(
                drawerQuery = "",
                drawerVisibleApps = defaultDrawerApps,
                selectedIndex = 0,
                listStartIndex = 0,
            ),
            visibleRows = visibleRows,
        ).copy(
            isDrawerSearchFocused = false,
            isDrawerRailSliding = false,
        )
        val restoredIndex = preservedApp?.let { selected ->
            currentDrawerApps(clearedState).indexOfFirst { candidate ->
                candidate.packageName == selected.packageName &&
                    candidate.activityName == selected.activityName
            }.takeIf { it >= 0 }
        } ?: 0
        return syncDrawerWindow(
            state = clearedState.copy(
                selectedIndex = restoredIndex,
                listStartIndex = restoredIndex,
            ),
            visibleRows = visibleRows,
        ).copy(
            isDrawerSearchFocused = false,
            isDrawerRailSliding = false,
        )
    }

    /**
     * 在屏幕尺寸、应用数据或焦点变化后，重新校正抽屉窗口。
     */
    fun reflowWindow(state: LauncherState, visibleRows: Int): LauncherState {
        val drawerApps = currentDrawerApps(state)
        if (drawerApps.isEmpty()) {
            return state.copy(
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
            )
        }

        val safeSelectedIndex = state.selectedIndex.coerceIn(0, drawerApps.lastIndex)
        return syncDrawerWindow(
            state = state.copy(selectedIndex = safeSelectedIndex),
            visibleRows = visibleRows,
        )
    }

    /**
     * 根据当前可视行数，为选中项计算一个尽量靠前但不会留下底部空白的窗口起点。
     */
    fun calculateListStartIndex(selectedIndex: Int, visibleRows: Int, totalCount: Int): Int {
        if (totalCount <= 0) {
            return 0
        }

        val safeRows = visibleRows.coerceAtLeast(1)
        val safeSelectedIndex = selectedIndex.coerceIn(0, totalCount - 1)
        val maxStartIndex = (totalCount - safeRows).coerceAtLeast(0)
        return safeSelectedIndex.coerceAtMost(maxStartIndex)
    }

    /** 写回启动统计快照：recent 列表、启动计数与最后启动包名。 */
    fun updateStats(state: LauncherState, stats: LauncherStatsSnapshot): LauncherState {
        return state.copy(
            recentApps = stats.recentApps,
            launchCount = stats.launchCount,
            lastLaunchPackageName = stats.lastLaunchPackageName,
        )
    }

    /**
     * 保持抽屉的焦点、顶部项和派生页索引处于一致状态。
     */
    private fun syncDrawerWindow(state: LauncherState, visibleRows: Int): LauncherState {
        val drawerApps = currentDrawerApps(state)
        if (drawerApps.isEmpty()) {
            return state.copy(
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
            )
        }

        val safeSelectedIndex = state.selectedIndex.coerceIn(0, drawerApps.lastIndex)
        val indexModel = AppDrawerIndexModel.create(
            apps = drawerApps,
            visibleRows = visibleRows,
            selectedIndex = safeSelectedIndex,
        )
        val listStartIndex = calculateListStartIndex(
            selectedIndex = safeSelectedIndex,
            visibleRows = visibleRows,
            totalCount = drawerApps.size,
        )

        return state.copy(
            selectedIndex = safeSelectedIndex,
            listStartIndex = listStartIndex,
            drawerPageIndex = indexModel.currentPageIndex,
            drawerFocus = DrawerFocus.LIST,
        )
    }

    private fun currentDrawerApps(state: LauncherState): List<AppEntry> {
        if (state.drawerVisibleApps.isNotEmpty()) {
            return state.drawerVisibleApps
        }
        if (state.drawerQuery.isNotBlank()) {
            return emptyList()
        }
        return state.apps
    }

    private fun orderBlankQueryApps(apps: List<AppEntry>, recentApps: List<String>): List<AppEntry> {
        if (apps.isEmpty()) {
            return apps
        }
        val metadataByIdentity = buildMetadataMap(apps)
        val recentRankByPackage = recentApps
            .take(maxRecentBoostAppCount)
            .withIndex()
            .associate { indexed -> indexed.value to indexed.index }

        return apps.sortedWith(
            compareBy<AppEntry> { recentRankByPackage[it.packageName] ?: Int.MAX_VALUE }
                .thenComparator { left, right ->
                    val leftMeta = metadataByIdentity.getValue(appIdentity(left))
                    val rightMeta = metadataByIdentity.getValue(appIdentity(right))
                    val sortCompare = labelCollator.compare(leftMeta.sortKey, rightMeta.sortKey)
                    if (sortCompare != 0) {
                        sortCompare
                    } else {
                        labelCollator.compare(left.label, right.label)
                    }
                },
        )
    }

    /**
     * 在不破坏字母排序心智模型的前提下，给最近使用的应用做轻量前移。
     */
    private fun orderDefaultApps(apps: List<AppEntry>, recentApps: List<String>): List<AppEntry> {
        if (apps.isEmpty()) {
            return apps
        }
        val metadataByIdentity = buildMetadataMap(apps)
        val alphabetical = apps.sortedWith { left, right ->
            val leftMeta = metadataByIdentity.getValue(appIdentity(left))
            val rightMeta = metadataByIdentity.getValue(appIdentity(right))
            val letterCompare = leftMeta.letterIndex.compareTo(rightMeta.letterIndex)
            if (letterCompare != 0) {
                return@sortedWith letterCompare
            }
            val sortCompare = labelCollator.compare(
                leftMeta.sortKey,
                rightMeta.sortKey,
            )
            if (sortCompare != 0) {
                return@sortedWith sortCompare
            }
            labelCollator.compare(left.label, right.label)
        }
        return applyLightRecentBoost(
            orderedApps = alphabetical,
            recentApps = recentApps,
            metadataByIdentity = metadataByIdentity,
        )
    }

    /**
     * 根据归一化标签、别名和轻量 recent 规则过滤并排序抽屉搜索结果。
     */
    private fun filterDrawerApps(
        orderedApps: List<AppEntry>,
        query: String,
        recentApps: List<String>,
    ): List<AppEntry> {
        if (query.isBlank()) {
            return orderedApps
        }
        val normalizedQuery = DrawerSearchSupport.normalizeForSearch(query)
        if (normalizedQuery.isEmpty()) {
            return orderedApps
        }
        val metadataByIdentity = buildMetadataMap(orderedApps)
        val recentRankByPackage = recentApps
            .take(maxRecentBoostAppCount)
            .withIndex()
            .associate { indexed -> indexed.value to indexed.index }

        return orderedApps
            .asSequence()
            .mapNotNull { app ->
                val metadata = metadataByIdentity.getValue(appIdentity(app))
                val score = DrawerSearchSupport.searchScoreForNormalizedQuery(
                    metadata = metadata,
                    normalizedQuery = normalizedQuery,
                ) ?: return@mapNotNull null

                DrawerSearchHit(
                    app = app,
                    score = score,
                    recentRank = recentRankByPackage[app.packageName] ?: Int.MAX_VALUE,
                    sortKey = metadata.sortKey,
                )
            }
            .sortedWith(
                compareBy<DrawerSearchHit> { it.score }
                    .thenBy { it.recentRank }
                    .thenComparator { left, right ->
                        val sortCompare = labelCollator.compare(left.sortKey, right.sortKey)
                        if (sortCompare != 0) {
                            sortCompare
                        } else {
                            labelCollator.compare(left.app.label, right.app.label)
                        }
                    },
            )
            .map { it.app }
            .toList()
    }

    private fun applyLightRecentBoost(
        orderedApps: List<AppEntry>,
        recentApps: List<String>,
        metadataByIdentity: Map<String, DrawerSearchMetadata>,
    ): List<AppEntry> {
        if (orderedApps.size < 2 || recentApps.isEmpty()) {
            return orderedApps
        }
        val adjustedApps = orderedApps.toMutableList()
        recentApps
            .take(maxRecentBoostAppCount)
            .forEachIndexed { recentRank, packageName ->
                val fromIndex = adjustedApps.indexOfFirst { it.packageName == packageName }
                if (fromIndex <= 0) {
                    return@forEachIndexed
                }
                val movingApp = adjustedApps[fromIndex]
                val movingMeta = metadataByIdentity.getValue(appIdentity(movingApp))
                val letterStartIndex = adjustedApps.indexOfFirst { candidate ->
                    metadataByIdentity.getValue(appIdentity(candidate)).letterIndex == movingMeta.letterIndex
                }
                if (letterStartIndex < 0) {
                    return@forEachIndexed
                }
                val maxShift = (maxRecentBoostShift - recentRank).coerceAtLeast(1)
                val targetIndex = (fromIndex - maxShift).coerceAtLeast(letterStartIndex)
                if (targetIndex >= fromIndex) {
                    return@forEachIndexed
                }
                adjustedApps.removeAt(fromIndex)
                adjustedApps.add(targetIndex, movingApp)
            }
        return adjustedApps
    }

    private fun buildMetadataMap(apps: List<AppEntry>): Map<String, DrawerSearchMetadata> {
        return apps.associate { appEntry ->
            appIdentity(appEntry) to DrawerSearchSupport.buildMetadata(appEntry)
        }
    }

    private fun appIdentity(appEntry: AppEntry): String {
        return "${appEntry.packageName}/${appEntry.activityName}"
    }

    private fun wrapIndex(index: Int, size: Int): Int {
        if (size <= 0) return 0
        val mod = index % size
        return if (mod < 0) mod + size else mod
    }

    private data class DrawerSearchHit(
        val app: AppEntry,
        val score: Int,
        val recentRank: Int,
        val sortKey: String,
    )

    private val labelCollator: Collator = Collator.getInstance(Locale.getDefault())
    private const val maxDrawerQueryLength: Int = 40
    private const val maxRecentBoostAppCount: Int = 3
    private const val maxRecentBoostShift: Int = 3
}

package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.data.DeviceStatus
import com.purride.pixellauncherv2.data.LauncherStatsSnapshot
import com.purride.pixellauncherv2.render.PixelFontId
import com.purride.pixellauncherv2.render.PixelShape
import com.purride.pixellauncherv2.render.PixelTheme
import java.text.Collator
import java.util.Locale

object LauncherStateTransitions {

    fun showHome(state: LauncherState): LauncherState {
        return state.copy(mode = LauncherMode.HOME)
    }

    fun showSettings(state: LauncherState): LauncherState {
        val returnMode = when (state.mode) {
            LauncherMode.HOME,
            LauncherMode.APP_DRAWER,
            LauncherMode.IDLE -> state.mode

            LauncherMode.SETTINGS,
            LauncherMode.DIAGNOSTICS -> state.returnMode
        }
        return state.copy(
            mode = LauncherMode.SETTINGS,
            returnMode = returnMode,
            settingsSelectedIndex = state.settingsSelectedIndex.coerceIn(0, SettingsMenuModel.rows(state).lastIndex),
        )
    }

    fun hideSettings(state: LauncherState): LauncherState {
        val fallbackMode = when (state.returnMode) {
            LauncherMode.HOME,
            LauncherMode.APP_DRAWER,
            LauncherMode.IDLE -> state.returnMode

            LauncherMode.SETTINGS,
            LauncherMode.DIAGNOSTICS -> LauncherMode.HOME
        }
        return state.copy(
            mode = fallbackMode,
            returnMode = fallbackMode,
        )
    }

    fun showDiagnostics(state: LauncherState): LauncherState {
        return state.copy(mode = LauncherMode.DIAGNOSTICS)
    }

    fun hideDiagnostics(state: LauncherState): LauncherState {
        return state.copy(mode = LauncherMode.SETTINGS)
    }

    fun showIdle(state: LauncherState): LauncherState {
        if (!state.isIdlePageEnabled) {
            return state
        }
        if (state.mode != LauncherMode.HOME && state.mode != LauncherMode.APP_DRAWER) {
            return state
        }
        return state.copy(
            mode = LauncherMode.IDLE,
            returnMode = state.mode,
        )
    }

    fun hideIdle(state: LauncherState): LauncherState {
        return state.copy(mode = state.returnMode)
    }

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
                apps = orderedApps,
                drawerVisibleApps = drawerApps,
                selectedIndex = selectedIndex,
                isLoading = false,
            ),
            visibleRows = visibleRows,
        )
    }

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
        return state.copy(
            selectedIndex = pageStartIndex,
            listStartIndex = pageStartIndex,
            drawerPageIndex = safePageIndex,
            drawerFocus = DrawerFocus.LIST,
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

    fun updateDrawerQuery(state: LauncherState, query: String, visibleRows: Int): LauncherState {
        val safeQuery = query.take(maxDrawerQueryLength)
        val orderedApps = orderDefaultApps(state.apps, state.recentApps)
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

    fun clearDrawerQuery(state: LauncherState, visibleRows: Int): LauncherState {
        return updateDrawerQuery(
            state = state,
            query = "",
            visibleRows = visibleRows,
        )
    }

    fun exitDrawerSearch(state: LauncherState, visibleRows: Int): LauncherState {
        val preservedApp = currentDrawerApps(state).getOrNull(state.selectedIndex)
        val clearedState = clearDrawerQuery(
            state = state,
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

    fun selectSettingsIndex(state: LauncherState, index: Int): LauncherState {
        val maxIndex = (SettingsMenuModel.rows(state).size - 1).coerceAtLeast(0)
        return state.copy(settingsSelectedIndex = index.coerceIn(0, maxIndex))
    }

    fun moveSettingsSelection(state: LauncherState, delta: Int): LauncherState {
        return selectSettingsIndex(
            state = state,
            index = state.settingsSelectedIndex + delta,
        )
    }

    fun updateTime(
        state: LauncherState,
        currentTimeText: String,
        currentDateText: String = state.currentDateText,
        currentWeekdayText: String = state.currentWeekdayText,
    ): LauncherState {
        return state.copy(
            currentTimeText = currentTimeText,
            currentDateText = currentDateText,
            currentWeekdayText = currentWeekdayText,
        )
    }

    fun updateAppearance(
        state: LauncherState,
        selectedFontId: PixelFontId = state.selectedFontId,
        selectedPixelShape: PixelShape = state.selectedPixelShape,
        selectedDotSizePx: Int = state.selectedDotSizePx,
        selectedTheme: PixelTheme = state.selectedTheme,
    ): LauncherState {
        return state.copy(
            selectedFontId = selectedFontId,
            selectedPixelShape = selectedPixelShape,
            selectedDotSizePx = selectedDotSizePx,
            selectedTheme = selectedTheme,
        )
    }

    fun updateUiBehavior(
        state: LauncherState,
        drawerListAlignment: DrawerListAlignment = state.drawerListAlignment,
        isIdlePageEnabled: Boolean = state.isIdlePageEnabled,
        openDrawerInSearchMode: Boolean = state.openDrawerInSearchMode,
    ): LauncherState {
        return state.copy(
            drawerListAlignment = drawerListAlignment,
            isIdlePageEnabled = isIdlePageEnabled,
            openDrawerInSearchMode = openDrawerInSearchMode,
        )
    }

    fun updateDeviceStatus(state: LauncherState, deviceStatus: DeviceStatus): LauncherState {
        return state.copy(
            batteryLevel = deviceStatus.batteryLevel,
            isCharging = deviceStatus.isCharging,
        )
    }

    fun updateStats(state: LauncherState, stats: LauncherStatsSnapshot): LauncherState {
        return state.copy(
            recentApps = stats.recentApps,
            launchCount = stats.launchCount,
            lastLaunchPackageName = stats.lastLaunchPackageName,
        )
    }

    fun updateTerminalStatus(state: LauncherState, terminalStatusText: String): LauncherState {
        return state.copy(terminalStatusText = terminalStatusText)
    }

    fun updateNextAlarmText(state: LauncherState, nextAlarmText: String): LauncherState {
        return state.copy(nextAlarmText = nextAlarmText)
    }

    fun updateCommunicationStatus(
        state: LauncherState,
        missedCallCount: Int,
        unreadSmsCount: Int,
    ): LauncherState {
        return state.copy(
            missedCallCount = missedCallCount.coerceAtLeast(0),
            unreadSmsCount = unreadSmsCount.coerceAtLeast(0),
        )
    }

    fun updateRainHintText(state: LauncherState, rainHintText: String): LauncherState {
        return state.copy(rainHintText = rainHintText)
    }

    fun updateScreenUsageSummary(
        state: LauncherState,
        screenUsageTimeText: String,
        screenOpenCountText: String,
    ): LauncherState {
        return state.copy(
            screenUsageTimeText = screenUsageTimeText,
            screenOpenCountText = screenOpenCountText,
        )
    }

    fun recordInteraction(state: LauncherState, uptimeMs: Long): LauncherState {
        return state.copy(lastInteractionUptimeMs = uptimeMs)
    }

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

    fun calculateListStartIndex(selectedIndex: Int, visibleRows: Int, totalCount: Int): Int {
        if (totalCount <= 0) {
            return 0
        }

        val safeRows = visibleRows.coerceAtLeast(1)
        val safeSelectedIndex = selectedIndex.coerceIn(0, totalCount - 1)
        return ((safeSelectedIndex / safeRows) * safeRows).coerceAtLeast(0)
    }

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
        val listStartIndex = safeSelectedIndex

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
                val score = resolveSearchScore(
                    normalizedQuery = normalizedQuery,
                    metadata = metadata,
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

    private fun resolveSearchScore(
        normalizedQuery: String,
        metadata: DrawerSearchMetadata,
    ): Int? {
        return when {
            metadata.normalizedLabel == normalizedQuery ||
                metadata.normalizedEnglishLabel == normalizedQuery ||
                metadata.normalizedAlias == normalizedQuery ||
                metadata.normalizedPackage == normalizedQuery ||
                metadata.normalizedActivity == normalizedQuery ||
                metadata.pinyinFull == normalizedQuery ||
                metadata.pinyinInitial == normalizedQuery -> 0

            metadata.normalizedLabel.startsWith(normalizedQuery) -> 1
            metadata.normalizedEnglishLabel.startsWith(normalizedQuery) -> 1
            metadata.normalizedAlias.startsWith(normalizedQuery) -> 2
            metadata.normalizedPackage.startsWith(normalizedQuery) -> 2
            metadata.normalizedActivity.startsWith(normalizedQuery) -> 2
            metadata.pinyinFull.startsWith(normalizedQuery) -> 3
            metadata.pinyinInitial.startsWith(normalizedQuery) -> 4
            metadata.normalizedLabel.contains(normalizedQuery) ||
                metadata.normalizedEnglishLabel.contains(normalizedQuery) ||
                metadata.normalizedAlias.contains(normalizedQuery) ||
                metadata.normalizedPackage.contains(normalizedQuery) ||
                metadata.normalizedActivity.contains(normalizedQuery) ||
                metadata.pinyinFull.contains(normalizedQuery) ||
                metadata.pinyinInitial.contains(normalizedQuery) -> 5

            else -> null
        }
    }

    private fun buildMetadataMap(apps: List<AppEntry>): Map<String, DrawerSearchMetadata> {
        return apps.associate { appEntry ->
            appIdentity(appEntry) to DrawerSearchSupport.buildMetadata(appEntry)
        }
    }

    private fun appIdentity(appEntry: AppEntry): String {
        return "${appEntry.packageName}/${appEntry.activityName}"
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

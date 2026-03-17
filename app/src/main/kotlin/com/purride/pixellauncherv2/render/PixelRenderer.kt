package com.purride.pixellauncherv2.render

import com.purride.pixellauncherv2.launcher.AppEntry
import com.purride.pixellauncherv2.launcher.DrawerAlphaIndexModel
import com.purride.pixellauncherv2.launcher.AppListLayout
import com.purride.pixellauncherv2.launcher.AppListLayoutMetrics
import com.purride.pixellauncherv2.launcher.DiagnosticsLine
import com.purride.pixellauncherv2.launcher.DiagnosticsModel
import com.purride.pixellauncherv2.launcher.DrawerListAlignment
import com.purride.pixellauncherv2.launcher.HomeContextCard
import com.purride.pixellauncherv2.launcher.HomeLayout
import com.purride.pixellauncherv2.launcher.HomeLayoutMetrics
import com.purride.pixellauncherv2.launcher.LauncherHeaderLayout
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import com.purride.pixellauncherv2.launcher.SettingsMenuLayout
import com.purride.pixellauncherv2.launcher.SettingsMenuLayoutMetrics
import com.purride.pixellauncherv2.launcher.SettingsMenuModel
import com.purride.pixellauncherv2.launcher.SettingsMenuRow
import com.purride.pixellauncherv2.util.LabelFormatter

class PixelRenderer(
    private val pixelFontEngine: PixelFontEngine,
) {

    fun render(
        state: LauncherState,
        screenProfile: ScreenProfile,
        animationState: LauncherAnimationState,
        pagerSnapshot: HorizontalPageSnapshot? = null,
        drawerListScrollOffsetPx: Int = 0,
    ): PixelBuffer {
        val bootBuffer = PixelBuffer(
            width = screenProfile.logicalWidth,
            height = screenProfile.logicalHeight,
        )
        bootBuffer.clear()

        val bootSequence = animationState.bootSequence
        if (bootSequence != null) {
            drawBootSequence(bootBuffer, screenProfile, bootSequence)
            return bootBuffer
        }

        val pageBuffer = if (pagerSnapshot == null) {
            renderSingleMode(
                state = state,
                screenProfile = screenProfile,
                animationState = animationState,
                mode = state.mode,
                drawerListScrollOffsetPx = drawerListScrollOffsetPx,
            )
        } else {
            renderPagerSnapshot(
                state = state,
                screenProfile = screenProfile,
                animationState = animationState,
                pagerSnapshot = pagerSnapshot,
                drawerListScrollOffsetPx = drawerListScrollOffsetPx,
            )
        }
        applyLaunchShutterOverlay(pageBuffer, animationState.launchShutter)
        return pageBuffer
    }

    private fun renderPagerSnapshot(
        state: LauncherState,
        screenProfile: ScreenProfile,
        animationState: LauncherAnimationState,
        pagerSnapshot: HorizontalPageSnapshot,
        drawerListScrollOffsetPx: Int,
    ): PixelBuffer {
        val anchorMode = pagerModeForIndex(pagerSnapshot.anchorPageIndex) ?: state.mode
        val anchorPage = renderSingleMode(
            state = state,
            screenProfile = screenProfile,
            animationState = animationState,
            mode = anchorMode,
            drawerListScrollOffsetPx = drawerListScrollOffsetPx,
        )

        val dragOffset = pagerSnapshot.dragOffsetPx
        if (kotlin.math.abs(dragOffset) < pagerCompositionThresholdPx) {
            return anchorPage
        }
        val adjacentMode = adjacentPagerMode(
            anchorIndex = pagerSnapshot.anchorPageIndex,
            dragOffsetPx = dragOffset,
            pageCount = pagerSnapshot.pageCount,
        ) ?: return anchorPage
        val adjacentPage = renderSingleMode(
            state = state,
            screenProfile = screenProfile,
            animationState = animationState,
            mode = adjacentMode,
            drawerListScrollOffsetPx = drawerListScrollOffsetPx,
        )
        return HorizontalPageRenderer.compose(
            currentPage = anchorPage,
            adjacentPage = adjacentPage,
            dragOffsetPx = dragOffset,
            contentStartY = LauncherHeaderLayout.contentTop,
        )
    }

    private fun renderSingleMode(
        state: LauncherState,
        screenProfile: ScreenProfile,
        animationState: LauncherAnimationState,
        mode: LauncherMode,
        drawerListScrollOffsetPx: Int = 0,
    ): PixelBuffer {
        val buffer = PixelBuffer(
            width = screenProfile.logicalWidth,
            height = screenProfile.logicalHeight,
        )
        buffer.clear()

        val modeState = if (state.mode == mode) state else state.copy(mode = mode)
        val modeScrollOffset = if (mode == LauncherMode.APP_DRAWER && state.mode == LauncherMode.APP_DRAWER) {
            drawerListScrollOffsetPx
        } else {
            0
        }
        when (mode) {
            LauncherMode.HOME -> drawHome(buffer, modeState, screenProfile, animationState)
            LauncherMode.APP_DRAWER -> drawAppDrawer(
                buffer = buffer,
                state = modeState,
                screenProfile = screenProfile,
                animationState = animationState,
                drawerListScrollOffsetPx = modeScrollOffset,
            )
            LauncherMode.SETTINGS -> drawSettings(buffer, modeState, screenProfile, animationState)
            LauncherMode.DIAGNOSTICS -> drawDiagnostics(buffer, modeState, screenProfile, animationState)
            LauncherMode.IDLE -> drawIdle(buffer, modeState, screenProfile)
        }
        if (mode == LauncherMode.APP_DRAWER) {
            applyDrawerRevealOverlay(buffer, animationState.drawerReveal)
        }
        return buffer
    }

    private fun pagerModeForIndex(index: Int): LauncherMode? {
        return when (index) {
            pagerSettingsIndex -> LauncherMode.SETTINGS
            pagerHomeIndex -> LauncherMode.HOME
            pagerAppsIndex -> LauncherMode.APP_DRAWER
            else -> null
        }
    }

    private fun adjacentPagerMode(
        anchorIndex: Int,
        dragOffsetPx: Float,
        pageCount: Int,
    ): LauncherMode? {
        val adjacentIndex = if (dragOffsetPx > 0f) {
            anchorIndex - 1
        } else {
            anchorIndex + 1
        }
        if (adjacentIndex !in 0 until pageCount) {
            return null
        }
        return pagerModeForIndex(adjacentIndex)
    }

    private fun drawHome(
        buffer: PixelBuffer,
        state: LauncherState,
        screenProfile: ScreenProfile,
        animationState: LauncherAnimationState,
    ) {
        val layoutMetrics = HomeLayout.metrics(screenProfile)
        drawHeader(
            buffer = buffer,
            screenProfile = screenProfile,
            state = state,
            titleCandidates = listOf("HOME", "HM"),
            chargeTick = animationState.headerChargeTick,
        )

        val dateLine = pixelFontEngine.trimToWidth(
            text = state.currentDateText.ifBlank { "------- --- --" },
            style = GlyphStyle.UI_SMALL_10,
            maxWidth = layoutMetrics.innerWidth,
        )
        if (dateLine.isNotEmpty()) {
            drawTextAsValue(
                buffer = buffer,
                text = dateLine,
                startX = layoutMetrics.innerLeft,
                startY = layoutMetrics.dateY,
                maxWidth = layoutMetrics.innerWidth,
                style = GlyphStyle.UI_SMALL_10,
                value = PixelBuffer.ACCENT,
            )
        }

        val dynamicInfoSegments = buildList {
            if (state.missedCallCount > 0) {
                add("CALL ${state.missedCallCount}")
            }
            if (state.unreadSmsCount > 0) {
                add("SMS ${state.unreadSmsCount}")
            }
            if (state.rainHintText.isNotBlank()) {
                add("RAIN ${state.rainHintText}")
            }
        }
        val fixedInfoLines = buildList {
            add("ALARM ${state.nextAlarmText.ifBlank { "--:--" }}")
            if (dynamicInfoSegments.isNotEmpty()) {
                add(dynamicInfoSegments.joinToString(separator = "  "))
            }
            add("USE ${state.screenUsageTimeText.ifBlank { "--:--" }}  OPEN ${state.screenOpenCountText.ifBlank { "--" }}")
            add(state.terminalStatusText.ifBlank { "READY" })
        }
        var detailY = layoutMetrics.fixedInfoStartY
        fixedInfoLines.forEach { line ->
            if (detailY + GlyphStyle.UI_SMALL_10.cellHeight <= layoutMetrics.fixedBottom) {
                val trimmedLine = pixelFontEngine.trimToWidth(
                    text = line,
                    style = GlyphStyle.UI_SMALL_10,
                    maxWidth = layoutMetrics.innerWidth,
                )
                if (trimmedLine.isNotEmpty()) {
                    pixelFontEngine.drawText(
                        buffer = buffer,
                        text = trimmedLine,
                        startX = layoutMetrics.innerLeft,
                        startY = detailY,
                        maxWidth = layoutMetrics.innerWidth,
                        style = GlyphStyle.UI_SMALL_10,
                    )
                }
            }
            detailY += layoutMetrics.fixedInfoRowHeight
        }

        val contextBody = homeContextBody(state)
        if (contextBody.isNotEmpty()) {
            if (state.homeContextCard == HomeContextCard.QUOTE) {
                drawMarqueeTextAsValue(
                    buffer = buffer,
                    text = contextBody,
                    startX = layoutMetrics.innerLeft,
                    startY = layoutMetrics.stackCardBodyY,
                    maxWidth = layoutMetrics.innerWidth,
                    style = GlyphStyle.UI_SMALL_10,
                    value = PixelBuffer.ACCENT,
                    tick = animationState.headerChargeTick,
                )
            } else {
                val trimmedContextBody = pixelFontEngine.trimToWidth(
                    text = contextBody,
                    style = GlyphStyle.UI_SMALL_10,
                    maxWidth = layoutMetrics.innerWidth,
                )
                if (trimmedContextBody.isNotEmpty()) {
                    drawTextAsValue(
                        buffer = buffer,
                        text = trimmedContextBody,
                        startX = layoutMetrics.innerLeft,
                        startY = layoutMetrics.stackCardBodyY,
                        maxWidth = layoutMetrics.innerWidth,
                        style = GlyphStyle.UI_SMALL_10,
                        value = PixelBuffer.ACCENT,
                    )
                }
            }
        }
    }

    private fun homeContextBody(state: LauncherState): String {
        if (state.isLoading) {
            return "Loading context"
        }
        return when (state.homeContextCard) {
            HomeContextCard.QUOTE -> state.quoteText
            HomeContextCard.MEDIA -> "No media now"
            HomeContextCard.NOTIFICATIONS -> "No priority notice"
            HomeContextCard.TODO -> "No TODO source"
        }
    }

    private fun drawAppDrawer(
        buffer: PixelBuffer,
        state: LauncherState,
        screenProfile: ScreenProfile,
        animationState: LauncherAnimationState,
        drawerListScrollOffsetPx: Int,
    ) {
        val drawerApps = resolveDrawerApps(state)
        val showPagedSearchResults = state.isDrawerSearchFocused && state.drawerQuery.isNotBlank()
        val shouldShowApps = showPagedSearchResults || !state.isDrawerSearchFocused
        val layoutMetrics = AppListLayout.metrics(screenProfile)
        val alphaIndexModel = DrawerAlphaIndexModel.create(
            apps = drawerApps,
            selectedIndex = state.selectedIndex,
        )
        drawDrawerHeader(
            buffer = buffer,
            screenProfile = screenProfile,
            state = state,
            alphaIndexModel = alphaIndexModel,
            tick = animationState.headerChargeTick,
        )

        when {
            state.isLoading && shouldShowApps -> drawCenteredText(
                buffer = buffer,
                text = "Loading",
                y = layoutMetrics.listStartY,
                style = GlyphStyle.APP_LABEL_16,
            )

            !shouldShowApps -> Unit

            drawerApps.isEmpty() -> drawCenteredText(
                buffer = buffer,
                text = if (state.drawerQuery.isBlank()) "No apps" else "NO RESULT",
                y = if (state.drawerQuery.isBlank()) {
                    layoutMetrics.listStartY
                } else {
                    layoutMetrics.listStartY +
                        ((layoutMetrics.railHeight - GlyphStyle.APP_LABEL_16.cellHeight) / 2).coerceAtLeast(0)
                },
                style = GlyphStyle.APP_LABEL_16,
            )

            else -> {
                drawScrollableApps(
                    buffer = buffer,
                    state = state,
                    layoutMetrics = layoutMetrics,
                    drawerApps = drawerApps,
                    drawerListScrollOffsetPx = drawerListScrollOffsetPx,
                )
            }
        }
    }

    private fun resolveDrawerApps(state: LauncherState): List<AppEntry> {
        if (state.drawerVisibleApps.isNotEmpty()) {
            return state.drawerVisibleApps
        }
        if (state.drawerQuery.isNotBlank()) {
            return emptyList()
        }
        return state.apps
    }

    private fun drawDrawerHeader(
        buffer: PixelBuffer,
        screenProfile: ScreenProfile,
        state: LauncherState,
        alphaIndexModel: DrawerAlphaIndexModel,
        tick: Int,
    ) {
        if (state.isDrawerSearchFocused) {
            drawDrawerSearchHeader(
                buffer = buffer,
                screenProfile = screenProfile,
                query = state.drawerQuery,
                tick = tick,
            )
        } else {
            drawDrawerIdleHeader(
                buffer = buffer,
                screenProfile = screenProfile,
                currentTimeText = state.currentTimeText,
                currentLetter = DrawerAlphaIndexModel.letterAt(alphaIndexModel.selectedLetterIndex).toString(),
            )
        }
        drawHeaderBatteryDivider(
            buffer = buffer,
            screenProfile = screenProfile,
            batteryLevel = state.batteryLevel,
            isCharging = state.isCharging,
            chargeTick = tick,
        )
    }

    private fun drawDrawerIdleHeader(
        buffer: PixelBuffer,
        screenProfile: ScreenProfile,
        currentTimeText: String,
        currentLetter: String,
    ) {
        val style = GlyphStyle.UI_SMALL_10
        val leftPadding = LauncherHeaderLayout.horizontalPadding
        val headerY = LauncherHeaderLayout.rowY + LauncherHeaderLayout.textOffsetY
        val displayTime = currentTimeText.ifBlank { "--:--" }
        val rightText = "APPS:$currentLetter"
        val trimmedTime = pixelFontEngine.trimToWidth(
            text = displayTime,
            style = style,
            maxWidth = (screenProfile.logicalWidth / 3).coerceAtLeast(1),
        )
        val rightWidth = pixelFontEngine.measureText(rightText, style)
        val rightX = (screenProfile.logicalWidth - LauncherHeaderLayout.horizontalPadding - rightWidth)
            .coerceAtLeast(leftPadding)
        val leftWidth = pixelFontEngine.measureText(trimmedTime, style)
        val middleLeft = (leftPadding + leftWidth + LauncherHeaderLayout.titleGap).coerceAtLeast(leftPadding)
        val middleRightExclusive = (rightX - LauncherHeaderLayout.titleGap).coerceAtLeast(middleLeft)
        val middleWidth = (middleRightExclusive - middleLeft).coerceAtLeast(0)
        val searchText = pixelFontEngine.trimToWidth(
            text = "SEARCH",
            style = style,
            maxWidth = middleWidth,
        )
        if (trimmedTime.isNotEmpty()) {
            drawTextAsValue(
                buffer = buffer,
                text = trimmedTime,
                startX = leftPadding,
                startY = headerY,
                maxWidth = (screenProfile.logicalWidth - leftPadding).coerceAtLeast(0),
                style = style,
                value = PixelBuffer.ON,
            )
        }
        if (searchText.isNotEmpty()) {
            val searchWidth = pixelFontEngine.measureText(searchText, style)
            val searchX = middleLeft + ((middleWidth - searchWidth) / 2).coerceAtLeast(0)
            drawTextAsValue(
                buffer = buffer,
                text = searchText,
                startX = searchX,
                startY = headerY,
                maxWidth = middleWidth,
                style = style,
                value = PixelBuffer.ACCENT,
            )
        }
        drawTextAsValue(
            buffer = buffer,
            text = rightText,
            startX = rightX,
            startY = headerY,
            maxWidth = (screenProfile.logicalWidth - rightX - LauncherHeaderLayout.horizontalPadding).coerceAtLeast(0),
            style = style,
            value = PixelBuffer.ON,
        )
    }

    private fun drawDrawerSearchHeader(
        buffer: PixelBuffer,
        screenProfile: ScreenProfile,
        query: String,
        tick: Int,
    ) {
        val style = GlyphStyle.UI_SMALL_10
        val leftPadding = LauncherHeaderLayout.horizontalPadding
        val availableWidth = (screenProfile.logicalWidth - (leftPadding * 2)).coerceAtLeast(1)
        val textMaxWidth = (availableWidth - drawerCursorGapFromText - drawerCursorWidth).coerceAtLeast(1)
        val trimmed = pixelFontEngine.trimToWidth(
            text = query,
            style = style,
            maxWidth = textMaxWidth,
        )
        val cursorVisible = ((tick / drawerCursorBlinkFrames) % 2) == 0
        val textWidth = pixelFontEngine.measureText(trimmed, style)
        val cursorBlockWidth = if (cursorVisible) drawerCursorGapFromText + drawerCursorWidth else 0
        val contentWidth = (textWidth + cursorBlockWidth).coerceAtLeast(if (cursorVisible) drawerCursorWidth else 0)
        val startX = leftPadding + ((availableWidth - contentWidth) / 2).coerceAtLeast(0)
        val startY = LauncherHeaderLayout.rowY + LauncherHeaderLayout.textOffsetY
        if (trimmed.isNotEmpty()) {
            drawTextAsValue(
                buffer = buffer,
                text = trimmed,
                startX = startX,
                startY = startY,
                maxWidth = textMaxWidth,
                style = style,
                value = PixelBuffer.ON,
            )
        }
        if (cursorVisible) {
            val cursorX = (startX + textWidth + if (trimmed.isNotEmpty()) drawerCursorGapFromText else 0)
                .coerceIn(leftPadding, (screenProfile.logicalWidth - LauncherHeaderLayout.horizontalPadding - 1).coerceAtLeast(leftPadding))
            val cursorTop = (startY + drawerCursorTopInset).coerceIn(0, buffer.height - 1)
            val cursorBottom = (startY + style.cellHeight - drawerCursorBottomInset)
                .coerceIn(cursorTop, buffer.height - 1)
            drawVerticalLine(
                buffer = buffer,
                x = cursorX,
                startY = cursorTop,
                endY = cursorBottom,
                value = PixelBuffer.ACCENT,
            )
        }
    }

    private fun drawScrollableApps(
        buffer: PixelBuffer,
        state: LauncherState,
        layoutMetrics: AppListLayoutMetrics,
        drawerApps: List<AppEntry>,
        drawerListScrollOffsetPx: Int,
    ) {
        if (drawerApps.isEmpty()) {
            return
        }
        val listClipTop = layoutMetrics.listStartY
        val listClipBottomExclusive = layoutMetrics.listStartY + layoutMetrics.railHeight
        val safeSelectedIndex = state.selectedIndex.coerceIn(0, drawerApps.lastIndex)
        val safeListStartIndex = state.listStartIndex.coerceIn(0, drawerApps.lastIndex)
        val textAreaWidth = drawerTextAreaWidth(
            state = state,
            layoutMetrics = layoutMetrics,
        )
        val remainingRows = drawerApps.size - safeListStartIndex
        val rowRange = if (drawerListScrollOffsetPx == 0) {
            0 until remainingRows
        } else {
            -1 until remainingRows
        }
        for (row in rowRange) {
            val appIndex = safeListStartIndex + row
            if (appIndex !in drawerApps.indices) {
                continue
            }
            val appEntry = drawerApps[appIndex]
            val rowTop = layoutMetrics.listStartY + (row * layoutMetrics.rowHeight) + drawerListScrollOffsetPx

            val displayLabel = LabelFormatter.displayLabel(appEntry.label)
            val trimmedLabel = pixelFontEngine.trimToWidth(
                text = displayLabel,
                style = GlyphStyle.APP_LABEL_16,
                maxWidth = textAreaWidth,
            )
            val textStartX = drawerTextStartX(
                text = trimmedLabel,
                layoutMetrics = layoutMetrics,
                textAreaWidth = textAreaWidth,
                alignment = state.drawerListAlignment,
            )

            drawTextAsValueClipped(
                buffer = buffer,
                text = trimmedLabel,
                startX = textStartX,
                startY = rowTop + layoutMetrics.labelYInset,
                maxWidth = textAreaWidth,
                style = GlyphStyle.APP_LABEL_16,
                value = PixelBuffer.ON,
                clipTop = listClipTop,
                clipBottomExclusive = listClipBottomExclusive,
            )
        }
    }

    private fun drawerTextAreaWidth(
        state: LauncherState,
        layoutMetrics: AppListLayoutMetrics,
    ): Int {
        if (state.isDrawerSearchFocused || state.drawerListAlignment != DrawerListAlignment.LEFT) {
            return layoutMetrics.maxTextWidth
        }
        return (layoutMetrics.hiddenRailLeft - layoutMetrics.textX - 1)
            .coerceAtLeast(8)
            .coerceAtMost(layoutMetrics.maxTextWidth)
    }

    private fun drawerTextStartX(
        text: String,
        layoutMetrics: AppListLayoutMetrics,
        textAreaWidth: Int,
        alignment: DrawerListAlignment,
    ): Int {
        if (text.isEmpty()) {
            return layoutMetrics.textX
        }
        val textWidth = pixelFontEngine.measureText(text, GlyphStyle.APP_LABEL_16)
            .coerceAtMost(textAreaWidth)
            .coerceAtLeast(0)
        val horizontalOffset = when (alignment) {
            DrawerListAlignment.LEFT -> 0
            DrawerListAlignment.CENTER -> ((textAreaWidth - textWidth) / 2).coerceAtLeast(0)
            DrawerListAlignment.RIGHT -> (textAreaWidth - textWidth).coerceAtLeast(0)
        }
        return layoutMetrics.textX + horizontalOffset
    }

    private fun drawSettings(
        buffer: PixelBuffer,
        state: LauncherState,
        screenProfile: ScreenProfile,
        animationState: LauncherAnimationState,
    ) {
        val layout = SettingsMenuLayout.metrics(screenProfile)
        drawHeader(
            buffer = buffer,
            screenProfile = screenProfile,
            state = state,
            titleCandidates = listOf("SETTINGS", "SETTING", "SET"),
            chargeTick = animationState.headerChargeTick,
        )

        val rows = SettingsMenuModel.rows(state, screenProfile)
        rows.forEachIndexed { rowIndex, row ->
            drawSettingsRow(
                buffer = buffer,
                layout = layout,
                row = row,
                rowIndex = rowIndex,
                selected = rowIndex == state.settingsSelectedIndex,
            )
        }
    }

    private fun drawSettingsRow(
        buffer: PixelBuffer,
        layout: SettingsMenuLayoutMetrics,
        row: SettingsMenuRow,
        rowIndex: Int,
        selected: Boolean,
    ) {
        val rowTop = layout.firstRowY + (rowIndex * layout.rowHeight)
        val stableValueText = SettingsMenuModel.displayValue(row)
        val stableValue = pixelFontEngine.trimToWidth(
            text = stableValueText,
            style = GlyphStyle.UI_SMALL_10,
            maxWidth = layout.rowMaxTextWidth,
        )
        val stableValueWidth = pixelFontEngine.measureText(stableValue, GlyphStyle.UI_SMALL_10)
        val stableValueStartX = (layout.rowValueRightX - stableValueWidth).coerceAtLeast(layout.rowTextX)
        val titleMaxWidth = if (stableValue.isEmpty()) {
            layout.rowMaxTextWidth
        } else {
            (stableValueStartX - layout.rowTextX - layout.rowMinGap).coerceAtLeast(0)
        }
        val trimmedTitle = pixelFontEngine.trimToWidth(
            text = row.title,
            style = GlyphStyle.UI_SMALL_10,
            maxWidth = titleMaxWidth,
        )

        if (trimmedTitle.isNotEmpty()) {
            pixelFontEngine.drawText(
                buffer = buffer,
                text = trimmedTitle,
                startX = layout.rowTextX,
                startY = rowTop + layout.rowTextYOffset,
                maxWidth = titleMaxWidth,
                style = GlyphStyle.UI_SMALL_10,
            )
        }

        if (stableValue.isNotEmpty()) {
            drawTextAsValue(
                buffer = buffer,
                text = stableValue,
                startX = stableValueStartX,
                startY = rowTop + layout.rowTextYOffset,
                maxWidth = stableValueWidth.coerceAtLeast(1),
                style = GlyphStyle.UI_SMALL_10,
                value = if (selected) PixelBuffer.ACCENT else PixelBuffer.ON,
            )
        }
    }

    private fun drawDiagnostics(
        buffer: PixelBuffer,
        state: LauncherState,
        screenProfile: ScreenProfile,
        animationState: LauncherAnimationState,
    ) {
        val layout = SettingsMenuLayout.metrics(screenProfile)
        drawHeader(
            buffer = buffer,
            screenProfile = screenProfile,
            state = state,
            titleCandidates = listOf("ADVANCED", "ADV"),
            chargeTick = animationState.headerChargeTick,
        )

        DiagnosticsModel.lines(state, screenProfile).forEachIndexed { rowIndex, line ->
            drawDiagnosticsLine(
                buffer = buffer,
                layout = layout,
                line = line,
                rowIndex = rowIndex,
            )
        }
    }

    private fun drawDiagnosticsLine(
        buffer: PixelBuffer,
        layout: SettingsMenuLayoutMetrics,
        line: DiagnosticsLine,
        rowIndex: Int,
    ) {
        val rowTop = layout.firstRowY + (rowIndex * layout.rowHeight)
        val valueWidth = pixelFontEngine.measureText(line.value, GlyphStyle.UI_SMALL_10)
        val valueStartX = (layout.rowValueRightX - valueWidth).coerceAtLeast(layout.rowTextX)
        val titleMaxWidth = (valueStartX - layout.rowTextX - layout.rowMinGap).coerceAtLeast(0)
        val trimmedTitle = pixelFontEngine.trimToWidth(line.title, GlyphStyle.UI_SMALL_10, titleMaxWidth)
        val trimmedValue = pixelFontEngine.trimToWidth(line.value, GlyphStyle.UI_SMALL_10, layout.rowMaxTextWidth)

        if (trimmedTitle.isNotEmpty()) {
            pixelFontEngine.drawText(
                buffer = buffer,
                text = trimmedTitle,
                startX = layout.rowTextX,
                startY = rowTop + layout.rowTextYOffset,
                maxWidth = titleMaxWidth,
                style = GlyphStyle.UI_SMALL_10,
            )
        }
        if (trimmedValue.isNotEmpty()) {
            drawTextAsValue(
                buffer = buffer,
                text = trimmedValue,
                startX = (layout.rowValueRightX - pixelFontEngine.measureText(trimmedValue, GlyphStyle.UI_SMALL_10))
                    .coerceAtLeast(layout.rowTextX),
                startY = rowTop + layout.rowTextYOffset,
                maxWidth = layout.rowMaxTextWidth,
                style = GlyphStyle.UI_SMALL_10,
                value = PixelBuffer.ACCENT,
            )
        }
    }

    private fun drawIdle(buffer: PixelBuffer, state: LauncherState, screenProfile: ScreenProfile) {
        drawIdleParticles(buffer, state)
        val timeY = ((screenProfile.logicalHeight - GlyphStyle.APP_LABEL_16.cellHeight) / 2).coerceAtLeast(0)
        drawCenteredText(
            buffer = buffer,
            text = state.currentTimeText.ifBlank { "--:--" },
            y = timeY,
            style = GlyphStyle.APP_LABEL_16,
        )
    }

    private fun drawBootSequence(buffer: PixelBuffer, screenProfile: ScreenProfile, animation: BootSequenceAnimation) {
        when (animation.stage) {
            BootSequenceStage.SCAN -> {
                val revealRows = (buffer.height.toFloat() * animation.revealProgress).toInt()
                    .coerceIn(0, buffer.height)
                for (y in 0 until revealRows) {
                    drawHorizontalLine(
                        buffer = buffer,
                        startX = 0,
                        endX = buffer.width - 1,
                        y = y,
                        value = if (y == revealRows - 1) PixelBuffer.ACCENT else PixelBuffer.ON,
                    )
                }
            }

            BootSequenceStage.CHECK -> {
                drawCenteredText(buffer, "BOOT", 18, GlyphStyle.APP_LABEL_16)
                val lines = listOf("DISPLAY OK", "GLYPH OK", "APP LINK", "POWER OK", "IDLE OK", "READY")
                repeat(animation.checkLinesVisible.coerceAtMost(lines.size)) { lineIndex ->
                    drawCenteredText(
                        buffer = buffer,
                        text = lines[lineIndex],
                        y = 42 + (lineIndex * 10),
                        style = GlyphStyle.UI_SMALL_10,
                    )
                }
            }

            BootSequenceStage.READY -> {
                drawCenteredText(buffer, "READY", 32, GlyphStyle.APP_LABEL_16)
                if (animation.readyVisible) {
                    drawCenteredTextInBounds(
                        buffer = buffer,
                        text = "PIXEL TERMINAL",
                        left = 4,
                        top = 54,
                        width = buffer.width - 8,
                        style = GlyphStyle.UI_SMALL_10,
                        value = PixelBuffer.ACCENT,
                    )
                }
            }
        }
    }

    private fun drawHeader(
        buffer: PixelBuffer,
        screenProfile: ScreenProfile,
        state: LauncherState,
        titleCandidates: List<String>,
        chargeTick: Int,
    ) {
        val displayTime = state.currentTimeText.ifBlank { "--:--" }
        val timeMaxWidth = (screenProfile.logicalWidth - (LauncherHeaderLayout.horizontalPadding * 2) - LauncherHeaderLayout.titleGap)
            .coerceAtLeast(0)
        val trimmedTime = pixelFontEngine.trimToWidth(
            text = displayTime,
            style = GlyphStyle.UI_SMALL_10,
            maxWidth = timeMaxWidth,
        )
        val timeWidth = pixelFontEngine.measureText(trimmedTime, GlyphStyle.UI_SMALL_10)
        val titleText = pickHeaderTitle(
            availableWidth = (
                screenProfile.logicalWidth -
                    LauncherHeaderLayout.horizontalPadding -
                    timeWidth -
                    LauncherHeaderLayout.horizontalPadding -
                    LauncherHeaderLayout.titleGap
                ).coerceAtLeast(0),
            titleCandidates = titleCandidates,
        )

        if (trimmedTime.isNotEmpty()) {
            pixelFontEngine.drawText(
                buffer = buffer,
                text = trimmedTime,
                startX = LauncherHeaderLayout.horizontalPadding,
                startY = LauncherHeaderLayout.rowY + LauncherHeaderLayout.textOffsetY,
                maxWidth = timeMaxWidth,
                style = GlyphStyle.UI_SMALL_10,
            )
        }

        if (titleText.isNotEmpty()) {
            val titleWidth = pixelFontEngine.measureText(titleText, GlyphStyle.UI_SMALL_10)
            val titleX = (screenProfile.logicalWidth - titleWidth - LauncherHeaderLayout.horizontalPadding)
                .coerceAtLeast(timeWidth + LauncherHeaderLayout.horizontalPadding + LauncherHeaderLayout.titleGap)
            pixelFontEngine.drawText(
                buffer = buffer,
                text = titleText,
                startX = titleX,
                startY = LauncherHeaderLayout.rowY + LauncherHeaderLayout.textOffsetY,
                maxWidth = (screenProfile.logicalWidth - titleX - LauncherHeaderLayout.horizontalPadding).coerceAtLeast(0),
                style = GlyphStyle.UI_SMALL_10,
            )
        }

        drawHeaderBatteryDivider(
            buffer = buffer,
            screenProfile = screenProfile,
            batteryLevel = state.batteryLevel,
            isCharging = state.isCharging,
            chargeTick = chargeTick,
        )
    }

    private fun drawHeaderBatteryDivider(
        buffer: PixelBuffer,
        screenProfile: ScreenProfile,
        batteryLevel: Int,
        isCharging: Boolean,
        chargeTick: Int,
    ) {
        val indicator = HeaderBatteryIndicatorModel.fromBatteryLevel(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            logicalWidth = screenProfile.logicalWidth,
            chargeTick = chargeTick,
        )

        drawHorizontalLine(
            buffer = buffer,
            startX = indicator.lineStartX,
            endX = indicator.lineEndX,
            y = LauncherHeaderLayout.dividerY,
            value = PixelBuffer.OFF,
        )

        val filledEndX = indicator.filledEndX
        if (filledEndX != null) {
            drawHorizontalLine(
                buffer = buffer,
                startX = indicator.lineStartX,
                endX = filledEndX,
                y = LauncherHeaderLayout.dividerY,
                value = PixelBuffer.ON,
            )
        }

        val chargePixelX = indicator.chargePixelX
        if (chargePixelX != null) {
            buffer.setPixel(chargePixelX, LauncherHeaderLayout.dividerY, PixelBuffer.ACCENT)
        }
    }

    private fun applyDrawerRevealOverlay(buffer: PixelBuffer, animation: DrawerRevealAnimation?) {
        val revealAnimation = animation ?: return
        val revealRows = (buffer.height.toFloat() * revealAnimation.revealProgress).toInt()
            .coerceIn(0, buffer.height)
        for (y in revealRows until buffer.height) {
            for (x in 0 until buffer.width) {
                buffer.setPixel(x, y, PixelBuffer.OFF)
            }
        }
    }

    private fun applyLaunchShutterOverlay(buffer: PixelBuffer, animation: LaunchShutterAnimation?) {
        val shutter = animation ?: return
        val closedRows = ((buffer.height / 2f) * shutter.closeProgress).toInt().coerceAtLeast(1)
        for (y in 0 until closedRows) {
            drawHorizontalLine(buffer, 0, buffer.width - 1, y, PixelBuffer.ACCENT)
        }
        for (y in (buffer.height - closedRows) until buffer.height) {
            drawHorizontalLine(buffer, 0, buffer.width - 1, y, PixelBuffer.ACCENT)
        }
    }

    private fun drawIdleParticles(buffer: PixelBuffer, state: LauncherState) {
        val fluidState = state.idleFluidState
        val mask = fluidState.litMask
        if (mask.isEmpty()) {
            return
        }

        val maxPixels = (buffer.width * buffer.height).coerceAtMost(mask.size)
        for (index in 0 until maxPixels) {
            if (!mask[index]) {
                continue
            }
            val x = index % buffer.width
            val y = index / buffer.width
            buffer.setPixel(x = x, y = y, value = PixelBuffer.ON)
        }
    }

    private fun drawCenteredLabel(buffer: PixelBuffer, text: String, y: Int, maxWidth: Int) {
        val trimmed = pixelFontEngine.trimToWidth(
            text = text,
            style = GlyphStyle.APP_LABEL_16,
            maxWidth = maxWidth,
        )
        val textWidth = pixelFontEngine.measureText(trimmed, GlyphStyle.APP_LABEL_16)
        val startX = ((buffer.width - textWidth) / 2).coerceAtLeast(0)
        pixelFontEngine.drawText(
            buffer = buffer,
            text = trimmed,
            startX = startX,
            startY = y,
            maxWidth = maxWidth,
            style = GlyphStyle.APP_LABEL_16,
        )
    }

    private fun drawCenteredText(buffer: PixelBuffer, text: String, y: Int, style: GlyphStyle) {
        val trimmed = pixelFontEngine.trimToWidth(text, style, buffer.width - 2)
        val textWidth = pixelFontEngine.measureText(trimmed, style)
        val startX = ((buffer.width - textWidth) / 2).coerceAtLeast(0)
        pixelFontEngine.drawText(
            buffer = buffer,
            text = trimmed,
            startX = startX,
            startY = y,
            maxWidth = buffer.width,
            style = style,
        )
    }

    private fun drawCenteredTextInBounds(
        buffer: PixelBuffer,
        text: String,
        left: Int,
        top: Int,
        width: Int,
        style: GlyphStyle,
        value: Byte = PixelBuffer.ON,
    ) {
        val trimmed = pixelFontEngine.trimToWidth(text, style, width)
        val textWidth = pixelFontEngine.measureText(trimmed, style)
        val startX = left + ((width - textWidth) / 2).coerceAtLeast(0)
        drawTextAsValue(
            buffer = buffer,
            text = trimmed,
            startX = startX,
            startY = top,
            maxWidth = width,
            style = style,
            value = value,
        )
    }

    private fun drawTextAsValue(
        buffer: PixelBuffer,
        text: String,
        startX: Int,
        startY: Int,
        maxWidth: Int,
        style: GlyphStyle,
        value: Byte,
    ) {
        drawTextAsValueClipped(
            buffer = buffer,
            text = text,
            startX = startX,
            startY = startY,
            maxWidth = maxWidth,
            style = style,
            value = value,
            clipTop = 0,
            clipBottomExclusive = buffer.height,
        )
    }

    private fun drawTextAsValueClipped(
        buffer: PixelBuffer,
        text: String,
        startX: Int,
        startY: Int,
        maxWidth: Int,
        style: GlyphStyle,
        value: Byte,
        clipTop: Int,
        clipBottomExclusive: Int,
    ) {
        if (text.isEmpty() || maxWidth <= 0) {
            return
        }
        val safeClipTop = clipTop.coerceAtLeast(0)
        val safeClipBottomExclusive = clipBottomExclusive.coerceIn(safeClipTop, buffer.height)
        if (safeClipBottomExclusive <= safeClipTop) {
            return
        }
        val temp = PixelBuffer(
            width = maxWidth.coerceAtLeast(1),
            height = style.cellHeight,
        )
        pixelFontEngine.drawText(
            buffer = temp,
            text = text,
            startX = 0,
            startY = 0,
            maxWidth = temp.width,
            style = style,
        )
        for (y in 0 until temp.height) {
            val targetY = startY + y
            if (targetY !in safeClipTop until safeClipBottomExclusive) {
                continue
            }
            for (x in 0 until temp.width) {
                if (temp.getPixel(x, y) == PixelBuffer.ON) {
                    buffer.setPixel(startX + x, targetY, value)
                }
            }
        }
    }

    private fun drawMarqueeTextAsValue(
        buffer: PixelBuffer,
        text: String,
        startX: Int,
        startY: Int,
        maxWidth: Int,
        style: GlyphStyle,
        value: Byte,
        tick: Int,
    ) {
        if (text.isEmpty() || maxWidth <= 0) {
            return
        }

        val staticWidth = pixelFontEngine.measureText(text, style)
        if (staticWidth <= maxWidth) {
            drawTextAsValue(
                buffer = buffer,
                text = text,
                startX = startX,
                startY = startY,
                maxWidth = maxWidth,
                style = style,
                value = value,
            )
            return
        }

        val separator = "   "
        val scrollBase = text + separator
        val loopText = scrollBase + text
        val scrollSpan = pixelFontEngine.measureText(scrollBase, style).coerceAtLeast(1)
        val loopWidth = pixelFontEngine.measureText(loopText, style).coerceAtLeast(maxWidth)
        val slowTick = tick / marqueeStepFramesPerPixel
        val offset = slowTick % scrollSpan

        val strip = PixelBuffer(
            width = loopWidth,
            height = style.cellHeight,
        )
        pixelFontEngine.drawText(
            buffer = strip,
            text = loopText,
            startX = 0,
            startY = 0,
            maxWidth = strip.width,
            style = style,
        )

        for (y in 0 until strip.height) {
            for (x in 0 until maxWidth) {
                val sourceX = x + offset
                if (sourceX >= strip.width) {
                    continue
                }
                if (strip.getPixel(sourceX, y) == PixelBuffer.ON) {
                    buffer.setPixel(startX + x, startY + y, value)
                }
            }
        }
    }

    private companion object {
        const val marqueeStepFramesPerPixel: Int = 5
        const val drawerCursorBlinkFrames: Int = 12
        const val drawerCursorWidth: Int = 1
        const val drawerCursorGapFromText: Int = 1
        const val drawerCursorTopInset: Int = 0
        const val drawerCursorBottomInset: Int = 1
        const val drawerRailBaseEmphasisCopies: Int = 1
        const val drawerRailSlidingEmphasisCopies: Int = 3
        const val pagerSettingsIndex: Int = 0
        const val pagerHomeIndex: Int = 1
        const val pagerAppsIndex: Int = 2
        const val pagerCompositionThresholdPx: Float = 0.5f
    }

    private fun drawHorizontalLine(
        buffer: PixelBuffer,
        startX: Int,
        endX: Int,
        y: Int,
        value: Byte = PixelBuffer.ON,
    ) {
        if (endX < startX) {
            return
        }
        for (drawX in startX..endX) {
            buffer.setPixel(drawX, y, value)
        }
    }

    private fun drawVerticalLine(
        buffer: PixelBuffer,
        x: Int,
        startY: Int,
        endY: Int,
        value: Byte = PixelBuffer.ON,
    ) {
        if (endY < startY) {
            return
        }
        for (drawY in startY..endY) {
            buffer.setPixel(x, drawY, value)
        }
    }

    private fun pickHeaderTitle(
        availableWidth: Int,
        titleCandidates: List<String>,
    ): String {
        if (titleCandidates.isEmpty()) {
            return ""
        }
        return titleCandidates.firstOrNull { candidate ->
            pixelFontEngine.measureText(candidate, GlyphStyle.UI_SMALL_10) <= availableWidth
        } ?: ""
    }

    private fun appDrawerHeaderTitles(drawerQuery: String): List<String> {
        if (drawerQuery.isNotBlank()) {
            return listOf("SEARCH", "SRCH")
        }
        return listOf("APPS", "APP")
    }

}

package com.purride.pixellauncherv2.render

import com.purride.pixellauncherv2.launcher.AppEntry
import com.purride.pixellauncherv2.launcher.AppDrawerIndexModel
import com.purride.pixellauncherv2.launcher.AppListLayout
import com.purride.pixellauncherv2.launcher.AppListLayoutMetrics
import com.purride.pixellauncherv2.launcher.DiagnosticsLine
import com.purride.pixellauncherv2.launcher.DiagnosticsModel
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
    ): PixelBuffer {
        val buffer = PixelBuffer(
            width = screenProfile.logicalWidth,
            height = screenProfile.logicalHeight,
        )
        buffer.clear()

        val bootSequence = animationState.bootSequence
        if (bootSequence != null) {
            drawBootSequence(buffer, screenProfile, bootSequence)
            return buffer
        }

        when (state.mode) {
            LauncherMode.HOME -> drawHome(buffer, state, screenProfile, animationState)
            LauncherMode.APP_DRAWER -> drawAppDrawer(buffer, state, screenProfile, animationState)
            LauncherMode.SETTINGS -> drawSettings(buffer, state, screenProfile, animationState)
            LauncherMode.DIAGNOSTICS -> drawDiagnostics(buffer, state, screenProfile, animationState)
            LauncherMode.IDLE -> drawIdle(buffer, state, screenProfile)
        }

        if (state.mode == LauncherMode.APP_DRAWER) {
            applyDrawerRevealOverlay(buffer, animationState.drawerReveal)
        }
        applyLaunchShutterOverlay(buffer, animationState.launchShutter)
        return buffer
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
            text = state.currentDateText.ifBlank { "--- --" },
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

        val weekdayLine = pixelFontEngine.trimToWidth(
            text = state.currentWeekdayText.ifBlank { "-------" },
            style = GlyphStyle.UI_SMALL_10,
            maxWidth = layoutMetrics.innerWidth,
        )
        if (weekdayLine.isNotEmpty()) {
            drawTextAsValue(
                buffer = buffer,
                text = weekdayLine,
                startX = layoutMetrics.innerLeft,
                startY = layoutMetrics.weekdayY,
                maxWidth = layoutMetrics.innerWidth,
                style = GlyphStyle.UI_SMALL_10,
                value = PixelBuffer.ON,
            )
        }

        val fixedInfoLines = listOf(
            "ALARM ${state.nextAlarmText.ifBlank { "--:--" }}",
            "CALL ${state.missedCallCount}  SMS ${state.unreadSmsCount}",
            "RAIN ${state.rainHintText.ifBlank { "--" }}",
            "24H ${state.screenOnTrack24h.ifBlank { "........" }}",
            "YDAY ${state.yesterdayScreenOnCount}  ${state.terminalStatusText.ifBlank { "READY" }}",
        )
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
    ) {
        val layoutMetrics = AppListLayout.metrics(screenProfile)
        val drawerIndexModel = AppDrawerIndexModel.create(
            apps = state.apps,
            visibleRows = layoutMetrics.visibleRows,
            selectedIndex = state.selectedIndex,
        )
        drawHeader(
            buffer = buffer,
            screenProfile = screenProfile,
            state = state,
            titleCandidates = appDrawerHeaderTitles(drawerIndexModel),
            chargeTick = animationState.headerChargeTick,
        )

        when {
            state.isLoading -> drawCenteredText(
                buffer = buffer,
                text = "Loading",
                y = layoutMetrics.listStartY,
                style = GlyphStyle.APP_LABEL_16,
            )

            state.apps.isEmpty() -> drawCenteredText(
                buffer = buffer,
                text = "No apps",
                y = layoutMetrics.listStartY,
                style = GlyphStyle.APP_LABEL_16,
            )

            else -> {
                drawApps(
                    buffer = buffer,
                    state = state,
                    layoutMetrics = layoutMetrics,
                    drawerIndexModel = drawerIndexModel,
                )
                drawAppDrawerIndexRail(
                    buffer = buffer,
                    layoutMetrics = layoutMetrics,
                    drawerIndexModel = drawerIndexModel,
                )
            }
        }
    }

    private fun drawApps(
        buffer: PixelBuffer,
        state: LauncherState,
        layoutMetrics: AppListLayoutMetrics,
        drawerIndexModel: AppDrawerIndexModel,
    ) {
        val listEndX = (layoutMetrics.textX + layoutMetrics.listWidth - 1).coerceAtMost(buffer.width - 1)
        drawerIndexModel.currentPageApps.forEachIndexed { row, appEntry ->
            val rowTop = layoutMetrics.listStartY + (row * layoutMetrics.rowHeight)
            if (row == drawerIndexModel.currentPageSelectedRow) {
                drawHorizontalLine(
                    buffer = buffer,
                    startX = 1,
                    endX = listEndX,
                    y = rowTop + layoutMetrics.labelFontHeight,
                    value = PixelBuffer.ACCENT,
                )
            }

            val displayLabel = LabelFormatter.displayLabel(appEntry.label)
            val trimmedLabel = pixelFontEngine.trimToWidth(
                text = displayLabel,
                style = GlyphStyle.APP_LABEL_16,
                maxWidth = layoutMetrics.maxTextWidth,
            )

            pixelFontEngine.drawText(
                buffer = buffer,
                text = trimmedLabel,
                startX = layoutMetrics.textX,
                startY = rowTop + layoutMetrics.labelYInset,
                maxWidth = layoutMetrics.maxTextWidth,
                style = GlyphStyle.APP_LABEL_16,
            )
        }
    }

    private fun drawAppDrawerIndexRail(
        buffer: PixelBuffer,
        layoutMetrics: AppListLayoutMetrics,
        drawerIndexModel: AppDrawerIndexModel,
    ) {
        if (layoutMetrics.indexRailWidth <= 0 || drawerIndexModel.pageCount <= 1) {
            return
        }

        val railLeft = layoutMetrics.indexRailLeft
        val railRight = railLeft + layoutMetrics.indexRailWidth - 1
        val railTop = layoutMetrics.railTop
        val railHeight = layoutMetrics.railHeight.coerceAtLeast(1)
        val numericAnchorCapacity = (railHeight / GlyphStyle.UI_SMALL_10.cellHeight).coerceAtLeast(1)
        val pageCount = drawerIndexModel.pageCount

        if (pageCount <= numericAnchorCapacity) {
            val anchorSpacing = railHeight.toFloat() / pageCount.toFloat()
            for (pageIndex in 0 until pageCount) {
                val labelTop = railTop + (pageIndex * anchorSpacing).toInt()
                val label = formatDrawerPageAnchor(pageIndex + 1, pageCount)
                drawCenteredTextInBounds(
                    buffer = buffer,
                    text = label,
                    left = railLeft,
                    top = labelTop,
                    width = layoutMetrics.indexRailWidth,
                    style = GlyphStyle.UI_SMALL_10,
                    value = if (pageIndex == drawerIndexModel.currentPageIndex) PixelBuffer.ACCENT else PixelBuffer.ON,
                )
            }
            return
        }

        for (pageIndex in 0 until pageCount) {
            val anchorY = railTop + (((pageIndex + 0.5f) * railHeight) / pageCount).toInt()
                .coerceIn(railTop, railTop + railHeight - 1)
            if (pageIndex == drawerIndexModel.currentPageIndex) {
                drawHorizontalLine(
                    buffer = buffer,
                    startX = railLeft,
                    endX = railRight,
                    y = anchorY,
                    value = PixelBuffer.ACCENT,
                )
            } else {
                drawHorizontalLine(
                    buffer = buffer,
                    startX = railRight - 1,
                    endX = railRight,
                    y = anchorY,
                    value = PixelBuffer.ON,
                )
            }
        }
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

        if (selected) {
            buffer.setPixel(0, rowTop + 4, PixelBuffer.ACCENT)
            buffer.setPixel(buffer.width - 1, rowTop + 4, PixelBuffer.ACCENT)
        }

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
        if (revealRows in 0 until buffer.height) {
            drawHorizontalLine(
                buffer = buffer,
                startX = 0,
                endX = buffer.width - 1,
                y = revealRows,
                value = PixelBuffer.ACCENT,
            )
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
        if (text.isEmpty() || maxWidth <= 0) {
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
            for (x in 0 until temp.width) {
                if (temp.getPixel(x, y) == PixelBuffer.ON) {
                    buffer.setPixel(startX + x, startY + y, value)
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

    private fun appDrawerHeaderTitles(drawerIndexModel: AppDrawerIndexModel): List<String> {
        if (drawerIndexModel.pageCount <= 0) {
            return listOf("APPS", "APP")
        }

        val digits = drawerIndexModel.pageCount.toString().length.coerceAtLeast(2)
        val currentPage = (drawerIndexModel.currentPageIndex + 1).toString().padStart(digits, '0')
        val totalPages = drawerIndexModel.pageCount.toString().padStart(digits, '0')
        val pageSuffix = "$currentPage/$totalPages"

        return linkedSetOf(
            "APPS $pageSuffix",
            "APP $pageSuffix",
            pageSuffix,
        ).toList()
    }

    private fun formatDrawerPageAnchor(pageNumber: Int, pageCount: Int): String {
        val digits = pageCount.toString().length.coerceAtLeast(2)
        return pageNumber.toString().padStart(digits, '0')
    }
}

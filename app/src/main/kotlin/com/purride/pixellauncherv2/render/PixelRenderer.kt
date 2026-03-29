package com.purride.pixellauncherv2.render

import android.provider.Telephony
import com.purride.pixellauncherv2.launcher.AppEntry
import com.purride.pixellauncherv2.launcher.DrawerAlphaIndexModel
import com.purride.pixellauncherv2.launcher.AppListLayout
import com.purride.pixellauncherv2.launcher.AppListLayoutMetrics
import com.purride.pixellauncherv2.launcher.DiagnosticsLine
import com.purride.pixellauncherv2.launcher.DiagnosticsModel
import com.purride.pixellauncherv2.launcher.DrawerListAlignment
import com.purride.pixellauncherv2.launcher.HomeContextCard
import com.purride.pixellauncherv2.launcher.HomeLayout
import com.purride.pixellauncherv2.launcher.HomeFixedInfoModel
import com.purride.pixellauncherv2.launcher.HomeLayoutMetrics
import com.purride.pixellauncherv2.launcher.LauncherHeaderLayout
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import com.purride.pixellauncherv2.launcher.SettingsMenuLayout
import com.purride.pixellauncherv2.launcher.SettingsMenuLayoutMetrics
import com.purride.pixellauncherv2.launcher.SettingsMenuModel
import com.purride.pixellauncherv2.launcher.SettingsMenuRow
import com.purride.pixellauncherv2.launcher.SmsLayout
import com.purride.pixellauncherv2.launcher.TextListViewport
import com.purride.pixellauncherv2.launcher.TextListSupport
import com.purride.pixellauncherv2.util.LabelFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PixelRenderer(
    private val pixelFontEngine: PixelFontEngine,
) {

    /**
     * 在 Idle 动态遮罩上挖掉中央时间区域，避免充电/流体效果覆盖时间主体。
     */
    fun carveIdleTimeCutout(
        frame: IdleMaskFrame,
        currentTimeText: String,
        screenProfile: ScreenProfile,
    ): IdleMaskFrame {
        val displayTime = currentTimeText.ifBlank { "--:--" }
        if (frame.width <= 0 || frame.height <= 0 || frame.mask.isEmpty()) {
            return frame
        }
        val logicalBuffer = PixelBuffer(
            width = screenProfile.logicalWidth.coerceAtLeast(1),
            height = screenProfile.logicalHeight.coerceAtLeast(1),
        )
        val timeY = ((screenProfile.logicalHeight - GlyphStyle.APP_LABEL_16.cellHeight) / 2).coerceAtLeast(0)
        drawCenteredText(
            buffer = logicalBuffer,
            text = displayTime,
            y = timeY,
            style = GlyphStyle.APP_LABEL_16,
        )

        val carvedMask = frame.mask.copyOf()
        for (y in 0 until logicalBuffer.height) {
            val maskY = ((y.toFloat() / logicalBuffer.height.toFloat()) * frame.height)
                .toInt()
                .coerceIn(0, frame.height - 1)
            for (x in 0 until logicalBuffer.width) {
                if (logicalBuffer.getPixel(x, y) == PixelBuffer.OFF) {
                    continue
                }
                val maskX = ((x.toFloat() / logicalBuffer.width.toFloat()) * frame.width)
                    .toInt()
                    .coerceIn(0, frame.width - 1)
                carvedMask[(maskY * frame.width) + maskX] = 0x00
            }
        }

        return frame.copy(mask = carvedMask)
    }

    /**
     * 把当前启动器状态渲染成一帧像素缓冲。
     *
     * 这是 Activity 使用的唯一主入口，会把 boot、分页快照、页面内容和启动遮罩统一合成为一帧。
     */
    fun render(
        state: LauncherState,
        screenProfile: ScreenProfile,
        animationState: LauncherAnimationState,
        pagerSnapshot: HorizontalPageSnapshot? = null,
        smsPageSnapshot: HorizontalPageSnapshot? = null,
        smsBodyScrollOffsetPx: Int = 0,
        drawerListScrollOffsetPx: Int = 0,
        settingsListScrollOffsetPx: Int = 0,
    ): PixelBuffer {
        return RenderPerfLogger.measure("renderer.render.${state.mode.name}") {
            val bootBuffer = PixelBuffer(
                width = screenProfile.logicalWidth,
                height = screenProfile.logicalHeight,
            )
            bootBuffer.clear()

            val bootSequence = animationState.bootSequence
            if (bootSequence != null) {
                drawBootSequence(bootBuffer, screenProfile, bootSequence)
                return@measure bootBuffer
            }

            val pageBuffer = if (smsPageSnapshot != null && state.mode == LauncherMode.SMS_INBOX) {
                renderSmsPageSnapshot(
                    state = state,
                    screenProfile = screenProfile,
                    animationState = animationState,
                    smsPageSnapshot = smsPageSnapshot,
                    smsBodyScrollOffsetPx = smsBodyScrollOffsetPx,
                )
            } else if (pagerSnapshot == null) {
                renderSingleMode(
                    state = state,
                    screenProfile = screenProfile,
                    animationState = animationState,
                    mode = state.mode,
                    smsBodyScrollOffsetPx = smsBodyScrollOffsetPx,
                    drawerListScrollOffsetPx = drawerListScrollOffsetPx,
                    settingsListScrollOffsetPx = settingsListScrollOffsetPx,
                )
            } else {
                renderPagerSnapshot(
                    state = state,
                    screenProfile = screenProfile,
                    animationState = animationState,
                    pagerSnapshot = pagerSnapshot,
                    smsBodyScrollOffsetPx = smsBodyScrollOffsetPx,
                    drawerListScrollOffsetPx = drawerListScrollOffsetPx,
                    settingsListScrollOffsetPx = settingsListScrollOffsetPx,
                )
            }
            applyLaunchShutterOverlay(pageBuffer, animationState.launchShutter)
            pageBuffer
        }
    }

    /** 在短信全文页左右切换时，同时渲染当前短信页与相邻短信页并合成横向滑动效果。 */
    private fun renderSmsPageSnapshot(
        state: LauncherState,
        screenProfile: ScreenProfile,
        animationState: LauncherAnimationState,
        smsPageSnapshot: HorizontalPageSnapshot,
        smsBodyScrollOffsetPx: Int,
    ): PixelBuffer {
        val anchorIndex = smsPageSnapshot.anchorPageIndex.coerceIn(0, (state.unreadSmsEntries.size - 1).coerceAtLeast(0))
        val anchorPage = renderSingleMode(
            state = state.copy(smsSelectedIndex = anchorIndex),
            screenProfile = screenProfile,
            animationState = animationState,
            mode = LauncherMode.SMS_INBOX,
            smsBodyScrollOffsetPx = smsBodyScrollOffsetPx,
        )

        val dragOffset = smsPageSnapshot.dragOffsetPx
        if (kotlin.math.abs(dragOffset) < pagerCompositionThresholdPx) {
            return anchorPage
        }
        val adjacentIndex = when {
            dragOffset < 0f -> (anchorIndex + 1).takeIf { it < state.unreadSmsEntries.size }
            dragOffset > 0f -> (anchorIndex - 1).takeIf { it >= 0 }
            else -> null
        } ?: return anchorPage
        val adjacentPage = renderSingleMode(
            state = state.copy(smsSelectedIndex = adjacentIndex),
            screenProfile = screenProfile,
            animationState = animationState,
            mode = LauncherMode.SMS_INBOX,
            smsBodyScrollOffsetPx = 0,
        )
        return HorizontalPageRenderer.compose(
            currentPage = anchorPage,
            adjacentPage = adjacentPage,
            dragOffsetPx = dragOffset,
            contentStartY = LauncherHeaderLayout.contentTop,
        )
    }

    /**
     * 只渲染 Idle 页的低频静态内容。
     *
     * 供 GL 动态遮罩模式使用，故意不包含流体粒子。
     */
    fun renderIdleStatic(
        state: LauncherState,
        screenProfile: ScreenProfile,
    ): PixelBuffer {
        return RenderPerfLogger.measure("renderer.renderIdleStatic") {
            val buffer = PixelBuffer(
                width = screenProfile.logicalWidth,
                height = screenProfile.logicalHeight,
            )
            buffer.clear()
            drawIdleStatic(
                buffer = buffer,
                state = state,
                screenProfile = screenProfile,
            )
            buffer
        }
    }

    /**
     * 在横向分页拖动或 settle 时，同时渲染锚点页和相邻页并完成合成。
     */
    private fun renderPagerSnapshot(
        state: LauncherState,
        screenProfile: ScreenProfile,
        animationState: LauncherAnimationState,
        pagerSnapshot: HorizontalPageSnapshot,
        smsBodyScrollOffsetPx: Int,
        drawerListScrollOffsetPx: Int,
        settingsListScrollOffsetPx: Int,
    ): PixelBuffer {
        val anchorMode = pagerModeForIndex(pagerSnapshot.anchorPageIndex) ?: state.mode
        val anchorPage = renderSingleMode(
            state = state,
            screenProfile = screenProfile,
            animationState = animationState,
            mode = anchorMode,
            smsBodyScrollOffsetPx = smsBodyScrollOffsetPx,
            drawerListScrollOffsetPx = drawerListScrollOffsetPx,
            settingsListScrollOffsetPx = settingsListScrollOffsetPx,
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
            smsBodyScrollOffsetPx = smsBodyScrollOffsetPx,
            drawerListScrollOffsetPx = drawerListScrollOffsetPx,
            settingsListScrollOffsetPx = settingsListScrollOffsetPx,
        )
        return HorizontalPageRenderer.compose(
            currentPage = anchorPage,
            adjacentPage = adjacentPage,
            dragOffsetPx = dragOffset,
            contentStartY = LauncherHeaderLayout.contentTop,
        )
    }

    /**
     * 在不做分页合成的前提下，单独渲染某一个页面模式。
     */
    private fun renderSingleMode(
        state: LauncherState,
        screenProfile: ScreenProfile,
        animationState: LauncherAnimationState,
        mode: LauncherMode,
        smsBodyScrollOffsetPx: Int = 0,
        drawerListScrollOffsetPx: Int = 0,
        settingsListScrollOffsetPx: Int = 0,
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
        val modeSettingsScrollOffset = if ((mode == LauncherMode.SETTINGS || mode == LauncherMode.SMS_INBOX || mode == LauncherMode.SMS_THREADS) &&
            (state.mode == LauncherMode.SETTINGS || state.mode == LauncherMode.SMS_INBOX || state.mode == LauncherMode.SMS_THREADS)
        ) {
            settingsListScrollOffsetPx
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
            LauncherMode.SETTINGS -> drawSettings(
                buffer = buffer,
                state = modeState,
                screenProfile = screenProfile,
                animationState = animationState,
                settingsListScrollOffsetPx = modeSettingsScrollOffset,
            )
            LauncherMode.SMS_ROLE_PROMPT -> drawSmsRolePrompt(
                buffer = buffer,
                state = modeState,
                screenProfile = screenProfile,
                animationState = animationState,
            )
            LauncherMode.SMS_THREADS -> drawSmsThreads(
                buffer = buffer,
                state = modeState,
                screenProfile = screenProfile,
                animationState = animationState,
                scrollOffsetPx = modeSettingsScrollOffset,
            )
            LauncherMode.SMS_THREAD_DETAIL -> drawSmsThreadDetail(
                buffer = buffer,
                state = modeState,
                screenProfile = screenProfile,
                animationState = animationState,
                bodyScrollOffsetPx = smsBodyScrollOffsetPx,
            )
            LauncherMode.SMS_INBOX -> drawSmsInbox(
                buffer = buffer,
                state = modeState,
                screenProfile = screenProfile,
                animationState = animationState,
                smsListScrollOffsetPx = smsBodyScrollOffsetPx,
            )
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

    /** 绘制 Home 页面，包括固定信息区和当前上下文卡片。 */
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
            pixelFontEngine.drawText(
                buffer = buffer,
                text = dateLine,
                startX = layoutMetrics.innerLeft,
                startY = layoutMetrics.dateY,
                maxWidth = layoutMetrics.innerWidth,
                style = GlyphStyle.UI_SMALL_10,
            )
        }

        val fixedInfoLines = HomeFixedInfoModel.rows(state).map { it.text }
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

    /** 绘制应用抽屉的头部、加载态和基于共享文本列表基座的正文内容。 */
    private fun drawAppDrawer(
        buffer: PixelBuffer,
        state: LauncherState,
        screenProfile: ScreenProfile,
        animationState: LauncherAnimationState,
        drawerListScrollOffsetPx: Int,
    ) {
        val drawerApps = resolveDrawerApps(state)
        val shouldShowApps = true
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

    /**
     * 根据抽屉当前处于普通态还是搜索态，绘制不同的状态栏头部，并保持电池分隔线在同一行。
     */
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
                alignment = state.drawerListAlignment,
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
        val availableWidth = (screenProfile.logicalWidth - (leftPadding * 2)).coerceAtLeast(0)
        val searchText = pixelFontEngine.trimToWidth(
            text = "SEARCH",
            style = style,
            maxWidth = availableWidth,
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
            val searchX = leftPadding + ((availableWidth - searchWidth) / 2).coerceAtLeast(0)
            drawTextAsValue(
                buffer = buffer,
                text = searchText,
                startX = searchX,
                startY = headerY,
                maxWidth = availableWidth,
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
        alignment: DrawerListAlignment,
        query: String,
        tick: Int,
    ) {
        val style = GlyphStyle.UI_SMALL_10
        val leftPadding = LauncherHeaderLayout.horizontalPadding
        val availableWidth = (screenProfile.logicalWidth - (leftPadding * 2)).coerceAtLeast(1)
        val textMaxWidth = (availableWidth - drawerCursorGapFromText - drawerCursorWidth).coerceAtLeast(1)
        val displayQuery = query.uppercase()
        val trimmed = pixelFontEngine.trimToWidth(
            text = displayQuery,
            style = style,
            maxWidth = textMaxWidth,
        )
        val cursorVisible = ((tick / drawerCursorBlinkFrames) % 2) == 0
        val textWidth = pixelFontEngine.measureText(trimmed, style)
        val trailingCursorSpace = drawerCursorGapFromText + drawerCursorWidth
        val horizontalOffset = when (alignment) {
            DrawerListAlignment.LEFT -> 0
            DrawerListAlignment.CENTER -> ((availableWidth - textWidth) / 2).coerceAtLeast(0)
            DrawerListAlignment.RIGHT -> (availableWidth - textWidth - trailingCursorSpace).coerceAtLeast(0)
        }
        val startX = leftPadding + horizontalOffset
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

    /** 使用共享文本列表 viewport 语义，绘制顶对齐且可滚动的抽屉正文列表。 */
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
        val safeListStartIndex = state.listStartIndex.coerceIn(0, drawerApps.lastIndex)
        val textAreaWidth = drawerTextAreaWidth(
            state = state,
            layoutMetrics = layoutMetrics,
        )
        forEachTextListRenderableRow(
            viewport = layoutMetrics.textList.viewport,
            rowCount = drawerApps.size,
            listStartIndex = safeListStartIndex,
            scrollOffsetPx = drawerListScrollOffsetPx,
        ) { appIndex, rowTop ->
            val appEntry = drawerApps[appIndex]

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
                clipTop = layoutMetrics.textList.viewport.top,
                clipBottomExclusive = layoutMetrics.textList.viewport.bottomExclusive,
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

    /** 使用和抽屉正文一致的文本列表渲染模型绘制设置页。 */
    private fun drawSettings(
        buffer: PixelBuffer,
        state: LauncherState,
        screenProfile: ScreenProfile,
        animationState: LauncherAnimationState,
        settingsListScrollOffsetPx: Int,
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
        forEachTextListRenderableRow(
            viewport = layout.textList.viewport,
            rowCount = rows.size,
            listStartIndex = state.settingsListStartIndex,
            scrollOffsetPx = settingsListScrollOffsetPx,
        ) { rowIndex, rowTop ->
            drawSettingsRow(
                buffer = buffer,
                layout = layout,
                row = rows[rowIndex],
                rowTop = rowTop,
                clipTop = layout.textList.viewport.top,
                clipBottomExclusive = layout.panelBottom,
            )
        }
    }

    private fun drawSettingsRow(
        buffer: PixelBuffer,
        layout: SettingsMenuLayoutMetrics,
        row: SettingsMenuRow,
        rowTop: Int,
        clipTop: Int,
        clipBottomExclusive: Int,
    ) {
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
            drawTextAsValueClipped(
                buffer = buffer,
                text = trimmedTitle,
                startX = layout.rowTextX,
                startY = rowTop + layout.rowTextYOffset,
                maxWidth = titleMaxWidth,
                style = GlyphStyle.UI_SMALL_10,
                value = PixelBuffer.ON,
                clipTop = clipTop,
                clipBottomExclusive = clipBottomExclusive,
            )
        }

        if (stableValue.isNotEmpty()) {
            drawTextAsValueClipped(
                buffer = buffer,
                text = stableValue,
                startX = stableValueStartX,
                startY = rowTop + layout.rowTextYOffset,
                maxWidth = stableValueWidth.coerceAtLeast(1),
                style = GlyphStyle.UI_SMALL_10,
                value = PixelBuffer.ON,
                clipTop = clipTop,
                clipBottomExclusive = clipBottomExclusive,
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

    /** 绘制 Home 中短信入口打开的未读短信全文页。 */
    private fun drawSmsInbox(
        buffer: PixelBuffer,
        state: LauncherState,
        screenProfile: ScreenProfile,
        animationState: LauncherAnimationState,
        smsListScrollOffsetPx: Int,
    ) {
        val layout = SettingsMenuLayout.largeTextMetrics(screenProfile)
        val entries = state.unreadSmsEntries
        val entry = entries.getOrNull(state.smsSelectedIndex)
        drawSmsInboxHeader(
            buffer = buffer,
            screenProfile = screenProfile,
            state = state,
            sender = entry?.address.orEmpty(),
            currentIndex = (state.smsSelectedIndex + 1).coerceAtLeast(1),
            totalCount = entries.size,
            tick = animationState.headerChargeTick,
        )
        if (entries.isEmpty()) {
            drawCenteredText(
                buffer = buffer,
                text = "NO UNREAD",
                y = layout.firstRowY,
                style = GlyphStyle.APP_LABEL_16,
            )
            return
        }
        val safeEntry = entry ?: return
        val body = safeEntry.body
            .replace("\r", "")
            .ifBlank { "(EMPTY)" }
            .uppercase()
        val bodyTop = layout.firstRowY
        val bodyBottomExclusive = screenProfile.logicalHeight - 2
        val bodyLines = wrapTextToWidth(
            text = body,
            style = GlyphStyle.APP_LABEL_16,
            maxWidth = layout.rowMaxTextWidth,
        )
        var lineY = bodyTop - smsListScrollOffsetPx
        bodyLines.forEach { line ->
            if (lineY + GlyphStyle.APP_LABEL_16.cellHeight > bodyBottomExclusive) {
                return@forEach
            }
            drawTextAsValueClipped(
                buffer = buffer,
                text = line,
                startX = layout.rowTextX,
                startY = lineY,
                maxWidth = layout.rowMaxTextWidth,
                style = GlyphStyle.APP_LABEL_16,
                value = PixelBuffer.ON,
                clipTop = LauncherHeaderLayout.contentTop,
                clipBottomExclusive = bodyBottomExclusive,
            )
            lineY += GlyphStyle.APP_LABEL_16.cellHeight + 2
        }
    }

    /** 默认短信角色申请提示页。 */
    private fun drawSmsRolePrompt(
        buffer: PixelBuffer,
        state: LauncherState,
        screenProfile: ScreenProfile,
        animationState: LauncherAnimationState,
    ) {
        drawHeader(
            buffer = buffer,
            screenProfile = screenProfile,
            state = state,
            titleCandidates = listOf("SMS"),
            chargeTick = animationState.headerChargeTick,
        )
        val layout = SettingsMenuLayout.largeTextMetrics(screenProfile)
        val lines = listOf(
            "SET PIXEL LAUNCHER",
            "AS DEFAULT SMS",
            "TO RECEIVE AND REPLY",
            "ENTER TO CONTINUE",
        )
        var y = layout.firstRowY
        lines.forEach { line ->
            drawCenteredText(
                buffer = buffer,
                text = line,
                y = y,
                style = GlyphStyle.APP_LABEL_16,
            )
            y += GlyphStyle.APP_LABEL_16.cellHeight + 2
        }
    }

    /** 短信线程列表页。 */
    private fun drawSmsThreads(
        buffer: PixelBuffer,
        state: LauncherState,
        screenProfile: ScreenProfile,
        animationState: LauncherAnimationState,
        scrollOffsetPx: Int,
    ) {
        drawHeader(
            buffer = buffer,
            screenProfile = screenProfile,
            state = state,
            titleCandidates = listOf("SMS"),
            chargeTick = animationState.headerChargeTick,
        )
        val layout = SmsLayout.threadListMetrics(screenProfile)
        if (state.smsThreads.isEmpty()) {
            drawCenteredText(
                buffer = buffer,
                text = if (state.smsPermissionState == com.purride.pixellauncherv2.launcher.SmsPermissionState.MISSING) "ALLOW SMS ACCESS" else "NO SMS",
                y = layout.textList.viewport.top,
                style = GlyphStyle.APP_LABEL_16,
            )
            return
        }
        val firstIndex = (state.smsThreadListStartIndex - 1).coerceAtLeast(0)
        val lastIndexExclusive = (state.smsThreadListStartIndex + layout.textList.viewport.visibleRows + 2)
            .coerceAtMost(state.smsThreads.size)
        for (index in firstIndex until lastIndexExclusive) {
            val rowTop = layout.textList.viewport.top +
                ((index - state.smsThreadListStartIndex) * layout.rowHeight) -
                scrollOffsetPx
            val rowBottom = rowTop + layout.rowHeight
            if (rowBottom <= layout.textList.viewport.top || rowTop >= layout.panelBottom) {
                continue
            }
            val thread = state.smsThreads[index]
            val addressText = sanitizeSmsHeaderSender(thread.address)
            val timeText = formatSmsHeaderTime(thread.dateMillis)
            val unreadText = if (thread.unreadCount > 0) "NEW ${thread.unreadCount}" else ""
            val addressAvailableWidth = (layout.rowMaxWidth - pixelFontEngine.measureText(timeText, GlyphStyle.UI_SMALL_10) - 2)
                .coerceAtLeast(8)
            drawTextAsValueClipped(
                buffer = buffer,
                text = pixelFontEngine.trimToWidth(addressText, GlyphStyle.APP_LABEL_16, addressAvailableWidth),
                startX = layout.rowTextX,
                startY = rowTop,
                maxWidth = addressAvailableWidth,
                style = GlyphStyle.APP_LABEL_16,
                value = PixelBuffer.ON,
                clipTop = layout.textList.viewport.top,
                clipBottomExclusive = layout.panelBottom,
            )
            drawTextAsValueClipped(
                buffer = buffer,
                text = timeText,
                startX = (screenProfile.logicalWidth - LauncherHeaderLayout.horizontalPadding - pixelFontEngine.measureText(timeText, GlyphStyle.UI_SMALL_10)).coerceAtLeast(layout.rowTextX),
                startY = rowTop + 2,
                maxWidth = pixelFontEngine.measureText(timeText, GlyphStyle.UI_SMALL_10),
                style = GlyphStyle.UI_SMALL_10,
                value = PixelBuffer.ACCENT,
                clipTop = layout.textList.viewport.top,
                clipBottomExclusive = layout.panelBottom,
            )
            val metaLine = buildString {
                if (unreadText.isNotBlank()) {
                    append(unreadText)
                    append("  ")
                }
                append(thread.snippet.ifBlank { "(EMPTY)" }.uppercase())
            }
            drawTextAsValueClipped(
                buffer = buffer,
                text = pixelFontEngine.trimToWidth(metaLine, GlyphStyle.UI_SMALL_10, layout.rowMaxWidth),
                startX = layout.rowTextX,
                startY = rowTop + GlyphStyle.APP_LABEL_16.cellHeight + 1,
                maxWidth = layout.rowMaxWidth,
                style = GlyphStyle.UI_SMALL_10,
                value = PixelBuffer.ACCENT,
                clipTop = layout.textList.viewport.top,
                clipBottomExclusive = layout.panelBottom,
            )
        }
    }

    /** 单个短信线程详情页，底部包含最小发送入口。 */
    private fun drawSmsThreadDetail(
        buffer: PixelBuffer,
        state: LauncherState,
        screenProfile: ScreenProfile,
        animationState: LauncherAnimationState,
        bodyScrollOffsetPx: Int,
    ) {
        val sender = state.smsCurrentAddress.ifBlank {
            state.smsMessages.lastOrNull()?.address.orEmpty()
        }
        val currentIndex = state.smsThreads.indexOfFirst { it.threadId == state.smsCurrentThreadId }
            .takeIf { it >= 0 }?.plus(1) ?: 1
        drawSmsInboxHeader(
            buffer = buffer,
            screenProfile = screenProfile,
            state = state,
            sender = sender,
            currentIndex = currentIndex,
            totalCount = state.smsThreads.size.coerceAtLeast(1),
            tick = animationState.headerChargeTick,
        )
        val layout = SmsLayout.detailMetrics(screenProfile)
        val messages = state.smsMessages
        if (messages.isEmpty()) {
            drawCenteredText(
                buffer = buffer,
                text = if (state.smsCurrentAddress.isNotBlank()) "START MESSAGE" else "NO MESSAGES",
                y = layout.bodyTop,
                style = GlyphStyle.APP_LABEL_16,
            )
        } else {
            var lineY = layout.bodyTop - bodyScrollOffsetPx
            messages.forEach { message ->
                val meta = "${if (message.type == Telephony.Sms.MESSAGE_TYPE_SENT) "OUT" else "IN"} ${formatSmsHeaderTime(message.dateMillis)}"
                drawTextAsValueClipped(
                    buffer = buffer,
                    text = meta,
                    startX = layout.textLeft,
                    startY = lineY,
                    maxWidth = layout.textWidth,
                    style = GlyphStyle.UI_SMALL_10,
                    value = PixelBuffer.ACCENT,
                    clipTop = layout.bodyTop,
                    clipBottomExclusive = layout.bodyBottomExclusive,
                )
                lineY += GlyphStyle.UI_SMALL_10.cellHeight + 1
                wrapTextToWidth(
                    text = message.body.replace("\r", "").ifBlank { "(EMPTY)" }.uppercase(),
                    style = GlyphStyle.APP_LABEL_16,
                    maxWidth = layout.textWidth,
                ).forEach { line ->
                    drawTextAsValueClipped(
                        buffer = buffer,
                        text = line,
                        startX = layout.textLeft,
                        startY = lineY,
                        maxWidth = layout.textWidth,
                        style = GlyphStyle.APP_LABEL_16,
                        value = PixelBuffer.ON,
                        clipTop = layout.bodyTop,
                        clipBottomExclusive = layout.bodyBottomExclusive,
                    )
                    lineY += GlyphStyle.APP_LABEL_16.cellHeight + 1
                }
                lineY += 2
            }
        }

        drawHorizontalLine(
            buffer = buffer,
            startX = LauncherHeaderLayout.horizontalPadding,
            endX = screenProfile.logicalWidth - LauncherHeaderLayout.horizontalPadding - 1,
            y = layout.composeTop - 1,
        )
        val sendLabel = if (state.smsPermissionState == com.purride.pixellauncherv2.launcher.SmsPermissionState.READY) "SEND" else "DEFAULT"
        val sendWidth = pixelFontEngine.measureText(sendLabel, GlyphStyle.UI_SMALL_10)
        val sendX = (layout.composeSendRight - sendWidth).coerceAtLeast(layout.composeTextLeft)
        val draftMaxWidth = (sendX - layout.composeTextLeft - 2).coerceAtLeast(8)
        val draftText = when {
            state.smsDraftText.isNotBlank() -> state.smsDraftText
            state.smsPermissionState == com.purride.pixellauncherv2.launcher.SmsPermissionState.READY -> "TYPE SMS"
            else -> "SET DEFAULT SMS TO REPLY"
        }
        drawTextAsValue(
            buffer = buffer,
            text = pixelFontEngine.trimToWidth(draftText, GlyphStyle.UI_SMALL_10, draftMaxWidth),
            startX = layout.composeTextLeft,
            startY = layout.composeTop + 1,
            maxWidth = draftMaxWidth,
            style = GlyphStyle.UI_SMALL_10,
            value = if (state.smsDraftText.isNotBlank()) PixelBuffer.ON else PixelBuffer.ACCENT,
        )
        drawTextAsValue(
            buffer = buffer,
            text = sendLabel,
            startX = sendX,
            startY = layout.composeTop + 1,
            maxWidth = sendWidth,
            style = GlyphStyle.UI_SMALL_10,
            value = PixelBuffer.ACCENT,
        )
    }

    /** 短信全文页头部：中间显示号码，右侧显示当前位置，视觉口径和抽屉状态栏一致。 */
    private fun drawSmsInboxHeader(
        buffer: PixelBuffer,
        screenProfile: ScreenProfile,
        state: LauncherState,
        sender: String,
        currentIndex: Int,
        totalCount: Int,
        tick: Int,
    ) {
        val style = GlyphStyle.UI_SMALL_10
        val leftPadding = LauncherHeaderLayout.horizontalPadding
        val headerY = LauncherHeaderLayout.rowY + LauncherHeaderLayout.textOffsetY
        val displayTime = state.currentTimeText.ifBlank { "--:--" }
        val rightText = "SMS:${currentIndex.coerceAtLeast(0)}/${totalCount.coerceAtLeast(0)}"
        val trimmedTime = pixelFontEngine.trimToWidth(
            text = displayTime,
            style = style,
            maxWidth = (screenProfile.logicalWidth / 3).coerceAtLeast(1),
        )
        val centerGap = pixelFontEngine.measureText("0", style).coerceAtLeast(1)
        val timeWidth = pixelFontEngine.measureText(trimmedTime, style)
        val rightWidth = pixelFontEngine.measureText(rightText, style)
        val rightX = (screenProfile.logicalWidth - LauncherHeaderLayout.horizontalPadding - rightWidth)
            .coerceAtLeast(leftPadding)
        val displaySender = sanitizeSmsHeaderSender(sender)
        val senderMarqueeKey = normalizeSmsHeaderSenderKey(sender)
        if (senderMarqueeKey != lastSmsHeaderSenderKey) {
            lastSmsHeaderSenderKey = senderMarqueeKey
            smsHeaderMarqueeStartTick = tick
        }
        val senderSafeLeft = (leftPadding + timeWidth + centerGap).coerceAtLeast(leftPadding)
        val senderSafeRight = (rightX - centerGap - 1).coerceAtLeast(senderSafeLeft)
        val centeredWindowWidth = centeredHeaderWindowWidth(
            screenWidth = screenProfile.logicalWidth,
            safeLeft = senderSafeLeft,
            safeRight = senderSafeRight,
        )
        val centeredWindowLeft = ((screenProfile.logicalWidth - centeredWindowWidth) / 2).coerceAtLeast(senderSafeLeft)
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
        if (displaySender.isNotEmpty()) {
            val senderWidth = pixelFontEngine.measureText(displaySender, style)
            if (senderWidth <= centeredWindowWidth) {
                val senderX = ((screenProfile.logicalWidth - senderWidth) / 2).coerceAtLeast(0)
                drawTextAsValue(
                    buffer = buffer,
                    text = displaySender,
                    startX = senderX,
                    startY = headerY,
                    maxWidth = centeredWindowWidth,
                    style = style,
                    value = PixelBuffer.ACCENT,
                )
            } else {
                drawMarqueeTextAsValue(
                    buffer = buffer,
                    text = displaySender,
                    startX = centeredWindowLeft,
                    startY = headerY,
                    maxWidth = centeredWindowWidth,
                    style = style,
                    value = PixelBuffer.ACCENT,
                    tick = (tick - smsHeaderMarqueeStartTick).coerceAtLeast(0),
                    separatorText = marqueeSeparatorForWidth(style, centerGap),
                )
            }
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
        drawHeaderBatteryDivider(
            buffer = buffer,
            screenProfile = screenProfile,
            batteryLevel = state.batteryLevel,
            isCharging = state.isCharging,
            chargeTick = tick,
        )
    }

    /** 短信页头部只显示号码相关字符，过滤中文联系人名和无关符号。 */
    private fun sanitizeSmsHeaderSender(sender: String): String {
        val filtered = sender.filter { ch ->
            ch.isDigit() || ch == '+' || ch == '*' || ch == '#' || ch == '-' || ch == ' ' || ch == '(' || ch == ')'
        }.trim()
        return filtered.ifBlank { "UNKNOWN" }.uppercase()
    }

    /** 跑马灯重置只基于号码本体，不受空格、括号、横杠等格式差异影响。 */
    private fun normalizeSmsHeaderSenderKey(sender: String): String {
        val normalized = sender.filter { ch ->
            ch.isDigit() || ch == '+' || ch == '*' || ch == '#'
        }.uppercase()
        return normalized.ifBlank { "UNKNOWN" }
    }

    private fun centeredHeaderWindowWidth(
        screenWidth: Int,
        safeLeft: Int,
        safeRight: Int,
    ): Int {
        val centerLeft = (screenWidth - 1) / 2
        val centerRight = screenWidth / 2
        val leftReach = (centerLeft - safeLeft + 1).coerceAtLeast(0)
        val rightReach = (safeRight - centerRight + 1).coerceAtLeast(0)
        val halfWidth = minOf(leftReach, rightReach)
        return (halfWidth * 2).coerceAtLeast(1)
    }

    private fun marqueeSeparatorForWidth(style: GlyphStyle, targetWidthPx: Int): String {
        if (targetWidthPx <= 0) {
            return " "
        }
        var separator = " "
        while (pixelFontEngine.measureText(separator, style) < targetWidthPx) {
            separator += " "
        }
        return separator
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
        drawIdleStatic(buffer, state, screenProfile)
    }

    private fun drawIdleStatic(buffer: PixelBuffer, state: LauncherState, screenProfile: ScreenProfile) {
        val timeY = ((screenProfile.logicalHeight - GlyphStyle.APP_LABEL_16.cellHeight) / 2).coerceAtLeast(0)
        drawCenteredHollowText(
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
        val maskWidth = fluidState.width
        val maskHeight = fluidState.height
        if (mask.isEmpty() || maskWidth <= 0 || maskHeight <= 0 || mask.size < (maskWidth * maskHeight)) {
            return
        }

        for (y in 0 until buffer.height) {
            val maskY = ((y.toFloat() / buffer.height.toFloat()) * maskHeight)
                .toInt()
                .coerceIn(0, maskHeight - 1)
            for (x in 0 until buffer.width) {
                val maskX = ((x.toFloat() / buffer.width.toFloat()) * maskWidth)
                    .toInt()
                    .coerceIn(0, maskWidth - 1)
                if (!mask[(maskY * maskWidth) + maskX]) {
                    continue
                }
                buffer.setPixel(x = x, y = y, value = PixelBuffer.ON)
            }
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

    /**
     * Idle 中央时间使用中空字，保留轮廓让充电遮罩从内部穿过，避免时间被整块盖住。
     */
    private fun drawCenteredHollowText(buffer: PixelBuffer, text: String, y: Int, style: GlyphStyle) {
        val trimmed = pixelFontEngine.trimToWidth(text, style, buffer.width - 2)
        val textWidth = pixelFontEngine.measureText(trimmed, style)
        val startX = ((buffer.width - textWidth) / 2).coerceAtLeast(0)
        drawTextAsHollowValue(
            buffer = buffer,
            text = trimmed,
            startX = startX,
            startY = y,
            maxWidth = buffer.width,
            style = style,
            value = PixelBuffer.ON,
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

    private fun drawTextAsHollowValue(
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
                if (temp.getPixel(x, y) != PixelBuffer.ON) {
                    continue
                }
                if (!isOutlinePixel(temp, x, y)) {
                    continue
                }
                buffer.setPixel(startX + x, startY + y, value)
            }
        }
    }

    private fun isOutlinePixel(buffer: PixelBuffer, x: Int, y: Int): Boolean {
        val neighbors = arrayOf(
            x - 1 to y,
            x + 1 to y,
            x to y - 1,
            x to y + 1,
        )
        return neighbors.any { (neighborX, neighborY) ->
            neighborX !in 0 until buffer.width ||
                neighborY !in 0 until buffer.height ||
                buffer.getPixel(neighborX, neighborY) == PixelBuffer.OFF
        }
    }

    private inline fun forEachTextListRenderableRow(
        viewport: TextListViewport,
        rowCount: Int,
        listStartIndex: Int,
        scrollOffsetPx: Int,
        block: (rowIndex: Int, rowTop: Int) -> Unit,
    ) {
        if (rowCount <= 0) {
            return
        }
        val firstRenderableIndex = TextListSupport.firstRenderableIndex(listStartIndex)
        for (rowIndex in firstRenderableIndex until rowCount) {
            block(
                rowIndex,
                TextListSupport.rowTop(
                    viewport = viewport,
                    rowIndex = rowIndex,
                    listStartIndex = listStartIndex,
                    scrollOffsetPx = scrollOffsetPx,
                ),
            )
        }
    }

    /** 按像素宽度把全文拆成多行，优先保留显式换行，其余按字符级换行。 */
    private fun wrapTextToWidth(
        text: String,
        style: GlyphStyle,
        maxWidth: Int,
    ): List<String> {
        val safeWidth = maxWidth.coerceAtLeast(style.narrowAdvanceWidth)
        val normalized = text.replace('\t', ' ')
        val lines = mutableListOf<String>()
        normalized.split('\n').forEach { rawLine ->
            if (rawLine.isEmpty()) {
                lines += ""
                return@forEach
            }
            var current = StringBuilder()
            rawLine.forEach { ch ->
                val candidate = current.toString() + ch
                if (pixelFontEngine.measureText(candidate, style) <= safeWidth || current.isEmpty()) {
                    current.append(ch)
                } else {
                    lines += current.toString()
                    current = StringBuilder().append(ch)
                }
            }
            lines += current.toString()
        }
        return lines
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
        separatorText: String = "   ",
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

        val separator = separatorText.ifEmpty { " " }
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

    private var lastSmsHeaderSenderKey: String = ""
    private var smsHeaderMarqueeStartTick: Int = 0
    private val smsHeaderTimeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    private fun formatSmsHeaderTime(dateMillis: Long): String {
        return runCatching {
            smsHeaderTimeFormatter.format(Date(dateMillis))
        }.getOrDefault("--:--")
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

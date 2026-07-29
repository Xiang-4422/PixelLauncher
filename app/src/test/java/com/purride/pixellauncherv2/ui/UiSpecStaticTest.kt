package com.purride.pixellauncherv2.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the Launcher UI rules that repeatedly caused clipping or tight text.
 *
 * This intentionally scans only app UI composition files. Low-level render code
 * may still use 1px lines and explicit clipping for graphics primitives.
 */
class UiSpecStaticTest {

    @Test
    fun screenAndWidgetSourcesAvoidKnownTextClippingPatterns() {
        val moduleRoot = resolveModuleRoot()
        val offenders = uiSourceRoots(moduleRoot)
            .flatMap { root ->
                root.walkTopDown()
                    .filter { file -> file.isFile && file.extension == "kt" }
                    .flatMap { file -> file.findUiSpecOffenders(moduleRoot) }
            }

        assertTrue(
            "Launcher UI spec violations:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun sharedLauncherSpacingTokensDriveTopLevelPagesAndControls() {
        val moduleRoot = resolveModuleRoot()
        val spacingSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherSpacing.kt")
            .readText()
        val headerSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherHeaderLayout.kt")
            .readText()
        val homeSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/screen/HomeScreen.kt")
            .readText()
        val drawerSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/screen/DrawerScreen.kt")
            .readText()
        val settingsSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/screen/SettingsScreen.kt")
            .readText()
        val controlsSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/widget/SettingsControls.kt")
            .readText()
        val mainActivitySource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/app/MainActivity.kt")
            .readText()
        val switchSource = Regex(
            """private fun SettingsSwitch\([\s\S]*?\n}\n\nprivate fun switchSegment""",
        ).find(controlsSource)?.value.orEmpty()
        val offenders = listOfNotNull(
            if (!spacingSource.contains("const val CONTENT_HORIZONTAL = 2")) "CONTENT_HORIZONTAL must be 2" else null,
            if (!spacingSource.contains("const val CONTENT_VERTICAL = 2")) "CONTENT_VERTICAL must be 2" else null,
            if (!spacingSource.contains("const val ROW_SPACING = 2")) "ROW_SPACING must be 2" else null,
            if (!spacingSource.contains("const val SETTINGS_SECTION_GAP = ROW_SPACING * 2")) {
                "SETTINGS_SECTION_GAP must describe extra Settings section separation"
            } else {
                null
            },
            if (!spacingSource.contains("const val EDGE_ACTION = 1")) "EDGE_ACTION must be 1" else null,
            if (!spacingSource.contains("const val BORDERED_CONTROL_INSET = 2")) {
                "BORDERED_CONTROL_INSET must be 2"
            } else {
                null
            },
            if (!headerSource.contains("horizontalPadding = LauncherSpacing.CONTENT_HORIZONTAL")) {
                "status bar must use shared horizontal content padding"
            } else {
                null
            },
            if (!homeSource.contains("horizontal = LauncherSpacing.CONTENT_HORIZONTAL") ||
                !homeSource.contains("spacing = LauncherSpacing.ROW_SPACING")
            ) {
                "Home must use shared content padding and row spacing"
            } else {
                null
            },
            if (!drawerSource.contains("horizontal = LauncherSpacing.CONTENT_HORIZONTAL") ||
                drawerSource.contains("DRAWER_ROW_PADDING_PX")
            ) {
                "Drawer must use page padding without duplicate row padding"
            } else {
                null
            },
            if (!settingsSource.contains("horizontal = LauncherSpacing.CONTENT_HORIZONTAL") ||
                controlsSource.contains("settingsRowPadding")
            ) {
                "Settings must use page padding without duplicate row padding"
            } else {
                null
            },
            if (switchSource.isEmpty() || switchSource.contains("padding =")) {
                "Settings Switch outer border must not add a gap around active segments"
            } else {
                null
            },
            if (!controlsSource.contains("LauncherSpacing.BORDERED_CONTROL_INSET")) {
                "Settings Switch labels must use the shared bordered-control inset"
            } else {
                null
            },
            if (!settingsSource.contains("LauncherSpacing.SETTINGS_SECTION_GAP") ||
                !controlsSource.contains("topMargin: Int = 0") ||
                !controlsSource.contains("fillColor = theme.button.border") ||
                !controlsSource.contains("style = TextStyle(color = theme.surface.offPixelColor)")
            ) {
                "Settings section headers must have distinct title bars and section separation"
            } else {
                null
            },
        )

        assertTrue(
            "Launcher top-level spacing must stay centralized:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun screenSourcesUseLauncherSpacingForPageRhythm() {
        val moduleRoot = resolveModuleRoot()
        val screenRoot = moduleRoot.resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/screen")
        val offenders = screenRoot.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .flatMap { file ->
                file.readLines().flatMapIndexed { index, line ->
                    forbiddenPageRhythmPatterns.mapNotNull { pattern ->
                        if (pattern.regex.containsMatchIn(line)) {
                            "${file.relativeTo(moduleRoot).invariantSeparatorsPath}:${index + 1}: ${pattern.description}: ${line.trim()}"
                        } else {
                            null
                        }
                    }
                }
            }
            .toList()

        assertTrue(
            "Launcher screens must use LauncherSpacing for page rhythm:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun settingsInlineRowsKeepBothSidesInExpandedCells() {
        val moduleRoot = resolveModuleRoot()
        val file = moduleRoot.resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/widget/SettingsControls.kt")
        val source = file.readText()
        val inlineRowSource = Regex(
            """private fun settingsInlineRow\([\s\S]*?\n\)\n\nprivate fun settingsRowCell""",
        ).find(source)?.value.orEmpty()
        val offenders = listOfNotNull(
            if (!inlineRowSource.contains("Expanded(flex = titleFlex, child = settingsRowCell(title, Alignment.CENTER_START))")) {
                "left settings cell must stay Expanded and center-start aligned"
            } else {
                null
            },
            if (!inlineRowSource.contains("Expanded(flex = trailingFlex, child = settingsRowCell(trailing, Alignment.CENTER_END))")) {
                "right settings cell must stay Expanded and center-end aligned"
            } else {
                null
            },
        )

        assertTrue(
            "Settings inline rows must keep left and right content in peer Expanded cells:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun pixelAndGapSettingsUseValueAdjusterAndSwitchersInsteadOfSliders() {
        val moduleRoot = resolveModuleRoot()
        val screenSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/screen/SettingsScreen.kt")
            .readText()
        val controlsSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/widget/SettingsControls.kt")
            .readText()
        val mainActivitySource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/app/MainActivity.kt")
            .readText()

        assertTrue(
            "PIXEL must use the value adjuster control and GAP must use the ON/OFF switcher.",
            Regex("""SettingsPixelSizeControl\(\s*title = "PIXEL"""").containsMatchIn(screenSource) &&
                Regex("""SettingsSwitchRow\(\s*title = "GAP"""").containsMatchIn(screenSource),
        )
        assertTrue(
            "The PIXEL control must be one ValueAdjuster row without S/M/L presets.",
            screenSource.contains("valueLabel = SettingsMenuModel.resolutionLabel(selectedDotSizePx)") &&
                controlsSource.contains("fun SettingsPixelSizeControl(") &&
                controlsSource.contains("ValueAdjuster(") &&
                controlsSource.contains("style = settingsValueAdjusterStyle(theme)") &&
                controlsSource.contains("buttonFillColor = theme.button.border") &&
                controlsSource.contains("buttonSymbolColor = theme.surface.offPixelColor") &&
                controlsSource.contains("valueTextColor = theme.settings.itemValue") &&
                !screenSource.contains("presetLabels =") &&
                !screenSource.contains("selectedPresetIndex =") &&
                !controlsSource.contains("pixelStepButton("),
        )
        assertTrue(
            "PIXEL +/- buttons must nudge the current value by 1 instead of cycling preset options.",
            mainActivitySource.contains("newDotSizePx = (s.selectedDotSizePx + direction).coerceAtLeast(1)") &&
                !mainActivitySource.contains("SettingsMenuModel.nextResolution(s.selectedDotSizePx"),
        )
        assertTrue(
            "PIXEL and GAP changes must preview first, persist only after CONFIRM, and restore on timeout.",
            mainActivitySource.contains("SettingsMenuItem.PIXEL_GAP ->") &&
                mainActivitySource.contains("applyPixelAppearancePreview(") &&
                mainActivitySource.contains("confirmPendingPixelAppearanceChange()") &&
                mainActivitySource.contains("actionLabel = \"CONFIRM\"") &&
                mainActivitySource.contains("restorePendingPixelAppearanceChange()") &&
                !mainActivitySource.contains("actionLabel = \"CANCEL\""),
        )
        assertTrue(
            "Settings must not retain the removed slider control or ratio preview state.",
            !screenSource.contains("SettingsValueSlider") &&
                !controlsSource.contains("fun SettingsValueSlider") &&
                !screenSource.contains("previewPixelSizeRatio") &&
                !screenSource.contains("previewGapRatio"),
        )
    }

    @Test
    fun drawerListDoesNotRenderSearchMatchReasonTags() {
        val moduleRoot = resolveModuleRoot()
        val file = moduleRoot.resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/screen/DrawerScreen.kt")
        val source = file.readText()
        val offenders = drawerMatchReasonUiPatterns.mapNotNull { pattern ->
            if (pattern.regex.containsMatchIn(source)) {
                "${file.relativeTo(moduleRoot).invariantSeparatorsPath}: ${pattern.description}"
            } else {
                null
            }
        }

        assertTrue(
            "Drawer list should render app titles only; match reasons stay in search logic:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun drawerLongPressActionsStayInLightweightOverlay() {
        val moduleRoot = resolveModuleRoot()
        val source = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/screen/DrawerScreen.kt")
            .readText()

        assertTrue(
            "Drawer long-press actions must stay in a lightweight dialog overlay.",
            source.contains("isAppActionMenuVisible") &&
                source.contains("Stack(") &&
                source.contains("PositionedFill(") &&
                source.contains("Dialog(") &&
                source.contains("TextButton(text = \"EDIT\"") &&
                source.contains("TextButton(text = \"REFRESH\"") &&
                source.contains("TextButton(text = \"CANCEL\""),
        )
    }

    @Test
    fun transientMessagesReplaceStatusBarTextButKeepBatteryDivider() {
        val moduleRoot = resolveModuleRoot()
        val headerSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/widget/LauncherHeader.kt")
            .readText()
        val rootHostSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherRootHost.kt")
            .readText()
        val homeSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/screen/HomeScreen.kt")
            .readText()

        val offenders = listOfNotNull(
            if (!headerSource.contains("centerContent = when {")) {
                "status bar center slot must switch transient states before normal text"
            } else {
                null
            },
            if (!headerSource.contains("isShowingAction -> StatusBarCenterContent.FilledAction(")) {
                "status bar action must take precedence over messages and normal text"
            } else {
                null
            },
            if (!headerSource.contains("isShowingMessage -> StatusBarCenterContent.Message(message)")) {
                "status bar messages must use the shared center slot"
            } else {
                null
            },
            if (!headerSource.contains("val fillColor = if (filled) statusBarActionBackgroundColor(isDanger, theme) else null") ||
                !headerSource.contains("PixelColor.fromRgb(255, 0, 0)")
            ) {
                "danger status bar actions must keep the neutral red fill"
            } else {
                null
            },
            if (!headerSource.contains("centerContent is StatusBarCenterContent.FilledAction") ||
                !headerSource.contains("statusBarFullWidthAction(") ||
                !headerSource.contains("height = STATUS_BAR_MEDIA_ROW_HEIGHT_PX")
            ) {
                "filled status bar actions such as CONFIRM must occupy the full status bar row without an inner segment"
            } else {
                null
            },
            if (Regex("""divider = BatteryDividerWidget\(""").findAll(headerSource).count() != 2) {
                "normal and search status bars must keep their battery divider in the global chrome"
            } else {
                null
            },
            if (!headerSource.contains("StatusBarBatteryFrame(")) {
                "battery divider must share the status bar frame while staying separate from the row border"
            } else {
                null
            },
            if (!rootHostSource.contains("buildGlobalStatusBar()") ||
                !rootHostSource.contains("LauncherStatusBarPresentation.forMode(uiState.mode)")
            ) {
                "LauncherRootHost must own the shared status bar presentation"
            } else {
                null
            },
            if (homeSource.contains("HomeNoticeToast") || homeSource.contains("statusBarMessageText")) {
                "Home must not render a second page-level transient message"
            } else {
                null
            },
        )

        assertTrue(
            "Transient messages must exclusively use the shared status bar:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun globalStatusBarChromeOwnsRouteHeaders() {
        val moduleRoot = resolveModuleRoot()
        val rootHostSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherRootHost.kt")
            .readText()
        val presentationSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherStatusBarPresentation.kt")
            .readText()
        val screenRoot = moduleRoot.resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/screen")
        val screenOffenders = screenRoot.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .mapNotNull { file ->
                val source = file.readText()
                if (
                    source.contains("LauncherHeader(") ||
                    source.contains("LauncherSearchHeader(") ||
                    source.contains("StatusBarBatteryFrame(") ||
                    source.contains("BatteryDividerWidget(")
                ) {
                    file.relativeTo(moduleRoot).invariantSeparatorsPath
                } else {
                    null
                }
            }
            .toList()

        assertTrue(
            "Route pages must not draw their own status bar or battery divider:\n${screenOffenders.joinToString("\n")}",
            screenOffenders.isEmpty(),
        )
        assertTrue(
            "LauncherRootHost must keep the global status bar outside route transitions.",
            rootHostSource.contains("children = listOf(\n                buildGlobalStatusBar(),\n                Expanded(") &&
                rootHostSource.contains("child = PixelNavigator(") &&
                !rootHostSource.contains("buildSharedStatusBar()") &&
                presentationSource.contains("data object Search") &&
                presentationSource.contains("data class Standard") &&
                presentationSource.contains("fun pageTitleFor(mode: LauncherMode): String"),
        )
    }

    @Test
    fun homeKeepsWeatherDirectlyBelowDate() {
        val moduleRoot = resolveModuleRoot()
        val source = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/screen/HomeScreen.kt")
            .readText()
        val dateIndex = source.indexOf("s.currentDateText.ifBlank")
        val weatherIndex = source.indexOf("HomeInfoModel.weatherLine(s)")
        val statusIndex = source.indexOf("HomeInfoModel.lines(s).forEach")

        assertTrue(
            "Home content order must remain date, weather, then priority status lines.",
            dateIndex >= 0 && weatherIndex > dateIndex && statusIndex > weatherIndex,
        )
        assertTrue(
            "Home information rows must use primary text consistently.",
            source.contains("style = TextStyle(color = t.text.primary)") &&
                source.contains("style = TextStyle(color = theme.text.primary)") &&
                !source.contains("style = TextStyle(color = theme.text.secondary)"),
        )
    }

    @Test
    fun screenEdgeStatusAndHomeActionsUseTheirSemanticSpacingTokens() {
        val moduleRoot = resolveModuleRoot()
        val headerLayoutSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherHeaderLayout.kt")
            .readText()
        val chromeLayoutSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherChromeLayout.kt")
            .readText()
        val headerSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/widget/LauncherHeader.kt")
            .readText()
        val homeSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/screen/HomeScreen.kt")
            .readText()

        assertTrue(
            "Status bar time and page title must use the shared 2px content edge.",
            headerLayoutSource.contains("horizontalPadding = LauncherSpacing.CONTENT_HORIZONTAL"),
        )
        assertTrue(
            "Home media controls must fill the horizontal and bottom edges while normal actions keep the 1px edge spacing.",
            homeSource.contains("left = if (s.mediaPlayback.hasTrack) {") &&
                homeSource.contains("0") &&
                homeSource.contains("LauncherSpacing.EDGE_ACTION") &&
                homeSource.contains("right = if (s.mediaPlayback.hasTrack) {") &&
                homeSource.contains("bottom = if (s.mediaPlayback.hasTrack) {") &&
                homeSource.contains("val horizontalInset = if (media.hasTrack)") &&
                homeSource.contains("0\n            } else {\n                LauncherSpacing.EDGE_ACTION") &&
                homeSource.contains("val barWidth = (widget.screenWidthPx - horizontalInset * 2).coerceAtLeast(1)") &&
                !homeSource.contains("if (s.mediaPlayback.hasTrack) 0 else LauncherSpacing.EDGE_ACTION"),
        )
        assertTrue(
            "Home actions must use the engine TextButton instead of a local button wrapper.",
            homeSource.contains("TextButton(") && !homeSource.contains("HomeTextButton"),
        )
        assertTrue(
            "Status bar title and center-content modes must share the same row box; page tag changes animate independently.",
            headerSource.contains("if (centerContent == StatusBarCenterContent.Empty)") &&
                headerSource.contains("if (centerContent == StatusBarCenterContent.Empty) {\n        return Container(") &&
                headerSource.contains("return Container(") &&
                headerSource.contains("borderColor = theme.button.border") &&
                headerSource.contains("height = STATUS_BAR_TITLE_ROW_HEIGHT_PX") &&
                headerSource.contains("padding = EdgeInsets.all(STATUS_BAR_TITLE_EDGE_PADDING_PX)") &&
                headerSource.contains("height = STATUS_BAR_MEDIA_ROW_HEIGHT_PX") &&
                headerSource.contains("padding = EdgeInsets.all(STATUS_BAR_MEDIA_BORDER_PX)") &&
                headerSource.contains("fillColor = content.color ?: theme.button.border") &&
                headerSource.contains("textColor = theme.surface.offPixelColor") &&
                headerSource.contains("StatusBarCenterContent.MediaTitle") &&
                headerSource.contains("statusBarPageTag(") &&
                headerSource.contains("AnimatedSwitcher(") &&
                headerSource.contains("status-bar-page-tag-\$") &&
                headerSource.contains("STATUS_BAR_SEGMENT_DIVIDER_PX = 1") &&
                headerSource.contains("STATUS_BAR_MEDIA_ROW_HEIGHT_PX = LauncherChromeLayout.sharedRowHeightPx") &&
                headerSource.contains("STATUS_BAR_TITLE_EDGE_PADDING_PX = STATUS_BAR_MEDIA_BORDER_PX") &&
                headerSource.contains("STATUS_BAR_TITLE_SEGMENT_HEIGHT_PX = STATUS_BAR_MEDIA_SEGMENT_HEIGHT_PX") &&
                headerSource.contains("STATUS_BAR_TITLE_ROW_HEIGHT_PX = LauncherChromeLayout.sharedRowHeightPx") &&
                headerSource.contains("STATUS_BAR_TITLE_CONTENT_HEIGHT_PX =") &&
                headerSource.contains("STATUS_BAR_TITLE_ROW_HEIGHT_PX + LauncherHeaderLayout.dividerHeight") &&
                headerSource.contains("StatusBarBatteryFrame(") &&
                homeSource.contains("HOME_ACTION_TOTAL_HEIGHT_PX = LauncherChromeLayout.sharedRowHeightPx") &&
                chromeLayoutSource.contains("sharedRowHeightPx = sharedSegmentHeightPx + sharedBorderPx * 2"),
        )
        val searchHeaderSource = Regex(
            "fun LauncherSearchHeader[\\s\\S]*?private fun statusBarSearchRow",
        ).find(headerSource)?.value.orEmpty()
        val searchRowSource = Regex(
            "private fun statusBarSearchRow[\\s\\S]*?private fun statusBarActionBackgroundColor",
        ).find(headerSource)?.value.orEmpty()
        assertTrue(
            "App drawer search header must use the same row box height as the normal status bar.",
            searchHeaderSource.contains("contentHeight = STATUS_BAR_TITLE_CONTENT_HEIGHT_PX") &&
                searchHeaderSource.contains("statusBarSearchRow(") &&
                !searchHeaderSource.contains("messageText") &&
                !searchHeaderSource.contains("actionLabel") &&
                searchRowSource.contains("height = STATUS_BAR_TITLE_ROW_HEIGHT_PX") &&
                searchRowSource.contains("padding = searchRowPadding(") &&
                searchRowSource.contains("child = TextField(") &&
                searchRowSource.contains("textAlign = textAlign") &&
                searchRowSource.contains("borderColor = PixelColor.Transparent") &&
                searchRowSource.contains("focusedBorderColor = PixelColor.Transparent") &&
                searchRowSource.contains("crossAxisAlignment = CrossAxisAlignment.STRETCH") &&
                !searchRowSource.contains("TextAlign.CENTER -> Row(") &&
                !searchRowSource.contains("textAlign = if (textAlign == TextAlign.CENTER)") &&
                !searchRowSource.contains("alignment = Alignment.CENTER,\n                    child = TextField("),
        )
        val notificationItemSource = Regex(
            "private fun HomeNotificationItem[\\s\\S]*?private fun homeNotificationHeader",
        ).find(homeSource)?.value.orEmpty()
        assertTrue(
            "Home notification items must not add their own border between the full-width media bars.",
            notificationItemSource.isNotEmpty() &&
                !notificationItemSource.contains("borderColor = theme.button.border") &&
                notificationItemSource.contains("padding = EdgeInsets.all(HOME_NOTIFICATION_PADDING_PX)"),
        )
    }

    @Test
    fun homeCommunicationCountsStayOnBottomActions() {
        val moduleRoot = resolveModuleRoot()
        val homeSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/screen/HomeScreen.kt")
            .readText()
        val modelSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/launcher/HomeInfoModel.kt")
            .readText()
        val mainActivitySource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/app/MainActivity.kt")
            .readText()

        assertTrue(
            "Home missed-call and unread-SMS counts must render on the bottom actions with mirrored positions.",
            homeSource.contains("count = s.missedCallCount") &&
                homeSource.contains("countOnStart = false") &&
                homeSource.contains("count = s.unreadSmsCount") &&
                homeSource.contains("countOnStart = true"),
        )
        assertTrue(
            "Home action counts must use the ValueAdjuster-style shared border on the normal CALL/SMS actions.",
            homeSource.contains("borderColor = theme.button.border") &&
                homeSource.contains("fillColor = theme.button.border") &&
                homeSource.contains("color = theme.surface.offPixelColor") &&
                homeSource.contains("role = LauncherTextRole.CHROME") &&
                homeSource.contains("HOME_ACTION_DIVIDER_PX = 1"),
        )
        val mediaBarSource = Regex(
            "private fun HomeMediaBottomBar[\\s\\S]*?private fun HomeMediaSideAction",
        ).find(homeSource)?.value.orEmpty()
        assertTrue(
            "Home media bottom bar must be independent from the normal CALL/SMS actions and use five equal slots.",
            homeSource.contains("return HomeMediaBottomBar(") &&
                !homeSource.contains("forceBorder") &&
                mediaBarSource.split("Expanded(").size - 1 == 5 &&
                mediaBarSource.contains("HomeMediaSideAction(") &&
                mediaBarSource.contains("filled = true") &&
                mediaBarSource.contains("filled = false") &&
                mediaBarSource.contains("mediaControlDivider(theme)"),
        )
        assertTrue(
            "Home media bottom bar must consume horizontal scrubs whenever a track is visible, even if the session cannot seek.",
            homeSource.contains("val canScrub = media.hasTrack") &&
                homeSource.contains("HOME_MEDIA_EDGE_SWIPE_TARGET_HEIGHT_PX") &&
                homeSource.contains("bottom = 0") &&
                homeSource.contains("onSwipeStart = if (canScrub)") &&
                homeSource.contains("if (media.canSeek)") &&
                !homeSource.contains("onSwipeStart = if (canSeek)"),
        )
        assertTrue(
            "Home media bottom bar must exclude the Android bottom gesture strip while media controls are visible.",
            mainActivitySource.contains("updateMediaGestureExclusion(uiState.mediaPlayback.hasTrack)") &&
                mainActivitySource.contains("view.systemGestureExclusionRects = listOf(") &&
                mainActivitySource.contains("MEDIA_BOTTOM_BAR_GESTURE_EXCLUSION_LOGICAL_HEIGHT"),
        )
        assertTrue(
            "Home info rows must not duplicate CALL/SMS counts already shown in the bottom actions.",
            !modelSource.contains("HomeInfoLine(\"CALL ") &&
                !modelSource.contains("HomeInfoLine(\"SMS "),
        )
    }

    @Test
    fun diagnosticsKeepsDataHealthDebugAction() {
        val moduleRoot = resolveModuleRoot()
        val diagnosticsSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/screen/DiagnosticsScreen.kt")
            .readText()
        val rootHostSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherRootHost.kt")
            .readText()

        assertTrue(
            "Advanced diagnostics must keep an actionable Data Health debug row.",
            diagnosticsSource.contains("onOpenDataHealth") &&
                diagnosticsSource.contains("line.title == \"DEBUG\"") &&
                diagnosticsSource.contains("line.value == \"DATA HEALTH\"") &&
                diagnosticsSource.contains("GestureDetector(onTap = onOpenDataHealth") &&
                rootHostSource.contains("onOpenDataHealth = callbacks.onOpenDataHealth"),
        )
    }

    @Test
    fun smsDetailKeepsMessageCopyAndCodeHint() {
        val moduleRoot = resolveModuleRoot()
        val source = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/screen/SmsThreadDetailScreen.kt")
            .readText()
        val rootHostSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherRootHost.kt")
            .readText()

        assertTrue(
            "SMS detail must keep copy/code hints, put the conversation title in the global status bar, hide service compose, and avoid the IME.",
            source.contains("onMessagePressed") &&
                source.contains("GestureDetector(") &&
                source.contains("SmsVerificationCodeModel.extract") &&
                source.contains("\"CODE ") &&
                source.contains("messageMetaRow(code, SmsTimeFormatter.format(msg.dateMillis), theme)") &&
                source.contains("private fun messageMetaRow(") &&
                !source.contains("private fun codeLine(") &&
                source.contains("smsCurrentIsServiceConversation") &&
                source.contains("MediaQuery.of(context).viewInsets.bottom") &&
                !source.contains("SEARCH MSG") &&
                !source.contains("SmsMessageStatusModel.label") &&
                !source.contains("conversationTitle(") &&
                rootHostSource.contains("statusBarPageTitle(presentation)") &&
                rootHostSource.contains("LauncherStatusBarPresentation.smsDetailPageTitle(") &&
                rootHostSource.contains("conversationTitle = uiState.smsCurrentConversationTitle") &&
                rootHostSource.contains("address = uiState.smsCurrentAddress"),
        )
    }

    @Test
    fun smsHomeKeepsBottomSharedTabsAndReadAction() {
        val moduleRoot = resolveModuleRoot()
        val source = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/screen/SmsThreadsScreen.kt")
            .readText()
        val controllerSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/app/SmsController.kt")
            .readText()
        val activitySource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/app/MainActivity.kt")
            .readText()
        val rootHostSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherRootHost.kt")
            .readText()
        val statusPresentationSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherStatusBarPresentation.kt")
            .readText()
        val unreadPageSource = Regex(
            """private fun buildUnreadMessagesPage\([\s\S]*?\n}\n\nprivate fun buildAllThreadsPage""",
        ).find(source)?.value.orEmpty()
        val unreadRowSource = Regex(
            """private fun buildUnreadRow\([\s\S]*?\n\nprivate fun buildThreadRow""",
        ).find(source)?.value.orEmpty()

        assertTrue(
            "SMS home tabs must stay at the bottom with a shared outer border and equal-width tab cells.",
            source.contains("smsBottomTabs(") &&
                source.contains("(uiState.isSmsThreadsLoading && uiState.smsPageIndex == SmsPageIndex.UNREAD)") &&
                source.contains("if (showUnreadTabs)") &&
                source.contains("SEARCH ALL SMS") &&
                source.contains("SmsThreadSearchModel.filter") &&
                source.contains("thread.snippet.trim()") &&
                source.contains("style = TextStyle(color = theme.sms.body)") &&
                source.contains("message.body.trim()") &&
                source.contains("maxLines = Int.MAX_VALUE") &&
                source.contains("borderColor = theme.button.border") &&
                source.contains("SMS_PAGE_TABS.mapIndexed") &&
                source.contains("Expanded(") &&
                source.contains("Semantics(") &&
                source.contains("PixelSemanticRole.TAB") &&
                source.contains("focused = index == selectedIndex") &&
                !source.contains("import com.purride.pixelui.Tabs"),
        )
        assertTrue(
            "SMS home must expose READ through the global status bar, expand unread messages, and support row swipe-to-read.",
            rootHostSource.contains("centerActionLabel = if (showSmsReadAction) \"READ\" else \"\"") &&
                rootHostSource.contains("isCenterActionEnabled = uiState.unreadSmsEntries.isNotEmpty()") &&
                rootHostSource.contains("onCenterAction = if (showSmsReadAction) callbacks.onMarkSmsRead else null") &&
                statusPresentationSource.contains("showSmsReadAction = mode == LauncherMode.SMS_THREADS || mode == LauncherMode.SMS_INBOX") &&
                !source.contains("LauncherHeader(") &&
                source.contains("onMarkUnreadMessageRead") &&
                unreadPageSource.isNotEmpty() &&
                !unreadPageSource.contains("itemExtent = SmsThreadGeometry.ROW_EXTENT_PX") &&
                unreadRowSource.contains("Slidable(") &&
                unreadRowSource.contains("startActionPane = unreadReadActionPane") &&
                unreadRowSource.contains("endActionPane = unreadReadActionPane") &&
                unreadRowSource.contains("onDismissed = { onMarkUnreadMessageRead(entry.messageId) }") &&
                source.contains("SlidableAction(") &&
                source.contains("label = \"READ\"") &&
                unreadRowSource.contains("maxLines = Int.MAX_VALUE") &&
                controllerSource.contains("val effectiveInitialPage") &&
                controllerSource.contains("host.state.unreadSmsCount <= 0") &&
                activitySource.contains("smsController.openModule(initialPage = SmsPageIndex.UNREAD)") &&
                !activitySource.contains("smsController.openModule(forceRefresh = true, initialPage = SmsPageIndex.UNREAD)") &&
                controllerSource.contains("fun markAllRead()") &&
                controllerSource.contains("smsRepository.markAllRead()") &&
                controllerSource.contains("fun markMessageRead(messageId: Long)") &&
                controllerSource.contains("smsRepository.markMessagesRead(listOf(messageId))") &&
                activitySource.contains("onMarkUnreadMessageRead = smsController::markMessageRead"),
        )
    }

    private fun File.findUiSpecOffenders(moduleRoot: File): List<String> {
        val relativePath = relativeTo(moduleRoot).invariantSeparatorsPath
        return readLines().flatMapIndexed { index, line ->
            forbiddenPatterns.mapNotNull { pattern ->
                if (pattern.regex.containsMatchIn(line)) {
                    "$relativePath:${index + 1}: ${pattern.description}: ${line.trim()}"
                } else {
                    null
                }
            }
        }
    }

    private fun uiSourceRoots(moduleRoot: File): List<File> = listOf(
        moduleRoot.resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/screen"),
        moduleRoot.resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/widget"),
    ).filter { root -> root.exists() }

    private fun resolveModuleRoot(): File {
        val cwd = File(".").canonicalFile
        return if (cwd.name == "app") cwd else cwd.resolve("app")
    }

    private data class ForbiddenPattern(
        val regex: Regex,
        val description: String,
    )

    private companion object {
        val forbiddenPatterns = listOf(
            ForbiddenPattern(Regex("""TextOverflow\.CLIP"""), "use ELLIPSIS or wrapping, not CLIP"),
            ForbiddenPattern(Regex("""\bfontScale\b"""), "font size must come from the controlled font scale"),
            ForbiddenPattern(Regex("""EdgeInsets\.all\(1\)"""), "bordered/text containers need at least 2px padding"),
            ForbiddenPattern(Regex("""\bvertical\s*=\s*1\b"""), "text row vertical padding must be at least 2px"),
            ForbiddenPattern(Regex("""\bCOMPOSE_HEIGHT\b"""), "compose bars should use natural height"),
        )

        val drawerMatchReasonUiPatterns = listOf(
            ForbiddenPattern(Regex("""matchSourceLabel"""), "do not pass match reason labels into Drawer UI rows"),
            ForbiddenPattern(
                Regex("""DrawerSearchSupport\.matchSourceLabel"""),
                "do not render Drawer search match reason labels",
            ),
            ForbiddenPattern(Regex(""""(ALIAS|PINYIN|PKG|ACT)""""), "do not hard-code search reason tags in Drawer UI"),
        )

        val forbiddenPageRhythmPatterns = listOf(
            ForbiddenPattern(Regex("""\bhorizontal\s*=\s*2\b"""), "use LauncherSpacing.CONTENT_HORIZONTAL"),
            ForbiddenPattern(Regex("""\bvertical\s*=\s*2\b"""), "use LauncherSpacing.CONTENT_VERTICAL"),
            ForbiddenPattern(Regex("""\bspacing\s*=\s*2\b"""), "use LauncherSpacing.ROW_SPACING"),
            ForbiddenPattern(Regex("""EdgeInsets\.all\(2\)"""), "use LauncherSpacing.BORDERED_CONTROL_INSET"),
            ForbiddenPattern(Regex("""\bpadding\s*=\s*2\b"""), "use LauncherSpacing.BORDERED_CONTROL_INSET"),
        )
    }
}

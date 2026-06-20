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
        val switchSource = Regex(
            """private fun SettingsSwitch\([\s\S]*?\n}\n\nprivate fun switchSegment""",
        ).find(controlsSource)?.value.orEmpty()
        val offenders = listOfNotNull(
            if (!spacingSource.contains("const val CONTENT_HORIZONTAL = 2")) "CONTENT_HORIZONTAL must be 2" else null,
            if (!spacingSource.contains("const val CONTENT_VERTICAL = 2")) "CONTENT_VERTICAL must be 2" else null,
            if (!spacingSource.contains("const val ROW_SPACING = 2")) "ROW_SPACING must be 2" else null,
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
    fun pixelAndGapSettingsUseSwitchersInsteadOfSliders() {
        val moduleRoot = resolveModuleRoot()
        val screenSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/screen/SettingsScreen.kt")
            .readText()
        val controlsSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/widget/SettingsControls.kt")
            .readText()

        assertTrue(
            "PIXEL must use engine SegmentedControl and GAP must use the ON/OFF switcher.",
            Regex("""SettingsSegmentedControlRow\(\s*title = "PIXEL"""").containsMatchIn(screenSource) &&
                Regex("""SettingsSwitchRow\(\s*title = "GAP"""").containsMatchIn(screenSource),
        )
        assertTrue(
            "The PIXEL wrapper must keep its title and all options in one inline SegmentedControl row.",
            controlsSource.contains("SegmentedControl(") &&
                !controlsSource.contains("labels.chunked(") &&
                controlsSource.contains("trailingFlex = 3"),
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
        val homeSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/screen/HomeScreen.kt")
            .readText()

        val offenders = listOfNotNull(
            if (!headerSource.contains("row = if (isShowingMessage)")) {
                "status bar row must switch to the transient message"
            } else {
                null
            },
            if (Regex("""divider = BatteryDividerWidget\(""").findAll(headerSource).count() != 2) {
                "normal and search status bars must keep their battery divider during messages"
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
    }

    @Test
    fun screenEdgeStatusAndHomeActionsUseTheirSemanticSpacingTokens() {
        val moduleRoot = resolveModuleRoot()
        val headerLayoutSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherHeaderLayout.kt")
            .readText()
        val homeSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/screen/HomeScreen.kt")
            .readText()

        assertTrue(
            "Status bar time and page title must use the shared 2px content edge.",
            headerLayoutSource.contains("horizontalPadding = LauncherSpacing.CONTENT_HORIZONTAL"),
        )
        assertTrue(
            "Home CALL and SMS must stay 1px from horizontal and bottom screen edges.",
            homeSource.contains("left = LauncherSpacing.EDGE_ACTION") &&
                homeSource.contains("right = LauncherSpacing.EDGE_ACTION") &&
                homeSource.contains("bottom = LauncherSpacing.EDGE_ACTION"),
        )
        assertTrue(
            "Home actions must use the engine TextButton instead of a local button wrapper.",
            homeSource.contains("TextButton(") && !homeSource.contains("HomeTextButton"),
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

        assertTrue(
            "SMS detail messages must keep tap-to-copy and visible verification code hints.",
            source.contains("onMessagePressed") &&
                source.contains("SEARCH MSG") &&
                source.contains("SmsThreadSearchModel.filter") &&
                source.contains("GestureDetector(") &&
                source.contains("SmsVerificationCodeModel.extract") &&
                source.contains("\"CODE "),
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

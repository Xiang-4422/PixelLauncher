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
    fun settingsTextPaddingConstantsRemainAtLeastTwoPixels() {
        val moduleRoot = resolveModuleRoot()
        val file = moduleRoot.resolve("src/main/kotlin/com/purride/pixellauncherv2/ui/widget/SettingsControls.kt")
        val source = file.readText()
        val offenders = minPaddingConstants.mapNotNull { rule ->
            val value = rule.regex.find(source)?.groupValues?.getOrNull(1)?.toIntOrNull()
            when {
                value == null -> "${file.relativeTo(moduleRoot).invariantSeparatorsPath}: missing ${rule.constantName}"
                value < 2 -> "${file.relativeTo(moduleRoot).invariantSeparatorsPath}: ${rule.constantName} must be >= 2, found $value"
                else -> null
            }
        }

        assertTrue(
            "Launcher settings text padding constants must stay >= 2px:\n${offenders.joinToString("\n")}",
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
            if (!inlineRowSource.contains("Expanded(child = settingsRowCell(title, Alignment.CENTER_START))")) {
                "left settings cell must stay Expanded and center-start aligned"
            } else {
                null
            },
            if (!inlineRowSource.contains("Expanded(child = settingsRowCell(trailing, Alignment.CENTER_END))")) {
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

    private data class MinPaddingConstant(
        val constantName: String,
        val regex: Regex,
    )

    private companion object {
        val forbiddenPatterns = listOf(
            ForbiddenPattern(Regex("""TextOverflow\.CLIP"""), "use ELLIPSIS or wrapping, not CLIP"),
            ForbiddenPattern(Regex("""\bfontScale\b"""), "font size must come from the controlled font scale"),
            ForbiddenPattern(Regex("""EdgeInsets\.all\(1\)"""), "bordered/text containers need at least 2px padding"),
            ForbiddenPattern(Regex("""\bvertical\s*=\s*1\b"""), "text row vertical padding must be at least 2px"),
            ForbiddenPattern(Regex("""\bCOMPOSE_HEIGHT\b"""), "compose bars should use natural height"),
        )

        val minPaddingConstants = listOf(
            MinPaddingConstant(
                constantName = "SETTINGS_SWITCH_PADDING_PX",
                regex = Regex("""SETTINGS_SWITCH_PADDING_PX\s*=\s*(\d+)"""),
            ),
            MinPaddingConstant(
                constantName = "SETTINGS_SWITCH_LABEL_HORIZONTAL_PADDING_PX",
                regex = Regex("""SETTINGS_SWITCH_LABEL_HORIZONTAL_PADDING_PX\s*=\s*(\d+)"""),
            ),
            MinPaddingConstant(
                constantName = "SETTINGS_SWITCH_LABEL_VERTICAL_PADDING_PX",
                regex = Regex("""SETTINGS_SWITCH_LABEL_VERTICAL_PADDING_PX\s*=\s*(\d+)"""),
            ),
            MinPaddingConstant(
                constantName = "SETTINGS_ROW_HORIZONTAL_PADDING_PX",
                regex = Regex("""SETTINGS_ROW_HORIZONTAL_PADDING_PX\s*=\s*(\d+)"""),
            ),
            MinPaddingConstant(
                constantName = "SETTINGS_ROW_VERTICAL_PADDING_PX",
                regex = Regex("""SETTINGS_ROW_VERTICAL_PADDING_PX\s*=\s*(\d+)"""),
            ),
            MinPaddingConstant(
                constantName = "SETTINGS_LABEL_VERTICAL_PADDING_PX",
                regex = Regex("""SETTINGS_LABEL_VERTICAL_PADDING_PX\s*=\s*(\d+)"""),
            ),
        )

        val drawerMatchReasonUiPatterns = listOf(
            ForbiddenPattern(Regex("""matchSourceLabel"""), "do not pass match reason labels into Drawer UI rows"),
            ForbiddenPattern(
                Regex("""DrawerSearchSupport\.matchSourceLabel"""),
                "do not render Drawer search match reason labels",
            ),
            ForbiddenPattern(Regex(""""(ALIAS|PINYIN|PKG|ACT)""""), "do not hard-code search reason tags in Drawer UI"),
        )
    }
}

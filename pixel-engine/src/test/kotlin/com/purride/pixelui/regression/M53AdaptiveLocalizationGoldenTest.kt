package com.purride.pixelui.regression

import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.AdaptiveBuilder
import com.purride.pixelui.Checkbox
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Directionality
import com.purride.pixelui.HostCapabilities
import com.purride.pixelui.HostCapabilitiesData
import com.purride.pixelui.ImeAvoidingView
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.MediaQuery
import com.purride.pixelui.MediaQueryData
import com.purride.pixelui.PixelAdaptiveEnvironment
import com.purride.pixelui.PixelAdaptiveLayoutData
import com.purride.pixelui.PixelDisplayFeature
import com.purride.pixelui.PixelDisplayFeatureState
import com.purride.pixelui.PixelDisplayFeatureType
import com.purride.pixelui.PixelLocale
import com.purride.pixelui.PixelLocalizationProvider
import com.purride.pixelui.PixelLogicalRect
import com.purride.pixelui.PixelSemanticsNode
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.PixelWindowInsets
import com.purride.pixelui.Row
import com.purride.pixelui.SafeArea
import com.purride.pixelui.Semantics
import com.purride.pixelui.Text
import com.purride.pixelui.TextDirection
import com.purride.pixelui.TextField
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.testing.PixelTester
import java.io.File
import org.junit.Test

/**
 * Reviewed M5-3F semantics and pixel goldens for adaptive/localization environment combinations.
 *
 * Ordinary test execution writes diagnostic candidates only under `build/reports`; it never
 * creates or updates either source-controlled baseline and has no REGEN acceptance path.
 */
class M53AdaptiveLocalizationGoldenTest {
    /** Immutable deterministic environment rendered in an isolated off-screen runtime. */
    private data class AcceptanceCase(
        /** Stable section name written to both review artifacts. */
        val name: String,
        /** Complete Host capability snapshot for this case. */
        val capabilities: HostCapabilitiesData,
        /** Logical viewport width supplied to MediaQuery and PixelTester. */
        val width: Int = 44,
        /** Logical viewport height supplied to MediaQuery and PixelTester. */
        val height: Int = 26,
        /** Stable safe-area insets consumed by SafeArea. */
        val viewPadding: PixelWindowInsets = PixelWindowInsets(left = 1, top = 1, right = 1, bottom = 1),
        /** Transient obstruction insets consumed by ImeAvoidingView. */
        val viewInsets: PixelWindowInsets = PixelWindowInsets.Zero,
    )

    /** Every adaptive environment matches the source-controlled semantic contract. */
    @Test
    fun adaptiveEnvironmentsMatchReviewedSemanticsSnapshot() {
        /** Canonical sections captured in the same order as [acceptanceCases]. */
        val actual = acceptanceCases().joinToString(separator = "\n", transform = ::captureSemantics)
        ReviewedGoldenVerifier.assertMatches(
            baselineFile = File(SEMANTICS_BASELINE_PATH),
            actual = actual,
            reportStem = File(REPORT_DIR_PATH, "m5-3-adaptive-localization-semantics"),
        )
    }

    /** Every adaptive environment matches the source-controlled ASCII pixel golden. */
    @Test
    fun adaptiveEnvironmentsMatchReviewedPixelGolden() {
        /** Exact brightness-bucket sections produced from fresh deterministic runtimes. */
        val actual = acceptanceCases().joinToString(separator = "\n", transform = ::capturePixels)
        ReviewedGoldenVerifier.assertMatches(
            baselineFile = File(PIXEL_BASELINE_PATH),
            actual = actual,
            reportStem = File(REPORT_DIR_PATH, "m5-3-adaptive-localization-pixels"),
        )
    }

    /** Defines LTR/RTL, 1x/2x text, contrast, compact-window and IME cases without mutation. */
    private fun acceptanceCases(): List<AcceptanceCase> {
        /** Shared English LTR baseline from which orthogonal changes are copied. */
        val base = HostCapabilitiesData(
            locales = listOf(PixelLocale.English),
            layoutDirection = TextDirection.LTR,
            textScaleFactor = 1f,
            highContrast = false,
            density = 1f,
            refreshRateHz = 60f,
        )
        return listOf(
            AcceptanceCase(name = "ltr-en-1x", capabilities = base),
            AcceptanceCase(
                name = "ltr-zh-1x",
                capabilities = base.copy(locales = listOf(PixelLocale("zh-CN"), PixelLocale.English)),
            ),
            AcceptanceCase(
                name = "rtl-ar-1x",
                capabilities = base.copy(
                    locales = listOf(PixelLocale("ar"), PixelLocale.English),
                    layoutDirection = TextDirection.RTL,
                ),
            ),
            AcceptanceCase(
                name = "ltr-en-2x-text",
                capabilities = base.copy(textScaleFactor = 2f),
            ),
            AcceptanceCase(
                name = "ltr-en-high-contrast",
                capabilities = base.copy(highContrast = true),
            ),
            AcceptanceCase(
                name = "compact-hinge",
                capabilities = base.copy(
                    density = 2f,
                    displayFeatures = listOf(
                        PixelDisplayFeature(
                            bounds = PixelLogicalRect(14f, 0f, 14f, 20f),
                            type = PixelDisplayFeatureType.HINGE,
                            state = PixelDisplayFeatureState.HALF_OPENED,
                        ),
                    ),
                ),
                width = 28,
                height = 20,
            ),
            AcceptanceCase(
                name = "small-window-ime",
                capabilities = base,
                width = 32,
                height = 22,
                viewPadding = PixelWindowInsets(left = 1, top = 2, right = 1, bottom = 1),
                viewInsets = PixelWindowInsets(bottom = 7),
            ),
        )
    }

    /** Captures canonical semantics from one independently disposed runtime. */
    private fun captureSemantics(case: AcceptanceCase): String {
        /** Runtime isolation prevents focus, semantic ids or retained state leaking across cases. */
        val tester = PixelTester()
        return try {
            tester.pumpWidget(case.buildWidget(), logicalWidth = case.width, logicalHeight = case.height)
            formatSemantics(case.name, tester.semanticsNodes())
        } finally {
            tester.dispose()
        }
    }

    /** Captures canonical ASCII pixels from one independently disposed runtime. */
    private fun capturePixels(case: AcceptanceCase): String {
        /** Runtime isolation keeps high-contrast and text-scale paint state case-local. */
        val tester = PixelTester()
        return try {
            tester.pumpWidget(case.buildWidget(), logicalWidth = case.width, logicalHeight = case.height)
            buildString {
                append("## ").append(case.name).append('\n')
                append(tester.dumpPixelsAsAscii())
            }
        } finally {
            tester.dispose()
        }
    }

    /** Builds the same atomic Host/Directionality/MediaQuery/adaptive scope order as PixelHostView. */
    private fun AcceptanceCase.buildWidget(): Widget {
        /** Fixed test profile matching the logical PixelTester viewport exactly. */
        val profile = ScreenProfile(
            logicalWidth = width,
            logicalHeight = height,
            dotSizePx = 1,
        )
        /** MediaQuery snapshot exposing stable and transient inset channels separately. */
        val media = MediaQueryData(
            logicalWidth = width,
            logicalHeight = height,
            screenProfile = profile,
            viewInsets = viewInsets,
            viewPadding = viewPadding,
            padding = PixelWindowInsets(
                left = viewPadding.left,
                top = viewPadding.top,
                right = viewPadding.right,
                bottom = (viewPadding.bottom - viewInsets.bottom).coerceAtLeast(0),
            ),
        )
        /** Adaptive snapshot paired with the exact same capability and inset frame. */
        val adaptive = PixelAdaptiveLayoutData(
            physicalWidthPx = (width * capabilities.density).toInt(),
            physicalHeightPx = (height * capabilities.density).toInt(),
            logicalWidth = width,
            logicalHeight = height,
            density = capabilities.density,
            viewInsets = media.viewInsets,
            viewPadding = media.viewPadding,
            padding = media.padding,
            displayFeatures = capabilities.displayFeatures,
        )
        return HostCapabilities(
            data = capabilities,
            child = Directionality(
                textDirection = capabilities.layoutDirection,
                child = MediaQuery(
                    data = media,
                    child = PixelAdaptiveEnvironment(
                        data = adaptive,
                        child = PixelLocalizationProvider(
                            child = PixelTheme(
                                tokens = PixelThemeTokens.forCapabilities(capabilities),
                                child = AdaptiveBuilder(
                                    builder = { _, data -> acceptanceContent(this, data) },
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    /** Builds visual direction, localization, text-scale, contrast, SafeArea and IME evidence. */
    private fun acceptanceContent(
        case: AcceptanceCase,
        adaptive: PixelAdaptiveLayoutData,
    ): Widget {
        /** Controlled field owner created once for this immutable snapshot build. */
        val textController = PixelTextFieldController()
        /** Grapheme-safe selection spanning the decomposed Latin suffix. */
        val textState = textController.create(
            initialText = "Cafe\u0301 👨‍👩‍👧‍👦 אבג",
            selectionStart = 3,
            selectionEnd = 5,
        )
        /** Deterministic environment summary frozen into the root semantic node. */
        val summary = buildString {
            append(case.capabilities.locales.first()).append('|')
            append(case.capabilities.layoutDirection).append('|')
            append(case.capabilities.textScaleFactor).append('|')
            append("contrast=").append(case.capabilities.highContrast).append('|')
            append(adaptive.widthSizeClass).append('/').append(adaptive.heightSizeClass).append('|')
            append("ime=").append(adaptive.viewInsets.bottom).append('|')
            append("features=").append(adaptive.displayFeatures.size)
        }
        /** Exact theme graph whose surface swatch makes contrast changes visible in pixel output. */
        val tokens = PixelThemeTokens.forCapabilities(case.capabilities)
        return SafeArea(
            child = ImeAvoidingView(
                child = Semantics(
                    label = "adaptive-${case.name}",
                    value = summary,
                    child = Container(
                        fillColor = tokens.colors.background,
                        child = Column(
                            children = listOf(
                                Text("中文 Cafe\u0301", style = TextStyle(color = PixelColor.White)),
                                Container(width = 17, height = 2, fillColor = tokens.colors.surfaceVariant),
                                Row(
                                    children = listOf(
                                        colorCell(PixelColor.fromRgb(240, 80, 80)),
                                        colorCell(PixelColor.fromRgb(80, 220, 120)),
                                        colorCell(PixelColor.fromRgb(80, 140, 255)),
                                    ),
                                    spacing = 1,
                                ),
                                Checkbox(checked = true, onChanged = {}, key = "golden-checkbox"),
                                TextField(
                                    state = textState,
                                    controller = textController,
                                    placeholder = "Unicode",
                                    minLines = 1,
                                    maxLines = 1,
                                    semanticLabel = "golden-input",
                                    key = "golden-text-field",
                                ),
                            ),
                            spacing = 1,
                            mainAxisSize = MainAxisSize.MIN,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                    ),
                ),
            ),
        )
    }

    /** Creates one asymmetric direction-sensitive color marker. */
    private fun colorCell(color: PixelColor): Widget {
        return Container(width = 5, height = 3, fillColor = color)
    }

    /** Formats allocator-independent semantics with geometry and localized control state. */
    private fun formatSemantics(name: String, nodes: List<PixelSemanticsNode>): String {
        /** Runtime ids replaced with stable section-local indexes. */
        val indexes = nodes.mapIndexed { index, node -> node.id to index }.toMap()
        return buildString {
            append("## ").append(name).append('\n')
            append("nodes=").append(nodes.size).append('\n')
            nodes.forEachIndexed { index, node ->
                /** Parent rendered as a canonical index or HOST for a root node. */
                val parent = node.parentId?.let(indexes::get)?.toString() ?: "HOST"
                append('[').append(index).append("] parent=").append(parent)
                append(" role=").append(node.role)
                append(" label=").append(quoted(node.label))
                append(" value=").append(quoted(node.value))
                append(" bounds=")
                    .append(node.left).append(',')
                    .append(node.top).append(',')
                    .append(node.width).append(',')
                    .append(node.height)
                append(" checked=").append(node.checked ?: "NA")
                append(" selection=").append(node.selectionStart).append("..").append(node.selectionEnd)
                append(" actions=")
                    .append(node.actions.map { action -> action.name }.sorted())
                append('\n')
            }
        }
    }

    /** Escapes nullable semantics text into one deterministic line. */
    private fun quoted(value: String?): String {
        if (value == null) return "NA"
        /** Escaped content preventing CRLF, slash or quote ambiguity in reviewed diffs. */
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\"", "\\\"")
        return "\"$escaped\""
    }

    /** Frozen baseline and build-report paths for both M5-3F artifacts. */
    private companion object {
        /** Reviewed semantic snapshot path relative to the engine module. */
        const val SEMANTICS_BASELINE_PATH: String =
            "src/test/resources/element-snapshots/m5-3-adaptive-localization.txt"

        /** Reviewed pixel golden path relative to the engine module. */
        const val PIXEL_BASELINE_PATH: String =
            "src/test/resources/golden/m5-3-adaptive-localization.txt"

        /** 候选与审阅差异的构建报告目录。 */
        const val REPORT_DIR_PATH: String = "build/reports/golden/m5-3"
    }
}

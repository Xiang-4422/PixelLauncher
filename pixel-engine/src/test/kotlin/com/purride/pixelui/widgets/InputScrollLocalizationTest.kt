package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.IconButton
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelButtonStyle
import com.purride.pixelui.PixelControlState
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelIconData
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelLabelTokens
import com.purride.pixelui.PixelLocale
import com.purride.pixelui.PixelLocalizationBundle
import com.purride.pixelui.PixelLocalizations
import com.purride.pixelui.PixelPercentFormatter
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.Radio
import com.purride.pixelui.RefreshIndicator
import com.purride.pixelui.Scrollbar
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Slider
import com.purride.pixelui.TextButton
import com.purride.pixelui.TextField
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelRefreshIndicatorController
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 输入与滚动家族的提供者优先级、格式化、retained 状态与渲染中立性覆盖。 */
class InputScrollLocalizationTest {
    /** Provider defaults beat theme labels while explicit values remain caller-owned. */
    @Test
    fun providerLocalizesInputDefaultsAndFormatsSliderPercent() {
        /** Complete provider label set with distinguishable values for every exercised family. */
        val providerLabels = PixelLabelTokens.Default.copy(
            button = "PROVIDER BUTTON",
            textButton = "PROVIDER TEXT BUTTON",
            textField = "PROVIDER TEXT FIELD",
            slider = "PROVIDER SLIDER",
        )
        /** Custom bundle proving Slider delegates its generated value to the provider formatter. */
        val bundle = PixelLocalizationBundle(
            locale = PixelLocale("xx"),
            labels = providerLabels,
            navigationBar = "PROVIDER BAR",
            navigationRail = "PROVIDER RAIL",
            percentFormatter = PixelPercentFormatter { fraction -> "PERCENT[$fraction]" },
        )
        /** Theme labels differ from both provider and English fallbacks to expose precedence. */
        val themeLabels = PixelLabelTokens.Default.copy(
            button = "THEME BUTTON",
            textButton = "THEME TEXT BUTTON",
            textField = "THEME TEXT FIELD",
            slider = "THEME SLIDER",
        )
        /** Controlled text state reused only for the real public TextField render. */
        val textController = PixelTextFieldController()
        /** Empty value keeps the semantic name independent from editable content. */
        val textState = textController.create(initialText = "")
        /** Reused off-screen runtime renders each public factory under identical inherited scopes. */
        val tester = PixelTester()
        try {
            pumpLocalized(
                tester = tester,
                bundle = bundle,
                themeLabels = themeLabels,
                child = OutlinedButton(text = "", onPressed = {}),
            )
            assertEquals(1, tester.semanticsNodesByLabel(providerLabels.button).size)

            pumpLocalized(
                tester = tester,
                bundle = bundle,
                themeLabels = themeLabels,
                child = TextButton(text = "", onPressed = {}),
            )
            assertEquals(1, tester.semanticsNodesByLabel(providerLabels.textButton).size)

            pumpLocalized(
                tester = tester,
                bundle = bundle,
                themeLabels = themeLabels,
                child = TextField(
                    state = textState,
                    controller = textController,
                    placeholder = "",
                ),
            )
            assertEquals(1, tester.semanticsNodesByLabel(providerLabels.textField).size)

            pumpLocalized(
                tester = tester,
                bundle = bundle,
                themeLabels = themeLabels,
                child = Slider(value = 0.25f, onDrag = {}, onRelease = {}),
            )
            /** Provider label and formatted value must land on the same real Slider node. */
            val slider = tester.semanticsNodesByLabel(providerLabels.slider).single()
            assertEquals("PERCENT[0.25]", slider.value)

            pumpLocalized(
                tester = tester,
                bundle = bundle,
                themeLabels = themeLabels,
                child = Slider(
                    value = 0.25f,
                    onDrag = {},
                    onRelease = {},
                    semanticLabel = "EXPLICIT SLIDER",
                    semanticValue = "EXPLICIT VALUE",
                ),
            )
            /** Explicit component text remains above both provider and theme layers. */
            val explicitSlider = tester.semanticsNodesByLabel("EXPLICIT SLIDER").single()
            assertEquals("EXPLICIT VALUE", explicitSlider.value)
            assertTrue(tester.semanticsNodesByLabel(providerLabels.slider).isEmpty())
        } finally {
            tester.dispose()
        }
    }

    /** Mandatory names stay explicit while provider text supplies status and scroll defaults. */
    @Test
    fun providerLocalizesSelectionAndScrollStatusWithoutReplacingBusinessNames() {
        /** Provider values distinguish every optional semantic channel exercised below. */
        val labels = PixelLabelTokens.Default.copy(
            loading = "PROVIDER LOADING",
            error = "PROVIDER ERROR",
            refresh = "PROVIDER REFRESH",
            scrollbar = "PROVIDER SCROLLBAR",
        )
        /** Exact custom bundle installed explicitly for this component batch. */
        val bundle = localizationBundle(labels)
        /** Combined state exposes both status fields without introducing pointer interaction. */
        val statusStates = PixelControlStateSet.of(
            PixelControlState.Loading,
            PixelControlState.Error,
        )
        /** Refresh controller owns the public controlled lifecycle used by the real component. */
        val refreshController = PixelRefreshIndicatorController()
        /** Idle refresh state receives caller Loading/Error independently from lifecycle phase. */
        val refreshState = refreshController.create()
        /** List controller supplies genuine viewport geometry to the Scrollbar render target. */
        val listController = PixelListController()
        /** Controlled list state shared by the list and Scrollbar wrapper. */
        val listState = listController.create()
        /** Tiny opaque icon exercises the real icon-only selection component. */
        val icon = PixelIconData(
            PixelBitmap(width = 1, height = 1, pixels = intArrayOf(PixelColor.White.argb)),
        )
        /** Reused runtime keeps all assertions on production factories rather than source strings. */
        val tester = PixelTester()
        try {
            pumpLocalized(
                tester = tester,
                bundle = bundle,
                child = Radio(
                    selected = false,
                    onSelected = {},
                    semanticLabel = "BUSINESS RADIO",
                    states = statusStates,
                ),
            )
            /** Required Radio name remains explicit while optional status text is localized. */
            val radio = tester.semanticsNodesByLabel("BUSINESS RADIO").single()
            assertEquals(labels.loading, radio.value)
            assertEquals(labels.error, radio.error)

            pumpLocalized(
                tester = tester,
                bundle = bundle,
                child = IconButton(
                    icon = icon,
                    onPressed = {},
                    semanticLabel = "BUSINESS ICON",
                    states = statusStates,
                ),
            )
            /** Required IconButton name follows the same business-name/status split. */
            val iconButton = tester.semanticsNodesByLabel("BUSINESS ICON").single()
            assertEquals(labels.loading, iconButton.value)
            assertEquals(labels.error, iconButton.error)

            pumpLocalized(
                tester = tester,
                bundle = bundle,
                child = RefreshIndicator(
                    child = SizedBox(width = 24, height = 12),
                    state = refreshState,
                    controller = refreshController,
                    states = statusStates,
                    onRefresh = {},
                ),
            )
            /** Refresh default name and both status fields come from the installed provider. */
            val refresh = tester.semanticsNodesByLabel(labels.refresh).single()
            assertEquals(labels.loading, refresh.value)
            assertEquals(labels.error, refresh.error)

            /** Overflowing real list ensures the Scrollbar has a mounted scroll target. */
            val list = ListViewBuilder(
                itemCount = 8,
                itemBuilder = { SizedBox(height = 8) },
                itemExtent = 8,
                state = listState,
                controller = listController,
            )
            pumpLocalized(
                tester = tester,
                bundle = bundle,
                child = Scrollbar(
                    child = list,
                    state = listState,
                    states = statusStates,
                ),
            )
            /** Scrollbar owns localized wrapper semantics while its child retains scroll actions. */
            val scrollbar = tester.semanticsNodesByLabel(labels.scrollbar).single()
            assertEquals(labels.loading, scrollbar.value)
            assertEquals(labels.error, scrollbar.error)
        } finally {
            tester.dispose()
        }
    }

    /** Provider replacement changes only text and retains TextField edit/focus identity. */
    @Test
    fun providerUpdateRetainsTextSelectionCompositionFocusAndSemanticIdentity() {
        /** Controlled state object whose identity and complete edit ranges must survive rebuilding. */
        val controller = PixelTextFieldController()
        /** Non-empty value supports distinct selection and composition ranges. */
        val state = controller.create(initialText = "pixel engine")
        controller.setSelection(state = state, selectionStart = 2, selectionEnd = 7)
        controller.updateComposition(state = state, compositionStart = 1, compositionEnd = 5)
        /** Mutable bundle models an application switching localization at runtime. */
        var bundle = PixelLocalizationBundle.English
        /** Stable declaration factory preserves provider and TextField keys across locale updates. */
        fun buildTree(): Widget = PixelLocalizations(
            locale = bundle.locale,
            bundle = bundle,
            child = TextField(
                state = state,
                controller = controller,
                placeholder = "",
                key = "localized-field",
            ),
            key = "localization-provider",
        )
        /** Retained runtime exposes semantic identity and real focus traversal. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(buildTree(), logicalWidth = 120, logicalHeight = 28)
            assertTrue(tester.pressKey(PixelKey.TAB))
            /** English node identity captured before replacing the inherited bundle. */
            val englishNode = tester.semanticsNodesByLabel("Text field").single()
            assertTrue(englishNode.focused)

            bundle = PixelLocalizationBundle.Chinese
            tester.pumpWidget(buildTree(), logicalWidth = 120, logicalHeight = 28)

            /** Localized node keeps its retained semantics identity and focus ownership. */
            val chineseNode = tester.semanticsNodesByLabel("文本框").single()
            assertEquals(englishNode.id, chineseNode.id)
            assertTrue(chineseNode.focused)
            assertEquals("pixel engine", state.text)
            assertEquals(2, state.selectionStart)
            assertEquals(7, state.selectionEnd)
            assertEquals(1, state.compositionStart)
            assertEquals(5, state.compositionEnd)
        } finally {
            tester.dispose()
        }
    }

    /** 挂载本地化提供者不会改变简洁按钮入口的任何输出像素。 */
    @Test
    fun providerPresenceDoesNotChangeConciseButtonPixels() {
        /** Explicit fill sentinel makes a visual-branch regression immediately observable. */
        val fill = PixelColor.fromRgb(37, 83, 149)
        /** 显式边框哨兵色属于调用方具体样式通道。 */
        val border = PixelColor.fromRgb(227, 61, 89)
        /** Explicit text sentinel ensures provider text resolution is a no-op for this comparison. */
        val text = PixelColor.fromRgb(247, 193, 41)
        /** 在两棵继承树中逐字复用的简洁入口声明。 */
        val button = OutlinedButton(
            text = "UNCHANGED",
            onPressed = {},
            style = PixelButtonStyle(
                fillColor = fill,
                borderColor = border,
                textStyle = PixelTextStyle(color = text),
            ),
        )
        /** Off-screen runtime captures every logical output pixel before and after provider insertion. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(button, logicalWidth = 96, logicalHeight = 20)
            /** 挂载提供者前的完整帧作为精确参考。 */
            val baselinePixels = capturePixels(tester = tester, width = 96, height = 20)

            tester.pumpWidget(
                PixelLocalizations(
                    locale = PixelLocale.English,
                    bundle = PixelLocalizationBundle.English,
                    child = button,
                ),
                logicalWidth = 96,
                logicalHeight = 20,
            )
            /** 英文提供者不改变任何显式文本，因此每个像素都必须一致。 */
            val localizedPixels = capturePixels(tester = tester, width = 96, height = 20)
            assertEquals(baselinePixels, localizedPixels)
            assertEquals(1, tester.semanticsNodesByLabel("UNCHANGED").size)
        } finally {
            tester.dispose()
        }
    }

    /** Wraps one production component in distinguishable theme and explicit localization scopes. */
    private fun pumpLocalized(
        tester: PixelTester,
        bundle: PixelLocalizationBundle,
        child: Widget,
        themeLabels: PixelLabelTokens = PixelLabelTokens.Default,
    ) {
        tester.pumpWidget(
            widget = PixelTheme(
                tokens = PixelThemeTokens.Default.copy(labels = themeLabels),
                child = PixelLocalizations(
                    locale = bundle.locale,
                    bundle = bundle,
                    child = child,
                ),
            ),
            logicalWidth = 160,
            logicalHeight = 72,
        )
    }

    /** Creates an exact provider bundle for the supplied complete [labels]. */
    private fun localizationBundle(labels: PixelLabelTokens): PixelLocalizationBundle {
        return PixelLocalizationBundle(
            locale = PixelLocale("xx"),
            labels = labels,
            navigationBar = "PROVIDER BAR",
            navigationRail = "PROVIDER RAIL",
        )
    }

    /** Captures one complete logical frame in deterministic row-major order. */
    private fun capturePixels(tester: PixelTester, width: Int, height: Int): List<PixelColor> {
        return buildList(width * height) {
            repeat(height) { y ->
                repeat(width) { x -> add(tester.pixelAt(x = x, y = y)) }
            }
        }
    }
}

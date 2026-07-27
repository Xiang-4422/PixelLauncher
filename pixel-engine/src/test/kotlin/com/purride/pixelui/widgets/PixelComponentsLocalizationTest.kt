package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.ActivityIndicator
import com.purride.pixelui.BottomSheet
import com.purride.pixelui.Checkbox
import com.purride.pixelui.ConfirmDialog
import com.purride.pixelui.Dialog
import com.purride.pixelui.EmptyState
import com.purride.pixelui.ListTile
import com.purride.pixelui.ModalBarrier
import com.purride.pixelui.PixelControlState
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelIntegerFormatter
import com.purride.pixelui.PixelLabelTokens
import com.purride.pixelui.PixelLoadingBar
import com.purride.pixelui.PixelLocale
import com.purride.pixelui.PixelLocalizationBundle
import com.purride.pixelui.PixelLocalizations
import com.purride.pixelui.PixelPercentFormatter
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.SegmentedControl
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Snackbar
import com.purride.pixelui.Stack
import com.purride.pixelui.Stepper
import com.purride.pixelui.Switch
import com.purride.pixelui.Tabs
import com.purride.pixelui.Text
import com.purride.pixelui.Toast
import com.purride.pixelui.ValueAdjuster
import com.purride.pixelui.Widget
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PixelComponents 的本地化优先级、格式化与渲染中立性覆盖。 */
class PixelComponentsLocalizationTest {
    /** Built-in Chinese labels replace theme defaults across controls and compound selectors. */
    @Test
    fun chineseProviderLocalizesCoreControlFamiliesAndStatuses() {
        /** Combined state exposing both localized dynamic semantic fields. */
        val statusStates = PixelControlStateSet.of(
            PixelControlState.Loading,
            PixelControlState.Error,
        )
        /** Theme labels deliberately distinct from the installed Chinese provider. */
        val themeLabels = PixelLabelTokens.Default.copy(
            listTile = "THEME LIST",
            checkbox = "THEME CHECKBOX",
            switch = "THEME SWITCH",
            tabs = "THEME TABS",
            segmentedControl = "THEME SEGMENTED",
            loading = "THEME LOADING",
            error = "THEME ERROR",
        )
        /** Reused production runtime for every public component family. */
        val tester = PixelTester()
        try {
            pumpLocalized(
                tester = tester,
                bundle = PixelLocalizationBundle.Chinese,
                themeLabels = themeLabels,
                child = ListTile(
                    title = Text("ROW"),
                    states = statusStates,
                    onTap = {},
                ),
            )
            /** ListTile node proving provider and both status fields share one resolution layer. */
            val listTile = tester.semanticsNodesByLabel("列表项").single()
            assertEquals("加载中", listTile.value)
            assertEquals("错误", listTile.error)

            pumpLocalized(
                tester = tester,
                bundle = PixelLocalizationBundle.Chinese,
                themeLabels = themeLabels,
                child = Checkbox(
                    checked = false,
                    onChanged = {},
                    states = statusStates,
                ),
            )
            /** Checkbox node proving provider labels beat explicit theme tokens. */
            val checkbox = tester.semanticsNodesByLabel("复选框").single()
            assertEquals("加载中", checkbox.value)
            assertEquals("错误", checkbox.error)

            pumpLocalized(
                tester = tester,
                bundle = PixelLocalizationBundle.Chinese,
                themeLabels = themeLabels,
                child = Switch(
                    checked = false,
                    onChanged = {},
                    states = statusStates,
                ),
            )
            /** Switch node proving localized text remains independent from motion visuals. */
            val switch = tester.semanticsNodesByLabel("开关").single()
            assertEquals("加载中", switch.value)
            assertEquals("错误", switch.error)

            pumpLocalized(
                tester = tester,
                bundle = PixelLocalizationBundle.Chinese,
                themeLabels = themeLabels,
                child = Tabs(
                    labels = listOf("ONE", "TWO"),
                    selectedIndex = 0,
                    onSelected = {},
                    states = statusStates,
                ),
            )
            assertEquals(1, tester.semanticsNodesByLabel("标签页").size)
            /** Caller-owned tab name remains explicit while status text is localized. */
            val tab = tester.semanticsNodesByLabel("ONE").single()
            assertEquals("加载中", tab.value)
            assertEquals("错误", tab.error)

            pumpLocalized(
                tester = tester,
                bundle = PixelLocalizationBundle.Chinese,
                themeLabels = themeLabels,
                child = SegmentedControl(
                    labels = listOf("LEFT", "RIGHT"),
                    selectedIndex = 0,
                    onSelected = {},
                    states = statusStates,
                ),
            )
            assertEquals(1, tester.semanticsNodesByLabel("分段控件").size)
            /** Caller-owned segment name keeps precedence over every localization layer. */
            val segment = tester.semanticsNodesByLabel("LEFT").single()
            assertEquals("加载中", segment.value)
            assertEquals("错误", segment.error)
        } finally {
            tester.dispose()
        }
    }

    /** A consumer bundle localizes modal and notification defaults including all status fields. */
    @Test
    fun customProviderLocalizesModalAndNotificationFamilies() {
        /** Complete custom labels for every modal and notification role exercised below. */
        val labels = PixelLabelTokens.Default.copy(
            confirm = "CUSTOM CONFIRM",
            cancel = "CUSTOM CANCEL",
            dismiss = "CUSTOM DISMISS",
            dialog = "CUSTOM DIALOG",
            bottomSheet = "CUSTOM SHEET",
            toast = "CUSTOM TOAST",
            snackbar = "CUSTOM SNACKBAR",
            loading = "CUSTOM LOADING",
            error = "CUSTOM ERROR",
        )
        /** Exact custom bundle installed explicitly by the application. */
        val bundle = localizationBundle(labels = labels)
        /** Combined state exposing localized Loading and Error fields. */
        val statusStates = PixelControlStateSet.of(
            PixelControlState.Loading,
            PixelControlState.Error,
        )
        /** Reused runtime rendering every public modal and notification factory. */
        val tester = PixelTester()
        try {
            pumpLocalized(
                tester = tester,
                bundle = bundle,
                child = Dialog(
                    content = SizedBox(width = 6, height = 4),
                    states = statusStates,
                    modal = false,
                ),
            )
            /** Dialog node with provider-owned name and status fields. */
            val dialog = tester.semanticsNodesByLabel(labels.dialog).single()
            assertEquals(labels.loading, dialog.value)
            assertEquals(labels.error, dialog.error)

            pumpLocalized(
                tester = tester,
                bundle = bundle,
                child = BottomSheet(
                    content = SizedBox(width = 6, height = 4),
                    states = statusStates,
                    modal = false,
                ),
            )
            /** BottomSheet node following the same provider status contract. */
            val sheet = tester.semanticsNodesByLabel(labels.bottomSheet).single()
            assertEquals(labels.loading, sheet.value)
            assertEquals(labels.error, sheet.error)

            pumpLocalized(
                tester = tester,
                bundle = bundle,
                child = ConfirmDialog(
                    title = "BUSINESS TITLE",
                    message = "BUSINESS MESSAGE",
                    onConfirm = {},
                    states = PixelControlStateSet.Normal,
                    onCancel = {},
                ),
            )
            assertEquals(1, tester.semanticsNodesByLabel(labels.confirm).size)
            assertEquals(1, tester.semanticsNodesByLabel(labels.cancel).size)

            pumpLocalized(
                tester = tester,
                bundle = bundle,
                child = Stack(
                    children = listOf(
                        ModalBarrier(
                            states = PixelControlStateSet.Normal,
                            dismissible = true,
                            onDismiss = {},
                        ),
                    ),
                ),
            )
            assertEquals(1, tester.semanticsNodesByLabel(labels.dismiss).size)

            pumpLocalized(
                tester = tester,
                bundle = bundle,
                child = Toast(message = "", states = statusStates),
            )
            /** 空白 Toast 消息按省略处理，解析主题 label token。 */
            val toast = tester.semanticsNodesByLabel(labels.toast).single()
            assertEquals(labels.loading, toast.value)
            assertEquals(labels.error, toast.error)

            pumpLocalized(
                tester = tester,
                bundle = bundle,
                child = Snackbar(message = "", states = statusStates),
            )
            /** Blank Snackbar message also resolves through provider before theme. */
            val snackbar = tester.semanticsNodesByLabel(labels.snackbar).first()
            assertEquals(labels.loading, snackbar.value)
            assertEquals(labels.error, snackbar.error)
        } finally {
            tester.dispose()
        }
    }

    /** Custom formatters drive generated Stepper and progress text while explicit values win. */
    @Test
    fun customProviderFormatsValueProgressAndEmptyFamilies() {
        /** Custom labels shared by adjuster, progress, and empty-state components. */
        val labels = PixelLabelTokens.Default.copy(
            valueAdjuster = "CUSTOM ADJUSTER",
            decrease = "CUSTOM DECREASE",
            increase = "CUSTOM INCREASE",
            progress = "CUSTOM PROGRESS",
            empty = "CUSTOM EMPTY",
            loading = "CUSTOM LOADING",
            error = "CUSTOM ERROR",
        )
        /** Custom bundle with observable integer and percentage formatters. */
        val bundle = localizationBundle(
            labels = labels,
            integerFormatter = PixelIntegerFormatter { value -> "INTEGER[$value]" },
            percentFormatter = PixelPercentFormatter { fraction -> "PERCENT[$fraction]" },
        )
        /** Combined status state for components that expose both semantic channels. */
        val statusStates = PixelControlStateSet.of(
            PixelControlState.Loading,
            PixelControlState.Error,
        )
        /** Reused runtime exercising production value and progress factories. */
        val tester = PixelTester()
        try {
            pumpLocalized(
                tester = tester,
                bundle = bundle,
                child = ValueAdjuster(
                    valueText = "7",
                    onDecrease = {},
                    onIncrease = {},
                    states = statusStates,
                ),
            )
            /** Group status and virtual action names all resolve from the same bundle. */
            val adjuster = tester.semanticsNodesByLabel(labels.valueAdjuster).single()
            assertEquals(labels.loading, adjuster.value)
            assertEquals(labels.error, adjuster.error)
            assertEquals(1, tester.semanticsNodesByLabel(labels.decrease).size)
            assertEquals(1, tester.semanticsNodesByLabel(labels.increase).size)

            pumpLocalized(
                tester = tester,
                bundle = bundle,
                child = Stepper(
                    value = 42,
                    range = 0..100,
                    onChanged = {},
                    states = PixelControlStateSet.Normal,
                ),
            )
            assertEquals("INTEGER[42]", tester.semanticsNodesByLabel(labels.valueAdjuster).single().value)

            pumpLocalized(
                tester = tester,
                bundle = bundle,
                child = Stepper(
                    value = 42,
                    range = 0..100,
                    onChanged = {},
                    states = PixelControlStateSet.Normal,
                    valueText = "EXPLICIT VALUE",
                ),
            )
            assertEquals("EXPLICIT VALUE", tester.semanticsNodesByLabel(labels.valueAdjuster).single().value)

            pumpLocalized(
                tester = tester,
                bundle = bundle,
                child = ProgressBar(
                    progress = 0.25f,
                    states = PixelControlStateSet.of(PixelControlState.Error),
                ),
            )
            /** Determinate progress uses the custom formatter after safe normalization. */
            val progress = tester.semanticsNodesByLabel(labels.progress).single()
            assertEquals("PERCENT[0.25]", progress.value)
            assertEquals(labels.error, progress.error)

            pumpLocalized(
                tester = tester,
                bundle = bundle,
                child = PixelLoadingBar(progress = 0.5f, states = statusStates),
            )
            /** Indeterminate-style loading bar uses localized Loading rather than a percentage. */
            val loadingBar = tester.semanticsNodesByLabel(labels.progress).single()
            assertEquals(labels.loading, loadingBar.value)
            assertEquals(labels.error, loadingBar.error)

            pumpLocalized(
                tester = tester,
                bundle = bundle,
                child = ActivityIndicator(states = statusStates),
            )
            /** ActivityIndicator follows the same progress/status label contract. */
            val activity = tester.semanticsNodesByLabel(labels.progress).single()
            assertEquals(labels.loading, activity.value)
            assertEquals(labels.error, activity.error)

            pumpLocalized(
                tester = tester,
                bundle = bundle,
                child = EmptyState(states = statusStates),
            )
            /** Empty fallback title and both status fields come from the provider. */
            val empty = tester.semanticsNodesByLabel(labels.empty).single { node ->
                node.value == labels.loading
            }
            assertEquals(labels.loading, empty.value)
            assertEquals(labels.error, empty.error)
        } finally {
            tester.dispose()
        }
    }

    /** Explicit component text stays first while a provider-free component uses theme labels. */
    @Test
    fun explicitTextWinsAndThemeRemainsProviderFreeFallback() {
        /** Provider labels that must lose to explicit component text. */
        val providerLabels = PixelLabelTokens.Default.copy(
            listTile = "PROVIDER LIST",
            checkbox = "PROVIDER CHECKBOX",
            loading = "PROVIDER LOADING",
            error = "PROVIDER ERROR",
        )
        /** Theme labels that lose to provider when installed but win without it. */
        val themeLabels = PixelLabelTokens.Default.copy(
            listTile = "THEME LIST",
            checkbox = "THEME CHECKBOX",
            loading = "THEME LOADING",
            error = "THEME ERROR",
        )
        /** Custom provider bundle above the distinguishable theme labels. */
        val bundle = localizationBundle(providerLabels)
        /** Combined status state used by both explicit and theme-only assertions. */
        val statusStates = PixelControlStateSet.of(
            PixelControlState.Loading,
            PixelControlState.Error,
        )
        /** Reused runtime for provider and provider-free theme trees. */
        val tester = PixelTester()
        try {
            pumpLocalized(
                tester = tester,
                bundle = bundle,
                themeLabels = themeLabels,
                child = ListTile(
                    title = Text("ROW"),
                    states = statusStates,
                    onTap = {},
                    semanticLabel = "EXPLICIT LIST",
                ),
            )
            /** Explicit stable name coexists with provider-owned status text. */
            val explicit = tester.semanticsNodesByLabel("EXPLICIT LIST").single()
            assertEquals(providerLabels.loading, explicit.value)
            assertEquals(providerLabels.error, explicit.error)

            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = PixelThemeTokens.Default.copy(labels = themeLabels),
                    child = Checkbox(
                        checked = false,
                        onChanged = {},
                        states = statusStates,
                    ),
                ),
                logicalWidth = 96,
                logicalHeight = 32,
            )
            /** Existing theme labels remain the complete fallback without a provider. */
            val themed = tester.semanticsNodesByLabel(themeLabels.checkbox).single()
            assertEquals(themeLabels.loading, themed.value)
            assertEquals(themeLabels.error, themed.error)
        } finally {
            tester.dispose()
        }
    }

    /** 显式空白值策略按组件而定，并在挂载提供者后保持不变。 */
    @Test
    fun explicitBlankPolicyRemainsComponentSpecific() {
        /** 仅在该入口把空白视为省略时才会生效的提供者标签。 */
        val labels = PixelLabelTokens.Default.copy(
            listTile = "PROVIDER LIST",
            checkbox = "PROVIDER CHECKBOX",
            confirm = "PROVIDER CONFIRM",
            cancel = "PROVIDER CANCEL",
            button = "PROVIDER BUTTON",
            textButton = "PROVIDER TEXT BUTTON",
            toast = "PROVIDER TOAST",
            snackbar = "PROVIDER SNACKBAR",
        )
        /** 供简洁入口与状态化入口空白检查共用的精确自定义提供者。 */
        val bundle = localizationBundle(labels)
        /** Reused runtime preserving production build-time validation behavior. */
        val tester = PixelTester()
        try {
            pumpLocalized(
                tester = tester,
                bundle = bundle,
                child = ListTile(
                    title = Text("ROW"),
                    states = PixelControlStateSet.Normal,
                    onTap = {},
                    semanticLabel = " ",
                ),
            )
            assertEquals(1, tester.semanticsNodesByLabel(" ").size)

            tester.pumpWidget(
                widget = PixelLocalizations(
                    locale = bundle.locale,
                    bundle = bundle,
                    child = Checkbox(
                        checked = false,
                        onChanged = {},
                        semanticLabel = "\t",
                    ),
                ),
                logicalWidth = 32,
                logicalHeight = 20,
            )
            /** 简洁入口保留调用方显式传入的空白朗读名称。 */
            assertEquals(1, tester.semanticsNodesByLabel("\t").size)

            pumpLocalized(
                tester = tester,
                bundle = bundle,
                child = ConfirmDialog(
                    title = "TITLE",
                    message = "MESSAGE",
                    onConfirm = {},
                    states = PixelControlStateSet.Normal,
                    onCancel = {},
                    confirmText = " ",
                    cancelText = "\t",
                ),
            )
            /** ConfirmDialog keeps blanks instead of substituting confirm/cancel provider labels. */
            assertTrue(tester.semanticsNodesByLabel(labels.confirm).isEmpty())
            assertTrue(tester.semanticsNodesByLabel(labels.cancel).isEmpty())
            /** Nested button contracts independently treat blank visible text as their omission. */
            assertEquals(1, tester.semanticsNodesByLabel(labels.button).size)
            assertEquals(1, tester.semanticsNodesByLabel(labels.textButton).size)

            pumpLocalized(
                tester = tester,
                bundle = bundle,
                child = Toast(message = "", states = PixelControlStateSet.Normal),
            )
            /** Toast 把空白消息视为省略而不是显式空白。 */
            assertEquals(1, tester.semanticsNodesByLabel(labels.toast).size)

            pumpLocalized(
                tester = tester,
                bundle = bundle,
                child = Snackbar(message = "", states = PixelControlStateSet.Normal),
            )
            /** Snackbar retains the same blank-as-omitted policy. */
            assertTrue(tester.semanticsNodesByLabel(labels.snackbar).isNotEmpty())

            tester.pumpWidget(
                widget = PixelLocalizations(
                    locale = bundle.locale,
                    bundle = bundle,
                    child = Toast(message = ""),
                ),
                logicalWidth = 64,
                logicalHeight = 24,
            )
            /** 无提供者的简洁 Toast 同样把空字符串视为省略并解析主题标签。 */
            assertEquals(1, tester.semanticsNodesByLabel(labels.toast).size)

            tester.pumpWidget(
                widget = PixelLocalizations(
                    locale = bundle.locale,
                    bundle = bundle,
                    child = Snackbar(message = ""),
                ),
                logicalWidth = 64,
                logicalHeight = 24,
            )
            /** 无提供者的简洁 Snackbar 遵循同一空白消息策略。 */
            assertTrue(tester.semanticsNodesByLabel(labels.snackbar).isNotEmpty())
        } finally {
            tester.dispose()
        }
    }

    /** 挂载本地化提供者只改变文本，绝不改变任何组件像素。 */
    @Test
    fun providerPresenceDoesNotChangeComponentPixels() {
        /** 显式勾选色，可暴露 Checkbox 被意外改色。 Explicit checked color making an accidental Checkbox recolor observable. */
        val checkboxActive = PixelColor.fromRgb(37, 149, 83)
        /** 作为第二个端点保留的显式未勾选色。 Explicit unchecked color retained as a second endpoint. */
        val checkboxInactive = PixelColor.fromRgb(191, 61, 113)
        /** 在插入提供者前后复用的稳定简洁 Checkbox 声明。 Stable concise Checkbox declaration reused before and after provider insertion. */
        val checkbox = Checkbox(
            checked = true,
            onChanged = {},
            activeColor = checkboxActive,
            inactiveColor = checkboxInactive,
        )
        /** Explicit selected Switch endpoint exposing any visual-branch change. */
        val switchActive = PixelColor.fromRgb(227, 193, 41)
        /** Explicit unselected Switch endpoint retained for completeness. */
        val switchInactive = PixelColor.fromRgb(53, 107, 211)
        /** 跨本地化边界复用的稳定简洁 Switch 声明。 Stable concise Switch declaration reused across the localization boundary. */
        val switch = Switch(
            checked = true,
            onChanged = {},
            activeColor = switchActive,
            inactiveColor = switchInactive,
        )
        /** 用于冻结绘制输出的显式进度前景色。 Explicit progress foreground used to freeze the painted output. */
        val progressFill = PixelColor.fromRgb(83, 173, 47)
        /** 用于冻结未填充像素的显式进度轨道色。 Explicit progress track used to freeze unfilled pixels. */
        val progressTrack = PixelColor.fromRgb(29, 43, 71)
        /** Custom progress label and formatter installed without a PixelTheme. */
        val progressBundle = localizationBundle(
            labels = PixelLabelTokens.Default.copy(progress = "CUSTOM PROGRESS"),
            percentFormatter = PixelPercentFormatter { fraction -> "CUSTOM[$fraction]" },
        )
        /** Reused runtime comparing complete frames around each provider insertion. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(checkbox, logicalWidth = 32, logicalHeight = 20)
            /** 挂载提供者前的帧与语义基线。 Pre-provider frame and semantic baseline. */
            val checkboxPixels = capturePixels(tester, width = 32, height = 20)
            assertEquals(1, tester.semanticsNodesByLabel("Checkbox").size)

            tester.pumpWidget(
                PixelLocalizations(
                    locale = PixelLocalizationBundle.Chinese.locale,
                    bundle = PixelLocalizationBundle.Chinese,
                    child = checkbox,
                ),
                logicalWidth = 32,
                logicalHeight = 20,
            )
            assertEquals(checkboxPixels, capturePixels(tester, width = 32, height = 20))
            assertEquals(1, tester.semanticsNodesByLabel("复选框").size)

            tester.pumpWidget(switch, logicalWidth = 32, logicalHeight = 20)
            /** 证明本地化无法改变 token 视觉的 Switch 帧。 Switch frame proving localization cannot change token visuals. */
            val switchPixels = capturePixels(tester, width = 32, height = 20)
            assertEquals(1, tester.semanticsNodesByLabel("Switch").size)

            tester.pumpWidget(
                PixelLocalizations(
                    locale = PixelLocalizationBundle.Chinese.locale,
                    bundle = PixelLocalizationBundle.Chinese,
                    child = switch,
                ),
                logicalWidth = 32,
                logicalHeight = 20,
            )
            assertEquals(switchPixels, capturePixels(tester, width = 32, height = 20))
            assertEquals(1, tester.semanticsNodesByLabel("开关").size)

            /** 跨本地化边界复用的稳定简洁 ProgressBar 声明。 Stable concise ProgressBar declaration reused across the localization boundary. */
            val progress = ProgressBar(
                progress = 0.25f,
                width = 12,
                height = 5,
                color = progressFill,
                trackColor = progressTrack,
            )
            tester.pumpWidget(progress, logicalWidth = 20, logicalHeight = 8)
            /** 挂载提供者前的帧及其主题解析出的进度语义。 Pre-provider frame and its theme-resolved progress semantics. */
            val progressPixels = capturePixels(tester, width = 20, height = 8)
            assertEquals(1, tester.semanticsNodesByLabel(PixelLabelTokens.Default.progress).size)

            tester.pumpWidget(
                PixelLocalizations(
                    locale = progressBundle.locale,
                    bundle = progressBundle,
                    child = progress,
                ),
                logicalWidth = 20,
                logicalHeight = 8,
            )
            assertEquals(progressPixels, capturePixels(tester, width = 20, height = 8))
            /** 提供者只替换本地化语义，不改变任何输出像素。 Provider replaces localized semantics without changing one output pixel. */
            val localizedProgress = tester.semanticsNodesByLabel("CUSTOM PROGRESS").single()
            assertEquals("CUSTOM[0.25]", localizedProgress.value)
        } finally {
            tester.dispose()
        }
    }

    /** Wraps one production component in distinguishable theme and localization scopes. */
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
            logicalWidth = 192,
            logicalHeight = 96,
        )
    }

    /** Creates one exact custom bundle with optional observable number formatters. */
    private fun localizationBundle(
        labels: PixelLabelTokens,
        integerFormatter: PixelIntegerFormatter = PixelIntegerFormatter.Default,
        percentFormatter: PixelPercentFormatter = PixelPercentFormatter.Default,
    ): PixelLocalizationBundle {
        return PixelLocalizationBundle(
            locale = PixelLocale("xx"),
            labels = labels,
            navigationBar = "CUSTOM BAR",
            navigationRail = "CUSTOM RAIL",
            integerFormatter = integerFormatter,
            percentFormatter = percentFormatter,
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

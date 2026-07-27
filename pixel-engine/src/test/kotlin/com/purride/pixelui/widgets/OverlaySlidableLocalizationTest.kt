package com.purride.pixelui.widgets

import com.purride.pixelui.Column
import com.purride.pixelui.Dropdown
import com.purride.pixelui.Menu
import com.purride.pixelui.PixelControlState
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelLabelTokens
import com.purride.pixelui.PixelLocale
import com.purride.pixelui.PixelLocalizationBundle
import com.purride.pixelui.PixelLocalizations
import com.purride.pixelui.PixelMenuItem
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Slidable
import com.purride.pixelui.SlidableAction
import com.purride.pixelui.Tooltip
import com.purride.pixelui.Widget
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** overlay 与 Slidable 家族的本地化优先级及渲染中立性覆盖。 */
class OverlaySlidableLocalizationTest {
    /** 挂载本地化提供者只改变语义兜底文本，绝不改变任何渲染像素。 */
    @Test
    fun localizationProvidersChangeSemanticFallbacksWithoutChangingPixels() {
        /** 简洁公开入口；其兜底标签不会绘制任何可见本地化字形。 */
        val cases = listOf(
            LocalizationPixelCase(
                name = "Menu",
                englishLabel = "Menu",
                chineseLabel = "菜单",
                build = {
                    Menu(
                        items = listOf(PixelMenuItem(label = "ROW", onSelected = {})),
                        modal = false,
                    )
                },
            ),
            LocalizationPixelCase(
                name = "Dropdown",
                englishLabel = "Dropdown",
                chineseLabel = "下拉菜单",
                build = {
                    Dropdown(
                        label = "",
                        selectedText = "",
                        expanded = false,
                        onToggle = {},
                        items = listOf(PixelMenuItem(label = "ROW", onSelected = {})),
                    )
                },
            ),
            LocalizationPixelCase(
                name = "Tooltip",
                englishLabel = "Tooltip",
                chineseLabel = "工具提示",
                build = {
                    Tooltip(
                        message = "",
                        visible = true,
                        child = SizedBox(width = 6, height = 4),
                    )
                },
            ),
            LocalizationPixelCase(
                name = "Slidable",
                englishLabel = "Slidable",
                chineseLabel = "滑动操作",
                build = {
                    Slidable(
                        child = SizedBox(width = 24, height = 8),
                        onTap = {},
                    )
                },
            ),
        )

        cases.forEach { case ->
            /** 未挂载提供者时的帧与英文语义基线。 */
            val withoutProvider = captureFrame(bundle = null, build = case.build)
            /** Explicit English provider frame proving the inherited boundary is paint-neutral. */
            val english = captureFrame(bundle = PixelLocalizationBundle.English, build = case.build)
            /** Chinese provider frame proving different text remains paint-neutral. */
            val chinese = captureFrame(bundle = PixelLocalizationBundle.Chinese, build = case.build)

            assertArrayEquals(
                "${case.name} English provider changed pixels",
                withoutProvider.pixels,
                english.pixels,
            )
            assertArrayEquals(
                "${case.name} Chinese provider changed pixels",
                withoutProvider.pixels,
                chinese.pixels,
            )
            assertTrue(
                "${case.name} lost token label fallback",
                case.englishLabel in withoutProvider.labels,
            )
            assertTrue("${case.name} lost English provider fallback", case.englishLabel in english.labels)
            assertTrue("${case.name} ignored Chinese provider", case.chineseLabel in chinese.labels)
        }
    }

    /** A custom provider overrides theme defaults for every fallback and Loading/Error channel. */
    @Test
    fun customProviderOverridesThemeLabelsAndLocalizesDropdownPopupMenu() {
        /** Theme-only sentinels that must lose whenever the custom provider is mounted. */
        val themeLabels = PixelLabelTokens.Default.copy(
            menu = "THEME MENU",
            dropdown = "THEME DROPDOWN",
            tooltip = "THEME TOOLTIP",
            slidable = "THEME SLIDABLE",
            loading = "THEME LOADING",
            error = "THEME ERROR",
        )
        /** Provider sentinels covering every migrated fallback and state channel. */
        val providerLabels = PixelLabelTokens.Default.copy(
            menu = "PROVIDER MENU",
            dropdown = "PROVIDER DROPDOWN",
            tooltip = "PROVIDER TOOLTIP",
            slidable = "PROVIDER SLIDABLE",
            loading = "PROVIDER LOADING",
            error = "PROVIDER ERROR",
        )
        /** Complete custom bundle installed beneath a competing explicit theme. */
        val bundle = PixelLocalizationBundle(
            locale = PixelLocale("x-overlay"),
            labels = providerLabels,
            navigationBar = "Provider navigation bar",
            navigationRail = "Provider navigation rail",
        )
        /** Explicit theme graph proving provider text wins without affecting visual token lookup. */
        val theme = PixelThemeTokens.Default.copy(labels = themeLabels)
        /** Deterministic runtime reused for the component family and popup composition. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = themedAndLocalized(
                    theme = theme,
                    bundle = bundle,
                    child = statusComponents(),
                ),
                logicalWidth = 180,
                logicalHeight = 180,
            )

            /** Provider-localized Menu collection status. */
            val menu = tester.semanticsNodesByLabel(providerLabels.menu).single()
            assertEquals(PixelSemanticRole.MENU, menu.role)
            assertEquals(providerLabels.loading, menu.value)
            assertEquals(providerLabels.error, menu.error)
            /** Explicit row label stays business content while its status uses the provider. */
            val menuRow = tester.semanticsNodesByLabel("MENU ITEM").single()
            assertEquals(providerLabels.loading, menuRow.value)
            assertEquals(providerLabels.error, menuRow.error)

            /** Blank visible Dropdown content reaches the provider fallback and state channels. */
            val dropdown = tester.semanticsNodesByLabel(providerLabels.dropdown).single()
            assertEquals(providerLabels.loading, dropdown.hint)
            assertEquals(providerLabels.error, dropdown.error)
            /** Blank Tooltip message reaches the provider fallback and state channels. */
            val tooltip = tester.semanticsNodesByLabel(providerLabels.tooltip).single()
            assertEquals(providerLabels.loading, tooltip.value)
            assertEquals(providerLabels.error, tooltip.error)
            /** Slidable row fallback and both status values come from the same provider bundle. */
            val slidable = tester.semanticsNodesByLabel(providerLabels.slidable).single()
            assertEquals(providerLabels.loading, slidable.value)
            assertEquals(providerLabels.error, slidable.error)
            /** Required pane-action text remains explicit while state values are localized. */
            val action = tester.semanticsNodesByLabel("ACTION").single()
            assertEquals(providerLabels.loading, action.value)
            assertEquals(providerLabels.error, action.error)
            assertFalse(tester.semanticsNodes().any { node -> node.label.startsWith("THEME ") })

            tester.pumpWidget(
                widget = themedAndLocalized(
                    theme = theme,
                    bundle = bundle,
                    child = Dropdown(
                        label = "Anchor",
                        selectedText = "A",
                        expanded = true,
                        onToggle = {},
                        items = listOf(PixelMenuItem(label = "POPUP ITEM", onSelected = {})),
                        states = STATUS_STATES,
                        key = "localized-popup",
                    ),
                ),
                logicalWidth = 120,
                logicalHeight = 72,
            )

            /** Dropdown's popup is still a Menu semantic container and therefore uses menu text. */
            val popupMenu = tester.semanticsNodesByLabel(providerLabels.menu).single()
            assertEquals(PixelSemanticRole.MENU, popupMenu.role)
            assertEquals(providerLabels.loading, popupMenu.value)
            assertEquals(providerLabels.error, popupMenu.error)
            /** Popup rows receive provider status labels while retaining caller-owned names. */
            val popupRow = tester.semanticsNodesByLabel("POPUP ITEM").single()
            assertEquals(providerLabels.loading, popupRow.value)
            assertEquals(providerLabels.error, popupRow.error)
        } finally {
            tester.dispose()
        }
    }

    /** Without a provider, existing theme labels remain the fallback and state source. */
    @Test
    fun explicitThemeRemainsFallbackWhenProviderIsAbsent() {
        /** Custom theme labels distinguish the provider-free branch from English defaults. */
        val themeLabels = PixelLabelTokens.Default.copy(
            menu = "THEME MENU",
            dropdown = "THEME DROPDOWN",
            tooltip = "THEME TOOLTIP",
            slidable = "THEME SLIDABLE",
            loading = "THEME LOADING",
            error = "THEME ERROR",
        )
        /** Complete explicit theme installed without any localization boundary. */
        val theme = PixelThemeTokens.Default.copy(labels = themeLabels)
        /** Runtime exposing each provider-free semantic node. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelTheme(tokens = theme, child = statusComponents()),
                logicalWidth = 180,
                logicalHeight = 180,
            )

            /** Menu collection and row both retain the custom theme state labels. */
            val menu = tester.semanticsNodesByLabel(themeLabels.menu).single()
            assertEquals(themeLabels.loading, menu.value)
            assertEquals(themeLabels.error, menu.error)
            assertEquals(themeLabels.loading, tester.semanticsNodesByLabel("MENU ITEM").single().value)
            /** Dropdown, Tooltip, and Slidable names remain sourced from the explicit theme. */
            val dropdown = tester.semanticsNodesByLabel(themeLabels.dropdown).single()
            assertEquals(themeLabels.loading, dropdown.hint)
            assertEquals(themeLabels.error, dropdown.error)
            val tooltip = tester.semanticsNodesByLabel(themeLabels.tooltip).single()
            assertEquals(themeLabels.loading, tooltip.value)
            assertEquals(themeLabels.error, tooltip.error)
            val slidable = tester.semanticsNodesByLabel(themeLabels.slidable).single()
            assertEquals(themeLabels.loading, slidable.value)
            assertEquals(themeLabels.error, slidable.error)
            /** Required action label remains explicit while status text follows the theme. */
            val action = tester.semanticsNodesByLabel("ACTION").single()
            assertEquals(themeLabels.loading, action.value)
            assertEquals(themeLabels.error, action.error)
        } finally {
            tester.dispose()
        }
    }

    /** 显式语义文本（含显式空白值）优先于任何提供者。 */
    @Test
    fun explicitAndVisibleTextPrecedeProviderWhileBlankExplicitValuesRemainValid() {
        /** Chinese bundle makes every accidental provider fallback immediately observable. */
        val bundle = PixelLocalizationBundle.Chinese
        /** Runtime reused across independent precedence cases. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = localized(
                    bundle = bundle,
                    child = Menu(
                        items = listOf(PixelMenuItem(label = "ROW", onSelected = {})),
                        states = PixelControlStateSet.Normal,
                        semanticLabel = "EXPLICIT MENU",
                        modal = false,
                    ),
                ),
                logicalWidth = 96,
                logicalHeight = 32,
            )
            assertEquals(1, tester.semanticsNodesByLabel("EXPLICIT MENU").size)
            assertTrue(tester.semanticsNodesByLabel(bundle.labels.menu).isEmpty())

            tester.pumpWidget(
                widget = localized(
                    bundle = bundle,
                    child = Dropdown(
                        label = "",
                        selectedText = "",
                        expanded = false,
                        onToggle = {},
                        items = listOf(PixelMenuItem(label = "ROW", onSelected = {})),
                        states = PixelControlStateSet.Normal,
                        semanticLabel = "EXPLICIT DROPDOWN",
                    ),
                ),
                logicalWidth = 96,
                logicalHeight = 24,
            )
            assertEquals(1, tester.semanticsNodesByLabel("EXPLICIT DROPDOWN").size)

            tester.pumpWidget(
                widget = localized(
                    bundle = bundle,
                    child = Tooltip(
                        message = "",
                        visible = true,
                        child = SizedBox(width = 4, height = 3),
                        states = PixelControlStateSet.Normal,
                        semanticLabel = "EXPLICIT TOOLTIP",
                    ),
                ),
                logicalWidth = 96,
                logicalHeight = 36,
            )
            assertEquals(1, tester.semanticsNodesByLabel("EXPLICIT TOOLTIP").size)

            tester.pumpWidget(
                widget = localized(
                    bundle = bundle,
                    child = Slidable(
                        child = SizedBox(width = 24, height = 8),
                        states = PixelControlStateSet.Normal,
                        onTap = {},
                        semanticLabel = "EXPLICIT SLIDABLE",
                    ),
                ),
                logicalWidth = 96,
                logicalHeight = 24,
            )
            assertEquals(1, tester.semanticsNodesByLabel("EXPLICIT SLIDABLE").size)

            tester.pumpWidget(
                widget = localized(
                    bundle = bundle,
                    child = Column(
                        children = listOf(
                            Dropdown(
                                label = "VISIBLE DROPDOWN",
                                selectedText = "A",
                                expanded = false,
                                onToggle = {},
                                items = listOf(PixelMenuItem(label = "A", onSelected = {})),
                            ),
                            Tooltip(
                                message = "VISIBLE TOOLTIP",
                                visible = true,
                                child = SizedBox(width = 4, height = 3),
                            ),
                        ),
                    ),
                ),
                logicalWidth = 120,
                logicalHeight = 64,
            )
            assertEquals(1, tester.semanticsNodesByLabel("VISIBLE DROPDOWN").size)
            assertEquals(1, tester.semanticsNodesByLabel("VISIBLE TOOLTIP").size)

            tester.pumpWidget(
                widget = localized(
                    bundle = bundle,
                    child = Column(
                        children = listOf(
                            Menu(
                                items = listOf(PixelMenuItem(label = "ROW", onSelected = {})),
                                states = PixelControlStateSet.Normal,
                                semanticLabel = "",
                                modal = false,
                            ),
                            Dropdown(
                                label = "",
                                selectedText = "",
                                expanded = false,
                                onToggle = {},
                                items = listOf(PixelMenuItem(label = "ROW", onSelected = {})),
                                states = PixelControlStateSet.Normal,
                                semanticLabel = "",
                            ),
                            Tooltip(
                                message = "",
                                visible = true,
                                child = SizedBox(width = 4, height = 3),
                                states = PixelControlStateSet.Normal,
                                semanticLabel = "",
                            ),
                            Slidable(
                                child = SizedBox(width = 24, height = 8),
                                states = PixelControlStateSet.Normal,
                                onTap = {},
                                semanticLabel = "",
                            ),
                        ),
                    ),
                ),
                logicalWidth = 96,
                logicalHeight = 96,
            )
            /** A present blank value remains explicit instead of being rejected or localized. */
            val blankNodes = tester.semanticsNodesByLabel("")
            assertEquals(4, blankNodes.size)
            assertEquals(1, blankNodes.count { node -> node.role == PixelSemanticRole.MENU })
            assertEquals(2, blankNodes.count { node -> node.role == PixelSemanticRole.BUTTON })
            assertEquals(1, blankNodes.count { node -> node.role == PixelSemanticRole.GENERIC })
        } finally {
            tester.dispose()
        }
    }

    /** Builds all migrated state channels with blank fallback-bearing visible text. */
    private fun statusComponents(): Widget {
        return Column(
            children = listOf(
                Menu(
                    items = listOf(PixelMenuItem(label = "MENU ITEM", onSelected = {})),
                    states = STATUS_STATES,
                    modal = false,
                ),
                Dropdown(
                    label = "",
                    selectedText = "",
                    expanded = false,
                    onToggle = {},
                    items = listOf(PixelMenuItem(label = "DROPDOWN ITEM", onSelected = {})),
                    states = STATUS_STATES,
                ),
                Tooltip(
                    message = "",
                    visible = true,
                    child = SizedBox(width = 6, height = 4),
                    states = STATUS_STATES,
                ),
                Slidable(
                    child = SizedBox(width = 24, height = 8),
                    states = STATUS_STATES,
                    onTap = {},
                ),
                SlidableAction(
                    label = "ACTION",
                    onPressed = {},
                    states = STATUS_STATES,
                ),
            ),
        )
    }

    /** Wraps [child] in one explicit provider without installing a theme. */
    private fun localized(bundle: PixelLocalizationBundle, child: Widget): Widget {
        return PixelLocalizations(
            locale = bundle.locale,
            bundle = bundle,
            child = child,
        )
    }

    /** Installs a competing theme outside the explicit localization provider. */
    private fun themedAndLocalized(
        theme: PixelThemeTokens,
        bundle: PixelLocalizationBundle,
        child: Widget,
    ): Widget {
        return PixelTheme(
            tokens = theme,
            child = localized(bundle = bundle, child = child),
        )
    }

    /** 为一次全新的简洁公开入口声明捕获防御性像素与语义标签。 */
    private fun captureFrame(
        bundle: PixelLocalizationBundle?,
        build: () -> Widget,
    ): LocalizationFrame {
        /** Short-lived runtime prevents retained state from crossing provider variants. */
        val tester = PixelTester()
        return try {
            /** Fresh declaration avoids reusing one widget instance under different ancestors. */
            val child = build()
            /** 可选显式提供者用于区分未挂载提供者的基线。 */
            val root = bundle?.let { installed -> localized(installed, child) } ?: child
            tester.pumpWidget(root, logicalWidth = 96, logicalHeight = 48)
            LocalizationFrame(
                pixels = requireNotNull(tester.renderResult).buffer.pixels.copyOf(),
                labels = tester.semanticsNodes().map { node -> node.label }.toSet(),
            )
        } finally {
            tester.dispose()
        }
    }

    /** 一组像素比较声明及其预期兜底名称。 */
    private data class LocalizationPixelCase(
        /** Human-readable component family included in assertion failures. */
        val name: String,
        /** 未挂载提供者与英文提供者共用的语义兜底标签。 */
        val englishLabel: String,
        /** Built-in Chinese-provider semantic fallback. */
        val chineseLabel: String,
        /** 为每个提供者变体新建一次的简洁公开入口声明。 */
        val build: () -> Widget,
    )

    /** Defensive render result retained after its short-lived [PixelTester] is disposed. */
    private data class LocalizationFrame(
        /** Exact row-major ARGB frame. */
        val pixels: IntArray,
        /** All exported semantic labels in the same rendered frame. */
        val labels: Set<String>,
    )

    /** Shared state set exposing both localized semantic status channels. */
    private companion object {
        /** Loading and Error combination used by every status assertion. */
        val STATUS_STATES: PixelControlStateSet = PixelControlStateSet.of(
            PixelControlState.Loading,
            PixelControlState.Error,
        )
    }
}

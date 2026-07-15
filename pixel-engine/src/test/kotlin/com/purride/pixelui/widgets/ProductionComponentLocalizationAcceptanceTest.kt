package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BottomSheet
import com.purride.pixelui.Checkbox
import com.purride.pixelui.Dialog
import com.purride.pixelui.Directionality
import com.purride.pixelui.Dropdown
import com.purride.pixelui.IconButton
import com.purride.pixelui.ListTile
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.Menu
import com.purride.pixelui.NavigationBar
import com.purride.pixelui.NavigationRail
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelControlState
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelIconData
import com.purride.pixelui.PixelIntegerFormatter
import com.purride.pixelui.PixelLabelTokens
import com.purride.pixelui.PixelLocale
import com.purride.pixelui.PixelLocalizationBundle
import com.purride.pixelui.PixelLocalizations
import com.purride.pixelui.PixelMenuItem
import com.purride.pixelui.PixelMultiStackNavigatorController
import com.purride.pixelui.PixelNavigationDestination
import com.purride.pixelui.PixelPercentFormatter
import com.purride.pixelui.PixelSemanticsNode
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.Radio
import com.purride.pixelui.RefreshIndicator
import com.purride.pixelui.Scrollbar
import com.purride.pixelui.SegmentedControl
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Slider
import com.purride.pixelui.Slidable
import com.purride.pixelui.Snackbar
import com.purride.pixelui.Switch
import com.purride.pixelui.Tabs
import com.purride.pixelui.TextButton
import com.purride.pixelui.TextDirection
import com.purride.pixelui.TextField
import com.purride.pixelui.Toast
import com.purride.pixelui.Tooltip
import com.purride.pixelui.ValueAdjuster
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelRefreshIndicatorController
import com.purride.pixelui.state.PixelRefreshIndicatorState
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState
import com.purride.pixelui.testing.PixelTester
import java.io.Closeable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end localization acceptance matrix for the canonical 25 production component families. */
class ProductionComponentLocalizationAcceptanceTest {
    /** Registry order stays byte-for-byte aligned with [ProductionComponentStateMatrixTest]. */
    @Test
    fun registryMatchesCanonicalProductionFamilyOrderExactly() {
        /** Ordered family ids declared by this localization acceptance registry. */
        val actualFamilies = COMPONENT_REGISTRY.map(FamilyRegistration::family)
        assertEquals(EXPECTED_COMPONENT_FAMILIES, actualFamilies)
        assertEquals(25, actualFamilies.size)
        assertEquals(actualFamilies.size, actualFamilies.toSet().size)
    }

    /** Every family resolves real provider text without translating mandatory business content. */
    @Test
    fun everyProductionFamilyAcceptsEnglishChineseAndRtlCustomProviders() {
        LocalizationHarness().use { harness ->
            PROVIDER_CASES.forEach { providerCase ->
                COMPONENT_REGISTRY.forEach { registration ->
                    /** Stable retained identity unique to one provider and family cell. */
                    val key = "localization-${providerCase.name}-${registration.family}"
                    /** Real public component carrying combined Loading and Error state. */
                    val component = buildProductionComponent(
                        registration = registration,
                        resources = harness,
                        states = STATUS_STATES,
                        key = key,
                    )
                    pumpLocalized(
                        harness = harness,
                        providerCase = providerCase,
                        child = component,
                    )

                    /** Actual exported semantic tree from the mounted production component. */
                    val nodes = harness.tester.semanticsNodes()
                    assertTrue("${providerCase.name}/${registration.family} exported no semantics", nodes.isNotEmpty())
                    /** Every spoken field and custom-action label split into exact scan fragments. */
                    val texts = semanticTexts(nodes)

                    registration.providerKeys.forEach { providerKey ->
                        /** Exact bundle-owned default required for this component family. */
                        val expected = providerKey.resolve(providerCase.bundle)
                        assertTrue(
                            "${providerCase.name}/${registration.family} missed provider text $expected; got $texts",
                            expected in texts,
                        )
                    }
                    registration.requiredBusinessTexts.forEach { businessText ->
                        assertTrue(
                            "${providerCase.name}/${registration.family} translated business text $businessText; got $texts",
                            businessText in texts,
                        )
                    }
                    if (registration.expectsFormattedPercent) {
                        /** Provider formatter output expected from Slider or determinate Progress. */
                        val expectedPercent = providerCase.bundle.formatPercent(MATRIX_PROGRESS)
                        assertTrue(
                            "${providerCase.name}/${registration.family} missed $expectedPercent; got $texts",
                            expectedPercent in texts,
                        )
                    }
                    assertProviderStatus(
                        providerCase = providerCase,
                        registration = registration,
                        texts = texts,
                    )
                    assertNoFallbackLeak(
                        providerCase = providerCase,
                        family = registration.family,
                        texts = texts,
                        allowedBusinessTexts = registration.requiredBusinessTexts,
                    )
                }
            }
        }
    }

    /** Blank optional notification messages still expose their family-specific provider defaults. */
    @Test
    fun omittedMessageDefaultsResolveFromEveryProvider() {
        LocalizationHarness().use { harness ->
            PROVIDER_CASES.forEach { providerCase ->
                MESSAGE_FALLBACK_KEYS.forEach { providerKey ->
                    /** Public notification component using a deliberately omitted optional message. */
                    val component = buildMessageFallbackComponent(
                        providerKey = providerKey,
                        key = "message-${providerCase.name}-${providerKey.name}",
                    )
                    pumpLocalized(
                        harness = harness,
                        providerCase = providerCase,
                        child = component,
                    )
                    /** Exact provider-owned message fallback exported by the real semantic tree. */
                    val expected = providerKey.resolve(providerCase.bundle)
                    /** Complete spoken-text scan for this one message fallback component. */
                    val texts = semanticTexts(harness.tester.semanticsNodes())
                    assertTrue("${providerCase.name}/${providerKey.name} missed $expected; got $texts", expected in texts)
                    assertNoFallbackLeak(
                        providerCase = providerCase,
                        family = "message-${providerKey.name}",
                        texts = texts,
                        allowedBusinessTexts = emptySet(),
                    )
                }
            }
        }
    }

    /** Direct and controller NavigationBar/Rail entry points share provider and business contracts. */
    @Test
    fun allFourNavigationEntryPointsParticipateInEveryProviderMatrix() {
        LocalizationHarness().use { harness ->
            PROVIDER_CASES.forEach { providerCase ->
                NAVIGATION_ENTRY_POINTS.forEach { entryPoint ->
                    /** Real public direct or controller-bound navigation declaration. */
                    val component = buildNavigationEntryPoint(
                        entryPoint = entryPoint,
                        resources = harness,
                        states = STATUS_STATES,
                        key = "navigation-${providerCase.name}-${entryPoint.name}",
                    )
                    pumpLocalized(
                        harness = harness,
                        providerCase = providerCase,
                        child = component,
                    )

                    /** Complete Navigation group and destination semantic text. */
                    val texts = semanticTexts(harness.tester.semanticsNodes())
                    /** Orientation-specific provider collection name. */
                    val expectedContainer = entryPoint.containerKey.resolve(providerCase.bundle)
                    assertTrue("${providerCase.name}/${entryPoint.name} missed $expectedContainer", expectedContainer in texts)
                    assertTrue("${providerCase.name}/${entryPoint.name} lost primary destination", BUSINESS_NAV_PRIMARY in texts)
                    assertTrue("${providerCase.name}/${entryPoint.name} lost secondary destination", BUSINESS_NAV_SECONDARY in texts)
                    assertTrue(providerCase.bundle.labels.loading in texts)
                    assertTrue(providerCase.bundle.labels.error in texts)
                    assertNoFallbackLeak(
                        providerCase = providerCase,
                        family = entryPoint.name,
                        texts = texts,
                        allowedBusinessTexts = NAVIGATION_BUSINESS_TEXTS,
                    )
                }
            }
        }
    }

    /** Non-sentinel caller text wins over a custom provider for every overridable family name. */
    @Test
    fun customExplicitOverridesAlwaysWinProviderDefaults() {
        LocalizationHarness().use { harness ->
            COMPONENT_REGISTRY.filter { registration -> registration.overrideKeys.isNotEmpty() }
                .forEach { registration ->
                    /** Distinguishable non-sentinel caller value for this exact family. */
                    val explicitText = "EXPLICIT::${registration.family}"
                    /** Normal-state component isolates precedence from Loading/Error status text. */
                    val component = buildProductionComponent(
                        registration = registration,
                        resources = harness,
                        states = PixelControlStateSet.Normal,
                        key = "explicit-${registration.family}",
                        explicitOverride = explicitText,
                    )
                    pumpLocalized(
                        harness = harness,
                        providerCase = RTL_PROVIDER_CASE,
                        child = component,
                    )

                    /** Actual spoken fragments after the explicit override is mounted. */
                    val texts = semanticTexts(harness.tester.semanticsNodes())
                    assertTrue("${registration.family} lost explicit text $explicitText; got $texts", explicitText in texts)
                    registration.overrideKeys.forEach { providerKey ->
                        /** Provider default that must be absent after the explicit override wins. */
                        val displacedProviderText = providerKey.resolve(RTL_PROVIDER_CASE.bundle)
                        assertTrue(
                            "${registration.family} retained displaced provider text $displacedProviderText; got $texts",
                            displacedProviderText !in texts,
                        )
                    }
                    assertNoFallbackLeak(
                        providerCase = RTL_PROVIDER_CASE,
                        family = "explicit-${registration.family}",
                        texts = texts,
                        allowedBusinessTexts = registration.requiredBusinessTexts + explicitText,
                    )
                }
        }
    }

    /** Builds one canonical public component from its exact registry family. */
    @Suppress("LongMethod")
    private fun buildProductionComponent(
        registration: FamilyRegistration,
        resources: LocalizationHarness,
        states: PixelControlStateSet,
        key: Any,
        explicitOverride: String? = null,
    ): Widget {
        return when (registration.family) {
            "button" -> OutlinedButton(
                text = explicitOverride.orEmpty(),
                onPressed = {},
                states = states,
                key = key,
            )
            "textButton" -> TextButton(
                text = explicitOverride.orEmpty(),
                onPressed = {},
                states = states,
                key = key,
            )
            "iconButton" -> IconButton(
                icon = MATRIX_ICON,
                onPressed = {},
                semanticLabel = BUSINESS_ICON_BUTTON,
                states = states,
                key = key,
            )
            "textField" -> TextField(
                state = resources.textState,
                controller = resources.textController,
                states = states,
                placeholder = "",
                semanticLabel = explicitOverride,
                key = key,
            )
            "listTile" -> ListTile(
                title = SizedBox(width = 16, height = 8),
                states = states,
                onTap = {},
                semanticLabel = explicitOverride,
                key = key,
            )
            "checkbox" -> Checkbox(
                checked = false,
                onChanged = {},
                states = states,
                semanticLabel = explicitOverride,
                key = key,
            )
            "radio" -> Radio(
                selected = false,
                onSelected = {},
                semanticLabel = BUSINESS_RADIO,
                states = states,
                key = key,
            )
            "switch" -> Switch(
                checked = false,
                onChanged = {},
                states = states,
                semanticLabel = explicitOverride,
                key = key,
            )
            "slider" -> Slider(
                value = MATRIX_PROGRESS,
                states = states,
                onDrag = {},
                onRelease = {},
                semanticLabel = explicitOverride,
                key = key,
            )
            "tabs" -> Tabs(
                labels = listOf(BUSINESS_TAB_PRIMARY, BUSINESS_TAB_SECONDARY),
                selectedIndex = 0,
                onSelected = {},
                states = states,
                key = key,
            )
            "segmented" -> SegmentedControl(
                labels = listOf(BUSINESS_SEGMENT_PRIMARY, BUSINESS_SEGMENT_SECONDARY),
                selectedIndex = 0,
                onSelected = {},
                states = states,
                key = key,
            )
            "navigationBar" -> buildDirectNavigationBar(
                states = states,
                key = key,
                semanticLabel = explicitOverride,
            )
            "navigationRail" -> buildDirectNavigationRail(
                states = states,
                key = key,
                semanticLabel = explicitOverride,
            )
            "valueAdjuster" -> ValueAdjuster(
                valueText = BUSINESS_ADJUSTER_VALUE,
                onDecrease = {},
                onIncrease = {},
                states = states,
                label = explicitOverride,
                key = key,
            )
            "menu" -> Menu(
                items = listOf(PixelMenuItem(label = BUSINESS_MENU_ITEM, onSelected = {})),
                states = states,
                semanticLabel = explicitOverride,
                onDismissRequest = {},
                modal = false,
                key = key,
            )
            "dropdown" -> Dropdown(
                label = "",
                selectedText = "",
                expanded = false,
                onToggle = {},
                items = listOf(PixelMenuItem(label = BUSINESS_DROPDOWN_ITEM, onSelected = {})),
                states = states,
                semanticLabel = explicitOverride,
                key = key,
            )
            "slidable" -> Slidable(
                child = SizedBox(width = 32, height = 10),
                states = states,
                onTap = {},
                semanticLabel = explicitOverride,
                key = key,
            )
            "dialog" -> Dialog(
                content = SizedBox(width = 20, height = 10),
                states = states,
                semanticLabel = explicitOverride,
                onDismissRequest = {},
                modal = false,
                key = key,
            )
            "bottomSheet" -> BottomSheet(
                content = SizedBox(width = 20, height = 10),
                states = states,
                semanticLabel = explicitOverride,
                onDismissRequest = {},
                modal = false,
                key = key,
            )
            "toast" -> Toast(
                message = explicitOverride ?: BUSINESS_TOAST_MESSAGE,
                states = states,
                key = key,
            )
            "snackbar" -> Snackbar(
                message = BUSINESS_SNACKBAR_MESSAGE,
                states = states,
                key = key,
            )
            "tooltip" -> Tooltip(
                message = BUSINESS_TOOLTIP_MESSAGE,
                visible = true,
                child = SizedBox(width = 10, height = 6),
                states = states,
                semanticLabel = explicitOverride,
                key = key,
            )
            "progress" -> ProgressBar(
                progress = MATRIX_PROGRESS,
                states = states,
                width = 48,
                height = 8,
                key = key,
            )
            "refresh" -> RefreshIndicator(
                child = SizedBox(width = 64, height = 24),
                state = resources.refreshState,
                controller = resources.refreshController,
                states = states,
                onRefresh = {},
                thresholdPx = 20,
                semanticLabel = explicitOverride,
                key = key,
            )
            "scrollbar" -> Scrollbar(
                child = overflowingList(resources = resources, key = "$key-list"),
                state = resources.listState,
                states = states,
                semanticLabel = explicitOverride,
                key = key,
            )
            else -> error("Unregistered localization family: ${registration.family}")
        }
    }

    /** Builds the direct NavigationBar while preserving omission of its default-string sentinel. */
    private fun buildDirectNavigationBar(
        states: PixelControlStateSet,
        key: Any,
        semanticLabel: String?,
    ): Widget {
        /** Stable business destinations shared by direct and controller-bound navigation. */
        val destinations = navigationDestinations()
        return if (semanticLabel == null) {
            NavigationBar(
                destinations = destinations,
                selectedId = BUSINESS_NAV_PRIMARY_ID,
                onSelected = {},
                states = states,
                key = key,
            )
        } else {
            NavigationBar(
                destinations = destinations,
                selectedId = BUSINESS_NAV_PRIMARY_ID,
                onSelected = {},
                states = states,
                semanticLabel = semanticLabel,
                key = key,
            )
        }
    }

    /** Builds the direct NavigationRail while preserving omission of its default-string sentinel. */
    private fun buildDirectNavigationRail(
        states: PixelControlStateSet,
        key: Any,
        semanticLabel: String?,
    ): Widget {
        /** Stable business destinations shared by direct and controller-bound navigation. */
        val destinations = navigationDestinations()
        return if (semanticLabel == null) {
            NavigationRail(
                destinations = destinations,
                selectedId = BUSINESS_NAV_PRIMARY_ID,
                onSelected = {},
                states = states,
                key = key,
            )
        } else {
            NavigationRail(
                destinations = destinations,
                selectedId = BUSINESS_NAV_PRIMARY_ID,
                onSelected = {},
                states = states,
                semanticLabel = semanticLabel,
                key = key,
            )
        }
    }

    /** Builds one of the four public direct/controller NavigationBar/Rail entry points. */
    private fun buildNavigationEntryPoint(
        entryPoint: NavigationEntryPoint,
        resources: LocalizationHarness,
        states: PixelControlStateSet,
        key: Any,
    ): Widget {
        /** Stable caller-owned destination labels that no provider may translate. */
        val destinations = navigationDestinations()
        return when (entryPoint) {
            NavigationEntryPoint.DirectBar -> NavigationBar(
                destinations = destinations,
                selectedId = BUSINESS_NAV_PRIMARY_ID,
                onSelected = {},
                states = states,
                key = key,
            )
            NavigationEntryPoint.ControllerBar -> NavigationBar(
                destinations = destinations,
                controller = resources.barController,
                states = states,
                key = key,
            )
            NavigationEntryPoint.DirectRail -> NavigationRail(
                destinations = destinations,
                selectedId = BUSINESS_NAV_PRIMARY_ID,
                onSelected = {},
                states = states,
                key = key,
            )
            NavigationEntryPoint.ControllerRail -> NavigationRail(
                destinations = destinations,
                controller = resources.railController,
                states = states,
                key = key,
            )
        }
    }

    /** Builds one blank-message public notification probe for its provider role. */
    private fun buildMessageFallbackComponent(providerKey: ProviderTextKey, key: Any): Widget {
        return when (providerKey) {
            ProviderTextKey.Toast -> Toast(
                message = "",
                states = PixelControlStateSet.Normal,
                key = key,
            )
            ProviderTextKey.Snackbar -> Snackbar(
                message = "",
                states = PixelControlStateSet.Normal,
                key = key,
            )
            ProviderTextKey.Tooltip -> Tooltip(
                message = "",
                visible = true,
                child = SizedBox(width = 10, height = 6),
                states = PixelControlStateSet.Normal,
                key = key,
            )
            else -> error("$providerKey is not a message fallback family")
        }
    }

    /** Creates a real overflowing list paired with the harness's controlled scroll state. */
    private fun overflowingList(resources: LocalizationHarness, key: Any): Widget {
        return ListViewBuilder(
            itemCount = 20,
            itemBuilder = { _ -> SizedBox(height = 8) },
            itemExtent = 8,
            state = resources.listState,
            controller = resources.listController,
            key = key,
        )
    }

    /** Creates two stable-id, explicitly named business destinations. */
    private fun navigationDestinations(): List<PixelNavigationDestination> {
        return listOf(
            PixelNavigationDestination(
                id = BUSINESS_NAV_PRIMARY_ID,
                label = BUSINESS_NAV_PRIMARY,
                icon = MATRIX_ICON,
            ),
            PixelNavigationDestination(
                id = BUSINESS_NAV_SECONDARY_ID,
                label = BUSINESS_NAV_SECONDARY,
                icon = MATRIX_ICON,
            ),
        )
    }

    /** Mounts a component beneath distinguishable theme, provider, and direction boundaries. */
    private fun pumpLocalized(
        harness: LocalizationHarness,
        providerCase: ProviderCase,
        child: Widget,
    ) {
        harness.tester.pumpWidget(
            widget = PixelTheme(
                tokens = THEME_TOKENS,
                child = PixelLocalizations(
                    locale = providerCase.bundle.locale,
                    bundle = providerCase.bundle,
                    child = Directionality(
                        textDirection = providerCase.direction,
                        child = child,
                    ),
                ),
            ),
            logicalWidth = MATRIX_WIDTH,
            logicalHeight = MATRIX_HEIGHT,
        )
    }

    /** Asserts only the status channels intentionally exported by one production family. */
    private fun assertProviderStatus(
        providerCase: ProviderCase,
        registration: FamilyRegistration,
        texts: Set<String>,
    ) {
        if (registration.statusExpectation.expectsLoading) {
            assertTrue(
                "${providerCase.name}/${registration.family} missed provider Loading; got $texts",
                providerCase.bundle.labels.loading in texts,
            )
        }
        if (registration.statusExpectation.expectsError) {
            assertTrue(
                "${providerCase.name}/${registration.family} missed provider Error; got $texts",
                providerCase.bundle.labels.error in texts,
            )
        }
    }

    /** Rejects theme sentinels and built-in English text outside the English provider itself. */
    private fun assertNoFallbackLeak(
        providerCase: ProviderCase,
        family: String,
        texts: Set<String>,
        allowedBusinessTexts: Set<String>,
    ) {
        /** Theme strings must never win while any explicit provider is mounted. */
        val themeLeaks = texts.intersect(THEME_TEXTS)
        assertTrue("${providerCase.name}/$family leaked theme text $themeLeaks from $texts", themeLeaks.isEmpty())
        /** English provider values are legitimate provider output only in the English case. */
        val allowedEnglishProviderTexts = if (providerCase === ENGLISH_PROVIDER_CASE) {
            providerTextValues(providerCase.bundle)
        } else {
            emptySet()
        }
        /** Built-in English fragments left after provider and explicit-business allowances. */
        val englishLeaks = texts.intersect(
            ENGLISH_FALLBACK_TEXTS - allowedEnglishProviderTexts - allowedBusinessTexts,
        )
        assertTrue("${providerCase.name}/$family leaked English fallback $englishLeaks from $texts", englishLeaks.isEmpty())
    }

    /** Extracts labels, values, hints, errors, and custom-action labels from real semantics. */
    private fun semanticTexts(nodes: List<PixelSemanticsNode>): Set<String> {
        return buildSet {
            nodes.forEach { node ->
                /** Merged semantic labels are scanned as their exact spoken fragments. */
                node.label.split(SPOKEN_FRAGMENT_SEPARATOR)
                    .filter(String::isNotBlank)
                    .forEach(::add)
                listOfNotNull(node.value, node.hint, node.error)
                    .filter(String::isNotBlank)
                    .forEach(::add)
                node.customActionLabels.values
                    .filter(String::isNotBlank)
                    .forEach(::add)
            }
        }
    }

    /** Canonical registration metadata for one production token family. */
    private data class FamilyRegistration(
        /** Exact family id and order used by ProductionComponentStateMatrixTest. */
        val family: String,
        /** Provider-owned names that must appear with omitted component defaults. */
        val providerKeys: Set<ProviderTextKey> = emptySet(),
        /** Explicit business text that must survive every provider unchanged. */
        val requiredBusinessTexts: Set<String> = emptySet(),
        /** Loading/Error semantic channels intentionally supported by the production family. */
        val statusExpectation: StatusExpectation = StatusExpectation.Both,
        /** Provider defaults displaced by this family's non-sentinel explicit override. */
        val overrideKeys: Set<ProviderTextKey> = emptySet(),
        /** Whether this family exports provider-formatted determinate percentage text. */
        val expectsFormattedPercent: Boolean = false,
    )

    /** Provider locale, bundle, and the direction boundary paired with that locale case. */
    private data class ProviderCase(
        /** Human-readable assertion prefix. */
        val name: String,
        /** Exact explicitly installed bundle. */
        val bundle: PixelLocalizationBundle,
        /** Logical direction inherited by the real production component. */
        val direction: TextDirection,
    )

    /** Status channels exported by one family while Loading and Error are both present. */
    private enum class StatusExpectation(
        /** Whether localized Loading text must appear. */
        val expectsLoading: Boolean,
        /** Whether localized Error text must appear. */
        val expectsError: Boolean,
    ) {
        /** Family exports both independent status channels. */
        Both(expectsLoading = true, expectsError = true),

        /** Family exports Error while its semantic value remains determinate progress. */
        ErrorOnly(expectsLoading = false, expectsError = true),

        /** Family exposes state visually without a generic spoken status field. */
        None(expectsLoading = false, expectsError = false),
    }

    /** Provider text roles referenced by the 25-family acceptance registry. */
    private enum class ProviderTextKey {
        /** Generic outlined-button fallback. */
        Button,
        /** Generic text-button fallback. */
        TextButton,
        /** Generic editable-field fallback. */
        TextField,
        /** Generic list-row fallback. */
        ListTile,
        /** Generic checkbox fallback. */
        Checkbox,
        /** Generic switch fallback. */
        Switch,
        /** Generic slider fallback. */
        Slider,
        /** Tab-strip collection fallback. */
        Tabs,
        /** Segmented-control collection fallback. */
        Segmented,
        /** Bottom-navigation collection fallback. */
        NavigationBar,
        /** Navigation-rail collection fallback. */
        NavigationRail,
        /** Value-adjuster group fallback. */
        ValueAdjuster,
        /** Value-adjuster decrement action fallback. */
        Decrease,
        /** Value-adjuster increment action fallback. */
        Increase,
        /** Menu collection fallback. */
        Menu,
        /** Dropdown anchor fallback. */
        Dropdown,
        /** Swipe-action row fallback. */
        Slidable,
        /** Dialog surface fallback. */
        Dialog,
        /** Bottom-sheet surface fallback. */
        BottomSheet,
        /** Toast message fallback. */
        Toast,
        /** Snackbar container/message fallback. */
        Snackbar,
        /** Tooltip message fallback. */
        Tooltip,
        /** Determinate progress fallback. */
        Progress,
        /** Pull-to-refresh fallback. */
        Refresh,
        /** Scrollbar wrapper fallback. */
        Scrollbar;

        /** Resolves this exact role from one installed bundle. */
        fun resolve(bundle: PixelLocalizationBundle): String {
            return when (this) {
                Button -> bundle.labels.button
                TextButton -> bundle.labels.textButton
                TextField -> bundle.labels.textField
                ListTile -> bundle.labels.listTile
                Checkbox -> bundle.labels.checkbox
                Switch -> bundle.labels.switch
                Slider -> bundle.labels.slider
                Tabs -> bundle.labels.tabs
                Segmented -> bundle.labels.segmentedControl
                NavigationBar -> bundle.navigationBar
                NavigationRail -> bundle.navigationRail
                ValueAdjuster -> bundle.labels.valueAdjuster
                Decrease -> bundle.labels.decrease
                Increase -> bundle.labels.increase
                Menu -> bundle.labels.menu
                Dropdown -> bundle.labels.dropdown
                Slidable -> bundle.labels.slidable
                Dialog -> bundle.labels.dialog
                BottomSheet -> bundle.labels.bottomSheet
                Toast -> bundle.labels.toast
                Snackbar -> bundle.labels.snackbar
                Tooltip -> bundle.labels.tooltip
                Progress -> bundle.labels.progress
                Refresh -> bundle.labels.refresh
                Scrollbar -> bundle.labels.scrollbar
            }
        }
    }

    /** Four distinct public navigation overload families required by acceptance. */
    private enum class NavigationEntryPoint(
        /** Orientation-specific provider collection key. */
        val containerKey: ProviderTextKey,
    ) {
        /** Direct controlled NavigationBar. */
        DirectBar(ProviderTextKey.NavigationBar),
        /** Controller-bound NavigationBar. */
        ControllerBar(ProviderTextKey.NavigationBar),
        /** Direct controlled NavigationRail. */
        DirectRail(ProviderTextKey.NavigationRail),
        /** Controller-bound NavigationRail. */
        ControllerRail(ProviderTextKey.NavigationRail),
    }

    /** Reusable controlled state and one tester whose close unmounts every retained listener. */
    private class LocalizationHarness : Closeable {
        /** Deterministic off-screen runtime reused by all cells in one test. */
        val tester: PixelTester = PixelTester()

        /** Controller backing the real editable TextField. */
        val textController: PixelTextFieldController = PixelTextFieldController()

        /** Empty controlled value keeping TextField name independent from business content. */
        val textState: PixelTextFieldState = textController.create(initialText = "")

        /** Controller backing the real RefreshIndicator lifecycle. */
        val refreshController: PixelRefreshIndicatorController = PixelRefreshIndicatorController()

        /** Reused idle refresh state receiving caller Loading/Error independently. */
        val refreshState: PixelRefreshIndicatorState = refreshController.create()

        /** Controller backing the real overflowing list used by Scrollbar. */
        val listController: PixelListController = PixelListController()

        /** Reused controlled viewport position shared by list and Scrollbar. */
        val listState: PixelListState = listController.create()

        /** Unattached controller exercising the public controller-bound NavigationBar entry. */
        val barController: PixelMultiStackNavigatorController =
            PixelMultiStackNavigatorController(initialStackId = BUSINESS_NAV_PRIMARY_ID)

        /** Unattached controller exercising the public controller-bound NavigationRail entry. */
        val railController: PixelMultiStackNavigatorController =
            PixelMultiStackNavigatorController(initialStackId = BUSINESS_NAV_PRIMARY_ID)

        /** Unmounts retained widgets so every controller listener and animation owner is released. */
        override fun close() {
            tester.dispose()
        }
    }

    /** Canonical constants, provider cases, and exact 25-family registry. */
    private companion object {
        /** Logical width large enough for every real component and lifted overlay. */
        const val MATRIX_WIDTH: Int = 180

        /** Logical height large enough for safe overlays and overflowing scroll content. */
        const val MATRIX_HEIGHT: Int = 120

        /** Determinate value shared by Slider and Progress formatter assertions. */
        const val MATRIX_PROGRESS: Float = 0.5f

        /** Separator used by merged semantic spoken fragments. */
        const val SPOKEN_FRAGMENT_SEPARATOR: String = ", "

        /** Stable required IconButton business name. */
        const val BUSINESS_ICON_BUTTON: String = "BUSINESS ICON BUTTON"

        /** Stable required Radio business name. */
        const val BUSINESS_RADIO: String = "BUSINESS RADIO"

        /** Caller-owned first tab name. */
        const val BUSINESS_TAB_PRIMARY: String = "BUSINESS TAB PRIMARY"

        /** Caller-owned second tab name. */
        const val BUSINESS_TAB_SECONDARY: String = "BUSINESS TAB SECONDARY"

        /** Caller-owned first segment name. */
        const val BUSINESS_SEGMENT_PRIMARY: String = "BUSINESS SEGMENT PRIMARY"

        /** Caller-owned second segment name. */
        const val BUSINESS_SEGMENT_SECONDARY: String = "BUSINESS SEGMENT SECONDARY"

        /** Stable selected navigation destination id. */
        const val BUSINESS_NAV_PRIMARY_ID: String = "business-primary"

        /** Stable unselected navigation destination id. */
        const val BUSINESS_NAV_SECONDARY_ID: String = "business-secondary"

        /** Caller-owned selected destination label. */
        const val BUSINESS_NAV_PRIMARY: String = "BUSINESS NAV PRIMARY"

        /** Caller-owned unselected destination label. */
        const val BUSINESS_NAV_SECONDARY: String = "BUSINESS NAV SECONDARY"

        /** Caller-owned controlled ValueAdjuster value. */
        const val BUSINESS_ADJUSTER_VALUE: String = "42"

        /** Caller-owned Menu item name. */
        const val BUSINESS_MENU_ITEM: String = "BUSINESS MENU ITEM"

        /** Caller-owned Dropdown item name retained even while its popup is collapsed. */
        const val BUSINESS_DROPDOWN_ITEM: String = "BUSINESS DROPDOWN ITEM"

        /** Caller-owned Toast message that no provider may translate. */
        const val BUSINESS_TOAST_MESSAGE: String = "BUSINESS TOAST MESSAGE"

        /** Caller-owned Snackbar message that no provider may translate. */
        const val BUSINESS_SNACKBAR_MESSAGE: String = "BUSINESS SNACKBAR MESSAGE"

        /** Caller-owned Tooltip message that no provider may translate. */
        const val BUSINESS_TOOLTIP_MESSAGE: String = "BUSINESS TOOLTIP MESSAGE"

        /** Business labels common to all four navigation entry-point assertions. */
        val NAVIGATION_BUSINESS_TEXTS: Set<String> = setOf(
            BUSINESS_NAV_PRIMARY,
            BUSINESS_NAV_SECONDARY,
        )

        /** Combined state exposing every generic Loading and Error semantic channel. */
        val STATUS_STATES: PixelControlStateSet = PixelControlStateSet.of(
            PixelControlState.Loading,
            PixelControlState.Error,
        )

        /** Distinguishable theme labels proving provider precedence in every matrix cell. */
        val THEME_LABELS: PixelLabelTokens = prefixedLabels("THEME")

        /** Explicit theme graph shared by every provider case. */
        val THEME_TOKENS: PixelThemeTokens = PixelThemeTokens.Default.copy(labels = THEME_LABELS)

        /** Custom RTL labels distinct from English, Chinese, and theme values. */
        val RTL_LABELS: PixelLabelTokens = prefixedLabels("RTL")

        /** Custom RTL bundle including distinguishable number formatters. */
        val RTL_BUNDLE: PixelLocalizationBundle = PixelLocalizationBundle(
            locale = PixelLocale("ar"),
            labels = RTL_LABELS,
            navigationBar = "RTL.navigationBar",
            navigationRail = "RTL.navigationRail",
            integerFormatter = PixelIntegerFormatter { value -> "RTL.integer[$value]" },
            percentFormatter = PixelPercentFormatter { fraction -> "RTL.percent[$fraction]" },
        )

        /** English provider case whose text intentionally equals terminal English fallbacks. */
        val ENGLISH_PROVIDER_CASE: ProviderCase = ProviderCase(
            name = "English",
            bundle = PixelLocalizationBundle.English,
            direction = TextDirection.LTR,
        )

        /** Built-in Chinese provider case with ordinary left-to-right layout direction. */
        val CHINESE_PROVIDER_CASE: ProviderCase = ProviderCase(
            name = "Chinese",
            bundle = PixelLocalizationBundle.Chinese,
            direction = TextDirection.LTR,
        )

        /** Custom provider case explicitly paired with a right-to-left Directionality boundary. */
        val RTL_PROVIDER_CASE: ProviderCase = ProviderCase(
            name = "RTL-custom",
            bundle = RTL_BUNDLE,
            direction = TextDirection.RTL,
        )

        /** Ordered provider matrix required by production acceptance. */
        val PROVIDER_CASES: List<ProviderCase> = listOf(
            ENGLISH_PROVIDER_CASE,
            CHINESE_PROVIDER_CASE,
            RTL_PROVIDER_CASE,
        )

        /** Message-bearing families whose omitted optional content has a provider fallback. */
        val MESSAGE_FALLBACK_KEYS: List<ProviderTextKey> = listOf(
            ProviderTextKey.Toast,
            ProviderTextKey.Snackbar,
            ProviderTextKey.Tooltip,
        )

        /** All direct and controller-bound NavigationBar/Rail public entry points. */
        val NAVIGATION_ENTRY_POINTS: List<NavigationEntryPoint> = NavigationEntryPoint.entries

        /** Canonical token-family order copied from ProductionComponentStateMatrixTest. */
        val EXPECTED_COMPONENT_FAMILIES: List<String> = listOf(
            "button",
            "textButton",
            "iconButton",
            "textField",
            "listTile",
            "checkbox",
            "radio",
            "switch",
            "slider",
            "tabs",
            "segmented",
            "navigationBar",
            "navigationRail",
            "valueAdjuster",
            "menu",
            "dropdown",
            "slidable",
            "dialog",
            "bottomSheet",
            "toast",
            "snackbar",
            "tooltip",
            "progress",
            "refresh",
            "scrollbar",
        )

        /** Exact executable localization registry in canonical production order. */
        val COMPONENT_REGISTRY: List<FamilyRegistration> = listOf(
            FamilyRegistration(
                family = "button",
                providerKeys = setOf(ProviderTextKey.Button),
                statusExpectation = StatusExpectation.None,
                overrideKeys = setOf(ProviderTextKey.Button),
            ),
            FamilyRegistration(
                family = "textButton",
                providerKeys = setOf(ProviderTextKey.TextButton),
                statusExpectation = StatusExpectation.None,
                overrideKeys = setOf(ProviderTextKey.TextButton),
            ),
            FamilyRegistration(
                family = "iconButton",
                requiredBusinessTexts = setOf(BUSINESS_ICON_BUTTON),
            ),
            FamilyRegistration(
                family = "textField",
                providerKeys = setOf(ProviderTextKey.TextField),
                statusExpectation = StatusExpectation.None,
                overrideKeys = setOf(ProviderTextKey.TextField),
            ),
            FamilyRegistration(
                family = "listTile",
                providerKeys = setOf(ProviderTextKey.ListTile),
                overrideKeys = setOf(ProviderTextKey.ListTile),
            ),
            FamilyRegistration(
                family = "checkbox",
                providerKeys = setOf(ProviderTextKey.Checkbox),
                overrideKeys = setOf(ProviderTextKey.Checkbox),
            ),
            FamilyRegistration(
                family = "radio",
                requiredBusinessTexts = setOf(BUSINESS_RADIO),
            ),
            FamilyRegistration(
                family = "switch",
                providerKeys = setOf(ProviderTextKey.Switch),
                overrideKeys = setOf(ProviderTextKey.Switch),
            ),
            FamilyRegistration(
                family = "slider",
                providerKeys = setOf(ProviderTextKey.Slider),
                statusExpectation = StatusExpectation.None,
                overrideKeys = setOf(ProviderTextKey.Slider),
                expectsFormattedPercent = true,
            ),
            FamilyRegistration(
                family = "tabs",
                providerKeys = setOf(ProviderTextKey.Tabs),
                requiredBusinessTexts = setOf(BUSINESS_TAB_PRIMARY, BUSINESS_TAB_SECONDARY),
            ),
            FamilyRegistration(
                family = "segmented",
                providerKeys = setOf(ProviderTextKey.Segmented),
                requiredBusinessTexts = setOf(BUSINESS_SEGMENT_PRIMARY, BUSINESS_SEGMENT_SECONDARY),
            ),
            FamilyRegistration(
                family = "navigationBar",
                providerKeys = setOf(ProviderTextKey.NavigationBar),
                requiredBusinessTexts = NAVIGATION_BUSINESS_TEXTS,
                overrideKeys = setOf(ProviderTextKey.NavigationBar),
            ),
            FamilyRegistration(
                family = "navigationRail",
                providerKeys = setOf(ProviderTextKey.NavigationRail),
                requiredBusinessTexts = NAVIGATION_BUSINESS_TEXTS,
                overrideKeys = setOf(ProviderTextKey.NavigationRail),
            ),
            FamilyRegistration(
                family = "valueAdjuster",
                providerKeys = setOf(
                    ProviderTextKey.ValueAdjuster,
                    ProviderTextKey.Decrease,
                    ProviderTextKey.Increase,
                ),
                overrideKeys = setOf(ProviderTextKey.ValueAdjuster),
            ),
            FamilyRegistration(
                family = "menu",
                providerKeys = setOf(ProviderTextKey.Menu),
                requiredBusinessTexts = setOf(BUSINESS_MENU_ITEM),
                overrideKeys = setOf(ProviderTextKey.Menu),
            ),
            FamilyRegistration(
                family = "dropdown",
                providerKeys = setOf(ProviderTextKey.Dropdown),
                overrideKeys = setOf(ProviderTextKey.Dropdown),
            ),
            FamilyRegistration(
                family = "slidable",
                providerKeys = setOf(ProviderTextKey.Slidable),
                overrideKeys = setOf(ProviderTextKey.Slidable),
            ),
            FamilyRegistration(
                family = "dialog",
                providerKeys = setOf(ProviderTextKey.Dialog),
                overrideKeys = setOf(ProviderTextKey.Dialog),
            ),
            FamilyRegistration(
                family = "bottomSheet",
                providerKeys = setOf(ProviderTextKey.BottomSheet),
                overrideKeys = setOf(ProviderTextKey.BottomSheet),
            ),
            FamilyRegistration(
                family = "toast",
                requiredBusinessTexts = setOf(BUSINESS_TOAST_MESSAGE),
                overrideKeys = setOf(ProviderTextKey.Toast),
            ),
            FamilyRegistration(
                family = "snackbar",
                providerKeys = setOf(ProviderTextKey.Snackbar),
                requiredBusinessTexts = setOf(BUSINESS_SNACKBAR_MESSAGE),
            ),
            FamilyRegistration(
                family = "tooltip",
                requiredBusinessTexts = setOf(BUSINESS_TOOLTIP_MESSAGE),
                overrideKeys = setOf(ProviderTextKey.Tooltip),
            ),
            FamilyRegistration(
                family = "progress",
                providerKeys = setOf(ProviderTextKey.Progress),
                statusExpectation = StatusExpectation.ErrorOnly,
                expectsFormattedPercent = true,
            ),
            FamilyRegistration(
                family = "refresh",
                providerKeys = setOf(ProviderTextKey.Refresh),
                overrideKeys = setOf(ProviderTextKey.Refresh),
            ),
            FamilyRegistration(
                family = "scrollbar",
                providerKeys = setOf(ProviderTextKey.Scrollbar),
                overrideKeys = setOf(ProviderTextKey.Scrollbar),
            ),
        )

        /** Opaque icon reused by IconButton and every business navigation destination. */
        val MATRIX_ICON: PixelIconData = PixelIconData(
            bitmap = PixelBitmap(
                width = 3,
                height = 3,
                pixels = IntArray(9) { PixelColor.White.argb },
            ),
        )

        /** Complete set of built-in English labels forbidden under non-English providers. */
        val ENGLISH_FALLBACK_TEXTS: Set<String> = providerTextValues(PixelLocalizationBundle.English)

        /** Complete set of theme sentinels forbidden while a provider is installed. */
        val THEME_TEXTS: Set<String> = labelValues(THEME_LABELS)

        /** Creates complete distinguishable label tokens for theme or custom-provider scopes. */
        fun prefixedLabels(prefix: String): PixelLabelTokens {
            return PixelLabelTokens(
                confirm = "$prefix.confirm",
                cancel = "$prefix.cancel",
                dismiss = "$prefix.dismiss",
                empty = "$prefix.empty",
                error = "$prefix.error",
                loading = "$prefix.loading",
                button = "$prefix.button",
                textButton = "$prefix.textButton",
                textField = "$prefix.textField",
                listTile = "$prefix.listTile",
                checkbox = "$prefix.checkbox",
                switch = "$prefix.switch",
                slider = "$prefix.slider",
                tabs = "$prefix.tabs",
                segmentedControl = "$prefix.segmentedControl",
                valueAdjuster = "$prefix.valueAdjuster",
                decrease = "$prefix.decrease",
                increase = "$prefix.increase",
                menu = "$prefix.menu",
                dropdown = "$prefix.dropdown",
                dialog = "$prefix.dialog",
                bottomSheet = "$prefix.bottomSheet",
                toast = "$prefix.toast",
                snackbar = "$prefix.snackbar",
                tooltip = "$prefix.tooltip",
                progress = "$prefix.progress",
                refresh = "$prefix.refresh",
                scrollbar = "$prefix.scrollbar",
                slidable = "$prefix.slidable",
            )
        }

        /** Enumerates every standard label-token value for leak detection. */
        fun labelValues(labels: PixelLabelTokens): Set<String> {
            return setOf(
                labels.confirm,
                labels.cancel,
                labels.dismiss,
                labels.empty,
                labels.error,
                labels.loading,
                labels.button,
                labels.textButton,
                labels.textField,
                labels.listTile,
                labels.checkbox,
                labels.switch,
                labels.slider,
                labels.tabs,
                labels.segmentedControl,
                labels.valueAdjuster,
                labels.decrease,
                labels.increase,
                labels.menu,
                labels.dropdown,
                labels.dialog,
                labels.bottomSheet,
                labels.toast,
                labels.snackbar,
                labels.tooltip,
                labels.progress,
                labels.refresh,
                labels.scrollbar,
                labels.slidable,
            )
        }

        /** Enumerates all static provider-owned text, including navigation container names. */
        fun providerTextValues(bundle: PixelLocalizationBundle): Set<String> {
            return labelValues(bundle.labels) + bundle.navigationBar + bundle.navigationRail
        }
    }
}

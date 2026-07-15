package com.purride.pixeldemo.showcase

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.AdaptiveBuilder
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.FocusNode
import com.purride.pixelui.HostCapabilities
import com.purride.pixelui.HostCapabilitiesData
import com.purride.pixelui.ImeAvoidingView
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.NavigationBar
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelAdaptiveLayoutData
import com.purride.pixelui.PixelDisplayFeature
import com.purride.pixelui.PixelDisplayFeatureState
import com.purride.pixelui.PixelDisplayFeatureType
import com.purride.pixelui.PixelHostProfilePolicy
import com.purride.pixelui.PixelIconData
import com.purride.pixelui.PixelLocale
import com.purride.pixelui.PixelLocalizationProvider
import com.purride.pixelui.PixelLocalizationResolver
import com.purride.pixelui.PixelLogicalRect
import com.purride.pixelui.PixelMotionSettings
import com.purride.pixelui.PixelMultiStackNavigator
import com.purride.pixelui.PixelMultiStackNavigatorController
import com.purride.pixelui.PixelNavigationDestination
import com.purride.pixelui.PixelNavigator
import com.purride.pixelui.PixelNavigatorStack
import com.purride.pixelui.PixelRoute
import com.purride.pixelui.PixelRouteTransition
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.PixelWindowInsets
import com.purride.pixelui.Row
import com.purride.pixelui.SafeArea
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextDirection
import com.purride.pixelui.TextField
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.Wrap
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.Accent
import com.purride.pixeldemo.scaffold.Blue
import com.purride.pixeldemo.scaffold.ComponentShowcaseScaffold
import com.purride.pixeldemo.scaffold.Cyan
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.Green
import com.purride.pixeldemo.scaffold.Muted
import com.purride.pixeldemo.scaffold.Pink
import com.purride.pixeldemo.scaffold.Purple
import com.purride.pixeldemo.scaffold.Yellow
import com.purride.pixeldemo.scaffold.samplePanel

/**
 * Integrated M5-3 showcase for adaptive Host metrics, localization, Unicode text and retained state.
 */
object AdaptiveLocalizationScene : DemoScene {
    /** Stable catalog identifier used by navigation, search and acceptance tests. */
    override val id: String = "adaptive_localization"

    /** User-facing catalog title. */
    override val title: String = "Adaptive & Localization"

    /** Compact explanation shown in the Demo browser. */
    override val summary: String =
        "Host capability、viewport、Localization、Unicode、IME 与多返回栈综合验收"

    /** Layout category containing Host geometry and SafeArea examples. */
    override val category = DemoCatalog.layout

    /** Search terms covering every interactive environment branch. */
    override val tags: Set<String> = setOf(
        "adaptive",
        "localization",
        "unicode",
        "rtl",
        "text-scale",
        "high-contrast",
        "reduce-motion",
        "ime",
        "multi-stack",
    )

    /** Public SDK APIs demonstrated by the real retained scene. */
    override val apis: Set<String> = setOf(
        "AdaptiveBuilder",
        "HostCapabilities",
        "PixelHostProfilePolicy",
        "PixelLocalizationProvider",
        "PixelThemeTokens.forCapabilities",
        "TextField",
        "SafeArea",
        "ImeAvoidingView",
        "PixelMultiStackNavigator",
        "NavigationBar",
    )

    /** Uses the complete content viewport so real IME and SafeArea effects remain visible. */
    override val isFullScreen: Boolean = true

    /** Builds one retained body inside the standard searchable Demo frame. */
    override fun build(env: DemoEnv): Widget {
        return ComponentShowcaseScaffold(
            item = this,
            env = env,
            body = AdaptiveLocalizationBody(env = env, key = "adaptive-localization-body"),
        )
    }
}

/** Environment presets that deliberately change one or two observable Host dimensions at a time. */
private enum class AdaptiveEnvironmentPreset(
    /** Compact control label used inside the constrained pixel viewport. */
    val label: String,
) {
    /** Restores the Host snapshot that existed before entering this scene. */
    SYSTEM("SYSTEM"),

    /** Requests simplified Chinese localization while retaining LTR geometry. */
    CHINESE("中文"),

    /** Requests an Arabic locale and true RTL layout direction. */
    RTL("RTL"),

    /** Doubles text metrics without changing non-text geometry. */
    TEXT_2X("TEXT 2X"),

    /** Selects high-contrast semantic tokens through the Host helper. */
    HIGH_CONTRAST("CONTRAST"),

    /** Enables the role-aware reduced-motion policy. */
    REDUCE_MOTION("REDUCE"),

    /** Overrides density and refresh rate to exercise adaptive-dp profile recalculation. */
    DENSITY_REFRESH("2D/120HZ"),

    /** Injects one logical hinge feature into the atomic Host snapshot. */
    HINGE("HINGE"),
}

/** Retained scene boundary owning text, focus, navigation and Host-override lifecycle. */
private class AdaptiveLocalizationBody(
    /** Demo services including the Android Host and shared ticker provider. */
    val env: DemoEnv,
    /** Stable element identity preserved while capability snapshots change. */
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    /** Creates the single state owner used for all environment modes. */
    override fun createState(): State<out StatefulWidget> = AdaptiveLocalizationState()
}

/** Mutable retained state behind [AdaptiveLocalizationBody]. */
private class AdaptiveLocalizationState : State<AdaptiveLocalizationBody>() {
    /** Controller preserving Unicode text and selection through every Host override. */
    private val textController: PixelTextFieldController = PixelTextFieldController()

    /** Real editable text initialized with decomposed Latin, emoji and mixed direction content. */
    private val textState: PixelTextFieldState = textController.create(
        initialText = "Cafe\u0301 · 👨‍👩‍👧‍👦 · ABC אבג 123",
    )

    /** Stable focus identity proving environment changes do not replace the active input target. */
    private val inputFocusNode: FocusNode = FocusNode(debugLabel = "adaptive-localization-input")

    /** Controller retaining two independent navigation histories. */
    private val navigationController: PixelMultiStackNavigatorController =
        PixelMultiStackNavigatorController(initialStackId = HOME_STACK_ID)

    /** Original complete capability override restored when the scene is removed. */
    private var previousCapabilitiesOverride: HostCapabilitiesData? = null

    /** Original profile policy restored together with the capability override. */
    private lateinit var previousProfilePolicy: PixelHostProfilePolicy

    /** Latest automatic or pre-existing Host snapshot used as the immutable preset base. */
    private var baseCapabilities: HostCapabilitiesData? = null

    /** Most recent adaptive geometry used to place a synthetic hinge inside the logical viewport. */
    private var latestLayout: PixelAdaptiveLayoutData? = null

    /** Active control preset displayed in the environment selector. */
    private var selectedPreset: AdaptiveEnvironmentPreset = AdaptiveEnvironmentPreset.SYSTEM

    /** Last complete override written by this scene, used for lifecycle diagnostics. */
    private var lastAppliedOverride: HostCapabilitiesData? = null

    /** Current editable-field status line. */
    private var textStatus: String = "retained input ready"

    /** Home detail route retained so repeated environment changes preserve its route entry. */
    private val homeDetailRoute: PixelRoute by lazy {
        PixelRoute(
            name = "adaptive-home-detail",
            transition = PixelRouteTransition.None,
            builder = { context ->
                navigationPage(
                    title = "HOME DETAIL",
                    color = Green,
                    status = "环境切换后此 route entry 仍保留",
                    actionLabel = "POP",
                    onAction = { PixelNavigator.of(context).pop() },
                )
            },
        )
    }

    /** Home root route exposing one history-producing push action. */
    private val homeRootRoute: PixelRoute by lazy {
        PixelRoute(
            name = "adaptive-home",
            transition = PixelRouteTransition.None,
            builder = { context ->
                navigationPage(
                    title = "HOME STACK",
                    color = Cyan,
                    status = "push 后切换 SETTINGS 再返回",
                    actionLabel = "PUSH",
                    onAction = { PixelNavigator.of(context).push(homeDetailRoute) },
                )
            },
        )
    }

    /** Settings detail route retained independently from the Home history. */
    private val settingsDetailRoute: PixelRoute by lazy {
        PixelRoute(
            name = "adaptive-settings-detail",
            transition = PixelRouteTransition.None,
            builder = { context ->
                navigationPage(
                    title = "SETTINGS DETAIL",
                    color = Pink,
                    status = "inactive stack 不绘制但保持 mounted",
                    actionLabel = "POP",
                    onAction = { PixelNavigator.of(context).pop() },
                )
            },
        )
    }

    /** Settings root route with a history separate from [homeRootRoute]. */
    private val settingsRootRoute: PixelRoute by lazy {
        PixelRoute(
            name = "adaptive-settings",
            transition = PixelRouteTransition.None,
            builder = { context ->
                navigationPage(
                    title = "SETTINGS STACK",
                    color = Purple,
                    status = "独立 history 与 semantic identity",
                    actionLabel = "PUSH",
                    onAction = { PixelNavigator.of(context).push(settingsDetailRoute) },
                )
            },
        )
    }

    /** Captures outer Host state and enables density-aware adaptive profile behavior. */
    override fun initState() {
        /** Host whose public override properties are scoped to this mounted scene. */
        val host = widget.env.hostView
        previousCapabilitiesOverride = host.capabilitiesOverride
        previousProfilePolicy = host.profilePolicy
        baseCapabilities = previousCapabilitiesOverride
        host.profilePolicy = PixelHostProfilePolicy.AdaptiveDp(dotSizeDp = 4f)
    }

    /**
     * Restores Host configuration before the retained scene releases focus and navigation state.
     */
    override fun dispose() {
        /** Scene-owned Host receiving the exact pre-entry policy and override. */
        val host = widget.env.hostView
        host.capabilitiesOverride = previousCapabilitiesOverride
        host.profilePolicy = previousProfilePolicy
        super.dispose()
    }

    /** Builds localization, contrast theme and adaptive content from one atomic Host snapshot. */
    override fun build(context: BuildContext): Widget {
        /** Complete capability snapshot observed by this retained state for the current frame. */
        val capabilities = HostCapabilities.of(context)
        if (widget.env.hostView.capabilitiesOverride == previousCapabilitiesOverride) {
            baseCapabilities = capabilities
        }
        /** High-contrast-aware token graph selected without a parallel Demo-only color branch. */
        val tokens = PixelThemeTokens.forCapabilities(capabilities)
        return PixelLocalizationProvider(
            resolver = PixelLocalizationResolver.Default,
            child = PixelTheme(
                tokens = tokens,
                child = SafeArea(
                    minimum = PixelWindowInsets(left = 1, top = 1, right = 1, bottom = 1),
                    child = AdaptiveBuilder(
                        key = "adaptive-environment-builder",
                        builder = { _, data -> adaptiveContent(capabilities, data) },
                    ),
                ),
            ),
        )
    }

    /** Builds all live metrics and interactive state-preservation fixtures. */
    private fun adaptiveContent(
        capabilities: HostCapabilitiesData,
        data: PixelAdaptiveLayoutData,
    ): Widget {
        latestLayout = data
        /** Resolved bundle proves active locale and localization fallback are separate values. */
        val resolvedBundle = PixelLocalizationResolver.Default.resolveBundle(capabilities.locales)
        return Column(
            children = listOf(
                environmentSelector(capabilities),
                metricsPanel(capabilities, data, resolvedBundle.locale.languageTag),
                unicodePanel(),
                inputPanel(),
                navigationPanel(),
            ),
            spacing = 4,
            mainAxisSize = MainAxisSize.MIN,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        )
    }

    /** Renders every environment override as a deterministic, repeatable control. */
    private fun environmentSelector(capabilities: HostCapabilitiesData): Widget {
        /** Buttons sharing retained keys across selection changes. */
        val controls = AdaptiveEnvironmentPreset.entries.map { preset ->
            OutlinedButton(
                text = preset.label,
                onPressed = { applyPreset(preset, capabilities) },
                borderColor = if (preset == selectedPreset) Accent else Muted,
                key = "adaptive-preset-${preset.name}",
            )
        }
        return samplePanel(
            title = "Environment override",
            color = Accent,
            child = Column(
                children = listOf(
                    Text("ENVIRONMENT OVERRIDE", style = TextStyle(color = Accent)),
                    Wrap(children = controls, spacing = 2, runSpacing = 2),
                    Text(
                        "切换后验证 input/focus/selection/scroll/navigation identity",
                        style = TextStyle(color = Muted),
                    ),
                ),
                spacing = 2,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            ),
        )
    }

    /** Publishes the selected immutable capability snapshot through the real Android Host. */
    private fun applyPreset(
        preset: AdaptiveEnvironmentPreset,
        currentCapabilities: HostCapabilitiesData,
    ) {
        /** Stable pre-override snapshot used to prevent presets from accidentally accumulating. */
        val base = baseCapabilities ?: currentCapabilities
        /** Complete override, or the exact pre-entry value for SYSTEM mode. */
        val override = when (preset) {
            AdaptiveEnvironmentPreset.SYSTEM -> previousCapabilitiesOverride
            AdaptiveEnvironmentPreset.CHINESE -> base.copy(
                locales = listOf(PixelLocale("zh-CN"), PixelLocale.English),
                layoutDirection = TextDirection.LTR,
            )
            AdaptiveEnvironmentPreset.RTL -> base.copy(
                locales = listOf(PixelLocale("ar"), PixelLocale.English),
                layoutDirection = TextDirection.RTL,
            )
            AdaptiveEnvironmentPreset.TEXT_2X -> base.copy(textScaleFactor = 2f)
            AdaptiveEnvironmentPreset.HIGH_CONTRAST -> base.copy(highContrast = true)
            AdaptiveEnvironmentPreset.REDUCE_MOTION -> base.copy(
                motionSettings = base.motionSettings.copy(reduceMotion = true),
            )
            AdaptiveEnvironmentPreset.DENSITY_REFRESH -> base.copy(
                density = 2f,
                refreshRateHz = 120f,
            )
            AdaptiveEnvironmentPreset.HINGE -> base.copy(
                displayFeatures = listOf(logicalHinge()),
            )
        }
        lastAppliedOverride = override
        widget.env.hostView.capabilitiesOverride = override
        setState { selectedPreset = preset }
    }

    /** Creates a logical zero-width hinge centered inside the latest adaptive viewport. */
    private fun logicalHinge(): PixelDisplayFeature {
        /** Latest logical width, with a deterministic fallback before the first adaptive build. */
        val logicalWidth = latestLayout?.logicalWidth ?: 96
        /** Latest logical height, with a deterministic fallback before the first adaptive build. */
        val logicalHeight = latestLayout?.logicalHeight ?: 96
        /** Center line represented without physical Android window types. */
        val center = logicalWidth / 2f
        return PixelDisplayFeature(
            bounds = PixelLogicalRect(center, 0f, center, logicalHeight.toFloat()),
            type = PixelDisplayFeatureType.HINGE,
            state = PixelDisplayFeatureState.HALF_OPENED,
        )
    }

    /** Shows every capability and adaptive layout value required by the M5-3 acceptance contract. */
    private fun metricsPanel(
        capabilities: HostCapabilitiesData,
        data: PixelAdaptiveLayoutData,
        bundleLocale: String,
    ): Widget {
        /** Stable description for absent refresh-rate data. */
        val refresh = capabilities.refreshRateHz?.let { "${it.toInt()}Hz" } ?: "unknown"
        /** Compact feature summary retaining type and bounds count. */
        val features = if (capabilities.displayFeatures.isEmpty()) {
            "none"
        } else {
            capabilities.displayFeatures.joinToString { feature -> feature.type.name }
        }
        return samplePanel(
            title = "Live Host / viewport",
            color = Blue,
            child = Column(
                children = listOf(
                    metric("locale/provider", "${capabilities.locales.joinToString()} -> $bundleLocale"),
                    metric("direction", capabilities.layoutDirection.name),
                    metric("text/contrast", "${capabilities.textScaleFactor}x / ${capabilities.highContrast}"),
                    metric("motion", "reduce=${capabilities.motionSettings.reduceMotion}"),
                    metric("density/refresh", "${capabilities.density} / $refresh"),
                    metric("physical", "${data.physicalWidthPx}x${data.physicalHeightPx} ${data.orientation}"),
                    metric("logical", "${data.logicalWidth}x${data.logicalHeight}"),
                    metric("window class", "${data.widthSizeClass}/${data.heightSizeClass}"),
                    metric("safe/ime", "${data.padding} / ${data.viewInsets}"),
                    metric("features", features),
                    metric("override", "${selectedPreset.name}:${lastAppliedOverride != null}"),
                ),
                spacing = 1,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            ),
        )
    }

    /** Displays the exact Unicode categories whose rendering and fallback contracts are frozen. */
    private fun unicodePanel(): Widget {
        return samplePanel(
            title = "Unicode / Bidi / fallback",
            color = Purple,
            child = Column(
                children = listOf(
                    Text("中文：像素引擎", style = TextStyle(color = Cyan)),
                    Text("decomposed: Cafe\u0301", style = TextStyle(color = Green)),
                    Text("emoji cluster: 👨‍👩‍👧‍👦 👍🏽 🇨🇳 1️⃣", style = TextStyle(color = Yellow)),
                    Text("CRLF: line A\r\nline B", style = TextStyle(color = Blue)),
                    Text("pure RTL: אבגדה", style = TextStyle(color = Pink)),
                    Text("mixed Bidi: ABC אבג 123", style = TextStyle(color = Accent)),
                    Text("unsupported: \uDBFF\uDFFF (one fallback unit)", style = TextStyle(color = Muted)),
                ),
                spacing = 2,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            ),
        )
    }

    /** Hosts a real IME-connected TextField inside both SafeArea and view-inset avoidance. */
    private fun inputPanel(): Widget {
        return samplePanel(
            title = "Retained TextField / IME",
            color = Cyan,
            child = ImeAvoidingView(
                child = Column(
                    children = listOf(
                        TextField(
                            state = textState,
                            controller = textController,
                            placeholder = "输入中文 / emoji / RTL",
                            minLines = 2,
                            maxLines = 3,
                            focusNode = inputFocusNode,
                            onChanged = { value ->
                                textStatus = "typing ${value.length} UTF-16 units"
                                setState {}
                            },
                            borderColor = Cyan,
                            fillColor = PixelColor.fromRgb(8, 16, 20),
                            semanticLabel = "Adaptive Unicode text input",
                            key = "adaptive-retained-text-field",
                        ),
                        Row(
                            children = listOf(
                                OutlinedButton(
                                    text = "FOCUS",
                                    onPressed = { inputFocusNode.requestFocus() },
                                    borderColor = Cyan,
                                ),
                                Text(
                                    "sel=${textState.selectionStart}..${textState.selectionEnd} " +
                                        "focus=${textState.isFocused}",
                                    style = TextStyle(color = Muted),
                                ),
                            ),
                            spacing = 2,
                        ),
                        Text(textStatus, style = TextStyle(color = Muted)),
                    ),
                    spacing = 2,
                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                ),
            ),
        )
    }

    /** Renders two always-mounted histories and a controller-bound localized NavigationBar. */
    private fun navigationPanel(): Widget {
        /** Stable definitions preserving each Navigator State across Host environment updates. */
        val stacks = listOf(
            PixelNavigatorStack(id = HOME_STACK_ID, initialRoute = homeRootRoute),
            PixelNavigatorStack(id = SETTINGS_STACK_ID, initialRoute = settingsRootRoute),
        )
        return samplePanel(
            title = "Retained Navigation multi-stack",
            color = Green,
            child = Column(
                children = listOf(
                    Container(
                        height = 52,
                        borderColor = Green,
                        child = PixelMultiStackNavigator(
                            stacks = stacks,
                            controller = navigationController,
                            vsync = widget.env.vsync,
                            transitionDuration = kotlin.time.Duration.ZERO,
                            defaultTransition = PixelRouteTransition.None,
                            key = "adaptive-multi-stack-host",
                        ),
                    ),
                    NavigationBar(
                        destinations = navigationDestinations(),
                        controller = navigationController,
                        popToRootOnReselect = true,
                        animated = false,
                        key = "adaptive-navigation-bar",
                    ),
                    Text(
                        "active=${navigationController.activeStackId}; Back 先 pop 当前栈再回 HOME",
                        style = TextStyle(color = Muted),
                    ),
                ),
                spacing = 2,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            ),
        )
    }

    /** Builds stable navigation metadata whose ids, rather than labels or positions, own state. */
    private fun navigationDestinations(): List<PixelNavigationDestination> {
        /** Deterministic icon shared by both destinations while ids remain distinct. */
        val icon = PixelIconData(bitmap = AdaptiveNavigationIcon)
        return listOf(
            PixelNavigationDestination(id = HOME_STACK_ID, label = "Home", icon = icon),
            PixelNavigationDestination(id = SETTINGS_STACK_ID, label = "Settings", icon = icon),
        )
    }

    /** Formats one live metric into a fixed-label Row suitable for small logical viewports. */
    private fun metric(label: String, value: String): Widget {
        return Row(
            children = listOf(
                Container(width = 54, child = Text(label, style = TextStyle(color = Muted))),
                Text(value, style = TextStyle(color = PixelColor.White)),
            ),
            spacing = 2,
            crossAxisAlignment = CrossAxisAlignment.CENTER,
        )
    }

    /** Builds a compact route page used by both independent retained histories. */
    private fun navigationPage(
        title: String,
        color: PixelColor,
        status: String,
        actionLabel: String,
        onAction: () -> Unit,
    ): Widget {
        return Container(
            padding = EdgeInsets.all(3),
            fillColor = PixelColor.fromRgb(10, 14, 18),
            child = Column(
                children = listOf(
                    Text(title, style = TextStyle(color = color)),
                    Text(status, style = TextStyle(color = Muted)),
                    OutlinedButton(text = actionLabel, onPressed = onAction, borderColor = color),
                ),
                spacing = 2,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            ),
        )
    }

    /** Stable stack and route identifiers shared by controls and multi-stack definitions. */
    private companion object {
        /** Initial stack and controller fallback target. */
        const val HOME_STACK_ID: String = "adaptive-home-stack"

        /** Secondary independent stack used to exercise root fallback behavior. */
        const val SETTINGS_STACK_ID: String = "adaptive-settings-stack"
    }
}

/** Deterministic 5×5 alpha mask used by both navigation destinations. */
private val AdaptiveNavigationIcon: PixelBitmap = PixelBitmap(
    width = 5,
    height = 5,
    pixels = intArrayOf(
        PixelColor.Transparent.argb,
        PixelColor.White.argb,
        PixelColor.White.argb,
        PixelColor.White.argb,
        PixelColor.Transparent.argb,
        PixelColor.White.argb,
        PixelColor.Transparent.argb,
        PixelColor.White.argb,
        PixelColor.Transparent.argb,
        PixelColor.White.argb,
        PixelColor.White.argb,
        PixelColor.White.argb,
        PixelColor.White.argb,
        PixelColor.White.argb,
        PixelColor.White.argb,
        PixelColor.White.argb,
        PixelColor.Transparent.argb,
        PixelColor.White.argb,
        PixelColor.Transparent.argb,
        PixelColor.White.argb,
        PixelColor.Transparent.argb,
        PixelColor.White.argb,
        PixelColor.White.argb,
        PixelColor.White.argb,
        PixelColor.Transparent.argb,
    ),
)

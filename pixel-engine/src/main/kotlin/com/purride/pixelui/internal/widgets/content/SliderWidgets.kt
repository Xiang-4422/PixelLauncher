package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.FocusNodeScope
import com.purride.pixelui.PixelControlColorMotion
import com.purride.pixelui.PixelControlMotionValue
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelLocalizations
import com.purride.pixelui.PixelMotionScope
import com.purride.pixelui.PixelMotionSpec
import com.purride.pixelui.PixelMotionTheme
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsActions
import com.purride.pixelui.PixelSemanticsRangeInfo
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.Semantics
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.getInheritedWidgetOfExactType
import kotlin.math.roundToInt
import kotlin.time.Duration

// ─────────────────────────────────────────────────────────────────────
//  Public-facing widget wrapper (internal to pixel-engine)
// ─────────────────────────────────────────────────────────────────────

/**
 * 像素风滑块 widget。
 *
 * @param value     当前值，0.0..1.0
 * @param onDrag    手指滑动时持续调用（值已钳位到 0..1）
 * @param onRelease 手指抬起时调用（值已钳位到 0..1）
 * @param onSetValue 键盘和无障碍动作提交完整值时调用，返回是否接受该值。
 * @param onPressedChanged 指针按压状态变化回调。
 * @param onHoveredChanged 鼠标或触控笔悬停状态变化回调。
 * @param activeColor  已填充区域颜色，默认橙色
 * @param trackColor   轨道边框/空区域颜色，默认白色
 * @param semanticLabel 与当前值分离的无障碍名称。
 * @param semanticValue 可选的本地化无障碍值；为 null 时生成百分比。
 * @param semanticSteps 两端点之间的离散步数；`0` 表示连续值。
 * @param enabled 是否接受指针、键盘和无障碍输入。
 * @param key retained widget identity。
 */
internal data class SliderWidget(
    /** 调用方持有的受控值。 */
    val value: Float,
    /** Persistent caller states merged with retained pointer and focus states. */
    val states: PixelControlStateSet = PixelControlStateSet.Normal,
    /** 拖动期间持续提交归一化值的回调。 */
    val onDrag: (Float) -> Unit,
    /** 指针释放时提交最终归一化值的回调。 */
    val onRelease: (Float) -> Unit,
    /** Shared committed-value callback used by semantics and keyboard adapters. */
    val onSetValue: (Float) -> Boolean = { requestedValue ->
        /** 统一钳位后同时交给拖动与释放契约的提交值。 */
        val nextValue = normalizeSliderValue(requestedValue)
        onDrag(nextValue)
        onRelease(nextValue)
        true
    },
    /** Internal pressed micro-state callback supplied by the standard Slider wrapper. */
    val onPressedChanged: ((Boolean) -> Unit)? = null,
    /** Internal hover micro-state callback supplied by the standard Slider wrapper. */
    val onHoveredChanged: ((Boolean) -> Unit)? = null,
    /** 已填充轨道的基础颜色。 */
    val activeColor: PixelColor? = null,
    /** 空轨道、边框和 thumb 的基础颜色。 */
    val trackColor: PixelColor? = null,
    /** Whether a mount without an explicit PixelTheme uses pre-token paint and geometry. */
    val legacyFacade: Boolean = false,
    /** Exact historical active color retained before facade sentinel conversion. */
    val legacyActiveColor: PixelColor = PixelColor.fromRgb(200, 100, 0),
    /** Exact historical track color retained before facade sentinel conversion. */
    val legacyTrackColor: PixelColor = PixelColor.White,
    /** Spoken control label. */
    val semanticLabel: String? = null,
    /** Optional spoken value; a percentage is generated when null. */
    val semanticValue: String? = null,
    /** Number of discrete semantic steps between the endpoints. */
    val semanticSteps: Int = 0,
    /** Whether pointer, keyboard, and semantic value changes are accepted. */
    val enabled: Boolean = true,
    /** Whether Loading should retain focus even though [enabled] rejects mutation. */
    val focusable: Boolean = enabled,
    /** retained widget identity。 */
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    /** 创建程序值 selection、直接拖动态和微交互反馈的 retained owner。 */
    override fun createState(): State<out StatefulWidget> = SliderMotionState()
}

/** Slider 程序值动画、直接拖动与 pressed/hover/focus 反馈的状态所有者。 */
private class SliderMotionState : State<SliderWidget>() {
    /** 当前绘制值；程序更新使用 selection token，拖动时同步跟手。 */
    private lateinit var selectionMotion: PixelControlMotionValue

    /** Retained active-track color transition initialized after first token resolution. */
    private var activeColorMotion: PixelControlColorMotion? = null

    /** Retained background-track color transition initialized after first token resolution. */
    private var trackColorMotion: PixelControlColorMotion? = null

    /** Retained optional-outline color transition initialized after first token resolution. */
    private var borderColorMotion: PixelControlColorMotion? = null

    /** 当前指针是否在 Slider 上保持按下。 */
    private var pressed: Boolean = false

    /** 当前鼠标或触控笔是否在 Slider 上悬停。 */
    private var hovered: Boolean = false

    /** 指针是否已开始改变 Slider 值；此阶段程序值同步呈现而不补间。 */
    private var dragging: Boolean = false

    /** 最近一次直接指针值，确保调用方延迟回写时 thumb 仍逐像素跟手。 */
    private var dragVisualValue: Float? = null

    /** 用首次受控值初始化绘制值，避免 mount 时从零补动画。 */
    override fun initState() {
        selectionMotion = PixelControlMotionValue(normalizeSliderValue(widget.value))
    }

    /** 释放 selection 和 feedback 驱动器拥有的 ticker。 */
    override fun dispose() {
        selectionMotion.dispose()
        activeColorMotion?.dispose()
        trackColorMotion?.dispose()
        borderColorMotion?.dispose()
    }

    /**
     * 区分程序更新和直接拖动，并只把最终视觉颜色交给无状态 RenderSlider 绘制。
     */
    override fun build(context: BuildContext): com.purride.pixelui.Widget {
        /** Old facades preserve exact historical pixels only outside an explicit theme boundary. */
        val usesScopeLessLegacyVisuals = widget.legacyFacade && PixelTheme.maybeTokensOf(context) == null
        /** 当前主题提供的 selection 与 feedback 运动 token。 */
        val motionTheme = PixelMotionTheme.of(context)
        /** 可选的统一 ticker 与 reduced-motion 设置来源。 */
        val motionScope = PixelMotionScope.maybeOf(context)
        configureSliderMotion(selectionMotion, motionScope, motionTheme.selection)
        /** Complete semantic token graph resolved from the nearest provider. */
        val themeTokens = PixelTheme.tokensOf(context)
        /** Optional bundle supplies provider-aware labels and percentage formatting. */
        val localization = PixelLocalizations.maybeOf(context)
        /** Slider-specific role and geometry tokens. */
        val componentTokens = themeTokens.components.slider

        /** 已处理 NaN 和越界输入的调用方受控值。 */
        val controlledValue = normalizeSliderValue(widget.value)
        if (!widget.enabled) {
            pressed = false
            hovered = false
            dragging = false
            dragVisualValue = null
            selectionMotion.snapTo(controlledValue)
        } else if (dragging) {
            selectionMotion.snapTo(dragVisualValue ?: controlledValue)
        } else {
            selectionMotion.animateTo(controlledValue)
        }
        /** 复用外层自动焦点边界提供的节点。 */
        val focusNode = context.getInheritedWidgetOfExactType<FocusNodeScope>()?.node
        if (focusNode != null) context.watch(focusNode)
        /** 仅在控件可用且继承节点获得焦点时呈现焦点反馈。 */
        val focused = widget.focusable && focusNode?.isFocused == true
        /** Runtime states combine caller state with retained pointer and focus flags. */
        val runtimeStates = mergeControlStates(
            persistent = widget.states,
            disabled = !widget.focusable,
            pressed = widget.enabled && pressed,
            hovered = widget.enabled && hovered,
            focused = focused,
        )
        /** Focus is rendered as an additive outline rather than replacing a track color role. */
        val baseStates = runtimeStates - com.purride.pixelui.PixelControlState.Focused
        /** Theme target for the active value region unless the public color overrides it. */
        val targetActiveColor = if (usesScopeLessLegacyVisuals) {
            widget.legacyActiveColor
        } else {
            widget.activeColor
                ?: componentTokens.resolveContentColor(baseStates, themeTokens.colors)
                ?: themeTokens.colors.primary
        }
        /** Theme target for the empty track unless the public color overrides it. */
        val targetTrackColor = if (usesScopeLessLegacyVisuals) {
            widget.legacyTrackColor
        } else {
            widget.trackColor
                ?: componentTokens.resolveContainerColor(baseStates, themeTokens.colors)
                ?: themeTokens.colors.track
        }
        /** Optional themed outline; legacy paint owns its historical one-pixel white frame. */
        val targetBorderColor = if (usesScopeLessLegacyVisuals) {
            null
        } else {
            componentTokens.resolveBorderColor(baseStates, themeTokens.colors)
        }
        /** Transparent endpoint retained when the component intentionally omits an outline. */
        val concreteBorderColor = targetBorderColor ?: PixelColor.Transparent
        /** Retained channels initialized from the exact first resolved colors. */
        val resolvedActiveMotion = activeColorMotion
            ?: PixelControlColorMotion(targetActiveColor).also { motion -> activeColorMotion = motion }
        val resolvedTrackMotion = trackColorMotion
            ?: PixelControlColorMotion(targetTrackColor).also { motion -> trackColorMotion = motion }
        /** Border feedback channel initialized from the exact first resolved endpoint. */
        val resolvedBorderMotion = borderColorMotion
            ?: PixelControlColorMotion(concreteBorderColor).also { motion -> borderColorMotion = motion }
        /** Feedback Motion policy shared by both color channels. */
        val feedbackSpec = motionTheme.feedback
        /** Runtime feedback policy after host reduce-motion settings. */
        val resolvedFeedback = motionScope?.let { scope -> feedbackSpec.resolve(scope.settings) }
        listOf(resolvedActiveMotion, resolvedTrackMotion, resolvedBorderMotion).forEach { motion ->
            configureControlColorMotion(
                motion = motion,
                scope = motionScope,
                resolvedDuration = resolvedFeedback?.duration ?: Duration.ZERO,
                resolvedDelay = resolvedFeedback?.delay ?: Duration.ZERO,
                resolvedCurve = resolvedFeedback?.curve ?: feedbackSpec.curve,
                immediate = resolvedFeedback?.let { resolved ->
                    resolved.isImmediate ||
                        resolved.transition == com.purride.pixelui.PixelMotionTransitionPreset.None
                } ?: true,
            )
        }
        if (widget.enabled) {
            resolvedActiveMotion.animateTo(targetActiveColor)
            resolvedTrackMotion.animateTo(targetTrackColor)
            resolvedBorderMotion.animateTo(concreteBorderColor)
        } else {
            resolvedActiveMotion.snapTo(targetActiveColor)
            resolvedTrackMotion.snapTo(targetTrackColor)
            resolvedBorderMotion.snapTo(concreteBorderColor)
        }
        selectionMotion.watch(context)
        resolvedActiveMotion.watch(context)
        resolvedTrackMotion.watch(context)
        resolvedBorderMotion.watch(context)
        /** Themed content padding projected from the shared spacing scale. */
        val resolvedPadding = if (usesScopeLessLegacyVisuals) {
            EdgeInsets.all(0)
        } else {
            componentTokens.resolvePadding(themeTokens.spacing)
        }
        /** Themed minimum width; zero retains the traditional fill-available-width behavior. */
        val resolvedMinimumWidth = if (usesScopeLessLegacyVisuals) {
            0
        } else {
            componentTokens.resolveMinimumWidth(themeTokens.sizes)
        }
        /** Historical height remains seven pixels outside an explicit theme boundary. */
        val resolvedHeight = if (usesScopeLessLegacyVisuals) {
            LEGACY_SLIDER_HEIGHT_PX
        } else {
            componentTokens.resolveMinimumHeight(themeTokens.sizes).coerceAtLeast(1)
        }
        /** Foundation-resolved outline width, unused by the dedicated legacy painter. */
        val resolvedBorderWidth = if (usesScopeLessLegacyVisuals) {
            0
        } else {
            componentTokens.resolveBorderWidth(themeTokens.borders)
        }
        /** Foundation-resolved pixel stair-step radius. */
        val resolvedCornerRadius = if (usesScopeLessLegacyVisuals) {
            0
        } else {
            componentTokens.resolveCornerRadius(themeTokens.radii)
        }
        /** Hard shadow offset selected by the semantic elevation role. */
        val resolvedElevation = if (usesScopeLessLegacyVisuals) {
            0
        } else {
            componentTokens.resolveElevation(themeTokens.elevations)
        }
        /** 只接收最终视觉值与底层指针回调的 render leaf。 */
        val sliderSurface = SliderRenderWidget(
            value = selectionMotion.value,
            onDrag = ::handleDrag,
            onRelease = ::handleRelease,
            onPressedChanged = ::handlePressedChanged,
            onHoveredChanged = ::handleHoveredChanged,
            enabled = widget.enabled,
            legacyVisuals = usesScopeLessLegacyVisuals,
            minimumWidth = resolvedMinimumWidth,
            height = resolvedHeight,
            padding = resolvedPadding,
            activeColor = resolvedActiveMotion.value,
            trackColor = resolvedTrackMotion.value,
            borderColor = resolvedBorderMotion.value.takeIf { targetBorderColor != null },
            borderWidth = resolvedBorderWidth,
            cornerRadius = resolvedCornerRadius,
            shadowColor = themeTokens.colors.shadow.takeIf { resolvedElevation > 0 },
            shadowOffset = resolvedElevation,
            key = widget.key,
        )
        /** Focus indicator remains an independent layer above every base state role. */
        val slider = withControlFocusIndicator(
            child = sliderSurface,
            states = runtimeStates,
            componentTokens = componentTokens,
            colors = themeTokens.colors,
            borders = themeTokens.borders,
            key = widget.key?.let { "$it-focus-indicator" },
        )
        /** 明确值优先，否则从当前受控值生成稳定百分比。 */
        val semanticValue = widget.semanticValue
            ?: localization?.formatPercent(controlledValue)
            ?: "${(controlledValue * 100f).roundToInt()}%"
        /** Explicit non-blank labels win; omitted facade defaults use the localizable theme token. */
        val semanticLabel = widget.semanticLabel?.takeIf { label -> label.isNotBlank() }
            ?: localization?.labels?.slider
            ?: themeTokens.labels.slider
        return Semantics(
            label = semanticLabel,
            role = PixelSemanticRole.SLIDER,
            enabled = widget.enabled,
            focused = focused,
            value = semanticValue,
            rangeInfo = PixelSemanticsRangeInfo(
                current = controlledValue,
                minimum = 0f,
                maximum = 1f,
                steps = widget.semanticSteps.coerceAtLeast(0),
            ),
            excludeDescendants = true,
            actions = PixelSemanticsActions(
                onSetProgress = widget.onSetValue.takeIf { widget.enabled },
            ),
            child = slider,
            key = widget.key?.let { "$it-semantics" },
        )
    }

    /** 让拖动值在本地立即呈现，再把同一个钳位值交给受控状态所有者。 */
    private fun handleDrag(rawValue: Float) {
        if (!widget.enabled) return
        /** 本次拖动可直接呈现并回传的安全值。 */
        val nextValue = normalizeSliderValue(rawValue)
        selectionMotion.snapTo(nextValue)
        setState {
            dragging = true
            dragVisualValue = nextValue
        }
        widget.onDrag(nextValue)
    }

    /** 抬手时结束直接跟手阶段；调用方若量化新值，下一帧会从当前位置 settle。 */
    private fun handleRelease(rawValue: Float) {
        if (!widget.enabled) return
        /** 本次指针生命周期结束时的安全最终值。 */
        val releasedValue = normalizeSliderValue(rawValue)
        selectionMotion.snapTo(releasedValue)
        setState {
            dragging = false
            pressed = false
            dragVisualValue = null
        }
        widget.onRelease(releasedValue)
    }

    /** 同步 router 的 down/up/cancel pressed 状态，取消时也退出直接拖动阶段。 */
    private fun handlePressedChanged(nextPressed: Boolean) {
        if (!widget.enabled && nextPressed) return
        if (pressed == nextPressed && (nextPressed || !dragging)) return
        setState {
            pressed = nextPressed
            if (!nextPressed) {
                dragging = false
                dragVisualValue = null
            }
        }
        widget.onPressedChanged?.invoke(nextPressed)
    }

    /** 同步鼠标或触控笔 hover 状态。 */
    private fun handleHoveredChanged(nextHovered: Boolean) {
        if (!widget.enabled && nextHovered) return
        if (hovered == nextHovered) return
        setState { hovered = nextHovered }
        widget.onHoveredChanged?.invoke(nextHovered)
    }
}

/**
 * 将 Motion token 解析成 Slider 驱动器可消费的统一时钟配置。
 *
 * @param motion 待配置的 retained 动画值。
 * @param scope 可选统一时钟与运动偏好来源。
 * @param spec 当前状态通道使用的主题运动规格。
 */
private fun configureSliderMotion(
    motion: PixelControlMotionValue,
    scope: PixelMotionScope?,
    spec: PixelMotionSpec,
) {
    /** 已应用 scope 设置的运行时运动规格；无 scope 时由调用方走即时更新。 */
    val resolved = scope?.let { availableScope -> spec.resolve(availableScope.settings) }
    motion.configure(
        nextVsync = scope?.vsync,
        nextDuration = resolved?.duration ?: Duration.ZERO,
        nextDelay = resolved?.delay ?: Duration.ZERO,
        nextCurve = resolved?.curve ?: spec.curve,
        nextImmediate = resolved?.let { motion ->
            motion.isImmediate || motion.transition == com.purride.pixelui.PixelMotionTransitionPreset.None
        } ?: true,
    )
}

/**
 * 把外部或指针输入归一化，NaN 使用安全的左端点。
 *
 * @param value 待限制到 `0f..1f` 的原始值。
 */
private fun normalizeSliderValue(value: Float): Float {
    return if (value.isNaN()) 0f else value.coerceIn(0f, 1f)
}

/** 只负责绘制已解析视觉值并导出 Slider 命中目标的 leaf widget。 */
private data class SliderRenderWidget(
    /** 当前实际绘制值。 */
    val value: Float,
    /** 直接拖动回调。 */
    val onDrag: (Float) -> Unit,
    /** 抬手回调。 */
    val onRelease: (Float) -> Unit,
    /** pressed 状态回调。 */
    val onPressedChanged: ((Boolean) -> Unit)?,
    /** hover 状态回调。 */
    val onHoveredChanged: ((Boolean) -> Unit)?,
    /** Whether this render object exports a pointer Slider target. */
    val enabled: Boolean,
    /** Whether paint and sizing must use the exact pre-token algorithm. */
    val legacyVisuals: Boolean,
    /** Theme-resolved minimum logical width; zero preserves fill-available sizing. */
    val minimumWidth: Int,
    /** Theme-resolved logical track height. */
    val height: Int,
    /** Theme-resolved insets between the component surface and active range. */
    val padding: EdgeInsets,
    /** 已应用反馈的 active 颜色。 */
    val activeColor: PixelColor,
    /** 已应用反馈的 track/thumb 颜色。 */
    val trackColor: PixelColor,
    /** Optional themed component outline color. */
    val borderColor: PixelColor?,
    /** Foundation-resolved component outline width. */
    val borderWidth: Int,
    /** Foundation-resolved component stair-step radius. */
    val cornerRadius: Int,
    /** Optional hard-shadow color selected by the theme elevation role. */
    val shadowColor: PixelColor?,
    /** Positive diagonal hard-shadow offset included in layout. */
    val shadowOffset: Int,
    /** retained render identity。 */
    override val key: Any? = null,
) : LeafRenderObjectWidget(key = key) {

    /** 创建无状态 Slider render object。 */
    override fun createRenderObject(context: BuildContext): RenderObject =
        RenderSlider(
            value = value,
            onDrag = onDrag,
            onRelease = onRelease,
            onPressedChanged = onPressedChanged,
            onHoveredChanged = onHoveredChanged,
            enabled = enabled,
            legacyVisuals = legacyVisuals,
            minimumWidth = minimumWidth,
            height = height,
            padding = padding,
            activeColor = activeColor,
            trackColor = trackColor,
            borderColor = borderColor,
            borderWidth = borderWidth,
            cornerRadius = cornerRadius,
            shadowColor = shadowColor,
            shadowOffset = shadowOffset,
        )

    /** 把新帧视觉值与最新交互回调同步到已有 render object。 */
    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderSlider).update(
            value = value,
            onDrag = onDrag,
            onRelease = onRelease,
            onPressedChanged = onPressedChanged,
            onHoveredChanged = onHoveredChanged,
            enabled = enabled,
            legacyVisuals = legacyVisuals,
            minimumWidth = minimumWidth,
            height = height,
            padding = padding,
            activeColor = activeColor,
            trackColor = trackColor,
            borderColor = borderColor,
            borderWidth = borderWidth,
            cornerRadius = cornerRadius,
            shadowColor = shadowColor,
            shadowOffset = shadowOffset,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
//  RenderSlider — leaf render object
// ─────────────────────────────────────────────────────────────────────

/**
 * 绘制 Slider 轨道和 thumb，并向输入路由导出同一矩形命中目标。
 *
 * @param value 当前绘制值。
 * @param onDrag 拖动期间的值回调。
 * @param onRelease 指针释放时的值回调。
 * @param onPressedChanged 按压微状态回调。
 * @param onHoveredChanged 悬停微状态回调。
 * @param enabled 是否导出指针命中目标。
 * @param legacyVisuals 是否使用精确的 pre-token 布局与绘制算法。
 * @param minimumWidth 主题解析后的最小逻辑宽度。
 * @param height 主题解析后的轨道高度。
 * @param padding 组件表面与 active range 之间的主题内边距。
 * @param activeColor 已填充轨道颜色。
 * @param trackColor 边框、空轨道与 thumb 颜色。
 * @param borderColor 可选主题边框颜色。
 * @param borderWidth 主题解析后的边框宽度。
 * @param cornerRadius 主题解析后的像素圆角。
 * @param shadowColor 可选硬阴影颜色。
 * @param shadowOffset 参与布局的正向硬阴影偏移。
 */
internal class RenderSlider(
    /** 当前由受控状态或拖动阶段解析出的绘制值。 */
    private var value: Float,
    /** 路由拖动值到 widget 状态的回调。 */
    private var onDrag: (Float) -> Unit,
    /** 路由最终值到 widget 状态的回调。 */
    private var onRelease: (Float) -> Unit,
    /** Callback receiving active pointer press changes for the owning widget. */
    private var onPressedChanged: ((Boolean) -> Unit)?,
    /** Callback receiving mouse or stylus hover changes for the owning widget. */
    private var onHoveredChanged: ((Boolean) -> Unit)?,
    /** Whether this render object currently exports pointer interaction. */
    private var enabled: Boolean,
    /** Whether this instance paints with the exact historical algorithm. */
    private var legacyVisuals: Boolean,
    /** Theme-resolved minimum logical width. */
    private var minimumWidth: Int,
    /** Current theme-resolved logical track height. */
    private var height: Int,
    /** Theme-resolved component-to-range insets. */
    private var padding: EdgeInsets,
    /** 已填充轨道的最终视觉颜色。 */
    private var activeColor: PixelColor,
    /** 边框、空轨道和 thumb 的最终视觉颜色。 */
    private var trackColor: PixelColor,
    /** Optional themed outline color. */
    private var borderColor: PixelColor?,
    /** Current themed outline width. */
    private var borderWidth: Int,
    /** Current themed stair-step radius. */
    private var cornerRadius: Int,
    /** Optional themed hard-shadow color. */
    private var shadowColor: PixelColor?,
    /** Current positive diagonal shadow offset. */
    private var shadowOffset: Int,
) : RenderBox() {

    /** Uses legacy fill sizing or a token minimum-width surface with measured elevation extent. */
    override fun layout(constraints: RenderConstraints) {
        if (legacyVisuals) {
            size = RenderSize(
                width = constraints.maxWidth,
                height = constraints.constrainHeight(LEGACY_SLIDER_HEIGHT_PX),
            )
            return
        }
        /** Shadow extent is layout-neutral when no concrete shadow color is available. */
        val decorationExtent = shadowOffset.coerceAtLeast(0).takeIf { shadowColor != null } ?: 0
        /** Safe outline width used to keep active paint out of the component border. */
        val outlineInset = borderWidth.coerceAtLeast(0).takeIf { borderColor != null } ?: 0
        /** Smallest main surface that leaves one drawable range pixel after insets. */
        val geometryWidthFloor = padding.left.coerceAtLeast(0) + padding.right.coerceAtLeast(0) +
            outlineInset * 2 + 1
        /** A positive token supplies natural width; zero retains fill-available behavior. */
        val requestedMainWidth = if (minimumWidth > 0) {
            maxOf(minimumWidth, geometryWidthFloor)
        } else {
            (constraints.maxWidth - decorationExtent).coerceAtLeast(geometryWidthFloor)
        }
        /** Main height keeps both the declared minimum and one drawable inset range pixel. */
        val requestedMainHeight = maxOf(
            height.coerceAtLeast(1),
            padding.top.coerceAtLeast(0) + padding.bottom.coerceAtLeast(0) + outlineInset * 2 + 1,
        )
        size = RenderSize(
            width = constraints.constrainWidth(requestedMainWidth + decorationExtent),
            height = constraints.constrainHeight(requestedMainHeight + decorationExtent),
        )
    }

    /** Draws the historical frame or every themed surface, range, outline, and elevation layer. */
    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        if (legacyVisuals) {
            paintLegacySlider(context, offsetX, offsetY)
            return
        }
        paintThemedSlider(context, offsetX, offsetY)
    }

    /** Draws the exact pre-token white frame, orange range, and one-pixel thumb algorithm. */
    private fun paintLegacySlider(context: PaintContext, offsetX: Int, offsetY: Int) {
        /** 已布局的轨道宽度。 */
        val w = size.width
        /** 固定轨道高度。 */
        val h = size.height
        if (w < 3 || h < 3) return

        /** 钳位后的最终绘制进度。 */
        val v = value.coerceIn(0f, 1f)

        // Outer border
        context.drawRect(offsetX, offsetY, w, h, trackColor)

        // Filled (active) region: inside the border, left portion
        /** 去除左右边框后的内部轨道宽度。 */
        val innerW = w - 2
        /** 当前进度覆盖的整数像素宽度。 */
        val fillW = (innerW * v).roundToInt().coerceIn(0, innerW)
        if (fillW > 0) {
            context.fillRect(offsetX + 1, offsetY + 1, fillW, h - 2, activeColor)
        }

        // Thumb: 1-px-wide bright column at the fill edge (only if not at extremes)
        if (fillW in 1 until innerW) {
            /** thumb 在缓冲区中的绝对横坐标。 */
            val thumbX = offsetX + 1 + fillW - 1
            for (y in offsetY + 1 until offsetY + h - 1) {
                context.buffer.setPixel(thumbX, y, trackColor)
            }
        }
    }

    /** Draws token-resolved fill, range, outline, radius, padding, and hard elevation. */
    private fun paintThemedSlider(context: PaintContext, offsetX: Int, offsetY: Int) {
        /** Shadow extent is included only while a concrete shadow is painted. */
        val decorationExtent = shadowOffset.coerceAtLeast(0).takeIf { shadowColor != null } ?: 0
        /** Main themed surface width excluding the reserved shadow extension. */
        val surfaceWidth = (size.width - decorationExtent).coerceAtLeast(0)
        /** Main themed surface height excluding the reserved shadow extension. */
        val surfaceHeight = (size.height - decorationExtent).coerceAtLeast(0)
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return
        /** Safe stair-step radius bounded again by actual render geometry. */
        val safeRadius = cornerRadius.coerceAtLeast(0)
        shadowColor?.takeIf { decorationExtent > 0 }?.let { color ->
            paintSliderRoundedRect(
                context = context,
                left = offsetX + decorationExtent,
                top = offsetY + decorationExtent,
                width = surfaceWidth,
                height = surfaceHeight,
                radius = safeRadius,
                color = color,
            )
        }
        paintSliderRoundedRect(
            context = context,
            left = offsetX,
            top = offsetY,
            width = surfaceWidth,
            height = surfaceHeight,
            radius = safeRadius,
            color = trackColor,
        )
        /** Outline inset keeps range paint below every declared border layer. */
        val outlineInset = borderWidth.coerceAtLeast(0).takeIf { borderColor != null } ?: 0
        /** Left edge of the token-padded active range. */
        val rangeLeft = offsetX + padding.left.coerceAtLeast(0) + outlineInset
        /** Top edge of the token-padded active range. */
        val rangeTop = offsetY + padding.top.coerceAtLeast(0) + outlineInset
        /** Drawable range width after padding and outline reservation. */
        val rangeWidth = (
            surfaceWidth - padding.left.coerceAtLeast(0) - padding.right.coerceAtLeast(0) - outlineInset * 2
        ).coerceAtLeast(0)
        /** Drawable range height after padding and outline reservation. */
        val rangeHeight = (
            surfaceHeight - padding.top.coerceAtLeast(0) - padding.bottom.coerceAtLeast(0) - outlineInset * 2
        ).coerceAtLeast(0)
        /** Controlled normalized progress converted to an integer active-range width. */
        val fillWidth = (rangeWidth * value.coerceIn(0f, 1f)).roundToInt().coerceIn(0, rangeWidth)
        if (fillWidth > 0 && rangeHeight > 0) {
            paintSliderRoundedRect(
                context = context,
                left = rangeLeft,
                top = rangeTop,
                width = fillWidth,
                height = rangeHeight,
                radius = (safeRadius - maxOf(padding.left, padding.top, outlineInset)).coerceAtLeast(0),
                color = activeColor,
            )
        }
        if (fillWidth in 1 until rangeWidth && rangeHeight > 0) {
            /** One-pixel thumb column located at the active range endpoint. */
            val thumbX = rangeLeft + fillWidth - 1
            context.fillRect(thumbX, rangeTop, 1, rangeHeight, borderColor ?: trackColor)
        }
        borderColor?.takeIf { borderWidth > 0 }?.let { color ->
            paintSliderRoundedBorder(
                context = context,
                left = offsetX,
                top = offsetY,
                width = surfaceWidth,
                height = surfaceHeight,
                radius = safeRadius,
                thickness = borderWidth,
                color = color,
            )
        }
    }

    /** 在可用时导出与绘制边界一致的 Slider 指针目标。 */
    override fun collectSliderTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelSliderTarget>,
    ) {
        if (!enabled) return
        /** Shadow pixels are visual-only and excluded from drag value conversion. */
        val decorationExtent = if (legacyVisuals) {
            0
        } else {
            shadowOffset.coerceAtLeast(0).takeIf { shadowColor != null } ?: 0
        }
        targets += PixelSliderTarget(
            bounds = PixelRect(
                left = offsetX,
                top = offsetY,
                width = (size.width - decorationExtent).coerceAtLeast(0),
                height = (size.height - decorationExtent).coerceAtLeast(0),
            ),
            onDrag = onDrag,
            onRelease = onRelease,
            onPressedChanged = onPressedChanged,
            onHoveredChanged = onHoveredChanged,
            source = this,
        )
    }

    /** 把下一帧受控值、回调和颜色同步到 retained render object。 */
    fun update(
        value: Float,
        onDrag: (Float) -> Unit,
        onRelease: (Float) -> Unit,
        onPressedChanged: ((Boolean) -> Unit)?,
        onHoveredChanged: ((Boolean) -> Unit)?,
        enabled: Boolean,
        legacyVisuals: Boolean,
        minimumWidth: Int,
        height: Int,
        padding: EdgeInsets,
        activeColor: PixelColor,
        trackColor: PixelColor,
        borderColor: PixelColor?,
        borderWidth: Int,
        cornerRadius: Int,
        shadowColor: PixelColor?,
        shadowOffset: Int,
    ) {
        /** 是否存在需要重绘或更新输入路由的字段变化。 */
        val changed = this.value != value ||
            this.onDrag !== onDrag ||
            this.onRelease !== onRelease ||
            this.onPressedChanged !== onPressedChanged ||
            this.onHoveredChanged !== onHoveredChanged ||
            this.enabled != enabled ||
            this.legacyVisuals != legacyVisuals ||
            this.minimumWidth != minimumWidth ||
            this.height != height ||
            this.padding != padding ||
            this.activeColor != activeColor ||
            this.trackColor != trackColor ||
            this.borderColor != borderColor ||
            this.borderWidth != borderWidth ||
            this.cornerRadius != cornerRadius ||
            this.shadowColor != shadowColor ||
            this.shadowOffset != shadowOffset
        if (!changed) return
        this.value = value
        this.onDrag = onDrag
        this.onRelease = onRelease
        this.onPressedChanged = onPressedChanged
        this.onHoveredChanged = onHoveredChanged
        this.enabled = enabled
        /** Whether any geometry or elevation field requires a fresh layout pass. */
        val needsLayout = this.legacyVisuals != legacyVisuals ||
            this.minimumWidth != minimumWidth ||
            this.height != height ||
            this.padding != padding ||
            (this.borderColor == null) != (borderColor == null) ||
            this.borderWidth != borderWidth ||
            this.shadowColor != shadowColor ||
            this.shadowOffset != shadowOffset
        this.legacyVisuals = legacyVisuals
        this.minimumWidth = minimumWidth.coerceAtLeast(0)
        this.height = height
        this.padding = padding
        this.activeColor = activeColor
        this.trackColor = trackColor
        this.borderColor = borderColor
        this.borderWidth = borderWidth.coerceAtLeast(0)
        this.cornerRadius = cornerRadius.coerceAtLeast(0)
        this.shadowColor = shadowColor
        this.shadowOffset = shadowOffset.coerceAtLeast(0)
        if (needsLayout) markNeedsLayout()
        markNeedsPaint()
    }

}

/** Exact fixed height used by the public Slider facade before component geometry tokens existed. */
private const val LEGACY_SLIDER_HEIGHT_PX: Int = 7

/** Paints one square or stair-step rounded Slider rectangle without anti-aliasing. */
private fun paintSliderRoundedRect(
    context: PaintContext,
    left: Int,
    top: Int,
    width: Int,
    height: Int,
    radius: Int,
    color: PixelColor,
) {
    if (width <= 0 || height <= 0) return
    /** Radius constrained so opposing integer corner steps cannot overlap. */
    val safeRadius = radius.coerceIn(0, minOf(width, height) / 2)
    if (safeRadius == 0) {
        context.fillRect(left, top, width, height, color)
        return
    }
    repeat(height) { row ->
        /** Distance from this scan line to the nearest horizontal edge. */
        val edgeDistance = minOf(row, height - 1 - row)
        /** Symmetric inset producing a deterministic pixel stair-step corner. */
        val inset = (safeRadius - edgeDistance - 1).coerceAtLeast(0)
        /** Remaining positive scan width after applying both corner insets. */
        val scanWidth = (width - inset * 2).coerceAtLeast(0)
        if (scanWidth > 0) context.fillRect(left + inset, top + row, scanWidth, 1, color)
    }
}

/** Paints nested Slider outline layers while preserving the same stair-step radius. */
private fun paintSliderRoundedBorder(
    context: PaintContext,
    left: Int,
    top: Int,
    width: Int,
    height: Int,
    radius: Int,
    thickness: Int,
    color: PixelColor,
) {
    if (width <= 0 || height <= 0 || thickness <= 0) return
    /** Outline count bounded before a nested rectangle becomes non-positive. */
    val layers = thickness.coerceIn(0, (minOf(width, height) + 1) / 2)
    repeat(layers) layerLoop@{ layer ->
        /** Current nested outline left coordinate. */
        val layerLeft = left + layer
        /** Current nested outline top coordinate. */
        val layerTop = top + layer
        /** Current nested outline width. */
        val layerWidth = width - layer * 2
        /** Current nested outline height. */
        val layerHeight = height - layer * 2
        if (layerWidth <= 0 || layerHeight <= 0) return@layerLoop
        /** Radius reduced by the same amount as the nested bounds. */
        val layerRadius = (radius - layer).coerceIn(0, minOf(layerWidth, layerHeight) / 2)
        repeat(layerHeight) rowLoop@{ row ->
            /** Distance from this row to the nearest nested horizontal edge. */
            val edgeDistance = minOf(row, layerHeight - 1 - row)
            /** Stair-step inset for this nested outline row. */
            val inset = (layerRadius - edgeDistance - 1).coerceAtLeast(0)
            /** First visible outline pixel on this row. */
            val startX = layerLeft + inset
            /** Last visible outline pixel on this row. */
            val endX = layerLeft + layerWidth - inset - 1
            if (startX > endX) return@rowLoop
            /** Only top and bottom rows are complete horizontal spans. */
            if (row == 0 || row == layerHeight - 1) {
                context.fillRect(startX, layerTop + row, endX - startX + 1, 1, color)
            } else {
                context.setColor(startX, layerTop + row, color)
                if (endX != startX) context.setColor(endX, layerTop + row, color)
            }
        }
    }
}

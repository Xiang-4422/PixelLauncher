package com.purride.pixelui

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.animation.PixelTicker
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.animation.PixelColorTween
import com.purride.pixelui.internal.HitTestResult
import com.purride.pixelui.internal.InteractionDetector
import com.purride.pixelui.internal.AutomaticFocusAction
import com.purride.pixelui.internal.LeafRenderObjectWidget
import com.purride.pixelui.internal.MultiChildRenderObject
import com.purride.pixelui.internal.MultiChildRenderObjectWidget
import com.purride.pixelui.internal.ModalInteractionScopeWidget
import com.purride.pixelui.internal.PaintContext
import com.purride.pixelui.internal.PixelArtifactInternalApi
import com.purride.pixelui.internal.PixelClickTarget
import com.purride.pixelui.internal.PixelRect
import com.purride.pixelui.internal.PixelSemanticsTarget
import com.purride.pixelui.internal.RenderBox
import com.purride.pixelui.internal.RenderConstraints
import com.purride.pixelui.internal.RenderObject
import com.purride.pixelui.internal.RenderSize
import com.purride.pixelui.internal.SafeOverlayAlignment
import com.purride.pixelui.internal.SafeOverlayBodyViewportWidget
import com.purride.pixelui.internal.SafeOverlayViewportWidget
import com.purride.pixelui.internal.activationKeyHandler
import com.purride.pixelui.internal.withControlFocusIndicator
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.Duration

/** 定义 `PixelIconData` 在 `PixelComponents` 中承担的数据与行为边界。
 *
 * Immutable pixel bitmap used by Icon and icon-bearing production controls.
 */
public data class PixelIconData(
    /** 公开 `PixelComponents` 的 `bitmap` 配置或运行值。
 *
 * Source bitmap whose dimensions and alpha are preserved by the basic Icon renderer.
 */
    public val bitmap: PixelBitmap,
)

/** 执行 `PixelComponents` 的 `Icon` 公开行为；具体参数、返回和副作用见下文。
 *
 * Renders one semantic-free bitmap icon; its owning control must provide an accessible label.
 */
public fun Icon(
    /** Immutable pixel bitmap descriptor to paint. */
    icon: PixelIconData,
    /** Stable retained identity for the image render object. */
    key: Any? = null,
): Widget = Image(bitmap = icon.bitmap, key = key)

/**
 * 按布尔状态选择显示 [child] 或 [replacement]。
 *
 * 组件不保留隐藏子树状态，也不做动画；需要过渡效果时使用动画组件包裹它。
 */
public fun Visibility(
    visible: Boolean,
    child: Widget,
    replacement: Widget = SizedBox(width = 0, height = 0),
): Widget = if (visible) child else replacement

/**
 * 组合 leading、文字区和 trailing，并为整行提供统一点击、焦点与结构化语义。
 *
 * @param title 必需的主标题内容。
 * @param subtitle 可选的第二行内容。
 * @param leading 可选的行首内容。
 * @param trailing 可选的行尾内容。
 * @param onTap 点击、键盘或无障碍激活时执行的回调；null 表示不可交互。
 * @param enabled 调用方声明的启用状态。
 * @param semanticLabel 可选整行无障碍名称；null 时解析主题 label token。
 * @param key 行内容、焦点与语义边界共用的稳定 identity。
 * @param semanticRole 整行导出的无障碍角色。
 * @param semanticSelected 是否导出结构化选中状态。
 * @param semanticChecked 可选的结构化勾选状态。
 * @param semanticExpanded 可选的结构化展开状态。
 */
public fun ListTile(
    title: Widget,
    subtitle: Widget? = null,
    leading: Widget? = null,
    trailing: Widget? = null,
    onTap: (() -> Unit)? = null,
    enabled: Boolean = true,
    semanticLabel: String? = null,
    key: Any? = null,
    semanticRole: PixelSemanticRole = PixelSemanticRole.BUTTON,
    semanticSelected: Boolean = false,
    semanticChecked: Boolean? = null,
    semanticExpanded: Boolean? = null,
): Widget = ListTile(
    title = title,
    states = PixelControlStateSet.Normal,
    subtitle = subtitle,
    leading = leading,
    trailing = trailing,
    onTap = onTap,
    enabled = enabled,
    semanticLabel = semanticLabel,
    key = key,
    semanticRole = semanticRole,
    semanticSelected = semanticSelected,
    semanticChecked = semanticChecked,
    semanticExpanded = semanticExpanded,
)

/** ListTile 焦点诊断使用的稳定兜底标签；朗读标签仍由主题 label token 解析。 */
private const val LIST_TILE_DEBUG_LABEL: String = "ListTile"

/**
 * 执行 `PixelComponents` 的 `ListTile` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware ListTile whose surface, spacing, focus layer, and labels resolve from [PixelTheme].
 *
 * Loading keeps the row in focus traversal but blocks pointer, keyboard, and semantic activation.
 * A null [onTap] or false [enabled] derives Disabled and removes the row from traversal.
 */
@Suppress("LongParameterList")
@kotlin.jvm.JvmName("ListTileWithControlStates")
public fun ListTile(
    title: Widget,
    states: PixelControlStateSet,
    subtitle: Widget? = null,
    leading: Widget? = null,
    trailing: Widget? = null,
    onTap: (() -> Unit)? = null,
    enabled: Boolean = true,
    semanticLabel: String? = null,
    key: Any? = null,
    semanticRole: PixelSemanticRole = PixelSemanticRole.BUTTON,
    semanticSelected: Boolean = false,
    semanticChecked: Boolean? = null,
    semanticExpanded: Boolean? = null,
): Widget {
    /** Caller states normalized with controlled selection and actual activation availability. */
    var effectiveStates = states
    if (semanticSelected) effectiveStates += PixelControlState.Selected
    if (!enabled || onTap == null) effectiveStates += PixelControlState.Disabled
    /** Disabled removes traversal while Loading intentionally retains it. */
    val focusable = PixelControlState.Disabled !in effectiveStates
    /** Loading and Disabled both suppress every activation channel. */
    val interactive = focusable && PixelControlState.Loading !in effectiveStates
    /** Shared pointer, keyboard, and semantics activation action. */
    val activate: (() -> Boolean)? = onTap?.takeIf { interactive }?.let { callback ->
        {
            callback()
            true
        }
    }
    return AutomaticFocusAction(
        enabled = focusable,
        debugLabel = semanticLabel ?: LIST_TILE_DEBUG_LABEL,
        onKeyEvent = activate?.let(::activationKeyHandler),
        key = key,
    ) { _, _ ->
        PixelListTileStateWidget(
            title = title,
            subtitle = subtitle,
            leading = leading,
            trailing = trailing,
            states = effectiveStates,
            activate = activate,
            semanticLabel = semanticLabel,
            semanticRole = semanticRole,
            semanticSelected = semanticSelected,
            semanticChecked = semanticChecked,
            semanticExpanded = semanticExpanded,
            key = key,
        )
    }
}

/** Retained ListTile configuration whose pointer interaction states are runtime-owned. */
private data class PixelListTileStateWidget(
    /** Primary row content. */
    val title: Widget,
    /** Optional secondary row content. */
    val subtitle: Widget?,
    /** Optional leading content. */
    val leading: Widget?,
    /** Optional trailing content. */
    val trailing: Widget?,
    /** Persistent normalized caller states. */
    val states: PixelControlStateSet,
    /** Shared activation action, or null while non-interactive. */
    val activate: (() -> Boolean)?,
    /** Optional caller label; null resolves from theme labels. */
    val semanticLabel: String?,
    /** Structured semantics role for the complete row. */
    val semanticRole: PixelSemanticRole,
    /** Controlled structured selection state. */
    val semanticSelected: Boolean,
    /** Optional controlled checked state. */
    val semanticChecked: Boolean?,
    /** Optional controlled expanded state. */
    val semanticExpanded: Boolean?,
    /** Stable retained identity for interaction, focus, and semantics. */
    override val key: Any?,
) : StatefulWidget(key = key) {
    /** Creates the retained hover and press owner. */
    override fun createState(): State<out StatefulWidget> = PixelListTileState()
}

/** ListTile runtime hover and press state merged with persistent semantic states. */
private class PixelListTileState : State<PixelListTileStateWidget>() {
    /** Whether the row currently owns a captured pointer press. */
    private var pressed: Boolean = false

    /** Whether a pointing device currently hovers over the row. */
    private var hovered: Boolean = false

    /** Resolves the current theme surface and all interaction boundaries. */
    override fun build(context: BuildContext): Widget {
        /** Complete inherited theme token graph. */
        val theme = PixelTheme.of(context)
        /** Provider-aware labels independent from ListTile visual token selection. */
        val localization = pixelComponentLocalizationOf(context, theme)
        /** ListTile-specific state and geometry tokens. */
        val tokens = theme.components.listTile
        /** Focus supplied by the public automatic focus boundary. */
        val focusNode = context.getInheritedWidgetOfExactType<FocusNodeScope>()?.node
        if (focusNode != null) context.watch(focusNode)
        /** Combined persistent, focus, hover, and press states. */
        var resolvedStates = widget.states
        if (focusNode?.isFocused == true) resolvedStates += PixelControlState.Focused
        if (pressed) resolvedStates += PixelControlState.Pressed
        if (hovered) resolvedStates += PixelControlState.Hovered
        if (
            PixelControlState.Disabled in resolvedStates ||
            PixelControlState.Loading in resolvedStates
        ) {
            pressed = false
            hovered = false
            resolvedStates -= PixelControlState.Pressed
            resolvedStates -= PixelControlState.Hovered
        }
        /** Title and optional subtitle grouped into the elastic middle column. */
        val subtitle = widget.subtitle
        val texts = if (subtitle == null) {
            widget.title
        } else {
            Column(
                children = listOf(widget.title, subtitle),
                spacing = theme.spacing.extraSmall,
            )
        }
        /** Leading, elastic text, and trailing content in visual order. */
        val rowChildren = buildList {
            widget.leading?.let(::add)
            add(Expanded(child = texts))
            widget.trailing?.let(::add)
        }
        /** Theme-resolved row surface before transient pointer behavior. */
        val surface = PixelSurface(
            child = Row(
                children = rowChildren,
                spacing = theme.spacing.small,
                crossAxisAlignment = CrossAxisAlignment.CENTER,
            ),
            padding = tokens.resolvePadding(theme.spacing),
            decoration = PixelSurfaceDecoration(
                fillColor = tokens.resolveContainerColor(resolvedStates, theme.colors),
                borderColor = tokens.resolveBorderColor(resolvedStates, theme.colors),
                borderWidth = tokens.resolveBorderWidth(theme.borders),
                cornerRadius = tokens.resolveCornerRadius(theme.radii),
                shadowColor = theme.colors.shadow,
                shadowOffset = tokens.resolveElevation(theme.elevations),
            ),
            key = widget.key?.let { "$it-surface" },
        )
        /** Pointer wrapper omitted while Loading or Disabled. */
        val interactiveSurface = widget.activate?.let { activate ->
            InteractionDetector(
                child = surface,
                onTap = { activate.invoke() },
                onPressedChanged = ::updatePressed,
                onHoveredChanged = ::updateHovered,
                key = widget.key,
            )
        } ?: surface
        return FocusableControl(
            label = localization.resolveLabel(
                explicitText = widget.semanticLabel,
                selector = PixelLabelTokens::listTile,
            ),
            role = widget.semanticRole,
            enabled = PixelControlState.Disabled !in resolvedStates,
            semanticsEnabled = widget.activate != null,
            automaticallyFocusable = false,
            selected = widget.semanticSelected,
            checked = widget.semanticChecked,
            expanded = widget.semanticExpanded,
            value = localization.resolveLabel(
                explicitText = null,
                selector = PixelLabelTokens::loading,
            ).takeIf { PixelControlState.Loading in resolvedStates },
            error = localization.resolveLabel(
                explicitText = null,
                selector = PixelLabelTokens::error,
            ).takeIf { PixelControlState.Error in resolvedStates },
            focusIndicator = tokens.focusIndicator ?: PixelFocusIndicatorTokens.Default,
            actions = PixelSemanticsActions(onClick = widget.activate),
            child = interactiveSurface,
            key = widget.key,
        )
    }

    /** Updates captured press state exactly once per transition. */
    private fun updatePressed(nextPressed: Boolean) {
        if (pressed == nextPressed) return
        setState { pressed = nextPressed }
    }

    /** Updates hover state exactly once per boundary transition. */
    private fun updateHovered(nextHovered: Boolean) {
        if (hovered == nextHovered) return
        setState { hovered = nextHovered }
    }
}

/**
 * 受控单选列表。
 *
 * [selectedIndex] 只决定当前选中标记；选中状态和列表数据都由调用方持有。
 * 组件不内置滚动，长列表请放进已有滚动容器。
 */
public fun <T> SelectionList(
    items: List<T>,
    selectedIndex: Int,
    onSelected: (index: Int, item: T) -> Unit,
    itemLabel: (T) -> String,
    enabled: Boolean = true,
    key: Any? = null,
): Widget {
    return Column(
        children = items.mapIndexed { index, item ->
            val label = itemLabel(item)
            ListTile(
                leading = Text(if (index == selectedIndex) ">" else " "),
                title = Text(label),
                onTap = if (enabled) {
                    { onSelected(index, item) }
                } else {
                    null
                },
                enabled = enabled,
                semanticLabel = label,
                semanticSelected = index == selectedIndex,
                key = key?.let { "$it-$index" },
            )
        },
        spacing = 1,
        key = key,
    )
}

/**
 * 字符串选项版 [SelectionList]。
 */
public fun OptionList(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    enabled: Boolean = true,
    key: Any? = null,
): Widget {
    return SelectionList(
        items = options,
        selectedIndex = selectedIndex,
        onSelected = { index, _ -> onSelected(index) },
        itemLabel = { it },
        enabled = enabled,
        key = key,
    )
}

/**
 * [SectionList] 的一个分组。
 *
 * [children] 是该分组的实际内容；[header] 和 [footer] 只作为普通 widget 渲染，
 * 引擎不为它们附加滚动吸顶、折叠或数据加载语义。
 */
public data class SectionListSection(
    val children: List<Widget>,
    val header: Widget? = null,
    val footer: Widget? = null,
)

/**
 * 分组列表布局容器。
 *
 * 组件只负责把多个 [SectionListSection] 排成纵向分组，不内置滚动、吸顶或懒加载。
 * 长列表应放入 `ListView`、`SingleChildScrollView` 或业务自有滚动容器。
 */
public fun SectionList(
    sections: List<SectionListSection>,
    itemSpacing: Int = 1,
    sectionSpacing: Int = 2,
    key: Any? = null,
): Widget {
    val sectionWidgets = sections.mapIndexed { index, section ->
        val children = buildList {
            if (section.header != null) add(section.header)
            addAll(section.children)
            if (section.footer != null) add(section.footer)
        }
        Column(
            children = children,
            spacing = itemSpacing,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            key = key?.let { "$it-$index" },
        )
    }
    return Column(
        children = sectionWidgets,
        spacing = sectionSpacing,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
        key = key,
    )
}

/** Checkbox 焦点诊断使用的稳定兜底标签；朗读标签仍由主题 label token 解析。 */
private const val CHECKBOX_DEBUG_LABEL: String = "Checkbox"

/**
 * 受控像素复选框，统一 pointer、keyboard 与无障碍 toggle 动作。
 *
 * @param checked 调用方持有的当前勾选状态。
 * @param onChanged 请求新勾选状态的回调；null 表示只读。
 * @param enabled 调用方声明的启用状态。
 * @param activeColor 可选勾选态边框与标记颜色；null 时解析组件状态角色。
 * @param inactiveColor 可选未勾选态边框颜色；null 时解析组件状态角色。
 * @param semanticLabel 可选无障碍名称；null 时解析主题 label token。
 * @param key 视觉、焦点与语义边界共用的稳定 identity。
 */
public fun Checkbox(
    checked: Boolean,
    onChanged: ((Boolean) -> Unit)?,
    enabled: Boolean = true,
    activeColor: PixelColor? = null,
    inactiveColor: PixelColor? = null,
    semanticLabel: String? = null,
    key: Any? = null,
): Widget = Checkbox(
    checked = checked,
    onChanged = onChanged,
    states = PixelControlStateSet.Normal,
    enabled = enabled,
    activeColor = activeColor,
    inactiveColor = inactiveColor,
    semanticLabel = semanticLabel,
    key = key,
)

/**
 * 执行 `PixelComponents` 的 `Checkbox` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware Checkbox that resolves every visual channel from the active theme.
 *
 * [PixelControlState.Loading] blocks new activation while retaining focus. Disabled is derived
 * when [enabled] is false or [onChanged] is null. Checked remains an independent selected state,
 * so checked+disabled and checked+error preserve both geometry and status color.
 */
@kotlin.jvm.JvmName("CheckboxWithControlStates")
public fun Checkbox(
    checked: Boolean,
    onChanged: ((Boolean) -> Unit)?,
    states: PixelControlStateSet,
    enabled: Boolean = true,
    activeColor: PixelColor? = null,
    inactiveColor: PixelColor? = null,
    semanticLabel: String? = null,
    key: Any? = null,
): Widget {
    /** Persistent caller states normalized with controlled selection and actual availability. */
    var effectiveStates = states
    if (checked) effectiveStates += PixelControlState.Selected
    if (!enabled || onChanged == null) effectiveStates += PixelControlState.Disabled
    /** Disabled removes traversal; Loading deliberately preserves an already focused control. */
    val focusable = PixelControlState.Disabled !in effectiveStates
    /** Activation is unavailable while either capability state is active. */
    val interactive = focusable && PixelControlState.Loading !in effectiveStates
    /** Pointer、keyboard 与 semantics 共用的状态翻转动作。 */
    val toggle: (() -> Boolean)? = onChanged?.takeIf { interactive }?.let { callback ->
        {
            callback(!checked)
            true
        }
    }
    return AutomaticFocusAction(
        enabled = focusable,
        debugLabel = semanticLabel ?: CHECKBOX_DEBUG_LABEL,
        onKeyEvent = toggle?.let(::activationKeyHandler),
        key = key,
    ) { _, _ ->
        PixelCheckboxStateWidget(
            checked = checked,
            states = effectiveStates,
            toggle = toggle,
            activeColor = activeColor,
            inactiveColor = inactiveColor,
            semanticLabel = semanticLabel,
            key = key,
        )
    }
}

/** Retained Checkbox configuration whose transient pointer states are runtime-owned. */
private data class PixelCheckboxStateWidget(
    /** Controlled checked state represented by selected geometry. */
    val checked: Boolean,
    /** Persistent normalized states supplied by the public facade. */
    val states: PixelControlStateSet,
    /** Shared pointer, keyboard, and semantics toggle action. */
    val toggle: (() -> Boolean)?,
    /** Optional explicit checked-state color override. */
    val activeColor: PixelColor?,
    /** Optional explicit unchecked-state color override. */
    val inactiveColor: PixelColor?,
    /** Optional caller label; null resolves from theme labels. */
    val semanticLabel: String?,
    /** Stable retained identity for state, focus, and semantics. */
    override val key: Any?,
) : StatefulWidget(key = key) {
    /** Creates the retained pointer interaction owner. */
    override fun createState(): State<out StatefulWidget> = PixelCheckboxState()
}

/** Checkbox hover and press state merged with persistent selected/error/capability states. */
private class PixelCheckboxState : State<PixelCheckboxStateWidget>() {
    /** Whether this Checkbox currently owns a captured pointer press. */
    private var pressed: Boolean = false

    /** Whether a mouse or stylus currently hovers over the Checkbox. */
    private var hovered: Boolean = false

    /** Resolves theme roles, independent focus geometry, pointer callbacks, and semantics. */
    override fun build(context: BuildContext): Widget {
        /** Complete token graph inherited by this exact runtime subtree. */
        val theme = PixelTheme.of(context)
        /** Provider-aware semantic labels kept separate from Checkbox visual roles. */
        val localization = pixelComponentLocalizationOf(context, theme)
        /** Component-specific visual and geometry tokens. */
        val tokens = theme.components.checkbox
        /** Focus node provided by the public AutomaticFocusAction boundary. */
        val focusNode = context.getInheritedWidgetOfExactType<FocusNodeScope>()?.node
        if (focusNode != null) context.watch(focusNode)
        /** Runtime states after actual focus and pointer ownership are merged. */
        var resolvedStates = widget.states
        if (focusNode?.isFocused == true) resolvedStates += PixelControlState.Focused
        if (pressed) resolvedStates += PixelControlState.Pressed
        if (hovered) resolvedStates += PixelControlState.Hovered
        /** Capability state forces stale interaction ownership to its terminal false state. */
        if (
            PixelControlState.Disabled in resolvedStates ||
            PixelControlState.Loading in resolvedStates
        ) {
            pressed = false
            hovered = false
            resolvedStates -= PixelControlState.Pressed
            resolvedStates -= PixelControlState.Hovered
        }
        /** 非普通状态是否需要覆盖调用方的勾选/未勾选颜色。 Whether a non-normal state must override caller checked/unchecked colors. */
        val usesStateRole = resolvedStates.highestPriority() !in setOf(
            PixelControlState.Normal,
            PixelControlState.Selected,
        )
        /** 调用方基础色只在 Normal 或 Selected 下保留。 Optional caller base color preserved only for Normal or Selected. */
        val explicitBaseColor = if (widget.checked) widget.activeColor else widget.inactiveColor
        /** Resolved surface fill for the current combined state. */
        val fillColor = tokens.resolveContainerColor(resolvedStates, theme.colors)
        /** 解析出的边框色；基础状态下调用方显式值优先。 Resolved outline with explicit caller precedence for base states. */
        val borderColor = if (usesStateRole) {
            tokens.resolveBorderColor(resolvedStates, theme.colors)
        } else {
            explicitBaseColor ?: tokens.resolveBorderColor(resolvedStates, theme.colors)
        }
        /** 与描边采用同一优先级顺序解析出的字形颜色。 */
        val contentColor = if (usesStateRole) {
            tokens.resolveContentColor(resolvedStates, theme.colors)
        } else {
            explicitBaseColor ?: tokens.resolveContentColor(resolvedStates, theme.colors)
        } ?: theme.colors.onSurface
        /** Checked geometry remains visible even when another persistent state has priority. */
        val mark = if (widget.checked) "X" else " "
        /** Token-sized Checkbox surface before pointer behavior is applied. */
        val box = PixelSurface(
            width = tokens.resolveMinimumWidth(theme.sizes),
            height = tokens.resolveMinimumHeight(theme.sizes),
            decoration = PixelSurfaceDecoration(
                fillColor = fillColor,
                borderColor = borderColor,
                borderWidth = tokens.resolveBorderWidth(theme.borders),
                cornerRadius = tokens.resolveCornerRadius(theme.radii),
                shadowColor = theme.colors.shadow,
                shadowOffset = tokens.resolveElevation(theme.elevations),
            ),
            child = Center(child = Text(mark, style = TextStyle(color = contentColor))),
        )
        /** Pointer target exists only while the normalized activation action is available. */
        val interactiveBox = widget.toggle?.let { toggle ->
            InteractionDetector(
                child = box,
                onTap = { toggle.invoke() },
                onPressedChanged = ::updatePressed,
                onHoveredChanged = ::updateHovered,
                key = widget.key,
            )
        } ?: box
        /** Localized loading value announced without replacing the stable control label. */
        val semanticValue = localization.resolveLabel(
            explicitText = null,
            selector = PixelLabelTokens::loading,
        ).takeIf {
            PixelControlState.Loading in resolvedStates
        }
        /** Localized validation error announced while Error is active. */
        val semanticError = localization.resolveLabel(
            explicitText = null,
            selector = PixelLabelTokens::error,
        ).takeIf {
            PixelControlState.Error in resolvedStates
        }
        return FocusableControl(
            label = localization.resolveLabel(
                explicitText = widget.semanticLabel,
                selector = PixelLabelTokens::checkbox,
            ),
            role = PixelSemanticRole.CHECKBOX,
            enabled = PixelControlState.Disabled !in resolvedStates,
            semanticsEnabled = widget.toggle != null,
            automaticallyFocusable = false,
            checked = widget.checked,
            value = semanticValue,
            error = semanticError,
            focusIndicator = tokens.focusIndicator ?: PixelFocusIndicatorTokens.Default,
            actions = PixelSemanticsActions(onClick = widget.toggle),
            child = interactiveBox,
            key = widget.key,
        )
    }

    /** Updates captured press state exactly once per pointer ownership transition. */
    private fun updatePressed(nextPressed: Boolean) {
        if (pressed == nextPressed) return
        setState { pressed = nextPressed }
    }

    /** Updates hover state exactly once per mouse or stylus boundary transition. */
    private fun updateHovered(nextHovered: Boolean) {
        if (hovered == nextHovered) return
        setState { hovered = nextHovered }
    }
}

/** Switch 焦点诊断使用的稳定兜底标签；朗读标签仍由主题 label token 解析。 */
private const val SWITCH_DEBUG_LABEL: String = "Switch"

/**
 * 受控像素开关，以 Motion token 驱动 thumb、颜色与交互反馈。
 *
 * 简洁入口委托到同一状态化实现；省略的颜色与标签统一由 token 解析。
 *
 * @param checked 调用方持有的当前开关状态。
 * @param onChanged 请求新开关状态的回调；null 表示只读。
 * @param enabled 调用方声明的启用状态。
 * @param activeColor 可选开启端点颜色；null 时解析组件状态角色。
 * @param inactiveColor 可选关闭端点颜色；null 时解析组件状态角色。
 * @param semanticLabel 可选无障碍名称；null 时解析主题 label token。
 * @param key 视觉状态、焦点与语义边界共用的稳定 identity。
 */
public fun Switch(
    checked: Boolean,
    onChanged: ((Boolean) -> Unit)?,
    enabled: Boolean = true,
    activeColor: PixelColor? = null,
    inactiveColor: PixelColor? = null,
    semanticLabel: String? = null,
    key: Any? = null,
): Widget = buildSwitch(
    checked = checked,
    onChanged = onChanged,
    states = PixelControlStateSet.Normal,
    enabled = enabled,
    activeColor = activeColor,
    inactiveColor = inactiveColor,
    semanticLabel = semanticLabel,
    key = key,
)

/**
 * 执行 `PixelComponents` 的 `Switch` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware Switch that resolves state colors, geometry, labels, and focus from theme tokens.
 *
 * Checked is represented as Selected without replacing Error or capability states. Loading blocks
 * activation but retains focus; Disabled removes the complete switch from traversal.
 */
@kotlin.jvm.JvmName("SwitchWithControlStates")
public fun Switch(
    checked: Boolean,
    onChanged: ((Boolean) -> Unit)?,
    states: PixelControlStateSet,
    enabled: Boolean = true,
    activeColor: PixelColor? = null,
    inactiveColor: PixelColor? = null,
    semanticLabel: String? = null,
    key: Any? = null,
): Widget = buildSwitch(
    checked = checked,
    onChanged = onChanged,
    states = states,
    enabled = enabled,
    activeColor = activeColor,
    inactiveColor = inactiveColor,
    semanticLabel = semanticLabel,
    key = key,
)

/** 归一化 Switch 的能力状态，并安装唯一的 retained 运动/焦点所有者。 */
@Suppress("LongParameterList")
private fun buildSwitch(
    checked: Boolean,
    onChanged: ((Boolean) -> Unit)?,
    states: PixelControlStateSet,
    enabled: Boolean,
    activeColor: PixelColor?,
    inactiveColor: PixelColor?,
    semanticLabel: String?,
    key: Any?,
): Widget {
    /** Persistent states normalized with checked selection and callback availability. */
    var effectiveStates = states
    if (checked) effectiveStates += PixelControlState.Selected
    if (!enabled || onChanged == null) effectiveStates += PixelControlState.Disabled
    /** Disabled removes traversal while Loading intentionally retains focus ownership. */
    val focusable = PixelControlState.Disabled !in effectiveStates
    /** Activation is unavailable for both capability states. */
    val interactive = focusable && PixelControlState.Loading !in effectiveStates
    /** Pointer、keyboard 与 semantics 共用的状态翻转动作。 */
    val toggle: (() -> Boolean)? = onChanged?.takeIf { interactive }?.let { callback ->
        {
            callback(!checked)
            true
        }
    }
    return AutomaticFocusAction(
        enabled = focusable,
        debugLabel = semanticLabel ?: SWITCH_DEBUG_LABEL,
        onKeyEvent = toggle?.let(::activationKeyHandler),
        key = key,
    ) { _, _ ->
        PixelSwitchMotionWidget(
            checked = checked,
            states = effectiveStates,
            semanticAction = toggle,
            activeColor = activeColor,
            inactiveColor = inactiveColor,
            semanticLabel = semanticLabel,
            key = key,
        )
    }
}

/** Switch 的受控逻辑参数与 retained 运动状态边界。 */
private data class PixelSwitchMotionWidget(
    /** 调用方立即拥有的逻辑选中状态。 */
    val checked: Boolean,
    /** Persistent normalized states supplied by the public facade. */
    val states: PixelControlStateSet,
    /** Pointer、semantics 与 keyboard 共享的 toggle action。 */
    val semanticAction: (() -> Boolean)?,
    /** Optional explicit selected endpoint color. */
    val activeColor: PixelColor?,
    /** Optional explicit unselected endpoint color. */
    val inactiveColor: PixelColor?,
    /** Optional caller label; null resolves from theme labels. */
    val semanticLabel: String?,
    /** retained identity。 */
    override val key: Any?,
) : StatefulWidget(key = key) {
    /** 创建 thumb selection 与交互反馈的 retained 状态。 */
    override fun createState(): State<out StatefulWidget> = PixelSwitchMotionState()
}

/** Switch 的 thumb 位置、颜色 selection 与 pressed/hover/focus 反馈状态。 */
private class PixelSwitchMotionState : State<PixelSwitchMotionWidget>() {
    /** thumb 与选中颜色的 `0f..1f` 视觉进度。 */
    private lateinit var selectionMotion: PixelControlMotionValue

    /** Hover/press feedback intensity layered above the selection transition. */
    private val feedbackMotion: PixelControlMotionValue = PixelControlMotionValue(0f)

    /** 指针当前是否保持按下。 */
    private var pressed: Boolean = false

    /** 鼠标或触控笔当前是否悬停。 */
    private var hovered: Boolean = false

    /** 用首次受控值初始化 selection，避免 mount 时从错误端点补动画。 */
    override fun initState() {
        selectionMotion = PixelControlMotionValue(if (widget.checked) 1f else 0f)
    }

    /** 释放 selection 与 feedback 驱动器拥有的 ticker。 */
    override fun dispose() {
        selectionMotion.dispose()
        feedbackMotion.dispose()
    }

    /**
     * 逻辑 checked/semantics 立即取新 widget，视觉 thumb 和颜色则按 selection token 连续追赶。
     */
    override fun build(context: BuildContext): Widget {
        /** Complete theme token graph inherited by this retained switch. */
        val theme = PixelTheme.of(context)
        /** Provider-aware labels that never participate in Switch visual branch selection. */
        val localization = pixelComponentLocalizationOf(context, theme)
        /** Switch-specific state and geometry tokens. */
        val tokens = theme.components.switch
        /** Whether pointer and semantic activation are currently available. */
        val interactive = widget.semanticAction != null
        /** selection 与 feedback 动画规格来源。 */
        val motionTheme = PixelMotionTheme.of(context)
        /** 可选统一 ticker 与 reduced-motion 设置来源。 */
        val motionScope = PixelMotionScope.maybeOf(context)
        configureControlMotion(selectionMotion, motionScope, motionTheme.selection)
        configureControlMotion(feedbackMotion, motionScope, motionTheme.feedback)

        /** 复用外层自动焦点边界提供的节点。 */
        val focusNode = context.getInheritedWidgetOfExactType<FocusNodeScope>()?.node
        if (focusNode != null) context.watch(focusNode)
        /** Runtime states after independent focus, hover, and press are merged. */
        var resolvedStates = widget.states
        if (focusNode?.isFocused == true) resolvedStates += PixelControlState.Focused
        if (pressed) resolvedStates += PixelControlState.Pressed
        if (hovered) resolvedStates += PixelControlState.Hovered
        if (
            PixelControlState.Disabled in resolvedStates ||
            PixelControlState.Loading in resolvedStates
        ) {
            pressed = false
            hovered = false
            resolvedStates -= PixelControlState.Pressed
            resolvedStates -= PixelControlState.Hovered
        }
        /** Whether a caller-forced interaction state must resolve synchronously for snapshots. */
        val hasPersistentFeedbackState = PixelControlState.Hovered in widget.states ||
            PixelControlState.Pressed in widget.states
        if (PixelControlState.Disabled !in resolvedStates) {
            selectionMotion.animateTo(if (widget.checked) 1f else 0f)
            when {
                hasPersistentFeedbackState -> feedbackMotion.snapTo(1f)
                else -> feedbackMotion.animateTo(
                    controlFeedbackTarget(pressed = pressed, hovered = hovered, focused = false),
                )
            }
        } else {
            pressed = false
            hovered = false
            selectionMotion.snapTo(if (widget.checked) 1f else 0f)
            feedbackMotion.snapTo(0f)
        }
        selectionMotion.watch(context)
        feedbackMotion.watch(context)

        /** 当前 thumb 位置与选中色插值共用的视觉进度。 */
        val selection = selectionMotion.value
        /** Base endpoint states exclude transient and priority states from both selection endpoints. */
        val baseEndpointStates = widget.states -
            PixelControlState.Selected -
            PixelControlState.Disabled -
            PixelControlState.Loading -
            PixelControlState.Error -
            PixelControlState.Pressed -
            PixelControlState.Hovered -
            PixelControlState.Focused
        /** 未选中端点边框色；调用方覆写具有最高优先级。 Unselected outline endpoint with the caller override at highest precedence. */
        val inactiveBorderColor = widget.inactiveColor
            ?: tokens.resolveBorderColor(baseEndpointStates, theme.colors)
            ?: theme.colors.inactive
        /** 选中端点边框色；调用方覆写具有最高优先级。 Selected outline endpoint with the caller override at highest precedence. */
        val activeBorderColor = widget.activeColor
            ?: tokens.resolveBorderColor(baseEndpointStates + PixelControlState.Selected, theme.colors)
            ?: theme.colors.primary
        /** Selection-motion outline resolved only from the border token field. */
        val selectedBorderColor = PixelColorTween(
            inactiveBorderColor,
            activeBorderColor,
        ).lerp(selection)
        /** Unselected thumb endpoint resolved independently through the content token field. */
        val inactiveContentColor = widget.inactiveColor
            ?: tokens.resolveContentColor(baseEndpointStates, theme.colors)
            ?: theme.colors.inactive
        /** Selected thumb endpoint resolved independently through the content token field. */
        val activeContentColor = widget.activeColor
            ?: tokens.resolveContentColor(baseEndpointStates + PixelControlState.Selected, theme.colors)
            ?: theme.colors.primary
        /** Selection-motion thumb foreground isolated from track and outline fields. */
        val selectedContentColor = PixelColorTween(
            inactiveContentColor,
            activeContentColor,
        ).lerp(selection)
        /** State resolution excludes Focused because focus is painted as an independent layer. */
        val colorStates = resolvedStates - PixelControlState.Focused
        /** Current state target resolved only from the outline token field. */
        val stateBorderColor = tokens.resolveBorderColor(colorStates, theme.colors)
            ?: selectedBorderColor
        /** Hover and press transition from the controlled outline endpoint to its state role. */
        val visualBorderColor = when (colorStates.highestPriority()) {
            PixelControlState.Normal,
            PixelControlState.Selected,
            -> selectedBorderColor
            PixelControlState.Hovered,
            PixelControlState.Pressed,
            -> PixelColorTween(selectedBorderColor, stateBorderColor).lerp(feedbackMotion.value)
            else -> stateBorderColor
        }
        /** Current thumb target resolved only from the content token field. */
        val stateContentColor = tokens.resolveContentColor(colorStates, theme.colors)
            ?: selectedContentColor
        /** Thumb feedback follows its own field without leaking outline or container changes. */
        val thumbColor = when (colorStates.highestPriority()) {
            PixelControlState.Normal,
            PixelControlState.Selected,
            -> selectedContentColor
            PixelControlState.Hovered,
            PixelControlState.Pressed,
            -> PixelColorTween(selectedContentColor, stateContentColor).lerp(feedbackMotion.value)
            else -> stateContentColor
        }
        /** Track fill consumes only the component container channel. */
        val trackFillColor = tokens.resolveContainerColor(colorStates, theme.colors)
        /** Token-sized track width preserving a usable one-pixel interior. */
        val trackWidth = tokens.resolveMinimumWidth(theme.sizes).coerceAtLeast(3)
        /** Token-sized track height preserving a usable one-pixel interior. */
        val trackHeight = tokens.resolveMinimumHeight(theme.sizes).coerceAtLeast(3)
        /** Border-derived inset used by both geometry endpoints. */
        val thumbInset = tokens.resolveBorderWidth(theme.borders)
            .coerceIn(1, (trackHeight - 1) / 2)
        /** Square thumb extent constrained to the token-sized track. */
        val thumbSize = (trackHeight - thumbInset * 2).coerceAtLeast(1)
        /** Final selected endpoint inside the token-sized track. */
        val thumbEnd = (trackWidth - thumbInset - thumbSize).coerceAtLeast(thumbInset)
        /** Total token-sized thumb travel. */
        val thumbTravel = thumbEnd - thumbInset
        /** 将 selection 进度量化并限制到轨道内的 thumb 左坐标。 */
        val thumbLeft = (thumbInset + thumbTravel * selection)
            .roundToInt()
            .coerceIn(thumbInset, thumbEnd)
        /** 固定尺寸且不改变布局的 thumb 表面。 */
        val thumb = PixelSurface(
            width = thumbSize,
            height = thumbSize,
            decoration = PixelSurfaceDecoration(
                fillColor = thumbColor,
                borderWidth = 0,
                cornerRadius = tokens.resolveCornerRadius(theme.radii),
            ),
            key = widget.key?.let { "$it-thumb" },
        )
        /** 包含动画 thumb 的固定尺寸开关轨道。 */
        val thumbStack = Stack(
            children = listOf(
                Positioned(
                    left = thumbLeft,
                    top = thumbInset,
                    child = thumb,
                    key = widget.key?.let { "$it-thumb-position" },
                ),
            ),
        )
        /** 承载同一移动 thumb 的 token 化轨道表面。 Token-aware track surface hosting the same moving thumb. */
        val track = PixelSurface(
            width = trackWidth,
            height = trackHeight,
            decoration = PixelSurfaceDecoration(
                fillColor = trackFillColor,
                borderColor = visualBorderColor,
                borderWidth = tokens.resolveBorderWidth(theme.borders),
                cornerRadius = tokens.resolveCornerRadius(theme.radii),
                shadowColor = theme.colors.shadow,
                shadowOffset = tokens.resolveElevation(theme.elevations),
            ),
            child = thumbStack,
            key = widget.key?.let { "$it-track" },
        )
        /** 仅在实际可交互时导出 pointer pressed、hover 与点击回调。 */
        val interactiveTrack = if (interactive) {
            InteractionDetector(
                child = track,
                onTap = { widget.semanticAction?.invoke() },
                onPressedChanged = ::updatePressed,
                onHoveredChanged = ::updateHovered,
                key = widget.key,
            )
        } else {
            track
        }
        return FocusableControl(
            label = localization.resolveLabel(
                explicitText = widget.semanticLabel,
                selector = PixelLabelTokens::switch,
            ),
            role = PixelSemanticRole.SWITCH,
            enabled = PixelControlState.Disabled !in resolvedStates,
            semanticsEnabled = widget.semanticAction != null,
            automaticallyFocusable = false,
            checked = widget.checked,
            value = localization.resolveLabel(
                explicitText = null,
                selector = PixelLabelTokens::loading,
            ).takeIf { PixelControlState.Loading in resolvedStates },
            error = localization.resolveLabel(
                explicitText = null,
                selector = PixelLabelTokens::error,
            ).takeIf { PixelControlState.Error in resolvedStates },
            focusIndicator = tokens.focusIndicator ?: PixelFocusIndicatorTokens.Default,
            actions = PixelSemanticsActions(onClick = widget.semanticAction),
            child = interactiveTrack,
            key = widget.key,
        )
    }

    /** 更新 pressed 状态；router 会在 up、cancel 和移出时归零。 */
    private fun updatePressed(nextPressed: Boolean) {
        if (pressed == nextPressed) return
        setState { pressed = nextPressed }
    }

    /** 更新 hover 状态。 */
    private fun updateHovered(nextHovered: Boolean) {
        if (hovered == nextHovered) return
        setState { hovered = nextHovered }
    }
}

/**
 * 使用当前 theme token 和可选 Motion scope 配置一个标准控件驱动器。
 *
 * @param motion 待配置的 retained 动画值。
 * @param scope 可选统一时钟与运动偏好来源。
 * @param spec 当前状态通道使用的主题运动规格。
 */
private fun configureControlMotion(
    motion: PixelControlMotionValue,
    scope: PixelMotionScope?,
    spec: PixelMotionSpec,
) {
    /** 已应用 scope 设置的运行时规格；无 scope 时走即时更新。 */
    val resolved = scope?.let { availableScope -> spec.resolve(availableScope.settings) }
    motion.configure(
        nextVsync = scope?.vsync,
        nextDuration = resolved?.duration ?: Duration.ZERO,
        nextDelay = resolved?.delay ?: Duration.ZERO,
        nextCurve = resolved?.curve ?: spec.curve,
        nextImmediate = resolved?.let { motion ->
            motion.isImmediate || motion.transition == PixelMotionTransitionPreset.None
        } ?: true,
    )
}

/**
 * 把可并存的 Switch/Tab 交互状态压缩为单一反馈目标。
 *
 * @param pressed 指针当前是否按下。
 * @param hovered 指针当前是否悬停。
 * @param focused 控件当前是否获得键盘焦点。
 */
private fun controlFeedbackTarget(
    pressed: Boolean,
    hovered: Boolean,
    focused: Boolean,
): Float = when {
    pressed -> 1f
    focused -> 1f
    hovered -> 0.5f
    else -> 0f
}

/**
 * 居中的像素对话框内容。
 *
 * 该函数负责布局 title/content/actions，将表面限制在稳定系统栏与 IME 共同决定的安全
 * 视口内，并在 [modal] 为 true 时建立焦点与交互边界；遮罩、呈现生命周期仍由调用方维护。需要 overlay 行为时，通过
 * [PixelOverlayController.showDialog] 显示并持有返回的 handle。
 * [semanticLabel] 命名 Android Dialog virtual node；提供 [onDismissRequest] 时同时导出
 * accessibility dismiss action。
 *
 * @param title 可选的标题 widget。
 * @param content 必需的对话框正文。
 * @param actions 排列在正文下方并右对齐的操作 widget。
 * @param fillColor 可选对话框表面填充色；null 时解析组件容器角色。
 * @param borderColor 可选对话框边框色；null 时解析组件边框角色。
 * @param key 表面、交互、焦点与语义边界共用的稳定 identity。
 * @param semanticLabel 可选无障碍名称；null 时解析主题 label token。
 * @param onDismissRequest back 或无障碍 dismiss 请求的受控回调。
 * @param modal 是否隔离对话框外的焦点、指针、文本输入与无障碍交互。
 */
public fun Dialog(
    title: Widget? = null,
    content: Widget,
    actions: List<Widget> = emptyList(),
    fillColor: PixelColor? = null,
    borderColor: PixelColor? = null,
    key: Any? = null,
    semanticLabel: String? = null,
    onDismissRequest: (() -> Unit)? = null,
    modal: Boolean = true,
): Widget = Dialog(
    content = content,
    states = PixelControlStateSet.Normal,
    title = title,
    actions = actions,
    fillColor = fillColor,
    borderColor = borderColor,
    key = key,
    semanticLabel = semanticLabel,
    onDismissRequest = onDismissRequest,
    modal = modal,
)

/**
 * 执行 `PixelComponents` 的 `Dialog` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware Dialog with token-resolved surface, spacing, label, and capability semantics.
 */
@kotlin.jvm.JvmName("DialogWithControlStates")
public fun Dialog(
    content: Widget,
    states: PixelControlStateSet,
    title: Widget? = null,
    actions: List<Widget> = emptyList(),
    fillColor: PixelColor? = null,
    borderColor: PixelColor? = null,
    key: Any? = null,
    semanticLabel: String? = null,
    onDismissRequest: (() -> Unit)? = null,
    modal: Boolean = true,
): Widget = PixelThemeResolvedWidget(
    key = PixelThemeResolverKey(ownerKey = key, component = "Dialog", mode = modal),
) { context, theme ->
    /** Provider-aware Dialog and status labels independent from modal visual tokens. */
    val localization = pixelComponentLocalizationOf(context, theme)
    /** Dialog-specific passive overlay tokens. */
    val tokens = theme.components.dialog
    /** Dismiss remains available unless a capability state blocks it. */
    val effectiveDismiss = onDismissRequest.takeIf {
        PixelControlState.Disabled !in states && PixelControlState.Loading !in states
    }
    safeOverlaySurface(
        title = title,
        content = content,
        actions = actions,
        decoration = PixelSurfaceDecoration(
            fillColor = fillColor ?: tokens.resolveContainerColor(states, theme.colors),
            borderColor = borderColor ?: tokens.resolveBorderColor(states, theme.colors),
            borderWidth = tokens.resolveBorderWidth(theme.borders),
            cornerRadius = tokens.resolveCornerRadius(theme.radii),
            shadowColor = theme.colors.shadow,
            shadowOffset = tokens.resolveElevation(theme.elevations),
        ),
        surfacePadding = tokens.resolvePadding(theme.spacing),
        contentSpacing = theme.spacing.small,
        actionSpacing = theme.spacing.small,
        key = key,
        semanticLabel = localization.resolveLabel(
            explicitText = semanticLabel,
            selector = PixelLabelTokens::dialog,
        ),
        semanticValue = localization.resolveLabel(
            explicitText = null,
            selector = PixelLabelTokens::loading,
        ).takeIf { PixelControlState.Loading in states },
        semanticError = localization.resolveLabel(
            explicitText = null,
            selector = PixelLabelTokens::error,
        ).takeIf { PixelControlState.Error in states },
        onDismissRequest = effectiveDismiss,
        modal = modal,
        alignment = SafeOverlayAlignment.Center,
        fillSafeWidth = false,
    )
}

/**
 * 贴近安全视口底部的像素 Bottom Sheet。
 *
 * Sheet 会填满稳定系统栏与 IME 共同确定的安全宽度，并把底边停在 IME 顶部；超出安全
 * 高度的像素、命中区与 semantics 均被裁切。[modal] 为 true 时同时隔离背景焦点、指针、
 * 文本输入和无障碍交互。该函数是受控内容组件，overlay 路由、遮罩和关闭生命周期仍由
 * 调用方维护。
 *
 * @param title 可选的 Sheet 标题 widget。
 * @param content 必需的 Sheet 正文。
 * @param actions 排列在正文下方并右对齐的操作 widget。
 * @param fillColor 可选 Sheet 表面填充色；null 时解析组件容器角色。
 * @param borderColor 可选 Sheet 表面边框色；null 时解析组件边框角色。
 * @param key 表面、布局、交互、焦点与语义边界共用的稳定 identity。
 * @param semanticLabel 可选无障碍名称；null 时解析主题 label token。
 * @param onDismissRequest back 或无障碍 dismiss 请求的受控回调。
 * @param modal 是否隔离 Sheet 外的焦点、指针、文本输入与无障碍交互。
 */
public fun BottomSheet(
    title: Widget? = null,
    content: Widget,
    actions: List<Widget> = emptyList(),
    fillColor: PixelColor? = null,
    borderColor: PixelColor? = null,
    key: Any? = null,
    semanticLabel: String? = null,
    onDismissRequest: (() -> Unit)? = null,
    modal: Boolean = true,
): Widget = BottomSheet(
    content = content,
    states = PixelControlStateSet.Normal,
    title = title,
    actions = actions,
    fillColor = fillColor,
    borderColor = borderColor,
    key = key,
    semanticLabel = semanticLabel,
    onDismissRequest = onDismissRequest,
    modal = modal,
)

/** 执行 `PixelComponents` 的 `BottomSheet` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware BottomSheet with token-resolved surface, spacing, and capability semantics.
 */
@kotlin.jvm.JvmName("BottomSheetWithControlStates")
public fun BottomSheet(
    content: Widget,
    states: PixelControlStateSet,
    title: Widget? = null,
    actions: List<Widget> = emptyList(),
    fillColor: PixelColor? = null,
    borderColor: PixelColor? = null,
    key: Any? = null,
    semanticLabel: String? = null,
    onDismissRequest: (() -> Unit)? = null,
    modal: Boolean = true,
): Widget = PixelThemeResolvedWidget(
    key = PixelThemeResolverKey(ownerKey = key, component = "BottomSheet", mode = modal),
) { context, theme ->
    /** Provider-aware BottomSheet and status labels independent from modal visual tokens. */
    val localization = pixelComponentLocalizationOf(context, theme)
    /** BottomSheet-specific passive overlay tokens. */
    val tokens = theme.components.bottomSheet
    /** Dismiss remains available unless a capability state blocks it. */
    val effectiveDismiss = onDismissRequest.takeIf {
        PixelControlState.Disabled !in states && PixelControlState.Loading !in states
    }
    safeOverlaySurface(
        title = title,
        content = content,
        actions = actions,
        decoration = PixelSurfaceDecoration(
            fillColor = fillColor ?: tokens.resolveContainerColor(states, theme.colors),
            borderColor = borderColor ?: tokens.resolveBorderColor(states, theme.colors),
            borderWidth = tokens.resolveBorderWidth(theme.borders),
            cornerRadius = tokens.resolveCornerRadius(theme.radii),
            shadowColor = theme.colors.shadow,
            shadowOffset = tokens.resolveElevation(theme.elevations),
        ),
        surfacePadding = tokens.resolvePadding(theme.spacing),
        contentSpacing = theme.spacing.small,
        actionSpacing = theme.spacing.small,
        key = key,
        semanticLabel = localization.resolveLabel(
            explicitText = semanticLabel,
            selector = PixelLabelTokens::bottomSheet,
        ),
        semanticValue = localization.resolveLabel(
            explicitText = null,
            selector = PixelLabelTokens::loading,
        ).takeIf { PixelControlState.Loading in states },
        semanticError = localization.resolveLabel(
            explicitText = null,
            selector = PixelLabelTokens::error,
        ).takeIf { PixelControlState.Error in states },
        onDismissRequest = effectiveDismiss,
        modal = modal,
        alignment = SafeOverlayAlignment.BottomCenter,
        fillSafeWidth = true,
    )
}

/** Builds the shared safe surface, semantics, interaction boundary, and optional focus scope. */
@Suppress("LongParameterList")
private fun safeOverlaySurface(
    /** Optional heading rendered before [content]. */
    title: Widget?,
    /** Primary overlay body. */
    content: Widget,
    /** Optional actions rendered after [content]. */
    actions: List<Widget>,
    /** Concrete surface paint, radius, border, and elevation decoration. */
    decoration: PixelSurfaceDecoration,
    /** Theme-resolved content padding. */
    surfacePadding: EdgeInsets,
    /** Theme-resolved title/body/footer spacing. */
    contentSpacing: Int,
    /** Theme-resolved spacing between footer actions. */
    actionSpacing: Int,
    /** Stable public identity used to derive internal boundary keys. */
    key: Any?,
    /** Accessible overlay name. */
    semanticLabel: String,
    /** Optional dynamic loading value. */
    semanticValue: String?,
    /** Optional validation or status error. */
    semanticError: String?,
    /** Optional controlled dismiss callback. */
    onDismissRequest: (() -> Unit)?,
    /** Whether background interaction and focus are isolated. */
    modal: Boolean,
    /** Center or bottom-center placement inside safe bounds. */
    alignment: SafeOverlayAlignment,
    /** Whether the surface fills the safe viewport width. */
    fillSafeWidth: Boolean,
): Widget {
    /** Title and body share the elastic region so the fixed action footer wins scarce height. */
    val bodyChildren = buildList {
        if (title != null) add(title)
        add(content)
        if (actions.isNotEmpty()) {
            /** Zero-sized tail turns normal 2px footer spacing into compressible body space. */
            add(SizedBox(width = 0, height = 0, key = key?.let { "$it-footer-gap" }))
        }
    }
    /** Clipped elastic body prevents oversized descendants from painting or targeting the footer. */
    val elasticBody = Flexible(
        child = SafeOverlayBodyViewportWidget(
            child = Column(children = bodyChildren, spacing = contentSpacing),
            key = key?.let { "$it-body-viewport" },
        ),
        fit = FlexFit.LOOSE,
        key = key?.let { "$it-body-flex" },
    )
    /** Surface children keep the action footer after the lower-priority elastic body. */
    val children = buildList {
        add(elasticBody)
        if (actions.isNotEmpty()) {
            add(Row(children = actions, spacing = actionSpacing, mainAxisAlignment = MainAxisAlignment.END))
        }
    }
    /** Constrained visual surface measured before safe-viewport placement. */
    val surface = PixelSurface(
        padding = surfacePadding,
        decoration = decoration,
        child = Column(children = children),
        key = key,
    )
    /** No-op target covering only the measured surface, before descendant real action targets. */
    val absorbingSurface = PixelOverlaySurface(
        child = surface,
        key = key?.let { "$it-overlay-surface" },
    )
    /** Semantic boundary follows the actual surface instead of claiming the complete Host area. */
    val semanticSurface = Semantics(
        label = semanticLabel,
        role = PixelSemanticRole.DIALOG,
        value = semanticValue,
        error = semanticError,
        mergeDescendants = false,
        actions = PixelSemanticsActions(
            onDismiss = onDismissRequest?.let { dismiss ->
                {
                    dismiss()
                    true
                }
            },
        ),
        child = absorbingSurface,
        key = key?.let { "$it-semantics" },
    )
    /** Safe layout merges stable padding with IME insets and clips all output channels. */
    val safePresentation = SafeOverlayViewportWidget(
        child = semanticSurface,
        alignment = alignment,
        fillSafeWidth = fillSafeWidth,
        key = key?.let { "$it-safe-layout" },
    )
    return if (modal) {
        ContextualSafeSurfaceModalBoundary(
            child = safePresentation,
            onDismissRequest = onDismissRequest,
            key = key?.let { "$it-modal-boundary" },
        )
    } else {
        ModalInteractionScopeWidget(
            active = false,
            child = safePresentation,
            key = key?.let { "$it-interaction" },
        )
    }
}

/** Lets a standard safe surface reuse the canonical modal owner of an enclosing popup route. */
private class ContextualSafeSurfaceModalBoundary(
    /** Safe-area-aware Dialog or BottomSheet subtree. */
    val child: Widget,
    /** Escape/Back callback used only when this surface owns the modal. */
    val onDismissRequest: (() -> Unit)?,
    /** Stable identity for the contextual modal decision. */
    override val key: Any?,
) : StatelessWidget(key = key) {
    /** Creates a modal owner only when no enclosing route already owns this presentation. */
    override fun build(context: BuildContext): Widget {
        /** Nearest route policy that permits standard nested surfaces to share its token. */
        val modalPresence = context.getInheritedWidgetOfExactType<PixelModalFocusPresence>()
        if (modalPresence?.coalesceNestedModal == true) return child
        return StandaloneModalBoundaryFactory.create(
            active = true,
            onDismissRequest = onDismissRequest,
            child = ModalInteractionScopeWidget(
                active = true,
                child = child,
                key = key?.let { "$it-interaction" },
            ),
            key = key,
        )
    }
}

/**
 * 像素确认对话框。
 *
 * 该组件是 [Dialog] 的受控组合封装，只负责标题、说明、取消/确认按钮布局。
 * overlay 显示、关闭句柄、遮罩、back 行为和危险操作二次校验都由调用方维护。
 *
 * @param title 对话框标题文本。
 * @param message 可选说明文本；空白时不渲染。
 * @param onConfirm 确认动作回调。
 * @param onCancel 可选取消动作回调；null 表示取消按钮不可用。
 * @param confirmText 可选确认标签；null 时解析主题 label token。
 * @param cancelText 可选取消标签；null 时解析主题 label token。
 * @param showCancel 是否渲染取消按钮。
 * @param fillColor 可选表面填充色；null 时解析组件容器角色。
 * @param borderColor 可选表面边框色；null 时解析组件边框角色。
 * @param titleStyle 可选标题样式；null 时解析 title typography。
 * @param messageStyle 可选说明样式；null 时解析 caption typography。
 * @param confirmStyle 确认按钮的显式样式。
 * @param cancelStyle 取消按钮的显式样式。
 * @param width 可选对话框固定宽度。
 * @param key 表面、交互、焦点与语义边界共用的稳定 identity。
 */
public fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onCancel: (() -> Unit)? = null,
    confirmText: String? = null,
    cancelText: String? = null,
    showCancel: Boolean = true,
    fillColor: PixelColor? = null,
    borderColor: PixelColor? = null,
    titleStyle: PixelTextStyle? = null,
    messageStyle: PixelTextStyle? = null,
    confirmStyle: ButtonStyle = ButtonStyle.Default,
    cancelStyle: TextButtonStyle = TextButtonStyle.Default,
    width: Int? = null,
    key: Any? = null,
): Widget = ConfirmDialog(
    title = title,
    message = message,
    onConfirm = onConfirm,
    states = PixelControlStateSet.Normal,
    onCancel = onCancel,
    showCancel = showCancel,
    confirmText = confirmText,
    cancelText = cancelText,
    fillColor = fillColor,
    borderColor = borderColor,
    titleStyle = titleStyle,
    messageStyle = messageStyle,
    confirmStyle = confirmStyle,
    cancelStyle = cancelStyle,
    width = width,
    key = key,
)

/** 执行 `PixelComponents` 的 `ConfirmDialog` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware ConfirmDialog whose text, action labels, spacing, and surface inherit theme tokens.
 */
@kotlin.jvm.JvmName("ConfirmDialogWithControlStates")
public fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    states: PixelControlStateSet,
    onCancel: (() -> Unit)? = null,
    showCancel: Boolean = true,
    confirmText: String? = null,
    cancelText: String? = null,
    fillColor: PixelColor? = null,
    borderColor: PixelColor? = null,
    titleStyle: PixelTextStyle? = null,
    messageStyle: PixelTextStyle? = null,
    confirmStyle: ButtonStyle = ButtonStyle.Default,
    cancelStyle: TextButtonStyle = TextButtonStyle.Default,
    width: Int? = null,
    key: Any? = null,
): Widget = PixelThemeResolvedWidget(key = key) { context, theme ->
    /** Provider-aware action labels independent from the Dialog surface theme. */
    val localization = pixelComponentLocalizationOf(context, theme)
    /** Confirmation label resolved after an optional caller override. */
    val resolvedConfirmText = localization.resolveLabel(
        explicitText = confirmText,
        selector = PixelLabelTokens::confirm,
    )
    /** Cancellation label resolved after an optional caller override. */
    val resolvedCancelText = localization.resolveLabel(
        explicitText = cancelText,
        selector = PixelLabelTokens::cancel,
    )
    /** Theme title typography unless explicitly overridden. */
    val resolvedTitleStyle = titleStyle ?: theme.typography.title.resolve(theme.colors)
    /** Theme caption typography unless explicitly overridden. */
    val resolvedMessageStyle = messageStyle ?: theme.typography.caption.resolve(theme.colors)
    /** Compact title and optional message column. */
    val bodyChildren = buildList {
        add(
            Text(
                title,
                style = resolvedTitleStyle,
                softWrap = true,
                maxLines = 2,
                overflow = PixelTextOverflow.ELLIPSIS,
                textAlign = TextAlign.CENTER,
            ),
        )
        if (message.isNotBlank()) {
            add(
                Text(
                    message,
                    style = resolvedMessageStyle,
                    softWrap = true,
                    maxLines = 3,
                    overflow = PixelTextOverflow.ELLIPSIS,
                    textAlign = TextAlign.CENTER,
                ),
            )
        }
    }
    val body = Column(
        children = bodyChildren,
        spacing = theme.spacing.extraSmall,
        mainAxisSize = MainAxisSize.MIN,
        crossAxisAlignment = CrossAxisAlignment.CENTER,
    )
    /** Theme-aware cancel and confirm action row. */
    val actions = buildList {
        if (showCancel) {
            add(
                TextButton(
                    text = resolvedCancelText,
                    onPressed = onCancel,
                    states = states,
                    enabled = onCancel != null,
                    style = cancelStyle,
                    key = key?.let { "$it-cancel" },
                ),
            )
        }
        add(
            OutlinedButton(
                text = resolvedConfirmText,
                onPressed = onConfirm,
                states = states,
                style = confirmStyle,
                key = key?.let { "$it-confirm" },
            ),
        )
    }
    Dialog(
        content = if (width == null) body else SizedBox(width = width.coerceAtLeast(1), child = body),
        states = states,
        actions = actions,
        fillColor = fillColor,
        borderColor = borderColor,
        key = key,
        semanticLabel = title,
        onDismissRequest = onCancel,
    )
}

/**
 * 填满父级 [Stack] 的模态遮罩。
 *
 * 该组件只负责绘制遮罩和可选点击关闭；不会自动管理 overlay、back、焦点锁定或动画。
 * 需要对话框生命周期时，请配合 [PixelOverlayHost] / [PixelOverlayController] 使用。
 *
 * @param color 可选遮罩颜色；null 时解析 scrim 语义角色。
 * @param dismissible 是否允许点击遮罩关闭。
 * @param onDismiss 关闭请求回调。
 * @param key 遮罩与语义边界共用的稳定 identity。
 */
public fun ModalBarrier(
    color: PixelColor? = null,
    dismissible: Boolean = false,
    onDismiss: (() -> Unit)? = null,
    key: Any? = null,
): Widget = ModalBarrier(
    states = PixelControlStateSet.Normal,
    color = color,
    dismissible = dismissible,
    onDismiss = onDismiss,
    key = key,
)

/** 执行 `PixelComponents` 的 `ModalBarrier` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware modal barrier using the current scrim and localized dismiss label tokens.
 */
@kotlin.jvm.JvmName("ModalBarrierWithControlStates")
public fun ModalBarrier(
    states: PixelControlStateSet,
    color: PixelColor? = null,
    dismissible: Boolean = false,
    onDismiss: (() -> Unit)? = null,
    key: Any? = null,
): Widget = PixelThemeResolvedWidget(key = key) { context, theme ->
    /** Provider-aware dismiss label kept separate from the scrim color branch. */
    val localization = pixelComponentLocalizationOf(context, theme)
    /** Effective dismiss callback suppressed by Loading or Disabled. */
    val effectiveDismiss = onDismiss.takeIf {
        dismissible &&
            PixelControlState.Disabled !in states &&
            PixelControlState.Loading !in states
    }
    /** Full-parent scrim paint. */
    val fill = Container(fillColor = color ?: theme.colors.scrim, key = key?.let { "$it-fill" })
    /** Optional pointer dismissal wrapper. */
    val barrier = if (effectiveDismiss != null) {
        GestureDetector(
            child = fill,
            onTap = effectiveDismiss,
            key = key,
        )
    } else {
        fill
    }
    /** Semantic dismissal boundary is exported only when dismissal is actually available. */
    val semanticBarrier = if (effectiveDismiss == null) {
        barrier
    } else {
        Semantics(
            label = localization.resolveLabel(
                explicitText = null,
                selector = PixelLabelTokens::dismiss,
            ),
            role = PixelSemanticRole.BUTTON,
            excludeDescendants = true,
            actions = PixelSemanticsActions(
                onClick = {
                    effectiveDismiss()
                    true
                },
                onDismiss = {
                    effectiveDismiss()
                    true
                },
            ),
            child = barrier,
            key = key?.let { "$it-semantics" },
        )
    }
    PositionedFill(child = semanticBarrier, key = key?.let { "$it-positioned" })
}

/**
 * 居中的短提示内容。
 *
 * 该函数只创建 toast widget，不内置自动超时或动画。需要 FIFO 与 Host active-time 超时时，
 * 使用 [ToastQueue] 和 [PixelToastQueueController]；自定义 route 生命周期可由
 * [PixelOverlayController] 的 entry 控制。
 *
 * @param message 提示文本；空白时解析主题 label token。
 * @param fillColor 可选表面填充色；null 时解析通知容器角色。
 * @param textStyle 可选文本样式；null 时解析 caption typography 与状态前景色。
 * @param key 表面与语义边界共用的稳定 identity。
 */
public fun Toast(
    message: String,
    fillColor: PixelColor? = null,
    textStyle: PixelTextStyle? = null,
    key: Any? = null,
): Widget = Toast(
    message = message,
    states = PixelControlStateSet.Normal,
    fillColor = fillColor,
    textStyle = textStyle,
    key = key,
)

/** 执行 `PixelComponents` 的 `Toast` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware Toast with token-resolved notification surface, typography, and live semantics.
 */
@kotlin.jvm.JvmName("ToastWithControlStates")
public fun Toast(
    message: String,
    states: PixelControlStateSet,
    fillColor: PixelColor? = null,
    textStyle: PixelTextStyle? = null,
    key: Any? = null,
): Widget = PixelThemeResolvedWidget(key = key) { context, theme ->
    /** Provider-aware Toast and status labels independent from notification visuals. */
    val localization = pixelComponentLocalizationOf(context, theme)
    /** Toast-specific notification tokens. */
    val tokens = theme.components.toast
    /** Blank message falls back to the localized Toast label. */
    val resolvedMessage = localization.resolveLabel(
        explicitText = message.takeIf { value -> value.isNotBlank() },
        selector = PixelLabelTokens::toast,
    )
    /** State-resolved foreground unless explicitly overridden by a complete text style. */
    val contentColor = tokens.resolveContentColor(states, theme.colors) ?: theme.colors.onSurface
    /** Theme caption typography with the current state foreground. */
    val resolvedTextStyle = textStyle
        ?: theme.typography.caption.resolve(theme.colors).copy(color = contentColor)
    /** live-region 语义包装前的居中 toast 表面。 */
    val toastSurface = Center(
        child = PixelSurface(
            padding = tokens.resolvePadding(theme.spacing),
            decoration = PixelSurfaceDecoration(
                fillColor = fillColor ?: tokens.resolveContainerColor(states, theme.colors),
                borderColor = tokens.resolveBorderColor(states, theme.colors),
                borderWidth = tokens.resolveBorderWidth(theme.borders),
                cornerRadius = tokens.resolveCornerRadius(theme.radii),
                shadowColor = theme.colors.shadow,
                shadowOffset = tokens.resolveElevation(theme.elevations),
            ),
            child = Text(
                resolvedMessage,
                style = resolvedTextStyle,
                softWrap = true,
                maxLines = Int.MAX_VALUE,
            ),
            key = key,
        ),
    )
    Semantics(
        label = resolvedMessage,
        role = PixelSemanticRole.GENERIC,
        value = localization.resolveLabel(
            explicitText = null,
            selector = PixelLabelTokens::loading,
        ).takeIf { PixelControlState.Loading in states },
        error = localization.resolveLabel(
            explicitText = null,
            selector = PixelLabelTokens::error,
        ).takeIf { PixelControlState.Error in states },
        liveRegion = PixelSemanticsLiveRegion.POLITE,
        excludeDescendants = true,
        child = toastSurface,
        key = key?.let { "$it-semantics" },
    )
}

/**
 * 像素风 snackbar 内容。
 *
 * 该函数只绘制条形内容本身；贴底定位由 [PixelOverlayController.showSnackbar] 或调用方的
 * [Positioned] 负责。可通过 [action] 放入一个按钮类 widget；需要 FIFO、一次性 action 与
 * active-time timeout 时使用 [SnackbarQueue] 和 [PixelSnackbarQueueController]。
 *
 * @param message 条形文本；空白时解析主题 label token。
 * @param action 可选行尾动作 widget。
 * @param fillColor 可选表面填充色；null 时解析通知容器角色。
 * @param textStyle 可选文本样式；null 时解析 body typography 与状态前景色。
 * @param key 表面与语义边界共用的稳定 identity。
 */
public fun Snackbar(
    message: String,
    action: Widget? = null,
    fillColor: PixelColor? = null,
    textStyle: PixelTextStyle? = null,
    key: Any? = null,
): Widget = Snackbar(
    message = message,
    states = PixelControlStateSet.Normal,
    action = action,
    fillColor = fillColor,
    textStyle = textStyle,
    key = key,
)

/** 执行 `PixelComponents` 的 `Snackbar` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware Snackbar with token-resolved notification surface and typography.
 */
@kotlin.jvm.JvmName("SnackbarWithControlStates")
public fun Snackbar(
    message: String,
    states: PixelControlStateSet,
    action: Widget? = null,
    fillColor: PixelColor? = null,
    textStyle: PixelTextStyle? = null,
    key: Any? = null,
): Widget = PixelThemeResolvedWidget(key = key) { context, theme ->
    /** Provider-aware Snackbar and status labels independent from notification visuals. */
    val localization = pixelComponentLocalizationOf(context, theme)
    /** Snackbar-specific state and geometry tokens. */
    val tokens = theme.components.snackbar
    /** Blank message falls back to the localized Snackbar label. */
    val resolvedMessage = localization.resolveLabel(
        explicitText = message.takeIf { value -> value.isNotBlank() },
        selector = PixelLabelTokens::snackbar,
    )
    /** Capability states remove an arbitrary action subtree instead of leaving hidden mutations. */
    val effectiveAction = action.takeIf {
        PixelControlState.Disabled !in states && PixelControlState.Loading !in states
    }
    /** State-resolved foreground unless a full text style is explicit. */
    val contentColor = tokens.resolveContentColor(states, theme.colors) ?: theme.colors.onSurface
    /** Theme body typography with state foreground. */
    val resolvedTextStyle = textStyle
        ?: theme.typography.body.resolve(theme.colors).copy(color = contentColor)
    /** Message and optional action retain their visual order. */
    val rowChildren = if (effectiveAction == null) {
        listOf<Widget>(Expanded(child = snackbarText(resolvedMessage, resolvedTextStyle)))
    } else {
        listOf(Expanded(child = snackbarText(resolvedMessage, resolvedTextStyle)), effectiveAction)
    }
    /** Token-resolved visual surface retained beneath structured live semantics. */
    val snackbarSurface = PixelSurface(
        padding = tokens.resolvePadding(theme.spacing),
        decoration = PixelSurfaceDecoration(
            fillColor = fillColor ?: tokens.resolveContainerColor(states, theme.colors),
            borderColor = tokens.resolveBorderColor(states, theme.colors),
            borderWidth = tokens.resolveBorderWidth(theme.borders),
            cornerRadius = tokens.resolveCornerRadius(theme.radii),
            shadowColor = theme.colors.shadow,
            shadowOffset = tokens.resolveElevation(theme.elevations),
        ),
        child = Row(
            children = rowChildren,
            spacing = theme.spacing.small,
            crossAxisAlignment = CrossAxisAlignment.CENTER,
        ),
        key = key,
    )
    Semantics(
        label = localization.resolveLabel(
            explicitText = null,
            selector = PixelLabelTokens::snackbar,
        ),
        role = PixelSemanticRole.GENERIC,
        enabled = PixelControlState.Disabled !in states && PixelControlState.Loading !in states,
        value = localization.resolveLabel(
            explicitText = null,
            selector = PixelLabelTokens::loading,
        ).takeIf { PixelControlState.Loading in states },
        error = localization.resolveLabel(
            explicitText = null,
            selector = PixelLabelTokens::error,
        ).takeIf { PixelControlState.Error in states },
        mergeDescendants = false,
        child = snackbarSurface,
        key = key?.let { "$it-semantics" },
    )
}

/**
 * 渲染受控标签条，并把指针、键盘与无障碍选择统一为单一回调。
 *
 * Renders one controlled tab strip as a single keyboard stop.
 *
 * Left/Right choose an adjacent label, Enter/Space reselect the current label, and [enabled]
 * removes the complete strip from pointer, semantic-action, and focus traversal when false.
 *
 * @param labels 按视觉顺序显示并作为无障碍名称的标签。
 * 每个非空标签同时作为动态重排时的稳定唯一身份。
 * @param selectedIndex 调用方持有的当前选中下标；空列表必须使用 `-1`。
 * @param onSelected Pointer、keyboard 或无障碍选择标签时回传其下标。
 * @param key 整个标签条及子项 retained 状态的稳定 identity。
 * @param enabled 是否启用整组 pointer、keyboard、DPAD 与无障碍选择。
 */
public fun Tabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    key: Any? = null,
    enabled: Boolean = true,
): Widget = Tabs(
    labels = labels,
    selectedIndex = selectedIndex,
    onSelected = onSelected,
    states = PixelControlStateSet.Normal,
    key = key,
    enabled = enabled,
)

/**
 * 执行 `PixelComponents` 的 `Tabs` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware tab strip using component state, motion, focus, and localized label tokens.
 *
 * Loading retains the group's single keyboard stop but suppresses all selection actions.
 */
@kotlin.jvm.JvmName("TabsWithControlStates")
public fun Tabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    states: PixelControlStateSet,
    key: Any? = null,
    enabled: Boolean = true,
): Widget {
    validateSingleSelectionLabels(
        componentName = "Tabs",
        labels = labels,
        selectedIndex = selectedIndex,
    )
    /** Persistent strip states normalized with caller availability. */
    var effectiveStates = states
    if (!enabled) effectiveStates += PixelControlState.Disabled
    /** Disabled removes traversal while Loading retains it. */
    val focusable = PixelControlState.Disabled !in effectiveStates && labels.isNotEmpty()
    /** Selection actions are suppressed for Loading and Disabled. */
    val interactive = focusable && PixelControlState.Loading !in effectiveStates
    /** Enter/Space 与当前选中 Tab 语义点击共用的重新选择动作。 */
    val selectCurrent: (() -> Boolean)? = labels.getOrNull(selectedIndex)?.takeIf { interactive }?.let {
        {
            onSelected(selectedIndex)
            true
        }
    }
    /** 整组唯一焦点节点使用的循环方向键与激活处理器。 */
    val keyHandler = tabSelectionKeyHandler(
        labels = labels,
        selectedIndex = selectedIndex,
        onSelected = onSelected,
        onActivate = selectCurrent,
    ).takeIf { interactive }
    return AutomaticFocusAction(
        enabled = focusable,
        debugLabel = TABS_DEBUG_LABEL,
        onKeyEvent = keyHandler,
        key = key,
    ) { _, _ ->
        PixelThemeResolvedWidget(key = key?.let { "$it-tabs-layout" }) { context, theme ->
            /** Provider-aware group label independent from tab layout and selection motion. */
            val localization = pixelComponentLocalizationOf(context, theme)
            /** Production tab row retained beneath a localized group semantic node. */
            val tabsRow = Row(
                children = labels.mapIndexed { index, label ->
                    /** 保留此槽位 selection 动画且避免内容替换时错误复用的 key。 */
                    val childKey = PixelTabMotionKey(parentKey = key, label = label)
                    PixelTabMotionItem(
                        label = label,
                        index = index,
                        selected = index == selectedIndex,
                        states = if (index == selectedIndex) {
                            effectiveStates + PixelControlState.Selected
                        } else {
                            effectiveStates
                        },
                        enabled = focusable,
                        interactive = interactive,
                        onSelected = onSelected,
                        key = childKey,
                    )
                },
                spacing = theme.spacing.extraSmall,
                key = key,
            )
            Semantics(
                label = localization.resolveLabel(
                    explicitText = null,
                    selector = PixelLabelTokens::tabs,
                ),
                role = PixelSemanticRole.GENERIC,
                enabled = interactive,
                collectionInfo = horizontalSingleSelectionCollection(labels.size),
                mergeDescendants = false,
                child = tabsRow,
                key = key?.let { "$it-group-semantics" },
            )
        }
    }
}

/** Tabs 焦点诊断使用的稳定兜底标签；朗读标签仍由主题 label token 解析。 */
private const val TABS_DEBUG_LABEL: String = "Tabs"

/** 值相等即可跨父级 rebuild 保持同一 Tab state 的稳定 key。 */
private data class PixelTabMotionKey(
    /** Tabs 调用方提供的父 identity。 */
    val parentKey: Any?,
    /** 唯一标签作为动态重排时的稳定业务 identity。 */
    val label: String,
)

/** 单个 Tab 的 selection 运动参数。 */
private data class PixelTabMotionItem(
    /** 显示及 semantics 标签。 */
    val label: String,
    /** 点击时回传的受控下标。 */
    val index: Int,
    /** 调用方立即拥有的逻辑选中状态。 */
    val selected: Boolean,
    /** Persistent normalized states for this tab, including controlled selection. */
    val states: PixelControlStateSet,
    /** Whether pointer, semantic, and parent keyboard selection remain available. */
    val enabled: Boolean,
    /** Whether Loading/Disabled currently permits selection. */
    val interactive: Boolean,
    /** 最新的 selection 回调。 */
    val onSelected: (Int) -> Unit,
    /** 跨快速切换保留视觉进度的稳定 identity。 */
    override val key: Any?,
) : StatefulWidget(key = key) {
    /** 创建此 Tab 独立的 selection 强度状态。 */
    override fun createState(): State<out StatefulWidget> = PixelTabMotionItemState()
}

/** 单个 Tab 的白色到选中色交叉变化状态。 */
private class PixelTabMotionItemState : State<PixelTabMotionItem>() {
    /** 当前 Tab 的 `0f..1f` selection 视觉强度。 */
    private lateinit var selectionMotion: PixelControlMotionValue

    /** Whether this tab currently owns a captured pointer press. */
    private var pressed: Boolean = false

    /** Whether a pointing device currently hovers over this tab. */
    private var hovered: Boolean = false

    /** mount 时直接使用逻辑状态，避免首帧出现错误选中色。 */
    override fun initState() {
        selectionMotion = PixelControlMotionValue(if (widget.selected) 1f else 0f)
    }

    /** 释放此 Tab selection 片段拥有的 ticker。 */
    override fun dispose() {
        selectionMotion.dispose()
    }

    /**
     * selection 只改变固定 1px 边框颜色；布局、按钮命中区域和逻辑 semantics 立即稳定。
     */
    override fun build(context: BuildContext): Widget {
        /** Complete theme token graph inherited by this retained tab. */
        val theme = PixelTheme.of(context)
        /** Provider-aware status labels while each visible tab label remains caller-owned. */
        val localization = pixelComponentLocalizationOf(context, theme)
        /** Tab-specific visual and focus tokens. */
        val tokens = theme.components.tabs
        /** 当前主题为 Tab selection 通道提供的运动规格。 */
        val selectionSpec = PixelMotionTheme.of(context).selection
        /** 可选统一 ticker 与 reduced-motion 设置来源。 */
        val motionScope = PixelMotionScope.maybeOf(context)
        configureControlMotion(selectionMotion, motionScope, selectionSpec)
        selectionMotion.animateTo(if (widget.selected) 1f else 0f)
        selectionMotion.watch(context)
        /** Theme-resolved normal border endpoint. */
        val normalBorder = tokens.resolveBorderColor(
            widget.states - PixelControlState.Selected,
            theme.colors,
        ) ?: theme.colors.outline
        /** Theme-resolved selected border endpoint. */
        val selectedBorder = tokens.resolveBorderColor(
            (widget.states - PixelControlState.Disabled - PixelControlState.Loading - PixelControlState.Error) +
                PixelControlState.Selected,
            theme.colors,
        ) ?: theme.colors.primary
        /** Motion-preserving border used only for Normal/Selected base states. */
        val animatedBorder = PixelColorTween(normalBorder, selectedBorder)
            .lerp(selectionMotion.value)
        /** Runtime visual states after retained pointer microstates are merged. */
        var visualStates = widget.states
        if (pressed) visualStates += PixelControlState.Pressed
        if (hovered) visualStates += PixelControlState.Hovered
        if (
            PixelControlState.Disabled in visualStates ||
            PixelControlState.Loading in visualStates
        ) {
            pressed = false
            hovered = false
            visualStates -= PixelControlState.Pressed
            visualStates -= PixelControlState.Hovered
        }
        /** Shared pointer and semantic selection action for this retained Tab item. */
        val select: (() -> Boolean)? = if (widget.interactive) {
            {
                widget.onSelected(widget.index)
                true
            }
        } else {
            null
        }
        /** Border keeps the historical selection motion for base states and token priority otherwise. */
        val borderColor = when (visualStates.highestPriority()) {
            PixelControlState.Normal,
            PixelControlState.Selected,
            -> animatedBorder
            else -> tokens.resolveBorderColor(visualStates, theme.colors)
        }
        /** State-resolved text foreground isolated to the content token field. */
        val contentColor = tokens.resolveContentColor(visualStates, theme.colors)
            ?: theme.colors.onSurface
        /** Token-resolved tab surface without a second internal feedback animation. */
        val surface = PixelSurface(
            decoration = PixelSurfaceDecoration(
                fillColor = tokens.resolveContainerColor(visualStates, theme.colors),
                borderColor = borderColor,
                borderWidth = tokens.resolveBorderWidth(theme.borders),
                cornerRadius = tokens.resolveCornerRadius(theme.radii),
                shadowColor = theme.colors.shadow,
                shadowOffset = tokens.resolveElevation(theme.elevations),
            ),
            padding = tokens.resolvePadding(theme.spacing),
            child = Text(
                widget.label,
                style = theme.typography.label.resolve(theme.colors).copy(color = contentColor),
                overflow = PixelTextOverflow.ELLIPSIS,
                softWrap = false,
                maxLines = 1,
                textAlign = TextAlign.CENTER,
            ),
            key = widget.key?.let { "$it-surface" },
        )
        /** Pointer microstate wrapper omitted while Loading or Disabled. */
        val interactiveSurface = select?.let { action ->
            InteractionDetector(
                child = surface,
                onTap = { action.invoke() },
                onPressedChanged = ::updatePressed,
                onHoveredChanged = ::updateHovered,
                key = widget.key,
            )
        } ?: surface
        return FocusableControl(
            label = widget.label,
            role = PixelSemanticRole.TAB,
            enabled = widget.enabled,
            semanticsEnabled = select != null,
            automaticallyFocusable = false,
            focusWhenParentFocused = widget.selected,
            selected = widget.selected,
            value = localization.resolveLabel(
                explicitText = null,
                selector = PixelLabelTokens::loading,
            ).takeIf { PixelControlState.Loading in widget.states },
            error = localization.resolveLabel(
                explicitText = null,
                selector = PixelLabelTokens::error,
            ).takeIf { PixelControlState.Error in widget.states },
            collectionItemInfo = PixelSemanticsCollectionItemInfo(
                rowIndex = 0,
                columnIndex = widget.index,
                selected = widget.selected,
            ),
            focusIndicator = tokens.focusIndicator ?: PixelFocusIndicatorTokens.Default,
            actions = PixelSemanticsActions(onClick = select),
            child = interactiveSurface,
            key = widget.key?.let { "$it-focusable" },
        )
    }

    /** Updates captured press ownership exactly once per transition. */
    private fun updatePressed(nextPressed: Boolean) {
        if (pressed == nextPressed) return
        setState { pressed = nextPressed }
    }

    /** Updates hover membership exactly once per pointer boundary transition. */
    private fun updateHovered(nextHovered: Boolean) {
        if (hovered == nextHovered) return
        setState { hovered = nextHovered }
    }
}

/**
 * 渲染受控分段选择器，并以整组为单位处理启用状态和方向键导航。
 *
 * Renders one controlled segmented selector as a single keyboard stop.
 *
 * [enabled] applies atomically to every segment and its shared directional key handler.
 *
 * @param labels 按视觉顺序显示并作为无障碍名称的分段标签；每项必须非空且唯一。
 * @param selectedIndex 调用方持有的当前选中下标；空列表必须使用 `-1`。
 * @param onSelected Pointer、keyboard 或无障碍选择分段时回传其下标。
 * @param key 整个分段组及子语义节点的稳定 identity。
 * @param enabled 是否启用整组 pointer、keyboard、DPAD 与无障碍选择。
 */
public fun SegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    key: Any? = null,
    enabled: Boolean = true,
): Widget = SegmentedControl(
    labels = labels,
    selectedIndex = selectedIndex,
    onSelected = onSelected,
    states = PixelControlStateSet.Normal,
    key = key,
    enabled = enabled,
)

/**
 * 执行 `PixelComponents` 的 `SegmentedControl` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware segmented selector with token-resolved per-segment interaction surfaces.
 *
 * The group remains one keyboard stop. Loading preserves that stop while blocking selection.
 */
@kotlin.jvm.JvmName("SegmentedControlWithControlStates")
public fun SegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    states: PixelControlStateSet,
    key: Any? = null,
    enabled: Boolean = true,
): Widget {
    validateSingleSelectionLabels(
        componentName = "SegmentedControl",
        labels = labels,
        selectedIndex = selectedIndex,
    )
    /** Persistent group states normalized with caller availability. */
    var effectiveStates = states
    if (!enabled) effectiveStates += PixelControlState.Disabled
    /** Disabled removes traversal while Loading retains focus ownership. */
    val focusable = PixelControlState.Disabled !in effectiveStates && labels.isNotEmpty()
    /** Selection actions are unavailable for Loading and Disabled. */
    val interactive = focusable && PixelControlState.Loading !in effectiveStates
    /** Enter/Space 与当前选中分段语义点击共用的重新选择动作。 */
    val selectCurrent: (() -> Boolean)? = labels.getOrNull(selectedIndex)?.takeIf { interactive }?.let {
        {
            onSelected(selectedIndex)
            true
        }
    }
    return AutomaticFocusAction(
        enabled = focusable,
        debugLabel = SEGMENTED_CONTROL_DEBUG_LABEL,
        onKeyEvent = tabSelectionKeyHandler(
            labels,
            selectedIndex,
            onSelected,
            selectCurrent,
        ).takeIf { interactive },
        key = key,
    ) { _, _ ->
        PixelThemeResolvedWidget(key = key?.let { "$it-segmented-layout" }) { context, theme ->
            /** Provider-aware group label independent from segmented-control layout. */
            val localization = pixelComponentLocalizationOf(context, theme)
            /** Production segment row retained beneath a localized group semantic node. */
            val segmentRow = Row(
                children = labels.mapIndexed { index, label ->
                    /** 当前分段的 pointer 与语义选择动作；禁用时统一为 null。 */
                    val select: (() -> Boolean)? = if (interactive) {
                        {
                            onSelected(index)
                            true
                        }
                    } else {
                        null
                    }
                    /** Controlled selection merged independently into this segment's persistent states. */
                    val segmentStates = if (index == selectedIndex) {
                        effectiveStates + PixelControlState.Selected
                    } else {
                        effectiveStates
                    }
                    PixelSegmentStateWidget(
                        label = label,
                        index = index,
                        selected = index == selectedIndex,
                        states = segmentStates,
                        select = select,
                        key = PixelSegmentKey(parentKey = key, label = label),
                    )
                },
                spacing = 0,
                key = key,
            )
            Semantics(
                label = localization.resolveLabel(
                    explicitText = null,
                    selector = PixelLabelTokens::segmentedControl,
                ),
                role = PixelSemanticRole.GENERIC,
                enabled = interactive,
                collectionInfo = horizontalSingleSelectionCollection(labels.size),
                mergeDescendants = false,
                child = segmentRow,
                key = key?.let { "$it-group-semantics" },
            )
        }
    }
}

/** SegmentedControl 焦点诊断使用的稳定兜底标签；朗读标签仍由主题 label token 解析。 */
private const val SEGMENTED_CONTROL_DEBUG_LABEL: String = "SegmentedControl"

/** Stable segment identity derived from the parent control and unique visible label. */
private data class PixelSegmentKey(
    /** Caller-owned group identity retained across list updates. */
    val parentKey: Any?,
    /** Unique segment label retained when its visual index changes. */
    val label: String,
)

/** Retained per-segment configuration with runtime-owned hover and press states. */
private data class PixelSegmentStateWidget(
    /** Visible and accessible segment label. */
    val label: String,
    /** Current zero-based visual column exported through collection semantics. */
    val index: Int,
    /** Controlled selection state. */
    val selected: Boolean,
    /** Persistent normalized states including controlled selection. */
    val states: PixelControlStateSet,
    /** Shared pointer and semantics selection action. */
    val select: (() -> Boolean)?,
    /** Stable segment identity. */
    override val key: Any?,
) : StatefulWidget(key = key) {
    /** Creates runtime pointer-state ownership for this segment. */
    override fun createState(): State<out StatefulWidget> = PixelSegmentState()
}

/** Runtime hover and press state for one segmented-control item. */
private class PixelSegmentState : State<PixelSegmentStateWidget>() {
    /** Whether this segment currently owns a pointer press. */
    private var pressed: Boolean = false

    /** Whether a pointer currently hovers over this segment. */
    private var hovered: Boolean = false

    /** Resolves the segment theme, transient states, semantics, and pointer wrapper. */
    override fun build(context: BuildContext): Widget {
        /** Complete inherited theme graph. */
        val theme = PixelTheme.of(context)
        /** Provider-aware status labels while segment names remain caller-owned. */
        val localization = pixelComponentLocalizationOf(context, theme)
        /** Segmented-control visual and geometry tokens. */
        val tokens = theme.components.segmented
        /** Parent strip focus shared by the selected segment's focus visual. */
        val focusNode = context.getInheritedWidgetOfExactType<FocusNodeScope>()?.node
        if (focusNode != null) context.watch(focusNode)
        /** Runtime state set after focus and pointer microstates. */
        var resolvedStates = widget.states
        if (widget.selected && focusNode?.isFocused == true) {
            resolvedStates += PixelControlState.Focused
        }
        if (pressed) resolvedStates += PixelControlState.Pressed
        if (hovered) resolvedStates += PixelControlState.Hovered
        if (
            PixelControlState.Disabled in resolvedStates ||
            PixelControlState.Loading in resolvedStates
        ) {
            pressed = false
            hovered = false
            resolvedStates -= PixelControlState.Pressed
            resolvedStates -= PixelControlState.Hovered
        }
        /** Concrete foreground resolved after the fixed state priority. */
        val contentColor = tokens.resolveContentColor(resolvedStates, theme.colors)
            ?: theme.colors.onSurface
        /** Token-resolved segment surface. */
        val surface = PixelSurface(
            decoration = PixelSurfaceDecoration(
                fillColor = tokens.resolveContainerColor(resolvedStates, theme.colors),
                borderColor = tokens.resolveBorderColor(resolvedStates, theme.colors),
                borderWidth = tokens.resolveBorderWidth(theme.borders),
                cornerRadius = tokens.resolveCornerRadius(theme.radii),
                shadowColor = theme.colors.shadow,
                shadowOffset = tokens.resolveElevation(theme.elevations),
            ),
            padding = tokens.resolvePadding(theme.spacing),
            child = Text(
                widget.label,
                style = theme.typography.label.resolve(theme.colors).copy(color = contentColor),
                overflow = PixelTextOverflow.ELLIPSIS,
                softWrap = false,
                maxLines = 1,
            ),
            key = widget.key?.let { "$it-surface" },
        )
        /** Pointer wrapper omitted for Loading and Disabled. */
        val interactiveSurface = widget.select?.let { select ->
            InteractionDetector(
                child = surface,
                onTap = { select.invoke() },
                onPressedChanged = ::updatePressed,
                onHoveredChanged = ::updateHovered,
                key = widget.key,
            )
        } ?: surface
        return FocusableControl(
            label = widget.label,
            role = PixelSemanticRole.TAB,
            enabled = PixelControlState.Disabled !in resolvedStates,
            semanticsEnabled = widget.select != null,
            automaticallyFocusable = false,
            focusWhenParentFocused = widget.selected,
            selected = widget.selected,
            value = localization.resolveLabel(
                explicitText = null,
                selector = PixelLabelTokens::loading,
            ).takeIf { PixelControlState.Loading in resolvedStates },
            error = localization.resolveLabel(
                explicitText = null,
                selector = PixelLabelTokens::error,
            ).takeIf { PixelControlState.Error in resolvedStates },
            collectionItemInfo = PixelSemanticsCollectionItemInfo(
                rowIndex = 0,
                columnIndex = widget.index,
                selected = widget.selected,
            ),
            focusIndicator = tokens.focusIndicator ?: PixelFocusIndicatorTokens.Default,
            actions = PixelSemanticsActions(onClick = widget.select),
            child = interactiveSurface,
            key = widget.key,
        )
    }

    /** Updates press ownership exactly once per transition. */
    private fun updatePressed(nextPressed: Boolean) {
        if (pressed == nextPressed) return
        setState { pressed = nextPressed }
    }

    /** Updates hover membership exactly once per boundary transition. */
    private fun updateHovered(nextHovered: Boolean) {
        if (hovered == nextHovered) return
        setState { hovered = nextHovered }
    }
}

/**
 * Creates cyclic horizontal selection actions for Tabs and SegmentedControl.
 *
 * @param labels 用于确定可选数量及循环边界的标签列表。
 * @param selectedIndex 已由 [validateSingleSelectionLabels] 验证的受控选中下标。
 * @param onSelected 左右方向键选择新下标时调用。
 * @param onActivate Enter/Space 重新选择当前项时调用。
 * @return 消费支持按键并回报动作是否执行的处理器。
 */
private fun tabSelectionKeyHandler(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    onActivate: (() -> Boolean)?,
): (PixelKeyEvent) -> Boolean = { event ->
    if (labels.isEmpty()) {
        false
    } else {
        /** Current index is safe because public construction validates it before mounting. */
        val currentIndex = selectedIndex
        when (event.key) {
            PixelKey.ARROW_LEFT -> {
                onSelected((currentIndex - 1).floorMod(labels.size))
                true
            }
            PixelKey.ARROW_RIGHT -> {
                onSelected((currentIndex + 1).floorMod(labels.size))
                true
            }
            PixelKey.ENTER,
            PixelKey.SPACE,
            -> onActivate?.invoke() == true
            else -> false
        }
    }
}

/** Validates exact-one selection and stable label identity for a controlled selector. */
private fun validateSingleSelectionLabels(
    componentName: String,
    labels: List<String>,
    selectedIndex: Int,
) {
    require(labels.all(String::isNotBlank)) { "$componentName labels must not be blank." }
    require(labels.distinct().size == labels.size) {
        "$componentName labels must be unique because they are retained identities."
    }
    if (labels.isEmpty()) {
        require(selectedIndex == -1) { "An empty $componentName must use selectedIndex = -1." }
    } else {
        require(selectedIndex in labels.indices) {
            "$componentName selectedIndex must identify exactly one label."
        }
    }
}

/** Creates horizontal SINGLE collection metadata, including a structurally empty collection. */
private fun horizontalSingleSelectionCollection(itemCount: Int): PixelSemanticsCollectionInfo {
    /** Empty selectors expose zero rows as well as zero columns instead of a phantom row. */
    val rowCount = if (itemCount == 0) 0 else 1
    return PixelSemanticsCollectionInfo(
        rowCount = rowCount,
        columnCount = itemCount,
        selectionMode = PixelSemanticsSelectionMode.SINGLE,
    )
}

/**
 * [ValueAdjuster] 的可选颜色覆盖；null 字段回退到当前 [PixelTheme]。
 *
 * @param borderColor 外边框和分隔线颜色。
 * @param buttonFillColor 两侧操作按钮填充色。
 * @param buttonSymbolColor 加减符号颜色。
 * @param valueTextColor 中央值文本颜色。
 * @param disabledColor 禁用按钮、边框和值文本的颜色。
 * @param focusColor 整组获得焦点时的边框强调色。
 */
public data class ValueAdjusterStyle(
    val borderColor: PixelColor? = null,
    val buttonFillColor: PixelColor? = null,
    val buttonSymbolColor: PixelColor? = null,
    val valueTextColor: PixelColor? = null,
    val disabledColor: PixelColor? = null,
    val focusColor: PixelColor? = null,
) {
    /** 集中提供 `PixelComponents` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 完全继承当前主题 token 的默认样式。 */
        public val Default: ValueAdjusterStyle = ValueAdjusterStyle()
    }
}

/**
 * 通用的减 / 值 / 加受控像素调节器。
 *
 * 组件不保存数值，也不做范围判断；调用方通过 [onDecrease] 和 [onIncrease] 控制边界。
 * 当某一侧回调为 null 或 [enabled] 为 false 时，对应按钮不可点。
 * 简洁入口与状态化入口共用同一套组件 token 解析。
 *
 * @param valueText 中央显示的受控值文本。
 * @param onDecrease 左侧按钮及向左/向下键执行的减值回调；null 表示该方向不可用。
 * @param onIncrease 右侧按钮及向右/向上键执行的增值回调；null 表示该方向不可用。
 * @param label 可选的组标题；同时作为自动焦点节点调试名称。
 * @param enabled 是否允许任一侧 pointer、keyboard 与无障碍动作。
 * @param valueWidth 中央值单元格的目标像素宽度，内部至少按 1px 处理。
 * @param style 颜色覆盖；未指定字段回退到当前主题。
 * @param key 视觉、焦点、命中与虚拟语义节点共用的稳定 identity。
 */
public fun ValueAdjuster(
    valueText: String,
    onDecrease: (() -> Unit)?,
    onIncrease: (() -> Unit)?,
    label: String? = null,
    enabled: Boolean = true,
    valueWidth: Int = 24,
    style: ValueAdjusterStyle = ValueAdjusterStyle.Default,
    key: Any? = null,
): Widget = buildValueAdjuster(
    valueText = valueText,
    onDecrease = onDecrease,
    onIncrease = onIncrease,
    states = PixelControlStateSet.Normal,
    label = label,
    enabled = enabled,
    valueWidth = valueWidth,
    style = style,
    key = key,
)

/**
 * 执行 `PixelComponents` 的 `ValueAdjuster` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware ValueAdjuster resolving colors, labels, spacing, and focus from theme tokens.
 *
 * Loading retains focus and value semantics while every value-changing action is suppressed.
 */
@kotlin.jvm.JvmName("ValueAdjusterWithControlStates")
public fun ValueAdjuster(
    valueText: String,
    onDecrease: (() -> Unit)?,
    onIncrease: (() -> Unit)?,
    states: PixelControlStateSet,
    label: String? = null,
    enabled: Boolean = true,
    valueWidth: Int = 24,
    style: ValueAdjusterStyle = ValueAdjusterStyle.Default,
    key: Any? = null,
): Widget {
    return buildValueAdjuster(
        valueText = valueText,
        onDecrease = onDecrease,
        onIncrease = onIncrease,
        states = states,
        label = label,
        enabled = enabled,
        valueWidth = valueWidth,
        style = style,
        key = key,
    )
}

/** 为两个公开入口统一归一化 ValueAdjuster 的能力状态。 */
private fun buildValueAdjuster(
    valueText: String,
    onDecrease: (() -> Unit)?,
    onIncrease: (() -> Unit)?,
    states: PixelControlStateSet,
    label: String?,
    enabled: Boolean,
    valueWidth: Int,
    style: ValueAdjusterStyle,
    key: Any?,
): Widget {
    /** Persistent states normalized with actual action availability. */
    var effectiveStates = states
    if (!enabled || (onDecrease == null && onIncrease == null)) {
        effectiveStates += PixelControlState.Disabled
    }
    /** Disabled removes traversal while Loading retains the current focus node. */
    val focusable = PixelControlState.Disabled !in effectiveStates
    /** Value-changing actions are blocked while Loading or Disabled. */
    val interactive = focusable && PixelControlState.Loading !in effectiveStates
    /** Runtime-safe decrement callback. */
    val effectiveDecrease = onDecrease.takeIf { interactive }
    /** Runtime-safe increment callback. */
    val effectiveIncrease = onIncrease.takeIf { interactive }
    /** Creates one shared focus boundary for both directional actions and group semantics. */
    return AutomaticFocusAction(
        enabled = focusable,
        debugLabel = label ?: VALUE_ADJUSTER_DEBUG_LABEL,
        onKeyEvent = { event ->
            when (event.key) {
                PixelKey.ARROW_LEFT,
                PixelKey.ARROW_DOWN,
                -> {
                    effectiveDecrease?.invoke()
                    effectiveDecrease != null
                }
                PixelKey.ARROW_RIGHT,
                PixelKey.ARROW_UP,
                -> {
                    effectiveIncrease?.invoke()
                    effectiveIncrease != null
                }
                else -> false
            }
        },
        key = key,
    ) { _, _ ->
        ValueAdjusterWidget(
            valueText = valueText,
            onDecrease = effectiveDecrease,
            onIncrease = effectiveIncrease,
            states = effectiveStates,
            label = label,
            valueWidth = valueWidth,
            style = style,
            key = key,
        )
    }
}

/** ValueAdjuster 焦点诊断使用的稳定兜底标签。 */
private const val VALUE_ADJUSTER_DEBUG_LABEL: String = "ValueAdjuster"

/** Theme-resolving ValueAdjuster configuration. */
private data class ValueAdjusterWidget(
    /** Controlled visible value. */
    val valueText: String,
    /** Effective decrement callback, or null when unavailable. */
    val onDecrease: (() -> Unit)?,
    /** Effective increment callback, or null when unavailable. */
    val onIncrease: (() -> Unit)?,
    /** Persistent normalized component states. */
    val states: PixelControlStateSet,
    /** Optional caller-visible group label. */
    val label: String?,
    /** Requested center value-cell width. */
    val valueWidth: Int,
    /** Explicit caller color overrides. */
    val style: ValueAdjusterStyle,
    /** Stable visual, focus, hit, and semantics identity. */
    override val key: Any? = null,
) : StatelessWidget(key = key) {
    /** Resolves state colors and creates the retained custom render widget. */
    override fun build(context: BuildContext): Widget {
        /** Complete theme token graph. */
        val theme = PixelTheme.of(context)
        /** Provider-aware group, action, and status labels independent from adjuster visuals. */
        val localization = pixelComponentLocalizationOf(context, theme)
        /** ValueAdjuster-specific state and geometry tokens. */
        val tokens = theme.components.valueAdjuster
        /** Focus inherited from the public automatic focus boundary. */
        val focusNode = context.getInheritedWidgetOfExactType<FocusNodeScope>()?.node
        if (focusNode != null) {
            context.watch(focusNode)
        }
        /** Runtime state set with independent focus membership. */
        var resolvedStates = states
        if (focusNode?.isFocused == true) resolvedStates += PixelControlState.Focused
        /** Whether decrement is currently actionable. */
        val decreaseEnabled = onDecrease != null
        /** Whether increment is currently actionable. */
        val increaseEnabled = onIncrease != null
        /** Canonical Disabled state used to resolve the two passive action-slot channels. */
        val disabledStates = PixelControlStateSet.of(PixelControlState.Disabled)
        /** 显式样式契约承诺的整控件禁用覆写色。 Whole-control disabled override promised by the explicit style contract. */
        val wholeControlDisabledColor = style.disabledColor.takeIf {
            PixelControlState.Disabled in resolvedStates
        }
        /** 禁用态动作填充色，显式样式优先。 Concrete disabled action fill with explicit style precedence. */
        val disabledFillColor = style.disabledColor
            ?: tokens.resolveContainerColor(disabledStates, theme.colors)
            ?: theme.colors.disabled
        /** Concrete disabled action glyph resolved independently from its fill. */
        val disabledSymbolColor = style.disabledColor
            ?: tokens.resolveContentColor(disabledStates, theme.colors)
            ?: theme.colors.onDisabled
        /** Base state excludes Focused because focus is an additive paint layer. */
        val baseStates = resolvedStates - PixelControlState.Focused
        /** Concrete outline color following explicit style then component token precedence. */
        val borderColor = wholeControlDisabledColor
            ?: style.borderColor
            ?: tokens.resolveBorderColor(baseStates, theme.colors)
            ?: theme.colors.outline
        /** Concrete action fill following explicit style then component token precedence. */
        val buttonFillColor = wholeControlDisabledColor
            ?: style.buttonFillColor
            ?: tokens.resolveContainerColor(baseStates, theme.colors)
            ?: borderColor
        /** Concrete action glyph color following explicit style then component token precedence. */
        val buttonSymbolColor = style.buttonSymbolColor
            ?: tokens.resolveContentColor(baseStates, theme.colors)
            ?: theme.colors.onSurface
        /** Concrete controlled-value foreground. */
        val valueColor = wholeControlDisabledColor
            ?: style.valueTextColor
            ?: tokens.resolveContentColor(baseStates, theme.colors)
            ?: theme.colors.onSurface
        /** Foundation-resolved outer outline and divider thickness. */
        val resolvedBorderWidth = tokens.resolveBorderWidth(theme.borders)
        /** Foundation-resolved value-cell padding. */
        val resolvedValuePadding = tokens.resolvePadding(theme.spacing)
        /** Action-cell width preserving the historical 11px result for default 9px size + border. */
        val resolvedButtonWidth = (
            tokens.resolveMinimumWidth(theme.sizes) + resolvedBorderWidth * 2
            ).coerceAtLeast(resolvedBorderWidth * 2 + 1)
        /** Total minimum height preserving the historical 13px result for compact height 12. */
        val resolvedMinimumHeight = (
            tokens.resolveMinimumHeight(theme.sizes) + resolvedBorderWidth
            ).coerceAtLeast(resolvedBorderWidth * 2 + 1)
        /** Foundation-resolved stair-step corner radius. */
        val resolvedCornerRadius = tokens.resolveCornerRadius(theme.radii)
        /** Theme typography used for the controlled value. */
        val valueStyle = theme.typography.label.resolve(theme.colors).copy(color = valueColor)
        val controls = ValueAdjusterRenderWidget(
            value = Text(
                valueText,
                style = valueStyle,
                overflow = PixelTextOverflow.ELLIPSIS,
                softWrap = false,
                maxLines = 1,
                textAlign = TextAlign.CENTER,
                key = key?.let { "$it-value" },
            ),
            onDecrease = onDecrease,
            onIncrease = onIncrease,
            decreaseEnabled = decreaseEnabled,
            increaseEnabled = increaseEnabled,
            valueWidth = valueWidth,
            borderWidth = resolvedBorderWidth,
            buttonWidth = resolvedButtonWidth,
            minimumHeight = resolvedMinimumHeight,
            valuePadding = resolvedValuePadding,
            cornerRadius = resolvedCornerRadius,
            borderColor = borderColor,
            buttonFillColor = buttonFillColor,
            buttonSymbolColor = buttonSymbolColor,
            disabledFillColor = disabledFillColor,
            disabledSymbolColor = disabledSymbolColor,
            decreaseLabel = localization.resolveLabel(
                explicitText = null,
                selector = PixelLabelTokens::decrease,
            ),
            increaseLabel = localization.resolveLabel(
                explicitText = null,
                selector = PixelLabelTokens::increase,
            ),
            key = key,
        )
        /** Independent shrink-wrapping focus layer that preserves virtual action geometry. */
        val focusedControls = withControlFocusIndicator(
            child = controls,
            states = resolvedStates,
            componentTokens = tokens,
            colors = theme.colors,
            borders = theme.borders,
            key = key?.let { "$it-focus-indicator" },
            colorOverride = style.focusColor,
        )
        /** Optional visible label uses localized fallback only for semantics, not visual insertion. */
        val labeledControls = if (label == null) {
            focusedControls
        } else {
            Column(
                children = listOf(
                    Text(label, style = theme.typography.label.resolve(theme.colors)),
                    focusedControls,
                ),
                spacing = theme.spacing.extraSmall,
                crossAxisAlignment = CrossAxisAlignment.START,
                key = key,
            )
        }
        return Semantics(
            label = localization.resolveLabel(
                explicitText = label,
                selector = PixelLabelTokens::valueAdjuster,
            ),
            role = PixelSemanticRole.GENERIC,
            enabled = PixelControlState.Disabled !in resolvedStates &&
                PixelControlState.Loading !in resolvedStates,
            focused = PixelControlState.Focused in resolvedStates,
            value = if (PixelControlState.Loading in resolvedStates) {
                localization.resolveLabel(
                    explicitText = null,
                    selector = PixelLabelTokens::loading,
                )
            } else {
                valueText
            },
            error = localization.resolveLabel(
                explicitText = null,
                selector = PixelLabelTokens::error,
            ).takeIf { PixelControlState.Error in resolvedStates },
            mergeDescendants = false,
            child = labeledControls,
            key = key?.let { "$it-group-semantics" },
        )
    }
}

private class ValueAdjusterRenderWidget(
    /** Controlled value widget occupying the center cell. */
    private val value: Widget,
    /** Effective decrement callback. */
    private val onDecrease: (() -> Unit)?,
    /** Effective increment callback. */
    private val onIncrease: (() -> Unit)?,
    /** Whether the decrement slot is actionable. */
    private val decreaseEnabled: Boolean,
    /** Whether the increment slot is actionable. */
    private val increaseEnabled: Boolean,
    /** Requested center value-cell width. */
    private val valueWidth: Int,
    /** Foundation-resolved outer outline and divider thickness. */
    private val borderWidth: Int,
    /** Foundation-resolved width of each action cell. */
    private val buttonWidth: Int,
    /** Foundation-resolved minimum total control height. */
    private val minimumHeight: Int,
    /** Foundation-resolved padding inside the center value cell. */
    private val valuePadding: EdgeInsets,
    /** Foundation-resolved outer stair-step corner radius. */
    private val cornerRadius: Int,
    /** Concrete outer border and divider color. */
    private val borderColor: PixelColor,
    /** Concrete enabled action-slot fill. */
    private val buttonFillColor: PixelColor,
    /** Concrete enabled action glyph color. */
    private val buttonSymbolColor: PixelColor,
    /** Concrete disabled action-slot fill color. */
    private val disabledFillColor: PixelColor,
    /** Concrete disabled action glyph color. */
    private val disabledSymbolColor: PixelColor,
    /** Localized decrement virtual-node label. */
    private val decreaseLabel: String,
    /** Localized increment virtual-node label. */
    private val increaseLabel: String,
    /** Stable retained render identity. */
    override val key: Any? = null,
) : MultiChildRenderObjectWidget(
    children = listOf(
        ValueAdjusterActionSlot(onTap = onDecrease, key = key?.let { "$it-decrease" }),
        value,
        ValueAdjusterActionSlot(onTap = onIncrease, key = key?.let { "$it-increase" }),
    ),
    key = key,
) {
    /** Creates the retained renderer with the first resolved geometry and paint snapshot. */
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderValueAdjuster(
            onDecrease = onDecrease,
            onIncrease = onIncrease,
            decreaseEnabled = decreaseEnabled,
            increaseEnabled = increaseEnabled,
            valueWidth = valueWidth,
            borderWidth = borderWidth,
            buttonWidth = buttonWidth,
            minimumHeight = minimumHeight,
            valuePadding = valuePadding,
            cornerRadius = cornerRadius,
            borderColor = borderColor,
            buttonFillColor = buttonFillColor,
            buttonSymbolColor = buttonSymbolColor,
            disabledFillColor = disabledFillColor,
            disabledSymbolColor = disabledSymbolColor,
            decreaseLabel = decreaseLabel,
            increaseLabel = increaseLabel,
        )
    }

    /** Updates the retained renderer without replacing action or value child identity. */
    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderValueAdjuster).update(
            onDecrease = onDecrease,
            onIncrease = onIncrease,
            decreaseEnabled = decreaseEnabled,
            increaseEnabled = increaseEnabled,
            valueWidth = valueWidth,
            borderWidth = borderWidth,
            buttonWidth = buttonWidth,
            minimumHeight = minimumHeight,
            valuePadding = valuePadding,
            cornerRadius = cornerRadius,
            borderColor = borderColor,
            buttonFillColor = buttonFillColor,
            buttonSymbolColor = buttonSymbolColor,
            disabledFillColor = disabledFillColor,
            disabledSymbolColor = disabledSymbolColor,
            decreaseLabel = decreaseLabel,
            increaseLabel = increaseLabel,
        )
    }
}

private data class ValueAdjusterActionSlot(
    /** Slot callback retained solely for element identity and diagnostics. */
    val onTap: (() -> Unit)?,
    /** Stable slot identity. */
    override val key: Any? = null,
) : LeafRenderObjectWidget(key = key) {
    /** Creates a zero-sized placeholder; the parent owns actual hit geometry. */
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderValueAdjusterActionSlot()
    }
}

private class RenderValueAdjusterActionSlot : RenderBox() {
    /** Keeps this placeholder out of parent layout calculations. */
    override fun layout(constraints: RenderConstraints) {
        size = RenderSize.Zero
    }

    /** The parent custom renderer paints the action slot. */
    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) = Unit
}

/** Custom three-cell renderer for ValueAdjuster geometry, paint, hit, and virtual semantics. */
private class RenderValueAdjuster(
    /** Latest decrement callback. */
    private var onDecrease: (() -> Unit)?,
    /** Latest increment callback. */
    private var onIncrease: (() -> Unit)?,
    /** Latest decrement availability. */
    private var decreaseEnabled: Boolean,
    /** Latest increment availability. */
    private var increaseEnabled: Boolean,
    /** Requested center value-cell width. */
    private var valueWidth: Int,
    /** Current outer outline and divider thickness. */
    private var borderWidth: Int,
    /** Current width of each action cell. */
    private var buttonWidth: Int,
    /** Current minimum total control height. */
    private var minimumHeight: Int,
    /** Current padding inside the center value cell. */
    private var valuePadding: EdgeInsets,
    /** Current outer stair-step corner radius. */
    private var cornerRadius: Int,
    /** Concrete outer border and divider color. */
    private var borderColor: PixelColor,
    /** Concrete action-slot fill. */
    private var buttonFillColor: PixelColor,
    /** Concrete action glyph color. */
    private var buttonSymbolColor: PixelColor,
    /** Concrete disabled action-slot fill color. */
    private var disabledFillColor: PixelColor,
    /** Concrete disabled action glyph color. */
    private var disabledSymbolColor: PixelColor,
    /** Localized decrement semantics label. */
    private var decreaseLabel: String,
    /** Localized increment semantics label. */
    private var increaseLabel: String,
) : MultiChildRenderObject() {
    /** Measured center-value horizontal offset. */
    private var valueOffsetX = 0
    /** Measured center-value vertical offset. */
    private var valueOffsetY = 0
    /** Outer border width after fitting the latest parent constraints. */
    private var layoutBorderWidth = borderWidth.coerceAtLeast(0)
    /** Divider width after preserving space for both action cells. */
    private var layoutDividerWidth = borderWidth.coerceAtLeast(0)
    /** Symmetric action-cell width after fitting the latest parent constraints. */
    private var layoutButtonWidth = buttonWidth.coerceAtLeast(0)
    /** Center value-cell width after fitting the latest parent constraints. */
    private var layoutValueCellWidth = valueWidth.coerceAtLeast(0)

    /** Applies new callbacks, state colors, and localized labels to this retained renderer. */
    fun update(
        onDecrease: (() -> Unit)?,
        onIncrease: (() -> Unit)?,
        decreaseEnabled: Boolean,
        increaseEnabled: Boolean,
        valueWidth: Int,
        borderWidth: Int,
        buttonWidth: Int,
        minimumHeight: Int,
        valuePadding: EdgeInsets,
        cornerRadius: Int,
        borderColor: PixelColor,
        buttonFillColor: PixelColor,
        buttonSymbolColor: PixelColor,
        disabledFillColor: PixelColor,
        disabledSymbolColor: PixelColor,
        decreaseLabel: String,
        increaseLabel: String,
    ) {
        if (
            this.onDecrease === onDecrease &&
            this.onIncrease === onIncrease &&
            this.decreaseEnabled == decreaseEnabled &&
            this.increaseEnabled == increaseEnabled &&
            this.valueWidth == valueWidth &&
            this.borderWidth == borderWidth &&
            this.buttonWidth == buttonWidth &&
            this.minimumHeight == minimumHeight &&
            this.valuePadding == valuePadding &&
            this.cornerRadius == cornerRadius &&
            this.borderColor == borderColor &&
            this.buttonFillColor == buttonFillColor &&
            this.buttonSymbolColor == buttonSymbolColor &&
            this.disabledFillColor == disabledFillColor &&
            this.disabledSymbolColor == disabledSymbolColor &&
            this.decreaseLabel == decreaseLabel &&
            this.increaseLabel == increaseLabel
        ) {
            return
        }
        val needsLayout = this.valueWidth != valueWidth ||
            this.borderWidth != borderWidth ||
            this.buttonWidth != buttonWidth ||
            this.minimumHeight != minimumHeight ||
            this.valuePadding != valuePadding
        this.onDecrease = onDecrease
        this.onIncrease = onIncrease
        this.decreaseEnabled = decreaseEnabled
        this.increaseEnabled = increaseEnabled
        this.valueWidth = valueWidth
        this.borderWidth = borderWidth.coerceAtLeast(0)
        this.buttonWidth = buttonWidth.coerceAtLeast(1)
        this.minimumHeight = minimumHeight.coerceAtLeast(1)
        this.valuePadding = valuePadding
        this.cornerRadius = cornerRadius.coerceAtLeast(0)
        this.borderColor = borderColor
        this.buttonFillColor = buttonFillColor
        this.buttonSymbolColor = buttonSymbolColor
        this.disabledFillColor = disabledFillColor
        this.disabledSymbolColor = disabledSymbolColor
        this.decreaseLabel = decreaseLabel
        this.increaseLabel = increaseLabel
        if (needsLayout) {
            markNeedsLayout()
        }
        markNeedsPaint()
    }

    /** Resolves token-driven cells, padding, and minimum height under the parent constraints. */
    override fun layout(constraints: RenderConstraints) {
        actionSlots.forEach { slot ->
            slot.layout(RenderConstraints(maxWidth = 0, maxHeight = 0))
        }

        /** Caller-requested center width with the historical one-pixel unconstrained floor. */
        val requestedValueWidth = valueWidth.coerceAtLeast(1)
        /** Non-negative configured border used only to calculate the natural width. */
        val requestedBorderWidth = borderWidth.coerceAtLeast(0)
        /** Non-negative configured action extent used only to calculate the natural width. */
        val requestedButtonWidth = buttonWidth.coerceAtLeast(0)
        /** Natural width before the parent applies a potentially narrower viewport. */
        val desiredWidth =
            (requestedBorderWidth * 2) +
                (requestedButtonWidth * 2) +
                (requestedBorderWidth * 2) +
                requestedValueWidth
        /** Actual width owned by this renderer and therefore by every exported geometry channel. */
        val constrainedWidth = constraints.constrainWidth(desiredWidth)
        /** Border layers cannot consume more than half of either constrained axis. */
        val fittedBorderWidth = minOf(
            requestedBorderWidth,
            constrainedWidth / 2,
            constraints.maxHeight.coerceAtLeast(0) / 2,
        )
        resolveHorizontalGeometry(totalWidth = constrainedWidth, outerBorderWidth = fittedBorderWidth)

        /** Width still available to the text after its token-resolved horizontal padding. */
        val valueContentWidth = valueContentWidth(layoutValueCellWidth)
        val value = valueBox
        value?.layout(
            RenderConstraints(
                minWidth = valueContentWidth,
                maxWidth = valueContentWidth,
                minHeight = 0,
                maxHeight = (
                    constraints.maxHeight -
                        (layoutBorderWidth * 2) -
                        valuePadding.top -
                        valuePadding.bottom
                    ).coerceAtLeast(0),
            ),
        )

        /** Natural inner height large enough for both the text and the centered action glyph. */
        val desiredInnerHeight = centeredSymbolExtent(
            maxOf(
                minimumHeight - (layoutBorderWidth * 2),
                (value?.size?.height ?: 0) + valuePadding.top + valuePadding.bottom,
            ),
        )
        /** Final constrained size shared by paint, hit testing, click targets, and semantics. */
        size = RenderSize(
            width = constrainedWidth,
            height = constraints.constrainHeight(desiredInnerHeight + (layoutBorderWidth * 2)),
        )

        /** Center text offset clamped so even a zero-height parent cannot leak child paint. */
        val valueHeight = value?.size?.height ?: 0
        valueOffsetX = valueContentLeft(layoutValueCellWidth)
        valueOffsetY = ((size.height - valueHeight) / 2).coerceIn(
            minimumValue = 0,
            maximumValue = (size.height - valueHeight).coerceAtLeast(0),
        )
    }

    /** Paints cells, value, dividers, rounded border, and derived action glyphs in order. */
    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        val innerHeight = innerHeight()
        if (size.width <= 0 || size.height <= 0 || innerHeight <= 0) {
            return
        }

        fillButton(context, offsetX, offsetY, leftButtonX(), decreaseEnabled)
        fillButton(context, offsetX, offsetY, rightButtonX(), increaseEnabled)

        if (layoutValueCellWidth > 0) {
            valueBox?.paint(context, offsetX + valueOffsetX, offsetY + valueOffsetY)
        }

        /** Divider thickness shares the component border scale. */
        val dividerWidth = dividerWidth()
        if (dividerWidth > 0) {
            context.fillRect(offsetX + leftDividerX(), offsetY, dividerWidth, size.height, borderColor)
            context.fillRect(offsetX + rightDividerX(), offsetY, dividerWidth, size.height, borderColor)
        }
        paintValueAdjusterBorder(context, offsetX, offsetY)

        drawSymbol(context, offsetX, offsetY, leftButtonX(), plus = false, enabled = decreaseEnabled)
        drawSymbol(context, offsetX, offsetY, rightButtonX(), plus = true, enabled = increaseEnabled)
    }

    /** Adds this renderer only for an enabled action cell inside the resolved control bounds. */
    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        if (localX !in 0 until size.width || localY !in 0 until size.height) {
            return
        }
        if (
            (decreaseEnabled && leftButtonRect().contains(localX, localY)) ||
            (increaseEnabled && rightButtonRect().contains(localX, localY))
        ) {
            result.add(this)
        }
    }

    /** Exports token-resolved click rectangles only for currently available actions. */
    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) {
        if (decreaseEnabled) {
            onDecrease?.let { callback ->
                visibleRect(leftButtonRect())?.let { visibleBounds ->
                    targets += PixelClickTarget(
                        bounds = visibleBounds.translate(offsetX, offsetY),
                        onClick = callback,
                        source = this,
                    )
                }
            }
        }
        if (increaseEnabled) {
            onIncrease?.let { callback ->
                visibleRect(rightButtonRect())?.let { visibleBounds ->
                    targets += PixelClickTarget(
                        bounds = visibleBounds.translate(offsetX, offsetY),
                        onClick = callback,
                        source = this,
                    )
                }
            }
        }
    }

    /** Exports one localized virtual button per action cell plus the center value semantics. */
    override fun collectSemantics(offsetX: Int, offsetY: Int, targets: MutableList<PixelSemanticsTarget>) {
        /** Decrement action is owned by the left virtual node, independent of its geometry. */
        val decreaseActions = PixelSemanticsActions(
            onClick = onDecrease?.takeIf { decreaseEnabled }?.let { callback ->
                {
                    callback()
                    true
                }
            },
        )
        /** Increment action is owned by the right virtual node, independent of its geometry. */
        val increaseActions = PixelSemanticsActions(
            onClick = onIncrease?.takeIf { increaseEnabled }?.let { callback ->
                {
                    callback()
                    true
                }
            },
        )
        visibleRect(leftButtonRect())?.let { visibleBounds ->
            targets += PixelSemanticsTarget(
                node = PixelSemanticsNode(
                    label = decreaseLabel,
                    role = PixelSemanticRole.BUTTON,
                    enabled = decreaseEnabled,
                    focused = false,
                    left = offsetX + visibleBounds.left,
                    top = offsetY + visibleBounds.top,
                    width = visibleBounds.width,
                    height = visibleBounds.height,
                    id = semanticNodeId(DECREASE_SEMANTIC_SLOT),
                    actions = decreaseActions.capabilitySet(),
                ),
                source = this,
                actions = decreaseActions,
            )
        }
        visibleRect(rightButtonRect())?.let { visibleBounds ->
            targets += PixelSemanticsTarget(
                node = PixelSemanticsNode(
                    label = increaseLabel,
                    role = PixelSemanticRole.BUTTON,
                    enabled = increaseEnabled,
                    focused = false,
                    left = offsetX + visibleBounds.left,
                    top = offsetY + visibleBounds.top,
                    width = visibleBounds.width,
                    height = visibleBounds.height,
                    id = semanticNodeId(INCREASE_SEMANTIC_SLOT),
                    actions = increaseActions.capabilitySet(),
                ),
                source = this,
                actions = increaseActions,
            )
        }
        if (layoutValueCellWidth > 0) {
            valueBox?.collectSemantics(offsetX + valueOffsetX, offsetY + valueOffsetY, targets)
        }
    }

    /** Paints one action cell with its enabled or disabled resolved fill. */
    private fun fillButton(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
        buttonX: Int,
        enabled: Boolean,
    ) {
        /** Action rectangle already fitted to the renderer's current layout bounds. */
        val buttonRect = if (buttonX == leftButtonX()) leftButtonRect() else rightButtonRect()
        if (buttonRect.width <= 0 || buttonRect.height <= 0) return
        context.fillRect(
            offsetX + buttonRect.left,
            offsetY + buttonRect.top,
            buttonRect.width,
            buttonRect.height,
            if (enabled) buttonFillColor else disabledFillColor,
        )
    }

    /** Paints one centered minus or plus glyph from the resolved action geometry. */
    private fun drawSymbol(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
        buttonX: Int,
        plus: Boolean,
        enabled: Boolean,
    ) {
        if (layoutButtonWidth <= 0 || innerHeight() <= 0) return
        val color = if (enabled) buttonSymbolColor else disabledSymbolColor
        /** Largest glyph extent fitting both the horizontal action inset and current inner height. */
        val availableSymbolSize = minOf(symbolSize(), innerHeight())
        /** Odd fitted extent prevents either glyph arm from escaping a short constrained control. */
        val symbolSize = if (availableSymbolSize > 1 && availableSymbolSize % 2 == 0) {
            availableSymbolSize - 1
        } else {
            availableSymbolSize
        }
        if (symbolSize <= 0) return
        /** Positive glyph stroke following the outline scale without disappearing at border none. */
        val symbolStroke = layoutBorderWidth.coerceAtLeast(1).coerceAtMost(symbolSize)
        val left = buttonX + ((layoutButtonWidth - symbolSize) / 2)
        val top = layoutBorderWidth + ((innerHeight() - symbolSize) / 2).coerceAtLeast(0)
        val center = symbolSize / 2
        context.fillRect(
            offsetX + left,
            offsetY + top + center,
            symbolSize,
            symbolStroke,
            color,
        )
        if (plus) {
            context.fillRect(
                offsetX + left + center,
                offsetY + top,
                symbolStroke,
                symbolSize,
                color,
            )
        }
    }

    /** Divider width follows the same foundation border channel as the outer outline. */
    private fun dividerWidth(): Int = layoutDividerWidth

    /** Derives a centered odd glyph extent from button, padding, and outline geometry. */
    private fun symbolSize(): Int {
        /** Positive space left after the action cell's visual insets. */
        val available = (
            layoutButtonWidth - valuePadding.left - valuePadding.right - layoutBorderWidth * 2
            ).coerceAtLeast(0)
        if (available == 0) return 0
        return if (available > 1 && available % 2 == 0) available - 1 else available
    }

    /** Paints every border layer with the token-resolved stair-step corner radius. */
    private fun paintValueAdjusterBorder(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
    ) {
        /** Layer count clamped so no outline has empty or inverted geometry. */
        val layers = layoutBorderWidth.coerceIn(0, (minOf(size.width, size.height) + 1) / 2)
        repeat(layers) { layer ->
            /** Current inset outline width after consuming both horizontal edges. */
            val layerWidth = size.width - layer * 2
            /** Current inset outline height after consuming both vertical edges. */
            val layerHeight = size.height - layer * 2
            if (layerWidth <= 0 || layerHeight <= 0) return@repeat
            /** Radius shrunk with the current nested outline layer. */
            val radius = (cornerRadius - layer)
                .coerceIn(0, minOf(layerWidth, layerHeight) / 2)
            if (radius == 0) {
                context.drawRect(
                    x = offsetX + layer,
                    y = offsetY + layer,
                    w = layerWidth,
                    h = layerHeight,
                    color = borderColor,
                )
                return@repeat
            }
            repeat(layerHeight) { row ->
                /** Distance to the nearest horizontal edge of this outline layer. */
                val edgeDistance = minOf(row, layerHeight - 1 - row)
                /** Stair-step inset for the current scan line. */
                val rowInset = (radius - edgeDistance).coerceAtLeast(0)
                /** Inclusive left pixel of the rounded outline scan line. */
                val left = offsetX + layer + rowInset
                /** Inclusive right pixel of the rounded outline scan line. */
                val right = offsetX + layer + layerWidth - rowInset - 1
                if (right < left) return@repeat
                /** Top and bottom scans are continuous; middle scans paint only both sides. */
                if (row == 0 || row == layerHeight - 1) {
                    context.fillRect(left, offsetY + layer + row, right - left + 1, 1, borderColor)
                } else {
                    context.fillRect(left, offsetY + layer + row, 1, 1, borderColor)
                    if (right != left) {
                        context.fillRect(right, offsetY + layer + row, 1, 1, borderColor)
                    }
                }
            }
        }
    }

    /** Fits border, dividers, symmetric action cells, and the value cell into [totalWidth]. */
    private fun resolveHorizontalGeometry(totalWidth: Int, outerBorderWidth: Int) {
        /** Safe outer outline consuming no more than the available horizontal extent. */
        layoutBorderWidth = outerBorderWidth.coerceIn(0, totalWidth.coerceAtLeast(0) / 2)
        /** Width remaining after the two outer border edges. */
        val innerWidth = (totalWidth - layoutBorderWidth * 2).coerceAtLeast(0)
        /** Dividers shrink first while retaining one pixel for each action whenever possible. */
        layoutDividerWidth = minOf(
            borderWidth.coerceAtLeast(0),
            ((innerWidth - 2).coerceAtLeast(0)) / 2,
        )
        /** Cell width after both dividers are removed. */
        val cellWidth = (innerWidth - layoutDividerWidth * 2).coerceAtLeast(0)
        /** Equal action widths consume at most half so the center receives every remainder pixel. */
        layoutButtonWidth = minOf(buttonWidth.coerceAtLeast(0), cellWidth / 2)
        /** Center cell absorbs constrained-width rounding without escaping this renderer. */
        layoutValueCellWidth = (cellWidth - layoutButtonWidth * 2).coerceAtLeast(0)
    }

    /** Removes the resolved horizontal padding from the controlled center-cell width. */
    private fun valueContentWidth(valueCellWidth: Int): Int {
        return (valueCellWidth - valuePadding.left - valuePadding.right).coerceAtLeast(0)
    }

    /** Returns the centered value child's x offset after the leading action and divider. */
    private fun valueContentLeft(valueCellWidth: Int): Int {
        /** Padding cannot move zero-width text beyond the center cell's trailing edge. */
        val fittedLeftPadding = valuePadding.left.coerceIn(0, valueCellWidth.coerceAtLeast(0))
        return layoutBorderWidth +
            layoutButtonWidth +
            dividerWidth() +
            fittedLeftPadding
    }

    /** Returns the leading action cell's x origin inside the outer border. */
    private fun leftButtonX(): Int = layoutBorderWidth

    /** Returns the leading divider's x origin. */
    private fun leftDividerX(): Int = layoutBorderWidth + layoutButtonWidth

    /** Returns the trailing divider's x origin after the controlled center cell. */
    private fun rightDividerX(): Int {
        return layoutBorderWidth +
            layoutButtonWidth +
            dividerWidth() +
            layoutValueCellWidth
    }

    /** Returns the trailing action cell's x origin. */
    private fun rightButtonX(): Int = rightDividerX() + dividerWidth()

    /** Returns the drawable height between the resolved top and bottom borders. */
    private fun innerHeight(): Int = (size.height - (layoutBorderWidth * 2)).coerceAtLeast(0)

    /** Returns the leading action's local hit and semantics rectangle. */
    private fun leftButtonRect(): PixelRect {
        return PixelRect(
            left = leftButtonX(),
            top = layoutBorderWidth,
            width = layoutButtonWidth,
            height = innerHeight(),
        )
    }

    /** Returns the trailing action's local hit and semantics rectangle. */
    private fun rightButtonRect(): PixelRect {
        return PixelRect(
            left = rightButtonX(),
            top = layoutBorderWidth,
            width = layoutButtonWidth,
            height = innerHeight(),
        )
    }

    /** Adjusts inner height so the derived odd-sized symbol remains exactly centered. */
    private fun centeredSymbolExtent(base: Int): Int {
        val symbolSize = symbolSize()
        val safe = base.coerceAtLeast(symbolSize)
        val freeSpace = safe - symbolSize
        return if (freeSpace % 2 == 0) safe else safe + 1
    }

    /** Intersects one local rectangle with this renderer's exact current bounds. */
    private fun visibleRect(rect: PixelRect): PixelRect? {
        return rect.intersect(PixelRect(left = 0, top = 0, width = size.width, height = size.height))
    }

    /** Center value child viewed through the render-box protocol. */
    private val valueBox: RenderBox?
        get() = children.getOrNull(1) as? RenderBox

    /** Zero-sized action identity children kept outside the measured center content. */
    private val actionSlots: List<RenderBox>
        get() = listOfNotNull(children.getOrNull(0) as? RenderBox, children.getOrNull(2) as? RenderBox)

    private companion object {
        /** Stable local identity for the decrement virtual node. */
        const val DECREASE_SEMANTIC_SLOT: String = "decrease"

        /** Stable local identity for the increment virtual node. */
        const val INCREASE_SEMANTIC_SLOT: String = "increase"
    }
}

/**
 * 整数范围步进器。
 *
 * [value]、范围和步长都由调用方传入；组件只在点击时把结果钳位到 [range]。
 * 空范围会禁用两侧按钮，避免把非法边界继续传播给业务状态。
 */
public fun Stepper(
    value: Int,
    range: IntRange,
    onChanged: (Int) -> Unit,
    step: Int = 1,
    label: String? = null,
    valueText: String? = null,
    enabled: Boolean = true,
    valueWidth: Int = 24,
    key: Any? = null,
): Widget = Stepper(
    value = value,
    range = range,
    onChanged = onChanged,
    states = PixelControlStateSet.Normal,
    step = step,
    label = label,
    valueText = valueText,
    enabled = enabled,
    valueWidth = valueWidth,
    key = key,
)

/** 执行 `PixelComponents` 的 `Stepper` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware integer Stepper delegating controlled behavior to [ValueAdjuster].
 */
@kotlin.jvm.JvmName("StepperWithControlStates")
public fun Stepper(
    value: Int,
    range: IntRange,
    onChanged: (Int) -> Unit,
    states: PixelControlStateSet,
    step: Int = 1,
    label: String? = null,
    valueText: String? = null,
    enabled: Boolean = true,
    valueWidth: Int = 24,
    key: Any? = null,
): Widget {
    /** Whether the caller supplied a non-empty inclusive range. */
    val hasRange = range.first <= range.last
    /** Positive step used for both directions. */
    val safeStep = step.coerceAtLeast(1)
    /** Controlled value clamped only while the range is valid. */
    val safeValue = if (hasRange) value.coerceIn(range.first, range.last) else value
    /** Controlled decrement callback available only above the lower bound. */
    val decrease = if (enabled && hasRange && safeValue > range.first) {
        { onChanged((safeValue - safeStep).coerceAtLeast(range.first)) }
    } else {
        null
    }
    /** Controlled increment callback available only below the upper bound. */
    val increase = if (enabled && hasRange && safeValue < range.last) {
        { onChanged((safeValue + safeStep).coerceAtMost(range.last)) }
    } else {
        null
    }
    return PixelThemeResolvedWidget(key = key?.let { "$it-stepper-localization" }) { context, theme ->
        /** Provider-aware integer formatter that never replaces an explicit value string. */
        val localization = pixelComponentLocalizationOf(context, theme)
        /** Caller text wins; only the generated integer value is locale formatted. */
        val resolvedValueText = valueText ?: localization.formatInteger(safeValue)
        ValueAdjuster(
            valueText = resolvedValueText,
            onDecrease = decrease,
            onIncrease = increase,
            states = states,
            label = label,
            enabled = enabled && hasRange,
            valueWidth = valueWidth,
            key = key,
        )
    }
}

/**
 * 像素风快捷键提示。
 *
 * 该组件只渲染快捷键和说明文本，不注册键盘事件；实际处理应放在 [Focus] 的 onKeyEvent
 * 或宿主级快捷键分发中。
 *
 * @param shortcut 键帽显示文本。
 * @param label 键帽右侧的说明文本。
 * @param shortcutStyle 可选键帽文本样式；null 时解析 label typography 与组件前景色。
 * @param labelStyle 可选说明文本样式；null 时解析 caption typography。
 * @param key 视觉边界的稳定 identity。
 */
public fun ShortcutHint(
    shortcut: String,
    label: String,
    shortcutStyle: PixelTextStyle? = null,
    labelStyle: PixelTextStyle? = null,
    key: Any? = null,
): Widget = PixelThemeResolvedWidget(key = key) { _, theme ->
    /** Value-adjuster surface tokens provide the compact key-cap visual channels. */
    val tokens = theme.components.valueAdjuster
    /** Canonical passive state used by the non-interactive hint. */
    val states = PixelControlStateSet.Normal
    /** 调用方省略键帽样式时使用主题 label 排版。 */
    val resolvedShortcutStyle = shortcutStyle ?: theme.typography.label.resolve(theme.colors).copy(
        color = tokens.resolveContentColor(states, theme.colors) ?: theme.colors.onSurface,
    )
    /** 调用方省略说明文本样式时使用主题 caption 排版。 Theme caption typography used when the caller omits an explicit label style. */
    val resolvedLabelStyle = labelStyle ?: theme.typography.caption.resolve(theme.colors)
    Row(
        children = listOf(
            PixelSurface(
                padding = EdgeInsets.all(theme.spacing.small),
                decoration = PixelSurfaceDecoration(
                    fillColor = tokens.resolveContainerColor(states, theme.colors),
                    borderColor = tokens.resolveBorderColor(states, theme.colors),
                    borderWidth = tokens.resolveBorderWidth(theme.borders),
                    cornerRadius = tokens.resolveCornerRadius(theme.radii),
                    shadowColor = theme.colors.shadow,
                    shadowOffset = tokens.resolveElevation(theme.elevations),
                ),
                child = Text(
                    shortcut,
                    style = resolvedShortcutStyle,
                    overflow = PixelTextOverflow.ELLIPSIS,
                    softWrap = false,
                    maxLines = 1,
                    textAlign = TextAlign.CENTER,
                ),
                key = key?.let { "$it-shortcut" },
            ),
            Text(
                label,
                style = resolvedLabelStyle,
                overflow = PixelTextOverflow.ELLIPSIS,
                softWrap = false,
                maxLines = 1,
                key = key?.let { "$it-label" },
            ),
        ),
        spacing = theme.spacing.small,
        crossAxisAlignment = CrossAxisAlignment.CENTER,
        key = key,
    )
}

/**
 * 固定尺寸的水平进度条。
 *
 * [progress] 会在绘制前钳位到 `0f..1f`：NaN 与负无穷归零，正无穷取一。
 * [width] 和 [height] 使用 pixel-engine 的逻辑像素，非正输入安全收敛且不会进入负尺寸布局；
 * 省略时由组件与 foundation 尺寸 token 解析。
 */
public fun ProgressBar(
    progress: Float,
    width: Int? = null,
    height: Int? = null,
    color: PixelColor? = null,
    trackColor: PixelColor? = null,
    key: Any? = null,
): Widget = ProgressBar(
    progress = progress,
    states = PixelControlStateSet.Normal,
    width = width,
    height = height,
    color = color,
    trackColor = trackColor,
    key = key,
)

/** Normalizes malformed determinate input without allowing NaN or infinity into geometry. */
private fun normalizeDeterminateProgress(progress: Float): Float {
    return when {
        progress.isNaN() -> 0f
        progress <= 0f -> 0f
        progress >= 1f -> 1f
        else -> progress
    }
}

/** 执行 `PixelComponents` 的 `ProgressBar` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware determinate progress bar with token-resolved color, size, border, and semantics.
 */
@kotlin.jvm.JvmName("ProgressBarWithControlStates")
public fun ProgressBar(
    progress: Float,
    states: PixelControlStateSet,
    width: Int? = null,
    height: Int? = null,
    color: PixelColor? = null,
    trackColor: PixelColor? = null,
    key: Any? = null,
): Widget = PixelThemeResolvedWidget(key = key) { context, theme ->
    /** Provider-aware progress labels and percentage formatter independent from bar visuals. */
    val localization = pixelComponentLocalizationOf(context, theme)
    /** Progress-specific state and geometry tokens. */
    val tokens = theme.components.progress
    /** Caller or foundation-derived non-negative width before the component minimum is applied. */
    val requestedWidth = (width ?: (theme.sizes.overlayMinimumWidth + theme.spacing.large)).coerceAtLeast(0)
    /** Component-level minimum width, now observable independently from foundation overlay sizes. */
    val tokenMinimumWidth = tokens.resolveMinimumWidth(theme.sizes).coerceAtLeast(0)
    /** Final width honoring both explicit geometry and the component minimum contract. */
    val resolvedWidth = maxOf(requestedWidth, tokenMinimumWidth)
    /** Caller or foundation-derived non-negative height before the component minimum is applied. */
    val requestedHeight = (height ?: theme.borders.thin).coerceAtLeast(0)
    /** Component-level minimum height retained as the lower bound for every facade. */
    val tokenMinimumHeight = tokens.resolveMinimumHeight(theme.sizes).coerceAtLeast(0)
    /** Final height safe for malformed caller dimensions and token-controlled minimum geometry. */
    val resolvedHeight = maxOf(requestedHeight, tokenMinimumHeight)
    /** Finite controlled progress constrained to the visual interval. */
    val safeProgress = normalizeDeterminateProgress(progress)
    /** Filled logical width derived from the controlled progress. */
    val fillWidth = (resolvedWidth * safeProgress).toInt().coerceIn(0, resolvedWidth)
    /** Theme-resolved track color. */
    val resolvedTrackColor = trackColor
        ?: tokens.resolveContainerColor(states, theme.colors)
        ?: theme.colors.track
    /** Theme-resolved active progress color. */
    val resolvedColor = color
        ?: tokens.resolveContentColor(states, theme.colors)
        ?: theme.colors.primary
    /** Fixed boundary preventing Stack from expanding beyond the resolved progress geometry. */
    val bar = SizedBox(
        width = resolvedWidth,
        height = resolvedHeight,
        child = Stack(
            children = listOf(
                PixelSurface(
                    width = resolvedWidth,
                    height = resolvedHeight,
                    decoration = PixelSurfaceDecoration(
                        fillColor = resolvedTrackColor,
                        borderColor = tokens.resolveBorderColor(states, theme.colors),
                        borderWidth = tokens.resolveBorderWidth(theme.borders),
                        cornerRadius = tokens.resolveCornerRadius(theme.radii),
                    ),
                ),
                PixelSurface(
                    width = fillWidth,
                    height = resolvedHeight,
                    decoration = PixelSurfaceDecoration(
                        fillColor = resolvedColor,
                        borderWidth = 0,
                        cornerRadius = tokens.resolveCornerRadius(theme.radii),
                    ),
                ),
            ),
            key = key?.let { "$it-stack" },
        ),
        key = key,
    )
    Semantics(
        label = localization.resolveLabel(
            explicitText = null,
            selector = PixelLabelTokens::progress,
        ),
        role = PixelSemanticRole.PROGRESS_BAR,
        enabled = PixelControlState.Disabled !in states,
        value = localization.formatPercent(safeProgress),
        error = localization.resolveLabel(
            explicitText = null,
            selector = PixelLabelTokens::error,
        ).takeIf { PixelControlState.Error in states },
        excludeDescendants = true,
        child = bar,
        key = key?.let { "$it-semantics" },
    )
}

/**
 * 点阵扫描式水平 Loading 条。
 *
 * [progress] 表示实心扫描块在轨道中的位置，`0f` 在左侧，`1f` 在右侧；[reversed]
 * 会把运动方向翻转，并把点阵残影绘制到运动尾部。组件只绘制当前帧，不创建 ticker。
 * 需要持续播放时使用 [AnimatedPixelLoadingBar]。所有省略的尺寸与颜色由 token 解析。
 */
public fun PixelLoadingBar(
    progress: Float,
    width: Int? = null,
    height: Int? = null,
    color: PixelColor? = null,
    trackColor: PixelColor? = null,
    blockWidth: Int? = null,
    trailWidth: Int? = null,
    reversed: Boolean = false,
    key: Any? = null,
): Widget = buildPixelLoadingBar(
    progress = progress,
    states = PixelControlStateSet.of(PixelControlState.Loading),
    width = width,
    height = height,
    color = color,
    trackColor = trackColor,
    blockWidth = blockWidth,
    trailWidth = trailWidth,
    reversed = reversed,
    key = key,
)

/** 执行 `PixelComponents` 的 `PixelLoadingBar` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware point-grid loading bar with token-resolved colors, geometry, and semantics.
 */
@kotlin.jvm.JvmName("PixelLoadingBarWithControlStates")
public fun PixelLoadingBar(
    progress: Float,
    states: PixelControlStateSet,
    width: Int? = null,
    height: Int? = null,
    color: PixelColor? = null,
    trackColor: PixelColor? = null,
    blockWidth: Int? = null,
    trailWidth: Int? = null,
    reversed: Boolean = false,
    key: Any? = null,
): Widget = buildPixelLoadingBar(
    progress = progress,
    states = states,
    width = width,
    height = height,
    color = color,
    trackColor = trackColor,
    blockWidth = blockWidth,
    trailWidth = trailWidth,
    reversed = reversed,
    key = key,
)

/** 统一解析 Loading 条的尺寸与颜色 token，并附加进度语义。 */
private fun buildPixelLoadingBar(
    progress: Float,
    states: PixelControlStateSet,
    width: Int?,
    height: Int?,
    color: PixelColor?,
    trackColor: PixelColor?,
    blockWidth: Int?,
    trailWidth: Int?,
    reversed: Boolean,
    key: Any?,
): Widget = PixelThemeResolvedWidget(key = key) { context, theme ->
    /** 与加载几何无关的、感知提供者的进度与状态标签。 Provider-aware progress and status labels independent from loading geometry. */
    val localization = pixelComponentLocalizationOf(context, theme)
    /** Loading uses the progress component token family. */
    val tokens = theme.components.progress
    /** Theme-derived concrete loading geometry. */
    val resolvedWidth = width ?: (theme.sizes.overlayMinimumWidth * 2)
    val resolvedHeight = height
        ?: tokens.resolveMinimumHeight(theme.sizes).coerceAtLeast(theme.sizes.iconSmall)
    val resolvedBlockWidth = blockWidth ?: resolvedHeight
    val resolvedTrailWidth = trailWidth ?: theme.spacing.medium
    /** Theme-derived active and background dot colors. */
    val resolvedColor = color
        ?: tokens.resolveContentColor(states, theme.colors)
        ?: theme.colors.onWarning
    val resolvedTrackColor = trackColor
        ?: tokens.resolveContainerColor(states, theme.colors)
        ?: theme.colors.warning
    /** Paint-only loading primitive. */
    val bar = PixelLoadingBarWidget(
        progress = progress,
        width = resolvedWidth,
        height = resolvedHeight,
        color = resolvedColor,
        trackColor = resolvedTrackColor,
        blockWidth = resolvedBlockWidth,
        trailWidth = resolvedTrailWidth,
        reversed = reversed,
        key = key,
    )
    Semantics(
        label = localization.resolveLabel(
            explicitText = null,
            selector = PixelLabelTokens::progress,
        ),
        role = PixelSemanticRole.PROGRESS_BAR,
        enabled = PixelControlState.Disabled !in states,
        value = localization.resolveLabel(
            explicitText = null,
            selector = PixelLabelTokens::loading,
        ),
        error = localization.resolveLabel(
            explicitText = null,
            selector = PixelLabelTokens::error,
        ).takeIf { PixelControlState.Error in states },
        excludeDescendants = true,
        child = bar,
        key = key?.let { "$it-semantics" },
    )
}

/**
 * 自带 ticker 的点阵扫描 Loading 条。
 *
 * 动画采用左右往返运动，扫描块到达边缘后反向，残影也随方向切换。所有省略的尺寸与颜色
 * 由 token 解析。
 */
public fun AnimatedPixelLoadingBar(
    vsync: PixelTickerProvider,
    width: Int? = null,
    height: Int? = null,
    color: PixelColor? = null,
    trackColor: PixelColor? = null,
    blockWidth: Int? = null,
    trailWidth: Int? = null,
    fps: Int = 30,
    cycleFrames: Int = 96,
    playing: Boolean = true,
    key: Any? = null,
): Widget = buildAnimatedPixelLoadingBar(
    vsync = vsync,
    states = PixelControlStateSet.of(PixelControlState.Loading),
    width = width,
    height = height,
    color = color,
    trackColor = trackColor,
    blockWidth = blockWidth,
    trailWidth = trailWidth,
    fps = fps,
    cycleFrames = cycleFrames,
    playing = playing,
    key = key,
)

/** 执行 `PixelComponents` 的 `AnimatedPixelLoadingBar` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware animated loading bar resolving all visual defaults before creating its ticker.
 */
@kotlin.jvm.JvmName("AnimatedPixelLoadingBarWithControlStates")
public fun AnimatedPixelLoadingBar(
    vsync: PixelTickerProvider,
    states: PixelControlStateSet,
    width: Int? = null,
    height: Int? = null,
    color: PixelColor? = null,
    trackColor: PixelColor? = null,
    blockWidth: Int? = null,
    trailWidth: Int? = null,
    fps: Int = 30,
    cycleFrames: Int = 96,
    playing: Boolean = true,
    key: Any? = null,
): Widget = buildAnimatedPixelLoadingBar(
    vsync = vsync,
    states = states,
    width = width,
    height = height,
    color = color,
    trackColor = trackColor,
    blockWidth = blockWidth,
    trailWidth = trailWidth,
    fps = fps,
    cycleFrames = cycleFrames,
    playing = playing,
    key = key,
)

/** 统一解析动画 Loading 条的尺寸与颜色 token，再创建其 ticker。 */
private fun buildAnimatedPixelLoadingBar(
    vsync: PixelTickerProvider,
    states: PixelControlStateSet,
    width: Int?,
    height: Int?,
    color: PixelColor?,
    trackColor: PixelColor?,
    blockWidth: Int?,
    trailWidth: Int?,
    fps: Int,
    cycleFrames: Int,
    playing: Boolean,
    key: Any?,
): Widget = PixelThemeResolvedWidget(key = key) { _, theme ->
    /** Loading uses the progress component token family. */
    val tokens = theme.components.progress
    /** Concrete theme-derived scan geometry. */
    val resolvedWidth = width ?: (theme.sizes.overlayMinimumWidth * 2)
    val resolvedHeight = height
        ?: tokens.resolveMinimumHeight(theme.sizes).coerceAtLeast(theme.sizes.iconSmall)
    /** Concrete theme-derived scan and background-dot colors. */
    val resolvedColor = color
        ?: tokens.resolveContentColor(states, theme.colors)
        ?: theme.colors.onWarning
    val resolvedTrackColor = trackColor
        ?: tokens.resolveContainerColor(states, theme.colors)
        ?: theme.colors.warning
    AnimatedPixelLoadingBarWidget(
        vsync = vsync,
        width = resolvedWidth,
        height = resolvedHeight,
        color = resolvedColor,
        trackColor = resolvedTrackColor,
        blockWidth = blockWidth ?: resolvedHeight,
        trailWidth = trailWidth ?: theme.spacing.medium,
        fps = fps,
        cycleFrames = cycleFrames,
        playing = playing && PixelControlState.Disabled !in states,
        states = states,
        key = key,
    )
}

private data class PixelLoadingBarWidget(
    val progress: Float,
    val width: Int,
    val height: Int,
    val color: PixelColor,
    val trackColor: PixelColor,
    val blockWidth: Int,
    val trailWidth: Int,
    val reversed: Boolean,
    override val key: Any? = null,
) : LeafRenderObjectWidget(key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject =
        RenderPixelLoadingBar(
            progress = progress,
            preferredWidth = width,
            preferredHeight = height,
            color = color,
            trackColor = trackColor,
            blockWidth = blockWidth,
            trailWidth = trailWidth,
            reversed = reversed,
        )

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderPixelLoadingBar).update(
            progress = progress,
            preferredWidth = width,
            preferredHeight = height,
            color = color,
            trackColor = trackColor,
            blockWidth = blockWidth,
            trailWidth = trailWidth,
            reversed = reversed,
        )
    }
}

private class RenderPixelLoadingBar(
    private var progress: Float,
    private var preferredWidth: Int,
    private var preferredHeight: Int,
    private var color: PixelColor,
    private var trackColor: PixelColor,
    private var blockWidth: Int,
    private var trailWidth: Int,
    private var reversed: Boolean,
) : RenderBox() {
    override fun layout(constraints: RenderConstraints) {
        size = RenderSize(
            width = constraints.constrainWidth(preferredWidth.coerceAtLeast(0)),
            height = constraints.constrainHeight(preferredHeight.coerceAtLeast(0)),
        )
    }

    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        val width = size.width
        val height = size.height
        if (width <= 0 || height <= 0) return

        val safeBlockWidth = blockWidth.coerceIn(1, width)
        val travel = (width - safeBlockWidth).coerceAtLeast(0)
        val normalized = progress.coerceIn(0f, 1f)
        val forwardX = (travel * normalized).toInt().coerceIn(0, travel)
        val blockLeft = if (reversed) travel - forwardX else forwardX

        val trailSegmentWidth = trailSegmentWidth(
            blockWidth = safeBlockWidth,
            motionFactor = pixelLoadingMotionFactor(normalized),
        )
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixelColor = loadingPixelColor(
                    x = x,
                    y = y,
                    height = height,
                    blockLeft = blockLeft,
                    blockWidth = safeBlockWidth,
                    trailSegmentWidth = trailSegmentWidth,
                )
                if (pixelColor != null) {
                    context.buffer.setPixel(offsetX + x, offsetY + y, pixelColor)
                }
            }
        }
    }

    fun update(
        progress: Float,
        preferredWidth: Int,
        preferredHeight: Int,
        color: PixelColor,
        trackColor: PixelColor,
        blockWidth: Int,
        trailWidth: Int,
        reversed: Boolean,
    ) {
        val needsLayout = this.preferredWidth != preferredWidth || this.preferredHeight != preferredHeight
        val changed = needsLayout ||
            this.progress != progress ||
            this.color != color ||
            this.trackColor != trackColor ||
            this.blockWidth != blockWidth ||
            this.trailWidth != trailWidth ||
            this.reversed != reversed
        if (!changed) return
        this.progress = progress
        this.preferredWidth = preferredWidth
        this.preferredHeight = preferredHeight
        this.color = color
        this.trackColor = trackColor
        this.blockWidth = blockWidth
        this.trailWidth = trailWidth
        this.reversed = reversed
        if (needsLayout) markNeedsLayout()
        markNeedsPaint()
    }

    private fun loadingPixelColor(
        x: Int,
        y: Int,
        height: Int,
        blockLeft: Int,
        blockWidth: Int,
        trailSegmentWidth: Int,
    ): PixelColor? {
        if (x in blockLeft until blockLeft + blockWidth) return color

        val trailDistance = wakeDistance(blockLeft, blockWidth, x)
        if (trailDistance != null && trailSegmentWidth > 0) {
            if (trailDistance in 1..trailSegmentWidth) {
                val gridX = trailDistance - 1
                return if (!wakeGridHasDot(gridX, y, height)) color else null
            }

            val farSegmentStart = trailSegmentWidth + 1
            val farSegmentEnd = trailSegmentWidth * 2
            if (trailDistance in farSegmentStart..farSegmentEnd) {
                val gridX = trailDistance - 2
                return if (wakeGridHasDot(gridX, y, height)) color else null
            }
        }

        return if (backgroundGridHasDot(x, y, height)) trackColor else null
    }

    private fun trailSegmentWidth(
        blockWidth: Int,
        motionFactor: Float,
    ): Int {
        val baseTrailWidth = trailWidth.coerceAtLeast(0)
        if (baseTrailWidth == 0) return 0
        val safeMotionFactor = motionFactor.coerceIn(0f, 1f)
        if (safeMotionFactor <= 0.001f) return 0
        val maxSegmentWidth = (
            blockWidth * PIXEL_LOADING_TRAIL_SEGMENT_MAX_NUMERATOR /
                PIXEL_LOADING_TRAIL_SEGMENT_MAX_DENOMINATOR
            ).coerceAtLeast(1)
        val segmentWidth = minOf(baseTrailWidth, maxSegmentWidth)
        return (segmentWidth * safeMotionFactor).roundToInt().coerceAtLeast(0)
    }

    private fun wakeGridHasDot(gridX: Int, y: Int, height: Int): Boolean {
        val row = loadingGridRow(y, height) ?: return false
        val offset = row.floorMod(PIXEL_LOADING_WAKE_DOT_STEP_X)
        return (gridX + offset).floorMod(PIXEL_LOADING_WAKE_DOT_STEP_X) == 0
    }

    private fun backgroundGridHasDot(gridX: Int, y: Int, height: Int): Boolean {
        val row = loadingGridRow(y, height) ?: return false
        val offset = row.floorMod(2) * (PIXEL_LOADING_BACKGROUND_DOT_STEP_X / 2)
        return (gridX + offset).floorMod(PIXEL_LOADING_BACKGROUND_DOT_STEP_X) == 0
    }

    private fun loadingGridRow(y: Int, height: Int): Int? {
        if (height <= 1) return 0
        val mirroredY = minOf(y, height - 1 - y)
        if (mirroredY.floorMod(PIXEL_LOADING_DOT_STEP_Y) != 0) {
            return null
        }
        return mirroredY / PIXEL_LOADING_DOT_STEP_Y
    }

    private fun wakeDistance(blockLeft: Int, blockWidth: Int, x: Int): Int? {
        val distance = if (reversed) x - (blockLeft + blockWidth - 1) else blockLeft - x
        return if (distance > 0) distance else null
    }
}

/** Retained ticker-backed loading bar with already-resolved theme values. */
private data class AnimatedPixelLoadingBarWidget(
    /** Shared caller-provided ticker source. */
    val vsync: PixelTickerProvider,
    /** Concrete visual width. */
    val width: Int,
    /** Concrete visual height. */
    val height: Int,
    /** Concrete scan color. */
    val color: PixelColor,
    /** Concrete background-dot color. */
    val trackColor: PixelColor,
    /** Concrete scan-block width. */
    val blockWidth: Int,
    /** Concrete wake width. */
    val trailWidth: Int,
    /** Requested logical animation frame rate. */
    val fps: Int,
    /** Frames in one complete bidirectional cycle. */
    val cycleFrames: Int,
    /** Whether ticker advancement is currently enabled. */
    val playing: Boolean,
    /** Persistent normalized visual and capability states. */
    val states: PixelControlStateSet,
    /** Stable retained ticker and visual identity. */
    override val key: Any?,
) : StatefulWidget(key = key) {
    init {
        require(fps > 0) { "fps must be > 0, got $fps" }
        require(cycleFrames > 1) { "cycleFrames must be > 1, got $cycleFrames" }
    }

    override fun createState(): State<out StatefulWidget> = AnimatedPixelLoadingBarState()
}

/** Ticker ownership and deterministic frame advancement for AnimatedPixelLoadingBar. */
private class AnimatedPixelLoadingBarState : State<AnimatedPixelLoadingBarWidget>() {
    /** Owned ticker, created from the latest caller provider. */
    private var ticker: PixelTicker? = null
    /** Current frame within the complete bidirectional cycle. */
    private var currentFrame = 0
    /** Last observed ticker elapsed time, or -1 before anchoring. */
    private var lastElapsedNanos = -1L
    /** Sub-frame nanoseconds carried into the next callback. */
    private var carryNanos = 0L

    override fun initState() {
        createTicker()
        syncPlaying()
    }

    override fun didUpdateWidget(oldWidget: AnimatedPixelLoadingBarWidget) {
        if (widget.fps != oldWidget.fps || widget.vsync !== oldWidget.vsync) {
            ticker?.dispose()
            ticker = null
            lastElapsedNanos = -1L
            carryNanos = 0L
            createTicker()
        }
        if (widget.cycleFrames != oldWidget.cycleFrames) {
            currentFrame = currentFrame.floorMod(widget.cycleFrames)
        }
        syncPlaying()
    }

    override fun dispose() {
        ticker?.dispose()
    }

    override fun build(context: BuildContext): Widget {
        val halfCycle = widget.cycleFrames / 2
        val frame = currentFrame.floorMod(widget.cycleFrames)
        val reversed = frame >= halfCycle
        val localFrame = if (reversed) frame - halfCycle else frame
        val localSpan = (if (reversed) widget.cycleFrames - halfCycle else halfCycle)
            .coerceAtLeast(1)
        val progress = pixelLoadingPositionCurve(localFrame.toFloat() / (localSpan - 1).coerceAtLeast(1).toFloat())
        return PixelLoadingBar(
            progress = progress,
            states = widget.states,
            width = widget.width,
            height = widget.height,
            color = widget.color,
            trackColor = widget.trackColor,
            blockWidth = widget.blockWidth,
            trailWidth = widget.trailWidth,
            reversed = reversed,
            key = widget.key?.let { "$it-bar" },
        )
    }

    private fun createTicker() {
        ticker = widget.vsync.createTicker { elapsedNanos ->
            if (!widget.playing) return@createTicker
            val delta = if (lastElapsedNanos < 0L) 0L else elapsedNanos - lastElapsedNanos
            lastElapsedNanos = elapsedNanos
            if (delta <= 0L) return@createTicker
            advance(delta)
        }
    }

    private fun advance(deltaNanos: Long) {
        val frameNanos = 1_000_000_000L / widget.fps
        carryNanos += deltaNanos
        var advanced = false
        while (carryNanos >= frameNanos) {
            carryNanos -= frameNanos
            currentFrame = (currentFrame + 1).floorMod(widget.cycleFrames)
            advanced = true
        }
        if (advanced) {
            setState { }
        }
    }

    private fun syncPlaying() {
        val activeTicker = ticker ?: return
        if (widget.playing) {
            activeTicker.start()
        } else {
            activeTicker.stop()
            lastElapsedNanos = -1L
        }
    }
}

/**
 * 由调用方驱动帧序号的四点加载指示器。
 *
 * 组件不会自己创建 ticker；调用方可通过动画控制器、定时器或测试里的 `pumpFrame`
 * 递增 [frame]，当前高亮点由 `frame % 4` 决定。
 */
public fun ActivityIndicator(
    frame: Int = 0,
    color: PixelColor = PixelColor.White,
    key: Any? = null,
): Widget = ActivityIndicator(
    states = PixelControlStateSet.of(PixelControlState.Loading),
    frame = frame,
    color = color.takeUnless { value -> value == PixelColor.White },
    key = key,
)

/** 执行 `PixelComponents` 的 `ActivityIndicator` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware activity indicator with token-resolved colors, geometry, and progress semantics.
 */
@kotlin.jvm.JvmName("ActivityIndicatorWithControlStates")
public fun ActivityIndicator(
    states: PixelControlStateSet,
    frame: Int = 0,
    color: PixelColor? = null,
    key: Any? = null,
): Widget = PixelThemeResolvedWidget(key = key) { context, theme ->
    /** Provider-aware progress and status labels independent from indicator geometry. */
    val localization = pixelComponentLocalizationOf(context, theme)
    /** Progress token family shared by all activity indicators. */
    val tokens = theme.components.progress
    /** Concrete active dot color. */
    val activeColor = color
        ?: tokens.resolveContentColor(states, theme.colors)
        ?: theme.colors.onWarning
    /** Concrete inactive dot color. */
    val inactiveColor = tokens.resolveContainerColor(states, theme.colors) ?: theme.colors.track
    /** Dot extent scales with the standard medium-icon envelope. */
    val dotExtent = (theme.sizes.iconMedium / 4).coerceAtLeast(theme.borders.thin)
    /** Four deterministic loading dots. */
    val dots = List(4) { index ->
        PixelSurface(
            width = dotExtent,
            height = dotExtent,
            decoration = PixelSurfaceDecoration(
                fillColor = if (index == frame.floorMod(4)) activeColor else inactiveColor,
                borderWidth = 0,
                cornerRadius = tokens.resolveCornerRadius(theme.radii),
            ),
        )
    }
    /** Natural four-dot row width before applying the large-icon envelope floor. */
    val naturalWidth = dotExtent * 4 + theme.spacing.extraSmall * 3
    /** Large-icon envelope keeps activity indicators aligned with adjacent icon slots. */
    val indicatorWidth = maxOf(theme.sizes.iconLarge, naturalWidth)
    Semantics(
        label = localization.resolveLabel(
            explicitText = null,
            selector = PixelLabelTokens::progress,
        ),
        role = PixelSemanticRole.PROGRESS_BAR,
        enabled = PixelControlState.Disabled !in states,
        value = localization.resolveLabel(
            explicitText = null,
            selector = PixelLabelTokens::loading,
        ),
        error = localization.resolveLabel(
            explicitText = null,
            selector = PixelLabelTokens::error,
        ).takeIf { PixelControlState.Error in states },
        excludeDescendants = true,
        child = SizedBox(
            width = indicatorWidth,
            child = Row(children = dots, spacing = theme.spacing.extraSmall, key = key),
        ),
        key = key?.let { "$it-semantics" },
    )
}

/**
 * 按 [PixelAsyncSnapshot] 呈现 loading / empty / error / content。
 *
 * 组件不发起请求、不订阅 source，也不保存数据状态；调用方负责持有 snapshot。
 * [isEmpty] 只在 [PixelAsyncSnapshot.Success] 时调用，用于把空列表等成功结果映射为空状态。
 */
public fun <T> LoadStateView(
    snapshot: PixelAsyncSnapshot<T>,
    content: (T) -> Widget,
    isEmpty: (T) -> Boolean = { false },
    loading: Widget = Center(child = ActivityIndicator()),
    empty: Widget = EmptyState(title = "EMPTY"),
    error: (Throwable) -> Widget = { throwable ->
        PixelErrorPanel(message = throwable.message ?: throwable::class.simpleName.orEmpty())
    },
): Widget {
    return when (snapshot) {
        PixelAsyncSnapshot.Loading -> loading
        is PixelAsyncSnapshot.Failure -> error(snapshot.error)
        is PixelAsyncSnapshot.Success -> {
            if (isEmpty(snapshot.value)) empty else content(snapshot.value)
        }
    }
}

/**
 * 执行 `PixelComponents` 的 `LoadStateView` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware async snapshot presenter that propagates Loading, Error, and Disabled states.
 *
 * Caller-supplied [loading], [empty], and [error] widgets retain full precedence. Null defaults are
 * resolved through the current theme-aware standard components.
 */
@kotlin.jvm.JvmName("LoadStateViewWithControlStates")
public fun <T> LoadStateView(
    snapshot: PixelAsyncSnapshot<T>,
    states: PixelControlStateSet,
    content: (T) -> Widget,
    isEmpty: (T) -> Boolean = { false },
    loading: Widget? = null,
    empty: Widget? = null,
    error: ((Throwable) -> Widget)? = null,
): Widget {
    return when (snapshot) {
        PixelAsyncSnapshot.Loading -> loading ?: Center(
            child = ActivityIndicator(states = states + PixelControlState.Loading),
        )
        is PixelAsyncSnapshot.Failure -> error?.invoke(snapshot.error) ?: EmptyState(
            states = states + PixelControlState.Error,
            title = snapshot.error.message ?: snapshot.error::class.simpleName.orEmpty(),
        )
        is PixelAsyncSnapshot.Success -> {
            if (isEmpty(snapshot.value)) {
                empty ?: EmptyState(states = states)
            } else {
                content(snapshot.value)
            }
        }
    }
}

/**
 * 居中的像素空状态。
 *
 * 该组件只负责把标题、说明、图标和操作按钮排成紧凑像素布局；空数据判断、加载状态、
 * 重试动作和路由跳转都由调用方维护。
 *
 * @param title 可选标题；null 时解析主题 label token。
 * @param message 可选说明文本。
 * @param icon 可选图标 widget。
 * @param action 可选操作 widget。
 * @param width 可选内容固定宽度。
 * @param titleStyle 可选标题样式；null 时解析 title typography。
 * @param messageStyle 可选说明样式；null 时解析 caption typography。
 * @param key 视觉与语义边界共用的稳定 identity。
 */
public fun EmptyState(
    title: String? = null,
    message: String? = null,
    icon: Widget? = null,
    action: Widget? = null,
    width: Int? = null,
    titleStyle: PixelTextStyle? = null,
    messageStyle: PixelTextStyle? = null,
    key: Any? = null,
): Widget = EmptyState(
    states = PixelControlStateSet.Normal,
    title = title,
    message = message,
    icon = icon,
    action = action,
    width = width,
    titleStyle = titleStyle,
    messageStyle = messageStyle,
    key = key,
)

/** 执行 `PixelComponents` 的 `EmptyState` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware EmptyState with token-resolved labels, typography, spacing, and semantics.
 */
@kotlin.jvm.JvmName("EmptyStateWithControlStates")
public fun EmptyState(
    states: PixelControlStateSet,
    title: String? = null,
    message: String? = null,
    icon: Widget? = null,
    action: Widget? = null,
    width: Int? = null,
    titleStyle: PixelTextStyle? = null,
    messageStyle: PixelTextStyle? = null,
    key: Any? = null,
): Widget = PixelThemeResolvedWidget(key = key) { context, theme ->
    /** Provider-aware empty and status labels independent from EmptyState visuals. */
    val localization = pixelComponentLocalizationOf(context, theme)
    /** Localized fallback title. */
    val resolvedTitle = localization.resolveLabel(
        explicitText = title,
        selector = PixelLabelTokens::empty,
    )
    /** Theme title typography unless explicitly overridden. */
    val resolvedTitleStyle = titleStyle ?: theme.typography.title.resolve(theme.colors)
    /** Theme caption typography unless explicitly overridden. */
    val resolvedMessageStyle = messageStyle ?: theme.typography.caption.resolve(theme.colors)
    /** Compact empty-state children in visual order. */
    val children = buildList {
        if (icon != null) add(icon)
        add(
            Text(
                resolvedTitle,
                style = resolvedTitleStyle,
                textAlign = TextAlign.CENTER,
                softWrap = true,
                maxLines = 2,
                overflow = PixelTextOverflow.ELLIPSIS,
            ),
        )
        if (!message.isNullOrBlank()) {
            add(
                Text(
                    message,
                    style = resolvedMessageStyle,
                    textAlign = TextAlign.CENTER,
                    softWrap = true,
                    maxLines = 3,
                    overflow = PixelTextOverflow.ELLIPSIS,
                ),
            )
        }
        if (action != null) add(action)
    }
    val content = Column(
        children = children,
        spacing = theme.spacing.extraSmall,
        mainAxisSize = MainAxisSize.MIN,
        crossAxisAlignment = CrossAxisAlignment.CENTER,
    )
    Semantics(
        label = resolvedTitle,
        role = PixelSemanticRole.GENERIC,
        enabled = PixelControlState.Disabled !in states,
        value = localization.resolveLabel(
            explicitText = null,
            selector = PixelLabelTokens::loading,
        ).takeIf { PixelControlState.Loading in states },
        error = localization.resolveLabel(
            explicitText = null,
            selector = PixelLabelTokens::error,
        ).takeIf { PixelControlState.Error in states },
        mergeDescendants = false,
        child = Center(
            child = if (width == null) {
                content
            } else {
                SizedBox(width = width.coerceAtLeast(1), child = content)
            },
        ),
        key = key,
    )
}

/**
 * 创建 `Badge` retained widget；简洁入口委托到状态化实现并默认使用 Error 状态角色。
 */
public fun Badge(
    child: Widget,
    label: Widget,
    key: Any? = null,
): Widget = Badge(
    child = child,
    label = label,
    states = PixelControlStateSet.of(PixelControlState.Error),
    key = key,
)

/** 执行 `PixelComponents` 的 `Badge` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware Badge using notification and foundation tokens for its compact status surface.
 */
@kotlin.jvm.JvmName("BadgeWithControlStates")
public fun Badge(
    child: Widget,
    label: Widget,
    states: PixelControlStateSet,
    key: Any? = null,
): Widget = PixelThemeResolvedWidget(key = key) { _, theme ->
    /** Notification tokens provide compact status geometry. */
    val tokens = theme.components.toast
    Stack(
        children = listOf(
            child,
            Positioned(
                top = 0,
                right = 0,
                child = PixelSurface(
                    padding = EdgeInsets.symmetric(horizontal = theme.spacing.extraSmall, vertical = 0),
                    decoration = PixelSurfaceDecoration(
                        fillColor = tokens.resolveContainerColor(states, theme.colors)
                            ?: theme.colors.surface,
                        borderColor = tokens.resolveBorderColor(states, theme.colors),
                        borderWidth = tokens.resolveBorderWidth(theme.borders),
                        cornerRadius = tokens.resolveCornerRadius(theme.radii),
                        shadowColor = theme.colors.shadow,
                        shadowOffset = tokens.resolveElevation(theme.elevations),
                    ),
                    child = label,
                ),
            ),
        ),
        key = key,
    )
}

/**
 * 创建 `Divider` retained widget；简洁入口委托到状态化实现。
 *
 * @param color 可选分割线颜色；null 时解析 outline 语义角色。
 * @param thickness 可选厚度；null 时解析 foundation 边框刻度。
 * @param key 视觉边界的稳定 identity。
 */
public fun Divider(
    color: PixelColor? = null,
    thickness: Int? = null,
    key: Any? = null,
): Widget = Divider(
    states = PixelControlStateSet.Normal,
    color = color,
    thickness = thickness,
    key = key,
)

/** 执行 `PixelComponents` 的 `Divider` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware divider resolving outline color and thickness from foundation tokens.
 */
@kotlin.jvm.JvmName("DividerWithControlStates")
public fun Divider(
    states: PixelControlStateSet,
    color: PixelColor? = null,
    thickness: Int? = null,
    key: Any? = null,
): Widget = PixelThemeResolvedWidget(key = key) { _, theme ->
    /** State-aware color falls back to disabled when capability is unavailable. */
    val resolvedColor = color ?: if (PixelControlState.Disabled in states) {
        theme.colors.disabled
    } else {
        theme.colors.outline
    }
    /** 调用方省略厚度时使用的 foundation 边框宽度。 Foundation border width used when the caller omits an explicit thickness. */
    val resolvedThickness = thickness ?: theme.borders.thin
    PixelSurface(
        height = resolvedThickness.coerceAtLeast(1),
        decoration = PixelSurfaceDecoration(fillColor = resolvedColor, borderWidth = 0),
        key = key,
    )
}

/** 创建 `Gap` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun Gap(
    width: Int = 0,
    height: Int = 0,
    key: Any? = null,
): Widget = SizedBox(width = width, height = height, key = key)

/**
 * 简单的像素页面骨架。
 *
 * [title] 会渲染为顶部描边区域，[body] 占据剩余空间，[bottomBar] 固定在底部。该组件不提供
 * navigator、系统 inset、overlay 或 Material 风格 app bar；这些能力由宿主或更高层组件组合。
 */
public fun AppScaffold(
    title: Widget? = null,
    body: Widget,
    bottomBar: Widget? = null,
    key: Any? = null,
): Widget = AppScaffold(
    body = body,
    states = PixelControlStateSet.Normal,
    title = title,
    bottomBar = bottomBar,
    key = key,
)

/** 执行 `PixelComponents` 的 `AppScaffold` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware page scaffold whose chrome consumes list-surface and foundation spacing tokens.
 */
@kotlin.jvm.JvmName("AppScaffoldWithControlStates")
public fun AppScaffold(
    body: Widget,
    states: PixelControlStateSet,
    title: Widget? = null,
    bottomBar: Widget? = null,
    key: Any? = null,
): Widget = PixelThemeResolvedWidget(key = key) { _, theme ->
    /** List-style surface tokens used by the compact top chrome. */
    val tokens = theme.components.listTile
    /** Scaffold vertical children in visual order. */
    val children = buildList {
        if (title != null) {
            add(
                PixelSurface(
                    padding = tokens.resolvePadding(theme.spacing),
                    decoration = PixelSurfaceDecoration(
                        fillColor = tokens.resolveContainerColor(states, theme.colors),
                        borderColor = tokens.resolveBorderColor(states, theme.colors),
                        borderWidth = tokens.resolveBorderWidth(theme.borders),
                        cornerRadius = tokens.resolveCornerRadius(theme.radii),
                    ),
                    child = title,
                ),
            )
            add(Gap(height = theme.spacing.extraSmall))
        }
        add(Expanded(child = body))
        if (bottomBar != null) {
            add(Gap(height = theme.spacing.extraSmall))
            add(bottomBar)
        }
    }
    Column(
        children = children,
        mainAxisSize = MainAxisSize.MAX,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
        key = key,
    )
}

private fun Int.floorMod(divisor: Int): Int {
    val remainder = this % divisor
    return if (remainder < 0) remainder + divisor else remainder
}

private fun pixelLoadingPositionCurve(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * x * (x * (x * 6f - 15f) + 10f)
}

private fun pixelLoadingMotionFactor(progress: Float): Float {
    return sin(progress.coerceIn(0f, 1f) * PI).toFloat().coerceIn(0f, 1f)
}

private const val PIXEL_LOADING_BACKGROUND_DOT_STEP_X = 4
private const val PIXEL_LOADING_DOT_STEP_Y = 2
private const val PIXEL_LOADING_TRAIL_SEGMENT_MAX_NUMERATOR = 2
private const val PIXEL_LOADING_TRAIL_SEGMENT_MAX_DENOMINATOR = 3
private const val PIXEL_LOADING_WAKE_DOT_STEP_X = 2

/**
 * 需要解析主题 token 的公开组件函数共用的 retained build-context 桥接。
 *
 * Retained build-context bridge for public component functions that must resolve theme tokens.
 *
 * 桥接不缓存具体主题值；[PixelTheme.of] 的依赖追踪会在最近的主题提供者变化时精确重建该子树。
 */
private data class PixelThemeResolvedWidget(
    /** Stable retained identity supplied by the public component. */
    override val key: Any?,
    /** Component-specific resolver receiving the current context and complete theme graph. */
    val resolver: (BuildContext, PixelThemeTokens) -> Widget,
) : StatelessWidget(key = key) {
    /** Resolves the latest inherited theme and delegates construction to [resolver]. */
    override fun build(context: BuildContext): Widget {
        /** Complete token graph from the nearest PixelTheme provider. */
        val theme = PixelTheme.of(context)
        return resolver(context, theme)
    }
}

/**
 * 本文件生产组件共用的、绑定 build context 的本地化输入。
 *
 * 可选提供者与主题解析相互独立，因此挂载本地化不会改变任何组件的视觉 token 解析。主题 label
 * 作为中间兜底，不可变英文包提供最终确定性兜底与数字格式化。
 */
private data class PixelComponentLocalization(
    /** 显式安装的本地化包；未挂载提供者时为 null。 Explicitly installed localization bundle, or null when no provider is mounted. */
    private val providerBundle: PixelLocalizationBundle?,
    /** Current theme labels used after an absent provider. */
    private val themeLabels: PixelLabelTokens,
) {
    /** Whether an application explicitly installed a localization provider. */
    val hasProvider: Boolean
        get() = providerBundle != null

    /**
     * Resolves one optional caller label through provider, theme, and English precedence.
     *
     * Component overloads allow an explicitly supplied blank label. That exact caller value
     * therefore returns before the stricter additive resolver is invoked. Blank provider and theme
     * values remain impossible through their validated public constructors.
     */
    fun resolveLabel(
        /** Caller-authored text, or null when the component default was omitted. */
        explicitText: String?,
        /** Label-token property selecting the same semantic role at every fallback layer. */
        selector: (PixelLabelTokens) -> String,
    ): String {
        if (explicitText != null && explicitText.isBlank()) return explicitText
        return PixelLocalizationResolver.resolveText(
            explicitText = explicitText,
            providerText = providerBundle?.labels?.let(selector),
            themeText = selector(themeLabels),
            englishFallback = selector(PixelLabelTokens.Default),
        )
    }

    /** Formats a normalized progress fraction through the provider or deterministic English. */
    fun formatPercent(fraction: Float): String {
        return (providerBundle ?: PixelLocalizationBundle.English).formatPercent(fraction)
    }

    /** Formats an integer through the provider or deterministic English. */
    fun formatInteger(value: Int): String {
        return (providerBundle ?: PixelLocalizationBundle.English).formatInteger(value)
    }
}

/** Captures the nearest explicit localization bundle without altering current theme selection. */
private fun pixelComponentLocalizationOf(
    context: BuildContext,
    theme: PixelThemeTokens,
): PixelComponentLocalization {
    return PixelComponentLocalization(
        providerBundle = PixelLocalizations.maybeOf(context),
        themeLabels = theme.labels,
    )
}

/** Stable resolver identity that replaces modal wrappers when their isolation mode changes. */
private data class PixelThemeResolverKey(
    /** Public component identity retained inside one isolation mode. */
    val ownerKey: Any?,
    /** Component family separating otherwise equal public keys. */
    val component: String,
    /** Mode bit whose change must dispose the previous modal boundary immediately. */
    val mode: Boolean,
)

private fun snackbarText(
    message: String,
    textStyle: PixelTextStyle,
): Widget = Semantics(
    label = message,
    role = PixelSemanticRole.GENERIC,
    liveRegion = PixelSemanticsLiveRegion.POLITE,
    excludeDescendants = true,
    child = Text(
        message,
        style = textStyle,
        softWrap = true,
        maxLines = 2,
        overflow = PixelTextOverflow.ELLIPSIS,
    ),
)

/**
 * 标准叶子控件和复合选择器条目共用的焦点与语义外壳。
 *
 * 可选集合字段默认值为 null，使已有控件保留原语义树；RadioGroup、Tabs 和
 * SegmentedControl 则可以发布结构化条目元数据。该类型仅用于 Pixel SDK 兄弟 artifact
 * 之间互操作，不属于消费者稳定 API。
 */
@PixelArtifactInternalApi
public data class FocusableControl(
    /** 与动态值及校验状态相互独立的控件朗读标签。 */
    public val label: String,
    /** 该复合控件导出的平台语义角色。 */
    public val role: PixelSemanticRole,
    /** 被焦点与语义边界包裹的可视子树。 */
    public val child: Widget,
    /** 控件是否仍可获得焦点所有权并参与焦点遍历。 */
    public val enabled: Boolean,
    /** 语义是否独立于焦点资格报告可变更能力。 */
    public val semanticsEnabled: Boolean = enabled,
    /** 该叶子是否拥有自动焦点边界，而不是复用复合父控件的边界。 */
    public val automaticallyFocusable: Boolean = true,
    /** 继承的父控件获得焦点时是否同时聚焦该语义子项。 */
    public val focusWhenParentFocused: Boolean = true,
    /** 与朗读标签相互独立的结构化选中状态。 */
    public val selected: Boolean = false,
    /** 结构化勾选状态；不可勾选的控件使用 null。 */
    public val checked: Boolean? = null,
    /** 结构化展开状态；不适用时使用 null。 */
    public val expanded: Boolean? = null,
    /** 可选动态值，包括本地化的加载状态。 */
    public val value: String? = null,
    /** 与 [label] 分开播报的可选校验错误。 */
    public val error: String? = null,
    /** 与 [label]、[value] 分开播报的可选使用提示。 */
    public val hint: String? = null,
    /** 当该边界拥有复合选择器时使用的可选集合元数据。 */
    public val collectionInfo: PixelSemanticsCollectionInfo? = null,
    /** 当该边界表示集合条目时使用的可选位置元数据。 */
    public val collectionItemInfo: PixelSemanticsCollectionItemInfo? = null,
    /** 不会覆盖选中或错误视觉的叠加焦点几何参数。 */
    public val focusIndicator: PixelFocusIndicatorTokens = PixelFocusIndicatorTokens.Default,
    /** 由该控件边界拥有的类型化语义回调。 */
    public val actions: PixelSemanticsActions = PixelSemanticsActions(),
    /** 在通用激活兜底逻辑前执行的可选专用按键行为。 */
    public val onKeyEvent: ((PixelKeyEvent) -> Boolean)? = null,
    /** retained 控件、焦点与语义边界共用的稳定标识。 */
    public override val key: Any? = null,
) : StatelessWidget(key = key) {
    /** 为普通叶子添加 retained 焦点节点，并允许复合控件显式退出。 */
    public override fun build(context: BuildContext): Widget {
        if (!automaticallyFocusable) return buildControl(context)
        /** 从精确语义点击动作派生的 Enter/Space 兜底处理器。 */
        val activationHandler = actions.onClick?.let(::activationKeyHandler)
        /** 专用组件按键的优先级高于通用激活动作。 */
        val specializedHandler = onKeyEvent
        /** 安装在调用方自定义 Focus 快捷键之后的组件默认处理器。 */
        val componentHandler: ((PixelKeyEvent) -> Boolean)? = when {
            specializedHandler == null -> activationHandler
            activationHandler == null -> specializedHandler
            else -> { event -> specializedHandler.invoke(event) || activationHandler.invoke(event) }
        }
        return AutomaticFocusAction(
            enabled = enabled,
            debugLabel = label,
            onKeyEvent = componentHandler,
            key = key,
        ) { focusedContext, _ ->
            buildControl(focusedContext)
        }
    }

    /** 基于最终继承的焦点节点构建视觉与语义边界。 */
    private fun buildControl(context: BuildContext): Widget {
        val focusNode = context.getInheritedWidgetOfExactType<FocusNodeScope>()?.node
        if (focusNode != null) {
            context.watch(focusNode)
        }
        val focused = enabled && focusWhenParentFocused && focusNode?.isFocused == true
        /** 用于解析独立焦点指示器的完整 token 图。 */
        val theme = PixelTheme.of(context)
        /** 仅用于传递独立焦点规格的最小组件包装。 */
        val focusComponentTokens = PixelComponentColorTokens(focusIndicator = focusIndicator)
        /** 保持子项尺寸和全部目标边界不变的紧缩包裹纯绘制焦点层。 */
        val highlighted = withControlFocusIndicator(
            child = child,
            states = if (focused) {
                PixelControlStateSet.of(PixelControlState.Focused)
            } else {
                PixelControlStateSet.Normal
            },
            componentTokens = focusComponentTokens,
            colors = theme.colors,
            borders = theme.borders,
            key = key?.let { "$it-focus-indicator" },
        )
        return Semantics(
            label = label,
            role = role,
            enabled = semanticsEnabled,
            focused = focused,
            value = value,
            hint = hint,
            error = error,
            selected = selected,
            checked = checked,
            expanded = expanded,
            collectionInfo = collectionInfo,
            collectionItemInfo = collectionItemInfo,
            excludeDescendants = true,
            actions = actions,
            child = highlighted,
            key = key?.let { "$it-semantics" },
        )
    }
}

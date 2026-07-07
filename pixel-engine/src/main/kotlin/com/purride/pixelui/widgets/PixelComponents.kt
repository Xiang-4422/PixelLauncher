package com.purride.pixelui

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.animation.PixelTicker
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.internal.HitTestResult
import com.purride.pixelui.internal.LeafRenderObjectWidget
import com.purride.pixelui.internal.MultiChildRenderObject
import com.purride.pixelui.internal.MultiChildRenderObjectWidget
import com.purride.pixelui.internal.PaintContext
import com.purride.pixelui.internal.PixelClickTarget
import com.purride.pixelui.internal.PixelRect
import com.purride.pixelui.internal.PixelSemanticsTarget
import com.purride.pixelui.internal.RenderBox
import com.purride.pixelui.internal.RenderConstraints
import com.purride.pixelui.internal.RenderObject
import com.purride.pixelui.internal.RenderSize
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

public data class PixelIconData(
    val bitmap: PixelBitmap,
)

public fun Icon(
    icon: PixelIconData,
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

public fun ListTile(
    title: Widget,
    subtitle: Widget? = null,
    leading: Widget? = null,
    trailing: Widget? = null,
    onTap: (() -> Unit)? = null,
    enabled: Boolean = true,
    semanticLabel: String = "ListTile",
    key: Any? = null,
): Widget {
    val texts = if (subtitle == null) title else Column(children = listOf(title, subtitle), spacing = 1)
    val rowChildren = buildList {
        if (leading != null) add(leading)
        add(Expanded(child = texts))
        if (trailing != null) add(trailing)
    }
    val content = Container(
        child = Row(children = rowChildren, spacing = 2, crossAxisAlignment = CrossAxisAlignment.CENTER),
        padding = EdgeInsets.symmetric(horizontal = 2, vertical = TEXT_CONTAINER_PADDING_PX),
        fillColor = if (enabled) null else PixelColor.fromArgb(80, 80, 80, 80),
        key = key,
    )
    val effectiveEnabled = enabled && onTap != null
    val interactive = if (effectiveEnabled) GestureDetector(child = content, onTap = onTap, key = key) else content
    return FocusableControl(
        label = semanticLabel,
        role = if (effectiveEnabled) PixelSemanticRole.BUTTON else PixelSemanticRole.GENERIC,
        enabled = effectiveEnabled,
        child = interactive,
    )
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
                semanticLabel = if (index == selectedIndex) "$label selected" else label,
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

public fun Checkbox(
    checked: Boolean,
    onChanged: ((Boolean) -> Unit)?,
    enabled: Boolean = true,
    activeColor: PixelColor = PixelColor.White,
    inactiveColor: PixelColor = PixelColor.fromRgb(120, 120, 120),
    semanticLabel: String = if (checked) "Checkbox checked" else "Checkbox unchecked",
    key: Any? = null,
): Widget {
    val color = if (!enabled) PixelColor.fromRgb(80, 80, 80) else if (checked) activeColor else inactiveColor
    val mark = if (checked) "X" else " "
    val box = Container(
        width = 9,
        height = 9,
        borderColor = color,
        child = Center(child = Text(mark, style = TextStyle(color = color))),
    )
    val effectiveEnabled = enabled && onChanged != null
    val interactive = if (effectiveEnabled) {
        GestureDetector(child = box, onTap = { onChanged(!checked) }, key = key)
    } else {
        box
    }
    return FocusableControl(
        label = semanticLabel,
        role = PixelSemanticRole.CHECKBOX,
        enabled = effectiveEnabled,
        child = interactive,
    )
}

public fun Switch(
    checked: Boolean,
    onChanged: ((Boolean) -> Unit)?,
    enabled: Boolean = true,
    activeColor: PixelColor = PixelColor.fromRgb(80, 180, 110),
    inactiveColor: PixelColor = PixelColor.fromRgb(120, 120, 120),
    semanticLabel: String = if (checked) "Switch on" else "Switch off",
    key: Any? = null,
): Widget {
    val border = if (!enabled) PixelColor.fromRgb(80, 80, 80) else if (checked) activeColor else inactiveColor
    val thumb = Container(width = 5, height = 5, fillColor = border)
    val track = Container(
        width = 14,
        height = 7,
        borderColor = border,
        child = Stack(
            children = listOf(
                Positioned(
                    left = if (checked) null else 1,
                    right = if (checked) 1 else null,
                    top = 1,
                    child = thumb,
                ),
            ),
        ),
    )
    val effectiveEnabled = enabled && onChanged != null
    val interactive = if (effectiveEnabled) {
        GestureDetector(child = track, onTap = { onChanged(!checked) }, key = key)
    } else {
        track
    }
    return FocusableControl(
        label = semanticLabel,
        role = PixelSemanticRole.SWITCH,
        enabled = effectiveEnabled,
        child = interactive,
    )
}

/**
 * 居中的像素对话框内容。
 *
 * 该函数只负责布局 title/content/actions，不负责遮罩、焦点锁定、back 关闭或生命周期。
 * 需要 overlay 行为时，通过 [PixelOverlayController.showDialog] 显示并持有返回的 handle。
 */
public fun Dialog(
    title: Widget? = null,
    content: Widget,
    actions: List<Widget> = emptyList(),
    fillColor: PixelColor = PixelColor.Black,
    borderColor: PixelColor = PixelColor.White,
    key: Any? = null,
): Widget {
    val children = buildList {
        if (title != null) add(title)
        add(content)
        if (actions.isNotEmpty()) add(Row(children = actions, spacing = 2, mainAxisAlignment = MainAxisAlignment.END))
    }
    return Center(
        child = Container(
            padding = EdgeInsets.all(3),
            fillColor = fillColor,
            borderColor = borderColor,
            child = Column(children = children, spacing = 2),
            key = key,
        ),
    )
}

/**
 * 像素确认对话框。
 *
 * 该组件是 [Dialog] 的受控组合封装，只负责标题、说明、取消/确认按钮布局。
 * overlay 显示、关闭句柄、遮罩、back 行为和危险操作二次校验都由调用方维护。
 */
public fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onCancel: (() -> Unit)? = null,
    confirmText: String = "OK",
    cancelText: String? = "CANCEL",
    fillColor: PixelColor = PixelColor.Black,
    borderColor: PixelColor = PixelColor.White,
    titleStyle: PixelTextStyle = PixelTextStyle.Default,
    messageStyle: PixelTextStyle = PixelTextStyle(color = PixelColor.fromRgb(160, 160, 160)),
    confirmStyle: ButtonStyle = ButtonStyle.Default,
    cancelStyle: TextButtonStyle = TextButtonStyle.Default,
    width: Int? = null,
    key: Any? = null,
): Widget {
    val bodyChildren = buildList {
        add(
            Text(
                title,
                style = titleStyle,
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
                    style = messageStyle,
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
        spacing = 1,
        mainAxisSize = MainAxisSize.MIN,
        crossAxisAlignment = CrossAxisAlignment.CENTER,
    )
    val actions = buildList {
        if (cancelText != null) {
            add(
                TextButton(
                    text = cancelText,
                    onPressed = onCancel,
                    enabled = onCancel != null,
                    style = cancelStyle,
                    key = key?.let { "$it-cancel" },
                ),
            )
        }
        add(
            OutlinedButton(
                text = confirmText,
                onPressed = onConfirm,
                style = confirmStyle,
                key = key?.let { "$it-confirm" },
            ),
        )
    }
    return Dialog(
        content = if (width == null) body else SizedBox(width = width.coerceAtLeast(1), child = body),
        actions = actions,
        fillColor = fillColor,
        borderColor = borderColor,
        key = key,
    )
}

/**
 * 填满父级 [Stack] 的模态遮罩。
 *
 * 该组件只负责绘制遮罩和可选点击关闭；不会自动管理 overlay、back、焦点锁定或动画。
 * 需要对话框生命周期时，请配合 [PixelOverlayHost] / [PixelOverlayController] 使用。
 */
public fun ModalBarrier(
    color: PixelColor = PixelColor.fromArgb(160, 0, 0, 0),
    dismissible: Boolean = false,
    onDismiss: (() -> Unit)? = null,
    key: Any? = null,
): Widget {
    val fill = Container(fillColor = color, key = key?.let { "$it-fill" })
    val barrier = if (dismissible && onDismiss != null) {
        GestureDetector(
            child = fill,
            onTap = onDismiss,
            key = key,
        )
    } else {
        fill
    }
    return PositionedFill(child = barrier, key = key?.let { "$it-positioned" })
}

/**
 * 居中的短提示内容。
 *
 * 该函数只创建 toast widget，不内置自动超时或动画。业务需要显示/关闭时长时，应由
 * [PixelOverlayController] 的 handle 或外部计时器控制。
 */
public fun Toast(
    message: String,
    fillColor: PixelColor = PixelColor.Black,
    textStyle: PixelTextStyle = PixelTextStyle.Default,
    key: Any? = null,
): Widget {
    return Center(
        child = Container(
            padding = EdgeInsets.symmetric(horizontal = 4, vertical = 2),
            fillColor = fillColor,
            borderColor = PixelColor.White,
            child = Text(
                message,
                style = textStyle,
                softWrap = true,
                maxLines = Int.MAX_VALUE,
            ),
            key = key,
        ),
    )
}

/**
 * 像素风 snackbar 内容。
 *
 * 该函数只绘制条形内容本身；贴底定位由 [PixelOverlayController.showSnackbar] 或调用方的
 * [Positioned] 负责。可通过 [action] 放入一个按钮类 widget。
 */
public fun Snackbar(
    message: String,
    action: Widget? = null,
    fillColor: PixelColor = PixelColor.fromRgb(40, 40, 40),
    textStyle: PixelTextStyle = PixelTextStyle.Default,
    key: Any? = null,
): Widget {
    val rowChildren = if (action == null) {
        listOf<Widget>(Expanded(child = snackbarText(message, textStyle)))
    } else {
        listOf(Expanded(child = snackbarText(message, textStyle)), action)
    }
    return Container(
        padding = EdgeInsets.symmetric(horizontal = 3, vertical = 2),
        fillColor = fillColor,
        borderColor = PixelColor.White,
        child = Row(children = rowChildren, spacing = 2, crossAxisAlignment = CrossAxisAlignment.CENTER),
        key = key,
    )
}

public fun Tabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    key: Any? = null,
): Widget = Row(
    children = labels.mapIndexed { index, label ->
        FocusableControl(
            label = label,
            role = PixelSemanticRole.TAB,
            enabled = true,
            focusWhenParentFocused = index == selectedIndex,
            child = OutlinedButton(
                text = label,
                onPressed = { onSelected(index) },
                borderColor = if (index == selectedIndex) PixelColor.fromRgb(80, 180, 110) else PixelColor.White,
            ),
        )
    },
    spacing = 1,
    key = key,
)

public fun SegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    key: Any? = null,
): Widget = Row(
    children = labels.mapIndexed { index, label ->
        FocusableControl(
            label = label,
            role = PixelSemanticRole.TAB,
            enabled = true,
            focusWhenParentFocused = index == selectedIndex,
            child = GestureDetector(
                child = Container(
                    fillColor = if (index == selectedIndex) PixelColor.White else PixelColor.Transparent,
                    borderColor = PixelColor.White,
                    child = Padding(
                        child = Text(
                            label,
                            style = TextStyle(color = if (index == selectedIndex) PixelColor.Black else PixelColor.White),
                            overflow = PixelTextOverflow.ELLIPSIS,
                            softWrap = false,
                            maxLines = 1,
                        ),
                        horizontal = 2,
                        vertical = TEXT_CONTAINER_PADDING_PX,
                    ),
                ),
                onTap = { onSelected(index) },
            ),
        )
    },
    spacing = 0,
    key = key,
)

/**
 * 通用的减 / 值 / 加像素调节器。
 *
 * 组件不保存数值，也不做范围判断；调用方通过 [onDecrease] 和 [onIncrease] 控制边界。
 * 当某一侧回调为 null 或 [enabled] 为 false 时，对应按钮不可点。
 */
public data class ValueAdjusterStyle(
    val borderColor: PixelColor? = null,
    val buttonFillColor: PixelColor? = null,
    val buttonSymbolColor: PixelColor? = null,
    val valueTextColor: PixelColor? = null,
    val disabledColor: PixelColor? = null,
    val focusColor: PixelColor? = null,
) {
    public companion object {
        public val Default: ValueAdjusterStyle = ValueAdjusterStyle()
    }
}

public fun ValueAdjuster(
    valueText: String,
    onDecrease: (() -> Unit)?,
    onIncrease: (() -> Unit)?,
    label: String? = null,
    enabled: Boolean = true,
    valueWidth: Int = 24,
    style: ValueAdjusterStyle = ValueAdjusterStyle.Default,
    key: Any? = null,
): Widget = ValueAdjusterWidget(
    valueText = valueText,
    onDecrease = onDecrease,
    onIncrease = onIncrease,
    label = label,
    enabled = enabled,
    valueWidth = valueWidth,
    style = style,
    key = key,
)

private data class ValueAdjusterWidget(
    val valueText: String,
    val onDecrease: (() -> Unit)?,
    val onIncrease: (() -> Unit)?,
    val label: String?,
    val enabled: Boolean,
    val valueWidth: Int,
    val style: ValueAdjusterStyle,
    override val key: Any? = null,
) : StatelessWidget(key = key) {
    override fun build(context: BuildContext): Widget {
        val theme = PixelTheme.of(context)
        val focusNode = context.getInheritedWidgetOfExactType<FocusNodeScope>()?.node
        if (focusNode != null) {
            context.watch(focusNode)
        }
        val decreaseEnabled = enabled && onDecrease != null
        val increaseEnabled = enabled && onIncrease != null
        val focused = focusNode?.isFocused == true && (decreaseEnabled || increaseEnabled)
        val disabledColor = style.disabledColor ?: theme.colors.disabled
        val borderColor = when {
            !enabled -> disabledColor
            focused -> style.focusColor ?: theme.colors.focus
            else -> style.borderColor ?: theme.buttonStyle.borderColor ?: theme.colors.border
        }
        val buttonFillColor = if (enabled) {
            style.buttonFillColor ?: borderColor
        } else {
            disabledColor
        }
        val buttonSymbolColor = if (enabled) {
            style.buttonSymbolColor ?: theme.buttonStyle.fillColor ?: theme.colors.background
        } else {
            theme.colors.background
        }
        val valueColor = if (enabled) {
            style.valueTextColor ?: theme.buttonStyle.textStyle.color
        } else {
            disabledColor
        }
        val controls = ValueAdjusterRenderWidget(
            value = Text(
                valueText,
                style = theme.buttonStyle.textStyle.copy(color = valueColor),
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
            focused = focused,
            valueWidth = valueWidth,
            borderColor = borderColor,
            buttonFillColor = buttonFillColor,
            buttonSymbolColor = buttonSymbolColor,
            disabledColor = disabledColor,
            key = key,
        )
        return if (label == null) {
            controls
        } else {
            Column(
                children = listOf(Text(label), controls),
                spacing = 1,
                crossAxisAlignment = CrossAxisAlignment.START,
                key = key,
            )
        }
    }
}

private class ValueAdjusterRenderWidget(
    private val value: Widget,
    private val onDecrease: (() -> Unit)?,
    private val onIncrease: (() -> Unit)?,
    private val decreaseEnabled: Boolean,
    private val increaseEnabled: Boolean,
    private val focused: Boolean,
    private val valueWidth: Int,
    private val borderColor: PixelColor,
    private val buttonFillColor: PixelColor,
    private val buttonSymbolColor: PixelColor,
    private val disabledColor: PixelColor,
    override val key: Any? = null,
) : MultiChildRenderObjectWidget(
    children = listOf(
        ValueAdjusterActionSlot(onTap = onDecrease, key = key?.let { "$it-decrease" }),
        value,
        ValueAdjusterActionSlot(onTap = onIncrease, key = key?.let { "$it-increase" }),
    ),
    key = key,
) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderValueAdjuster(
            onDecrease = onDecrease,
            onIncrease = onIncrease,
            decreaseEnabled = decreaseEnabled,
            increaseEnabled = increaseEnabled,
            focused = focused,
            valueWidth = valueWidth,
            borderColor = borderColor,
            buttonFillColor = buttonFillColor,
            buttonSymbolColor = buttonSymbolColor,
            disabledColor = disabledColor,
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderValueAdjuster).update(
            onDecrease = onDecrease,
            onIncrease = onIncrease,
            decreaseEnabled = decreaseEnabled,
            increaseEnabled = increaseEnabled,
            focused = focused,
            valueWidth = valueWidth,
            borderColor = borderColor,
            buttonFillColor = buttonFillColor,
            buttonSymbolColor = buttonSymbolColor,
            disabledColor = disabledColor,
        )
    }
}

private data class ValueAdjusterActionSlot(
    val onTap: (() -> Unit)?,
    override val key: Any? = null,
) : LeafRenderObjectWidget(key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderValueAdjusterActionSlot()
    }
}

private class RenderValueAdjusterActionSlot : RenderBox() {
    override fun layout(constraints: RenderConstraints) {
        size = RenderSize.Zero
    }

    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) = Unit
}

private class RenderValueAdjuster(
    private var onDecrease: (() -> Unit)?,
    private var onIncrease: (() -> Unit)?,
    private var decreaseEnabled: Boolean,
    private var increaseEnabled: Boolean,
    private var focused: Boolean,
    private var valueWidth: Int,
    private var borderColor: PixelColor,
    private var buttonFillColor: PixelColor,
    private var buttonSymbolColor: PixelColor,
    private var disabledColor: PixelColor,
) : MultiChildRenderObject() {
    private var valueOffsetX = 0
    private var valueOffsetY = 0

    fun update(
        onDecrease: (() -> Unit)?,
        onIncrease: (() -> Unit)?,
        decreaseEnabled: Boolean,
        increaseEnabled: Boolean,
        focused: Boolean,
        valueWidth: Int,
        borderColor: PixelColor,
        buttonFillColor: PixelColor,
        buttonSymbolColor: PixelColor,
        disabledColor: PixelColor,
    ) {
        if (
            this.onDecrease === onDecrease &&
            this.onIncrease === onIncrease &&
            this.decreaseEnabled == decreaseEnabled &&
            this.increaseEnabled == increaseEnabled &&
            this.focused == focused &&
            this.valueWidth == valueWidth &&
            this.borderColor == borderColor &&
            this.buttonFillColor == buttonFillColor &&
            this.buttonSymbolColor == buttonSymbolColor &&
            this.disabledColor == disabledColor
        ) {
            return
        }
        val needsLayout = this.valueWidth != valueWidth
        this.onDecrease = onDecrease
        this.onIncrease = onIncrease
        this.decreaseEnabled = decreaseEnabled
        this.increaseEnabled = increaseEnabled
        this.focused = focused
        this.valueWidth = valueWidth
        this.borderColor = borderColor
        this.buttonFillColor = buttonFillColor
        this.buttonSymbolColor = buttonSymbolColor
        this.disabledColor = disabledColor
        if (needsLayout) {
            markNeedsLayout()
        }
        markNeedsPaint()
    }

    override fun layout(constraints: RenderConstraints) {
        actionSlots.forEach { slot ->
            slot.layout(RenderConstraints(maxWidth = 0, maxHeight = 0))
        }

        val safeValueWidth = valueCellWidth()
        val valueContentWidth = valueContentWidth(safeValueWidth)
        val value = valueBox
        value?.layout(
            RenderConstraints(
                minWidth = valueContentWidth,
                maxWidth = valueContentWidth,
                minHeight = 0,
                maxHeight = (
                    constraints.maxHeight -
                        (VALUE_ADJUSTER_BORDER_PX * 2) -
                        (VALUE_ADJUSTER_VALUE_VERTICAL_PADDING_PX * 2)
                    ).coerceAtLeast(0),
            ),
        )

        val desiredInnerHeight = centeredSymbolExtent(
            maxOf(
                VALUE_ADJUSTER_MIN_HEIGHT_PX - (VALUE_ADJUSTER_BORDER_PX * 2),
                (value?.size?.height ?: 0) + (VALUE_ADJUSTER_VALUE_VERTICAL_PADDING_PX * 2),
            ),
        )
        val desiredWidth =
            (VALUE_ADJUSTER_BORDER_PX * 2) +
                (VALUE_ADJUSTER_BUTTON_WIDTH_PX * 2) +
                (VALUE_ADJUSTER_DIVIDER_PX * 2) +
                safeValueWidth
        size = RenderSize(
            width = constraints.constrainWidth(desiredWidth),
            height = constraints.constrainHeight(desiredInnerHeight + (VALUE_ADJUSTER_BORDER_PX * 2)),
        )

        valueOffsetX = valueContentLeft(safeValueWidth)
        valueOffsetY = ((size.height - (value?.size?.height ?: 0)) / 2).coerceAtLeast(VALUE_ADJUSTER_BORDER_PX)
    }

    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        val innerHeight = innerHeight()
        if (size.width <= 0 || size.height <= 0 || innerHeight <= 0) {
            return
        }

        fillButton(context, offsetX, offsetY, leftButtonX(), decreaseEnabled)
        fillButton(context, offsetX, offsetY, rightButtonX(), increaseEnabled)

        valueBox?.paint(context, offsetX + valueOffsetX, offsetY + valueOffsetY)

        context.fillRect(offsetX + leftDividerX(), offsetY, VALUE_ADJUSTER_DIVIDER_PX, size.height, borderColor)
        context.fillRect(offsetX + rightDividerX(), offsetY, VALUE_ADJUSTER_DIVIDER_PX, size.height, borderColor)
        context.drawRect(offsetX, offsetY, size.width, size.height, borderColor)

        drawSymbol(context, offsetX, offsetY, leftButtonX(), plus = false, enabled = decreaseEnabled)
        drawSymbol(context, offsetX, offsetY, rightButtonX(), plus = true, enabled = increaseEnabled)
    }

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

    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) {
        if (decreaseEnabled) {
            onDecrease?.let { callback ->
                targets += PixelClickTarget(
                    bounds = leftButtonRect().translate(offsetX, offsetY),
                    onClick = callback,
                    source = this,
                )
            }
        }
        if (increaseEnabled) {
            onIncrease?.let { callback ->
                targets += PixelClickTarget(
                    bounds = rightButtonRect().translate(offsetX, offsetY),
                    onClick = callback,
                    source = this,
                )
            }
        }
    }

    override fun collectSemantics(offsetX: Int, offsetY: Int, targets: MutableList<PixelSemanticsTarget>) {
        targets += PixelSemanticsTarget(
            node = PixelSemanticsNode(
                label = "Decrease",
                role = PixelSemanticRole.BUTTON,
                enabled = decreaseEnabled,
                focused = focused && decreaseEnabled,
                left = offsetX + leftButtonRect().left,
                top = offsetY + leftButtonRect().top,
                width = leftButtonRect().width,
                height = leftButtonRect().height,
            ),
            source = this,
        )
        targets += PixelSemanticsTarget(
            node = PixelSemanticsNode(
                label = "Increase",
                role = PixelSemanticRole.BUTTON,
                enabled = increaseEnabled,
                focused = focused && increaseEnabled,
                left = offsetX + rightButtonRect().left,
                top = offsetY + rightButtonRect().top,
                width = rightButtonRect().width,
                height = rightButtonRect().height,
            ),
            source = this,
        )
        valueBox?.collectSemantics(offsetX + valueOffsetX, offsetY + valueOffsetY, targets)
    }

    private fun fillButton(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
        buttonX: Int,
        enabled: Boolean,
    ) {
        context.fillRect(
            offsetX + buttonX,
            offsetY + VALUE_ADJUSTER_BORDER_PX,
            VALUE_ADJUSTER_BUTTON_WIDTH_PX,
            innerHeight(),
            if (enabled) buttonFillColor else disabledColor,
        )
    }

    private fun drawSymbol(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
        buttonX: Int,
        plus: Boolean,
        enabled: Boolean,
    ) {
        val color = if (enabled) buttonSymbolColor else PixelColor.Black
        val left = buttonX + ((VALUE_ADJUSTER_BUTTON_WIDTH_PX - VALUE_ADJUSTER_SYMBOL_SIZE_PX) / 2)
        val top = VALUE_ADJUSTER_BORDER_PX +
            ((innerHeight() - VALUE_ADJUSTER_SYMBOL_SIZE_PX) / 2).coerceAtLeast(0)
        val center = VALUE_ADJUSTER_SYMBOL_SIZE_PX / 2
        context.fillRect(
            offsetX + left,
            offsetY + top + center,
            VALUE_ADJUSTER_SYMBOL_SIZE_PX,
            VALUE_ADJUSTER_SYMBOL_STROKE_PX,
            color,
        )
        if (plus) {
            context.fillRect(
                offsetX + left + center,
                offsetY + top,
                VALUE_ADJUSTER_SYMBOL_STROKE_PX,
                VALUE_ADJUSTER_SYMBOL_SIZE_PX,
                color,
            )
        }
    }

    private fun valueCellWidth(): Int = valueWidth.coerceAtLeast(1)

    private fun valueContentWidth(valueCellWidth: Int): Int {
        return (valueCellWidth - (VALUE_ADJUSTER_VALUE_HORIZONTAL_PADDING_PX * 2)).coerceAtLeast(1)
    }

    private fun valueContentLeft(valueCellWidth: Int): Int {
        return VALUE_ADJUSTER_BORDER_PX +
            VALUE_ADJUSTER_BUTTON_WIDTH_PX +
            VALUE_ADJUSTER_DIVIDER_PX +
            ((valueCellWidth - valueContentWidth(valueCellWidth)) / 2)
    }

    private fun leftButtonX(): Int = VALUE_ADJUSTER_BORDER_PX

    private fun leftDividerX(): Int = VALUE_ADJUSTER_BORDER_PX + VALUE_ADJUSTER_BUTTON_WIDTH_PX

    private fun rightDividerX(): Int {
        return VALUE_ADJUSTER_BORDER_PX +
            VALUE_ADJUSTER_BUTTON_WIDTH_PX +
            VALUE_ADJUSTER_DIVIDER_PX +
            valueCellWidth()
    }

    private fun rightButtonX(): Int = rightDividerX() + VALUE_ADJUSTER_DIVIDER_PX

    private fun innerHeight(): Int = (size.height - (VALUE_ADJUSTER_BORDER_PX * 2)).coerceAtLeast(0)

    private fun leftButtonRect(): PixelRect {
        return PixelRect(
            left = leftButtonX(),
            top = VALUE_ADJUSTER_BORDER_PX,
            width = VALUE_ADJUSTER_BUTTON_WIDTH_PX,
            height = innerHeight(),
        )
    }

    private fun rightButtonRect(): PixelRect {
        return PixelRect(
            left = rightButtonX(),
            top = VALUE_ADJUSTER_BORDER_PX,
            width = VALUE_ADJUSTER_BUTTON_WIDTH_PX,
            height = innerHeight(),
        )
    }

    private fun centeredSymbolExtent(base: Int): Int {
        val safe = base.coerceAtLeast(VALUE_ADJUSTER_SYMBOL_SIZE_PX)
        val freeSpace = safe - VALUE_ADJUSTER_SYMBOL_SIZE_PX
        return if (freeSpace % 2 == 0) safe else safe + 1
    }

    private val valueBox: RenderBox?
        get() = children.getOrNull(1) as? RenderBox

    private val actionSlots: List<RenderBox>
        get() = listOfNotNull(children.getOrNull(0) as? RenderBox, children.getOrNull(2) as? RenderBox)
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
): Widget {
    val hasRange = range.first <= range.last
    val safeStep = step.coerceAtLeast(1)
    val safeValue = if (hasRange) value.coerceIn(range.first, range.last) else value
    val decrease = if (enabled && hasRange && safeValue > range.first) {
        { onChanged((safeValue - safeStep).coerceAtLeast(range.first)) }
    } else {
        null
    }
    val increase = if (enabled && hasRange && safeValue < range.last) {
        { onChanged((safeValue + safeStep).coerceAtMost(range.last)) }
    } else {
        null
    }
    return ValueAdjuster(
        valueText = valueText ?: safeValue.toString(),
        onDecrease = decrease,
        onIncrease = increase,
        label = label,
        enabled = enabled && hasRange,
        valueWidth = valueWidth,
        key = key,
    )
}

/**
 * 像素风快捷键提示。
 *
 * 该组件只渲染快捷键和说明文本，不注册键盘事件；实际处理应放在 [Focus] 的 onKeyEvent
 * 或宿主级快捷键分发中。
 */
public fun ShortcutHint(
    shortcut: String,
    label: String,
    shortcutStyle: PixelTextStyle = PixelTextStyle.Default,
    labelStyle: PixelTextStyle = PixelTextStyle(color = PixelColor.fromRgb(160, 160, 160)),
    key: Any? = null,
): Widget {
    return Row(
        children = listOf(
            Container(
                padding = EdgeInsets.symmetric(horizontal = 2, vertical = TEXT_CONTAINER_PADDING_PX),
                borderColor = PixelColor.White,
                child = Text(
                    shortcut,
                    style = shortcutStyle,
                    overflow = PixelTextOverflow.ELLIPSIS,
                    softWrap = false,
                    maxLines = 1,
                    textAlign = TextAlign.CENTER,
                ),
                key = key?.let { "$it-shortcut" },
            ),
            Text(
                label,
                style = labelStyle,
                overflow = PixelTextOverflow.ELLIPSIS,
                softWrap = false,
                maxLines = 1,
                key = key?.let { "$it-label" },
            ),
        ),
        spacing = 2,
        crossAxisAlignment = CrossAxisAlignment.CENTER,
        key = key,
    )
}

/**
 * 固定尺寸的水平进度条。
 *
 * [progress] 会在绘制前钳位到 `0f..1f`；业务侧仍应把它视为受控状态并保存真实进度。
 * [width] 和 [height] 使用 pixel-engine 的逻辑像素。
 */
public fun ProgressBar(
    progress: Float,
    width: Int = 48,
    height: Int = 5,
    color: PixelColor = PixelColor.fromRgb(80, 180, 110),
    trackColor: PixelColor = PixelColor.fromRgb(60, 60, 60),
    key: Any? = null,
): Widget {
    val safeProgress = progress.coerceIn(0f, 1f)
    val fillWidth = (width * safeProgress).toInt().coerceIn(0, width)
    return Stack(
        children = listOf(
            Container(width = width, height = height, fillColor = trackColor, borderColor = PixelColor.White),
            Container(width = fillWidth, height = height, fillColor = color),
        ),
        key = key,
    )
}

/**
 * 点阵扫描式水平 Loading 条。
 *
 * [progress] 表示实心扫描块在轨道中的位置，`0f` 在左侧，`1f` 在右侧；[reversed]
 * 会把运动方向翻转，并把点阵残影绘制到运动尾部。组件只绘制当前帧，不创建 ticker。
 * 需要持续播放时使用 [AnimatedPixelLoadingBar]。
 */
public fun PixelLoadingBar(
    progress: Float,
    width: Int = 96,
    height: Int = 9,
    color: PixelColor = PixelColor.White,
    trackColor: PixelColor = color.withAlpha(96),
    blockWidth: Int = 9,
    trailWidth: Int = 5,
    reversed: Boolean = false,
    key: Any? = null,
): Widget = PixelLoadingBarWidget(
    progress = progress,
    width = width,
    height = height,
    color = color,
    trackColor = trackColor,
    blockWidth = blockWidth,
    trailWidth = trailWidth,
    reversed = reversed,
    key = key,
)

/**
 * 自带 ticker 的点阵扫描 Loading 条。
 *
 * 动画采用左右往返运动，扫描块到达边缘后反向，残影也随方向切换。
 */
public fun AnimatedPixelLoadingBar(
    vsync: PixelTickerProvider,
    width: Int = 96,
    height: Int = 9,
    color: PixelColor = PixelColor.White,
    trackColor: PixelColor = color.withAlpha(96),
    blockWidth: Int = 9,
    trailWidth: Int = 5,
    fps: Int = 30,
    cycleFrames: Int = 96,
    playing: Boolean = true,
    key: Any? = null,
): Widget = AnimatedPixelLoadingBarWidget(
    vsync = vsync,
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

        paintTrackDots(context, offsetX, offsetY, width, height)

        val safeBlockWidth = blockWidth.coerceIn(1, width)
        val travel = (width - safeBlockWidth).coerceAtLeast(0)
        val normalized = progress.coerceIn(0f, 1f)
        val forwardX = (travel * normalized).toInt().coerceIn(0, travel)
        val blockLeft = if (reversed) travel - forwardX else forwardX

        val motionFactor = pixelLoadingMotionFactor(normalized)
        paintElasticTrail(
            context = context,
            offsetX = offsetX,
            offsetY = offsetY,
            width = width,
            height = height,
            blockLeft = blockLeft,
            blockWidth = safeBlockWidth,
            motionFactor = motionFactor,
        )
        context.fillRect(offsetX + blockLeft, offsetY, safeBlockWidth, height, color)
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

    private fun paintTrackDots(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
        width: Int,
        height: Int,
    ) {
        var row = 0
        var y = if (height <= 2) 0 else 1
        val lastY = if (height <= 2) height else height - 1
        while (y < lastY) {
            val startX = row.floorMod(2)
            var x = startX
            while (x < width) {
                context.buffer.setPixel(offsetX + x, offsetY + y, trackColor)
                x += PIXEL_LOADING_DOT_STEP_X
            }
            y += PIXEL_LOADING_DOT_STEP_Y
            row++
        }
    }

    private fun paintElasticTrail(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
        width: Int,
        height: Int,
        blockLeft: Int,
        blockWidth: Int,
        motionFactor: Float,
    ) {
        val baseTrailWidth = trailWidth.coerceAtLeast(0)
        if (baseTrailWidth == 0) return
        val nearWidth = (
            baseTrailWidth +
                (blockWidth * PIXEL_LOADING_NEAR_TRAIL_STRETCH * motionFactor)
            ).roundToInt().coerceAtLeast(1)
        val farWidth = (
            baseTrailWidth * 2 +
                (blockWidth * PIXEL_LOADING_FAR_TRAIL_STRETCH * motionFactor)
            ).roundToInt().coerceAtLeast(nearWidth + 1)

        paintSparseWake(
            context = context,
            offsetX = offsetX,
            offsetY = offsetY,
            width = width,
            height = height,
            blockLeft = blockLeft,
            blockWidth = blockWidth,
            wakeWidth = farWidth,
        )
        paintDenseWake(
            context = context,
            offsetX = offsetX,
            offsetY = offsetY,
            width = width,
            height = height,
            blockLeft = blockLeft,
            blockWidth = blockWidth,
            wakeWidth = nearWidth,
        )
    }

    private fun paintSparseWake(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
        width: Int,
        height: Int,
        blockLeft: Int,
        blockWidth: Int,
        wakeWidth: Int,
    ) {
        for (distance in 1..wakeWidth) {
            val x = wakeX(blockLeft, blockWidth, distance)
            if (x !in 0 until width) continue
            val localProgress = 1f - ((distance - 1).toFloat() / wakeWidth.toFloat())
            val density = when {
                localProgress > 0.72f -> 2
                localProgress > 0.42f -> 3
                else -> 4
            }
            for (y in 0 until height) {
                val rowVisible = if (height <= 3) true else y in 1 until height - 1
                val clusterVisible = (distance + y * 2).floorMod(density) == 0 ||
                    (localProgress > 0.58f && (distance + y).floorMod(2) == 0)
                if (rowVisible && clusterVisible) {
                    context.buffer.setPixel(offsetX + x, offsetY + y, color)
                    if (localProgress > 0.55f) {
                        val nextX = wakeX(blockLeft, blockWidth, distance + 1)
                        if (nextX in 0 until width) {
                            context.buffer.setPixel(offsetX + nextX, offsetY + y, color)
                        }
                    }
                }
            }
        }
    }

    private fun paintDenseWake(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
        width: Int,
        height: Int,
        blockLeft: Int,
        blockWidth: Int,
        wakeWidth: Int,
    ) {
        for (distance in 1..wakeWidth) {
            val x = wakeX(blockLeft, blockWidth, distance)
            if (x !in 0 until width) continue
            for (y in 0 until height) {
                val isHole = if (height <= 3) {
                    (distance + y).floorMod(3) == 0
                } else {
                    y in 1 until height - 1 && (distance + y).floorMod(3) == 0
                }
                if (!isHole) {
                    context.buffer.setPixel(offsetX + x, offsetY + y, color)
                }
            }
        }
    }

    private fun wakeX(blockLeft: Int, blockWidth: Int, distance: Int): Int =
        if (reversed) blockLeft + blockWidth - 1 + distance else blockLeft - distance
}

private data class AnimatedPixelLoadingBarWidget(
    val vsync: PixelTickerProvider,
    val width: Int,
    val height: Int,
    val color: PixelColor,
    val trackColor: PixelColor,
    val blockWidth: Int,
    val trailWidth: Int,
    val fps: Int,
    val cycleFrames: Int,
    val playing: Boolean,
    override val key: Any?,
) : StatefulWidget(key = key) {
    init {
        require(fps > 0) { "fps must be > 0, got $fps" }
        require(cycleFrames > 1) { "cycleFrames must be > 1, got $cycleFrames" }
    }

    override fun createState(): State<out StatefulWidget> = AnimatedPixelLoadingBarState()
}

private class AnimatedPixelLoadingBarState : State<AnimatedPixelLoadingBarWidget>() {
    private var ticker: PixelTicker? = null
    private var currentFrame = 0
    private var lastElapsedNanos = -1L
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
): Widget {
    val dots = List(4) { index ->
        Container(
            width = 3,
            height = 3,
            fillColor = if (index == frame.floorMod(4)) color else PixelColor.fromRgb(60, 60, 60),
        )
    }
    return Row(children = dots, spacing = 1, key = key)
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
 * 居中的像素空状态。
 *
 * 该组件只负责把标题、说明、图标和操作按钮排成紧凑像素布局；空数据判断、加载状态、
 * 重试动作和路由跳转都由调用方维护。
 */
public fun EmptyState(
    title: String,
    message: String? = null,
    icon: Widget? = null,
    action: Widget? = null,
    width: Int? = null,
    titleStyle: PixelTextStyle = PixelTextStyle.Default,
    messageStyle: PixelTextStyle = PixelTextStyle(color = PixelColor.fromRgb(160, 160, 160)),
    key: Any? = null,
): Widget {
    val children = buildList {
        if (icon != null) add(icon)
        add(
            Text(
                title,
                style = titleStyle,
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
                    style = messageStyle,
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
        spacing = 1,
        mainAxisSize = MainAxisSize.MIN,
        crossAxisAlignment = CrossAxisAlignment.CENTER,
    )
    return Center(
        child = if (width == null) content else SizedBox(width = width.coerceAtLeast(1), child = content),
        key = key,
    )
}

public fun Badge(
    child: Widget,
    label: Widget,
    key: Any? = null,
): Widget = Stack(
    children = listOf(
        child,
        Positioned(
            top = 0,
            right = 0,
            child = Container(
                padding = EdgeInsets.symmetric(horizontal = 1, vertical = 0),
                fillColor = PixelColor.fromRgb(220, 90, 80),
                borderColor = PixelColor.White,
                child = label,
            ),
        ),
    ),
    key = key,
)

public fun Divider(
    color: PixelColor = PixelColor.White,
    thickness: Int = 1,
    key: Any? = null,
): Widget = Container(height = thickness.coerceAtLeast(1), fillColor = color, key = key)

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
): Widget {
    val children = buildList {
        if (title != null) {
            add(
                Container(
                    padding = EdgeInsets.symmetric(horizontal = 2, vertical = TEXT_CONTAINER_PADDING_PX),
                    borderColor = PixelColor.White,
                    child = title,
                ),
            )
            add(Gap(height = 1))
        }
        add(Expanded(child = body))
        if (bottomBar != null) {
            add(Gap(height = 1))
            add(bottomBar)
        }
    }
    return Column(children = children, mainAxisSize = MainAxisSize.MAX, crossAxisAlignment = CrossAxisAlignment.STRETCH, key = key)
}

private fun Int.floorMod(divisor: Int): Int {
    val remainder = this % divisor
    return if (remainder < 0) remainder + divisor else remainder
}

private fun PixelColor.withAlpha(alpha: Int): PixelColor = PixelColor.fromArgb(
    a = alpha.coerceIn(0, 255),
    r = red,
    g = green,
    b = blue,
)

private fun pixelLoadingPositionCurve(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * x * (x * (x * 6f - 15f) + 10f)
}

private fun pixelLoadingMotionFactor(progress: Float): Float {
    val base = sin(progress.coerceIn(0f, 1f) * PI).toFloat().coerceIn(0f, 1f)
    return (PIXEL_LOADING_MIN_TRAIL_FACTOR + (1f - PIXEL_LOADING_MIN_TRAIL_FACTOR) * base)
        .coerceIn(0f, 1f)
}

private const val PIXEL_LOADING_DOT_STEP_X = 3
private const val PIXEL_LOADING_DOT_STEP_Y = 2
private const val PIXEL_LOADING_MIN_TRAIL_FACTOR = 0.18f
private const val PIXEL_LOADING_NEAR_TRAIL_STRETCH = 0.9f
private const val PIXEL_LOADING_FAR_TRAIL_STRETCH = 1.8f
private const val TEXT_CONTAINER_PADDING_PX = 2
private const val VALUE_ADJUSTER_BORDER_PX = 1
private const val VALUE_ADJUSTER_DIVIDER_PX = 1
private const val VALUE_ADJUSTER_BUTTON_WIDTH_PX = 11
private const val VALUE_ADJUSTER_MIN_HEIGHT_PX = 13
private const val VALUE_ADJUSTER_VALUE_HORIZONTAL_PADDING_PX = 2
private const val VALUE_ADJUSTER_VALUE_VERTICAL_PADDING_PX = 1
private const val VALUE_ADJUSTER_SYMBOL_SIZE_PX = 5
private const val VALUE_ADJUSTER_SYMBOL_STROKE_PX = 1

private fun snackbarText(
    message: String,
    textStyle: PixelTextStyle,
): Widget = Text(
    message,
    style = textStyle,
    softWrap = true,
    maxLines = 2,
    overflow = PixelTextOverflow.ELLIPSIS,
)

private data class FocusableControl(
    val label: String,
    val role: PixelSemanticRole,
    val child: Widget,
    val enabled: Boolean,
    val focusWhenParentFocused: Boolean = true,
    override val key: Any? = null,
) : StatelessWidget(key = key) {
    override fun build(context: BuildContext): Widget {
        val focusNode = context.getInheritedWidgetOfExactType<FocusNodeScope>()?.node
        if (focusNode != null) {
            context.watch(focusNode)
        }
        val focused = enabled && focusWhenParentFocused && focusNode?.isFocused == true
        val highlighted = if (focused) {
            Stack(
                children = listOf(
                    child,
                    PositionedFill(child = Container(borderColor = PixelColor.fromRgb(255, 200, 0))),
                ),
            )
        } else {
            child
        }
        return Semantics(
            label = label,
            role = role,
            enabled = enabled,
            focused = focused,
            child = highlighted,
        )
    }
}

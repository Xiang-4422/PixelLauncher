package com.purride.pixelui

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor

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

private const val TEXT_CONTAINER_PADDING_PX = 2

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

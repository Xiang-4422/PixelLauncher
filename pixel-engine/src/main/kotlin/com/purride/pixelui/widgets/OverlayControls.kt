package com.purride.pixelui

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.animation.IntOffset

/**
 * 受控弹出层。
 *
 * [expanded] 由调用方持有；组件只在同一个 [Stack] 中按 [contentOffset] 放置内容，
 * 不做自动测量、避让屏幕边缘、hover 触发或全局 overlay 管理。
 */
public fun Popover(
    anchor: Widget,
    content: Widget,
    expanded: Boolean,
    contentOffset: IntOffset = IntOffset(0, 10),
    dismissible: Boolean = false,
    onDismiss: (() -> Unit)? = null,
    key: Any? = null,
): Widget {
    val children = buildList {
        add(anchor)
        if (expanded) {
            if (dismissible && onDismiss != null) {
                add(
                    ModalBarrier(
                        color = PixelColor.Transparent,
                        dismissible = true,
                        onDismiss = onDismiss,
                        key = key?.let { "$it-barrier" },
                    ),
                )
            }
            add(
                Positioned(
                    left = contentOffset.x,
                    top = contentOffset.y,
                    child = content,
                    key = key?.let { "$it-content" },
                ),
            )
        }
    }
    return Stack(children = children, key = key)
}

/**
 * 像素菜单的一行。
 *
 * [onSelected] 只处理该行动作；关闭菜单、路由跳转或状态更新由调用方决定。
 */
public data class PixelMenuItem(
    val label: String,
    val onSelected: () -> Unit,
    val enabled: Boolean = true,
    val shortcut: String? = null,
)

/**
 * 纵向像素菜单。
 *
 * 组件不持有选中状态，也不自动关闭弹出层；每行点击时只调用对应 [PixelMenuItem.onSelected]。
 */
public fun Menu(
    items: List<PixelMenuItem>,
    enabled: Boolean = true,
    fillColor: PixelColor = PixelColor.Black,
    borderColor: PixelColor = PixelColor.White,
    key: Any? = null,
): Widget {
    val rows = items.mapIndexed { index, item ->
        val itemEnabled = enabled && item.enabled
        ListTile(
            title = Text(item.label),
            trailing = item.shortcut?.let { Text(it, style = PixelTextStyle(color = PixelColor.fromRgb(160, 160, 160))) },
            enabled = itemEnabled,
            onTap = if (itemEnabled) item.onSelected else null,
            semanticLabel = item.label,
            key = key?.let { "$it-$index" },
        )
    }
    return Container(
        padding = EdgeInsets.all(1),
        fillColor = fillColor,
        borderColor = borderColor,
        child = Column(children = rows, spacing = 0, crossAxisAlignment = CrossAxisAlignment.STRETCH),
        key = key,
    )
}

/**
 * 受控下拉菜单。
 *
 * [expanded] 和选中值由调用方维护；点击 anchor 只调用 [onToggle]，点击菜单项只调用 item 自身动作。
 */
public fun Dropdown(
    label: String,
    selectedText: String,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
    items: List<PixelMenuItem>,
    enabled: Boolean = true,
    contentOffset: IntOffset = IntOffset(0, 14),
    key: Any? = null,
): Widget {
    val buttonText = if (label.isBlank()) "$selectedText v" else "$label: $selectedText v"
    return Popover(
        anchor = OutlinedButton(
            text = buttonText,
            onPressed = onToggle,
            enabled = enabled && onToggle != null,
            key = key?.let { "$it-anchor" },
        ),
        content = Menu(
            items = items,
            enabled = enabled,
            key = key?.let { "$it-menu" },
        ),
        expanded = expanded,
        contentOffset = contentOffset,
        key = key,
    )
}

/**
 * 受控提示浮层。
 *
 * [visible] 由调用方根据焦点、长按或业务状态维护；组件不会监听 hover，也不会自动延迟显示。
 */
public fun Tooltip(
    message: String,
    visible: Boolean,
    child: Widget,
    contentOffset: IntOffset = IntOffset(0, 10),
    fillColor: PixelColor = PixelColor.Black,
    borderColor: PixelColor = PixelColor.White,
    textStyle: PixelTextStyle = PixelTextStyle.Default,
    key: Any? = null,
): Widget {
    return Popover(
        anchor = child,
        content = Container(
            padding = EdgeInsets.symmetric(horizontal = 3, vertical = 2),
            fillColor = fillColor,
            borderColor = borderColor,
            child = Text(
                message,
                style = textStyle,
                softWrap = true,
                maxLines = 2,
                overflow = PixelTextOverflow.ELLIPSIS,
            ),
            key = key?.let { "$it-tooltip" },
        ),
        expanded = visible,
        contentOffset = contentOffset,
        key = key,
    )
}

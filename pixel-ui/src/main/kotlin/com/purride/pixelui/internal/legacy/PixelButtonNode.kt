package com.purride.pixelui.internal.legacy

import com.purride.pixelui.PixelButtonStyle
import com.purride.pixelui.toPixelAlignment

internal data class PixelButtonNode(
    override val key: Any? = null,
    override val modifier: PixelModifier = PixelModifier.Empty,
    val text: String,
    val style: PixelButtonStyle = PixelButtonStyle.Default,
    val disabledStyle: PixelButtonStyle = PixelButtonStyle.Disabled,
    val styleLocked: Boolean = false,
    val enabled: Boolean = true,
    val onClick: (() -> Unit)? = null,
) : PixelNode

internal fun PixelButton(
    text: String,
    onClick: (() -> Unit)?,
    modifier: PixelModifier = PixelModifier.Empty,
    style: PixelButtonStyle = PixelButtonStyle.Default,
    disabledStyle: PixelButtonStyle = PixelButtonStyle.Disabled,
    enabled: Boolean = true,
    key: Any? = null,
): PixelNode {
    return PixelButtonNode(
        key = key,
        modifier = modifier,
        text = text,
        style = style,
        disabledStyle = disabledStyle,
        enabled = enabled,
        onClick = onClick,
    )
}

internal fun PixelButtonNode.toSurfaceNode(): PixelSurfaceNode {
    val isEnabled = enabled && onClick != null
    val resolvedStyle = if (isEnabled) style else disabledStyle
    return PixelSurfaceNode(
        key = key,
        modifier = if (isEnabled) modifier.clickable { onClick?.invoke() } else modifier,
        fillTone = resolvedStyle.fillTone,
        borderTone = resolvedStyle.borderTone,
        child = PixelBox(
            modifier = PixelModifier.Empty.fillMaxSize(),
            alignment = resolvedStyle.alignment.toPixelAlignment(),
            children = listOf(
                PixelText(
                    text = text,
                    style = resolvedStyle.textStyle,
                ),
            ),
        ),
    )
}

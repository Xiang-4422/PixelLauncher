package com.purride.pixelui

import com.purride.pixelui.internal.SemanticsWidget

public enum class PixelSemanticRole {
    TEXT,
    BUTTON,
    TEXT_FIELD,
    CHECKBOX,
    SWITCH,
    TAB,
    GENERIC,
}

public data class PixelSemanticsNode(
    public val label: String,
    public val role: PixelSemanticRole,
    public val enabled: Boolean,
    public val focused: Boolean,
    public val left: Int,
    public val top: Int,
    public val width: Int,
    public val height: Int,
)

public fun Semantics(
    label: String,
    child: Widget,
    role: PixelSemanticRole = PixelSemanticRole.GENERIC,
    enabled: Boolean = true,
    focused: Boolean = false,
    key: Any? = null,
): Widget {
    return SemanticsWidget(
        label = label,
        role = role,
        enabled = enabled,
        focused = focused,
        child = child,
        key = key,
    )
}

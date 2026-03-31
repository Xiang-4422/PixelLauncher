package com.purride.pixelui

/**
 * runtime 级临时状态容器。
 *
 * 这类状态不属于业务域模型，而是 UI runtime 自身维护的交互现场，
 * 例如焦点节点、激活中的滚动容器和命中的手势目标。
 */
data class PixelUiState(
    val focusedNodeKey: Any? = null,
    val activeScrollerKey: Any? = null,
    val activeGestureKey: Any? = null,
)

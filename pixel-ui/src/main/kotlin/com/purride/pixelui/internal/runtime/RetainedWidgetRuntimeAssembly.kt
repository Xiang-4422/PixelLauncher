package com.purride.pixelui.internal

/**
 * retained widget runtime 默认装配结果。
 *
 * 这层把 retained build runtime 和 retained render support 对齐到同一个 assembly，
 * 避免工厂继续把装配细节散在多处。
 */
internal data class RetainedWidgetRuntimeAssembly(
    val buildRuntime: ElementTreeBuildRuntime,
    val elementTreeRenderer: ElementTreeRenderer,
)

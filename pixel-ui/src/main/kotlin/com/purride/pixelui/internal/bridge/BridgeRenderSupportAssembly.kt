package com.purride.pixelui.internal

/**
 * bridge 渲染支持的默认装配结果。
 *
 * 这层把 bridge tree resolver、bridge runtime 和 element tree renderer 对齐到
 * 同一个 assembly，避免 bridge support 工厂继续在方法体里手拼对象图。
 */
internal data class BridgeRenderSupportAssembly(
    val bridgeTreeResolver: BridgeTreeResolving,
    val bridgeTreeRenderer: BridgeTreeRenderer,
    val elementTreeRenderer: ElementTreeRenderer,
)

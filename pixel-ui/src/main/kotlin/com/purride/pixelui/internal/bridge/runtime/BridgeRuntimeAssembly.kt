package com.purride.pixelui.internal

/**
 * bridge 运行时装配结果。
 *
 * 这层把 bridge tree resolver 和 bridge tree renderer 对齐成一个中间装配单元，
 * 让 bridge support factory 不必同时承担底层 runtime 的全部创建细节。
 */
internal data class BridgeRuntimeAssembly(
    val bridgeTreeResolver: BridgeTreeResolving,
    val bridgeTreeRenderer: BridgeTreeRenderer,
)

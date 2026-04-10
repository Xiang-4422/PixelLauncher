package com.purride.pixelui.internal

/**
 * retained build/runtime 主链只依赖 bridge 语义，不直接暴露 legacy renderer 的命名。
 *
 * 当前阶段 bridge 节点仍然落到 legacy 渲染节点实现，但这层别名把 retained 侧和
 * legacy renderer 的命名边界先拉开，方便后续继续替换真正的 retained render object。
 */
internal typealias BridgeRenderNode = LegacyRenderNode

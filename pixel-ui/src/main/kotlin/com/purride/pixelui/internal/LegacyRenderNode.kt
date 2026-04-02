package com.purride.pixelui.internal

import com.purride.pixelui.internal.legacy.PixelNode

/**
 * retained runtime 过渡到当前 legacy renderer 时使用的内部桥接节点类型。
 *
 * 当前底层实现仍然直接复用旧的 `PixelNode`，但在 retained/build/render
 * 主链里统一使用 `LegacyRenderNode` 这个名字，避免把旧节点语义继续扩散到
 * 新架构主路径。
 */
internal typealias LegacyRenderNode = PixelNode

package com.purride.pixelui

import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.PixelNode
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState

/**
 * 像素列表节点。
 *
 * 第一版列表先只支持纵向单列滚动。
 */
internal data class PixelListNode(
    override val key: Any? = null,
    override val modifier: PixelModifier = PixelModifier.Empty,
    val state: PixelListState,
    val controller: PixelListController,
    val items: List<PixelNode>,
    val spacing: Int = 0,
) : PixelNode

/**
 * 最小可用像素列表组件。
 *
 * 当前版本只负责：
 * 1. 提供独立视口
 * 2. 裁剪可见区域
 * 3. 通过 `PixelListController` 响应触摸拖动
 */
internal fun PixelList(
    items: List<PixelNode>,
    state: PixelListState,
    controller: PixelListController,
    modifier: PixelModifier = PixelModifier.Empty,
    spacing: Int = 0,
    key: Any? = null,
): PixelNode {
    return PixelListNode(
        key = key,
        modifier = modifier,
        state = state,
        controller = controller,
        items = items,
        spacing = spacing,
    )
}

package com.purride.pixelui

import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState

/**
 * 单子节点滚动容器。
 *
 * 这个组件对应常见 UI 框架里的 `SingleChildScrollView`：
 * - 当前阶段先只支持纵向滚动
 * - 复用现有 `PixelListState / PixelListController`
 * - 适合承载“一个很长的表单或内容列”，而不是离散列表项
 */
internal data class PixelSingleChildScrollViewNode(
    override val key: Any? = null,
    override val modifier: PixelModifier = PixelModifier.Empty,
    val state: PixelListState,
    val controller: PixelListController,
    val child: PixelNode,
) : PixelNode

/**
 * 最小可用单子节点滚动组件。
 *
 * 当前只提供：
 * 1. 单一子树的独立滚动视口
 * 2. 纵向触摸拖动
 * 3. 子树点击、分页、输入目标的裁剪与平移
 */
internal fun PixelSingleChildScrollView(
    child: PixelNode,
    state: PixelListState,
    controller: PixelListController,
    modifier: PixelModifier = PixelModifier.Empty,
    key: Any? = null,
): PixelNode {
    return PixelSingleChildScrollViewNode(
        key = key,
        modifier = modifier,
        state = state,
        controller = controller,
        child = child,
    )
}

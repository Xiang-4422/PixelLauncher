package com.purride.pixelui.internal

import com.purride.pixelcore.PixelAxis

/**
 * 复合滚动手势仲裁规则。
 *
 * 第一版只解决一个最关键场景：
 * 纵向分页内部包着纵向列表时，优先让列表消费自己还能处理的拖动；
 * 只有列表已经到边界、无法继续消费时，才把这次手势让给外层分页。
 */
internal object NestedScrollGesturePolicy {

    fun shouldDeferPagerToList(
        pagerAxis: PixelAxis,
        pagerWantsDrag: Boolean,
        listWantsDrag: Boolean,
        listCanConsumeDrag: Boolean,
    ): Boolean {
        return pagerAxis == PixelAxis.VERTICAL &&
            pagerWantsDrag &&
            listWantsDrag &&
            listCanConsumeDrag
    }
}

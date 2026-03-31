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

    /**
     * 列表已经开始消费手势后，是否应该把后续拖动接力给外层分页。
     *
     * 这一版只处理纵向分页 + 纵向列表的同轴场景：
     * 当列表已经到边界，且当前手势仍在继续沿纵向拖动时，
     * 允许外层分页接管本次手势，避免用户必须抬手再滑一次。
     */
    fun shouldHandOffListToPager(
        pagerAxis: PixelAxis,
        listCanConsumeDrag: Boolean,
        deltaPx: Float,
    ): Boolean {
        return pagerAxis == PixelAxis.VERTICAL &&
            !listCanConsumeDrag &&
            deltaPx != 0f
    }
}

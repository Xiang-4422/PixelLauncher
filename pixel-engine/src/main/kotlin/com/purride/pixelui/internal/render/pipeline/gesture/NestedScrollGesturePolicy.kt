package com.purride.pixelui.internal

import com.purride.pixelcore.PixelAxis

/**
 * direct pipeline 的复合滚动手势仲裁规则。
 */
internal object NestedScrollGesturePolicy {
    /**
     * 判断外层分页是否应该把当前拖动让给内层列表。
     */
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
     * 判断列表到边界后，是否应该把同一次手势接力给外层分页。
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

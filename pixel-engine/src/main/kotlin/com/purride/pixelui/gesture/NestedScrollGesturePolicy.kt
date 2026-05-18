package com.purride.pixelui.gesture

import com.purride.pixelcore.PixelAxis

/**
 * 嵌套滚动手势仲裁规则。
 *
 * 当外层 PageView 与内层 ListView 同方向时，决定一次拖动归谁消费。
 * 默认实现的语义：
 * - 纵向嵌套时，若内层 list 还能接收，则让出给 list（避免误翻页）；
 * - 内层 list 到边界后接力给外层 pager（保证连贯）。
 *
 * 宿主可继承此类重写两个方法提供更激进/保守的策略，然后通过
 * `PixelHostSetupConfig.nestedScrollPolicy` 注入。
 */
public open class NestedScrollGesturePolicy {
    /**
     * 判断外层分页是否应该把当前拖动让给内层列表。
     */
    public open fun shouldDeferPagerToList(
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
    public open fun shouldHandOffListToPager(
        pagerAxis: PixelAxis,
        listCanConsumeDrag: Boolean,
        deltaPx: Float,
    ): Boolean {
        return pagerAxis == PixelAxis.VERTICAL &&
            !listCanConsumeDrag &&
            deltaPx != 0f
    }

    public companion object {
        /**
         * 默认策略实例。绝大多数宿主直接复用即可。
         */
        @JvmField
        public val Default: NestedScrollGesturePolicy = NestedScrollGesturePolicy()
    }
}

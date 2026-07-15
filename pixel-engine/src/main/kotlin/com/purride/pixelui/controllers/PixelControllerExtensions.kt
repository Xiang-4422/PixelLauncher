package com.purride.pixelui

import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelPagerState

/**
 * Flutter 风格控制器扩展。
 *
 * 当前阶段这些能力先映射到现有控制器，
 * 让页面层优先使用统一的公开语言，而不是直接依赖底层方法名。
 */
public fun PageController.jumpToPage(
    state: PixelPagerState,
    page: Int,
) {
    syncToPage(state = state, targetPage = page)
}

/** 推进 `PixelControllerExtensions` 的 `nextPage` 页面或滚动目标，并把越界输入限制到有效范围。 */
public fun PageController.nextPage(state: PixelPagerState) {
    jumpToPage(state = state, page = state.currentPage + 1)
}

/** 推进 `PixelControllerExtensions` 的 `previousPage` 页面或滚动目标，并把越界输入限制到有效范围。 */
public fun PageController.previousPage(state: PixelPagerState) {
    jumpToPage(state = state, page = state.currentPage - 1)
}

/** 推进 `PixelControllerExtensions` 的 `showItem` 页面或滚动目标，并把越界输入限制到有效范围。 */
public fun ScrollController.showItem(
    state: PixelListState,
    itemIndex: Int,
) {
    scrollItemIntoView(
        state = state,
        itemIndex = itemIndex,
    )
}

/** 推进 `PixelControllerExtensions` 的 `jumpToStart` 页面或滚动目标，并把越界输入限制到有效范围。 */
public fun ScrollController.jumpToStart(state: PixelListState) {
    scrollTo(
        state = state,
        targetOffsetPx = 0f,
        viewportHeightPx = state.viewportHeightPx,
        contentHeightPx = state.contentHeightPx,
    )
}

/** 推进 `PixelControllerExtensions` 的 `jumpToEnd` 页面或滚动目标，并把越界输入限制到有效范围。 */
public fun ScrollController.jumpToEnd(state: PixelListState) {
    scheduleJumpToEnd(state)
}

/** 推进 `PixelControllerExtensions` 的 `fling` 页面或滚动目标，并把越界输入限制到有效范围。 */
public fun ScrollController.fling(
    state: PixelListState,
    velocityPxPerSecond: Float,
) {
    endDrag(
        state = state,
        velocityPxPerSecond = velocityPxPerSecond,
        viewportHeightPx = state.viewportHeightPx,
        contentHeightPx = state.contentHeightPx,
    )
}

/**
 * 给任意 ChangeNotifier（包括 PageController / ScrollController /
 * TextEditingController）注册一个无参监听回调，并把对应的
 * [VoidCallback] 句柄返回给调用方，便于稍后 [ChangeNotifier.removeListener]。
 *
 * 这是 LiveData 风格的语法糖，等价于：
 * ```
 * val handle = VoidCallback { onChanged() }
 * controller.addListener(handle)
 * // 解绑：controller.removeListener(handle)
 * ```
 */
public inline fun ChangeNotifier.observe(crossinline onChanged: () -> Unit): VoidCallback {
    val listener = VoidCallback { onChanged() }
    addListener(listener)
    return listener
}

package com.purride.pixelui

import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelPagerState

/**
 * Flutter 风格控制器扩展。
 *
 * 当前阶段这些能力先映射到现有控制器，
 * 让页面层优先使用统一的公开语言，而不是直接依赖底层方法名。
 */
fun PageController.jumpToPage(
    state: PixelPagerState,
    page: Int,
) {
    syncToPage(state = state, targetPage = page)
}

fun PageController.nextPage(state: PixelPagerState) {
    jumpToPage(state = state, page = state.currentPage + 1)
}

fun PageController.previousPage(state: PixelPagerState) {
    jumpToPage(state = state, page = state.currentPage - 1)
}

fun ScrollController.showItem(
    state: PixelListState,
    itemIndex: Int,
) {
    scrollItemIntoView(
        state = state,
        itemIndex = itemIndex,
    )
}

fun ScrollController.jumpToStart(state: PixelListState) {
    scrollTo(
        state = state,
        targetOffsetPx = 0f,
        viewportHeightPx = state.viewportHeightPx,
        contentHeightPx = state.contentHeightPx,
    )
}

fun ScrollController.jumpToEnd(state: PixelListState) {
    scrollTo(
        state = state,
        targetOffsetPx = state.maxScrollOffsetPx,
        viewportHeightPx = state.viewportHeightPx,
        contentHeightPx = state.contentHeightPx,
    )
}

fun ScrollController.fling(
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
inline fun ChangeNotifier.observe(crossinline onChanged: () -> Unit): VoidCallback {
    val listener = VoidCallback { onChanged() }
    addListener(listener)
    return listener
}

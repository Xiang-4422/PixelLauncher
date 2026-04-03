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

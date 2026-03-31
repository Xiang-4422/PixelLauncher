package com.purride.pixelcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `HorizontalPageController` 的核心模块测试。
 *
 * 这组测试覆盖拖拽阈值、速度翻页和边界夹紧逻辑，
 * 让横向分页运行时可以在 `:pixel-core` 内独立回归验证。
 */
class HorizontalPageControllerTest {

    private val controller = HorizontalPageController()

    @Test
    fun 拖拽超过距离阈值后吸附到上一页() {
        var state = controller.create(pageCount = 3, currentIndex = 1)
        state = controller.startDrag(state)
        state = controller.dragBy(state = state, deltaPx = 45f, pageWidth = 100)
        state = controller.endDrag(
            state = state,
            pageWidth = 100,
            velocityPxPerSecond = 0f,
        )

        assertTrue(state.isSettling)
        assertEquals(0, state.settleTargetIndex)

        state = controller.step(state, deltaMs = 240L)
        assertEquals(false, state.isSettling)
        assertEquals(0, state.currentIndex)
    }

    @Test
    fun 拖拽超过距离阈值后吸附到下一页() {
        var state = controller.create(pageCount = 3, currentIndex = 1)
        state = controller.startDrag(state)
        state = controller.dragBy(state = state, deltaPx = -45f, pageWidth = 100)
        state = controller.endDrag(
            state = state,
            pageWidth = 100,
            velocityPxPerSecond = 0f,
        )

        assertEquals(2, state.settleTargetIndex)
        state = controller.step(state, deltaMs = 240L)
        assertEquals(2, state.currentIndex)
    }

    @Test
    fun 高速度时即使位移较小也会触发翻页() {
        var state = controller.create(pageCount = 3, currentIndex = 1)
        state = controller.startDrag(state)
        state = controller.dragBy(state = state, deltaPx = 8f, pageWidth = 100)
        state = controller.endDrag(
            state = state,
            pageWidth = 100,
            velocityPxPerSecond = 80f,
        )

        assertEquals(0, state.settleTargetIndex)
    }

    @Test
    fun 边界页会限制拖拽位移和目标页索引() {
        var state = controller.create(pageCount = 3, currentIndex = 0)
        state = controller.startDrag(state)
        state = controller.dragBy(
            state = state,
            deltaPx = 40f,
            pageWidth = 100,
        )
        assertEquals(0f, state.dragOffsetPx)

        state = controller.endDrag(
            state = state,
            pageWidth = 100,
            velocityPxPerSecond = 120f,
        )
        assertEquals(0, state.settleTargetIndex)

        state = controller.create(pageCount = 3, currentIndex = 2)
        state = controller.startDrag(state)
        state = controller.dragBy(
            state = state,
            deltaPx = -40f,
            pageWidth = 100,
        )
        assertEquals(0f, state.dragOffsetPx)

        state = controller.endDrag(
            state = state,
            pageWidth = 100,
            velocityPxPerSecond = -120f,
        )
        assertEquals(2, state.settleTargetIndex)
    }
}

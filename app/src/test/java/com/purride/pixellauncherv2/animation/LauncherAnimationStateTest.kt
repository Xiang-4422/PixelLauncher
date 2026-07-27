package com.purride.pixellauncherv2.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

/**
 * [LauncherAnimationState] 的纯 JVM 行为测试：验证帧推进不可变性与关键时长常量。
 */
class LauncherAnimationStateTest {

    @Test
    fun nextFrame_递增计数且不修改原对象() {
        val original = LauncherAnimationState(headerChargeTick = 5)

        val next = original.nextFrame()

        // 新状态的计数应为原值加一
        assertEquals(6, next.headerChargeTick)
        // 原对象的计数保持不变
        assertEquals(5, original.headerChargeTick)
        assertNotSame(original, next)
    }

    @Test
    fun 帧时长常量满足预期数值关系() {
        val frameDelayMs = LauncherAnimationState.frameDelayMs
        val launchShutterDurationMs = LauncherAnimationState.launchShutterDurationMs

        assertEquals(60L, frameDelayMs)
        assertEquals(240L, launchShutterDurationMs)
        assertEquals(frameDelayMs * 4, launchShutterDurationMs)
    }
}

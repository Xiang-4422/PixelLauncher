package com.purride.pixellockscreen

import org.junit.Assert.assertEquals
import org.junit.Test

/** 像素图案认证运行时的系统截止时间换算测试。 */
class PixelPatternSecuritySessionTest {
    /** 已结束或恰好到期的系统截止时间应立即恢复输入。 */
    @Test
    fun expiredDeadlineHasNoRemainingSecond() {
        assertEquals(0, remainingLockoutSeconds(10_000L, 10_000L))
        assertEquals(0, remainingLockoutSeconds(9_999L, 10_000L))
    }

    /** 未满一秒也必须向上显示一秒，避免提前允许输入。 */
    @Test
    fun partialSecondRoundsUp() {
        assertEquals(1, remainingLockoutSeconds(10_001L, 10_000L))
        assertEquals(2, remainingLockoutSeconds(11_001L, 10_000L))
    }

    /** 异常超长截止时间必须限制在 UI 可表达的整数秒范围。 */
    @Test
    fun hugeDeadlineIsClamped() {
        assertEquals(Int.MAX_VALUE, remainingLockoutSeconds(Long.MAX_VALUE, 0L))
    }
}

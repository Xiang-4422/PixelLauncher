package com.purride.pixellockscreen.credential

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Titan 2 PIN 自动确认合同的边界测试。 */
class Titan2PinControllerBindingTest {
    /** 关闭自动确认时不得使用控制器中的缓存长度。 */
    @Test
    fun disabledAutoConfirmIgnoresCachedLength() {
        assertNull(validatedPinAutoConfirmLength(enabled = false, pinLength = -1L))
    }

    /** 启用自动确认时应保留系统给出的有效精确长度。 */
    @Test
    fun enabledAutoConfirmKeepsValidatedLength() {
        assertEquals(6, validatedPinAutoConfirmLength(enabled = true, pinLength = 6L))
        assertEquals(64, validatedPinAutoConfirmLength(enabled = true, pinLength = 64L))
    }

    /** 启用自动确认但长度越界时必须拒绝接管。 */
    @Test
    fun enabledAutoConfirmRejectsUnsafeLength() {
        assertThrows(IllegalStateException::class.java) {
            validatedPinAutoConfirmLength(enabled = true, pinLength = 3L)
        }
        assertThrows(IllegalStateException::class.java) {
            validatedPinAutoConfirmLength(enabled = true, pinLength = 65L)
        }
    }
}

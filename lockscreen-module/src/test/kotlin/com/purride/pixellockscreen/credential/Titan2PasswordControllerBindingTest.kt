package com.purride.pixellockscreen.credential

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Titan 2 密码输入连接只读长度合同的边界测试。 */
class Titan2PasswordControllerBindingTest {
    /** 原生输入框允许的首尾长度必须原样保留。 */
    @Test
    fun acceptsNativePasswordLengthBoundary() {
        assertEquals(0, validatedNativePasswordLength(0))
        assertEquals(500, validatedNativePasswordLength(500))
    }

    /** 异常负数和超过原生 maxLength 的值必须触发安全回退。 */
    @Test
    fun rejectsImpossibleNativePasswordLength() {
        assertThrows(IllegalStateException::class.java) {
            validatedNativePasswordLength(-1)
        }
        assertThrows(IllegalStateException::class.java) {
            validatedNativePasswordLength(501)
        }
    }
}

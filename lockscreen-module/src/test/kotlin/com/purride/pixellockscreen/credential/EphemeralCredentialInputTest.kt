package com.purride.pixellockscreen.credential

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证凭据输入只在可清零缓冲与单次 lease 中存在。 */
class EphemeralCredentialInputTest {
    /** 删除、清空和关闭都必须覆写字符数组。 */
    @Test
    fun characterBufferZeroizesEveryReleasedSlot() {
        /** 待验证的字符缓冲。 */
        val buffer = EphemeralCharBuffer(4)
        buffer.append('1')
        buffer.append('2')
        assertTrue(buffer.deleteLast())
        assertEquals(charArrayOf('1', '\u0000', '\u0000', '\u0000').toList(), storageOf(buffer).toList())

        buffer.clear()
        assertTrue(storageOf(buffer).all { character -> character == '\u0000' })
        buffer.append('9')
        buffer.close()

        assertTrue(storageOf(buffer).all { character -> character == '\u0000' })
        assertThrows(IllegalStateException::class.java) { buffer.length }
        buffer.close()
    }

    /** 字符和图案字符串化结果不得包含原始凭据。 */
    @Test
    fun debugTextAlwaysRemainsRedacted() {
        /** 包含测试 PIN 的输入会话。 */
        val pinSession = CredentialInputSession(PixelCredentialMode.PIN)
        "1234".forEach(pinSession::appendCharacter)
        /** 提交后的 PIN lease。 */
        val pinLease = pinSession.submit()!!
        assertFalse(pinLease.toString().contains("1234"))

        /** 包含测试图案的输入会话。 */
        val patternSession = CredentialInputSession(PixelCredentialMode.PATTERN)
        listOf(0, 1, 4, 8).forEach(patternSession::appendPatternCell)
        /** 提交后的图案 lease。 */
        val patternLease = patternSession.submit()!!
        assertFalse(patternLease.toString().contains("0, 1, 4, 8"))

        pinLease.close()
        patternLease.close()
        pinSession.close()
        patternSession.close()
    }

    /** 提交会把独立所有权交给校验任务，并立即清空输入会话。 */
    @Test
    fun submissionTransfersCopyAndClearsOriginalSession() {
        /** 当前 PIN 输入会话。 */
        val session = CredentialInputSession(PixelCredentialMode.PIN)
        "5072".forEach(session::appendCharacter)

        /** 独占本次校验输入的字符 lease。 */
        val lease = session.submit() as EphemeralCredentialLease.Characters
        assertEquals(0, session.inputLength)
        assertEquals("5072", lease.withCharacters { characters -> characters.toStringByIndex() })

        lease.close()
        assertThrows(IllegalStateException::class.java) {
            lease.withCharacters { characters -> characters.length }
        }
        session.close()
    }

    /** 图案只接受 0–8 且保持首次经过顺序。 */
    @Test
    fun patternRejectsDuplicatesAndInvalidCells() {
        /** 当前图案输入会话。 */
        val session = CredentialInputSession(PixelCredentialMode.PATTERN)
        assertTrue(session.appendPatternCell(0))
        assertTrue(session.appendPatternCell(4))
        assertFalse(session.appendPatternCell(0))
        assertThrows(IllegalArgumentException::class.java) { session.appendPatternCell(9) }

        /** 独占本次校验路径的图案 lease。 */
        val lease = session.submit() as EphemeralCredentialLease.Pattern
        assertEquals(2, lease.size)
        assertEquals(0, lease.cellAt(0))
        assertEquals(4, lease.cellAt(1))
        assertEquals(0, session.inputLength)

        lease.close()
        assertThrows(IllegalStateException::class.java) { lease.size }
        session.close()
    }

    /** PIN 拒绝非数字，空输入不创建校验 lease。 */
    @Test
    fun pinAcceptsOnlyDigitsAndSkipsEmptySubmission() {
        /** 当前 PIN 输入会话。 */
        val session = CredentialInputSession(PixelCredentialMode.PIN)
        assertNull(session.submit())
        assertFalse(session.appendCharacter('A'))
        assertTrue(session.appendCharacter('7'))
        assertEquals(1, session.inputLength)
        session.close()
    }

    /** 通过反射读取测试对象的固定字符数组，避免生产代码暴露调试接口。 */
    private fun storageOf(buffer: EphemeralCharBuffer): CharArray {
        /** 可清零字符数组字段。 */
        val field = EphemeralCharBuffer::class.java.getDeclaredField("storage")
        field.isAccessible = true
        return field.get(buffer) as CharArray
    }

    /** 测试中按索引复制字符，验证生产代码无需 `subSequence`。 */
    private fun CharSequence.toStringByIndex(): String = CharArray(length) { index -> this[index] }.concatToString()
}

package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MmsNotificationModelTest {

    @Test
    fun extractSenderReadsAddressPresentFrom() {
        val pdu = buildNotificationInd(from = "13800138000/TYPE=PLMN")

        assertEquals("13800138000", MmsNotificationModel.extractSender(pdu))
    }

    @Test
    fun extractSenderKeepsPlainAddressWithoutTypeSuffix() {
        val pdu = buildNotificationInd(from = "+8613800138000")

        assertEquals("+8613800138000", MmsNotificationModel.extractSender(pdu))
    }

    @Test
    fun extractSenderReturnsNullForInsertAddressToken() {
        // insert-address-token：地址由彩信中心补齐，PDU 中不存在。
        val pdu = byteArrayOf(
            0x8C.toByte(), 0x82.toByte(),
            0x89.toByte(), 0x01, 0x81.toByte(),
        )

        assertNull(MmsNotificationModel.extractSender(pdu))
    }

    @Test
    fun extractSenderSurvivesMalformedPdu() {
        assertNull(MmsNotificationModel.extractSender(byteArrayOf()))
        assertNull(MmsNotificationModel.extractSender(byteArrayOf(0x01, 0x02)))
        assertNull(MmsNotificationModel.extractSender(byteArrayOf(0x89.toByte())))
        assertNull(MmsNotificationModel.extractSender(byteArrayOf(0x89.toByte(), 0x1F)))
    }

    /** 手工构造一段最小 m-notification.ind：类型 + 事务 ID + 版本 + From。 */
    private fun buildNotificationInd(from: String): ByteArray {
        val fromBytes = from.toByteArray(Charsets.US_ASCII)
        return byteArrayOf(
            // X-Mms-Message-Type: m-notification-ind
            0x8C.toByte(), 0x82.toByte(),
            // X-Mms-Transaction-ID: "T1"（text-string，NUL 结尾）
            0x98.toByte(), 'T'.code.toByte(), '1'.code.toByte(), 0x00,
            // X-Mms-MMS-Version: 1.2
            0x8D.toByte(), 0x92.toByte(),
            // From: value-length + address-present-token + 地址文本 + NUL
            0x89.toByte(), (fromBytes.size + 2).toByte(), 0x80.toByte(),
        ) + fromBytes + byteArrayOf(0x00)
    }
}

package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.data.SmsMessageEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class SmsThreadSearchModelTest {

    @Test
    fun filterMatchesContactNameAddressAndBody() {
        val bank = message(id = 1, address = "+86 10086", displayName = "Bank", body = "Your code is 123456")
        val courier = message(id = 2, address = "95555", displayName = "Courier", body = "Pickup at gate")
        val messages = listOf(bank, courier)

        assertEquals(listOf(bank), SmsThreadSearchModel.filter(messages, "bank"))
        assertEquals(listOf(bank), SmsThreadSearchModel.filter(messages, "10086"))
        assertEquals(listOf(courier), SmsThreadSearchModel.filter(messages, "pickup"))
    }

    @Test
    fun blankQueryReturnsOriginalMessages() {
        val messages = listOf(message(id = 1, address = "10086", displayName = "", body = "hello"))

        assertEquals(messages, SmsThreadSearchModel.filter(messages, " "))
    }

    private fun message(
        id: Long,
        address: String,
        displayName: String,
        body: String,
    ): SmsMessageEntry {
        return SmsMessageEntry(
            messageId = id,
            threadId = 1L,
            address = address,
            body = body,
            dateMillis = 0L,
            type = 1,
            isRead = true,
            displayName = displayName,
        )
    }
}

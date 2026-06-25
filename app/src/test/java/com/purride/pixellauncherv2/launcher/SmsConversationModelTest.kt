package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.data.SmsMessageEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsConversationModelTest {

    @Test
    fun personalContactWinsOverBracketSource() {
        val identity = SmsConversationModel.identify(
            address = "95555",
            body = "【Bank】Your statement is ready",
            contactName = "Alice",
        )

        assertEquals("person:95555", identity.key)
        assertEquals("Alice", identity.title)
        assertFalse(identity.isService)
    }

    @Test
    fun mainlandMobilePrefixesNormalizeToOneConversation() {
        val local = SmsConversationModel.identify("15388888888", "", "")
        val plus86 = SmsConversationModel.identify("+86 153 8888 8888", "", "")
        val zero86 = SmsConversationModel.identify("008615388888888", "", "")

        assertEquals(local.key, plus86.key)
        assertEquals(local.key, zero86.key)
        assertEquals("15388888888", local.title)
    }

    @Test
    fun serviceSourceGroupsDifferentAddresses() {
        val first = SmsConversationModel.identify("10690001", "【借呗】还款提醒", "")
        val second = SmsConversationModel.identify("10690002", "  【借呗】账单已出", "")

        assertEquals(first.key, second.key)
        assertEquals("借呗", first.title)
        assertTrue(first.isService)
    }

    @Test
    fun stripLeadingSourceRemovesOnlyLeadingFullWidthSource() {
        assertEquals("还款提醒", SmsConversationModel.stripLeadingSource("【借呗】还款提醒"))
        assertEquals("账单已出", SmsConversationModel.stripLeadingSource("  【借呗】 账单已出"))
        assertEquals("您好【借呗】还款提醒", SmsConversationModel.stripLeadingSource("您好【借呗】还款提醒"))
    }

    @Test
    fun missingOrNonLeadingSourceFallsBackToAddress() {
        val missing = SmsConversationModel.identify("10690001", "普通通知", "")
        val nonLeading = SmsConversationModel.identify("10690001", "您好【借呗】还款提醒", "")

        assertEquals("address:10690001", missing.key)
        assertEquals(missing.key, nonLeading.key)
        assertFalse(missing.isService)
    }

    @Test
    fun summariesMergeServiceMessagesAndKeepLatestMetadata() {
        val older = message(
            id = 1,
            threadId = 10,
            address = "10690001",
            body = "【借呗】旧消息",
            date = 100,
            read = true,
        )
        val latestUnread = message(
            id = 2,
            threadId = 20,
            address = "10690002",
            body = "【借呗】新消息",
            date = 200,
            read = false,
        )

        val summary = SmsConversationModel.summarize(listOf(older, latestUnread)).single()

        assertEquals("service:借呗", summary.conversationKey)
        assertEquals(20L, summary.threadId)
        assertEquals("10690002", summary.address)
        assertEquals("新消息", summary.snippet)
        assertEquals(2, summary.messageCount)
        assertEquals(1, summary.unreadCount)
        assertTrue(summary.isServiceConversation)
    }

    private fun message(
        id: Long,
        threadId: Long,
        address: String,
        body: String,
        date: Long,
        read: Boolean,
    ): SmsMessageEntry {
        val identity = SmsConversationModel.identify(address, body, "")
        val displayBody = if (identity.isService) SmsConversationModel.stripLeadingSource(body) else body
        return SmsMessageEntry(
            messageId = id,
            threadId = threadId,
            address = address,
            body = displayBody,
            dateMillis = date,
            type = 1,
            isRead = read,
            conversationKey = identity.key,
            conversationTitle = identity.title,
            isServiceConversation = identity.isService,
        )
    }
}

package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.model.SmsMessageEntry
import com.purride.pixellauncherv2.model.SmsThreadSummary
import java.util.Locale

data class SmsConversationIdentity(
    val key: String,
    val title: String,
    val isService: Boolean,
)

object SmsConversationModel {

    fun identify(
        address: String,
        body: String,
        contactName: String,
        allowSource: Boolean = true,
    ): SmsConversationIdentity {
        val normalizedAddress = normalizeAddress(address)
        val titleAddress = normalizedAddress.ifBlank { address.trim().ifBlank { "UNKNOWN" } }
        if (contactName.isNotBlank() || mainlandMobile.matches(normalizedAddress)) {
            return SmsConversationIdentity(
                key = "person:$normalizedAddress",
                title = contactName.trim().ifBlank { titleAddress },
                isService = false,
            )
        }

        val source = if (allowSource) sourceAtStart(body) else null
        if (source != null) {
            return SmsConversationIdentity(
                key = "service:${source.lowercase(Locale.ROOT)}",
                title = source,
                isService = true,
            )
        }

        return SmsConversationIdentity(
            key = "address:${normalizedAddress.lowercase(Locale.ROOT)}",
            title = titleAddress,
            isService = false,
        )
    }

    fun summarize(messages: List<SmsMessageEntry>): List<SmsThreadSummary> {
        val summaries = LinkedHashMap<String, MutableSummary>()
        messages.sortedByDescending(SmsMessageEntry::dateMillis).forEach { message ->
            val summary = summaries.getOrPut(message.conversationKey) {
                MutableSummary(
                    conversationKey = message.conversationKey,
                    threadId = message.threadId,
                    address = message.address,
                    snippet = message.body,
                    dateMillis = message.dateMillis,
                    displayName = message.conversationTitle,
                    isServiceConversation = message.isServiceConversation,
                )
            }
            summary.messageCount += 1
            if (!message.isRead && !SmsMessageStatusModel.isOutgoing(message.type)) {
                summary.unreadCount += 1
            }
        }
        return summaries.values.map { summary ->
            SmsThreadSummary(
                threadId = summary.threadId,
                address = summary.address,
                snippet = summary.snippet,
                dateMillis = summary.dateMillis,
                unreadCount = summary.unreadCount,
                messageCount = summary.messageCount,
                displayName = summary.displayName,
                conversationKey = summary.conversationKey,
                isServiceConversation = summary.isServiceConversation,
            )
        }
    }

    fun messages(
        allMessages: List<SmsMessageEntry>,
        conversationKey: String,
    ): List<SmsMessageEntry> {
        return allMessages
            .filter { it.conversationKey == conversationKey }
            .sortedBy(SmsMessageEntry::dateMillis)
    }

    fun unread(messages: List<SmsMessageEntry>): List<SmsMessageEntry> {
        return messages.filter { !it.isRead && !SmsMessageStatusModel.isOutgoing(it.type) }
    }

    fun stripLeadingSource(body: String): String {
        return sourcePattern.replaceFirst(body, "").trimStart()
    }

    fun normalizeAddress(address: String): String {
        val digits = address.filter(Char::isDigit)
        return when {
            digits.length == 15 && digits.startsWith("0086") -> digits.drop(4)
            digits.length == 13 && digits.startsWith("86") -> digits.drop(2)
            digits.isNotEmpty() -> digits
            else -> address.trim().uppercase(Locale.ROOT)
        }
    }

    private fun sourceAtStart(body: String): String? {
        return sourcePattern.find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }

    private data class MutableSummary(
        val conversationKey: String,
        val threadId: Long,
        val address: String,
        val snippet: String,
        val dateMillis: Long,
        val displayName: String,
        val isServiceConversation: Boolean,
        var unreadCount: Int = 0,
        var messageCount: Int = 0,
    )

    private val mainlandMobile = Regex("^1[3-9]\\d{9}$")
    private val sourcePattern = Regex("^[\\s\\u3000]*【([^】\\r\\n]+)】")
}

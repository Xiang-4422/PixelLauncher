package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.data.SmsMessageEntry

object SmsThreadSearchModel {

    fun filter(
        messages: List<SmsMessageEntry>,
        query: String,
    ): List<SmsMessageEntry> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty()) return messages
        val digitQuery = query.filter(Char::isDigit)
        return messages.filter { message ->
            normalize(message.conversationTitle).contains(normalizedQuery) ||
            normalize(message.displayName).contains(normalizedQuery) ||
                normalize(message.address).contains(normalizedQuery) ||
                normalize(message.body).contains(normalizedQuery) ||
                (digitQuery.isNotEmpty() && message.address.filter(Char::isDigit).contains(digitQuery))
        }
    }

    private fun normalize(value: String): String {
        return value.trim().lowercase()
    }
}

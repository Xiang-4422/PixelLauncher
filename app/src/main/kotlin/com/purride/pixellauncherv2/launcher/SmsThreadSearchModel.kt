package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.model.SmsMessageEntry

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

    /**
     * 搜索词可直接作为收件号码时返回规范化号码（供"新建会话"入口使用），否则返回 null。
     *
     * 只接受数字与常见号码分隔符（空格、连字符），"+" 仅允许出现在开头；
     * 至少 [MIN_COMPOSE_DIGITS] 位数字，避免把普通搜索词误判成号码。
     */
    fun composeAddress(query: String): String? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.any { !it.isDigit() && it != '+' && it != ' ' && it != '-' }) return null
        if (trimmed.indexOf('+') > 0) return null
        val digits = trimmed.filter(Char::isDigit)
        if (digits.length < MIN_COMPOSE_DIGITS) return null
        return if (trimmed.startsWith("+")) "+$digits" else digits
    }

    private fun normalize(value: String): String {
        return value.trim().lowercase()
    }

    private const val MIN_COMPOSE_DIGITS = 3
}

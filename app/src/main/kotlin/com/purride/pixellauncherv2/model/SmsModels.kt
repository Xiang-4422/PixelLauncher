package com.purride.pixellauncherv2.model

/**
 * 单个短信会话（线程）的摘要信息，用于会话列表展示。
 */
data class SmsThreadSummary(
    /** 短信线程 ID（对应系统短信数据库的 thread_id）。 */
    val threadId: Long,
    /** 对方号码或发件人地址。 */
    val address: String,
    /** 会话最新一条消息的摘要文本。 */
    val snippet: String,
    /** 最新一条消息的时间戳（毫秒）。 */
    val dateMillis: Long,
    /** 该会话中未读消息的数量。 */
    val unreadCount: Int,
    /** 该会话中的消息总数。 */
    val messageCount: Int,
    /** 联系人展示名，未匹配到联系人时为空字符串。 */
    val displayName: String = "",
    /** 会话在 UI 层的唯一标识键，默认按 threadId 生成。 */
    val conversationKey: String = "thread:$threadId",
    /** 是否为验证码/通知类服务号会话。 */
    val isServiceConversation: Boolean = false,
)

/**
 * 单条短信记录，覆盖收发双向的展示与归属信息。
 */
data class SmsMessageEntry(
    /** 短信在系统数据库中的消息 ID。 */
    val messageId: Long,
    /** 所属会话线程 ID。 */
    val threadId: Long,
    /** 对方号码或发件人地址。 */
    val address: String,
    /** 短信正文内容。 */
    val body: String,
    /** 消息时间戳（毫秒）。 */
    val dateMillis: Long,
    /** 消息类型（对应 Telephony.Sms 的收/发类型常量）。 */
    val type: Int,
    /** 是否已读。 */
    val isRead: Boolean,
    /** 送达状态（对应 Telephony.Sms.STATUS：-1 无回执，0 已送达）。 */
    val deliveryStatus: Int = -1,
    /** 联系人展示名，未匹配到联系人时为空字符串。 */
    val displayName: String = "",
    /** 会话在 UI 层的唯一标识键，默认按 threadId 生成。 */
    val conversationKey: String = "thread:$threadId",
    /** 会话展示标题，默认取联系人名，无联系人名时回退为地址。 */
    val conversationTitle: String = displayName.ifBlank { address },
    /** 是否为验证码/通知类服务号会话。 */
    val isServiceConversation: Boolean = false,
)

/**
 * 发送短信的请求参数。
 */
data class SmsSendRequest(
    /** 接收方号码。 */
    val address: String,
    /** 短信正文内容。 */
    val body: String,
    /** 目标会话线程 ID；为空时由发送逻辑自行解析或创建。 */
    val threadId: Long? = null,
)

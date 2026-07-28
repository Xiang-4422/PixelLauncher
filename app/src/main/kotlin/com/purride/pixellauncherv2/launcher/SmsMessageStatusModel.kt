package com.purride.pixellauncherv2.launcher

/**
 * 短信消息类型（Telephony.Sms.MESSAGE_TYPE_*）的纯逻辑判定。
 *
 * 发送链路的状态流转：发送时先落一条 OUTBOX 记录，系统回执到达后
 * 由 SmsSendResultReceiver 更新为 SENT（成功）或 FAILED（失败）。
 */
object SmsMessageStatusModel {

    /** 已确认发出成功。 */
    fun isSent(type: Int): Boolean = type == TYPE_SENT

    /** 本机发出方向的消息（含发送中/失败），用于收发布局与未读统计。 */
    fun isOutgoing(type: Int): Boolean =
        type == TYPE_SENT || type == TYPE_OUTBOX || type == TYPE_QUEUED || type == TYPE_FAILED

    /** 已提交发送、回执尚未到达。 */
    fun isPending(type: Int): Boolean = type == TYPE_OUTBOX || type == TYPE_QUEUED

    /** 发送失败，可重发。 */
    fun isFailed(type: Int): Boolean = type == TYPE_FAILED

    /** android.provider.Telephony.Sms.MESSAGE_TYPE_SENT = 2 */
    private const val TYPE_SENT = 2

    /** android.provider.Telephony.Sms.MESSAGE_TYPE_OUTBOX = 4 */
    private const val TYPE_OUTBOX = 4

    /** android.provider.Telephony.Sms.MESSAGE_TYPE_FAILED = 5 */
    private const val TYPE_FAILED = 5

    /** android.provider.Telephony.Sms.MESSAGE_TYPE_QUEUED = 6 */
    private const val TYPE_QUEUED = 6
}

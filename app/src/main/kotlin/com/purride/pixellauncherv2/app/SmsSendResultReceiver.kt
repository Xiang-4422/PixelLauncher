package com.purride.pixellauncherv2.app

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log

/**
 * 接收 SmsManager 发送/送达回执，把 OUTBOX 记录更新为 SENT/FAILED/QUEUED，
 * 送达回执写入 STATUS 列。
 *
 * PendingIntent 由 [com.purride.pixellauncherv2.data.SmsRepository.sendMessage] 挂载；
 * 走 Manifest 注册以在进程被回收后仍能收到回执。提供者更新会触发内容观察者，
 * 前台 UI 随之自动刷新。跨进程写库不占用接收器主线程。
 */
class SmsSendResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        if (messageId <= 0L) {
            return
        }
        val action = intent.action
        val resultCode = resultCode
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                runCatching {
                    when (action) {
                        ACTION_SMS_SENT -> AndroidComponentDependencies.smsRepository(appContext)
                            .applySendResult(
                                messageId = messageId,
                                success = resultCode == Activity.RESULT_OK,
                                errorCode = resultCode,
                            )

                        ACTION_SMS_DELIVERED -> AndroidComponentDependencies.smsRepository(appContext)
                            .applyDeliveryResult(
                                messageId = messageId,
                                delivered = resultCode == Activity.RESULT_OK &&
                                    deliveryReportConfirms(intent),
                            )
                    }
                }.onFailure {
                    Log.w(LOG_TAG, "apply send/delivery result failed", it)
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    /**
     * 解析送达回执 PDU 的真实 TP-Status：resultCode 只表示回执送达了本机，
     * 状态报告本身可能是“未送达/仍在重试”。无 PDU 或解析失败时信任 resultCode。
     */
    private fun deliveryReportConfirms(intent: Intent): Boolean {
        val pdu = intent.getByteArrayExtra("pdu") ?: return true
        val status = runCatching {
            val format = intent.getStringExtra("format")
            val message = if (format != null) {
                SmsMessage.createFromPdu(pdu, format)
            } else {
                @Suppress("DEPRECATION")
                SmsMessage.createFromPdu(pdu)
            }
            message?.status
        }.getOrNull() ?: return true
        return status == Telephony.Sms.STATUS_COMPLETE
    }

    companion object {
        const val ACTION_SMS_SENT = "com.purride.pixellauncherv2.action.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.purride.pixellauncherv2.action.SMS_DELIVERED"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        private const val LOG_TAG = "SmsSendResult"
    }
}

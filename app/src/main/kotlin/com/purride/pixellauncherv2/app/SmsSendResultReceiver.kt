package com.purride.pixellauncherv2.app

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 接收 SmsManager 发送回执，把 OUTBOX 记录更新为 SENT 或 FAILED。
 *
 * PendingIntent 由 [com.purride.pixellauncherv2.data.SmsRepository.sendMessage] 挂载；
 * 走 Manifest 注册以在进程被回收后仍能收到回执。提供者更新会触发内容观察者，
 * 前台 UI 随之自动刷新。
 */
class SmsSendResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SMS_SENT) {
            return
        }
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        if (messageId <= 0L) {
            return
        }
        AndroidComponentDependencies.smsRepository(context).applySendResult(
            messageId = messageId,
            success = resultCode == Activity.RESULT_OK,
            errorCode = resultCode,
        )
    }

    companion object {
        const val ACTION_SMS_SENT = "com.purride.pixellauncherv2.action.SMS_SENT"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
    }
}

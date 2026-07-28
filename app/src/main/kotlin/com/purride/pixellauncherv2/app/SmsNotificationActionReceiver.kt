package com.purride.pixellauncherv2.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.purride.pixellauncherv2.model.SmsSendRequest

/**
 * 短信通知上的快捷操作：直接回复（RemoteInput）与标记会话已读。
 *
 * 发送与提供者写入都是跨进程 IO，goAsync 后在工作线程执行；
 * 处理完成必须撤下对应通知，否则 RemoteInput 的发送转圈不会结束。
 */
class SmsNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
        val address = intent.getStringExtra(EXTRA_ADDRESS).orEmpty()
        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_REPLY_TEXT)
            ?.toString()
            .orEmpty()
            .trim()
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                when (action) {
                    ACTION_REPLY -> handleReply(appContext, threadId, address, replyText)
                    ACTION_MARK_READ -> handleMarkRead(appContext, threadId)
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun handleReply(context: Context, threadId: Long, address: String, replyText: String) {
        val helper = AndroidComponentDependencies.smsNotificationHelper(context)
        helper.cancelForThread(threadId)
        if (address.isBlank() || replyText.isBlank()) {
            return
        }
        val result = AndroidComponentDependencies.smsRepository(context).sendMessage(
            SmsSendRequest(
                address = address,
                body = replyText,
                threadId = threadId.takeIf { it > 0L },
            ),
        )
        if (result.isFailure) {
            // 通知栏回复没有别的反馈通道：失败必须以通知可见。
            helper.showSendFailure(address)
        }
    }

    private fun handleMarkRead(context: Context, threadId: Long) {
        if (threadId <= 0L) {
            return
        }
        AndroidComponentDependencies.smsRepository(context).markThreadRead(threadId)
        AndroidComponentDependencies.smsNotificationHelper(context).cancelForThread(threadId)
    }

    companion object {
        const val ACTION_REPLY = "com.purride.pixellauncherv2.action.SMS_NOTIFICATION_REPLY"
        const val ACTION_MARK_READ = "com.purride.pixellauncherv2.action.SMS_NOTIFICATION_MARK_READ"
        const val EXTRA_THREAD_ID = "extra_thread_id"
        const val EXTRA_ADDRESS = "extra_address"
        const val KEY_REPLY_TEXT = "key_reply_text"
    }
}

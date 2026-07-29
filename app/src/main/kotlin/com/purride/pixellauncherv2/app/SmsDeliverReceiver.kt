package com.purride.pixellauncherv2.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/** 系统在收到短信时创建此接收器；仓库通过 [AndroidComponentDependencies] 边界统一构造。 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 入库与联系人解析都是跨进程 IO，不能占用接收器主线程：
        // goAsync 保活后移到工作线程执行，完成后 finish。
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                // 工作线程的未捕获异常会杀死整个进程：全程兜底，只记日志。
                runCatching {
                    handleIncoming(appContext, intent, pendingResult)
                }.onFailure { Log.w(LOG_TAG, "handle incoming sms failed", it) }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun handleIncoming(
        appContext: Context,
        intent: Intent,
        pendingResult: PendingResult,
    ) {
        val repository = AndroidComponentDependencies.smsRepository(appContext)
        val entry = repository.storeIncomingFromIntent(intent) ?: return
        // 静音会话照常入库，只是不弹通知。
        val muted = AndroidComponentDependencies.smsMuteSettingsRepository(appContext)
            .isMuted(entry.conversationKey)
        if (!muted) {
            // 取该线程最近未读用于通知堆叠；入库失败（threadId <= 0）时自然为空。
            // 必须再按 conversationKey 过滤：服务号会话键取决于每条消息的【来源】
            // 前缀，同一 threadId 可分属多个会话，否则被静音会话的消息会以错误的
            // 发件人身份出现在这条通知里。
            val recentUnread = repository.recentUnreadInboxMessages(
                threadId = entry.threadId,
                limit = 5,
            ).filter { it.conversationKey == entry.conversationKey }
            AndroidComponentDependencies.smsNotificationHelper(appContext)
                .showIncomingMessage(entry, recentUnread)
        }
        pendingResult.resultCode = Telephony.Sms.Intents.RESULT_SMS_HANDLED
    }

    private companion object {
        const val LOG_TAG = "SmsDeliver"
    }
}

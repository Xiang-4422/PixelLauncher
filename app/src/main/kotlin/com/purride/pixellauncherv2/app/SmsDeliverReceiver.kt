package com.purride.pixellauncherv2.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/** 系统在收到短信时创建此接收器；仓库通过 [AndroidComponentDependencies] 边界统一构造。 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 入库与联系人解析都是跨进程 IO，不能占用接收器主线程：
        // goAsync 保活后移到工作线程执行，完成后 finish。
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                val repository = AndroidComponentDependencies.smsRepository(appContext)
                val entry = repository.storeIncomingFromIntent(intent)
                if (entry != null) {
                    // 取该线程最近未读用于通知堆叠；入库失败（threadId <= 0）时自然为空。
                    val recentUnread = repository.recentUnreadInboxMessages(
                        threadId = entry.threadId,
                        limit = 5,
                    )
                    AndroidComponentDependencies.smsNotificationHelper(appContext)
                        .showIncomingMessage(entry, recentUnread)
                    pendingResult.resultCode = Telephony.Sms.Intents.RESULT_SMS_HANDLED
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}

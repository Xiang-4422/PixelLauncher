package com.purride.pixellauncherv2.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.purride.pixellauncherv2.launcher.MmsNotificationModel

/**
 * 彩信到达接收器。本 Launcher 不做彩信下载（与极简定位冲突），但作为默认
 * 短信应用会独占 WAP_PUSH_DELIVER 路由：至少解析出发件人并通知用户，
 * 避免彩信到达后彻底无感知。
 */
class MmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pdu = intent.getByteArrayExtra("data")
        // 联系人解析是跨进程 IO，goAsync 后移到工作线程。
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                val sender = pdu?.let(MmsNotificationModel::extractSender).orEmpty()
                val senderTitle = if (sender.isBlank()) {
                    ""
                } else {
                    AndroidComponentDependencies.smsRepository(appContext)
                        .conversationForAddress(sender)
                        .title
                }
                AndroidComponentDependencies.smsNotificationHelper(appContext)
                    .showUnsupportedMms(senderTitle.ifBlank { sender })
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}

package com.purride.pixellauncherv2.app

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.purride.pixellauncherv2.model.SmsSendRequest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** 系统在“通过消息回复”操作时创建此服务；仓库通过 [AndroidComponentDependencies] 边界统一构造。 */
class RespondViaMessageService : Service() {

    // 发送涉及 SmsManager 与 ContentProvider 写入，不能占用主线程；
    // 单线程执行器保证多条回复按到达顺序串行发送。
    private val sendExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.data?.schemeSpecificPart.orEmpty().substringBefore('?').trim()
        val body = intent?.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent?.getStringExtra("sms_body")
            ?: ""
        if (address.isBlank() || body.isBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        sendExecutor.execute {
            val result = AndroidComponentDependencies.smsRepository(applicationContext).sendMessage(
                SmsSendRequest(
                    address = address,
                    body = body,
                ),
            )
            if (result.isFailure) {
                // 快捷回复没有任何界面：发送失败必须以通知可见，点按可进会话补发。
                AndroidComponentDependencies.smsNotificationHelper(applicationContext)
                    .showSendFailure(address)
            }
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        sendExecutor.shutdown()
        super.onDestroy()
    }
}

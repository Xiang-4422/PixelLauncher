package com.purride.pixellauncherv2.launcher

/**
 * 发送失败的重试策略：区分“临时性”与“终局性”错误。
 *
 * 临时性错误（无服务、飞行模式）标记为 QUEUED，由前台的自动重试队列
 * 周期性补发；其余错误（PDU 异常、通用失败等）标记为 FAILED，等待用户手动重发。
 */
object SmsSendRetryPolicy {

    /** 该错误码是否值得自动重试（网络恢复后大概率能成功）。 */
    fun isTransientError(errorCode: Int): Boolean =
        errorCode == RESULT_ERROR_RADIO_OFF || errorCode == RESULT_ERROR_NO_SERVICE

    /** 自动重试的轮询间隔：Launcher 常驻前台，用固定间隔扫描 QUEUED 消息。 */
    const val RETRY_INTERVAL_MS = 30_000L

    /** android.telephony.SmsManager.RESULT_ERROR_RADIO_OFF = 2（飞行模式/无线电关闭）。 */
    private const val RESULT_ERROR_RADIO_OFF = 2

    /** android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE = 4（无信号/无服务）。 */
    private const val RESULT_ERROR_NO_SERVICE = 4
}

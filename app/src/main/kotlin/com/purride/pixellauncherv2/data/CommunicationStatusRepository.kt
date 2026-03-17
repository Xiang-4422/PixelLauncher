package com.purride.pixellauncherv2.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.provider.CallLog
import android.provider.Telephony
import androidx.core.content.ContextCompat

data class CommunicationStatus(
    val missedCallCount: Int,
    val unreadSmsCount: Int,
)

class CommunicationStatusRepository(
    private val context: Context,
) {

    private val contentResolver = context.contentResolver
    private var callObserver: ContentObserver? = null
    private var smsObserver: ContentObserver? = null

    /** 判断当前是否具备读取未接来电数据的权限。 */
    fun hasCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 判断当前是否具备读取未读短信数据的权限。 */
    fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 启动轻量级内容观察者，让 Home 可以在来电或短信变化时即时刷新，而不是轮询。
     */
    fun start(onStatusChanged: (CommunicationStatus) -> Unit) {
        stop()
        if (hasCallLogPermission()) {
            val observer = object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    onStatusChanged(readStatus())
                }
            }
            callObserver = observer
            contentResolver.registerContentObserver(
                CallLog.Calls.CONTENT_URI,
                true,
                observer,
            )
        }
        if (hasSmsPermission()) {
            val observer = object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    onStatusChanged(readStatus())
                }
            }
            smsObserver = observer
            contentResolver.registerContentObserver(
                Telephony.Sms.Inbox.CONTENT_URI,
                true,
                observer,
            )
        }
        onStatusChanged(readStatus())
    }

    /** 停止已经注册的内容观察者。 */
    fun stop() {
        callObserver?.let(contentResolver::unregisterContentObserver)
        smsObserver?.let(contentResolver::unregisterContentObserver)
        callObserver = null
        smsObserver = null
    }

    /** 读取最新的未接来电数和未读短信数快照。 */
    fun readStatus(): CommunicationStatus {
        return CommunicationStatus(
            missedCallCount = readMissedCallCount(),
            unreadSmsCount = readUnreadSmsCount(),
        )
    }

    private fun readMissedCallCount(): Int {
        if (!hasCallLogPermission()) {
            return 0
        }
        val cursor = try {
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID),
                "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.NEW} = ?",
                arrayOf(
                    CallLog.Calls.MISSED_TYPE.toString(),
                    "1",
                ),
                null,
            )
        } catch (_: SecurityException) {
            null
        } ?: return 0
        cursor.use { queryCursor ->
            return queryCursor.count.coerceAtLeast(0)
        }
    }

    private fun readUnreadSmsCount(): Int {
        if (!hasSmsPermission()) {
            return 0
        }
        val cursor = try {
            contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms.READ} = ?",
                arrayOf("0"),
                null,
            )
        } catch (_: SecurityException) {
            null
        } ?: return 0
        cursor.use { queryCursor ->
            return queryCursor.count.coerceAtLeast(0)
        }
    }
}

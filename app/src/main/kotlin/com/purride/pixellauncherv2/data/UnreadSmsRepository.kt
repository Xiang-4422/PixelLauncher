package com.purride.pixellauncherv2.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat

data class UnreadSmsEntry(
    val messageId: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val dateMillis: Long,
)

class UnreadSmsRepository(
    private val context: Context,
) {

    private val contentResolver = context.contentResolver

    /** 读取当前所有未读短信，按时间倒序返回。 */
    fun readUnreadMessages(): List<UnreadSmsEntry> {
        if (!hasSmsPermission()) {
            return emptyList()
        }
        val cursor = try {
            contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.THREAD_ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                ),
                "${Telephony.Sms.READ} = ?",
                arrayOf("0"),
                "${Telephony.Sms.DATE} DESC",
            )
        } catch (_: SecurityException) {
            null
        } ?: return emptyList()

        cursor.use { queryCursor ->
            val idIndex = queryCursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val threadIdIndex = queryCursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val addressIndex = queryCursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = queryCursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = queryCursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

            val entries = ArrayList<UnreadSmsEntry>(queryCursor.count.coerceAtLeast(0))
            while (queryCursor.moveToNext()) {
                entries += UnreadSmsEntry(
                    messageId = queryCursor.getLong(idIndex),
                    threadId = queryCursor.getLong(threadIdIndex),
                    address = queryCursor.getString(addressIndex).orEmpty(),
                    body = queryCursor.getString(bodyIndex).orEmpty(),
                    dateMillis = queryCursor.getLong(dateIndex),
                )
            }
            return entries
        }
    }

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}

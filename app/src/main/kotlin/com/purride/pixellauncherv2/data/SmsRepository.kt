package com.purride.pixellauncherv2.data

import android.Manifest
import android.app.role.RoleManager
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.purride.pixellauncherv2.launcher.SmsPermissionState
import java.util.Locale

data class SmsThreadSummary(
    val threadId: Long,
    val address: String,
    val snippet: String,
    val dateMillis: Long,
    val unreadCount: Int,
    val messageCount: Int,
)

data class SmsMessageEntry(
    val messageId: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val dateMillis: Long,
    val type: Int,
    val isRead: Boolean,
)

data class SmsSendRequest(
    val address: String,
    val body: String,
    val threadId: Long? = null,
)

class SmsRepository(
    private val context: Context,
) {

    private val contentResolver: ContentResolver = context.contentResolver
    private var smsObserver: ContentObserver? = null

    fun hasReadSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasSendSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasReceiveSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isDefaultSmsApp(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true) {
                return roleManager.isRoleHeld(RoleManager.ROLE_SMS)
            }
        }
        return Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }

    fun permissionState(): SmsPermissionState {
        return when {
            hasReadSmsPermission() && hasSendSmsPermission() && hasReceiveSmsPermission() && isDefaultSmsApp() ->
                SmsPermissionState.READY

            hasReadSmsPermission() -> SmsPermissionState.READ_ONLY
            else -> SmsPermissionState.MISSING
        }
    }

    fun buildDefaultSmsRoleIntent(): Intent? {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
                roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            }

            else -> {
                Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                    putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                }
            }
        }
    }

    fun start(onChanged: () -> Unit) {
        stop()
        if (!hasReadSmsPermission()) {
            onChanged()
            return
        }
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                onChanged()
            }
        }
        smsObserver = observer
        contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            true,
            observer,
        )
        onChanged()
    }

    fun stop() {
        smsObserver?.let(contentResolver::unregisterContentObserver)
        smsObserver = null
    }

    fun readThreads(): List<SmsThreadSummary> {
        if (!hasReadSmsPermission()) {
            return emptyList()
        }
        val cursor = try {
            contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.THREAD_ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.READ,
                    Telephony.Sms.TYPE,
                ),
                null,
                null,
                "${Telephony.Sms.DATE} DESC",
            )
        } catch (_: SecurityException) {
            null
        } ?: return emptyList()

        cursor.use { queryCursor ->
            val idThread = queryCursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val idAddress = queryCursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val idBody = queryCursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val idDate = queryCursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val idRead = queryCursor.getColumnIndexOrThrow(Telephony.Sms.READ)

            val threads = LinkedHashMap<Long, MutableSmsThread>()
            while (queryCursor.moveToNext()) {
                val threadId = queryCursor.getLong(idThread)
                val address = queryCursor.getString(idAddress).orEmpty()
                val body = queryCursor.getString(idBody).orEmpty()
                val date = queryCursor.getLong(idDate)
                val isRead = queryCursor.getInt(idRead) != 0

                val aggregate = threads.getOrPut(threadId) {
                    MutableSmsThread(
                        threadId = threadId,
                        address = address,
                        snippet = body,
                        dateMillis = date,
                    )
                }
                aggregate.messageCount += 1
                if (!isRead) {
                    aggregate.unreadCount += 1
                }
            }
            return threads.values.map { thread ->
                SmsThreadSummary(
                    threadId = thread.threadId,
                    address = thread.address.ifBlank { "UNKNOWN" },
                    snippet = thread.snippet,
                    dateMillis = thread.dateMillis,
                    unreadCount = thread.unreadCount,
                    messageCount = thread.messageCount,
                )
            }
        }
    }

    fun readThreadMessages(threadId: Long): List<SmsMessageEntry> {
        if (!hasReadSmsPermission()) {
            return emptyList()
        }
        val cursor = try {
            contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.THREAD_ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                    Telephony.Sms.READ,
                ),
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} ASC",
            )
        } catch (_: SecurityException) {
            null
        } ?: return emptyList()

        cursor.use { queryCursor ->
            val idMessage = queryCursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val idThread = queryCursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val idAddress = queryCursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val idBody = queryCursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val idDate = queryCursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val idType = queryCursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val idRead = queryCursor.getColumnIndexOrThrow(Telephony.Sms.READ)
            val entries = ArrayList<SmsMessageEntry>(queryCursor.count.coerceAtLeast(0))
            while (queryCursor.moveToNext()) {
                entries += SmsMessageEntry(
                    messageId = queryCursor.getLong(idMessage),
                    threadId = queryCursor.getLong(idThread),
                    address = queryCursor.getString(idAddress).orEmpty(),
                    body = queryCursor.getString(idBody).orEmpty(),
                    dateMillis = queryCursor.getLong(idDate),
                    type = queryCursor.getInt(idType),
                    isRead = queryCursor.getInt(idRead) != 0,
                )
            }
            return entries
        }
    }

    fun findThreadForAddress(address: String): SmsThreadSummary? {
        val normalizedTarget = normalizeAddress(address)
        return readThreads().firstOrNull { normalizeAddress(it.address) == normalizedTarget }
    }

    fun markThreadRead(threadId: Long): Boolean {
        if (!isDefaultSmsApp()) {
            Log.d(LOG_TAG, "markThreadRead skipped: not default sms app threadId=$threadId")
            return false
        }
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        return try {
            val updatedRows = contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString()),
            )
            Log.d(LOG_TAG, "markThreadRead threadId=$threadId updatedRows=$updatedRows")
            updatedRows > 0
        } catch (_: SecurityException) {
            Log.d(LOG_TAG, "markThreadRead security exception threadId=$threadId")
            false
        }
    }

    fun sendMessage(request: SmsSendRequest): Result<SmsMessageEntry> {
        val address = request.address.trim()
        val body = request.body.trim()
        if (address.isBlank() || body.isBlank()) {
            return Result.failure(IllegalArgumentException("Address or body is blank"))
        }
        if (!hasSendSmsPermission()) {
            return Result.failure(SecurityException("Missing SEND_SMS permission"))
        }
        return runCatching {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            } ?: error("SmsManager unavailable")
            val parts = smsManager.divideMessage(body)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(address, null, ArrayList(parts), null, null)
            } else {
                smsManager.sendTextMessage(address, null, body, null, null)
            }

            val now = System.currentTimeMillis()
            val threadId = request.threadId ?: resolveThreadId(address)
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, now)
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                if (threadId > 0L) {
                    put(Telephony.Sms.THREAD_ID, threadId)
                }
            }
            val uri = contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            val messageId = uri?.lastPathSegment?.toLongOrNull() ?: -1L
            SmsMessageEntry(
                messageId = messageId,
                threadId = threadId,
                address = address,
                body = body,
                dateMillis = now,
                type = Telephony.Sms.MESSAGE_TYPE_SENT,
                isRead = true,
            )
        }
    }

    fun storeIncomingFromIntent(intent: Intent): SmsMessageEntry? {
        if (!isDefaultSmsApp()) {
            return null
        }
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) {
            return null
        }
        val address = messages.firstOrNull()?.originatingAddress.orEmpty()
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        val dateMillis = messages.firstOrNull()?.timestampMillis?.takeIf { it > 0L } ?: System.currentTimeMillis()
        return insertIncomingMessage(address = address, body = body, dateMillis = dateMillis)
    }

    fun insertIncomingMessage(
        address: String,
        body: String,
        dateMillis: Long = System.currentTimeMillis(),
    ): SmsMessageEntry? {
        if (!isDefaultSmsApp()) {
            return null
        }
        return runCatching {
            val threadId = resolveThreadId(address)
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, dateMillis)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.SEEN, 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                if (threadId > 0L) {
                    put(Telephony.Sms.THREAD_ID, threadId)
                }
            }
            val uri = contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            val messageId = uri?.lastPathSegment?.toLongOrNull() ?: -1L
            SmsMessageEntry(
                messageId = messageId,
                threadId = threadId,
                address = address,
                body = body,
                dateMillis = dateMillis,
                type = Telephony.Sms.MESSAGE_TYPE_INBOX,
                isRead = false,
            )
        }.getOrNull()
    }

    private fun resolveThreadId(address: String): Long {
        return runCatching {
            Telephony.Threads.getOrCreateThreadId(context, address)
        }.getOrDefault(-1L)
    }

    private fun normalizeAddress(address: String): String {
        return buildString {
            address.forEach { ch ->
                if (ch.isDigit() || ch == '+') {
                    append(ch)
                }
            }
        }.ifBlank { address.trim().uppercase(Locale.US) }
    }

    private data class MutableSmsThread(
        val threadId: Long,
        val address: String,
        val snippet: String,
        val dateMillis: Long,
        var unreadCount: Int = 0,
        var messageCount: Int = 0,
    )

    companion object {
        private const val LOG_TAG = "SmsRepo"
    }
}

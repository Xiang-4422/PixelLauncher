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
import com.purride.pixellauncherv2.launcher.SmsConversationIdentity
import com.purride.pixellauncherv2.launcher.SmsConversationModel
import com.purride.pixellauncherv2.launcher.SmsPermissionState

data class SmsThreadSummary(
    val threadId: Long,
    val address: String,
    val snippet: String,
    val dateMillis: Long,
    val unreadCount: Int,
    val messageCount: Int,
    val displayName: String = "",
    val conversationKey: String = "thread:$threadId",
    val isServiceConversation: Boolean = false,
)

data class SmsMessageEntry(
    val messageId: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val dateMillis: Long,
    val type: Int,
    val isRead: Boolean,
    val displayName: String = "",
    val conversationKey: String = "thread:$threadId",
    val conversationTitle: String = displayName.ifBlank { address },
    val isServiceConversation: Boolean = false,
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
    private val contactResolver = SmsContactResolver(context)
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

    fun hasReadContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS,
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

    fun readMessages(): List<SmsMessageEntry> {
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
            val idMessage = queryCursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val idThread = queryCursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val idAddress = queryCursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val idBody = queryCursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val idDate = queryCursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val idRead = queryCursor.getColumnIndexOrThrow(Telephony.Sms.READ)
            val idType = queryCursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val messages = ArrayList<SmsMessageEntry>(queryCursor.count.coerceAtLeast(0))
            while (queryCursor.moveToNext()) {
                val address = queryCursor.getString(idAddress).orEmpty()
                val body = queryCursor.getString(idBody).orEmpty()
                messages += buildMessageEntry(
                    messageId = queryCursor.getLong(idMessage),
                    threadId = queryCursor.getLong(idThread),
                    address = address,
                    body = body,
                    dateMillis = queryCursor.getLong(idDate),
                    type = queryCursor.getInt(idType),
                    isRead = queryCursor.getInt(idRead) != 0,
                )
            }
            return messages
        }
    }

    fun conversationForAddress(address: String): SmsConversationIdentity {
        return SmsConversationModel.identify(
            address = address,
            body = "",
            contactName = contactResolver.displayName(address),
            allowSource = false,
        )
    }

    fun markMessagesRead(messageIds: Collection<Long>): Boolean {
        if (!isDefaultSmsApp() || messageIds.isEmpty()) {
            return false
        }
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        return runCatching {
            messageIds.distinct().chunked(500).sumOf { ids ->
                val placeholders = ids.joinToString(",") { "?" }
                contentResolver.update(
                    Telephony.Sms.CONTENT_URI,
                    values,
                    "${Telephony.Sms._ID} IN ($placeholders) AND ${Telephony.Sms.READ} = 0",
                    ids.map(Long::toString).toTypedArray(),
                )
            } > 0
        }.getOrDefault(false)
    }

    fun markAllRead(): Boolean {
        if (!isDefaultSmsApp()) {
            Log.d(LOG_TAG, "markAllRead skipped: not default sms app")
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
                "${Telephony.Sms.READ} = 0",
                null,
            )
            Log.d(LOG_TAG, "markAllRead updatedRows=$updatedRows")
            updatedRows > 0
        } catch (_: SecurityException) {
            Log.d(LOG_TAG, "markAllRead security exception")
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
            buildMessageEntry(
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
            buildMessageEntry(
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

    private fun buildMessageEntry(
        messageId: Long,
        threadId: Long,
        address: String,
        body: String,
        dateMillis: Long,
        type: Int,
        isRead: Boolean,
    ): SmsMessageEntry {
        val displayName = contactResolver.displayName(address)
        val conversation = SmsConversationModel.identify(
            address = address,
            body = body,
            contactName = displayName,
            allowSource = type != Telephony.Sms.MESSAGE_TYPE_SENT,
        )
        val displayBody = if (conversation.isService) SmsConversationModel.stripLeadingSource(body) else body
        return SmsMessageEntry(
            messageId = messageId,
            threadId = threadId,
            address = address,
            body = displayBody,
            dateMillis = dateMillis,
            type = type,
            isRead = isRead,
            displayName = displayName,
            conversationKey = conversation.key,
            conversationTitle = conversation.title,
            isServiceConversation = conversation.isService,
        )
    }

    private fun resolveThreadId(address: String): Long {
        return runCatching {
            Telephony.Threads.getOrCreateThreadId(context, address)
        }.getOrDefault(-1L)
    }

    companion object {
        private const val LOG_TAG = "SmsRepo"
    }
}

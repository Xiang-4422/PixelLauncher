package com.purride.pixellauncherv2.data

import android.Manifest
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.purride.pixellauncherv2.app.SmsSendResultReceiver
import com.purride.pixellauncherv2.launcher.SmsConversationIdentity
import com.purride.pixellauncherv2.launcher.SmsConversationModel
import com.purride.pixellauncherv2.launcher.SmsPermissionState
import com.purride.pixellauncherv2.model.SmsMessageEntry
import com.purride.pixellauncherv2.model.SmsSendRequest

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
        // 回调统一投递到主线程：onChanged 会读写主线程持有的 Launcher 状态，
        // 传 null Handler 时 onChange 在 Binder 线程回调，存在丢更新竞态。
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
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

            // 先落一条 OUTBOX 记录并对最后一个分段挂发送回执：回执到达后由
            // SmsSendResultReceiver 把该记录更新为 SENT/FAILED，UI 才能呈现真实结果。
            val now = System.currentTimeMillis()
            val threadId = request.threadId ?: resolveThreadId(address)
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, now)
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
                if (threadId > 0L) {
                    put(Telephony.Sms.THREAD_ID, threadId)
                }
            }
            val uri = contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values)
            val messageId = uri?.lastPathSegment?.toLongOrNull() ?: -1L
            // 非默认短信应用时 OUTBOX 落库会失败（messageId <= 0），此时不挂回执，
            // 行为退化为“发出即认为成功”，与仅有 SEND_SMS 权限的场景保持一致。
            val sentIntent = if (messageId > 0L) buildSentPendingIntent(messageId) else null

            val parts = smsManager.divideMessage(body)
            try {
                if (parts.size > 1) {
                    val sentIntents = ArrayList<PendingIntent?>(parts.size)
                    repeat(parts.size - 1) { sentIntents.add(null) }
                    sentIntents.add(sentIntent)
                    smsManager.sendMultipartTextMessage(address, null, ArrayList(parts), sentIntents, null)
                } else {
                    smsManager.sendTextMessage(address, null, body, sentIntent, null)
                }
            } catch (t: Throwable) {
                // 提交给无线电层都没成功，回执永远不会来：把刚落的 OUTBOX 记录
                // 标成 FAILED，避免它永远停留在 SENDING。
                if (messageId > 0L) {
                    applySendResult(messageId, success = false, errorCode = SEND_ERROR_SUBMIT)
                }
                throw t
            }

            buildMessageEntry(
                messageId = messageId,
                threadId = threadId,
                address = address,
                body = body,
                dateMillis = now,
                type = if (messageId > 0L) {
                    Telephony.Sms.MESSAGE_TYPE_OUTBOX
                } else {
                    Telephony.Sms.MESSAGE_TYPE_SENT
                },
                isRead = true,
            )
        }
    }

    /** 发送回执到达后更新消息状态；成功 → SENT，失败 → FAILED 并记录错误码。 */
    fun applySendResult(messageId: Long, success: Boolean, errorCode: Int): Boolean {
        if (messageId <= 0L) {
            return false
        }
        val values = ContentValues().apply {
            put(
                Telephony.Sms.TYPE,
                if (success) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_FAILED,
            )
            if (!success) {
                put(Telephony.Sms.ERROR_CODE, errorCode)
            }
        }
        return runCatching {
            contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms._ID} = ?",
                arrayOf(messageId.toString()),
            ) > 0
        }.getOrDefault(false)
    }

    /** 删除单条消息（重发失败消息前清理旧记录用）；仅默认短信应用可写。 */
    fun deleteMessage(messageId: Long): Boolean {
        if (!isDefaultSmsApp() || messageId <= 0L) {
            return false
        }
        return runCatching {
            contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms._ID} = ?",
                arrayOf(messageId.toString()),
            ) > 0
        }.getOrDefault(false)
    }

    private fun buildSentPendingIntent(messageId: Long): PendingIntent {
        val intent = Intent(context, SmsSendResultReceiver::class.java)
            .setAction(SmsSendResultReceiver.ACTION_SMS_SENT)
            .putExtra(SmsSendResultReceiver.EXTRA_MESSAGE_ID, messageId)
        return PendingIntent.getBroadcast(
            context,
            messageId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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

        /** 本地哨兵错误码：消息尚未提交给无线电层即抛异常。 */
        private const val SEND_ERROR_SUBMIT = -1
    }
}

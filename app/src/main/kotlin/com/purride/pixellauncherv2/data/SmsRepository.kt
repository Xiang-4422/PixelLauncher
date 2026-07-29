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
import com.purride.pixellauncherv2.launcher.SmsMessageStatusModel
import com.purride.pixellauncherv2.launcher.SmsSendRetryPolicy
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
        // 只取会话流可展示的类型（收件 + 发出方向），过滤历史遗留的草稿等记录。
        val visibleTypes = SmsMessageStatusModel.conversationTypes
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
                    Telephony.Sms.STATUS,
                    Telephony.Sms.SUBSCRIPTION_ID,
                ),
                "${Telephony.Sms.TYPE} IN (${visibleTypes.joinToString(",") { "?" }})",
                visibleTypes.map(Int::toString).toTypedArray(),
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
            val idStatus = queryCursor.getColumnIndexOrThrow(Telephony.Sms.STATUS)
            val idSubscription = queryCursor.getColumnIndexOrThrow(Telephony.Sms.SUBSCRIPTION_ID)
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
                    deliveryStatus = queryCursor.getInt(idStatus),
                    subscriptionId = queryCursor.getInt(idSubscription),
                )
            }
            return messages
        }
    }

    /**
     * 定向取某线程最近 [limit] 条未读收件消息（通知堆叠用），按时间升序返回。
     * 不走全表扫描；无权限或线程无效时返回空。
     */
    fun recentUnreadInboxMessages(threadId: Long, limit: Int): List<SmsMessageEntry> {
        if (threadId <= 0L || limit <= 0 || !hasReadSmsPermission()) {
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
                    Telephony.Sms.STATUS,
                    Telephony.Sms.SUBSCRIPTION_ID,
                ),
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0 " +
                    "AND ${Telephony.Sms.TYPE} = ${Telephony.Sms.MESSAGE_TYPE_INBOX}",
                arrayOf(threadId.toString()),
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
            val idStatus = queryCursor.getColumnIndexOrThrow(Telephony.Sms.STATUS)
            val idSubscription = queryCursor.getColumnIndexOrThrow(Telephony.Sms.SUBSCRIPTION_ID)
            val messages = ArrayList<SmsMessageEntry>(limit)
            while (queryCursor.moveToNext() && messages.size < limit) {
                messages += buildMessageEntry(
                    messageId = queryCursor.getLong(idMessage),
                    threadId = queryCursor.getLong(idThread),
                    address = queryCursor.getString(idAddress).orEmpty(),
                    body = queryCursor.getString(idBody).orEmpty(),
                    dateMillis = queryCursor.getLong(idDate),
                    type = queryCursor.getInt(idType),
                    isRead = queryCursor.getInt(idRead) != 0,
                    deliveryStatus = queryCursor.getInt(idStatus),
                    subscriptionId = queryCursor.getInt(idSubscription),
                )
            }
            return messages.sortedBy(SmsMessageEntry::dateMillis)
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

    /** 将某会话线程的收件消息全部置为已读（通知栏 READ 操作用）。 */
    fun markThreadRead(threadId: Long): Boolean {
        if (!isDefaultSmsApp() || threadId <= 0L) {
            return false
        }
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        return runCatching {
            contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0 " +
                    "AND ${Telephony.Sms.TYPE} = ${Telephony.Sms.MESSAGE_TYPE_INBOX}",
                arrayOf(threadId.toString()),
            ) > 0
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
            // 只把收件消息置为已读，与首页未读角标的统计口径（收件箱 READ=0）一致。
            val updatedRows = contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.READ} = 0 AND ${Telephony.Sms.TYPE} = ${Telephony.Sms.MESSAGE_TYPE_INBOX}",
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
            val smsManager = resolveSmsManager(request.subscriptionId)
                ?: error("SmsManager unavailable")

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
                // 记录发送用的 SIM，自动重试与后续回复能保持同卡。
                request.subscriptionId?.takeIf { it >= 0 }?.let {
                    put(Telephony.Sms.SUBSCRIPTION_ID, it)
                }
            }
            val uri = contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values)
            val messageId = uri?.lastPathSegment?.toLongOrNull() ?: -1L
            // 非默认短信应用时 OUTBOX 落库会失败（messageId <= 0），此时不挂回执，
            // 行为退化为“发出即认为成功”，与仅有 SEND_SMS 权限的场景保持一致。
            val sentIntent = if (messageId > 0L) {
                buildResultPendingIntent(messageId, now, SmsSendResultReceiver.ACTION_SMS_SENT)
            } else {
                null
            }
            val deliveryIntent = if (messageId > 0L) {
                buildResultPendingIntent(messageId, now, SmsSendResultReceiver.ACTION_SMS_DELIVERED)
            } else {
                null
            }

            val parts = smsManager.divideMessage(body)
            try {
                if (parts.size > 1) {
                    // 每个分段都挂同一个回执：任一分段失败都会把整条判为失败
                    // （applySendResult 的类型限定保证失败优先、成功不可回翻）。
                    val sentIntents = ArrayList<PendingIntent?>(parts.size)
                    val deliveryIntents = ArrayList<PendingIntent?>(parts.size)
                    repeat(parts.size) {
                        sentIntents.add(sentIntent)
                        deliveryIntents.add(deliveryIntent)
                    }
                    smsManager.sendMultipartTextMessage(
                        address,
                        null,
                        ArrayList(parts),
                        sentIntents,
                        deliveryIntents,
                    )
                } else {
                    smsManager.sendTextMessage(address, null, body, sentIntent, deliveryIntent)
                }
            } catch (t: Throwable) {
                // 提交给无线电层都没成功，回执永远不会来：把刚落的 OUTBOX 记录
                // 标成 FAILED，避免它永远停留在 SENDING。
                if (messageId > 0L) {
                    applySendResult(
                        messageId = messageId,
                        dateMillis = now,
                        success = false,
                        errorCode = SEND_ERROR_SUBMIT,
                    )
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

    /**
     * 发送回执到达后更新消息状态；成功 → SENT，临时性错误（无服务/飞行模式）
     * → QUEUED 等待自动重试，其余错误 → FAILED 并记录错误码。
     */
    fun applySendResult(
        messageId: Long,
        dateMillis: Long,
        success: Boolean,
        errorCode: Int,
    ): Boolean {
        if (messageId <= 0L) {
            return false
        }
        val values = ContentValues().apply {
            put(
                Telephony.Sms.TYPE,
                when {
                    success -> Telephony.Sms.MESSAGE_TYPE_SENT
                    SmsSendRetryPolicy.isTransientError(errorCode) -> Telephony.Sms.MESSAGE_TYPE_QUEUED
                    else -> Telephony.Sms.MESSAGE_TYPE_FAILED
                },
            )
            if (!success) {
                put(Telephony.Sms.ERROR_CODE, errorCode)
            }
        }
        // 类型限定双保险：成功只允许 OUTBOX→SENT（分段失败后不被后续成功回翻）；
        // 失败可覆盖 OUTBOX/SENT（失败优先）。同时避免旧回执污染复用 rowid 的新行。
        val allowedTypes = if (success) {
            "${Telephony.Sms.TYPE} = ${Telephony.Sms.MESSAGE_TYPE_OUTBOX}"
        } else {
            "${Telephony.Sms.TYPE} IN (${Telephony.Sms.MESSAGE_TYPE_OUTBOX}, ${Telephony.Sms.MESSAGE_TYPE_SENT})"
        }
        // date 一并入条件：认领式重发会插入复用同一 rowid 的新行，只靠 _id
        // 无法区分上一次发送迟到的回执。dateMillis <= 0 时退化为只按 _id 匹配。
        val dateClause = if (dateMillis > 0L) " AND ${Telephony.Sms.DATE} = ?" else ""
        val selectionArgs = if (dateMillis > 0L) {
            arrayOf(messageId.toString(), dateMillis.toString())
        } else {
            arrayOf(messageId.toString())
        }
        return runCatching {
            contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms._ID} = ? AND $allowedTypes$dateClause",
                selectionArgs,
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

    /**
     * 批量删除消息（删除整个会话用）。按 500 条分块走 IN 子句，
     * 与 markMessagesRead 的分块模式一致；仅默认短信应用可写。
     */
    fun deleteMessages(messageIds: Collection<Long>): Boolean {
        if (!isDefaultSmsApp() || messageIds.isEmpty()) {
            return false
        }
        return runCatching {
            messageIds.distinct().filter { it > 0L }.chunked(500).sumOf { ids ->
                val placeholders = ids.joinToString(",") { "?" }
                contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "${Telephony.Sms._ID} IN ($placeholders)",
                    ids.map(Long::toString).toTypedArray(),
                )
            } > 0
        }.getOrDefault(false)
    }

    /** 送达回执到达后更新 STATUS 列（详情页显示 DELIVERED 用）。 */
    fun applyDeliveryResult(messageId: Long, dateMillis: Long, delivered: Boolean): Boolean {
        if (messageId <= 0L || !delivered) {
            return false
        }
        val values = ContentValues().apply {
            put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_COMPLETE)
        }
        val dateClause = if (dateMillis > 0L) " AND ${Telephony.Sms.DATE} = ?" else ""
        val selectionArgs = if (dateMillis > 0L) {
            arrayOf(messageId.toString(), dateMillis.toString())
        } else {
            arrayOf(messageId.toString())
        }
        return runCatching {
            contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms._ID} = ? AND ${Telephony.Sms.TYPE} = ${Telephony.Sms.MESSAGE_TYPE_SENT}$dateClause",
                selectionArgs,
            ) > 0
        }.getOrDefault(false)
    }

    /**
     * 启动对账：把滞留超过 [olderThanMillis] 的 OUTBOX 记录判为 FAILED。
     * 回执广播极端情况下可能丢失（系统被杀），没有对账会让消息永远停在“发送中”。
     */
    fun failStaleOutboxMessages(olderThanMillis: Long): Boolean {
        if (!isDefaultSmsApp()) {
            return false
        }
        val cutoff = System.currentTimeMillis() - olderThanMillis
        val values = ContentValues().apply {
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED)
            put(Telephony.Sms.ERROR_CODE, SEND_ERROR_RECEIPT_LOST)
        }
        return runCatching {
            contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.TYPE} = ${Telephony.Sms.MESSAGE_TYPE_OUTBOX} AND ${Telephony.Sms.DATE} < ?",
                arrayOf(cutoff.toString()),
            ) > 0
        }.getOrDefault(false)
    }

    /**
     * 解析发送用的 SmsManager：指定了有效订阅 id 时用对应 SIM（双卡同卡回复），
     * 否则用系统默认 SIM。订阅已失效（换卡）时回退默认。
     */
    private fun resolveSmsManager(subscriptionId: Int?): SmsManager? {
        val default = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        if (subscriptionId == null || subscriptionId < 0) {
            return default
        }
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                default?.createForSubscriptionId(subscriptionId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            }
        }.getOrNull() ?: default
    }

    private fun buildResultPendingIntent(
        messageId: Long,
        dateMillis: Long,
        action: String,
    ): PendingIntent {
        val intent = Intent(context, SmsSendResultReceiver::class.java)
            .setAction(action)
            // 插入时间戳同时放进 data：PendingIntent 的相等性只看 filterEquals
            // （忽略 extras），只有 data 不同才能保证上一次发送的在途回执不会被
            // FLAG_UPDATE_CURRENT 改写成新一次的 token。
            .setData(Uri.parse("pixellauncher-sms://receipt/$messageId/$dateMillis"))
            .putExtra(SmsSendResultReceiver.EXTRA_MESSAGE_ID, messageId)
            .putExtra(SmsSendResultReceiver.EXTRA_MESSAGE_DATE, dateMillis)
        return PendingIntent.getBroadcast(
            context,
            messageId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun storeIncomingFromIntent(intent: Intent): SmsMessageEntry? {
        // 平台返回值可能为 null 数组或含 null 元素（畸形 PDU），防御后再用。
        val messages = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }
            .getOrNull()
            ?.filterNotNull()
            .orEmpty()
        if (messages.isEmpty()) {
            return null
        }
        val address = messages.firstOrNull()?.originatingAddress.orEmpty()
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        val dateMillis = messages.firstOrNull()?.timestampMillis?.takeIf { it > 0L } ?: System.currentTimeMillis()
        // SMS_DELIVER 附带来信 SIM 的订阅 id，记录后回复可保持同卡。
        val subscriptionId = intent.getIntExtra("subscription", -1)
        insertIncomingMessage(
            address = address,
            body = body,
            dateMillis = dateMillis,
            subscriptionId = subscriptionId,
        )?.let { return it }
        // 入库失败（异常或默认应用角色竞态）也不能吞掉来信：
        // 返回未落库的条目，至少保证通知可见。
        Log.w(LOG_TAG, "storeIncomingFromIntent: insert failed, notify without persistence")
        return buildMessageEntry(
            messageId = -1L,
            threadId = -1L,
            address = address,
            body = body,
            dateMillis = dateMillis,
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            isRead = false,
        )
    }

    fun insertIncomingMessage(
        address: String,
        body: String,
        dateMillis: Long = System.currentTimeMillis(),
        subscriptionId: Int = -1,
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
                if (subscriptionId >= 0) {
                    put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
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
        deliveryStatus: Int = -1,
        subscriptionId: Int = -1,
    ): SmsMessageEntry {
        val displayName = contactResolver.displayName(address)
        val conversation = SmsConversationModel.identify(
            address = address,
            body = body,
            contactName = displayName,
            // 发出方向一律不做服务号来源识别：以【】开头的外发消息不能被剥前缀
            // 或归入服务号会话，否则重发会用失真的展示正文。
            allowSource = !SmsMessageStatusModel.isOutgoing(type),
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
            deliveryStatus = deliveryStatus,
            subscriptionId = subscriptionId,
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

        /** 本地哨兵错误码：OUTBOX 滞留超时，回执判定为已丢失。 */
        private const val SEND_ERROR_RECEIPT_LOST = -2
    }
}

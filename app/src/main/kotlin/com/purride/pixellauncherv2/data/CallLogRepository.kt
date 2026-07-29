package com.purride.pixellauncherv2.data

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.util.Log
import androidx.core.content.ContextCompat
import com.purride.pixellauncherv2.model.CallLogEntry

/**
 * 系统通话记录的读写。
 *
 * 与 [SmsRepository] 保持同一套约定：内容观察者经主线程 Handler 回调（回调方
 * 会读写主线程持有的 Launcher 状态），权限缺失与 SecurityException 一律降级为
 * 空结果而不抛出。写操作（清除未接标记、删除记录）需要 WRITE_CALL_LOG。
 */
class CallLogRepository(
    private val context: Context,
) {

    private val contentResolver: ContentResolver = context.contentResolver
    private val contactResolver = ContactNameResolver(context)
    private var callObserver: ContentObserver? = null

    fun hasReadCallLogPermission(): Boolean = hasPermission(Manifest.permission.READ_CALL_LOG)

    fun hasWriteCallLogPermission(): Boolean = hasPermission(Manifest.permission.WRITE_CALL_LOG)

    /** 前台时监听通话记录变化。 */
    fun start(onChanged: () -> Unit) {
        stop()
        if (!hasReadCallLogPermission()) {
            onChanged()
            return
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                onChanged()
            }
        }
        callObserver = observer
        contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
        onChanged()
    }

    fun stop() {
        callObserver?.let(contentResolver::unregisterContentObserver)
        callObserver = null
    }

    /**
     * 读取最近 [limit] 条通话记录，按时间倒序。
     *
     * 通话记录只用于展示最近通话，无需全量读取：用 LIMIT 收敛游标规模，
     * 避免长期使用后一次读进上万条。
     */
    fun readRecentCalls(limit: Int = DEFAULT_LIMIT): List<CallLogEntry> {
        if (!hasReadCallLogPermission() || limit <= 0) {
            return emptyList()
        }
        // 条数上限必须走 URI 的 limit 查询参数：把 "LIMIT n" 拼进 sortOrder 会被
        // 通话记录 provider 的排序串校验拒绝（实机 Android 12 抛 Invalid token LIMIT）。
        val limitedUri = CallLog.Calls.CONTENT_URI.buildUpon()
            .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, limit.toString())
            .build()
        val cursor = try {
            contentResolver.query(
                limitedUri,
                arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.NEW,
                    CallLog.Calls.PHONE_ACCOUNT_ID,
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC",
            )
        } catch (error: Throwable) {
            // 各家 ROM 的通话记录 provider 行为差异很大：读不到就当空列表，
            // 绝不能让一次查询异常把 Launcher 进程带走。
            Log.w(LOG_TAG, "readRecentCalls failed", error)
            null
        } ?: return emptyList()

        cursor.use { queryCursor ->
            val idCall = queryCursor.getColumnIndexOrThrow(CallLog.Calls._ID)
            val idNumber = queryCursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val idCachedName = queryCursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val idDate = queryCursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val idDuration = queryCursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            val idType = queryCursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val idNew = queryCursor.getColumnIndexOrThrow(CallLog.Calls.NEW)
            val idAccount = queryCursor.getColumnIndexOrThrow(CallLog.Calls.PHONE_ACCOUNT_ID)
            val entries = ArrayList<CallLogEntry>(queryCursor.count.coerceAtLeast(0))
            while (queryCursor.moveToNext()) {
                val number = queryCursor.getString(idNumber).orEmpty()
                // 通话记录自带的缓存名可能是旧的或空的：为空时再查联系人（结果有缓存）。
                val cachedName = queryCursor.getString(idCachedName).orEmpty()
                val displayName = cachedName.ifBlank {
                    if (number.isBlank()) "" else contactResolver.displayName(number)
                }
                entries += CallLogEntry(
                    callId = queryCursor.getLong(idCall),
                    number = number,
                    dateMillis = queryCursor.getLong(idDate),
                    durationSeconds = queryCursor.getLong(idDuration),
                    type = queryCursor.getInt(idType),
                    isNew = queryCursor.getInt(idNew) != 0,
                    displayName = displayName,
                    subscriptionId = queryCursor.getString(idAccount)?.toIntOrNull() ?: -1,
                )
            }
            return entries
        }
    }

    /**
     * 把指定记录标记为已确认（NEW=0），用于进入通话记录页后清除未接来电角标。
     * 同时清 IS_READ，与系统拨号应用的行为一致。
     */
    fun markCallsAcknowledged(callIds: Collection<Long>): Boolean {
        if (!hasWriteCallLogPermission() || callIds.isEmpty()) {
            return false
        }
        val values = ContentValues().apply {
            put(CallLog.Calls.NEW, 0)
            put(CallLog.Calls.IS_READ, 1)
        }
        return runCatching {
            callIds.distinct().chunked(CHUNK_SIZE).sumOf { ids ->
                val placeholders = ids.joinToString(",") { "?" }
                contentResolver.update(
                    CallLog.Calls.CONTENT_URI,
                    values,
                    "${CallLog.Calls._ID} IN ($placeholders) AND ${CallLog.Calls.NEW} != 0",
                    ids.map(Long::toString).toTypedArray(),
                )
            } > 0
        }.getOrElse { error ->
            Log.w(LOG_TAG, "markCallsAcknowledged failed", error)
            false
        }
    }

    /** 删除指定通话记录（删除整组时传入组内全部 ID）。 */
    fun deleteCalls(callIds: Collection<Long>): Boolean {
        if (!hasWriteCallLogPermission() || callIds.isEmpty()) {
            return false
        }
        return runCatching {
            callIds.distinct().filter { it > 0L }.chunked(CHUNK_SIZE).sumOf { ids ->
                val placeholders = ids.joinToString(",") { "?" }
                contentResolver.delete(
                    CallLog.Calls.CONTENT_URI,
                    "${CallLog.Calls._ID} IN ($placeholders)",
                    ids.map(Long::toString).toTypedArray(),
                )
            } > 0
        }.getOrElse { error ->
            Log.w(LOG_TAG, "deleteCalls failed", error)
            false
        }
    }

    /** 解析号码对应的联系人名，用于拨号盘与通话界面。 */
    fun contactNameFor(number: String): String =
        if (number.isBlank()) "" else contactResolver.displayName(number)

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val LOG_TAG = "CallLogRepo"

        /** 最近通话的默认读取上限；界面只展示最近记录，无需全量。 */
        const val DEFAULT_LIMIT = 300

        /** IN 子句分块大小，与短信仓库保持一致。 */
        const val CHUNK_SIZE = 500
    }
}

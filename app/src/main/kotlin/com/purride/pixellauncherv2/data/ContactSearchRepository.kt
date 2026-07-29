package com.purride.pixellauncherv2.data

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.purride.pixellauncherv2.launcher.T9Model
import com.purride.pixellauncherv2.model.ContactEntry
import java.util.concurrent.atomic.AtomicReference

/**
 * T9 智能拨号用的联系人快照。
 *
 * 联系人库不大但查询开销不低，且拨号盘每敲一个键都要重新筛选：一次性读进内存
 * 快照，之后的匹配全在内存里做。快照放在进程级缓存中，避免每次进入拨号盘重读。
 *
 * 拼音来源是 SORT_KEY_PRIMARY——中文 ROM 上它通常就是拼音，让中文联系人也能被
 * T9 命中。这一列并非所有 ROM 都提供，因此取不到时降级为不带拼音的快照。
 */
class ContactSearchRepository(
    private val context: Context,
) {

    private val contentResolver: ContentResolver = context.contentResolver

    fun hasReadContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** 读取（或复用）联系人快照。 */
    fun contacts(): List<ContactEntry> {
        cache.get()?.let { return it }
        val loaded = load()
        cache.set(loaded)
        return loaded
    }

    /** 联系人库变化后丢弃快照，下次检索重新读取。 */
    fun invalidate() {
        cache.set(null)
    }

    /**
     * 按 T9 输入检索，返回最多 [limit] 条命中。
     *
     * 排序：号码前缀命中优先（用户多半在直接拨号），其次按姓名排列，
     * 保证同一输入下结果稳定，不会因读取顺序抖动。
     */
    fun search(query: String, limit: Int = DEFAULT_LIMIT): List<ContactEntry> {
        if (query.isBlank() || limit <= 0 || !hasReadContactsPermission()) {
            return emptyList()
        }
        val digitsQuery = query.filter(Char::isDigit)
        return contacts()
            .filter { entry ->
                T9Model.matches(
                    query = query,
                    name = entry.displayName,
                    phonetic = entry.phoneticName,
                    number = entry.number,
                )
            }
            .sortedWith(
                compareByDescending<ContactEntry> { entry ->
                    digitsQuery.isNotEmpty() && entry.number.filter(Char::isDigit).startsWith(digitsQuery)
                }.thenBy { entry -> entry.displayName },
            )
            .take(limit)
    }

    private fun load(): List<ContactEntry> {
        if (!hasReadContactsPermission()) {
            return emptyList()
        }
        // 先带拼音列查；该列并非所有 ROM 都提供，失败后降级重查。
        return query(withPhonetic = true) ?: query(withPhonetic = false) ?: emptyList()
    }

    private fun query(withPhonetic: Boolean): List<ContactEntry>? {
        val projection = buildList {
            add(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
            add(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (withPhonetic) add(ContactsContract.Contacts.SORT_KEY_PRIMARY)
        }.toTypedArray()
        val cursor = try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null,
            )
        } catch (error: Throwable) {
            Log.w(LOG_TAG, "contact query failed (withPhonetic=$withPhonetic)", error)
            return null
        } ?: return null

        return cursor.use { queryCursor ->
            val idName = queryCursor.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            )
            val idNumber = queryCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val idPhonetic = if (withPhonetic) {
                queryCursor.getColumnIndex(ContactsContract.Contacts.SORT_KEY_PRIMARY)
            } else {
                -1
            }
            if (idName < 0 || idNumber < 0) {
                return@use null
            }
            val entries = ArrayList<ContactEntry>(queryCursor.count.coerceAtLeast(0))
            val seen = HashSet<String>()
            while (queryCursor.moveToNext()) {
                val number = queryCursor.getString(idNumber).orEmpty()
                if (number.isBlank()) continue
                val name = queryCursor.getString(idName).orEmpty()
                // 同一联系人的同一号码可能出现多行（多账号同步），去重。
                if (!seen.add("$name|${number.filter(Char::isDigit)}")) continue
                entries += ContactEntry(
                    displayName = name,
                    number = number,
                    phoneticName = if (idPhonetic >= 0) {
                        queryCursor.getString(idPhonetic).orEmpty()
                    } else {
                        ""
                    },
                )
            }
            entries
        }
    }

    private companion object {
        const val LOG_TAG = "ContactSearch"
        const val DEFAULT_LIMIT = 20

        /** 进程级快照：辅助类可能被多次构造，缓存要跨实例共享。 */
        val cache = AtomicReference<List<ContactEntry>?>(null)
    }
}

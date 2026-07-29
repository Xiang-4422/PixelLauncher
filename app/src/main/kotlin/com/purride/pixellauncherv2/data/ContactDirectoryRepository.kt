package com.purride.pixellauncherv2.data

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.purride.pixellauncherv2.launcher.ContactListModel
import com.purride.pixellauncherv2.model.ContactDetail
import com.purride.pixellauncherv2.model.ContactPhone

/**
 * 联系人目录：目录页/详情页的完整读取，与后续的新建/编辑写入。
 *
 * 与 [ContactSearchRepository]（T9 的扁平快照，一号一条、进程级缓存）分工不同：
 * 这里一人一条、带全部号码与编辑定位键，每次进入模块重新读取——目录页要如实
 * 反映外部改动（其它应用增删联系人），不做进程级缓存。
 *
 * 只收录**有电话号码**的联系人：这是拨号模块的目录，纯邮箱联系人在这里点不出
 * 任何动作，列出来只会稀释列表。
 */
class ContactDirectoryRepository(
    private val context: Context,
) {

    private val contentResolver: ContentResolver = context.contentResolver

    fun hasReadContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    fun hasWriteContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 读取完整目录，按拼音排序键排序（provider 侧排序，Chinese ROM 上即拼音序）。
     *
     * 任何失败（权限、provider 异常、列缺失）都返回空列表并记日志——目录页
     * 渲染空态，绝不允许把宿主进程带崩。
     */
    fun loadContacts(): List<ContactDetail> {
        if (!hasReadContactsPermission()) {
            return emptyList()
        }
        // 三级降级：分桶字母列（隐藏 API，汉字按拼音归桶）→ 拼音排序键 → 展示名排序。
        // 分桶字母不是锦上添花：SORT_KEY 为汉字原文的 ROM 上（实测 MIUI 如此），
        // 没有它时全部中文联系人会塌进一个 # 组，字母分组名存实亡。
        return query(withSortKey = true, withGroupLabel = true)
            ?: query(withSortKey = true, withGroupLabel = false)
            ?: query(withSortKey = false, withGroupLabel = false)
            ?: emptyList()
    }

    private fun query(withSortKey: Boolean, withGroupLabel: Boolean): List<ContactDetail>? {
        val projection = buildList {
            add(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            add(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
            add(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID)
            add(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
            add(ContactsContract.CommonDataKinds.Phone._ID)
            add(ContactsContract.CommonDataKinds.Phone.NUMBER)
            add(ContactsContract.CommonDataKinds.Phone.TYPE)
            add(ContactsContract.CommonDataKinds.Phone.LABEL)
            if (withSortKey) add(ContactsContract.Contacts.SORT_KEY_PRIMARY)
            if (withGroupLabel) add(PHONEBOOK_LABEL_COLUMN)
        }.toTypedArray()
        val sortOrder = if (withSortKey) {
            "${ContactsContract.Contacts.SORT_KEY_PRIMARY} ASC"
        } else {
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
        }
        val cursor = try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                sortOrder,
            )
        } catch (error: Throwable) {
            Log.w(
                LOG_TAG,
                "contact directory query failed (withSortKey=$withSortKey, withGroupLabel=$withGroupLabel)",
                error,
            )
            return null
        } ?: return null

        return cursor.use { queryCursor ->
            val idContact = queryCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val idLookup = queryCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
            val idRawContact = queryCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID)
            val idName = queryCursor.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            )
            val idData = queryCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID)
            val idNumber = queryCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val idType = queryCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
            val idLabel = queryCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)
            val idSortKey = if (withSortKey) {
                queryCursor.getColumnIndex(ContactsContract.Contacts.SORT_KEY_PRIMARY)
            } else {
                -1
            }
            val idGroupLabel = if (withGroupLabel) {
                queryCursor.getColumnIndex(PHONEBOOK_LABEL_COLUMN)
            } else {
                -1
            }
            if (idContact < 0 || idLookup < 0 || idName < 0 || idData < 0 || idNumber < 0) {
                return@use null
            }
            // Phone 表一号一行且已按排序键排好；按 contactId 聚合成一人一条，
            // LinkedHashMap 保住 provider 的排序。
            val byContact = LinkedHashMap<Long, ContactDetail>()
            val seenNumbers = HashMap<Long, MutableSet<String>>()
            while (queryCursor.moveToNext()) {
                val number = queryCursor.getString(idNumber).orEmpty()
                if (number.isBlank()) continue
                val contactId = queryCursor.getLong(idContact)
                // 同一联系人的同一号码可能出现多行（多账号同步），按规范化号码去重。
                val normalized = number.filterNot(Char::isWhitespace)
                if (seenNumbers.getOrPut(contactId) { HashSet() }.add(normalized).not()) continue
                val phone = ContactPhone(
                    dataId = queryCursor.getLong(idData),
                    number = number,
                    typeLabel = ContactListModel.phoneTypeLabel(
                        type = if (idType >= 0) queryCursor.getInt(idType) else 0,
                        customLabel = if (idLabel >= 0) queryCursor.getString(idLabel).orEmpty() else "",
                    ),
                )
                val existing = byContact[contactId]
                if (existing == null) {
                    byContact[contactId] = ContactDetail(
                        contactId = contactId,
                        lookupKey = queryCursor.getString(idLookup).orEmpty(),
                        rawContactId = if (idRawContact >= 0) queryCursor.getLong(idRawContact) else 0L,
                        displayName = queryCursor.getString(idName).orEmpty(),
                        phoneticName = if (idSortKey >= 0) queryCursor.getString(idSortKey).orEmpty() else "",
                        groupLabel = if (idGroupLabel >= 0) queryCursor.getString(idGroupLabel).orEmpty() else "",
                        numbers = listOf(phone),
                    )
                } else {
                    byContact[contactId] = existing.copy(numbers = existing.numbers + phone)
                }
            }
            byContact.values.toList()
        }
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    /**
     * 新建联系人（本地账户）：一个姓名 + 一个手机号。
     *
     * 三条 op 打包成一个 applyBatch——半成品联系人（有名无号/有号无名）比失败更糟，
     * 要么全成要么全不成。
     */
    fun createContact(name: String, number: String): Boolean {
        if (!hasWriteContactsPermission()) {
            Log.w(LOG_TAG, "createContact skipped: WRITE_CONTACTS not granted")
            return false
        }
        val operations = arrayListOf(
            android.content.ContentProviderOperation
                .newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build(),
            android.content.ContentProviderOperation
                .newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                )
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name.trim())
                .build(),
            android.content.ContentProviderOperation
                .newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                )
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number.trim())
                .withValue(
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                )
                .build(),
        )
        return runCatching { contentResolver.applyBatch(ContactsContract.AUTHORITY, operations) }
            .onFailure { error -> Log.w(LOG_TAG, "createContact failed", error) }
            .isSuccess
    }

    /** 改名：更新该 raw contact 的 StructuredName 行；provider 负责重新拆分姓与名。 */
    fun renameContact(rawContactId: Long, name: String): Boolean {
        if (!hasWriteContactsPermission()) {
            Log.w(LOG_TAG, "renameContact skipped: WRITE_CONTACTS not granted")
            return false
        }
        val values = android.content.ContentValues().apply {
            put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name.trim())
        }
        return runCatching {
            contentResolver.update(
                ContactsContract.Data.CONTENT_URI,
                values,
                "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(
                    rawContactId.toString(),
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                ),
            ) > 0
        }.onFailure { error -> Log.w(LOG_TAG, "renameContact failed", error) }
            .getOrDefault(false)
    }

    /** 给既有联系人追加一个手机号。 */
    fun addNumber(rawContactId: Long, number: String): Boolean {
        if (!hasWriteContactsPermission()) {
            Log.w(LOG_TAG, "addNumber skipped: WRITE_CONTACTS not granted")
            return false
        }
        val values = android.content.ContentValues().apply {
            put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
            put(
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            )
            put(ContactsContract.CommonDataKinds.Phone.NUMBER, number.trim())
            put(
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
            )
        }
        return runCatching { contentResolver.insert(ContactsContract.Data.CONTENT_URI, values) != null }
            .onFailure { error -> Log.w(LOG_TAG, "addNumber failed", error) }
            .getOrDefault(false)
    }

    /** 删除一个号码（Data 行）。联系人本身保留；号码删光后它只是从本目录消失。 */
    fun deleteNumber(dataId: Long): Boolean {
        if (!hasWriteContactsPermission()) {
            Log.w(LOG_TAG, "deleteNumber skipped: WRITE_CONTACTS not granted")
            return false
        }
        return runCatching {
            contentResolver.delete(
                ContactsContract.Data.CONTENT_URI,
                "${ContactsContract.Data._ID} = ?",
                arrayOf(dataId.toString()),
            ) > 0
        }.onFailure { error -> Log.w(LOG_TAG, "deleteNumber failed", error) }
            .getOrDefault(false)
    }

    private companion object {
        const val LOG_TAG = "ContactDirRepo"

        /**
         * 通讯录分桶字母列。AOSP 自 Lollipop 起存在但未收进公开常量表，
         * 系统通讯录/拨号的字母索引都靠它；列缺失时上层降级链兜住。
         */
        const val PHONEBOOK_LABEL_COLUMN = "phonebook_label"
    }
}

package com.purride.pixellauncherv2.model

/**
 * 单条通话记录，对应系统通话记录数据库的一行。
 */
data class CallLogEntry(
    /** 通话记录在系统数据库中的 ID。 */
    val callId: Long,
    /** 对方号码；未知来电时为空字符串。 */
    val number: String,
    /** 通话时间戳（毫秒）。 */
    val dateMillis: Long,
    /** 通话时长（秒）；未接通为 0。 */
    val durationSeconds: Long,
    /** 通话类型（对应 CallLog.Calls 的类型常量）。 */
    val type: Int,
    /** 是否为未确认的新记录（未接来电角标的依据）。 */
    val isNew: Boolean,
    /** 联系人展示名，未匹配到联系人时为空字符串。 */
    val displayName: String = "",
    /** 收发所用的 SIM 订阅 id，未知为 -1。 */
    val subscriptionId: Int = -1,
) {
    /** 列表展示标题：优先联系人名，其次号码，最后未知来电占位。 */
    val displayTitle: String
        get() = displayName.ifBlank { number }.ifBlank { UNKNOWN_NUMBER }

    private companion object {
        const val UNKNOWN_NUMBER = "UNKNOWN"
    }
}

/**
 * 通话记录列表中的一组：同一号码连续发生的多次通话合并为一行。
 */
data class CallLogGroup(
    /** 组内最新一条记录的 ID，用于列表 key 与操作定位。 */
    val callId: Long,
    /** 对方号码。 */
    val number: String,
    /** 展示标题（联系人名或号码）。 */
    val displayTitle: String,
    /** 组内最新一条记录的时间戳。 */
    val dateMillis: Long,
    /** 组内最新一条记录的时长。 */
    val durationSeconds: Long,
    /** 组内最新一条记录的通话类型。 */
    val type: Int,
    /** 合并的通话次数。 */
    val callCount: Int,
    /** 组内是否含未确认的新记录。 */
    val hasNew: Boolean,
    /** 组内全部记录的 ID，删除整组时使用。 */
    val callIds: List<Long>,
)

/**
 * T9 检索用的联系人条目。
 */
data class ContactEntry(
    /** 展示名。 */
    val displayName: String,
    /** 号码（保留原始分隔符，展示与拨号都用它）。 */
    val number: String,
    /** 拼音或注音，中文联系人靠它参与 T9 匹配；ROM 未提供时为空。 */
    val phoneticName: String = "",
)

/**
 * 联系人的一个电话号码。
 */
data class ContactPhone(
    /** ContactsContract Data 行 id，编辑/删除该号码时的定位键。 */
    val dataId: Long,
    /** 号码原文（保留分隔符）。 */
    val number: String,
    /** 号码类型的展示标签（MOBILE/HOME/WORK/…，自定义类型取其原文）。 */
    val typeLabel: String,
)

/**
 * 联系人目录页与详情页共用的完整条目。
 *
 * 与 [ContactEntry]（T9 检索的扁平快照，一号一条）不同：这里一人一条、
 * 携带全部号码与编辑所需的定位键。
 */
data class ContactDetail(
    /** 聚合联系人 id。 */
    val contactId: Long,
    /** 稳定定位键；聚合 id 会因同步变化，跨会话定位一律用它。 */
    val lookupKey: String,
    /** 主 raw contact id，改名/改号的写入目标。 */
    val rawContactId: Long,
    /** 展示名。 */
    val displayName: String,
    /** 拼音排序键；ROM 未提供时为空，分组降级用展示名。 */
    val phoneticName: String = "",
    /** 全部号码（已去重），至少一条——目录只收录有号码的联系人。 */
    val numbers: List<ContactPhone> = emptyList(),
)

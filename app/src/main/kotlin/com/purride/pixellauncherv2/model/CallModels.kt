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

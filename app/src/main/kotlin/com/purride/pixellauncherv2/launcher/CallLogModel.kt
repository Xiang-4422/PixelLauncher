package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.model.CallLogEntry
import com.purride.pixellauncherv2.model.CallLogGroup
import java.util.Locale

/**
 * 通话记录的纯逻辑：类型判定、连续同号合并、时长与号码格式化。
 *
 * 不触碰 Android API，便于在 JVM 单测中完整覆盖。
 */
object CallLogModel {

    /** 未接来电（含未接与已拒接之外的漏接）。 */
    fun isMissed(type: Int): Boolean = type == TYPE_MISSED

    /** 本机拒接。 */
    fun isRejected(type: Int): Boolean = type == TYPE_REJECTED

    /** 呼出。 */
    fun isOutgoing(type: Int): Boolean = type == TYPE_OUTGOING

    /** 已接通的来电。 */
    fun isIncoming(type: Int): Boolean = type == TYPE_INCOMING

    /** 需要在列表中以警示色呈现的类型（未接、拒接、被拦截）。 */
    fun isUnanswered(type: Int): Boolean =
        type == TYPE_MISSED || type == TYPE_REJECTED || type == TYPE_BLOCKED

    /**
     * 单字符状态符：未接通一律 `!`，呼出 `>`，语音信箱 `v`，其余（已接来电）`<`。
     *
     * 用 1 个字符替代 MISS/OUT/IN 这类 4 字符缩写——在 29 字符一行里，
     * 方向信息不值得占 4 格，真正需要被强调的只有「未接通」。
     */
    fun statusSymbol(type: Int): String = when {
        isUnanswered(type) -> "!"
        type == TYPE_OUTGOING -> ">"
        type == TYPE_VOICEMAIL -> "v"
        else -> "<"
    }

    /** 通话方向标记：呼出 OUT、未接 MISS、拒接 REJ、拦截 BLOCK、其余 IN。 */
    fun directionLabel(type: Int): String = when (type) {
        TYPE_OUTGOING -> "OUT"
        TYPE_MISSED -> "MISS"
        TYPE_REJECTED -> "REJ"
        TYPE_BLOCKED -> "BLOCK"
        TYPE_VOICEMAIL -> "VM"
        else -> "IN"
    }

    /**
     * 把连续的同号码记录合并成一行（真实拨号应用的通常做法）。
     *
     * 只合并**相邻**的同号码记录：中间夹了别人的通话就另起一组，
     * 这样时间顺序不会被打乱。输入需按时间倒序。
     */
    fun group(entries: List<CallLogEntry>): List<CallLogGroup> {
        val groups = mutableListOf<CallLogGroup>()
        var index = 0
        while (index < entries.size) {
            val head = entries[index]
            val headKey = groupKey(head)
            var next = index + 1
            while (next < entries.size && groupKey(entries[next]) == headKey) {
                next += 1
            }
            val run = entries.subList(index, next)
            groups += CallLogGroup(
                callId = head.callId,
                number = head.number,
                displayTitle = head.displayTitle,
                dateMillis = head.dateMillis,
                durationSeconds = head.durationSeconds,
                type = head.type,
                callCount = run.size,
                hasNew = run.any(CallLogEntry::isNew),
                callIds = run.map(CallLogEntry::callId),
            )
            index = next
        }
        return groups
    }

    /** 组内全部未确认新记录的 ID（进入通话记录页后据此清除未接角标）。 */
    fun newCallIds(entries: List<CallLogEntry>): List<Long> =
        entries.filter(CallLogEntry::isNew).map(CallLogEntry::callId)

    /**
     * 通话时长文本：不足一小时用 M:SS，超过用 H:MM:SS，未接通为空。
     */
    fun formatDuration(durationSeconds: Long): String {
        if (durationSeconds <= 0L) return ""
        val hours = durationSeconds / 3600
        val minutes = (durationSeconds % 3600) / 60
        val seconds = durationSeconds % 60
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    /** 合并次数徽标：单次为空，多次显示 xN。 */
    fun countBadge(callCount: Int): String = if (callCount > 1) "x$callCount" else ""

    /**
     * 单条第二行左侧文本。
     *
     * 有联系人姓名时给号码——第一行是姓名，两行互补。没有姓名时 [displayTitle] 本身
     * 就是号码，第二行再重复一次等于把一条记录的两行花在同一个信息上（通讯录里没有
     * 的陌生来电正是最常见的情形），此时留空。该行右侧还有时长文本撑住行高，留空
     * 不会让行塌陷。
     */
    fun secondaryLine(displayTitle: String, number: String): String =
        if (displayTitle.trim() == number.trim()) "" else number

    /**
     * 分组键：号码规范化后比较，号码为空（未知来电）的记录各自独立成组
     * ——多个未知来电之间无法确认是否同一来源，不应合并。
     */
    private fun groupKey(entry: CallLogEntry): String {
        val normalized = SmsConversationModel.normalizeAddress(entry.number)
        return if (normalized.isBlank()) "unknown:${entry.callId}" else normalized
    }

    /** android.provider.CallLog.Calls.INCOMING_TYPE = 1 */
    private const val TYPE_INCOMING = 1

    /** android.provider.CallLog.Calls.OUTGOING_TYPE = 2 */
    private const val TYPE_OUTGOING = 2

    /** android.provider.CallLog.Calls.MISSED_TYPE = 3 */
    private const val TYPE_MISSED = 3

    /** android.provider.CallLog.Calls.VOICEMAIL_TYPE = 4 */
    private const val TYPE_VOICEMAIL = 4

    /** android.provider.CallLog.Calls.REJECTED_TYPE = 5 */
    private const val TYPE_REJECTED = 5

    /** android.provider.CallLog.Calls.BLOCKED_TYPE = 6 */
    private const val TYPE_BLOCKED = 6
}

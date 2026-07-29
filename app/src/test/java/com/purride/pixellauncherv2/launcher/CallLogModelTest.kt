package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.model.CallLogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLogModelTest {

    @Test
    fun typePredicatesMatchPlatformConstants() {
        assertTrue(CallLogModel.isIncoming(TYPE_INCOMING))
        assertTrue(CallLogModel.isOutgoing(TYPE_OUTGOING))
        assertTrue(CallLogModel.isMissed(TYPE_MISSED))
        assertTrue(CallLogModel.isRejected(TYPE_REJECTED))
        assertFalse(CallLogModel.isMissed(TYPE_INCOMING))
    }

    @Test
    fun unansweredCoversMissedRejectedAndBlocked() {
        assertTrue(CallLogModel.isUnanswered(TYPE_MISSED))
        assertTrue(CallLogModel.isUnanswered(TYPE_REJECTED))
        assertTrue(CallLogModel.isUnanswered(TYPE_BLOCKED))
        assertFalse(CallLogModel.isUnanswered(TYPE_INCOMING))
        assertFalse(CallLogModel.isUnanswered(TYPE_OUTGOING))
    }

    @Test
    fun directionLabelsStayShortAndDistinct() {
        assertEquals("OUT", CallLogModel.directionLabel(TYPE_OUTGOING))
        assertEquals("MISS", CallLogModel.directionLabel(TYPE_MISSED))
        assertEquals("REJ", CallLogModel.directionLabel(TYPE_REJECTED))
        assertEquals("IN", CallLogModel.directionLabel(TYPE_INCOMING))
    }

    @Test
    fun groupMergesAdjacentSameNumberCalls() {
        val entries = listOf(
            entry(id = 5, number = "13800138000", date = 500),
            entry(id = 4, number = "138 0013-8000", date = 400),
            entry(id = 3, number = "10086", date = 300),
            entry(id = 2, number = "13800138000", date = 200),
        )

        val groups = CallLogModel.group(entries)

        assertEquals(3, groups.size)
        // 号码格式不同但规范化后相同，应合并。
        assertEquals(2, groups[0].callCount)
        assertEquals(listOf(5L, 4L), groups[0].callIds)
        assertEquals(500L, groups[0].dateMillis)
        // 中间夹了别人的通话，后面的同号码另起一组，时间顺序不被打乱。
        assertEquals(1, groups[1].callCount)
        assertEquals("10086", groups[1].number)
        assertEquals(1, groups[2].callCount)
        assertEquals(2L, groups[2].callId)
    }

    @Test
    fun groupKeepsUnknownCallersSeparate() {
        val entries = listOf(
            entry(id = 2, number = "", date = 200),
            entry(id = 1, number = "", date = 100),
        )

        // 多个未知来电之间无法确认同源，不应合并。
        assertEquals(2, CallLogModel.group(entries).size)
    }

    @Test
    fun groupPropagatesNewFlagAndPrefersLatestEntry() {
        val entries = listOf(
            entry(id = 2, number = "10086", date = 200, type = TYPE_MISSED, isNew = false),
            entry(id = 1, number = "10086", date = 100, type = TYPE_INCOMING, isNew = true),
        )

        val group = CallLogModel.group(entries).single()

        assertTrue(group.hasNew)
        assertEquals(TYPE_MISSED, group.type)
        assertEquals(200L, group.dateMillis)
        assertEquals(2, group.callCount)
    }

    @Test
    fun groupReturnsEmptyForNoEntries() {
        assertTrue(CallLogModel.group(emptyList()).isEmpty())
    }

    @Test
    fun newCallIdsCollectUnacknowledgedRecordsOnly() {
        val entries = listOf(
            entry(id = 3, number = "10086", date = 300, isNew = true),
            entry(id = 2, number = "10010", date = 200, isNew = false),
            entry(id = 1, number = "10000", date = 100, isNew = true),
        )

        assertEquals(listOf(3L, 1L), CallLogModel.newCallIds(entries))
    }

    @Test
    fun formatDurationSwitchesToHoursWhenNeeded() {
        assertEquals("", CallLogModel.formatDuration(0))
        assertEquals("0:05", CallLogModel.formatDuration(5))
        assertEquals("1:00", CallLogModel.formatDuration(60))
        assertEquals("2:03", CallLogModel.formatDuration(123))
        assertEquals("1:00:00", CallLogModel.formatDuration(3600))
        assertEquals("1:01:05", CallLogModel.formatDuration(3665))
    }

    @Test
    fun countBadgeOnlyAppearsForMergedGroups() {
        assertEquals("", CallLogModel.countBadge(1))
        assertEquals("x3", CallLogModel.countBadge(3))
    }

    @Test
    fun displayTitleFallsBackFromNameToNumberToUnknown() {
        assertEquals("BANK", entry(id = 1, number = "10086", date = 1, name = "BANK").displayTitle)
        assertEquals("10086", entry(id = 1, number = "10086", date = 1).displayTitle)
        assertEquals("UNKNOWN", entry(id = 1, number = "", date = 1).displayTitle)
    }

    private fun entry(
        id: Long,
        number: String,
        date: Long,
        type: Int = TYPE_INCOMING,
        isNew: Boolean = false,
        name: String = "",
    ): CallLogEntry = CallLogEntry(
        callId = id,
        number = number,
        dateMillis = date,
        durationSeconds = 0,
        type = type,
        isNew = isNew,
        displayName = name,
    )

    private companion object {
        /** android.provider.CallLog.Calls.INCOMING_TYPE = 1 */
        const val TYPE_INCOMING = 1

        /** android.provider.CallLog.Calls.OUTGOING_TYPE = 2 */
        const val TYPE_OUTGOING = 2

        /** android.provider.CallLog.Calls.MISSED_TYPE = 3 */
        const val TYPE_MISSED = 3

        /** android.provider.CallLog.Calls.REJECTED_TYPE = 5 */
        const val TYPE_REJECTED = 5

        /** android.provider.CallLog.Calls.BLOCKED_TYPE = 6 */
        const val TYPE_BLOCKED = 6
    }
}

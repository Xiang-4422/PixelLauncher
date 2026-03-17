package com.purride.pixellauncherv2.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.util.Calendar
import java.util.Locale

data class ScreenUsageSnapshot(
    val usageTimeText: String,
    val openCountText: String,
)

class ScreenUsageRepository(
    context: Context,
) {

    private val appContext = context.applicationContext
    private val usageStatsManager = appContext.getSystemService(UsageStatsManager::class.java)
    private val appOpsManager = appContext.getSystemService(AppOpsManager::class.java)

    /**
     * 聚合当天的屏幕交互事件，生成 Home 中 `USE / OPEN` 所需的摘要数据。
     */
    fun readTodaySummary(nowMillis: Long = System.currentTimeMillis()): ScreenUsageSnapshot {
        if (!hasUsageAccess()) {
            return noAccessSnapshot
        }

        val dayStartMillis = startOfToday(nowMillis)
        val usageEvents = usageStatsManager?.queryEvents(dayStartMillis, nowMillis)
            ?: return noAccessSnapshot
        val event = UsageEvents.Event()
        var interactiveStartedAt: Long? = null
        var interactiveDurationMillis = 0L
        var screenOpenCount = 0

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    screenOpenCount += 1
                    interactiveStartedAt = event.timeStamp
                }

                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    val startedAt = interactiveStartedAt
                    if (startedAt != null && event.timeStamp > startedAt) {
                        interactiveDurationMillis += (event.timeStamp - startedAt)
                    }
                    interactiveStartedAt = null
                }
            }
        }

        val activeStart = interactiveStartedAt
        if (activeStart != null && nowMillis > activeStart) {
            interactiveDurationMillis += (nowMillis - activeStart)
        }

        return ScreenUsageSnapshot(
            usageTimeText = formatDurationText(interactiveDurationMillis),
            openCountText = screenOpenCount.toString(),
        )
    }

    /** 检查当前是否已经获得 Usage Access。 */
    fun hasUsageAccess(): Boolean {
        val mode = appOpsManager?.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            appContext.packageName,
        ) ?: return false
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun startOfToday(nowMillis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    companion object {
        val noAccessSnapshot = ScreenUsageSnapshot(
            usageTimeText = "--:--",
            openCountText = "--",
        )

        /** 把时长格式化成 Home 固定使用的时钟式文本。 */
        fun formatDurationText(durationMillis: Long): String {
            val totalMinutes = (durationMillis / 60_000L).coerceAtLeast(0L)
            val hours = totalMinutes / 60L
            val minutes = totalMinutes % 60L
            return String.format(Locale.ENGLISH, "%02d:%02d", hours, minutes)
        }
    }
}

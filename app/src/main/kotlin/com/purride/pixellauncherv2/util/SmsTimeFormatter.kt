package com.purride.pixellauncherv2.util

import java.util.Calendar
import java.util.Locale

/**
 * 短信时间格式化工具。
 *
 * - 当天消息：显示 "HH:MM"（24 小时制）
 * - 非当天消息：显示 "M/D"
 */
object SmsTimeFormatter {

    fun format(dateMillis: Long): String {
        if (dateMillis <= 0L) return "--:--"
        val now = Calendar.getInstance()
        val msg = Calendar.getInstance().also { it.timeInMillis = dateMillis }

        val sameDay = now.get(Calendar.YEAR) == msg.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == msg.get(Calendar.DAY_OF_YEAR)

        return if (sameDay) {
            String.format(
                Locale.US,
                "%02d:%02d",
                msg.get(Calendar.HOUR_OF_DAY),
                msg.get(Calendar.MINUTE),
            )
        } else {
            String.format(
                Locale.US,
                "%d/%d",
                msg.get(Calendar.MONTH) + 1,
                msg.get(Calendar.DAY_OF_MONTH),
            )
        }
    }
}

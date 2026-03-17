package com.purride.pixellauncherv2.data

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NextAlarmRepository(
    private val context: Context,
    private val locale: Locale = Locale.getDefault(),
) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val timeFormatter = SimpleDateFormat("HH:mm", locale)

    private var receiver: BroadcastReceiver? = null

    /**
     * 开始监听系统闹钟变化，并立即回调当前的下一次闹钟文本。
     */
    fun start(onNextAlarmChanged: (String) -> Unit) {
        stop()
        val filter = IntentFilter().apply {
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        val alarmReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                onNextAlarmChanged(readNextAlarmText())
            }
        }
        receiver = alarmReceiver
        ContextCompat.registerReceiver(context, alarmReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onNextAlarmChanged(readNextAlarmText())
    }

    /** 停止已经注册的系统闹钟广播接收器。 */
    fun stop() {
        val registeredReceiver = receiver ?: return
        context.unregisterReceiver(registeredReceiver)
        receiver = null
    }

    /** 读取下一次系统闹钟，并按 Home 固定格式输出。 */
    fun readNextAlarmText(): String {
        val triggerAtMillis = alarmManager?.nextAlarmClock?.triggerTime ?: return noAlarmText
        if (triggerAtMillis <= 0L) {
            return noAlarmText
        }
        return timeFormatter.format(Date(triggerAtMillis))
    }

    private companion object {
        const val noAlarmText = "--:--"
    }
}

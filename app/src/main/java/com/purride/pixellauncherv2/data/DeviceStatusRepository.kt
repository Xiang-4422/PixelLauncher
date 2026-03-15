package com.purride.pixellauncherv2.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat

data class DeviceStatus(
    val batteryLevel: Int,
    val isCharging: Boolean,
)

class DeviceStatusRepository(
    private val context: Context,
) {

    private var receiver: BroadcastReceiver? = null

    fun start(onStatusChanged: (DeviceStatus) -> Unit) {
        stop()
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                onStatusChanged(readStatus(intent))
            }
        }
        receiver = batteryReceiver
        ContextCompat.registerReceiver(context, batteryReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onStatusChanged(readStatus(context.registerReceiver(null, filter)))
    }

    fun stop() {
        val registeredReceiver = receiver ?: return
        context.unregisterReceiver(registeredReceiver)
        receiver = null
    }

    private fun readStatus(intent: Intent?): DeviceStatus {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) ?: 100
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val batteryLevel = if (scale > 0) {
            ((level * 100f) / scale.toFloat()).toInt().coerceIn(0, 100)
        } else {
            100
        }
        val isCharging = plugged == BatteryManager.BATTERY_PLUGGED_AC ||
            plugged == BatteryManager.BATTERY_PLUGGED_USB ||
            plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS ||
            plugged == BatteryManager.BATTERY_PLUGGED_DOCK
        return DeviceStatus(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
        )
    }
}

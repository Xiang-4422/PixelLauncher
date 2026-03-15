package com.purride.pixellauncherv2.system

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import com.purride.pixellauncherv2.launcher.AppEntry

class AndroidAppLauncher(
    private val activity: Activity,
) {

    @Suppress("DEPRECATION")
    fun launch(appEntry: AppEntry): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(appEntry.packageName, appEntry.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            activity.startActivity(intent)
            activity.overridePendingTransition(0, 0)
            true
        } catch (_: Exception) {
            false
        }
    }
}

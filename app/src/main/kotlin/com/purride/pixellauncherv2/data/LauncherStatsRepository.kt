package com.purride.pixellauncherv2.data

import android.content.Context
import com.purride.pixellauncherv2.launcher.AppEntry

data class LauncherStatsSnapshot(
    val launchCount: Int,
    val recentApps: List<String>,
    val lastLaunchPackageName: String?,
)

class LauncherStatsRepository(
    context: Context,
) {

    private val sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(): LauncherStatsSnapshot {
        val recentApps = sharedPreferences.getString(KEY_RECENT_APPS, null)
            ?.split(RECENT_SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        return LauncherStatsSnapshot(
            launchCount = sharedPreferences.getInt(KEY_LAUNCH_COUNT, 0),
            recentApps = recentApps.take(MAX_RECENT_APPS),
            lastLaunchPackageName = sharedPreferences.getString(KEY_LAST_LAUNCH_PACKAGE, null),
        )
    }

    fun recordLaunch(appEntry: AppEntry): LauncherStatsSnapshot {
        val current = read()
        val updatedRecentApps = buildList {
            add(appEntry.packageName)
            addAll(current.recentApps.filterNot { it == appEntry.packageName })
        }.take(MAX_RECENT_APPS)
        val updatedSnapshot = LauncherStatsSnapshot(
            launchCount = current.launchCount + 1,
            recentApps = updatedRecentApps,
            lastLaunchPackageName = appEntry.packageName,
        )
        sharedPreferences.edit()
            .putInt(KEY_LAUNCH_COUNT, updatedSnapshot.launchCount)
            .putString(KEY_RECENT_APPS, updatedRecentApps.joinToString(RECENT_SEPARATOR))
            .putString(KEY_LAST_LAUNCH_PACKAGE, appEntry.packageName)
            .apply()
        return updatedSnapshot
    }

    private companion object {
        const val PREFERENCES_NAME = "pixel_launcher_stats"
        const val KEY_LAUNCH_COUNT = "launch_count"
        const val KEY_RECENT_APPS = "recent_apps"
        const val KEY_LAST_LAUNCH_PACKAGE = "last_launch_package"
        const val RECENT_SEPARATOR = "|"
        const val MAX_RECENT_APPS = 3
    }
}

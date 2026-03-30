package com.purride.pixellauncherv2.data

import com.purride.pixellauncherv2.launcher.AppEntry

interface AppRepository {
    fun loadCachedLaunchableApps(): List<AppEntry>

    fun loadLaunchableApps(): List<AppEntry>
}

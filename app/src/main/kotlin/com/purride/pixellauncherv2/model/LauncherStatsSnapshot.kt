package com.purride.pixellauncherv2.model

/**
 * Launcher 使用统计快照：启动次数、最近使用应用列表及最近一次启动的应用包名。
 */
data class LauncherStatsSnapshot(
    /** 累计启动应用的次数。 */
    val launchCount: Int,
    /** 最近使用的应用包名列表，按最近使用顺序排列。 */
    val recentApps: List<String>,
    /** 最近一次启动的应用包名，尚未有记录时为空。 */
    val lastLaunchPackageName: String?,
)

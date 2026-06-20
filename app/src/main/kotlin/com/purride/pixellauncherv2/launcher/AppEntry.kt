package com.purride.pixellauncherv2.launcher

data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String,
    val englishLabel: String = "",
    val systemLabel: String = label,
    val aliases: List<String> = emptyList(),
)

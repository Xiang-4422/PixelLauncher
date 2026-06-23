package com.purride.pixellauncherv2.launcher

internal object SmsPageIndex {
    const val UNREAD = 0
    const val ALL = 1
    const val COUNT = 2

    fun coerce(index: Int): Int = index.coerceIn(UNREAD, ALL)
}

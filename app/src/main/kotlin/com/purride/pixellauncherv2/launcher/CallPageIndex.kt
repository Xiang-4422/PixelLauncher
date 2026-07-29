package com.purride.pixellauncherv2.launcher

/** 拨号模块首页的固定两页：左页最近通话，右页拨号盘。 */
object CallPageIndex {
    const val RECENT = 0
    const val DIAL = 1
    const val COUNT = 2

    /** 把任意下标收敛到合法页码。 */
    fun coerce(index: Int): Int = index.coerceIn(RECENT, DIAL)
}

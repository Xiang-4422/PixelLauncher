package com.purride.pixellauncherv2.launcher

/**
 * 拨号模块首页的固定三页：最近通话 / 联系人 / 拨号盘。
 *
 * 拨号盘保持在最右——它是模块的"常驻显式入口"（设计评审结论），
 * 联系人插在中间，从任一侧横滑一步可达。
 */
object CallPageIndex {
    const val RECENT = 0
    const val CONTACTS = 1
    const val DIAL = 2
    const val COUNT = 3

    /** 把任意下标收敛到合法页码。 */
    fun coerce(index: Int): Int = index.coerceIn(RECENT, DIAL)
}

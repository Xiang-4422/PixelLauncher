package com.purride.pixellauncherv2.render

data class IdleMaskFrame(
    val sequence: Long,
    val width: Int,
    val height: Int,
    val mask: ByteArray,
)

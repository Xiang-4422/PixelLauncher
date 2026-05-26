package com.purride.pixelui

public data class PixelBoxConstraints(
    val minWidth: Int = 0,
    val maxWidth: Int = Int.MAX_VALUE,
    val minHeight: Int = 0,
    val maxHeight: Int = Int.MAX_VALUE,
) {
    init {
        require(minWidth >= 0) { "minWidth must be >= 0" }
        require(minHeight >= 0) { "minHeight must be >= 0" }
        require(maxWidth >= minWidth) { "maxWidth must be >= minWidth" }
        require(maxHeight >= minHeight) { "maxHeight must be >= minHeight" }
    }
}

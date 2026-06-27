package com.purride.pixelcore

/** [PixelBitmap] 内的一块矩形区域，用于描述 sprite sheet 单帧。 */
public data class PixelBitmapRegion(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
) {
    init {
        require(left >= 0) { "left must be >= 0, got $left" }
        require(top >= 0) { "top must be >= 0, got $top" }
        require(width > 0) { "width must be > 0, got $width" }
        require(height > 0) { "height must be > 0, got $height" }
    }
}

/** 基于内存 [PixelBitmap] 的不可变 sprite sheet。 */
public data class PixelSpriteSheet(
    val bitmap: PixelBitmap,
    val frames: List<PixelBitmapRegion>,
) {
    init {
        require(frames.isNotEmpty()) { "frames must not be empty" }
        frames.forEachIndexed { index, frame ->
            require(frame.left + frame.width <= bitmap.width) {
                "frame[$index] exceeds bitmap width"
            }
            require(frame.top + frame.height <= bitmap.height) {
                "frame[$index] exceeds bitmap height"
            }
        }
    }
}

package com.purride.pixelcore

/** [PixelBitmap] 内的一块矩形区域，用于描述 sprite sheet 单帧。 */
public data class PixelBitmapRegion(
    /** 区域左边缘的非负像素坐标。 */
    val left: Int,
    /** 区域上边缘的非负像素坐标。 */
    val top: Int,
    /** 区域正宽度。 */
    val width: Int,
    /** 区域正高度。 */
    val height: Int,
) {
    init {
        require(left >= 0) { "left must be >= 0, got $left" }
        require(top >= 0) { "top must be >= 0, got $top" }
        require(width in 1..PixelResourceSafetyLimits.MaxDimension) {
            "width must be within 1..${PixelResourceSafetyLimits.MaxDimension}, got $width"
        }
        require(height in 1..PixelResourceSafetyLimits.MaxDimension) {
            "height must be within 1..${PixelResourceSafetyLimits.MaxDimension}, got $height"
        }
        require(left.toLong() + width.toLong() <= Int.MAX_VALUE.toLong()) {
            "left + width overflows Int"
        }
        require(top.toLong() + height.toLong() <= Int.MAX_VALUE.toLong()) {
            "top + height overflows Int"
        }
    }
}

/** 基于内存 [PixelBitmap] 的不可变 sprite sheet。 */
public data class PixelSpriteSheet(
    /** 所有帧共同引用的不可变 bitmap。 */
    val bitmap: PixelBitmap,
    /** 非空且完全位于 bitmap 内的帧区域。 */
    val frames: List<PixelBitmapRegion>,
) {
    init {
        require(frames.isNotEmpty()) { "frames must not be empty" }
        require(frames.size <= PixelResourceSafetyLimits.MaxEntries) {
            "frame count ${frames.size} exceeds ${PixelResourceSafetyLimits.MaxEntries}"
        }
        frames.forEachIndexed { index, frame ->
            require(frame.left.toLong() + frame.width.toLong() <= bitmap.width.toLong()) {
                "frame[$index] exceeds bitmap width"
            }
            require(frame.top.toLong() + frame.height.toLong() <= bitmap.height.toLong()) {
                "frame[$index] exceeds bitmap height"
            }
        }
    }
}

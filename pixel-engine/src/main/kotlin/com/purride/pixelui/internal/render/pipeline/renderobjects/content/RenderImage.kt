package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelSpriteSheet
import com.purride.pixelcore.internal.PixelCoreArtifactAccess

/**
 * [Image] widget 的 render object。
 *
 * Layout：intrinsic 尺寸 = bitmap 自身 (width, height)，再按父约束 clamp。
 * Paint：把 bitmap 像素整块拷贝到目标 buffer 的 (offsetX, offsetY)；
 *        超出 size 的部分自动裁剪，无缩放。
 *
 * 调用方若需缩放，应在构造 [PixelBitmap] 前对源 Android Bitmap 缩放。
 */
public class RenderImage(
    private var bitmap: PixelBitmap,
) : RenderBox() {

    /** 更新 `RenderImage` 的 `updateBitmap` 状态，并保持相关边界与派生状态一致。 */
    public fun updateBitmap(next: PixelBitmap) {
        if (bitmap === next) return
        val sizeChanged = bitmap.width != next.width || bitmap.height != next.height
        bitmap = next
        if (sizeChanged) markNeedsLayout()
        markNeedsPaint()
    }

    override fun layout(constraints: RenderConstraints) {
        val w = constraints.constrainWidth(bitmap.width)
        val h = constraints.constrainHeight(bitmap.height)
        size = RenderSize(width = w, height = h)
    }

    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        val srcW = bitmap.width
        val srcH = bitmap.height
        if (srcW <= 0 || srcH <= 0) return

        val copyW = srcW.coerceAtMost(size.width)
        val copyH = srcH.coerceAtMost(size.height)
        if (copyW <= 0 || copyH <= 0) return

        val target = context.buffer
        /** SDK 内部只读源像素；绘制过程不得修改 backing array。 */
        val sourcePixels = PixelCoreArtifactAccess.pixelsUnsafe(bitmap)
        val targetW = target.width
        val targetH = target.height
        var dstY = offsetY
        var srcY = 0
        // 行循环裁剪到目标 buffer 的可见区域，避免 ArrayIndexOutOfBounds。
        while (srcY < copyH) {
            if (dstY in 0 until targetH) {
                val rowStartX = maxOf(0, offsetX)
                val rowEndX = minOf(targetW, offsetX + copyW)
                if (rowEndX > rowStartX) {
                    val skipLeft = rowStartX - offsetX
                    val srcOffset = srcY * srcW + skipLeft
                    val dstOffset = dstY * targetW + rowStartX
                    val rowLen = rowEndX - rowStartX
                    for (column in 0 until rowLen) {
                        val dstIndex = dstOffset + column
                        target.pixels[dstIndex] = PixelBuffer.blendSrcOver(
                            src = sourcePixels[srcOffset + column],
                            dst = target.pixels[dstIndex],
                        )
                    }
                }
            }
            dstY++
            srcY++
        }
    }
}

/**
 * 定义 `RenderSprite` 在 `RenderImage` 中承担的数据与行为边界。
 *
 * [Sprite] widget render object.
 *
 * Layout uses the selected frame size. Paint clips to the current layout box and
 * copies only the frame region from the backing sprite sheet bitmap.
 */
public class RenderSprite(
    private var sheet: PixelSpriteSheet,
    private var frameIndex: Int,
) : RenderBox() {

    init {
        requireFrameIndex(frameIndex, sheet)
    }

    /** 更新 `RenderImage` 的 `update` 状态，并保持相关边界与派生状态一致。 */
    public fun update(sheet: PixelSpriteSheet, frameIndex: Int) {
        requireFrameIndex(frameIndex, sheet)
        val oldFrame = this.sheet.frames[this.frameIndex]
        val newFrame = sheet.frames[frameIndex]
        val sizeChanged = oldFrame.width != newFrame.width || oldFrame.height != newFrame.height
        val anyChanged = this.sheet !== sheet || this.frameIndex != frameIndex
        if (!anyChanged) return
        this.sheet = sheet
        this.frameIndex = frameIndex
        if (sizeChanged) markNeedsLayout()
        markNeedsPaint()
    }

    override fun layout(constraints: RenderConstraints) {
        val frame = sheet.frames[frameIndex]
        size = RenderSize(
            width = constraints.constrainWidth(frame.width),
            height = constraints.constrainHeight(frame.height),
        )
    }

    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        val bitmap = sheet.bitmap
        val frame = sheet.frames[frameIndex]
        val copyW = frame.width.coerceAtMost(size.width)
        val copyH = frame.height.coerceAtMost(size.height)
        if (copyW <= 0 || copyH <= 0) return

        val target = context.buffer
        /** SDK 内部只读 atlas 像素；绘制过程不得修改 backing array。 */
        val sourcePixels = PixelCoreArtifactAccess.pixelsUnsafe(bitmap)
        val targetW = target.width
        val targetH = target.height
        var dstY = offsetY
        var srcY = frame.top
        var copiedRows = 0
        while (copiedRows < copyH) {
            if (dstY in 0 until targetH) {
                val rowStartX = maxOf(0, offsetX)
                val rowEndX = minOf(targetW, offsetX + copyW)
                if (rowEndX > rowStartX) {
                    val skipLeft = rowStartX - offsetX
                    val srcOffset = srcY * bitmap.width + frame.left + skipLeft
                    val dstOffset = dstY * targetW + rowStartX
                    val rowLen = rowEndX - rowStartX
                    for (column in 0 until rowLen) {
                        val dstIndex = dstOffset + column
                        target.pixels[dstIndex] = PixelBuffer.blendSrcOver(
                            src = sourcePixels[srcOffset + column],
                            dst = target.pixels[dstIndex],
                        )
                    }
                }
            }
            dstY++
            srcY++
            copiedRows++
        }
    }

    private fun requireFrameIndex(frameIndex: Int, sheet: PixelSpriteSheet) {
        require(frameIndex in sheet.frames.indices) {
            "frameIndex $frameIndex out of bounds for ${sheet.frames.size} frames"
        }
    }
}

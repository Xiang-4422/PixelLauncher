package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelui.BuildContext

/**
 * Flutter 风格 `Image` 的直接 render object widget。
 *
 * 不参与缩放或着色：把 [bitmap] 1:1 blit 到目标 buffer。
 */
internal data class ImageWidget(
    val bitmap: PixelBitmap,
    override val key: Any? = null,
) : LeafRenderObjectWidget(key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderImage(bitmap = bitmap)
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderImage).updateBitmap(bitmap)
    }
}

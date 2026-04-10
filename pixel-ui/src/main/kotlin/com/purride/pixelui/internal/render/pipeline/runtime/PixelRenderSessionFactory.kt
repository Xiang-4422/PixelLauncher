package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer

/**
 * 创建 direct pipeline 渲染会话。
 */
internal object PixelRenderSessionFactory {
    /**
     * 创建指定尺寸的空白渲染会话。
     */
    fun create(
        width: Int,
        height: Int,
    ): PixelRenderSession {
        return PixelRenderSession(
            buffer = PixelBuffer(width = width, height = height),
        ).also { session ->
            session.buffer.clear()
        }
    }
}

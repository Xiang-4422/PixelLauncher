package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBufferPool

/**
 * 创建 direct pipeline 渲染会话。
 */
internal object PixelRenderSessionFactory {
    fun create(
        width: Int,
        height: Int,
        bufferPool: PixelBufferPool,
    ): PixelRenderSession {
        val buffer = bufferPool.acquire(width = width, height = height)
        return PixelRenderSession(buffer = buffer)
    }
}

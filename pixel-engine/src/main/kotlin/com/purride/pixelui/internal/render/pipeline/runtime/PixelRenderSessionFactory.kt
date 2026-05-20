package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBufferPool

/**
 * 创建 direct pipeline 渲染会话。
 */
internal object PixelRenderSessionFactory {
    /**
     * 从指定 buffer pool 取一个 buffer 创建空白渲染会话。
     * 调用方有责任在结果不再使用时把 buffer 归还到同一个池。
     */
    fun create(
        width: Int,
        height: Int,
        bufferPool: PixelBufferPool,
    ): PixelRenderSession {
        return PixelRenderSession(
            buffer = bufferPool.acquireMono(width = width, height = height),
        )
    }
}

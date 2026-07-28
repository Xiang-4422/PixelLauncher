package com.purride.pixelui.host

import android.view.Choreographer

/**
 * Android 默认帧调度器。
 *
 * 实现由统一 `pixel-engine` 持有，并直接委托给系统 [Choreographer]，从而同步
 * 60/90/120Hz 等不同屏幕帧节拍。
 */
internal object ChoreographerFrameScheduler : PixelFrameScheduler {
    /** 注册可由 Host 生命周期实际移除的 Choreographer 回调。 */
    override fun scheduleFrame(
        callback: (Long) -> Unit,
    ): PixelFrameCallbackRegistration {
        /** 当前主线程关联的系统 Choreographer。 */
        val choreographer: Choreographer = Choreographer.getInstance()
        /** 同时实现系统回调和 SDK 取消句柄的一次性注册。 */
        val registration = ChoreographerFrameCallbackRegistration(choreographer, callback)
        choreographer.postFrameCallback(registration)
        return registration
    }
}

/** 注册到 Android [Choreographer] 且可移除的一次性回调。 */
private class ChoreographerFrameCallbackRegistration(
    /** 持有当前注册的系统 Choreographer。 */
    private val choreographer: Choreographer,
    /** 最多交付一次的消费者回调。 */
    private val callback: (Long) -> Unit,
) : Choreographer.FrameCallback, PixelFrameCallbackRegistration {
    /** 回调是否仍已注册并允许未来交付。 */
    override var isPending: Boolean = true
        private set

    /** 在仍待处理时从 Choreographer 中移除回调。 */
    override fun cancel(): Boolean {
        if (!isPending) return false
        isPending = false
        choreographer.removeFrameCallback(this)
        return true
    }

    /** 取得一次性交付权并把系统帧时间传给消费者。 */
    override fun doFrame(frameTimeNanos: Long) {
        if (!isPending) return
        isPending = false
        callback(frameTimeNanos)
    }
}

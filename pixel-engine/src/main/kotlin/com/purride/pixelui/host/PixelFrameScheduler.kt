package com.purride.pixelui.host

import android.view.Choreographer

/**
 * 帧调度抽象。
 *
 * pixel-engine 默认依赖 Android `View` 的 `postInvalidateOnAnimation` 触发重绘
 * （间接走 [Choreographer]），但有以下场景需要更细粒度的帧时机控制：
 *
 * - **动画引擎**：业务侧实现自定义动画驱动时，需要知道下一帧的精确时间戳
 *   （`frameTimeNanos`）来推进物理或时间曲线；
 * - **单元测试**：需要在不依赖 Android Looper 的情况下手工驱动帧，
 *   断言"第 N 帧后的状态"；
 * - **替代宿主**：将来如果 pixel-engine 跑在非 Android 平台（JVM 仿真器、
 *   离屏 server 等），需要替换默认实现。
 *
 * 用户通过 [PixelHostSetupConfig.frameScheduler] 注入自定义实例。默认值
 * 走 Android Choreographer，对常规 Android 应用零迁移成本。
 */
public interface PixelFrameScheduler {
    /**
     * 注册一个一次性帧回调。回调会在下一次系统帧到来时被调用，
     * 参数是该帧的纳秒级时间戳（与 [System.nanoTime] 同基准但单调）。
     *
     * 实现方需保证回调在 Android 主线程被触发（生产实现）或者
     * 在调用线程同步触发（manual 测试实现）。
     */
    public fun scheduleFrame(callback: (frameTimeNanos: Long) -> Unit)

    public companion object {
        /**
         * 默认实现，走 Android [Choreographer.postFrameCallback]。
         */
        public val Default: PixelFrameScheduler
            get() = ChoreographerFrameScheduler
    }
}

/**
 * 默认 Android 帧调度器。
 *
 * 直接委托给 [Choreographer.getInstance].[postFrameCallback]——与 Android
 * 主帧节拍同步，自动适配 60/90/120Hz 屏。
 */
internal object ChoreographerFrameScheduler : PixelFrameScheduler {
    override fun scheduleFrame(callback: (Long) -> Unit) {
        Choreographer.getInstance().postFrameCallback { frameTimeNanos ->
            callback(frameTimeNanos)
        }
    }
}

/**
 * 手动驱动的帧调度器。供单元测试使用。
 *
 * `scheduleFrame` 不会立刻执行回调，而是把它压入待触发队列。测试代码
 * 通过 [advanceFrame] 显式触发"下一帧"，并指定该帧的时间戳，让动画
 * 推进可被精确观察。
 *
 * ```
 * val scheduler = ManualFrameScheduler()
 * runtime.attach(scheduler)
 * scheduler.advanceFrame(frameTimeNanos = 16_000_000L)
 * // 此时 scheduleFrame 注册的所有回调按序执行
 * ```
 */
public class ManualFrameScheduler : PixelFrameScheduler {
    private val pending = ArrayDeque<(Long) -> Unit>()

    override fun scheduleFrame(callback: (Long) -> Unit) {
        pending.addLast(callback)
    }

    /**
     * 触发"下一帧"，按 FIFO 顺序执行调用前已注册的回调。
     *
     * 回调内新注册的帧回调不会在本次触发，需要再调一次 advanceFrame（与真实 Choreographer 行为一致）。
     */
    public fun advanceFrame(frameTimeNanos: Long) {
        val toFire = pending.toList()
        pending.clear()
        for (callback in toFire) {
            callback(frameTimeNanos)
        }
    }

    /**
     * 当前等待触发的回调数量。
     */
    public val pendingCount: Int get() = pending.size

    /**
     * 清空待触发队列，丢弃所有已注册的回调。
     */
    public fun clear() {
        pending.clear()
    }
}

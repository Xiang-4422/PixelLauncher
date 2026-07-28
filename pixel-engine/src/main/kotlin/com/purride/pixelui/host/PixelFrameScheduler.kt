package com.purride.pixelui.host

/**
 * 帧调度抽象。
 *
 * pixel-engine 的 Android Host 默认依赖 `Choreographer` 触发重绘。本协议允许以下场景
 * 注入更细粒度的帧时机控制：
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
     *
     * 返回的 [PixelFrameCallbackRegistration] 必须能真正把尚未交付的回调从底层队列移除，
     * 这样 Host 生命周期在暂停或销毁时不会再收到一帧迟到的回调。
     */
    public fun scheduleFrame(
        callback: (frameTimeNanos: Long) -> Unit,
    ): PixelFrameCallbackRegistration

    /** 集中提供 `PixelFrameScheduler` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /**
         * pixel-engine 内置的默认 Android 帧调度器，直接委托系统 `Choreographer`。
         *
         * 测试和非 Android 宿主应注入 [ManualFrameScheduler] 或自定义实现。
         */
        public val Default: PixelFrameScheduler
            get() = ChoreographerFrameScheduler
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
    /** 按 FIFO 顺序保留的待触发注册。 */
    private val pending: ArrayDeque<ManualFrameCallbackRegistration> = ArrayDeque()

    /** 把一个可被真正移除的回调加入手动 FIFO 队列。 */
    override fun scheduleFrame(
        callback: (Long) -> Unit,
    ): PixelFrameCallbackRegistration {
        val registration = ManualFrameCallbackRegistration(callback)
        pending.addLast(registration)
        return registration
    }

    /**
     * 触发"下一帧"，按 FIFO 顺序执行调用前已注册的回调。
     *
     * 回调内新注册的帧回调不会在本次触发，需要再调一次 advanceFrame（与真实 Choreographer 行为一致）。
     */
    public fun advanceFrame(frameTimeNanos: Long) {
        val toFire: List<ManualFrameCallbackRegistration> = pending.toList()
        pending.clear()
        for (registration in toFire) {
            registration.dispatch(frameTimeNanos)
        }
    }

    /**
     * 当前等待触发的回调数量。
     */
    public val pendingCount: Int
        get() = pending.size

    /**
     * 清空待触发队列，丢弃所有已注册的回调。
     */
    public fun clear() {
        val registrations: List<ManualFrameCallbackRegistration> = pending.toList()
        pending.clear()
        registrations.forEach(ManualFrameCallbackRegistration::cancelAfterRemoval)
    }

    /** 由本手动调度器持有、可被真正移除的单个回调。 */
    private inner class ManualFrameCallbackRegistration(
        /** 最多交付一次的消费者回调。 */
        private val callback: (Long) -> Unit,
    ) : PixelFrameCallbackRegistration {
        /** 回调是否仍在队列中，或仍在本次分发快照里具备交付资格。 */
        override var isPending: Boolean = true
            private set

        /** 回调尚未触发时，把它从待触发队列中移除。 */
        override fun cancel(): Boolean {
            if (!isPending) return false
            isPending = false
            pending.remove(this)
            return true
        }

        /** 队列被整体清空后，把该回调标记为已取消。 */
        fun cancelAfterRemoval() {
            isPending = false
        }

        /** 从一次 [advanceFrame] 快照中取得交付权并执行该回调。 */
        fun dispatch(frameTimeNanos: Long) {
            if (!isPending) return
            isPending = false
            callback(frameTimeNanos)
        }
    }
}

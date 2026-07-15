package com.purride.pixelui.host

/**
 * 帧调度抽象。
 *
 * pixel-engine 的 Android Host 默认依赖 `Choreographer` 触发重绘，runtime 只保留本协议，
 * 避免只使用 runtime 的消费者被迫引入 Android UI 实现。以下场景需要更细粒度的帧时机控制：
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

    /** 集中提供 `PixelFrameScheduler` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /**
         * 解析由 `pixel-android` 提供的默认帧调度器。
         *
         * 未安装 Android 适配 artifact 时访问该属性会明确失败；测试和非 Android 宿主应注入
         * [ManualFrameScheduler] 或自定义实现。通过类名解析是为了保留已经冻结的 `Default`
         * JVM 描述符，同时消除 runtime 到 Android artifact 的编译依赖。
         */
        public val Default: PixelFrameScheduler
            get() = PixelAndroidFrameSchedulerResolver.resolve()
    }
}

/**
 * 兼容旧 `PixelFrameScheduler.Default` 描述符的 Android 实现解析器。
 *
 * 解析只发生在显式读取默认值时；runtime 本身不静态引用 Android 类。
 */
private object PixelAndroidFrameSchedulerResolver {
    /** `pixel-android` 中默认实现的稳定二进制类名。 */
    private const val ANDROID_SCHEDULER_CLASS_NAME: String =
        "com.purride.pixelui.host.ChoreographerFrameScheduler"

    /**
     * 返回 Android artifact 的单例调度器；缺少适配 artifact 时给出可操作的错误信息。
     */
    fun resolve(): PixelFrameScheduler {
        try {
            /** 反射类只在默认调度器被请求时加载，纯 runtime 消费者不会触发 Android 类解析。 */
            val schedulerClass: Class<*> = Class.forName(ANDROID_SCHEDULER_CLASS_NAME)
            /** Kotlin object 的 `INSTANCE` 字段保存唯一 Android 调度器实例。 */
            val schedulerInstance: Any? = schedulerClass.getField("INSTANCE").get(null)
            return schedulerInstance as? PixelFrameScheduler
                ?: throw IllegalStateException(
                    "$ANDROID_SCHEDULER_CLASS_NAME does not implement PixelFrameScheduler",
                )
        } catch (error: ReflectiveOperationException) {
            throw IllegalStateException(
                "PixelFrameScheduler.Default requires the pixel-android artifact; " +
                    "inject a scheduler when running pixel-runtime without Android Host support.",
                error,
            )
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
public class ManualFrameScheduler : PixelCancellableFrameScheduler {
    /** Pending registrations retained in FIFO order. */
    private val pending: ArrayDeque<ManualFrameCallbackRegistration> = ArrayDeque()

    /** Preserves the historical fire-and-forget scheduling entry point. */
    override fun scheduleFrame(callback: (Long) -> Unit) {
        scheduleCancellableFrame(callback)
    }

    /** Adds one physically removable callback to the manual FIFO queue. */
    override fun scheduleCancellableFrame(
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

    /** One physically removable callback owned by this manual scheduler. */
    private inner class ManualFrameCallbackRegistration(
        /** Consumer callback delivered at most once. */
        private val callback: (Long) -> Unit,
    ) : PixelFrameCallbackRegistration {
        /** Whether this callback remains queued or eligible in the current dispatch snapshot. */
        override var isPending: Boolean = true
            private set

        /** Removes this callback from the pending queue when it has not fired. */
        override fun cancel(): Boolean {
            if (!isPending) return false
            isPending = false
            pending.remove(this)
            return true
        }

        /** Marks a callback cancelled after its queue has already been cleared in bulk. */
        fun cancelAfterRemoval() {
            isPending = false
        }

        /** Claims and delivers one callback from an [advanceFrame] snapshot. */
        fun dispatch(frameTimeNanos: Long) {
            if (!isPending) return
            isPending = false
            callback(frameTimeNanos)
        }
    }
}

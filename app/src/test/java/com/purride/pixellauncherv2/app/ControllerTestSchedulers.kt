package com.purride.pixellauncherv2.app

import android.os.Handler
import io.mockk.every
import io.mockk.mockk
import java.util.ArrayDeque
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/**
 * 由测试显式推进的后台执行器。
 *
 * Controller 提交任务后先保留在队列中，测试可分别观察“开始加载”和“后台结果已返回”
 * 两个时点，避免直执行器把中间态与完成态折叠成一次同步调用。
 */
internal class QueuedControllerExecutor : AbstractExecutorService() {

    /** 尚未执行的后台任务。 */
    private val tasks = ArrayDeque<Runnable>()

    /** 是否已经拒绝新的后台任务。 */
    private var shutdown = false

    /** 当前等待执行的任务数量。 */
    val pendingTaskCount: Int
        get() = tasks.size

    /** 拒绝后续提交，但保留已经排队的任务供测试推进。 */
    override fun shutdown() {
        shutdown = true
    }

    /** 拒绝后续提交，并返回尚未执行的任务。 */
    override fun shutdownNow(): MutableList<Runnable> {
        shutdown = true
        return buildList {
            while (tasks.isNotEmpty()) {
                add(tasks.removeFirst())
            }
        }.toMutableList()
    }

    /** 返回执行器是否已经停止接收任务。 */
    override fun isShutdown(): Boolean = shutdown

    /** 返回执行器是否停止接收任务且队列已经清空。 */
    override fun isTerminated(): Boolean = shutdown && tasks.isEmpty()

    /** 本执行器没有真实工作线程，因此直接返回当前终止状态。 */
    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = isTerminated

    /** 把任务加入可控队列；shutdown 后保持 ExecutorService 的拒绝语义。 */
    override fun execute(command: Runnable) {
        if (shutdown) {
            throw RejectedExecutionException("QueuedControllerExecutor is shut down")
        }
        tasks.addLast(command)
    }

    /** 执行队首的一个后台任务。 */
    fun runNext() {
        check(tasks.isNotEmpty()) { "No queued controller task to run." }
        tasks.removeFirst().run()
    }

    /** 按提交顺序执行当前队列及执行过程中追加的全部后台任务。 */
    fun runAll() {
        while (tasks.isNotEmpty()) {
            runNext()
        }
    }
}

/**
 * 严格 mock 的 Android 主线程 Handler 及其可控任务队列。
 *
 * [Handler.post]、[Handler.postDelayed] 和 [Handler.removeCallbacks] 都在这里显式
 * 定义行为；测试不会触碰 JVM `android.jar` 中没有实现的 Looper。
 */
internal class ControlledMainHandler {

    /** Controller 实际接收的严格 Handler mock。 */
    val handler: Handler = mockk()

    /** 等待测试推进的立即任务。 */
    private val immediateTasks = ArrayDeque<Runnable>()

    /** 被延迟排程但不会自动运行的任务。 */
    private val delayedTasks = ArrayDeque<Runnable>()

    /** 当前等待投递到“主线程”的立即任务数。 */
    val pendingImmediateTaskCount: Int
        get() = immediateTasks.size

    /** 当前保留的延迟任务数。 */
    val pendingDelayedTaskCount: Int
        get() = delayedTasks.size

    init {
        every { handler.post(any<Runnable>()) } answers {
            immediateTasks.addLast(firstArg())
            true
        }
        every { handler.postDelayed(any<Runnable>(), any<Long>()) } answers {
            delayedTasks.addLast(firstArg())
            true
        }
        every { handler.removeCallbacks(any<Runnable>()) } answers {
            val target = firstArg<Runnable>()
            immediateTasks.removeIf { task -> task === target }
            delayedTasks.removeIf { task -> task === target }
        }
    }

    /** 执行队首的一个立即任务。 */
    fun runNextImmediate() {
        check(immediateTasks.isNotEmpty()) { "No queued main-handler task to run." }
        immediateTasks.removeFirst().run()
    }

    /** 按投递顺序执行当前队列及执行过程中追加的全部立即任务。 */
    fun runAllImmediate() {
        while (immediateTasks.isNotEmpty()) {
            runNextImmediate()
        }
    }
}

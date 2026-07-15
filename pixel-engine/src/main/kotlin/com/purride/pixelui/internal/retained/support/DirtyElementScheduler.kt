package com.purride.pixelui.internal

/**
 * retained element 的 dirty build 调度器。
 *
 * 这层负责：
 * 1. 记录待重建 element
 * 2. 按 depth 排序执行 rebuild
 * 3. 清理调度队列
 */
internal class DirtyElementScheduler {
    private val dirtyElements = linkedSetOf<Element>()
    private var scheduledCount = 0
    private var buildScopeCount = 0
    private var rebuiltElementCount = 0

    fun schedule(element: Element) {
        dirtyElements += element
        scheduledCount += 1
    }

    /** Removes one terminal Element without disturbing other pending sibling builds. */
    fun unschedule(element: Element) {
        dirtyElements -= element
    }

    fun buildScope() {
        buildScopeCount += 1
        while (true) {
            val pending = dirtyElements.sortedBy { it.depth }
            if (pending.isEmpty()) {
                break
            }
            dirtyElements.clear()
            pending.forEach { element ->
                element.rebuildIfNeeded()
                rebuiltElementCount += 1
            }
        }
    }

    fun clear() {
        dirtyElements.clear()
    }

    fun collectDiagnostics(): DirtyElementSchedulerDiagnostics {
        return DirtyElementSchedulerDiagnostics(
            pendingElementCount = dirtyElements.size,
            pendingElementNames = dirtyElements
                .sortedBy { it.depth }
                .map { element -> element.javaClass.simpleName },
            scheduledCount = scheduledCount,
            buildScopeCount = buildScopeCount,
            rebuiltElementCount = rebuiltElementCount,
        )
    }

    /** Returns the cumulative rebuild count without allocating a diagnostics snapshot. */
    fun cumulativeRebuiltElementCount(): Long = rebuiltElementCount.toLong()
}

/**
 * Retained dirty queue 调试快照。
 */
internal data class DirtyElementSchedulerDiagnostics(
    val pendingElementCount: Int,
    val pendingElementNames: List<String>,
    val scheduledCount: Int,
    val buildScopeCount: Int,
    val rebuiltElementCount: Int,
)

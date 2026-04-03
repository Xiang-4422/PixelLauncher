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

    fun schedule(element: Element) {
        dirtyElements += element
    }

    fun buildScope() {
        while (true) {
            val pending = dirtyElements.sortedBy { it.depth }
            if (pending.isEmpty()) {
                break
            }
            dirtyElements.clear()
            pending.forEach { element ->
                element.rebuildIfNeeded()
            }
        }
    }

    fun clear() {
        dirtyElements.clear()
    }
}

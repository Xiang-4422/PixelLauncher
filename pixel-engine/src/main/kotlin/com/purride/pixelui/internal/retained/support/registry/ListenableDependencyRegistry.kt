package com.purride.pixelui.internal

import com.purride.pixelui.Listenable
import com.purride.pixelui.VoidCallback

/**
 * retained element 对 listenable 的依赖注册表。
 *
 * 这层负责：
 * 1. 监听对象与依赖 element 的绑定
 * 2. 回调注册与清理
 * 3. 依赖变更时触发 element 重建
 */
internal class ListenableDependencyRegistry(
    private val requestVisualUpdate: () -> Unit,
) {
    private val listenableCallbacks = mutableMapOf<Listenable, ListenerBinding>()

    fun register(
        element: Element,
        listenable: Listenable,
    ) {
        val binding = listenableCallbacks.getOrPut(listenable) {
            val callback = VoidCallback {
                listenableCallbacks[listenable]
                    ?.elements
                    ?.toList()
                    ?.forEach { dependent ->
                        dependent.markNeedsBuild()
                    }
                requestVisualUpdate()
            }
            listenable.addListener(callback)
            ListenerBinding(
                callback = callback,
                elements = linkedSetOf(),
            )
        }
        if (binding.elements.add(element)) {
            element.listenedObjects += listenable
        }
    }

    fun clear(element: Element) {
        /** Failures from consumer listeners must not retain later listener bindings. */
        val failures = TeardownFailureCollector()
        element.listenedObjects.toList().forEach { listenable ->
            val binding = listenableCallbacks[listenable] ?: return@forEach
            binding.elements -= element
            if (binding.elements.isEmpty()) {
                listenableCallbacks -= listenable
                failures.capture { listenable.removeListener(binding.callback) }
            }
        }
        element.listenedObjects.clear()
        failures.throwIfAny()
    }

    fun dispose() {
        /** Snapshot detached before callbacks so a failure cannot leave the registry populated. */
        val bindings = listenableCallbacks.toList()
        listenableCallbacks.clear()
        /** Failure collector allowing every distinct listenable to release its callback. */
        val failures = TeardownFailureCollector()
        bindings.forEach { (listenable, binding) ->
            failures.capture { listenable.removeListener(binding.callback) }
        }
        failures.throwIfAny()
    }

    private data class ListenerBinding(
        val callback: VoidCallback,
        val elements: MutableSet<Element>,
    )
}

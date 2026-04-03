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
        element.listenedObjects.toList().forEach { listenable ->
            val binding = listenableCallbacks[listenable] ?: return@forEach
            binding.elements -= element
            if (binding.elements.isEmpty()) {
                listenable.removeListener(binding.callback)
                listenableCallbacks -= listenable
            }
        }
        element.listenedObjects.clear()
    }

    fun dispose() {
        listenableCallbacks.forEach { (listenable, binding) ->
            listenable.removeListener(binding.callback)
        }
        listenableCallbacks.clear()
    }

    private data class ListenerBinding(
        val callback: VoidCallback,
        val elements: MutableSet<Element>,
    )
}

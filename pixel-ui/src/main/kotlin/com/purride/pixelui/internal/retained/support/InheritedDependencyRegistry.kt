package com.purride.pixelui.internal

/**
 * inherited element 的依赖注册表。
 *
 * 这层负责维护依赖当前 inherited element 的下游 element，并在通知时根据
 * element 类型选择更细的刷新语义。
 */
internal class InheritedDependencyRegistry {
    private val dependents = linkedSetOf<Element>()

    fun add(element: Element) {
        dependents += element
    }

    fun remove(element: Element) {
        dependents -= element
    }

    fun notifyDependents() {
        dependents.toList().forEach { dependent ->
            if (dependent is StatefulElement) {
                dependent.markDependenciesChanged()
            } else {
                dependent.markNeedsBuild()
            }
        }
    }
}

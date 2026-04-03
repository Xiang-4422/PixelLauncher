package com.purride.pixelui.internal

import com.purride.pixelui.InheritedWidget
import kotlin.reflect.KClass

/**
 * 管理单个 retained element 对 inherited 链路的查找与依赖绑定。
 *
 * 这层负责：
 * 1. 沿父链查找指定类型的 inherited element
 * 2. 维护当前 element 对 inherited ancestor 的依赖集合
 * 3. 在重建和卸载前清理依赖关系
 */
internal class InheritedLookupBinding(
    private val host: Element,
) {
    private val inheritedDependencies = linkedSetOf<InheritedElement>()

    /**
     * 读取并登记对目标 inherited widget 的依赖。
     */
    fun <T : InheritedWidget> dependOn(type: KClass<T>): T? {
        val ancestor = findAncestor(type) ?: return null
        inheritedDependencies += ancestor
        ancestor.addDependent(host)
        @Suppress("UNCHECKED_CAST")
        return ancestor.widget as T
    }

    /**
     * 只读取 inherited widget，不登记依赖。
     */
    fun <T : InheritedWidget> get(type: KClass<T>): T? {
        val ancestor = findAncestor(type) ?: return null
        @Suppress("UNCHECKED_CAST")
        return ancestor.widget as T
    }

    /**
     * 清理当前 element 持有的 inherited 依赖关系。
     */
    fun clear() {
        inheritedDependencies.toList().forEach { ancestor ->
            ancestor.removeDependent(host)
        }
        inheritedDependencies.clear()
    }

    /**
     * 沿父链查找目标 inherited element。
     */
    private fun findAncestor(type: KClass<out InheritedWidget>): InheritedElement? {
        var cursor = host.parent
        while (cursor != null) {
            if (cursor is InheritedElement && type.isInstance(cursor.widget)) {
                return cursor
            }
            cursor = cursor.parent
        }
        return null
    }
}

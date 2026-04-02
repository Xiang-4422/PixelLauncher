package com.purride.pixelui

/**
 * 最小可用的监听协议。
 *
 * 第一版先覆盖框架状态系统真正需要的三件事：
 * - 外部控制器变化后自动触发宿主刷新
 * - `InheritedNotifier` 可感知 notifier 变化
 * - `ValueListenableBuilder` 这类组件可以直接监听值
 */
fun interface VoidCallback {
    fun invoke()
}

interface Listenable {
    fun addListener(listener: VoidCallback)

    fun removeListener(listener: VoidCallback)
}

interface ValueListenable<T> : Listenable {
    val value: T
}

open class ChangeNotifier : Listenable {
    private val listeners = linkedSetOf<VoidCallback>()

    override fun addListener(listener: VoidCallback) {
        listeners += listener
    }

    override fun removeListener(listener: VoidCallback) {
        listeners -= listener
    }

    protected fun notifyListeners() {
        listeners.toList().forEach { listener ->
            listener.invoke()
        }
    }
}

class ValueNotifier<T>(
    initialValue: T,
) : ChangeNotifier(), ValueListenable<T> {
    override var value: T = initialValue
        set(newValue) {
            if (field == newValue) {
                return
            }
            field = newValue
            notifyListeners()
        }
}

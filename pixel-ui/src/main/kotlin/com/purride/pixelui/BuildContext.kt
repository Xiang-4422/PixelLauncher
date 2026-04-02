package com.purride.pixelui

import kotlin.reflect.KClass

/**
 * Flutter 风格的构建上下文。
 *
 * 当前这层已经不再只是“占位接口”，而是 retained build tree 暴露给组件的
 * 最小上下文能力：
 * - 读取和订阅环境
 * - 监听外部 notifier
 */
interface BuildContext {
    val widget: Widget

    fun <T : InheritedWidget> dependOnInheritedWidgetOfExactType(type: KClass<T>): T?

    fun <T : InheritedWidget> getInheritedWidgetOfExactType(type: KClass<T>): T?

    fun watch(listenable: Listenable?)
}

inline fun <reified T : InheritedWidget> BuildContext.dependOnInheritedWidgetOfExactType(): T? {
    return dependOnInheritedWidgetOfExactType(T::class)
}

inline fun <reified T : InheritedWidget> BuildContext.getInheritedWidgetOfExactType(): T? {
    return getInheritedWidgetOfExactType(T::class)
}

abstract class StatelessWidget(
    override val key: Any? = null,
) : Widget {
    abstract fun build(context: BuildContext): Widget
}

abstract class StatefulWidget(
    override val key: Any? = null,
) : Widget {
    abstract fun createState(): State<out StatefulWidget>
}

abstract class State<T : StatefulWidget> {
    lateinit var widget: T
        internal set

    lateinit var context: BuildContext
        internal set

    internal var mounted: Boolean = false
        private set

    internal fun attach() {
        mounted = true
    }

    internal fun detach() {
        mounted = false
    }

    open fun initState() = Unit

    open fun didChangeDependencies() = Unit

    open fun didUpdateWidget(oldWidget: T) = Unit

    open fun dispose() = Unit

    abstract fun build(context: BuildContext): Widget

    open fun setState(action: () -> Unit) {
        action()
        (context as? InternalBuildContext)?.markCurrentElementNeedsBuild()
    }
}

open class InheritedWidget(
    open val child: Widget,
    override val key: Any? = null,
) : Widget {
    open fun updateShouldNotify(oldWidget: InheritedWidget): Boolean = true
}

open class InheritedNotifier<T : Listenable>(
    val notifier: T?,
    override val child: Widget,
    override val key: Any? = null,
) : InheritedWidget(
    child = child,
    key = key,
) {
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        val oldNotifier = (oldWidget as? InheritedNotifier<*>)?.notifier
        return notifier !== oldNotifier
    }
}

internal interface InternalBuildContext : BuildContext {
    fun markCurrentElementNeedsBuild()
}

class ListenableBuilder(
    private val listenable: Listenable,
    override val key: Any? = null,
    private val builder: (BuildContext) -> Widget,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
        context.watch(listenable)
        return builder(context)
    }
}

class ValueListenableBuilder<T>(
    private val listenable: ValueListenable<T>,
    override val key: Any? = null,
    private val builder: (BuildContext, T) -> Widget,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
        context.watch(listenable)
        return builder(context, listenable.value)
    }
}

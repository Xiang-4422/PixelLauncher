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
public interface BuildContext {
    public val widget: Widget

    public fun <T : InheritedWidget> dependOnInheritedWidgetOfExactType(type: KClass<T>): T?

    public fun <T : InheritedWidget> getInheritedWidgetOfExactType(type: KClass<T>): T?

    public fun watch(listenable: Listenable?)
}

public typealias WidgetBuilder = (BuildContext) -> Widget
public typealias StateSetter = (() -> Unit) -> Unit

public inline fun <reified T : InheritedWidget> BuildContext.dependOnInheritedWidgetOfExactType(): T? {
    return dependOnInheritedWidgetOfExactType(T::class)
}

public inline fun <reified T : InheritedWidget> BuildContext.getInheritedWidgetOfExactType(): T? {
    return getInheritedWidgetOfExactType(T::class)
}

public abstract class StatelessWidget(
    override val key: Any? = null,
) : Widget {
    public abstract fun build(context: BuildContext): Widget
}

public abstract class StatefulWidget(
    override val key: Any? = null,
) : Widget {
    public abstract fun createState(): State<out StatefulWidget>
}

public abstract class State<T : StatefulWidget> {
    public lateinit var widget: T
        internal set

    public lateinit var context: BuildContext
        internal set

    internal var mounted: Boolean = false
        private set

    internal fun attach() {
        mounted = true
    }

    internal fun detach() {
        mounted = false
    }

    public open fun initState(): Unit = Unit

    public open fun didChangeDependencies(): Unit = Unit

    public open fun didUpdateWidget(oldWidget: T): Unit = Unit

    public open fun dispose(): Unit = Unit

    public abstract fun build(context: BuildContext): Widget

    public open fun setState(action: () -> Unit) {
        action()
        (context as? InternalBuildContext)?.markCurrentElementNeedsBuild()
    }
}

public open class InheritedWidget(
    public open val child: Widget,
    override val key: Any? = null,
) : Widget {
    public open fun updateShouldNotify(oldWidget: InheritedWidget): Boolean = true
}

public open class InheritedNotifier<T : Listenable>(
    public val notifier: T?,
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

public class ListenableBuilder(
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

public class Builder(
    override val key: Any? = null,
    private val builder: WidgetBuilder,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
        return builder(context)
    }
}

public class ValueListenableBuilder<T>(
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

public class StatefulBuilder(
    override val key: Any? = null,
    private val builder: (BuildContext, StateSetter) -> Widget,
) : StatefulWidget(
    key = key,
) {
    override fun createState(): State<out StatefulWidget> = StatefulBuilderState()

    private class StatefulBuilderState : State<StatefulBuilder>() {
        override fun build(context: BuildContext): Widget {
            return widget.builder(context) { action ->
                setState(action)
            }
        }
    }
}

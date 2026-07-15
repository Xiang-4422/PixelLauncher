package com.purride.pixelui

import com.purride.pixelui.internal.PixelArtifactInternalApi
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
    /** 提供 `BuildContext` 用于识别或兼容校验的 `widget` 值。 */
    public val widget: Widget

    /** 让 `BuildContext` 订阅 `dependOnInheritedWidgetOfExactType` 依赖，并在依赖变化时触发正确的重建路径。 */
    public fun <T : InheritedWidget> dependOnInheritedWidgetOfExactType(type: KClass<T>): T?

    /** 读取 `BuildContext` 的 `getInheritedWidgetOfExactType` 结果，不产生额外状态变更。 */
    public fun <T : InheritedWidget> getInheritedWidgetOfExactType(type: KClass<T>): T?

    /** 让 `BuildContext` 订阅 `watch` 依赖，并在依赖变化时触发正确的重建路径。 */
    public fun watch(listenable: Listenable?)
}

/** 保留 `BuildContext` 对 `WidgetBuilder` 的稳定源码别名，避免 artifact 拆分破坏旧导入路径。 */
public typealias WidgetBuilder = (BuildContext) -> Widget
/** 保留 `BuildContext` 对 `StateSetter` 的稳定源码别名，避免 artifact 拆分破坏旧导入路径。 */
public typealias StateSetter = (() -> Unit) -> Unit

/** 让 `BuildContext` 订阅 `dependOnInheritedWidgetOfExactType` 依赖，并在依赖变化时触发正确的重建路径。 */
public inline fun <reified T : InheritedWidget> BuildContext.dependOnInheritedWidgetOfExactType(): T? {
    return dependOnInheritedWidgetOfExactType(T::class)
}

/** 读取 `BuildContext` 的 `getInheritedWidgetOfExactType` 结果，不产生额外状态变更。 */
public inline fun <reified T : InheritedWidget> BuildContext.getInheritedWidgetOfExactType(): T? {
    return getInheritedWidgetOfExactType(T::class)
}

/** 定义 retained widget 树中的 `StatelessWidget` 生命周期与依赖传播契约。 */
public abstract class StatelessWidget(
    override val key: Any? = null,
) : Widget {
    /** 创建 `BuildContext` 所需的新对象，并在返回前建立其初始不变量。 */
    public abstract fun build(context: BuildContext): Widget
}

/** 定义 retained widget 树中的 `StatefulWidget` 生命周期与依赖传播契约。 */
public abstract class StatefulWidget(
    override val key: Any? = null,
) : Widget {
    /** 创建 `BuildContext` 所需的新对象，并在返回前建立其初始不变量。 */
    public abstract fun createState(): State<out StatefulWidget>
}

/** 保存 `BuildContext` 的可观察或可恢复状态；字段变更必须维持类型声明的不变量。 */
public abstract class State<T : StatefulWidget> {
    /** 提供 `BuildContext` 用于识别或兼容校验的 `widget` 值；写入后由所属对象在下一次状态同步时生效。 */
    public lateinit var widget: T
        internal set

    /** 保存 `BuildContext` 对外传递的 `context` 数据；写入后由所属对象在下一次状态同步时生效。 */
    public lateinit var context: BuildContext
        internal set

    /** 供 testing artifact 读取 retained State 当前挂载状态的内部契约。 */
    @PixelArtifactInternalApi
    public var mounted: Boolean = false
        private set

    internal fun attach() {
        mounted = true
    }

    internal fun detach() {
        mounted = false
    }

    /** 在 `BuildContext` 的 `initState` 生命周期阶段同步依赖与 retained 状态。 */
    public open fun initState(): Unit = Unit

    /** 在 `BuildContext` 的 `didChangeDependencies` 生命周期阶段同步依赖与 retained 状态。 */
    public open fun didChangeDependencies(): Unit = Unit

    /** 在 `BuildContext` 的 `didUpdateWidget` 生命周期阶段同步依赖与 retained 状态。 */
    public open fun didUpdateWidget(oldWidget: T): Unit = Unit

    /** 从 `BuildContext` 释放 `dispose` 对应内容；重复调用按既有幂等约束处理。 */
    public open fun dispose(): Unit = Unit

    /** 创建 `BuildContext` 所需的新对象，并在返回前建立其初始不变量。 */
    public abstract fun build(context: BuildContext): Widget

    /** 更新 `BuildContext` 的 `setState` 状态并保持派生数据一致。
 *
 * Applies [action] only while this State remains mounted, then schedules its retained element.
 */
    public open fun setState(action: () -> Unit) {
        if (!mounted) return
        action()
        (context as? InternalBuildContext)?.markCurrentElementNeedsBuild()
    }
}

/** 定义 retained widget 树中的 `InheritedWidget` 生命周期与依赖传播契约。 */
public open class InheritedWidget(
    /** 提供 `BuildContext` 当前管理的 `child` 内容。 */
    public open val child: Widget,
    override val key: Any? = null,
) : Widget {
    /** 更新 `BuildContext` 的 `updateShouldNotify` 状态，并保持相关边界与派生状态一致。 */
    public open fun updateShouldNotify(oldWidget: InheritedWidget): Boolean = true
}

/** 定义 retained widget 树中的 `InheritedNotifier` 生命周期与依赖传播契约。 */
public open class InheritedNotifier<T : Listenable>(
    /** 记录 `BuildContext` 的 `notifier` 配置或运行值，读取与更新均遵守所属类型约束。 */
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

/** 为 `BuildContext` 提供可替换的创建或解析边界，调用方可以注入自定义实现。 */
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

/** 为 `BuildContext` 提供可替换的创建或解析边界，调用方可以注入自定义实现。 */
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

/** 为 `BuildContext` 提供可替换的创建或解析边界，调用方可以注入自定义实现。 */
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

/** 为 `BuildContext` 提供可替换的创建或解析边界，调用方可以注入自定义实现。 */
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

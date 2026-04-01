package com.purride.pixelui

/**
 * Flutter 风格的构建上下文。
 *
 * 第一版先作为公开骨架预留，
 * 让后续 `StatelessWidget / StatefulWidget / Element` 的关系有稳定落点。
 */
interface BuildContext

/**
 * 无状态组件骨架。
 *
 * 当前 runtime 还没有真正的 `Element` 树，
 * 这里先把公开 API 形状定下来，后续逐步把内部实现补齐。
 */
abstract class StatelessWidget(
    override val key: Any? = null,
    override val modifier: PixelModifier = PixelModifier.Empty,
) : Widget {
    abstract fun build(context: BuildContext): Widget
}

/**
 * 有状态组件骨架。
 *
 * 当前先冻结公开接口，不在这一轮实现完整生命周期。
 */
abstract class StatefulWidget(
    override val key: Any? = null,
    override val modifier: PixelModifier = PixelModifier.Empty,
) : Widget {
    abstract fun createState(): State<out StatefulWidget>
}

/**
 * 有状态组件的状态对象骨架。
 *
 * 这一层会在后续真正承接 `setState` 与宿主刷新联动。
 * 当前先只定义结构，不引入完整状态机。
 */
abstract class State<T : StatefulWidget> {
    lateinit var widget: T
        internal set

    lateinit var context: BuildContext
        internal set

    abstract fun build(context: BuildContext): Widget

    open fun setState(action: () -> Unit) {
        action()
    }
}

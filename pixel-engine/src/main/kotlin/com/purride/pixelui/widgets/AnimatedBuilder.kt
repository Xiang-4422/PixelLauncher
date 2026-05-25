package com.purride.pixelui

/**
 * 性能取向的动画订阅 widget——只让 [builder] 返回的子树重建，
 * 而把不参与动画的子树通过 [child] 参数静态传入。
 *
 * 与 [ListenableBuilder] 的区别：
 *  - `ListenableBuilder` 每次 listener 触发都让 builder 重新构造整棵子树。
 *  - `AnimatedBuilder` 把不变的部分通过 [child] 在外层构造一次，builder
 *    内部把它作为参数收下并嵌入到正在 tween 的容器里。child 在 Element
 *    层会按引用 reconcile，子树整体复用。
 *
 * 典型用法（动 padding，不动里面的 text）：
 *
 * ```kotlin
 * AnimatedBuilder(
 *     animation = controller,
 *     child = Text("HELLO"),
 * ) { _, child ->
 *     Padding(all = (controller.value * 8).toInt(), child = child!!)
 * }
 * ```
 *
 * 等价于 `ListenableBuilder { Padding(all = ..., child = Text("HELLO")) }`，
 * 但后者每帧都重新构造 `Text` widget；前者只构造一次。
 *
 * 名字沿用 Flutter `AnimatedBuilder` 习惯——尽管 [animation] 类型放宽到
 * 任意 [Listenable]（与 `ListenableBuilder` 对齐）。
 */
public class AnimatedBuilder(
    private val animation: Listenable,
    override val key: Any? = null,
    private val child: Widget? = null,
    private val builder: (BuildContext, Widget?) -> Widget,
) : StatelessWidget(key = key) {
    override fun build(context: BuildContext): Widget {
        context.watch(animation)
        return builder(context, child)
    }
}

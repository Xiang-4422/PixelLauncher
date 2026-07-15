package com.purride.pixelui

/**
 * 订阅 [PixelAsyncSource] 并按当前 [PixelAsyncSnapshot] 重建子树的 widget。
 *
 * ```kotlin
 * AsyncBuilder(source = appListSource) { _, snapshot ->
 *     when (snapshot) {
 *         is PixelAsyncSnapshot.Loading -> Text("LOADING…")
 *         is PixelAsyncSnapshot.Success -> AppList(snapshot.value)
 *         is PixelAsyncSnapshot.Failure -> Text("ERROR: ${snapshot.error.message}")
 *     }
 * }
 * ```
 *
 * 生命周期：
 *  - `initState`：调用 [PixelAsyncSource.subscribe] 拿到 unsubscribe lambda。
 *  - 每次 listener 触发：`setState` 把 [PixelAsyncSnapshot] 写进 state，触发重建。
 *  - `didUpdateWidget`：source 实例变化时取消旧订阅、再订阅新 source。
 *  - `dispose`：取消订阅。
 *
 * source 在 `Loading` 与 `Success/Failure` 之间任意切换都是合法的，
 * `Loading` 语义包括"首次加载"与"刷新中"。
 */
public class AsyncBuilder<T>(
    /** 提供 `AsyncBuilder` 执行 `source` 职责时使用的协作者。 */
    public val source: PixelAsyncSource<T>,
    /** 记录 `AsyncBuilder` 的 `initial` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val initial: PixelAsyncSnapshot<T> = PixelAsyncSnapshot.Loading,
    override val key: Any? = null,
    /** 记录 `AsyncBuilder` 的 `builder` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val builder: (BuildContext, PixelAsyncSnapshot<T>) -> Widget,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = AsyncBuilderState<T>()

    private class AsyncBuilderState<T> : State<AsyncBuilder<T>>() {
        private var snapshot: PixelAsyncSnapshot<T> = PixelAsyncSnapshot.Loading
        private var unsubscribe: (() -> Unit)? = null

        override fun initState() {
            snapshot = widget.initial
            subscribeCurrent()
        }

        override fun didUpdateWidget(oldWidget: AsyncBuilder<T>) {
            if (oldWidget.source !== widget.source) {
                unsubscribe?.invoke()
                unsubscribe = null
                // 切 source 时回到 initial（默认 Loading），让 UI 表达"重新加载"。
                snapshot = widget.initial
                subscribeCurrent()
            }
        }

        override fun dispose() {
            unsubscribe?.invoke()
            unsubscribe = null
        }

        override fun build(context: BuildContext): Widget {
            return widget.builder(context, snapshot)
        }

        private fun subscribeCurrent() {
            val source = widget.source
            // 同步 emit（在 subscribe 调用栈内）时 element owner 还没就位，
            // 不能走 setState；直接写字段即可，初次 build 会读到正确值。
            var inSubscribe = true
            unsubscribe = source.subscribe { next ->
                if (inSubscribe) {
                    snapshot = next
                    return@subscribe
                }
                if (!mounted) return@subscribe
                setState { snapshot = next }
            }
            inSubscribe = false
        }
    }
}

package com.purride.pixelui

/**
 * 异步数据快照。
 *
 * - [Loading]：source 还没有任何值（初始 / 等待中）。
 * - [Success]：source 发出过一个值；[value] 是最近一次值。
 * - [Failure]：source 报告了一个错误；[error] 是错误对象。
 *
 * `Success` 之后再到 `Loading` 是合法状态机迁移——表示"重新加载中"。
 */
public sealed class PixelAsyncSnapshot<out T> {
    /** 集中提供 `PixelAsync` 共享的工厂、常量或无状态辅助入口。 */
    public object Loading : PixelAsyncSnapshot<Nothing>() {
        override fun toString(): String = "PixelAsyncSnapshot.Loading"
    }

    /** 定义 `Success` 在 `PixelAsync` 中承担的数据或执行职责，并保持公开不变量稳定。 */
    public data class Success<T>(/** 保存 `PixelAsync` 对外传递的 `value` 数据。 */ public val value: T) : PixelAsyncSnapshot<T>()

    /** 表示 `PixelAsync` 对外可观察的结果或事件，并保留稳定的分支语义。 */
    public data class Failure(/** 保存 `PixelAsync` 的 `error` 结果或失败信息。 */ public val error: Throwable) : PixelAsyncSnapshot<Nothing>()
}

/**
 * 框架中性的异步数据源协议。
 *
 * pixel-engine 不依赖 kotlinx.coroutines；调用方按需把
 * `Flow` / `Future` / 普通 callback 适配成 [PixelAsyncSource]：
 *
 * ```kotlin
 * fun <T> Flow<T>.asPixelAsyncSource(scope: CoroutineScope): PixelAsyncSource<T> =
 *     PixelAsyncSource { listener ->
 *         val job = scope.launch {
 *             catch { e -> listener(PixelAsyncSnapshot.Failure(e)) }
 *                 .collect { listener(PixelAsyncSnapshot.Success(it)) }
 *         }
 *         return@PixelAsyncSource { job.cancel() }
 *     }
 * ```
 *
 * [subscribe] 必须返回一个用于取消订阅的 lambda；[AsyncBuilder] 会在
 * widget dispose 时调用它，避免泄漏。
 *
 * 实现方应当：
 *  - 立即在 [subscribe] 调用线程上发一次初始 snapshot 或保持 Loading
 *  - 多次订阅同一 source 应当各自独立（不要共享 listener）
 *  - listener 调用线程不限定，但 [AsyncBuilder] 内部不会做线程切换；
 *    所有 setState 必须在 UI 线程发起
 */
public fun interface PixelAsyncSource<T> {
    /** 向 `PixelAsync` 注册 `subscribe` 对应内容，并遵守重复注册与生命周期规则。 */
    public fun subscribe(listener: (PixelAsyncSnapshot<T>) -> Unit): () -> Unit
}

/**
 * 构造一个同步、立即解析为 [PixelAsyncSnapshot.Success] 的 source。
 *
 * 适合把已经在内存中的值通过 [AsyncBuilder] 接口暴露——通常用于测试或
 * 同步缓存命中场景。
 */
public fun <T> pixelAsyncSourceOf(value: T): PixelAsyncSource<T> {
    return PixelAsyncSource { listener ->
        listener(PixelAsyncSnapshot.Success(value))
        return@PixelAsyncSource { }
    }
}

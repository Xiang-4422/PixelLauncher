package com.purride.pixelui

/** 定义 `VoidCallback` 在 `Listenable` 中的可替换调用契约。
 *
 * Zero-argument callback used by retained state, controllers, and inherited notifications.
 */
public fun interface VoidCallback {
    /** 执行 `Listenable` 的 `invoke` 公开行为；具体参数、返回和副作用见下文。
 *
 * Receives one synchronous change notification.
 */
    public fun invoke()
}

/** 定义 `Listenable` 在 `Listenable` 中的可替换调用契约。
 *
 * Minimal synchronous listener registration contract.
 */
public interface Listenable {
    /** 向 `Listenable` 注册 `addListener` 内容并绑定对应生命周期。
 *
 * Registers [listener] once according to the implementation's identity policy.
 */
    public fun addListener(listener: VoidCallback)

    /** 从 `Listenable` 释放 `removeListener` 内容并收敛相关所有权。
 *
 * Removes a previously registered [listener] when present.
 */
    public fun removeListener(listener: VoidCallback)
}

/** 定义 `ValueListenable` 在 `Listenable` 中的可替换调用契约。
 *
 * A [Listenable] that exposes its current typed [value].
 */
public interface ValueListenable<T> : Listenable {
    /** 公开 `Listenable` 的 `value` 配置或运行值。
 *
 * Current value observed by registered listeners.
 */
    public val value: T
}

/** 定义 `ChangeNotifier` 在 `Listenable` 中承担的数据与行为边界。
 *
 * Synchronous listener registry that isolates fan-out from individual callback failures.
 */
public open class ChangeNotifier : Listenable {
    /** Identity-stable listener order used for deterministic notification fan-out. */
    private val listeners = linkedSetOf<VoidCallback>()

    /** Adds [listener] without creating duplicate registrations. */
    override fun addListener(listener: VoidCallback) {
        listeners += listener
    }

    /** Removes [listener] if it is currently registered. */
    override fun removeListener(listener: VoidCallback) {
        listeners -= listener
    }

    /** 执行 `Listenable` 的 `notifyListeners` 公开行为；具体参数、返回和副作用见下文。
 *
 * Notifies a snapshot of every listener, then rethrows the first aggregated failure.
 */
    protected fun notifyListeners() {
        /** First listener failure retained while later listeners still receive this mutation. */
        var firstFailure: Throwable? = null
        listeners.toList().forEach { listener ->
            try {
                listener.invoke()
            } catch (failure: Throwable) {
                /** Existing primary failure receiving later independent failures as suppressed. */
                val primary = firstFailure
                if (primary == null) {
                    firstFailure = failure
                } else if (primary !== failure) {
                    primary.addSuppressed(failure)
                }
            }
        }
        firstFailure?.let { failure -> throw failure }
    }
}

/** 定义 `ValueNotifier` 在 `Listenable` 中承担的数据与行为边界。
 *
 * Mutable [ValueListenable] that notifies only when its structural value changes.
 */
public class ValueNotifier<T>(
    /** Initial value published before any listener is registered. */
    initialValue: T,
) : ChangeNotifier(), ValueListenable<T> {
    /** Current value; assigning an equal value is a no-op. */
    override var value: T = initialValue
        set(newValue) {
            if (field == newValue) {
                return
            }
            field = newValue
            notifyListeners()
        }
}

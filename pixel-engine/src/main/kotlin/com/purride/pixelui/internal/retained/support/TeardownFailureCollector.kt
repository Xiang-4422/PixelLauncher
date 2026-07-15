package com.purride.pixelui.internal

/**
 * 定义 `TeardownFailureCollector` 在 `TeardownFailureCollector` 中承担的数据与行为边界。
 *
 * Collects teardown failures while allowing every registered cleanup step to run.
 *
 * Teardown is a terminal state transition: one user callback must not prevent sibling States,
 * owner registries, or retained slots from releasing their resources. The first failure remains
 * the primary exception and later independent failures are attached as suppressed exceptions.
 */
public class TeardownFailureCollector {
    /** First cleanup failure, retained as the exception eventually reported to the caller. */
    private var firstFailure: Throwable? = null

    /** 执行 `TeardownFailureCollector` 的 `capture` 公开行为；具体参数、返回和副作用见下文。
 *
 * Runs one cleanup [step] and retains its failure without interrupting later steps.
 */
    public fun capture(step: () -> Unit) {
        try {
            step()
        } catch (failure: Throwable) {
            record(failure)
        }
    }

    /** 执行 `TeardownFailureCollector` 的 `record` 公开行为；具体参数、返回和副作用见下文。
 *
 * Adds an already-caught cleanup [failure] to this collector.
 */
    public fun record(failure: Throwable) {
        /** Existing primary failure, when an earlier cleanup step already failed. */
        val primary = firstFailure
        if (primary == null) {
            firstFailure = failure
        } else if (primary !== failure) {
            primary.addSuppressed(failure)
        }
    }

    /** 执行 `TeardownFailureCollector` 的 `takeFailure` 公开行为；具体参数、返回和副作用见下文。
 *
 * Removes and returns the accumulated primary failure, or null when every step succeeded.
 */
    public fun takeFailure(): Throwable? {
        /** Failure transferred to the caller before this collector is reset for later teardown. */
        val failure = firstFailure
        firstFailure = null
        return failure
    }

    /** 执行 `TeardownFailureCollector` 的 `throwIfAny` 公开行为；具体参数、返回和副作用见下文。
 *
 * Throws the accumulated primary failure after clearing this collector.
 */
    public fun throwIfAny() {
        throw takeFailure() ?: return
    }
}

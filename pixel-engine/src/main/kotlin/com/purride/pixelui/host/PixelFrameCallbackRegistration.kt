package com.purride.pixelui.host

/**
 * 定义 `PixelFrameCallbackRegistration` 在帧调度中的可替换调用契约。
 *
 * Handle for one not-yet-delivered frame callback.
 *
 * Cancellation is idempotent. A callback that has already started or completed is no longer
 * pending and cannot be cancelled retroactively.
 */
public interface PixelFrameCallbackRegistration {
    /** 表示 `PixelFrameCallbackRegistration` 当前是否满足 `isPending` 对应条件。
 *
 * Whether the callback is still eligible for one future delivery.
 */
    public val isPending: Boolean

    /**
 * 判断 `PixelFrameCallbackRegistration` 是否满足 `cancel` 条件，并在满足时取消。
 *
     * Cancels the pending callback.
     *
     * @return `true` only when this call changed a pending callback to cancelled.
     */
    public fun cancel(): Boolean
}

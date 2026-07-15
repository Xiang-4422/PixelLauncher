package com.purride.pixelui.internal.host

import com.purride.pixelui.PixelBackDispatcher
import com.purride.pixelui.PixelPredictiveBackEvent

internal fun handlePixelHostBack(
    hasFocusedTextInput: Boolean,
    clearFocusedTextInput: () -> Unit,
    backDispatcher: PixelBackDispatcher?,
    onUnhandledBack: (() -> Boolean)?,
    onHandled: () -> Unit,
): Boolean {
    // 宿主 back 顺序必须稳定：先让输入框失焦，再给 widget 栈，最后才交给 app。
    if (hasFocusedTextInput) {
        clearFocusedTextInput()
        onHandled()
        return true
    }
    if (backDispatcher?.handleBack() == true) {
        onHandled()
        return true
    }
    if (onUnhandledBack?.invoke() == true) {
        onHandled()
        return true
    }
    return false
}

/**
 * 协调一次 Host 级预测返回会话，同时保留“输入框、widget、app fallback”的既有优先级。
 *
 * start 只建立临时会话，不清除输入焦点也不修改导航栈；只有 commit 才执行正式返回。
 * 这样 Android 14 取消手势时可以无副作用回滚。
 */
internal class PixelHostPredictiveBackSession(
    /** 查询当前是否存在已聚焦文本输入。 */
    private val hasFocusedTextInput: () -> Boolean,
    /** 在提交输入会话时清除焦点。 */
    private val clearFocusedTextInput: () -> Unit,
    /** 动态读取当前 widget back dispatcher。 */
    private val backDispatcher: () -> PixelBackDispatcher?,
    /** widget 栈未处理提交时调用的 app fallback。 */
    private val onUnhandledBack: () -> (() -> Boolean)?,
    /** start/progress/cancel/commit 后请求 Host 重绘。 */
    private val onSessionChanged: () -> Unit,
) {
    /** 当前手势已经锁定的 Host 消费目标。 */
    private var activeTarget: ActiveTarget? = null

    /**
     * 启动一条预测返回手势。
     *
     * 若前一会话尚未结束，会先取消前一会话，确保任何时刻至多存在一个消费者。
     */
    fun start(event: PixelPredictiveBackEvent): Boolean {
        cancel()
        if (hasFocusedTextInput()) {
            activeTarget = ActiveTarget.TextInput
            onSessionChanged()
            return true
        }
        val dispatcher = backDispatcher()
        if (dispatcher?.startPredictiveBack(event) == true) {
            activeTarget = ActiveTarget.Dispatcher(dispatcher)
            onSessionChanged()
            return true
        }
        return false
    }

    /** 把最新进度发送给 start 时锁定的 Dispatcher；输入焦点目标只保留会话。 */
    fun progress(event: PixelPredictiveBackEvent) {
        val target = activeTarget ?: return
        if (target is ActiveTarget.Dispatcher) {
            target.dispatcher.updatePredictiveBack(event)
        }
        onSessionChanged()
    }

    /** 取消当前临时会话；文本输入焦点在取消后保持不变。 */
    fun cancel() {
        val target = activeTarget ?: return
        activeTarget = null
        if (target is ActiveTarget.Dispatcher) {
            target.dispatcher.cancelPredictiveBack()
        }
        onSessionChanged()
    }

    /**
     * 提交当前手势；没有 start 的 API 33/硬件返回会退化为既有离散 back 顺序。
     */
    fun commit(): Boolean {
        val target = activeTarget
        activeTarget = null
        val handled = when (target) {
            ActiveTarget.TextInput -> {
                clearFocusedTextInput()
                true
            }
            is ActiveTarget.Dispatcher -> {
                target.dispatcher.commitPredictiveBack() || (onUnhandledBack()?.invoke() == true)
            }
            null -> handlePixelHostBack(
                hasFocusedTextInput = hasFocusedTextInput(),
                clearFocusedTextInput = clearFocusedTextInput,
                backDispatcher = backDispatcher(),
                onUnhandledBack = onUnhandledBack(),
                onHandled = {},
            )
        }
        if (handled || target != null) onSessionChanged()
        return handled
    }

    /** Host 销毁或平台取消注册时终止任何未完成手势。 */
    fun dispose() {
        cancel()
    }

    /** Host 级预测返回会话可以锁定的目标。 */
    private sealed interface ActiveTarget {
        /** 输入框只在 commit 时清除，cancel 不改变焦点。 */
        data object TextInput : ActiveTarget

        /** 已接收 start 并拥有后续进度的 widget Dispatcher。 */
        data class Dispatcher(
            val dispatcher: PixelBackDispatcher,
        ) : ActiveTarget
    }
}

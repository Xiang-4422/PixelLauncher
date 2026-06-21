package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.viewmodel.LauncherUiState

/** SMS 宿主只在业务状态变化时执行一次滚动定位，普通重绘不得修改 offset。 */
internal object SmsScrollSyncPolicy {

    fun shouldRevealSelectedThread(previous: LauncherUiState, current: LauncherUiState): Boolean {
        if (current.mode != LauncherMode.SMS_THREADS) return false
        return previous.mode != LauncherMode.SMS_THREADS ||
            previous.smsThreadSelectedIndex != current.smsThreadSelectedIndex
    }

    fun shouldFollowMessagesToEnd(
        previous: LauncherUiState,
        current: LauncherUiState,
        wasAtEnd: Boolean,
    ): Boolean {
        if (current.mode != LauncherMode.SMS_THREAD_DETAIL) return false
        val enteredDetail = previous.mode != LauncherMode.SMS_THREAD_DETAIL
        val threadChanged = previous.smsCurrentThreadId != current.smsCurrentThreadId ||
            previous.smsCurrentAddress != current.smsCurrentAddress
        val messagesChanged = previous.smsMessages != current.smsMessages
        return enteredDetail || threadChanged || (messagesChanged && wasAtEnd)
    }
}

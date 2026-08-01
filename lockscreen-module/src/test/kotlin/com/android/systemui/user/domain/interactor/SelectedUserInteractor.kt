package com.android.systemui.user.domain.interactor

/** 测试中模拟 SystemUI 当前用户读取器。 */
class SelectedUserInteractor(
    /** 当前选择用户 ID。 */
    var currentUserId: Int,
) {
    /** 返回当前选择用户 ID。 */
    fun getSelectedUserId(): Int = currentUserId
}

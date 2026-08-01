package com.android.keyguard

import com.android.internal.widget.LockPatternUtils
import com.android.systemui.user.domain.interactor.SelectedUserInteractor

/** 测试中模拟 Titan 2 安全模式模型。 */
class KeyguardSecurityModel(
    /** 当前用户应解析出的模式。 */
    var resolvedMode: SecurityMode,
) {
    /** Titan 2 可达安全模式枚举。 */
    enum class SecurityMode {
        /** 尚未初始化。 */
        Invalid,

        /** 无设备凭据。 */
        None,

        /** 九宫格图案。 */
        Pattern,

        /** 数字 PIN。 */
        PIN,

        /** 系统输入法密码。 */
        Password,

        /** 特殊 SIM 场景，用于验证通用桥拒绝接管。 */
        SimPinPukMe1,
    }

    /** 按用户返回测试模式。 */
    fun getSecurityMode(userId: Int): SecurityMode {
        check(userId >= 0)
        return resolvedMode
    }
}

/** 测试中模拟 SystemUI 原生安全回调接口。 */
interface KeyguardSecurityCallback {
    /** 上报一次成功、失败或限流尝试。 */
    fun reportUnlockAttempt(userId: Int, timeoutMillis: Int, success: Boolean)

    /** 请求原生 Keyguard 完成解锁。 */
    fun dismiss(userId: Int, securityMode: KeyguardSecurityModel.SecurityMode)

    /** 通知 SystemUI 用户开始输入。 */
    fun onUserInput()

    /** 刷新系统用户活动。 */
    fun userActivity()
}

/** 测试中记录原生安全回调动作。 */
class RecordingKeyguardSecurityCallback : KeyguardSecurityCallback {
    /** 已上报的尝试。 */
    val attempts: MutableList<FakeUnlockAttempt> = mutableListOf()

    /** 已请求 dismiss 的用户与模式。 */
    val dismissals: MutableList<Pair<Int, KeyguardSecurityModel.SecurityMode>> = mutableListOf()

    /** 用户输入通知次数。 */
    var userInputCount: Int = 0

    /** 用户活动通知次数。 */
    var userActivityCount: Int = 0

    /** 记录尝试。 */
    override fun reportUnlockAttempt(userId: Int, timeoutMillis: Int, success: Boolean) {
        attempts += FakeUnlockAttempt(userId, timeoutMillis, success)
    }

    /** 记录原生解锁请求。 */
    override fun dismiss(userId: Int, securityMode: KeyguardSecurityModel.SecurityMode) {
        dismissals += userId to securityMode
    }

    /** 记录用户输入通知。 */
    override fun onUserInput() {
        userInputCount += 1
    }

    /** 记录用户活动通知。 */
    override fun userActivity() {
        userActivityCount += 1
    }
}

/** 测试中记录的一次脱敏解锁尝试。 */
data class FakeUnlockAttempt(
    /** 用户 ID。 */
    val userId: Int,
    /** 系统限流毫秒数。 */
    val timeoutMillis: Int,
    /** 是否成功。 */
    val success: Boolean,
)

/** 测试中模拟 Titan 2 主安全容器控制器。 */
class KeyguardSecurityContainerController(
    /** Android 锁屏工具对象。 */
    val mLockPatternUtils: LockPatternUtils,
    /** 原生安全回调。 */
    val mKeyguardSecurityCallback: KeyguardSecurityCallback,
    /** 安全模式模型。 */
    val mSecurityModel: KeyguardSecurityModel,
    /** 当前用户读取器。 */
    val mSelectedUserInteractor: SelectedUserInteractor,
    /** 当前显示的原生安全模式。 */
    var mCurrentSecurityMode: KeyguardSecurityModel.SecurityMode,
)

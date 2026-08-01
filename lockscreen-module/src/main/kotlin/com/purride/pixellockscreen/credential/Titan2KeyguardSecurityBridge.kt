package com.purride.pixellockscreen.credential

import android.annotation.SuppressLint
import java.lang.reflect.Field
import java.lang.reflect.Method

/** Titan 2 原生 Keyguard 当前支持交给通用设备凭据桥的认证模式。 */
internal enum class Titan2CredentialMode {
    /** 九宫格图案。 */
    PATTERN,

    /** 数字 PIN。 */
    PIN,

    /** 系统输入法密码。 */
    PASSWORD,
}

/** 把系统校验结果提交给原生 Keyguard 链路后的脱敏处置结果。 */
internal sealed interface KeyguardSecurityDisposition {
    /** 成功尝试已经上报，并已请求原生 Keyguard 完成解锁。 */
    data object DismissRequested : KeyguardSecurityDisposition

    /** 普通失败已经交给系统累计失败次数。 */
    data object FailureReported : KeyguardSecurityDisposition

    /** 限流失败已经上报，并由系统写入锁定截止时间。 */
    data class LockoutStarted(
        /** `LockPatternUtils` 返回的系统 elapsed realtime 截止时间。 */
        val deadlineElapsedRealtime: Long,
    ) : KeyguardSecurityDisposition

    /** 主动取消不触发失败上报或解锁动作。 */
    data object Cancelled : KeyguardSecurityDisposition

    /** 校验期间用户或安全模式发生变化，旧结果被安全丢弃。 */
    data object StaleContext : KeyguardSecurityDisposition
}

/**
 * Titan 2 `KeyguardSecurityContainerController` 的精确反射绑定。
 *
 * 该桥不校验凭据，也不直接调用 `KeyguardViewMediator`。它只复用 ROM 自己的
 * `KeyguardSecurityCallback` 上报与 dismiss 链路，因此失败次数、擦除策略、统计和解锁转场
 * 仍由 SystemUI 与系统服务处理。
 */
internal class Titan2KeyguardSecurityBridge private constructor(
    /** 当前 `KeyguardSecurityContainerController` 实例。 */
    private val controller: Any,
    /** 当前控制器持有的 `LockPatternUtils`。 */
    val lockPatternUtils: Any,
    /** 当前控制器持有的原生安全回调。 */
    internal val securityCallback: Any,
    /** 当前控制器持有的安全模式模型。 */
    private val securityModel: Any,
    /** 当前选择用户读取器。 */
    private val selectedUserInteractor: Any,
    /** 绑定时解析出的当前用户。 */
    val userId: Int,
    /** 绑定时解析出的通用凭据模式。 */
    val credentialMode: Titan2CredentialMode,
    /** 绑定时对应的原生 `SecurityMode` 枚举对象。 */
    private val nativeSecurityMode: Any,
    /** 读取当前安全模式的字段。 */
    private val currentSecurityModeField: Field,
    /** 按用户解析安全模式的方法。 */
    private val getSecurityModeMethod: Method,
    /** 读取当前选择用户的方法。 */
    private val getSelectedUserIdMethod: Method,
    /** 上报解锁尝试的方法。 */
    private val reportUnlockAttemptMethod: Method,
    /** 请求 Keyguard 完成原生解锁的方法。 */
    private val dismissMethod: Method,
    /** 通知 SystemUI 用户已经输入的方法。 */
    private val onUserInputMethod: Method,
    /** 刷新系统用户活动的方法。 */
    private val userActivityMethod: Method,
    /** 写入系统锁定截止时间的方法。 */
    private val setLockoutDeadlineMethod: Method,
    /** 读取系统现有锁定截止时间的方法。 */
    private val getLockoutDeadlineMethod: Method,
) {
    /**
     * 通知原生 Keyguard 当前会话收到了用户输入。
     *
     * 该动作只复用原生防休眠和生物识别取消语义，不携带任何凭据内容。
     */
    fun signalUserInput() {
        onUserInputMethod.invoke(securityCallback)
        userActivityMethod.invoke(securityCallback)
    }

    /** 读取当前用户由 Android 维护的单调时钟锁定截止时间。 */
    fun currentLockoutDeadline(): Long {
        check(isCurrentContext()) { "keyguard_lockout_context_stale" }
        return getLockoutDeadlineMethod.invoke(lockPatternUtils, userId) as? Long
            ?: error("keyguard_lockout_deadline_read")
    }

    /** 判断图案控制器是否持有本桥绑定的同一回调和同一原生模式对象。 */
    fun matchesControllerBinding(callback: Any, securityMode: Any): Boolean =
        callback === securityCallback && securityMode === nativeSecurityMode

    /**
     * 按 ROM 原生顺序提交系统校验结果。
     *
     * 提交前再次检查当前用户和安全模式；上下文变化时不会累计失败或请求解锁。
     */
    fun complete(result: CredentialCheckResult): KeyguardSecurityDisposition {
        if (result == CredentialCheckResult.Cancelled) {
            return KeyguardSecurityDisposition.Cancelled
        }
        if (!isCurrentContext()) {
            return KeyguardSecurityDisposition.StaleContext
        }
        return when (result) {
            CredentialCheckResult.Matched -> {
                reportUnlockAttemptMethod.invoke(securityCallback, userId, NO_TIMEOUT, true)
                dismissMethod.invoke(securityCallback, userId, nativeSecurityMode)
                KeyguardSecurityDisposition.DismissRequested
            }

            CredentialCheckResult.Rejected -> {
                reportUnlockAttemptMethod.invoke(securityCallback, userId, NO_TIMEOUT, false)
                KeyguardSecurityDisposition.FailureReported
            }

            is CredentialCheckResult.Throttled -> {
                require(result.timeoutMillis > 0) { "keyguard_lockout_timeout" }
                reportUnlockAttemptMethod.invoke(
                    securityCallback,
                    userId,
                    result.timeoutMillis,
                    false,
                )
                /** 系统写入并返回的单调时钟截止时间。 */
                val deadline = setLockoutDeadlineMethod.invoke(
                    lockPatternUtils,
                    userId,
                    result.timeoutMillis,
                ) as? Long ?: error("keyguard_lockout_deadline")
                KeyguardSecurityDisposition.LockoutStarted(deadline)
            }

            CredentialCheckResult.Cancelled -> KeyguardSecurityDisposition.Cancelled
        }
    }

    /** 检查异步校验完成时仍然属于同一用户和同一设备凭据模式。 */
    fun isCurrentContext(): Boolean {
        /** 回调时的系统选择用户。 */
        val currentUserId = getSelectedUserIdMethod.invoke(selectedUserInteractor) as? Int ?: return false
        if (currentUserId != userId) {
            return false
        }
        /** 回调时控制器或模型解析出的原生模式。 */
        val currentMode = resolveNativeSecurityMode(
            controller = controller,
            securityModel = securityModel,
            userId = userId,
            currentSecurityModeField = currentSecurityModeField,
            getSecurityModeMethod = getSecurityModeMethod,
        )
        return currentMode === nativeSecurityMode
    }

    internal companion object {
        /**
         * 绑定 Titan 2 已验证控制器，任一类、字段或方法签名变化都会抛出并触发原生回退。
         */
        @SuppressLint("BlockedPrivateApi", "PrivateApi")
        fun bind(controller: Any, classLoader: ClassLoader): Titan2KeyguardSecurityBridge {
            /** Titan 2 的主安全容器控制器类。 */
            val controllerClass = Class.forName(CONTROLLER_CLASS, false, classLoader)
            check(controllerClass.isInstance(controller)) { "keyguard_security_controller_instance" }
            /** Android 内部锁屏工具类。 */
            val lockPatternUtilsClass = Class.forName(LOCK_PATTERN_UTILS_CLASS, false, classLoader)
            /** Titan 2 安全模式模型类。 */
            val securityModelClass = Class.forName(SECURITY_MODEL_CLASS, false, classLoader)
            /** Titan 2 安全模式枚举类。 */
            val securityModeClass = Class.forName(SECURITY_MODE_CLASS, false, classLoader)
            /** SystemUI 安全回调接口。 */
            val securityCallbackClass = Class.forName(SECURITY_CALLBACK_CLASS, false, classLoader)
            /** SystemUI 当前用户读取器。 */
            val selectedUserInteractorClass = Class.forName(
                SELECTED_USER_INTERACTOR_CLASS,
                false,
                classLoader,
            )
            /** 控制器持有的 LockPatternUtils 字段。 */
            val lockPatternUtilsField = exactField(
                controllerClass,
                LOCK_PATTERN_UTILS_FIELD,
                lockPatternUtilsClass,
            )
            /** 控制器持有的安全回调字段。 */
            val securityCallbackField = assignableField(
                controllerClass,
                SECURITY_CALLBACK_FIELD,
                securityCallbackClass,
            )
            /** 控制器持有的安全模型字段。 */
            val securityModelField = exactField(controllerClass, SECURITY_MODEL_FIELD, securityModelClass)
            /** 控制器持有的当前用户读取器字段。 */
            val selectedUserInteractorField = exactField(
                controllerClass,
                SELECTED_USER_INTERACTOR_FIELD,
                selectedUserInteractorClass,
            )
            /** 控制器持有的当前安全模式字段。 */
            val currentSecurityModeField = exactField(
                controllerClass,
                CURRENT_SECURITY_MODE_FIELD,
                securityModeClass,
            )
            /** 当前 LockPatternUtils 实例。 */
            val lockPatternUtils = requireNotNull(lockPatternUtilsField.get(controller)) {
                "keyguard_lock_pattern_utils"
            }
            /** 当前原生安全回调实例。 */
            val securityCallback = requireNotNull(securityCallbackField.get(controller)) {
                "keyguard_security_callback"
            }
            /** 当前安全模型实例。 */
            val securityModel = requireNotNull(securityModelField.get(controller)) {
                "keyguard_security_model"
            }
            /** 当前用户读取器实例。 */
            val selectedUserInteractor = requireNotNull(selectedUserInteractorField.get(controller)) {
                "keyguard_selected_user_interactor"
            }
            /** 当前用户读取方法。 */
            val getSelectedUserIdMethod = selectedUserInteractorClass.getDeclaredMethod(
                GET_SELECTED_USER_ID_METHOD,
            )
            /** 绑定时的系统选择用户。 */
            val userId = getSelectedUserIdMethod.invoke(selectedUserInteractor) as? Int
                ?: error("keyguard_selected_user_id")
            /** 按用户解析安全模式的方法。 */
            val getSecurityModeMethod = securityModelClass.getDeclaredMethod(
                GET_SECURITY_MODE_METHOD,
                Int::class.javaPrimitiveType,
            )
            /** 绑定时当前原生安全模式。 */
            val nativeSecurityMode = resolveNativeSecurityMode(
                controller = controller,
                securityModel = securityModel,
                userId = userId,
                currentSecurityModeField = currentSecurityModeField,
                getSecurityModeMethod = getSecurityModeMethod,
            )
            /** 原生模式映射出的通用模式。 */
            val credentialMode = modeOf(nativeSecurityMode)
                ?: error("keyguard_unsupported_security_mode:${enumName(nativeSecurityMode)}")
            /** 上报成功、失败和限流的方法。 */
            val reportUnlockAttemptMethod = securityCallbackClass.getDeclaredMethod(
                REPORT_UNLOCK_ATTEMPT_METHOD,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            )
            /** 请求原生解锁的方法。 */
            val dismissMethod = securityCallbackClass.getDeclaredMethod(
                DISMISS_METHOD,
                Int::class.javaPrimitiveType,
                securityModeClass,
            )
            /** 通知输入的方法。 */
            val onUserInputMethod = securityCallbackClass.getDeclaredMethod(ON_USER_INPUT_METHOD)
            /** 刷新用户活动的方法。 */
            val userActivityMethod = securityCallbackClass.getDeclaredMethod(USER_ACTIVITY_METHOD)
            /** 写入锁定截止时间的方法。 */
            val setLockoutDeadlineMethod = lockPatternUtilsClass.getDeclaredMethod(
                SET_LOCKOUT_DEADLINE_METHOD,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            /** 读取系统现有锁定截止时间的方法。 */
            val getLockoutDeadlineMethod = lockPatternUtilsClass.getDeclaredMethod(
                GET_LOCKOUT_DEADLINE_METHOD,
                Int::class.javaPrimitiveType,
            )
            return Titan2KeyguardSecurityBridge(
                controller = controller,
                lockPatternUtils = lockPatternUtils,
                securityCallback = securityCallback,
                securityModel = securityModel,
                selectedUserInteractor = selectedUserInteractor,
                userId = userId,
                credentialMode = credentialMode,
                nativeSecurityMode = nativeSecurityMode,
                currentSecurityModeField = currentSecurityModeField,
                getSecurityModeMethod = getSecurityModeMethod,
                getSelectedUserIdMethod = getSelectedUserIdMethod,
                reportUnlockAttemptMethod = reportUnlockAttemptMethod,
                dismissMethod = dismissMethod,
                onUserInputMethod = onUserInputMethod,
                userActivityMethod = userActivityMethod,
                setLockoutDeadlineMethod = setLockoutDeadlineMethod,
                getLockoutDeadlineMethod = getLockoutDeadlineMethod,
            )
        }

        /** 按精确名称和类型解析公开字段。 */
        private fun exactField(owner: Class<*>, name: String, expectedType: Class<*>): Field {
            /** 目标控制器字段。 */
            val field = owner.getDeclaredField(name)
            check(field.type == expectedType) { "keyguard_field_type:$name" }
            field.isAccessible = true
            return field
        }

        /** 按精确名称解析实现指定系统接口的字段。 */
        private fun assignableField(owner: Class<*>, name: String, contractType: Class<*>): Field {
            /** 目标控制器持有的接口实现字段。 */
            val field = owner.getDeclaredField(name)
            check(contractType.isAssignableFrom(field.type)) { "keyguard_field_contract:$name" }
            field.isAccessible = true
            return field
        }

        /** 优先使用控制器当前模式，尚未初始化时回退到系统安全模型。 */
        private fun resolveNativeSecurityMode(
            controller: Any,
            securityModel: Any,
            userId: Int,
            currentSecurityModeField: Field,
            getSecurityModeMethod: Method,
        ): Any {
            /** 控制器当前字段中的模式。 */
            val controllerMode = currentSecurityModeField.get(controller)
            if (controllerMode != null && modeOf(controllerMode) != null) {
                return controllerMode
            }
            return getSecurityModeMethod.invoke(securityModel, userId)
                ?: error("keyguard_security_mode")
        }

        /** 把支持的原生枚举名映射到通用凭据模式。 */
        private fun modeOf(nativeMode: Any): Titan2CredentialMode? = when (enumName(nativeMode)) {
            MODE_PATTERN -> Titan2CredentialMode.PATTERN
            MODE_PIN -> Titan2CredentialMode.PIN
            MODE_PASSWORD -> Titan2CredentialMode.PASSWORD
            else -> null
        }

        /** 安全读取原生枚举名，不调用可能含额外状态的业务 `toString()`。 */
        private fun enumName(nativeMode: Any): String = (nativeMode as? Enum<*>)?.name
            ?: error("keyguard_security_mode_enum")

        /** 普通失败和成功上报使用的零限流值。 */
        private const val NO_TIMEOUT: Int = 0

        /** Titan 2 安全容器控制器类名。 */
        private const val CONTROLLER_CLASS: String =
            "com.android.keyguard.KeyguardSecurityContainerController"

        /** Android LockPatternUtils 类名。 */
        private const val LOCK_PATTERN_UTILS_CLASS: String = "com.android.internal.widget.LockPatternUtils"

        /** Titan 2 安全模式模型类名。 */
        private const val SECURITY_MODEL_CLASS: String = "com.android.keyguard.KeyguardSecurityModel"

        /** Titan 2 安全模式枚举类名。 */
        private const val SECURITY_MODE_CLASS: String =
            "com.android.keyguard.KeyguardSecurityModel\$SecurityMode"

        /** SystemUI 安全回调接口类名。 */
        private const val SECURITY_CALLBACK_CLASS: String = "com.android.keyguard.KeyguardSecurityCallback"

        /** SystemUI 当前用户读取器类名。 */
        private const val SELECTED_USER_INTERACTOR_CLASS: String =
            "com.android.systemui.user.domain.interactor.SelectedUserInteractor"

        /** LockPatternUtils 字段名。 */
        private const val LOCK_PATTERN_UTILS_FIELD: String = "mLockPatternUtils"

        /** 安全回调字段名。 */
        private const val SECURITY_CALLBACK_FIELD: String = "mKeyguardSecurityCallback"

        /** 安全模型字段名。 */
        private const val SECURITY_MODEL_FIELD: String = "mSecurityModel"

        /** 当前用户读取器字段名。 */
        private const val SELECTED_USER_INTERACTOR_FIELD: String = "mSelectedUserInteractor"

        /** 当前原生安全模式字段名。 */
        private const val CURRENT_SECURITY_MODE_FIELD: String = "mCurrentSecurityMode"

        /** 当前用户 ID 方法名。 */
        private const val GET_SELECTED_USER_ID_METHOD: String = "getSelectedUserId"

        /** 按用户获取安全模式的方法名。 */
        private const val GET_SECURITY_MODE_METHOD: String = "getSecurityMode"

        /** 上报解锁尝试的方法名。 */
        private const val REPORT_UNLOCK_ATTEMPT_METHOD: String = "reportUnlockAttempt"

        /** 请求原生解锁的方法名。 */
        private const val DISMISS_METHOD: String = "dismiss"

        /** 通知用户输入的方法名。 */
        private const val ON_USER_INPUT_METHOD: String = "onUserInput"

        /** 刷新用户活动的方法名。 */
        private const val USER_ACTIVITY_METHOD: String = "userActivity"

        /** 写入锁定截止时间的方法名。 */
        private const val SET_LOCKOUT_DEADLINE_METHOD: String = "setLockoutAttemptDeadline"

        /** 读取系统锁定截止时间的方法名。 */
        private const val GET_LOCKOUT_DEADLINE_METHOD: String = "getLockoutAttemptDeadline"

        /** 图案模式枚举名。 */
        private const val MODE_PATTERN: String = "Pattern"

        /** PIN 模式枚举名。 */
        private const val MODE_PIN: String = "PIN"

        /** 密码模式枚举名。 */
        private const val MODE_PASSWORD: String = "Password"
    }
}

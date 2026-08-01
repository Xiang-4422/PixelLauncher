package com.purride.pixellockscreen

import android.annotation.SuppressLint
import com.purride.pixellockscreen.ui.LockscreenBiometricModality
import com.purride.pixellockscreen.ui.LockscreenBiometricPhase
import com.purride.pixellockscreen.ui.LockscreenBiometricUiState
import java.lang.reflect.Field
import java.lang.reflect.Method

/** Titan 2 原生生物识别组件的一次只读、非敏感快照输入。 */
internal data class Titan2BiometricSnapshotInput(
    /** 当前用户是否注册且允许使用指纹硬件。 */
    val fingerprintEnrolled: Boolean,
    /** 当前用户是否启用并注册人脸。 */
    val faceEnrolled: Boolean,
    /** 指纹传感器当前是否正在监听。 */
    val fingerprintRunning: Boolean,
    /** 人脸传感器当前是否正在监听。 */
    val faceRunning: Boolean,
    /** 指纹是否被系统临时或永久锁定。 */
    val fingerprintLockedOut: Boolean,
    /** 人脸是否在当前认证会话中被系统锁定。 */
    val faceLockedOut: Boolean,
    /** StrongAuth 是否允许当前指纹解锁。 */
    val fingerprintAllowed: Boolean,
    /** StrongAuth 是否允许当前人脸解锁。 */
    val faceAllowed: Boolean,
    /** 当前用户是否已由任一系统生物识别方式认证。 */
    val authenticated: Boolean,
    /** Android 当前用户的 StrongAuth 原始位标志。 */
    val strongAuthFlags: Int,
    /** SystemUI 当前可见的生物识别提示。 */
    val messageText: String,
)

/**
 * 只读复用 Titan 2 `KeyguardUpdateMonitor` 与 `KeyguardIndicationController` 的安全状态适配器。
 *
 * 适配器不注册传感器回调、不启动或停止认证，也不读取模板、图像、特征、令牌和失败次数。
 * 所有阶段均由 SystemUI 已维护的字段与公开方法在绘制前解析。
 */
internal class Titan2BiometricStateAdapter private constructor(
    /** 原生锁屏提示控制器。 */
    private val indicationController: Any,
    /** 原生 Keyguard 认证状态监视器。 */
    private val updateMonitor: Any,
    /** 用于检测监视器身份是否变化的字段。 */
    private val updateMonitorField: Field,
    /** 原生主生物识别消息字段。 */
    private val biometricMessageField: Field,
    /** 原生后续生物识别消息字段。 */
    private val biometricFollowUpField: Field,
    /** 当前会话人脸锁定字段。 */
    private val faceLockedOutField: Field,
    /** 读取当前 Keyguard 用户的方法。 */
    private val getCurrentUserMethod: Method,
    /** 读取当前用户指纹注册与策略可用性的方法。 */
    private val isFingerprintPossibleMethod: Method,
    /** 读取当前用户人脸注册状态的方法。 */
    private val isFaceEnrolledMethod: Method,
    /** 读取指纹监听状态的方法。 */
    private val isFingerprintRunningMethod: Method,
    /** 读取人脸监听状态的方法。 */
    private val isFaceRunningMethod: Method,
    /** 读取指纹锁定状态的方法。 */
    private val isFingerprintLockedOutMethod: Method,
    /** 读取 StrongAuth 对指定方式许可的方法。 */
    private val isBiometricAllowedMethod: Method,
    /** 读取当前用户是否已由生物识别认证的方法。 */
    private val isAuthenticatedMethod: Method,
    /** 原生状态监视器持有的 StrongAuth 追踪器字段。 */
    private val strongAuthTrackerField: Field,
    /** 读取当前用户 StrongAuth 位标志的方法。 */
    private val getStrongAuthFlagsMethod: Method,
    /** Android 隐藏枚举中的指纹来源对象。 */
    private val fingerprintSource: Any,
    /** Android 隐藏枚举中的人脸来源对象。 */
    private val faceSource: Any,
) {
    /** 从原生对象读取一帧状态并转换为像素 UI 状态。 */
    fun snapshot(): LockscreenBiometricUiState {
        check(updateMonitorField.get(indicationController) === updateMonitor) {
            "keyguard_biometric_monitor_stale"
        }
        /** 当前原生 Keyguard 用户 ID。 */
        val userId = readInt(getCurrentUserMethod, indicationController, "biometric_user")
        /** 当前原生 StrongAuth 追踪器。 */
        val strongAuthTracker = requireNotNull(strongAuthTrackerField.get(updateMonitor)) {
            "biometric_strong_auth_tracker"
        }
        /** 当前用户是否具有可用指纹。 */
        val fingerprintEnrolled = readBoolean(
            isFingerprintPossibleMethod,
            updateMonitor,
            "biometric_fingerprint_possible",
            userId,
        )
        /** 当前用户是否具有可用人脸。 */
        val faceEnrolled = readBoolean(
            isFaceEnrolledMethod,
            updateMonitor,
            "biometric_face_enrolled",
        )
        /** SystemUI 当前生物识别主消息。 */
        val primaryMessage = biometricMessageField.get(indicationController) as? CharSequence
        /** SystemUI 当前生物识别后续消息。 */
        val followUpMessage = biometricFollowUpField.get(indicationController) as? CharSequence
        return resolveTitan2BiometricState(
            Titan2BiometricSnapshotInput(
                fingerprintEnrolled = fingerprintEnrolled,
                faceEnrolled = faceEnrolled,
                fingerprintRunning = readBoolean(
                    isFingerprintRunningMethod,
                    updateMonitor,
                    "biometric_fingerprint_running",
                ),
                faceRunning = readBoolean(
                    isFaceRunningMethod,
                    updateMonitor,
                    "biometric_face_running",
                ),
                fingerprintLockedOut = readBoolean(
                    isFingerprintLockedOutMethod,
                    updateMonitor,
                    "biometric_fingerprint_locked",
                ),
                faceLockedOut = faceLockedOutField.getBoolean(indicationController),
                fingerprintAllowed = !fingerprintEnrolled || readBoolean(
                    isBiometricAllowedMethod,
                    updateMonitor,
                    "biometric_fingerprint_allowed",
                    fingerprintSource,
                ),
                faceAllowed = !faceEnrolled || readBoolean(
                    isBiometricAllowedMethod,
                    updateMonitor,
                    "biometric_face_allowed",
                    faceSource,
                ),
                authenticated = readBoolean(
                    isAuthenticatedMethod,
                    updateMonitor,
                    "biometric_authenticated",
                    userId,
                ),
                strongAuthFlags = readInt(
                    getStrongAuthFlagsMethod,
                    strongAuthTracker,
                    "biometric_strong_auth_flags",
                    userId,
                ),
                messageText = sanitizeBiometricMessage(primaryMessage, followUpMessage),
            ),
        )
    }

    /** 调用精确方法并要求原始布尔返回值。 */
    private fun readBoolean(method: Method, receiver: Any, error: String, vararg args: Any): Boolean =
        method.invoke(receiver, *args) as? Boolean ?: error(error)

    /** 调用精确方法并要求原始整数返回值。 */
    private fun readInt(method: Method, receiver: Any, error: String, vararg args: Any): Int =
        method.invoke(receiver, *args) as? Int ?: error(error)

    internal companion object {
        /** 按精确类、字段和方法签名绑定已启动的 Titan 2 提示控制器。 */
        @SuppressLint("BlockedPrivateApi", "PrivateApi")
        fun bind(indicationController: Any): Titan2BiometricStateAdapter {
            /** SystemUI 最终类加载器。 */
            val classLoader = indicationController.javaClass.classLoader
                ?: error("biometric_class_loader")
            /** Titan 2 锁屏提示控制器类。 */
            val indicationClass = Class.forName(INDICATION_CONTROLLER_CLASS, false, classLoader)
            check(indicationController.javaClass == indicationClass) {
                "biometric_indication_controller_type"
            }
            /** Titan 2 Keyguard 状态监视器类。 */
            val monitorClass = Class.forName(UPDATE_MONITOR_CLASS, false, classLoader)
            /** Titan 2 StrongAuth 追踪器字段类型。 */
            val strongAuthTrackerClass = Class.forName(
                STRONG_AUTH_TRACKER_CLASS,
                false,
                classLoader,
            )
            /** Android 框架隐藏的生物识别来源枚举类。 */
            val biometricSourceClass = Class.forName(
                BIOMETRIC_SOURCE_CLASS,
                false,
                classLoader,
            )
            /** 隐藏枚举中的精确指纹来源对象。 */
            val fingerprintSource = enumConstant(biometricSourceClass, SOURCE_FINGERPRINT)
            /** 隐藏枚举中的精确人脸来源对象。 */
            val faceSource = enumConstant(biometricSourceClass, SOURCE_FACE)
            /** 提示控制器持有的状态监视器字段。 */
            val updateMonitorField = typedField(
                indicationClass,
                UPDATE_MONITOR_FIELD,
                monitorClass,
            )
            /** 当前状态监视器实例。 */
            val updateMonitor = requireNotNull(updateMonitorField.get(indicationController)) {
                "biometric_update_monitor"
            }
            return Titan2BiometricStateAdapter(
                indicationController = indicationController,
                updateMonitor = updateMonitor,
                updateMonitorField = updateMonitorField,
                biometricMessageField = typedField(
                    indicationClass,
                    BIOMETRIC_MESSAGE_FIELD,
                    CharSequence::class.java,
                ),
                biometricFollowUpField = typedField(
                    indicationClass,
                    BIOMETRIC_FOLLOW_UP_FIELD,
                    CharSequence::class.java,
                ),
                faceLockedOutField = typedField(
                    indicationClass,
                    FACE_LOCKED_OUT_FIELD,
                    Boolean::class.javaPrimitiveType!!,
                ),
                getCurrentUserMethod = exactMethod(
                    indicationClass,
                    GET_CURRENT_USER_METHOD,
                    Int::class.javaPrimitiveType!!,
                ),
                isFingerprintPossibleMethod = exactMethod(
                    monitorClass,
                    IS_FINGERPRINT_POSSIBLE_METHOD,
                    Boolean::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!,
                ),
                isFaceEnrolledMethod = exactMethod(
                    monitorClass,
                    IS_FACE_ENROLLED_METHOD,
                    Boolean::class.javaPrimitiveType!!,
                ),
                isFingerprintRunningMethod = exactMethod(
                    monitorClass,
                    IS_FINGERPRINT_RUNNING_METHOD,
                    Boolean::class.javaPrimitiveType!!,
                ),
                isFaceRunningMethod = exactMethod(
                    monitorClass,
                    IS_FACE_RUNNING_METHOD,
                    Boolean::class.javaPrimitiveType!!,
                ),
                isFingerprintLockedOutMethod = exactMethod(
                    monitorClass,
                    IS_FINGERPRINT_LOCKED_OUT_METHOD,
                    Boolean::class.javaPrimitiveType!!,
                ),
                isBiometricAllowedMethod = exactMethod(
                    monitorClass,
                    IS_BIOMETRIC_ALLOWED_METHOD,
                    Boolean::class.javaPrimitiveType!!,
                    biometricSourceClass,
                ),
                isAuthenticatedMethod = exactMethod(
                    monitorClass,
                    IS_AUTHENTICATED_METHOD,
                    Boolean::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!,
                ),
                strongAuthTrackerField = typedField(
                    monitorClass,
                    STRONG_AUTH_TRACKER_FIELD,
                    strongAuthTrackerClass,
                ),
                getStrongAuthFlagsMethod = exactPublicMethod(
                    strongAuthTrackerClass,
                    GET_STRONG_AUTH_FLAGS_METHOD,
                    Int::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!,
                ),
                fingerprintSource = fingerprintSource,
                faceSource = faceSource,
            )
        }

        /** 从 Android 隐藏枚举中按稳定名称解析唯一对象。 */
        private fun enumConstant(owner: Class<*>, name: String): Any {
            check(owner.isEnum) { "biometric_source_not_enum" }
            return owner.enumConstants
                ?.singleOrNull { value -> (value as? Enum<*>)?.name == name }
                ?: error("biometric_source_missing:$name")
        }

        /** 解析声明字段并要求类型精确匹配。 */
        private fun typedField(owner: Class<*>, name: String, type: Class<*>): Field =
            owner.getDeclaredField(name).apply {
                check(this.type == type) { "biometric_field_type:$name" }
                isAccessible = true
            }

        /** 解析公开方法并要求返回值和参数列表精确匹配。 */
        private fun exactMethod(
            owner: Class<*>,
            name: String,
            returnType: Class<*>,
            vararg parameterTypes: Class<*>,
        ): Method = owner.getDeclaredMethod(name, *parameterTypes).apply {
            check(this.returnType == returnType) { "biometric_method_type:$name" }
            isAccessible = true
        }

        /** 解析允许由父类声明的公开方法，并要求返回值精确匹配。 */
        private fun exactPublicMethod(
            owner: Class<*>,
            name: String,
            returnType: Class<*>,
            vararg parameterTypes: Class<*>,
        ): Method = owner.getMethod(name, *parameterTypes).apply {
            check(this.returnType == returnType) { "biometric_public_method_type:$name" }
        }

        /** Titan 2 锁屏提示控制器类名。 */
        private const val INDICATION_CONTROLLER_CLASS: String =
            "com.android.systemui.statusbar.KeyguardIndicationController"

        /** Titan 2 Keyguard 状态监视器类名。 */
        private const val UPDATE_MONITOR_CLASS: String = "com.android.keyguard.KeyguardUpdateMonitor"

        /** Titan 2 StrongAuth 追踪器类名。 */
        private const val STRONG_AUTH_TRACKER_CLASS: String =
            "com.android.keyguard.KeyguardUpdateMonitor\$StrongAuthTracker"

        /** Android 框架隐藏的生物识别来源枚举类名。 */
        private const val BIOMETRIC_SOURCE_CLASS: String =
            "android.hardware.biometrics.BiometricSourceType"

        /** 指纹来源隐藏枚举名。 */
        private const val SOURCE_FINGERPRINT: String = "FINGERPRINT"

        /** 人脸来源隐藏枚举名。 */
        private const val SOURCE_FACE: String = "FACE"

        /** 原生状态监视器字段名。 */
        private const val UPDATE_MONITOR_FIELD: String = "mKeyguardUpdateMonitor"

        /** 原生 StrongAuth 追踪器字段名。 */
        private const val STRONG_AUTH_TRACKER_FIELD: String = "mStrongAuthTracker"

        /** 原生主生物识别消息字段名。 */
        private const val BIOMETRIC_MESSAGE_FIELD: String = "mBiometricMessage"

        /** 原生后续生物识别消息字段名。 */
        private const val BIOMETRIC_FOLLOW_UP_FIELD: String = "mBiometricMessageFollowUp"

        /** 当前会话人脸锁定字段名。 */
        private const val FACE_LOCKED_OUT_FIELD: String = "mFaceLockedOutThisAuthSession"

        /** 当前 Keyguard 用户读取方法名。 */
        private const val GET_CURRENT_USER_METHOD: String = "getCurrentUser"

        /** 当前用户指纹可用性方法名。 */
        private const val IS_FINGERPRINT_POSSIBLE_METHOD: String =
            "isUnlockWithFingerprintPossible"

        /** 当前用户人脸注册方法名。 */
        private const val IS_FACE_ENROLLED_METHOD: String = "isFaceEnabledAndEnrolled"

        /** 指纹监听状态方法名。 */
        private const val IS_FINGERPRINT_RUNNING_METHOD: String = "isFingerprintDetectionRunning"

        /** 人脸监听状态方法名。 */
        private const val IS_FACE_RUNNING_METHOD: String = "isFaceDetectionRunning"

        /** 指纹锁定状态方法名。 */
        private const val IS_FINGERPRINT_LOCKED_OUT_METHOD: String = "isFingerprintLockedOut"

        /** StrongAuth 生物识别许可方法名。 */
        private const val IS_BIOMETRIC_ALLOWED_METHOD: String =
            "isUnlockingWithBiometricAllowed"

        /** 当前用户生物识别成功状态方法名。 */
        private const val IS_AUTHENTICATED_METHOD: String = "getUserUnlockedWithBiometric"

        /** 当前用户 StrongAuth 位标志读取方法名。 */
        private const val GET_STRONG_AUTH_FLAGS_METHOD: String = "getStrongAuthForUser"
    }
}

/** 将原生只读输入按安全优先级转换为单一像素生物识别状态。 */
internal fun resolveTitan2BiometricState(
    input: Titan2BiometricSnapshotInput,
): LockscreenBiometricUiState {
    /** 当前已注册传感器组合。 */
    val modality = when {
        input.fingerprintEnrolled && input.faceEnrolled ->
            LockscreenBiometricModality.FACE_AND_FINGERPRINT
        input.fingerprintEnrolled -> LockscreenBiometricModality.FINGERPRINT
        input.faceEnrolled -> LockscreenBiometricModality.FACE
        else -> LockscreenBiometricModality.NONE
    }
    if (modality == LockscreenBiometricModality.NONE) {
        return if (input.strongAuthFlags != STRONG_AUTH_NOT_REQUIRED) {
            LockscreenBiometricUiState(
                modality = LockscreenBiometricModality.NONE,
                phase = LockscreenBiometricPhase.STRONG_AUTH_REQUIRED,
                messageText = strongAuthMessage(input.strongAuthFlags),
            )
        } else {
            LockscreenBiometricUiState()
        }
    }
    /** 至少一种已注册方式当前是否被 StrongAuth 允许。 */
    val anyAllowed = (input.fingerprintEnrolled && input.fingerprintAllowed) ||
        (input.faceEnrolled && input.faceAllowed)
    /** 至少一种已注册且未锁定的方式是否正在监听。 */
    val anyRunning =
        (input.fingerprintEnrolled && !input.fingerprintLockedOut && input.fingerprintRunning) ||
            (input.faceEnrolled && !input.faceLockedOut && input.faceRunning)
    /** 所有已注册方式是否都已被系统锁定。 */
    val allLockedOut = (!input.fingerprintEnrolled || input.fingerprintLockedOut) &&
        (!input.faceEnrolled || input.faceLockedOut)
    /** 按认证成功、StrongAuth、锁定、系统消息、监听和就绪顺序确定唯一阶段。 */
    val phase = when {
        input.authenticated -> LockscreenBiometricPhase.SUCCESS
        !anyAllowed -> LockscreenBiometricPhase.STRONG_AUTH_REQUIRED
        allLockedOut -> LockscreenBiometricPhase.LOCKED_OUT
        input.messageText.isNotBlank() -> LockscreenBiometricPhase.ERROR
        anyRunning -> LockscreenBiometricPhase.SCANNING
        else -> LockscreenBiometricPhase.READY
    }
    return LockscreenBiometricUiState(
        modality = modality,
        phase = phase,
        messageText = when (phase) {
            LockscreenBiometricPhase.ERROR -> input.messageText
            LockscreenBiometricPhase.STRONG_AUTH_REQUIRED ->
                strongAuthMessage(input.strongAuthFlags)
            else -> ""
        },
    )
}

/** 按 Android StrongAuth 位标志输出不推断安全决策的固定提示。 */
internal fun strongAuthMessage(flags: Int): String = when {
    flags and STRONG_AUTH_AFTER_USER_LOCKDOWN != 0 -> "LOCKDOWN - USE CREDENTIAL"
    flags and STRONG_AUTH_AFTER_BOOT != 0 -> "DEVICE RESTARTED - USE CREDENTIAL"
    flags and STRONG_AUTH_AFTER_DPM_LOCK_NOW != 0 -> "ADMIN LOCK - USE CREDENTIAL"
    flags and STRONG_AUTH_AFTER_LOCKOUT != 0 -> "TOO MANY ATTEMPTS - USE CREDENTIAL"
    flags and STRONG_AUTH_AFTER_TIMEOUT != 0 -> "TIMEOUT - USE CREDENTIAL"
    flags != STRONG_AUTH_NOT_REQUIRED -> "USE DEVICE CREDENTIAL"
    else -> ""
}

/** 合并并清理 SystemUI 两段非敏感生物识别消息，使其适合单行像素布局。 */
internal fun sanitizeBiometricMessage(primary: CharSequence?, followUp: CharSequence?): String =
    listOfNotNull(primary, followUp)
        .joinToString(separator = " ") { message ->
            message.toString().replace('\n', ' ').replace('\r', ' ').trim()
        }
        .replace(BIOMETRIC_WHITESPACE_REGEX, " ")
        .take(MAXIMUM_BIOMETRIC_MESSAGE_LENGTH)

/** 与锁屏 UI 状态边界一致的最大生物识别消息长度。 */
private const val MAXIMUM_BIOMETRIC_MESSAGE_LENGTH: Int = 160

/** 复用的系统消息空白折叠表达式，避免每帧创建正则对象。 */
private val BIOMETRIC_WHITESPACE_REGEX: Regex = Regex("\\s+")

/** Android 表示当前无需强认证的位值。 */
private const val STRONG_AUTH_NOT_REQUIRED: Int = 0x0

/** Android 表示设备重启后必须强认证的位值。 */
private const val STRONG_AUTH_AFTER_BOOT: Int = 0x1

/** Android 表示设备管理员锁定后必须强认证的位值。 */
private const val STRONG_AUTH_AFTER_DPM_LOCK_NOW: Int = 0x2

/** Android 表示凭据失败锁定后必须强认证的位值。 */
private const val STRONG_AUTH_AFTER_LOCKOUT: Int = 0x8

/** Android 表示认证超时后必须强认证的位值。 */
private const val STRONG_AUTH_AFTER_TIMEOUT: Int = 0x10

/** Android 表示用户主动 Lockdown 后必须强认证的位值。 */
private const val STRONG_AUTH_AFTER_USER_LOCKDOWN: Int = 0x20

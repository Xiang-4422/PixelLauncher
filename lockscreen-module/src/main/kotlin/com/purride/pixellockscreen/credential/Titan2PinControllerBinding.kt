package com.purride.pixellockscreen.credential

import android.annotation.SuppressLint
import android.view.ViewGroup
import java.lang.reflect.Field

/** Titan 2 当前 PIN 控制器中完成运行时接管所需的最小对象绑定。 */
internal data class Titan2PinControllerBinding(
    /** 原生 PIN 控制器实例。 */
    val controller: Any,
    /** 原生 PIN 页面根视图。 */
    val pinView: ViewGroup,
    /** 原生 PIN 控制器使用的 Keyguard 安全回调。 */
    val securityCallback: Any,
    /** 原生 PIN 控制器使用的安全模式枚举对象。 */
    val securityMode: Any,
    /** 系统启用自动确认时要求的精确 PIN 长度。 */
    val autoConfirmLength: Int?,
) {
    internal companion object {
        /** 类、字段、模式或自动确认合同不匹配时拒绝接管当前 ROM。 */
        @SuppressLint("BlockedPrivateApi", "PrivateApi")
        fun bind(controller: Any, classLoader: ClassLoader): Titan2PinControllerBinding {
            /** Titan 2 PIN 控制器类。 */
            val controllerClass = Class.forName(PIN_CONTROLLER_CLASS, false, classLoader)
            check(controllerClass.isInstance(controller)) { "keyguard_pin_controller_instance" }
            /** Titan 2 PIN 根视图类。 */
            val pinViewClass = Class.forName(PIN_VIEW_CLASS, false, classLoader)
            /** SystemUI 安全回调接口。 */
            val securityCallbackClass = Class.forName(SECURITY_CALLBACK_CLASS, false, classLoader)
            /** SystemUI 安全模式枚举。 */
            val securityModeClass = Class.forName(SECURITY_MODE_CLASS, false, classLoader)
            /** ViewController 继承层级中的原生页面字段。 */
            val viewField = hierarchyField(controllerClass, VIEW_FIELD)
            /** KeyguardInputViewController 继承层级中的安全回调字段。 */
            val callbackField = hierarchyField(controllerClass, SECURITY_CALLBACK_FIELD)
            check(securityCallbackClass.isAssignableFrom(callbackField.type)) {
                "keyguard_pin_callback_field"
            }
            /** KeyguardInputViewController 继承层级中的安全模式字段。 */
            val securityModeField = hierarchyField(controllerClass, SECURITY_MODE_FIELD)
            check(securityModeField.type == securityModeClass) { "keyguard_pin_mode_field" }
            /** 当前原生 PIN 页面对象。 */
            val pinView = requireNotNull(viewField.get(controller)) { "keyguard_pin_view" }
            check(pinViewClass.isInstance(pinView) && pinView is ViewGroup) {
                "keyguard_pin_view_instance"
            }
            /** 当前原生安全回调对象。 */
            val securityCallback = requireNotNull(callbackField.get(controller)) {
                "keyguard_pin_callback"
            }
            check(securityCallbackClass.isInstance(securityCallback)) {
                "keyguard_pin_callback_instance"
            }
            /** 当前原生安全模式对象。 */
            val securityMode = requireNotNull(securityModeField.get(controller)) {
                "keyguard_pin_mode"
            }
            check((securityMode as? Enum<*>)?.name == MODE_PIN) { "keyguard_pin_mode_name" }
            /** Titan 2 判断当前用户是否启用自动确认的方法。 */
            val autoConfirmMethod = controllerClass.getDeclaredMethod(AUTO_CONFIRM_METHOD).apply {
                check(returnType == Boolean::class.javaPrimitiveType) {
                    "keyguard_pin_auto_confirm_method"
                }
                isAccessible = true
            }
            /** 系统当前自动确认开关。 */
            val autoConfirmEnabled = autoConfirmMethod.invoke(controller) as? Boolean
                ?: error("keyguard_pin_auto_confirm_result")
            /** Titan 2 控制器缓存的系统 PIN 长度字段。 */
            val pinLengthField = controllerClass.getDeclaredField(PIN_LENGTH_FIELD).apply {
                check(type == Long::class.javaPrimitiveType) { "keyguard_pin_length_field" }
                isAccessible = true
            }
            /** 只在自动确认启用时暴露经过边界校验的长度。 */
            val autoConfirmLength = validatedPinAutoConfirmLength(
                enabled = autoConfirmEnabled,
                pinLength = pinLengthField.getLong(controller),
            )
            return Titan2PinControllerBinding(
                controller = controller,
                pinView = pinView,
                securityCallback = securityCallback,
                securityMode = securityMode,
                autoConfirmLength = autoConfirmLength,
            )
        }

        /** 沿控制器父类链按精确名称解析字段。 */
        private fun hierarchyField(owner: Class<*>, name: String): Field {
            /** 当前待检查的控制器类。 */
            var current: Class<*>? = owner
            while (current != null) {
                /** 当前类中可能存在的目标字段。 */
                val field = runCatching { current.getDeclaredField(name) }.getOrNull()
                if (field != null) {
                    field.isAccessible = true
                    return field
                }
                current = current.superclass
            }
            error("keyguard_pin_field_missing:$name")
        }

        /** Titan 2 PIN 控制器类名。 */
        private const val PIN_CONTROLLER_CLASS: String =
            "com.android.keyguard.KeyguardPinViewController"

        /** Titan 2 PIN 页面类名。 */
        private const val PIN_VIEW_CLASS: String = "com.android.keyguard.KeyguardPINView"

        /** SystemUI 安全回调接口类名。 */
        private const val SECURITY_CALLBACK_CLASS: String =
            "com.android.keyguard.KeyguardSecurityCallback"

        /** SystemUI 安全模式枚举类名。 */
        private const val SECURITY_MODE_CLASS: String =
            "com.android.keyguard.KeyguardSecurityModel\$SecurityMode"

        /** 通用 ViewController 原生 View 字段名。 */
        private const val VIEW_FIELD: String = "mView"

        /** KeyguardInputViewController 安全回调字段名。 */
        private const val SECURITY_CALLBACK_FIELD: String = "mKeyguardSecurityCallback"

        /** KeyguardInputViewController 安全模式字段名。 */
        private const val SECURITY_MODE_FIELD: String = "mSecurityMode"

        /** Titan 2 自动确认设置查询方法名。 */
        private const val AUTO_CONFIRM_METHOD: String = "isAutoPinConfirmEnabledInSettings"

        /** Titan 2 控制器缓存的 PIN 长度字段名。 */
        private const val PIN_LENGTH_FIELD: String = "mPinLength"

        /** Titan 2 PIN 安全模式枚举名。 */
        private const val MODE_PIN: String = "PIN"
    }
}

/** 把系统自动确认开关与长整型 PIN 长度收敛为像素协调器可接受的安全边界。 */
internal fun validatedPinAutoConfirmLength(enabled: Boolean, pinLength: Long): Int? {
    if (!enabled) {
        return null
    }
    check(pinLength in MINIMUM_AUTO_CONFIRM_LENGTH..MAXIMUM_AUTO_CONFIRM_LENGTH) {
        "keyguard_pin_auto_confirm_length"
    }
    return pinLength.toInt()
}

/** Android 设备凭据允许提交的最短 PIN 长度。 */
private const val MINIMUM_AUTO_CONFIRM_LENGTH: Long = 4L

/** 与模块可清零字符缓冲一致的 PIN 长度上限。 */
private const val MAXIMUM_AUTO_CONFIRM_LENGTH: Long = 64L

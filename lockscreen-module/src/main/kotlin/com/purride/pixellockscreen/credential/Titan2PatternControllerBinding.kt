package com.purride.pixellockscreen.credential

import android.annotation.SuppressLint
import android.view.ViewGroup
import java.lang.reflect.Field

/** Titan 2 当前图案控制器中完成运行时接管所需的最小对象绑定。 */
internal data class Titan2PatternControllerBinding(
    /** 原生图案控制器实例。 */
    val controller: Any,
    /** 原生图案页面根视图。 */
    val patternView: ViewGroup,
    /** 原生图案控制器使用的 Keyguard 安全回调。 */
    val securityCallback: Any,
    /** 原生图案控制器使用的安全模式枚举对象。 */
    val securityMode: Any,
) {
    internal companion object {
        /** 类、字段或对象类型不匹配时拒绝接管当前 ROM。 */
        @SuppressLint("BlockedPrivateApi", "PrivateApi")
        fun bind(controller: Any, classLoader: ClassLoader): Titan2PatternControllerBinding {
            /** Titan 2 图案控制器类。 */
            val controllerClass = Class.forName(PATTERN_CONTROLLER_CLASS, false, classLoader)
            check(controllerClass.isInstance(controller)) { "keyguard_pattern_controller_instance" }
            /** Titan 2 图案根视图类。 */
            val patternViewClass = Class.forName(PATTERN_VIEW_CLASS, false, classLoader)
            /** SystemUI 安全回调接口。 */
            val securityCallbackClass = Class.forName(SECURITY_CALLBACK_CLASS, false, classLoader)
            /** SystemUI 安全模式枚举。 */
            val securityModeClass = Class.forName(SECURITY_MODE_CLASS, false, classLoader)
            /** ViewController 继承层级中的原生页面字段。 */
            val viewField = hierarchyField(controllerClass, VIEW_FIELD)
            /** KeyguardInputViewController 继承层级中的安全回调字段。 */
            val callbackField = hierarchyField(controllerClass, SECURITY_CALLBACK_FIELD)
            check(securityCallbackClass.isAssignableFrom(callbackField.type)) {
                "keyguard_pattern_callback_field"
            }
            /** KeyguardInputViewController 继承层级中的安全模式字段。 */
            val securityModeField = hierarchyField(controllerClass, SECURITY_MODE_FIELD)
            check(securityModeField.type == securityModeClass) { "keyguard_pattern_mode_field" }
            /** 当前原生图案页面对象。 */
            val patternView = requireNotNull(viewField.get(controller)) { "keyguard_pattern_view" }
            check(patternViewClass.isInstance(patternView) && patternView is ViewGroup) {
                "keyguard_pattern_view_instance"
            }
            /** 当前原生安全回调对象。 */
            val securityCallback = requireNotNull(callbackField.get(controller)) {
                "keyguard_pattern_callback"
            }
            check(securityCallbackClass.isInstance(securityCallback)) {
                "keyguard_pattern_callback_instance"
            }
            /** 当前原生安全模式对象。 */
            val securityMode = requireNotNull(securityModeField.get(controller)) {
                "keyguard_pattern_mode"
            }
            check((securityMode as? Enum<*>)?.name == MODE_PATTERN) {
                "keyguard_pattern_mode_name"
            }
            return Titan2PatternControllerBinding(
                controller = controller,
                patternView = patternView,
                securityCallback = securityCallback,
                securityMode = securityMode,
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
            error("keyguard_pattern_field_missing:$name")
        }

        /** Titan 2 图案控制器类名。 */
        private const val PATTERN_CONTROLLER_CLASS: String =
            "com.android.keyguard.KeyguardPatternViewController"

        /** Titan 2 图案页面类名。 */
        private const val PATTERN_VIEW_CLASS: String = "com.android.keyguard.KeyguardPatternView"

        /** SystemUI 安全回调接口类名。 */
        private const val SECURITY_CALLBACK_CLASS: String = "com.android.keyguard.KeyguardSecurityCallback"

        /** SystemUI 安全模式枚举类名。 */
        private const val SECURITY_MODE_CLASS: String =
            "com.android.keyguard.KeyguardSecurityModel\$SecurityMode"

        /** 通用 ViewController 原生 View 字段名。 */
        private const val VIEW_FIELD: String = "mView"

        /** KeyguardInputViewController 安全回调字段名。 */
        private const val SECURITY_CALLBACK_FIELD: String = "mKeyguardSecurityCallback"

        /** KeyguardInputViewController 安全模式字段名。 */
        private const val SECURITY_MODE_FIELD: String = "mSecurityMode"

        /** Titan 2 图案安全模式枚举名。 */
        private const val MODE_PATTERN: String = "Pattern"
    }
}

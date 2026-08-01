package com.purride.pixellockscreen.credential

import android.annotation.SuppressLint
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Titan 2 设备凭据页原生紧急操作的精确反射桥。
 *
 * 该桥不复制拨号、通话返回或 Keyguard 上报逻辑，只在原生按钮仍然挂载且可用时调用
 * `performClick()`，让 ROM 自己的 `EmergencyButtonController` 继续处理完整安全流程。
 */
internal class Titan2EmergencyActionBridge private constructor(
    /** 当前原生设备凭据控制器。 */
    private val credentialController: Any,
    /** 当前原生紧急按钮控制器。 */
    private val emergencyController: Any,
    /** 当前原生紧急按钮。 */
    private val emergencyButton: Any,
    /** 从凭据控制器继承层级读取紧急按钮控制器的字段。 */
    private val emergencyControllerField: Field,
    /** 从通用 ViewController 读取原生按钮的字段。 */
    private val controllerViewField: Field,
    /** 判断按钮是否仍挂载的方法。 */
    private val isAttachedToWindowMethod: Method,
    /** 判断按钮是否启用的方法。 */
    private val isEnabledMethod: Method,
    /** 读取按钮自身可见状态的方法。 */
    private val getVisibilityMethod: Method,
    /** 判断原生点击监听器是否存在的方法。 */
    private val hasOnClickListenersMethod: Method,
    /** 触发 ROM 原生紧急操作的方法。 */
    private val performClickMethod: Method,
) {
    /** 桥是否已经失效。 */
    private var disposed: Boolean = false

    /**
     * 请求当前原生按钮执行一次紧急操作。
     *
     * 任一绑定对象或可用状态发生变化都会抛出异常，由上层立即恢复原生 Bouncer。
     */
    fun requestEmergencyAction() {
        check(!disposed) { "keyguard_emergency_bridge_disposed" }
        check(emergencyControllerField.get(credentialController) === emergencyController) {
            "keyguard_emergency_controller_stale"
        }
        check(controllerViewField.get(emergencyController) === emergencyButton) {
            "keyguard_emergency_button_stale"
        }
        check(readBoolean(isAttachedToWindowMethod, "keyguard_emergency_detached")) {
            "keyguard_emergency_detached"
        }
        check(readBoolean(isEnabledMethod, "keyguard_emergency_disabled")) {
            "keyguard_emergency_disabled"
        }
        check(readInt(getVisibilityMethod, "keyguard_emergency_visibility") == VIEW_VISIBLE) {
            "keyguard_emergency_hidden"
        }
        check(readBoolean(hasOnClickListenersMethod, "keyguard_emergency_listener")) {
            "keyguard_emergency_listener_missing"
        }
        check(readBoolean(performClickMethod, "keyguard_emergency_click")) {
            "keyguard_emergency_click_rejected"
        }
    }

    /** 使当前绑定幂等失效，后续请求必须回退原生页面。 */
    fun dispose() {
        disposed = true
    }

    /** 调用无参方法并要求返回原始布尔值。 */
    private fun readBoolean(method: Method, error: String): Boolean =
        method.invoke(emergencyButton) as? Boolean ?: error(error)

    /** 调用无参方法并要求返回原始整数值。 */
    private fun readInt(method: Method, error: String): Int =
        method.invoke(emergencyButton) as? Int ?: error(error)

    internal companion object {
        /**
         * 绑定已经执行 `onViewAttached()` 的 Titan 2 设备凭据控制器。
         *
         * 类名、字段类型、按钮状态或公开方法签名不一致时拒绝接管。
         */
        @SuppressLint("BlockedPrivateApi", "PrivateApi")
        fun bind(
            credentialController: Any,
            credentialMode: Titan2CredentialMode,
            classLoader: ClassLoader,
        ): Titan2EmergencyActionBridge {
            /** 当前凭据模式对应的 Titan 2 控制器类名。 */
            val controllerClassName = when (credentialMode) {
                Titan2CredentialMode.PATTERN -> PATTERN_CONTROLLER_CLASS
                Titan2CredentialMode.PIN -> PIN_CONTROLLER_CLASS
                Titan2CredentialMode.PASSWORD -> PASSWORD_CONTROLLER_CLASS
            }
            return bindController(credentialController, controllerClassName, classLoader)
        }

        /** 绑定 SIM 或 AntiTheft 页面继承的同一套 ROM 原生紧急入口。 */
        @SuppressLint("BlockedPrivateApi", "PrivateApi")
        fun bindSpecial(
            credentialController: Any,
            mode: Titan2SpecialPinMode,
            classLoader: ClassLoader,
        ): Titan2EmergencyActionBridge = bindController(
            credentialController = credentialController,
            controllerClassName = if (mode.isSim) {
                SIM_CONTROLLER_CLASS
            } else {
                ANTI_THEFT_CONTROLLER_CLASS
            },
            classLoader = classLoader,
        )

        /** 按已白名单化的最终控制器类名复用统一紧急按钮合同。 */
        private fun bindController(
            credentialController: Any,
            controllerClassName: String,
            classLoader: ClassLoader,
        ): Titan2EmergencyActionBridge {
            /** 当前页面对应的 Titan 2 最终控制器类。 */
            val credentialControllerClass = Class.forName(controllerClassName, false, classLoader)
            check(credentialControllerClass.isInstance(credentialController)) {
                "keyguard_credential_controller_instance"
            }
            /** Titan 2 紧急按钮控制器类。 */
            val emergencyControllerClass = Class.forName(
                EMERGENCY_CONTROLLER_CLASS,
                false,
                classLoader,
            )
            /** Titan 2 紧急按钮类。 */
            val emergencyButtonClass = Class.forName(EMERGENCY_BUTTON_CLASS, false, classLoader)
            /** 凭据控制器继承层级持有的紧急按钮控制器字段。 */
            val emergencyControllerField = hierarchyTypedField(
                owner = credentialControllerClass,
                name = EMERGENCY_CONTROLLER_FIELD,
                expectedType = emergencyControllerClass,
            )
            /** 当前紧急按钮控制器。 */
            val emergencyController = requireNotNull(
                emergencyControllerField.get(credentialController),
            ) { "keyguard_emergency_controller" }
            /** ViewController 继承层级中持有原生按钮的字段。 */
            val controllerViewField = hierarchyField(
                owner = emergencyControllerClass,
                name = CONTROLLER_VIEW_FIELD,
            )
            /** 当前紧急按钮对象。 */
            val emergencyButton = requireNotNull(controllerViewField.get(emergencyController)) {
                "keyguard_emergency_button"
            }
            check(emergencyButtonClass.isInstance(emergencyButton)) {
                "keyguard_emergency_button_instance"
            }
            /** 判断按钮是否仍挂载的方法。 */
            val isAttachedToWindowMethod = exactBooleanMethod(
                emergencyButtonClass,
                IS_ATTACHED_TO_WINDOW_METHOD,
            )
            /** 判断按钮是否启用的方法。 */
            val isEnabledMethod = exactBooleanMethod(emergencyButtonClass, IS_ENABLED_METHOD)
            /** 读取按钮可见状态的方法。 */
            val getVisibilityMethod = exactIntMethod(emergencyButtonClass, GET_VISIBILITY_METHOD)
            /** 判断点击监听器是否存在的方法。 */
            val hasOnClickListenersMethod = exactBooleanMethod(
                emergencyButtonClass,
                HAS_ON_CLICK_LISTENERS_METHOD,
            )
            /** 复用 ROM 原生点击链的方法。 */
            val performClickMethod = exactBooleanMethod(emergencyButtonClass, PERFORM_CLICK_METHOD)
            /** 完成所有反射签名解析后的桥。 */
            val bridge = Titan2EmergencyActionBridge(
                credentialController = credentialController,
                emergencyController = emergencyController,
                emergencyButton = emergencyButton,
                emergencyControllerField = emergencyControllerField,
                controllerViewField = controllerViewField,
                isAttachedToWindowMethod = isAttachedToWindowMethod,
                isEnabledMethod = isEnabledMethod,
                getVisibilityMethod = getVisibilityMethod,
                hasOnClickListenersMethod = hasOnClickListenersMethod,
                performClickMethod = performClickMethod,
            )
            bridge.requireAvailable()
            return bridge
        }

        /** 绑定时验证原生按钮已经完成挂载和点击监听器安装。 */
        private fun Titan2EmergencyActionBridge.requireAvailable() {
            check(readBoolean(isAttachedToWindowMethod, "keyguard_emergency_detached")) {
                "keyguard_emergency_detached"
            }
            check(readBoolean(isEnabledMethod, "keyguard_emergency_disabled")) {
                "keyguard_emergency_disabled"
            }
            check(readInt(getVisibilityMethod, "keyguard_emergency_visibility") == VIEW_VISIBLE) {
                "keyguard_emergency_hidden"
            }
            check(readBoolean(hasOnClickListenersMethod, "keyguard_emergency_listener")) {
                "keyguard_emergency_listener_missing"
            }
        }

        /** 沿父类链按精确名称和类型解析字段。 */
        private fun hierarchyTypedField(owner: Class<*>, name: String, expectedType: Class<*>): Field {
            /** 目标字段。 */
            val field = hierarchyField(owner, name)
            check(field.type == expectedType) { "keyguard_emergency_field_type:$name" }
            return field
        }

        /** 沿父类链按精确名称查找字段。 */
        private fun hierarchyField(owner: Class<*>, name: String): Field {
            /** 当前待检查类。 */
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
            error("keyguard_emergency_field_missing:$name")
        }

        /** 解析公开无参布尔方法并校验返回类型。 */
        private fun exactBooleanMethod(owner: Class<*>, name: String): Method {
            /** 包含继承方法的公开目标方法。 */
            val method = owner.getMethod(name)
            check(method.returnType == Boolean::class.javaPrimitiveType) {
                "keyguard_emergency_method_type:$name"
            }
            return method
        }

        /** 解析公开无参整数方法并校验返回类型。 */
        private fun exactIntMethod(owner: Class<*>, name: String): Method {
            /** 包含继承方法的公开目标方法。 */
            val method = owner.getMethod(name)
            check(method.returnType == Int::class.javaPrimitiveType) {
                "keyguard_emergency_method_type:$name"
            }
            return method
        }

        /** Android `View.VISIBLE` 的稳定公开值。 */
        private const val VIEW_VISIBLE: Int = 0

        /** Titan 2 图案认证控制器类名。 */
        private const val PATTERN_CONTROLLER_CLASS: String =
            "com.android.keyguard.KeyguardPatternViewController"

        /** Titan 2 PIN 认证控制器类名。 */
        private const val PIN_CONTROLLER_CLASS: String =
            "com.android.keyguard.KeyguardPinViewController"

        /** Titan 2 密码认证控制器类名。 */
        private const val PASSWORD_CONTROLLER_CLASS: String =
            "com.android.keyguard.KeyguardPasswordViewController"

        /** Titan 2 MediaTek SIM PIN/PUK/ME 控制器类名。 */
        private const val SIM_CONTROLLER_CLASS: String =
            "com.mediatek.keyguard.Telephony.KeyguardSimPinPukMeViewController"

        /** Titan 2 MediaTek 防盗控制器类名。 */
        private const val ANTI_THEFT_CONTROLLER_CLASS: String =
            "com.mediatek.keyguard.AntiTheft.KeyguardAntiTheftLockViewController"

        /** Titan 2 紧急按钮控制器类名。 */
        private const val EMERGENCY_CONTROLLER_CLASS: String =
            "com.android.keyguard.EmergencyButtonController"

        /** Titan 2 紧急按钮类名。 */
        private const val EMERGENCY_BUTTON_CLASS: String = "com.android.keyguard.EmergencyButton"

        /** 凭据控制器继承层级中的紧急按钮控制器字段名。 */
        private const val EMERGENCY_CONTROLLER_FIELD: String = "mEmergencyButtonController"

        /** SystemUI `ViewController` 中的原生 View 字段名。 */
        private const val CONTROLLER_VIEW_FIELD: String = "mView"

        /** Android View 挂载状态方法名。 */
        private const val IS_ATTACHED_TO_WINDOW_METHOD: String = "isAttachedToWindow"

        /** Android View 启用状态方法名。 */
        private const val IS_ENABLED_METHOD: String = "isEnabled"

        /** Android View 可见状态方法名。 */
        private const val GET_VISIBILITY_METHOD: String = "getVisibility"

        /** Android View 点击监听器状态方法名。 */
        private const val HAS_ON_CLICK_LISTENERS_METHOD: String = "hasOnClickListeners"

        /** Android View 原生点击执行方法名。 */
        private const val PERFORM_CLICK_METHOD: String = "performClick"
    }
}

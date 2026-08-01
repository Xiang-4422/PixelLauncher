package com.purride.pixellockscreen.credential

import android.annotation.SuppressLint
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import java.lang.reflect.Field

/**
 * Titan 2 密码控制器与原生输入连接的精确运行时绑定。
 *
 * 像素层只读取 `EditText` 的长度、焦点和可用状态；密码字符、编辑操作、IME 会话及认证提交
 * 始终由 SystemUI 原对象处理。字段身份或视图层级变化时，调用方必须立即结束接管。
 */
internal class Titan2PasswordControllerBinding private constructor(
    /** 原生密码控制器实例。 */
    val controller: Any,
    /** 原生密码页面根视图。 */
    val passwordView: ViewGroup,
    /** 唯一保留的系统密码输入连接。 */
    val passwordEntry: EditText,
    /** 原生输入法切换按钮。 */
    val imeSwitcher: ImageView,
    /** SystemUI 管理当前输入连接的输入法服务。 */
    private val inputMethodManager: InputMethodManager,
    /** 原生密码控制器使用的 Keyguard 安全回调。 */
    val securityCallback: Any,
    /** 原生密码控制器使用的安全模式枚举对象。 */
    val securityMode: Any,
    /** 用于检测输入连接是否被 SystemUI 替换的字段。 */
    private val passwordEntryField: Field,
    /** 用于检测输入法切换按钮是否被替换的字段。 */
    private val imeSwitcherField: Field,
    /** 用于检测输入法服务是否被替换的字段。 */
    private val inputMethodManagerField: Field,
) {
    /** 判断控制器字段身份、视图后代关系和密码输入类型是否仍符合初始合同。 */
    fun isCurrent(): Boolean =
        passwordEntryField.get(controller) === passwordEntry &&
            imeSwitcherField.get(controller) === imeSwitcher &&
            inputMethodManagerField.get(controller) === inputMethodManager &&
            passwordEntry.isDescendantOf(passwordView) &&
            imeSwitcher.isDescendantOf(passwordView) &&
            isPasswordInputType(passwordEntry.inputType)

    /** 返回经过原生 500 字符边界校验的当前输入长度，不复制 `Editable` 内容。 */
    fun currentInputLength(): Int = validatedNativePasswordLength(passwordEntry.length())

    /** 返回原生输入连接当前是否可继续编辑。 */
    fun isInputEnabled(): Boolean = passwordEntry.isEnabled && passwordEntry.isFocusable

    /** 返回原生输入连接当前是否拥有窗口焦点。 */
    fun hasInputFocus(): Boolean = passwordEntry.hasFocus()

    /** 返回原生输入法切换入口当前是否由 SystemUI 判定为可见。 */
    fun isImeSwitcherVisible(): Boolean = imeSwitcher.visibility == View.VISIBLE

    /** 请求现有原生输入连接获得焦点并让 SystemUI 的输入法服务显示软键盘。 */
    fun requestInput() {
        check(isCurrent()) { "keyguard_password_binding_stale" }
        check(passwordEntry.isAttachedToWindow) { "keyguard_password_entry_detached" }
        check(passwordEntry.isShown) { "keyguard_password_entry_not_visible" }
        check(isInputEnabled()) { "keyguard_password_entry_disabled" }
        check(passwordEntry.hasFocus() || passwordEntry.requestFocus()) {
            "keyguard_password_focus_rejected"
        }
        inputMethodManager.showSoftInput(passwordEntry, InputMethodManager.SHOW_IMPLICIT)
    }

    /** 复用原生按钮点击链打开系统输入法选择器，不复制 ROM 内部选择逻辑。 */
    fun requestImeSwitcher() {
        check(isCurrent()) { "keyguard_password_binding_stale" }
        check(imeSwitcher.isAttachedToWindow) { "keyguard_password_ime_switcher_detached" }
        check(imeSwitcher.visibility == View.VISIBLE) { "keyguard_password_ime_switcher_hidden" }
        check(imeSwitcher.isEnabled) { "keyguard_password_ime_switcher_disabled" }
        check(imeSwitcher.hasOnClickListeners()) {
            "keyguard_password_ime_switcher_listener_missing"
        }
        check(imeSwitcher.performClick()) { "keyguard_password_ime_switcher_click_rejected" }
    }

    internal companion object {
        /** 类、字段、视图层级或输入类型不匹配时拒绝接管当前 ROM。 */
        @SuppressLint("BlockedPrivateApi", "PrivateApi")
        fun bind(controller: Any, classLoader: ClassLoader): Titan2PasswordControllerBinding {
            /** Titan 2 密码控制器类。 */
            val controllerClass = Class.forName(PASSWORD_CONTROLLER_CLASS, false, classLoader)
            check(controllerClass.isInstance(controller)) {
                "keyguard_password_controller_instance"
            }
            /** Titan 2 密码页面类。 */
            val passwordViewClass = Class.forName(PASSWORD_VIEW_CLASS, false, classLoader)
            /** SystemUI 安全回调接口。 */
            val securityCallbackClass = Class.forName(SECURITY_CALLBACK_CLASS, false, classLoader)
            /** SystemUI 安全模式枚举。 */
            val securityModeClass = Class.forName(SECURITY_MODE_CLASS, false, classLoader)
            /** ViewController 继承层级中的原生页面字段。 */
            val viewField = hierarchyField(controllerClass, VIEW_FIELD)
            /** 密码控制器声明的原生输入连接字段。 */
            val passwordEntryField = typedDeclaredField(
                controllerClass,
                PASSWORD_ENTRY_FIELD,
                EditText::class.java,
            )
            /** 密码控制器声明的输入法切换按钮字段。 */
            val imeSwitcherField = typedDeclaredField(
                controllerClass,
                IME_SWITCHER_FIELD,
                ImageView::class.java,
            )
            /** 密码控制器声明的输入法服务字段。 */
            val inputMethodManagerField = typedDeclaredField(
                controllerClass,
                INPUT_METHOD_MANAGER_FIELD,
                InputMethodManager::class.java,
            )
            /** KeyguardInputViewController 继承层级中的安全回调字段。 */
            val callbackField = hierarchyField(controllerClass, SECURITY_CALLBACK_FIELD)
            check(securityCallbackClass.isAssignableFrom(callbackField.type)) {
                "keyguard_password_callback_field"
            }
            /** KeyguardInputViewController 继承层级中的安全模式字段。 */
            val securityModeField = hierarchyField(controllerClass, SECURITY_MODE_FIELD)
            check(securityModeField.type == securityModeClass) {
                "keyguard_password_mode_field"
            }
            /** 当前密码页面对象。 */
            val passwordView = requireNotNull(viewField.get(controller)) {
                "keyguard_password_view"
            }
            check(passwordViewClass.isInstance(passwordView) && passwordView is ViewGroup) {
                "keyguard_password_view_instance"
            }
            /** 当前系统密码输入连接。 */
            val passwordEntry = requireNotNull(passwordEntryField.get(controller)) {
                "keyguard_password_entry"
            } as EditText
            /** 当前系统输入法切换按钮。 */
            val imeSwitcher = requireNotNull(imeSwitcherField.get(controller)) {
                "keyguard_password_ime_switcher"
            } as ImageView
            /** 当前 SystemUI 输入法服务。 */
            val inputMethodManager = requireNotNull(inputMethodManagerField.get(controller)) {
                "keyguard_password_input_method_manager"
            } as InputMethodManager
            check(passwordEntry.isDescendantOf(passwordView)) {
                "keyguard_password_entry_parent"
            }
            check(imeSwitcher.isDescendantOf(passwordView)) {
                "keyguard_password_ime_switcher_parent"
            }
            check(isPasswordInputType(passwordEntry.inputType)) {
                "keyguard_password_input_type"
            }
            /** 当前原生安全回调。 */
            val securityCallback = requireNotNull(callbackField.get(controller)) {
                "keyguard_password_callback"
            }
            check(securityCallbackClass.isInstance(securityCallback)) {
                "keyguard_password_callback_instance"
            }
            /** 当前原生安全模式。 */
            val securityMode = requireNotNull(securityModeField.get(controller)) {
                "keyguard_password_mode"
            }
            check((securityMode as? Enum<*>)?.name == MODE_PASSWORD) {
                "keyguard_password_mode_name"
            }
            return Titan2PasswordControllerBinding(
                controller = controller,
                passwordView = passwordView,
                passwordEntry = passwordEntry,
                imeSwitcher = imeSwitcher,
                inputMethodManager = inputMethodManager,
                securityCallback = securityCallback,
                securityMode = securityMode,
                passwordEntryField = passwordEntryField,
                imeSwitcherField = imeSwitcherField,
                inputMethodManagerField = inputMethodManagerField,
            )
        }

        /** 解析控制器自己声明且类型精确匹配的字段。 */
        private fun typedDeclaredField(owner: Class<*>, name: String, type: Class<*>): Field =
            owner.getDeclaredField(name).apply {
                check(this.type == type) { "keyguard_password_field_type:$name" }
                isAccessible = true
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
            error("keyguard_password_field_missing:$name")
        }

        /** 判断视图是否位于指定密码根视图的后代链中。 */
        private fun View.isDescendantOf(root: View): Boolean {
            /** 当前待检查的视图或父视图。 */
            var current: View? = this
            while (current != null) {
                if (current === root) {
                    return true
                }
                current = current.parent as? View
            }
            return false
        }

        /** 判断输入类型是否仍为 Android 文本密码。 */
        private fun isPasswordInputType(inputType: Int): Boolean =
            inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
                inputType and InputType.TYPE_MASK_VARIATION == InputType.TYPE_TEXT_VARIATION_PASSWORD

        /** Titan 2 密码控制器类名。 */
        private const val PASSWORD_CONTROLLER_CLASS: String =
            "com.android.keyguard.KeyguardPasswordViewController"

        /** Titan 2 密码页面类名。 */
        private const val PASSWORD_VIEW_CLASS: String =
            "com.android.keyguard.KeyguardPasswordView"

        /** SystemUI 安全回调接口类名。 */
        private const val SECURITY_CALLBACK_CLASS: String =
            "com.android.keyguard.KeyguardSecurityCallback"

        /** SystemUI 安全模式枚举类名。 */
        private const val SECURITY_MODE_CLASS: String =
            "com.android.keyguard.KeyguardSecurityModel\$SecurityMode"

        /** 通用 ViewController 原生 View 字段名。 */
        private const val VIEW_FIELD: String = "mView"

        /** 密码控制器原生输入连接字段名。 */
        private const val PASSWORD_ENTRY_FIELD: String = "mPasswordEntry"

        /** 密码控制器输入法切换按钮字段名。 */
        private const val IME_SWITCHER_FIELD: String = "mSwitchImeButton"

        /** 密码控制器输入法服务字段名。 */
        private const val INPUT_METHOD_MANAGER_FIELD: String = "mInputMethodManager"

        /** KeyguardInputViewController 安全回调字段名。 */
        private const val SECURITY_CALLBACK_FIELD: String = "mKeyguardSecurityCallback"

        /** KeyguardInputViewController 安全模式字段名。 */
        private const val SECURITY_MODE_FIELD: String = "mSecurityMode"

        /** Titan 2 密码安全模式枚举名。 */
        private const val MODE_PASSWORD: String = "Password"
    }
}

/** 校验从原生 `Editable.length` 读取的非敏感长度，拒绝异常或越界值。 */
internal fun validatedNativePasswordLength(length: Int): Int {
    check(length in 0..MAXIMUM_NATIVE_PASSWORD_LENGTH) {
        "keyguard_password_length"
    }
    return length
}

/** Titan 2 `keyguard_password_view` 中原生 `EditText` 声明的最大长度。 */
private const val MAXIMUM_NATIVE_PASSWORD_LENGTH: Int = 500

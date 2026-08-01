package com.purride.pixellockscreen.credential

import android.annotation.SuppressLint
import android.view.ViewGroup
import com.purride.pixellockscreen.ui.PinCredentialFeedback
import com.purride.pixellockscreen.ui.PinCredentialUiState
import java.lang.reflect.Field
import java.lang.reflect.Method

/** Titan 2 使用原生数字键盘但不属于设备凭据校验的特殊安全模式。 */
internal enum class Titan2SpecialPinMode(
    /** SystemUI `SecurityMode` 的精确枚举名。 */
    val nativeModeName: String,
    /** 像素页面固定主提示。 */
    val promptText: String,
) {
    /** 第一张 SIM 卡的 PIN、PUK 或网络锁流程。 */
    SIM_1("SimPinPukMe1", "UNLOCK SIM 1"),

    /** 第二张 SIM 卡的 PIN、PUK 或网络锁流程。 */
    SIM_2("SimPinPukMe2", "UNLOCK SIM 2"),

    /** 第三张 SIM 卡的 PIN、PUK 或网络锁流程。 */
    SIM_3("SimPinPukMe3", "UNLOCK SIM 3"),

    /** 第四张 SIM 卡的 PIN、PUK 或网络锁流程。 */
    SIM_4("SimPinPukMe4", "UNLOCK SIM 4"),

    /** MediaTek 防盗服务提供的数字解锁流程。 */
    ANTI_THEFT("AntiTheft", "DEVICE PROTECTION"),
    ;

    /** 当前模式是否由 MediaTek SIM 控制器承载。 */
    val isSim: Boolean
        get() = this != ANTI_THEFT

    internal companion object {
        /** 按原生枚举名解析唯一支持模式。 */
        fun fromNativeName(name: String): Titan2SpecialPinMode? =
            entries.singleOrNull { mode -> mode.nativeModeName == name }
    }
}

/** 特殊数字安全页一次不包含输入内容的可见快照。 */
internal data class Titan2SpecialPinSnapshot(
    /** 当前原生掩码控件报告的输入长度。 */
    val inputLength: Int,
    /** SystemUI 已经决定展示的单行消息。 */
    val messageText: String,
    /** 原生 SIM 服务是否正在执行异步校验。 */
    val checking: Boolean,
)

/**
 * 精确绑定 Titan 2 的 SIM PIN/PUK/ME 与 MediaTek AntiTheft 数字页面。
 *
 * 模块不读取原生 `mText`，只读取掩码文本长度；所有按键继续调用 ROM 已安装的点击链，
 * 因而 SIM/PUK/ME 状态机、防盗服务、失败计数和 dismiss 全部仍由原控制器处理。
 */
internal class Titan2SpecialPinControllerBinding private constructor(
    /** 当前原生特殊安全控制器。 */
    private val controller: Any,
    /** 当前原生特殊安全模式。 */
    val mode: Titan2SpecialPinMode,
    /** 当前原生特殊安全页根视图。 */
    val credentialView: ViewGroup,
    /** 当前原生安全模式对象。 */
    private val nativeSecurityMode: Any,
    /** 控制器继承层级中的安全模式字段。 */
    private val securityModeField: Field,
    /** 原生数字按键对象。 */
    private val digitButtons: List<Any>,
    /** 原生删除按键对象。 */
    private val deleteButton: Any,
    /** 原生确认按键对象。 */
    private val confirmButton: Any,
    /** 原生掩码输入控件。 */
    private val passwordEntry: Any,
    /** 原生可见消息控件。 */
    private val messageArea: Any,
    /** 读取原生掩码文本的方法。 */
    private val transformedTextMethod: Method,
    /** 读取 SystemUI 当前消息的字段。 */
    private val messageField: Field,
    /** SIM 控制器异步校验标志；AntiTheft 不存在该字段。 */
    private val simCheckingField: Field?,
    /** Android View 是否挂载的方法。 */
    private val isAttachedMethod: Method,
    /** Android View 是否启用的方法。 */
    private val isEnabledMethod: Method,
    /** Android View 是否具有点击监听器的方法。 */
    private val hasClickListenerMethod: Method,
    /** Android View 执行原生点击链的方法。 */
    private val performClickMethod: Method,
) {
    /** 检查控制器仍属于绑定模式并读取一帧脱敏可见状态。 */
    fun snapshot(): Titan2SpecialPinSnapshot {
        check(securityModeField.get(controller) === nativeSecurityMode) {
            "special_pin_security_mode_stale"
        }
        /** 原生掩码后的字符序列；只读取长度，不转成字符串。 */
        val transformed = transformedTextMethod.invoke(passwordEntry) as? CharSequence
            ?: error("special_pin_transformed_text")
        return Titan2SpecialPinSnapshot(
            inputLength = transformed.length.coerceIn(0, MAXIMUM_VISIBLE_INPUT_LENGTH),
            messageText = sanitizeSpecialSecurityMessage(messageField.get(messageArea) as? CharSequence),
            checking = simCheckingField?.getBoolean(controller) == true,
        )
    }

    /** 把一个数字交给原生 `NumPadKey` 点击链。 */
    fun enterDigit(digit: Char) {
        require(digit in '0'..'9') { "special_pin_digit" }
        click(digitButtons[digit - '0'], "special_pin_digit_click")
    }

    /** 把删除动作交给原生数字页。 */
    fun delete() {
        click(deleteButton, "special_pin_delete_click")
    }

    /** 把确认动作交给原生控制器已经安装的校验入口。 */
    fun confirm() {
        click(confirmButton, "special_pin_confirm_click")
    }

    /** 调用仍挂载且启用的原生按键点击链。 */
    private fun click(button: Any, errorCode: String) {
        check(isAttachedMethod.invoke(button) as? Boolean == true) { "${errorCode}_detached" }
        check(isEnabledMethod.invoke(button) as? Boolean == true) { "${errorCode}_disabled" }
        check(hasClickListenerMethod.invoke(button) as? Boolean == true) {
            "${errorCode}_listener"
        }
        check(performClickMethod.invoke(button) as? Boolean == true) { errorCode }
    }

    internal companion object {
        /** 按最终控制器、视图、字段和方法签名建立 fail-closed 绑定。 */
        @SuppressLint("BlockedPrivateApi", "PrivateApi")
        fun bind(controller: Any, classLoader: ClassLoader): Titan2SpecialPinControllerBinding {
            /** 控制器继承层级中的原生安全模式字段。 */
            val provisionalModeField = hierarchyField(controller.javaClass, SECURITY_MODE_FIELD)
            /** 当前原生安全模式对象。 */
            val nativeMode = requireNotNull(provisionalModeField.get(controller)) {
                "special_pin_security_mode"
            }
            /** 当前原生安全模式枚举名。 */
            val nativeModeName = (nativeMode as? Enum<*>)?.name
                ?: error("special_pin_security_mode_name")
            /** 当前模块明确支持的特殊模式。 */
            val mode = Titan2SpecialPinMode.fromNativeName(nativeModeName)
                ?: error("special_pin_unsupported_mode:$nativeModeName")
            /** 当前模式要求的最终控制器类。 */
            val controllerClass = Class.forName(
                if (mode.isSim) SIM_CONTROLLER_CLASS else ANTI_THEFT_CONTROLLER_CLASS,
                false,
                classLoader,
            )
            check(controller.javaClass == controllerClass) { "special_pin_controller_type" }
            /** 当前模式要求的最终根视图类。 */
            val viewClass = Class.forName(
                if (mode.isSim) SIM_VIEW_CLASS else ANTI_THEFT_VIEW_CLASS,
                false,
                classLoader,
            )
            /** 通用数字根视图类。 */
            val pinViewClass = Class.forName(PIN_VIEW_CLASS, false, classLoader)
            /** 通用原生掩码输入类。 */
            val passwordEntryClass = Class.forName(PASSWORD_ENTRY_CLASS, false, classLoader)
            /** 通用锁屏消息控件类。 */
            val messageAreaClass = Class.forName(MESSAGE_AREA_CLASS, false, classLoader)
            /** ViewController 继承层级中的根视图字段。 */
            val rootViewField = hierarchyField(controllerClass, VIEW_FIELD)
            /** 当前根视图实例。 */
            val rootView = requireNotNull(rootViewField.get(controller)) { "special_pin_view" }
            check(viewClass.isInstance(rootView) && rootView is ViewGroup) {
                "special_pin_view_type"
            }
            /** 原生数字按键数组。 */
            val rawButtons = hierarchyField(pinViewClass, BUTTONS_FIELD).get(rootView)
                ?: error("special_pin_buttons")
            check(java.lang.reflect.Array.getLength(rawButtons) == DIGIT_COUNT) {
                "special_pin_button_count"
            }
            /** 保持 0 到 9 稳定下标的按键列表。 */
            val digitButtons = List(DIGIT_COUNT) { index ->
                requireNotNull(java.lang.reflect.Array.get(rawButtons, index)) {
                    "special_pin_button:$index"
                }
            }
            /** 原生掩码输入控件。 */
            val passwordEntry = requireNotNull(
                hierarchyField(pinViewClass, PASSWORD_ENTRY_FIELD).get(rootView),
            ) { "special_pin_password_entry" }
            check(passwordEntryClass.isInstance(passwordEntry)) {
                "special_pin_password_entry_type"
            }
            /** 控制器持有的消息控制器。 */
            val messageController = requireNotNull(
                hierarchyField(controllerClass, MESSAGE_CONTROLLER_FIELD).get(controller),
            ) { "special_pin_message_controller" }
            /** 消息控制器在 ViewController 父类中持有的原生消息控件。 */
            val messageArea = requireNotNull(
                hierarchyField(messageController.javaClass, VIEW_FIELD).get(messageController),
            ) { "special_pin_message_area" }
            check(messageAreaClass.isInstance(messageArea)) { "special_pin_message_area_type" }
            /** Android View 的公开点击合同统一从任一数字按钮类解析。 */
            val buttonClass = digitButtons.first().javaClass
            return Titan2SpecialPinControllerBinding(
                controller = controller,
                mode = mode,
                credentialView = rootView,
                nativeSecurityMode = nativeMode,
                securityModeField = provisionalModeField,
                digitButtons = digitButtons,
                deleteButton = requireNotNull(
                    hierarchyField(pinViewClass, DELETE_BUTTON_FIELD).get(rootView),
                ) { "special_pin_delete_button" },
                confirmButton = requireNotNull(
                    hierarchyField(pinViewClass, CONFIRM_BUTTON_FIELD).get(rootView),
                ) { "special_pin_confirm_button" },
                passwordEntry = passwordEntry,
                messageArea = messageArea,
                transformedTextMethod = passwordEntryClass.getDeclaredMethod(
                    TRANSFORMED_TEXT_METHOD,
                ).apply {
                    check(returnType == CharSequence::class.java) {
                        "special_pin_transformed_text_type"
                    }
                },
                messageField = hierarchyField(messageAreaClass, MESSAGE_FIELD).apply {
                    check(type == CharSequence::class.java) { "special_pin_message_type" }
                },
                simCheckingField = if (mode.isSim) {
                    hierarchyField(controllerClass, SIM_CHECKING_FIELD).apply {
                        check(type == Boolean::class.javaPrimitiveType) {
                            "special_pin_checking_type"
                        }
                    }
                } else {
                    null
                },
                isAttachedMethod = exactBooleanMethod(buttonClass, IS_ATTACHED_METHOD),
                isEnabledMethod = exactBooleanMethod(buttonClass, IS_ENABLED_METHOD),
                hasClickListenerMethod = exactBooleanMethod(buttonClass, HAS_CLICK_LISTENER_METHOD),
                performClickMethod = exactBooleanMethod(buttonClass, PERFORM_CLICK_METHOD),
            )
        }

        /** 沿继承链查找精确字段。 */
        private fun hierarchyField(owner: Class<*>, name: String): Field {
            /** 当前待检查类。 */
            var current: Class<*>? = owner
            while (current != null) {
                /** 当前类可能声明的目标字段。 */
                val field = runCatching { current.getDeclaredField(name) }.getOrNull()
                if (field != null) {
                    field.isAccessible = true
                    return field
                }
                current = current.superclass
            }
            error("special_pin_field_missing:$name")
        }

        /** 解析公开无参布尔方法并校验返回类型。 */
        private fun exactBooleanMethod(owner: Class<*>, name: String): Method =
            owner.getMethod(name).apply {
                check(returnType == Boolean::class.javaPrimitiveType) {
                    "special_pin_method_type:$name"
                }
            }

        private const val SIM_CONTROLLER_CLASS: String =
            "com.mediatek.keyguard.Telephony.KeyguardSimPinPukMeViewController"
        private const val SIM_VIEW_CLASS: String =
            "com.mediatek.keyguard.Telephony.KeyguardSimPinPukMeView"
        private const val ANTI_THEFT_CONTROLLER_CLASS: String =
            "com.mediatek.keyguard.AntiTheft.KeyguardAntiTheftLockViewController"
        private const val ANTI_THEFT_VIEW_CLASS: String =
            "com.mediatek.keyguard.AntiTheft.KeyguardAntiTheftLockView"
        private const val PIN_VIEW_CLASS: String = "com.android.keyguard.KeyguardPinBasedInputView"
        private const val PASSWORD_ENTRY_CLASS: String = "com.android.keyguard.PasswordTextView"
        private const val MESSAGE_AREA_CLASS: String = "com.android.keyguard.KeyguardMessageArea"
        private const val VIEW_FIELD: String = "mView"
        private const val SECURITY_MODE_FIELD: String = "mSecurityMode"
        private const val BUTTONS_FIELD: String = "mButtons"
        private const val DELETE_BUTTON_FIELD: String = "mDeleteButton"
        private const val CONFIRM_BUTTON_FIELD: String = "mOkButton"
        private const val PASSWORD_ENTRY_FIELD: String = "mPasswordEntry"
        private const val MESSAGE_CONTROLLER_FIELD: String = "mMessageAreaController"
        private const val MESSAGE_FIELD: String = "mMessage"
        private const val SIM_CHECKING_FIELD: String = "mSimCheckInProgress"
        private const val TRANSFORMED_TEXT_METHOD: String = "getTransformedText"
        private const val IS_ATTACHED_METHOD: String = "isAttachedToWindow"
        private const val IS_ENABLED_METHOD: String = "isEnabled"
        private const val HAS_CLICK_LISTENER_METHOD: String = "hasOnClickListeners"
        private const val PERFORM_CLICK_METHOD: String = "performClick"
        private const val DIGIT_COUNT: Int = 10
        private const val MAXIMUM_VISIBLE_INPUT_LENGTH: Int = 64
    }
}

/** 清理原生特殊安全页的可见消息并过滤 MediaTek 内部占位符。 */
internal fun sanitizeSpecialSecurityMessage(message: CharSequence?): String {
    /** 折叠换行和连续空白后的系统文字。 */
    val sanitized = message
        ?.toString()
        ?.replace('\n', ' ')
        ?.replace('\r', ' ')
        ?.trim()
        ?.replace(SPECIAL_SECURITY_WHITESPACE, " ")
        ?.take(MAXIMUM_SPECIAL_SECURITY_MESSAGE_LENGTH)
        .orEmpty()
    return sanitized.takeUnless { value -> value == ANTI_THEFT_HIDDEN_MESSAGE } ?: ""
}

/** 把特殊页快照转换为复用数字键盘宿主的非敏感状态。 */
internal fun specialPinUiState(
    mode: Titan2SpecialPinMode,
    snapshot: Titan2SpecialPinSnapshot,
): PinCredentialUiState = PinCredentialUiState(
    promptText = mode.promptText,
    inputLength = snapshot.inputLength,
    feedbackText = snapshot.messageText.ifBlank {
        if (snapshot.checking) "CHECKING" else ""
    },
    feedback = if (snapshot.checking) {
        PinCredentialFeedback.CHECKING
    } else {
        PinCredentialFeedback.READY
    },
)

private val SPECIAL_SECURITY_WHITESPACE: Regex = Regex("\\s+")
private const val MAXIMUM_SPECIAL_SECURITY_MESSAGE_LENGTH: Int = 160
private const val ANTI_THEFT_HIDDEN_MESSAGE: String = "AntiTheft Noneed Print Text"

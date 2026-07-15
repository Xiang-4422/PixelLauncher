package com.purride.pixelui

import com.purride.pixelui.internal.host.PixelHostBridgeScope

/**
 * 像素 UI runtime 与 Android 宿主之间的桥接协议。
 *
 * 引擎只负责内容区 UI，本接口把输入法、系统动作、震动和帧调度等
 * 宿主能力从 runtime 中隔离出来，避免通用模块直接依赖 Activity 细节。
 */
public interface PixelHostBridge {
    /** 执行 `PixelHostBridge` 的 `showTextInput` 公开行为；具体参数、返回和副作用见下文。
 *
 * Starts a platform text-input session described by the frozen request subset.
 */
    public fun showTextInput(request: PixelTextInputRequest)

    /**
     * 同步已激活输入会话的文本和选区，不重新获取焦点或重启 IME。
     *
     * 不支持主动同步的宿主可以保留默认空实现；Android 默认桥接会完整实现。
     */
    public fun updateTextInput(request: PixelTextInputRequest): Unit = Unit

    /** 执行 `PixelHostBridge` 的 `hideTextInput` 公开行为；具体参数、返回和副作用见下文。
 *
 * Hides and retires the current platform text-input session.
 */
    public fun hideTextInput()

    /** 执行 `PixelHostBridge` 的 `performHapticFeedback` 公开行为；具体参数、返回和副作用见下文。
 *
 * Requests one host-native haptic feedback effect.
 */
    public fun performHapticFeedback(type: PixelHapticType)

    /** 执行 `PixelHostBridge` 的 `requestFrame` 公开行为；具体参数、返回和副作用见下文。
 *
 * Schedules a future engine frame without requiring an Activity-specific dependency.
 */
    public fun requestFrame()

    /** 执行 `PixelHostBridge` 的 `dispatchSystemAction` 公开行为；具体参数、返回和副作用见下文。
 *
 * Dispatches a typed-at-the-boundary host system request.
 */
    public fun dispatchSystemAction(action: PixelSystemAction)

    /**
     * 返回宿主剪贴板中的纯文本；没有文本或宿主不支持剪贴板时返回 `null`。
     */
    public fun readClipboardText(): String? = null

    /**
     * 把 [text] 写入宿主剪贴板；不支持剪贴板的宿主可以保留默认空实现。
     */
    public fun writeClipboardText(text: String): Unit = Unit
}

/**
 * 完整描述一次平台文本编辑会话的不可变值。
 *
 * [PixelTextInputRequest] 的八字段主构造器已经属于兼容边界，因此 composition 不能直接追加到
 * 旧请求。支持完整编辑状态的宿主通过本值对象接收 composition，同时继续让 selection 和
 * composition 使用 Android 兼容的 UTF-16 offset。调用方可以传入尚未规范化的 offset；默认
 * Android bridge 会按 extended grapheme boundary 进行最终规范化。
 *
 * @property text 未经 Unicode normalization 改写的原始 UTF-16 文本。
 * @property selectionStart selection 的起始 UTF-16 offset。
 * @property selectionEnd selection 的结束 UTF-16 offset。
 * @property compositionStart composition 的起始 UTF-16 offset，`-1` 表示不存在。
 * @property compositionEnd composition 的结束 UTF-16 offset，`-1` 表示不存在。
 */
public data class PixelTextEditingValue(
    public val text: String,
    public val selectionStart: Int = text.length,
    public val selectionEnd: Int = selectionStart,
    public val compositionStart: Int = -1,
    public val compositionEnd: Int = -1,
)

/**
 * 可选的完整文本编辑宿主能力。
 *
 * Coordinator 只在 bridge 实现本接口时发送 composition；旧 [PixelHostBridge] 实现仍只接收
 * 冻结的 [PixelTextInputRequest]，因此不会因新增抽象方法产生运行时兼容问题。
 */
public interface PixelTextEditingHostBridge : PixelHostBridge {
    /** 显示输入法并以 [value] 初始化完整的 text/selection/composition 状态。 */
    public fun showTextEditing(
        request: PixelTextInputRequest,
        value: PixelTextEditingValue,
    )

    /** 在不重新获取焦点的前提下同步活动输入会话的完整编辑状态。 */
    public fun updateTextEditing(
        request: PixelTextInputRequest,
        value: PixelTextEditingValue,
    )
}

/**
 * widget 树内的宿主震动反馈入口。
 *
 * 返回 `false` 表示当前树没有宿主桥接或宿主不支持该反馈；调用方不需要兜底。
 */
public object PixelHapticFeedback {
    /**
     * 通过当前宿主执行一次震动反馈。
     */
    public fun perform(context: BuildContext, type: PixelHapticType): Boolean {
        /** Nearest Host capability; absence is the documented unsupported result. */
        val bridge = context.dependOnInheritedWidgetOfExactType<PixelHostBridgeScope>()?.bridge ?: return false
        bridge.performHapticFeedback(type)
        return true
    }
}

/**
 * 对当前聚焦 TextField 执行的标准文本编辑动作。
 */
public enum class PixelTextEditAction {
    COPY,
    CUT,
    PASTE,
    SELECT_ALL,
}

/**
 * 文本输入桥接请求。
 *
 * 第一版仍然走宿主隐藏输入框或等价桥接方案，不在 runtime 内重做 IME。
 *
 * @property text 未经 Unicode normalization 改写的原始文本。
 * @property selectionStart selection 起点的 Android UTF-16 offset。
 * @property selectionEnd selection 终点的 Android UTF-16 offset。
 * @property readOnly 是否禁止平台输入产生文本修改。
 * @property minLines 请求的最小可见行数；Android bridge 会至少规范化为一行。
 * @property maxLines 请求的最大可见行数；小于 [minLines] 时会被向上规范化。
 * @property inputType 决定 Android inputType、过滤器和键盘提示的输入语义。
 * @property action 软键盘 action button 对应的提交或遍历语义。
 */
public data class PixelTextInputRequest(
    val text: String,
    val selectionStart: Int = text.length,
    val selectionEnd: Int = selectionStart,
    val readOnly: Boolean = false,
    val minLines: Int = 1,
    val maxLines: Int = 1,
    val inputType: PixelInputType = PixelInputType.TEXT,
    val action: PixelTextInputAction = PixelTextInputAction.DONE,
)

/**
 * 文本输入种类。
 *
 * 宿主侧的 PixelTextInputBridge 会把这个值映射到 Android EditorInfo 的
 * InputType / variation 常量，让系统软键盘在不同语义下弹出对应面板。
 *
 * - [TEXT]: 普通文本（默认），允许多行受 minLines/maxLines 限制
 * - [ASCII]: 单行 ASCII 文本，向宿主请求英文大写键盘
 * - [NUMBER]: 数字面板（含正负号 / 小数点）
 * - [NUMBER_PASSWORD]: 数字密码（不显示原文）
 * - [EMAIL]: 邮件地址，含 @ 键
 * - [PHONE]: 电话号码
 * - [URL]: URL，含 / 键
 * - [PASSWORD]: 普通密码（不显示原文）
 */
public enum class PixelInputType {
    TEXT,
    ASCII,
    NUMBER,
    NUMBER_PASSWORD,
    EMAIL,
    PHONE,
    URL,
    PASSWORD,
}

/**
 * 文本输入动作。
 *
 * 第一版先只覆盖最常用的几种 IME 动作，页面层通过它表达“下一项”还是“提交完成”。
 */
public enum class PixelTextInputAction {
    DONE,
    NEXT,
    GO,
    SEARCH,
    SEND,
}

/**
 * 宿主系统动作。
 *
 * 这里只定义稳定的抽象入口，后续再按真实需求细化为打开 Intent、
 * 页面返回、权限引导等具体动作。
 *
 * @property type 宿主与消费者约定的稳定动作类型标识。
 * @property payload 可选的动作数据；其格式由 [type] 对应协议定义。
 */
public data class PixelSystemAction(
    val type: String,
    val payload: String? = null,
)

/**
 * 震动反馈类型。
 */
public enum class PixelHapticType {
    /**
     * 短点击反馈。
     */
    TAP,

    /**
     * 长按反馈。
     */
    LONG_PRESS,
}

package com.purride.pixelui

/**
 * 像素 UI runtime 与 Android 宿主之间的桥接协议。
 *
 * 引擎只负责内容区 UI，本接口把输入法、系统动作、震动和帧调度等
 * 宿主能力从 runtime 中隔离出来，避免通用模块直接依赖 Activity 细节。
 */
public interface PixelHostBridge {
    public fun showTextInput(request: PixelTextInputRequest)

    /**
     * 同步已激活输入会话的文本和选区，不重新获取焦点或重启 IME。
     *
     * 不支持主动同步的宿主可以保留默认空实现；Android 默认桥接会完整实现。
     */
    public fun updateTextInput(request: PixelTextInputRequest): Unit = Unit

    public fun hideTextInput()

    public fun performHapticFeedback(type: PixelHapticType)

    public fun requestFrame()

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
 */
public data class PixelSystemAction(
    val type: String,
    val payload: String? = null,
)

/**
 * 震动反馈类型。
 */
public enum class PixelHapticType {
    TAP,
    LONG_PRESS,
}

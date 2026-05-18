package com.purride.pixelui

/**
 * 像素 UI runtime 与 Android 宿主之间的桥接协议。
 *
 * 引擎只负责内容区 UI，本接口把输入法、系统动作、震动和帧调度等
 * 宿主能力从 runtime 中隔离出来，避免通用模块直接依赖 Activity 细节。
 */
interface PixelHostBridge {
    fun showTextInput(request: PixelTextInputRequest)

    fun hideTextInput()

    fun performHapticFeedback(type: PixelHapticType)

    fun requestFrame()

    fun dispatchSystemAction(action: PixelSystemAction)
}

/**
 * 文本输入桥接请求。
 *
 * 第一版仍然走宿主隐藏输入框或等价桥接方案，不在 runtime 内重做 IME。
 */
data class PixelTextInputRequest(
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
 * - [NUMBER]: 数字面板（含正负号 / 小数点）
 * - [NUMBER_PASSWORD]: 数字密码（不显示原文）
 * - [EMAIL]: 邮件地址，含 @ 键
 * - [PHONE]: 电话号码
 * - [URL]: URL，含 / 键
 * - [PASSWORD]: 普通密码（不显示原文）
 */
enum class PixelInputType {
    TEXT,
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
enum class PixelTextInputAction {
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
data class PixelSystemAction(
    val type: String,
    val payload: String? = null,
)

/**
 * 震动反馈类型。
 */
enum class PixelHapticType {
    TAP,
    LONG_PRESS,
}

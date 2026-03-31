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
)

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

package com.purride.pixelui

/**
 * 完整描述一次平台文本编辑会话的不可变值。
 *
 * selection 与 composition 都使用 Android 兼容的 UTF-16 offset。调用方可以传入尚未规范化的
 * offset；Android 编辑器会按 extended grapheme boundary 完成最终规范化。
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
 * runtime 与平台输入法之间唯一的编辑会话契约。
 *
 * 一次会话同时携带编辑器配置、完整编辑状态和会话身份：[id] 用来区分逻辑上不同的
 * TextField，宿主据此在字段之间切换时作废过期的 InputConnection，避免上一个字段的
 * 输入被写进下一个字段。
 *
 * @property id 会话身份；同一 TextField 的连续更新必须保持引用相等。
 * @property request 编辑器配置（行数、输入语义、IME action、只读）。
 * @property value 完整的 text / selection / composition 状态。
 */
public data class PixelTextEditingSession(
    public val id: Any,
    public val request: PixelTextInputRequest,
    public val value: PixelTextEditingValue,
)

/**
 * widget 树内的宿主震动反馈入口。
 *
 * 返回 `false` 表示当前树没有宿主震动 capability；调用方不需要兜底。
 */
public object PixelHapticFeedback {
    /**
     * 通过当前宿主 capability 执行一次震动反馈。
     */
    public fun perform(context: BuildContext, type: PixelHapticType): Boolean {
        /** 最近的 Host capability 集合；缺失震动能力即为文档化的不支持结果。 */
        return PixelHostServices.of(context).performHapticFeedback(type) is PixelCapabilityResult.Handled
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
 * 文本输入的编辑器配置请求。
 *
 * runtime 不自绘 IME；这层只描述宿主编辑器需要呈现的输入语义。
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

package com.purride.pixelui

/** 枚举 `PixelTextOverflow` 支持的 `PixelTextOverflow` 策略，序号不作为持久化协议。 */
public enum class PixelTextOverflow {
    CLIP,

    /** 保留开头，省略号加在末尾。适合标题、姓名等"前缀足以辨认"的文本。 */
    ELLIPSIS,

    /**
     * 保留**末尾**，省略号加在开头。适合末位才是核对依据的文本——正在输入的电话号码、
     * 文件路径、长 ID：从尾部截断等于把用户刚输入的内容藏起来。
     *
     * 只作用于单行（`softWrap = false`）。多行文本按行截断时仍丢弃末尾行并在最后一行
     * 尾部加省略号，与 [ELLIPSIS] 一致——"保留末尾"在多行语境下没有公认语义，
     * 强行定义反而会让调用方难以预期。
     */
    ELLIPSIS_START,
    ;

    /** 是否需要生成省略号；[CLIP] 之外都需要。 */
    internal val ellipsizes: Boolean
        get() = this != CLIP

    /** 省略号是否加在逻辑开头（即保留末尾）。 */
    internal val ellipsizesFromStart: Boolean
        get() = this == ELLIPSIS_START
}

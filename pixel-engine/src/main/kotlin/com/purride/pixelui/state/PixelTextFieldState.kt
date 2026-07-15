package com.purride.pixelui.state

import android.os.Bundle
import com.purride.pixelui.PixelGraphemeBoundaryMap
import com.purride.pixelui.internal.PixelArtifactInternalApi

/**
 * 通用文本输入状态。
 *
 * 公开 offset 延续 Android 的 UTF-16 坐标系，但稳定的 selection 端点始终由
 * [PixelGraphemeBoundaryMap] 规范化：折叠光标吸附到最近边界（等距时向较大 offset），
 * 非空正序范围向外扩展，倒序范围保持历史行为并折叠在 start 附近。本类不会规范化或改写
 * [text] 的 Unicode 内容。
 *
 * @param initialText 初始文本，按原始 UTF-16 内容保留，不执行 NFC 等规范化。
 * @param selectionStart 初始 selection 起点；越界值会 clamp 后按字素边界规范化。
 * @param selectionEnd 初始 selection 终点；默认创建折叠在 [selectionStart] 的光标。
 */
public class PixelTextFieldState(
    initialText: String = "",
    selectionStart: Int = initialText.length,
    selectionEnd: Int = selectionStart,
) {
    /** 当前原始文本；只允许同模块控制器在保持编辑不变量时写入。 */
    public var text: String = initialText
        internal set

    /** 当前 selection 的 UTF-16 起点，始终位于扩展字素边界。 */
    public var selectionStart: Int
        internal set

    /** 当前 selection 的 UTF-16 终点，始终不小于 [selectionStart] 且位于字素边界。 */
    public var selectionEnd: Int
        internal set

    /** 使用同一张边界图一次性初始化两个端点，且不在 retained state 中保留临时 map/range。 */
    init {
        /** 构造期间一次性计算的稳定初始 selection。 */
        val initialSelection = PixelGraphemeBoundaryMap(initialText).expand(selectionStart, selectionEnd)
        this.selectionStart = initialSelection.start
        this.selectionEnd = initialSelection.end
    }

    /** 当前文本输入目标是否已实际获得焦点。 */
    public var isFocused: Boolean = false
        internal set

    /**
     * IME composition 区段。-1 表示当前没有 composing 文本（IME 已提交所有按键）。
     *
     * 范围对应 [text] 的 UTF-16 offset；end 是 exclusive，非空范围始终向外扩展到完整
     * 扩展字素簇。
     * 由宿主侧（PixelTextInputBridge / 测试）通过 [PixelTextFieldController.updateComposition]
     * 写入；RenderSurface 在聚焦且区段非空时绘制下划线。
     */
    public var compositionStart: Int = -1
        internal set

    /** IME composition 的 exclusive UTF-16 终点；-1 表示当前不存在 composition。 */
    public var compositionEnd: Int = -1
        internal set

    /** 下一帧是否需要向宿主申请文本焦点。 */
    @PixelArtifactInternalApi
    public var focusRequested: Boolean = false

    /** 下一帧是否需要向宿主释放文本焦点。 */
    @PixelArtifactInternalApi
    public var blurRequested: Boolean = false

    /** 当前 retained state 是否已消费一次 autofocus 请求。 */
    @PixelArtifactInternalApi
    public var autofocusConsumed: Boolean = false

    /** 当前主题/宿主配置是否允许光标闪烁。 */
    internal var cursorBlinkEnabled: Boolean = true

    /** 一个完整光标闪烁周期的毫秒数。 */
    internal var cursorBlinkPeriodMs: Long = 1_000L

    /** 距离上次光标可见态翻转累计的毫秒数。 */
    internal var cursorBlinkElapsedMs: Long = 0L

    /** 当前帧是否应绘制输入光标。 */
    @PixelArtifactInternalApi
    public var cursorVisible: Boolean = true
}

/**
 * TextField 的 ABI 稳定可持久化快照。
 *
 * 主构造器继续保留历史三个字段，允许读取旧快照。写入 Bundle 或交给
 * [PixelTextFieldController.restoreState] 时，selection 会按 [text] 的字素边界重新规范化；
 * composition 是瞬态 IME 状态，不跨 Activity 重建保存。
 *
 * @property text 原样保存的文本，不执行 Unicode 规范化。
 * @property selectionStart selection 的 UTF-16 起点。
 * @property selectionEnd selection 的 UTF-16 exclusive 终点。
 */
public data class PixelTextFieldSavedState(
    public val text: String,
    public val selectionStart: Int = text.length,
    public val selectionEnd: Int = selectionStart,
)

/**
 * [PixelTextFieldSavedState.saveToBundle] 与 [getPixelTextFieldSavedState] 使用的稳定默认 key。
 */
public const val PixelTextFieldSavedStateBundleKey: String = "com.purride.pixelui.textField.savedState"

/**
 * 把 TextField 文本和 selection 写入 Android [Bundle]。
 *
 * 写入前会按快照文本重新规范化 offset，因此旧版本或手工构造的内部字素 offset 不会
 * 被继续持久化；文本本身始终按原样保存。
 *
 * @param outState 接收嵌套 TextField payload 的父 Bundle。
 * @param key 父 Bundle 中使用的非空键。
 */
public fun PixelTextFieldSavedState.saveToBundle(
    outState: Bundle,
    key: String = PixelTextFieldSavedStateBundleKey,
) {
    require(key.isNotBlank()) { "PixelTextFieldSavedState Bundle key must not be blank" }
    /** 按快照自身文本规范化的 selection，防止手工构造的无效 offset 进入 Bundle。 */
    val normalizedSelection = PixelGraphemeBoundaryMap(text).expand(selectionStart, selectionEnd)
    /** 隔离在父 Bundle 下的版本稳定 TextField payload。 */
    val bundle = Bundle()
    bundle.putString(PixelTextFieldSavedStateKeys.Text, text)
    bundle.putInt(PixelTextFieldSavedStateKeys.SelectionStart, normalizedSelection.start)
    bundle.putInt(PixelTextFieldSavedStateKeys.SelectionEnd, normalizedSelection.end)
    outState.putBundle(key, bundle)
}

/**
 * 从 Android [Bundle] 读取之前保存的 TextField 文本和 selection。
 *
 * 读取后使用当前固定 Unicode 数据重新规范化 selection；缺失 payload 或文本时返回
 * `null`，composition 不属于稳定快照。
 *
 * @param key 父 Bundle 中使用的非空键。
 * @return 保留原文且 selection 稳定的快照，或在没有有效 payload 时返回 `null`。
 */
public fun Bundle.getPixelTextFieldSavedState(
    key: String = PixelTextFieldSavedStateBundleKey,
): PixelTextFieldSavedState? {
    require(key.isNotBlank()) { "PixelTextFieldSavedState Bundle key must not be blank" }
    /** 旧版本写入的嵌套 payload；缺失表示没有可恢复状态。 */
    val bundle = getBundle(key) ?: return null
    /** 原样恢复的文本；缺失文本的 payload 视为损坏而忽略。 */
    val text = bundle.getString(PixelTextFieldSavedStateKeys.Text) ?: return null
    /** 旧 payload 中可能越界或落在字素内部的原始 selection 起点。 */
    val savedSelectionStart =
        bundle.getInt(PixelTextFieldSavedStateKeys.SelectionStart, text.length)
    /** 旧 payload 中可能越界或落在字素内部的原始 selection 终点。 */
    val savedSelectionEnd =
        bundle.getInt(PixelTextFieldSavedStateKeys.SelectionEnd, text.length)
    /** 恢复时使用当前固定 Unicode 数据重新建立的稳定 selection。 */
    val normalizedSelection =
        PixelGraphemeBoundaryMap(text).expand(savedSelectionStart, savedSelectionEnd)
    return PixelTextFieldSavedState(
        text = text,
        selectionStart = normalizedSelection.start,
        selectionEnd = normalizedSelection.end,
    )
}

/** Bundle 内部字段名；这些键保持私有以便后续兼容读取旧 payload。 */
private object PixelTextFieldSavedStateKeys {
    /** 原始文本字段。 */
    const val Text = "text"

    /** UTF-16 selection 起点字段。 */
    const val SelectionStart = "selectionStart"

    /** UTF-16 selection 终点字段。 */
    const val SelectionEnd = "selectionEnd"
}

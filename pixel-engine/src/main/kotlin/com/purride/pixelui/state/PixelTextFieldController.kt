package com.purride.pixelui.state

import com.purride.pixelui.ChangeNotifier
import com.purride.pixelui.PixelGraphemeBoundaryMap
import com.purride.pixelui.PixelUtf16Range
import com.purride.pixelui.internal.PixelArtifactInternalApi
import com.purride.pixelui.internal.text.UnicodeGraphemeBreakProperty
import com.purride.pixelui.internal.text.UnicodeGraphemeData

/**
 * 通用文本输入控制器。
 *
 * 所有文本编辑都集中在这里，避免宿主和页面层直接改字段：
 * - 创建状态
 * - 更新文本、selection 与 IME composition
 * - 按扩展字素簇移动、删除、剪切、粘贴和选词
 * - 聚焦与失焦
 *
 * selection/composition 对外继续采用 Android 兼容的 UTF-16 offset，但稳定端点必须落在
 * [PixelGraphemeBoundaryMap] 的扩展字素边界。控制器不会对文本执行 NFC 或其他 Unicode
 * 规范化；每次文本替换后会针对新文本重建边界图，再规范化新光标。
 *
 * 监听变化：本类继承 [ChangeNotifier]，可直接
 * `controller.addListener { /* on changed */ }` 注册回调，或用
 * `controller.observe { ... }` 扩展拿到句柄方便后续 removeListener。
 */
public class PixelTextFieldController : ChangeNotifier() {

    /**
     * 创建文本输入状态，并按固定 Unicode 字素图规范化初始 selection。
     *
     * @param initialText 按原样保留且不执行 NFC 的初始文本。
     * @param selectionStart 初始 selection 的 UTF-16 起点。
     * @param selectionEnd 初始 selection 的 UTF-16 exclusive 终点。
     * @return 满足稳定字素边界不变量的新状态。
     */
    public fun create(
        initialText: String = "",
        selectionStart: Int = initialText.length,
        selectionEnd: Int = selectionStart,
    ): PixelTextFieldState {
        return PixelTextFieldState(
            initialText = initialText,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
        )
    }

    /**
     * 创建可写入 Android Bundle 的文本和 selection 快照。
     *
     * 即使调用方通过同模块内部路径留下了旧式非边界 offset，快照也会再次规范化；IME
     * composition 仍是瞬态状态，不进入稳定三字段 ABI。
     *
     * @param state 要保存的文本输入状态。
     * @return 保留原文且 selection 端点稳定的快照。
     */
    public fun saveState(state: PixelTextFieldState): PixelTextFieldSavedState {
        /** 与当前原文严格绑定的字素边界图。 */
        val boundaryMap = PixelGraphemeBoundaryMap(state.text)
        /** 向外扩展或按 downstream affinity 折叠后的稳定 selection。 */
        val selection = boundaryMap.expand(state.selectionStart, state.selectionEnd)
        return PixelTextFieldSavedState(
            text = state.text,
            selectionStart = selection.start,
            selectionEnd = selection.end,
        )
    }

    /**
     * 恢复保存过的文本和 selection；IME composition 不跨 Activity 重建保存。
     *
     * @param state 接收快照的 retained 状态。
     * @param savedState 可能来自旧版本或手工构造、因此需要重新规范化的快照。
     */
    public fun restoreState(
        state: PixelTextFieldState,
        savedState: PixelTextFieldSavedState,
    ) {
        updateText(
            state = state,
            text = savedState.text,
            selectionStart = savedState.selectionStart,
            selectionEnd = savedState.selectionEnd,
        )
    }

    /**
     * 更新原始文本、selection 和可选 IME composition 范围。
     *
     * 文本不会被 Unicode 规范化。selection 的折叠点吸附到最近边界，等距时选择更大的
     * UTF-16 offset；非空正序范围向外扩展；倒序范围折叠到规范化后的 start。有效非空
     * composition 同样向外扩展，无效、倒序或空范围会被清除。
     *
     * @param state 接收更新的文本输入状态。
     * @param text 新原始文本。
     * @param selectionStart 新 selection 的 UTF-16 起点。
     * @param selectionEnd 新 selection 的 UTF-16 exclusive 终点。
     * @param compositionStart 新 composition 起点，负值表示清除。
     * @param compositionEnd 新 composition exclusive 终点；不大于起点时清除。
     */
    public fun updateText(
        state: PixelTextFieldState,
        text: String,
        selectionStart: Int = text.length,
        selectionEnd: Int = selectionStart,
        compositionStart: Int = -1,
        compositionEnd: Int = -1,
    ) {
        /** 新文本专属的边界图，不能沿用更新前文本的 offset 分段。 */
        val boundaryMap = PixelGraphemeBoundaryMap(text)
        /** 按统一 selection 规则得到的稳定端点。 */
        val selection = boundaryMap.expand(selectionStart, selectionEnd)
        state.text = text
        state.selectionStart = selection.start
        state.selectionEnd = selection.end
        applyComposition(state, boundaryMap, compositionStart, compositionEnd)
        notifyListeners()
    }

    /**
     * 更新 IME composition 区段。
     *
     * `start < 0`、`end <= start`，或 clamp 后为空都视作“无 composing”。其余范围会 clamp
     * 到当前文本并向外扩展到完整字素簇，永远不会停在有效 surrogate pair、组合序列或
     * emoji ZWJ 序列内部。
     *
     * @param state 接收 composition 的状态。
     * @param compositionStart UTF-16 起点，负值表示清除。
     * @param compositionEnd UTF-16 exclusive 终点。
     */
    public fun updateComposition(
        state: PixelTextFieldState,
        compositionStart: Int,
        compositionEnd: Int,
    ) {
        /** 当前文本对应的固定 Unicode 字素边界图。 */
        val boundaryMap = PixelGraphemeBoundaryMap(state.text)
        applyComposition(state, boundaryMap, compositionStart, compositionEnd)
        notifyListeners()
    }

    /** 把一个 composition 请求应用为稳定非空范围，或用 -1/-1 表示清除。 */
    private fun applyComposition(
        state: PixelTextFieldState,
        boundaryMap: PixelGraphemeBoundaryMap,
        compositionStart: Int,
        compositionEnd: Int,
    ) {
        if (compositionStart < 0 || compositionEnd <= compositionStart) {
            state.compositionStart = -1
            state.compositionEnd = -1
            return
        }
        /** 当前文本的 UTF-16 长度，也是 composition offset 的 clamp 上界。 */
        val length = boundaryMap.utf16Length
        /** clamp 后仍需保持正序才是有效 composition。 */
        val clampedStart = compositionStart.coerceIn(0, length)
        /** 独立 clamp 终点，以便明确识别落到空范围的越界输入。 */
        val clampedEnd = compositionEnd.coerceIn(0, length)
        if (clampedStart >= clampedEnd) {
            state.compositionStart = -1
            state.compositionEnd = -1
            return
        }
        /** composition 只能向外扩展，不能丢弃调用方命中的字素内容。 */
        val composition = boundaryMap.expand(clampedStart, clampedEnd)
        if (composition.isCollapsed) {
            state.compositionStart = -1
            state.compositionEnd = -1
            return
        }
        state.compositionStart = composition.start
        state.compositionEnd = composition.end
    }

    /** 同步主题光标闪烁配置，并在配置实际变化时重新开始闪烁周期。 */
    /** 供 Host 与 render 层同步光标闪烁主题配置的内部 SPI。 */
    @PixelArtifactInternalApi
    public fun syncCursorBlinkConfig(
        state: PixelTextFieldState,
        enabled: Boolean,
        periodMs: Long,
    ) {
        /** 防止零或负周期令宿主产生立即重调度循环。 */
        val safePeriodMs = periodMs.coerceAtLeast(1L)
        if (state.cursorBlinkEnabled == enabled && state.cursorBlinkPeriodMs == safePeriodMs) return
        state.cursorBlinkEnabled = enabled
        state.cursorBlinkPeriodMs = safePeriodMs
        resetCursorBlink(state)
    }

    /** 把光标恢复为可见并清除当前半周期累计时间。 */
    internal fun resetCursorBlink(state: PixelTextFieldState) {
        state.cursorBlinkElapsedMs = 0L
        state.cursorVisible = true
    }

    /** 推进光标闪烁时钟，并返回可见态是否发生变化。 */
    /** 供帧循环推进光标闪烁状态的内部 SPI。 */
    @PixelArtifactInternalApi
    public fun stepCursorBlink(state: PixelTextFieldState, deltaMs: Long): Boolean {
        return stepCursorBlinkInternal(
            state = state,
            deltaMs = deltaMs,
            notifyOnVisibilityChange = true,
        )
    }

    /**
     * 供 Android Host 静默推进光标时钟。
     *
     * Host 会直接把可见态写入当前 retained RenderSurface 并只标记 paint，因此这里不能再
     * 通知 controller 监听者，否则同一次闪烁仍会把整棵文本框 widget 子树标记为需要 build。
     */
    @PixelArtifactInternalApi
    public fun stepCursorBlinkForHost(state: PixelTextFieldState, deltaMs: Long): Boolean {
        return stepCursorBlinkInternal(
            state = state,
            deltaMs = deltaMs,
            notifyOnVisibilityChange = false,
        )
    }

    /** 推进共享闪烁状态，并按调用路径决定是否广播可见态变化。 */
    private fun stepCursorBlinkInternal(
        state: PixelTextFieldState,
        deltaMs: Long,
        notifyOnVisibilityChange: Boolean,
    ): Boolean {
        if (!state.isFocused || !state.cursorBlinkEnabled || deltaMs <= 0L) {
            /** 非活动状态恢复可见时需要请求一次重绘。 */
            val wasHidden = !state.cursorVisible
            state.cursorVisible = true
            state.cursorBlinkElapsedMs = 0L
            return wasHidden
        }
        state.cursorBlinkElapsedMs += deltaMs
        /** 光标每半个配置周期翻转一次可见态。 */
        val halfPeriodMs = (state.cursorBlinkPeriodMs / 2L).coerceAtLeast(1L)
        if (state.cursorBlinkElapsedMs < halfPeriodMs) return false
        /** 大步进期间跨过的完整半周期数量。 */
        val toggles = state.cursorBlinkElapsedMs / halfPeriodMs
        state.cursorBlinkElapsedMs %= halfPeriodMs
        if (toggles % 2L == 0L) return false
        state.cursorVisible = !state.cursorVisible
        if (notifyOnVisibilityChange) notifyListeners()
        return true
    }

    /**
     * 距离下一次光标可见态翻转还有多少毫秒。
     *
     * 宿主用它把"聚焦期间每帧全量重绘"换成"只在下一个闪烁边界安排一次延迟
     * 重绘"（见 [com.purride.pixelui.PixelHostView] 的 onDraw）：光标可见态每
     * [PixelTextFieldState.cursorBlinkPeriodMs] 的一半才翻转一次，没必要逐帧
     * postInvalidate。
     *
     * 未聚焦 / 关闭闪烁时返回 0L，调用方据此停止调度，循环自然停下；否则返回
     * `(半周期 - 已累计 elapsed)` 的正数，最小 1L。
     */
    /** 供 Host 计算下一次光标重绘边界的内部 SPI。 */
    @PixelArtifactInternalApi
    public fun millisUntilNextCursorBlink(state: PixelTextFieldState): Long {
        if (!state.isFocused || !state.cursorBlinkEnabled) return 0L
        /** 配置周期的一半对应下一次可见态翻转间隔。 */
        val halfPeriodMs = (state.cursorBlinkPeriodMs / 2L).coerceAtLeast(1L)
        return (halfPeriodMs - state.cursorBlinkElapsedMs).coerceIn(1L, halfPeriodMs)
    }

    /**
     * 设置 selection，并把每个稳定端点规范化到扩展字素边界。
     *
     * 折叠点使用最近边界且等距时向更大的 UTF-16 offset；非空正序范围向外扩展；倒序
     * 范围保持旧合约并折叠在 start 对应的最近边界。
     *
     * @param state 接收 selection 的文本输入状态。
     * @param selectionStart 请求的 UTF-16 起点。
     * @param selectionEnd 请求的 UTF-16 exclusive 终点；默认创建折叠光标。
     */
    public fun setSelection(
        state: PixelTextFieldState,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
    ) {
        /** 当前文本专属边界图。 */
        val boundaryMap = PixelGraphemeBoundaryMap(state.text)
        /** 统一规则产生的稳定 selection。 */
        val selection = boundaryMap.expand(selectionStart, selectionEnd)
        state.selectionStart = selection.start
        state.selectionEnd = selection.end
        notifyListeners()
    }

    /**
     * 清空文本并把光标重置到开头，同时清除 composition。
     *
     * @param state 要清空的文本输入状态。
     */
    public fun clear(state: PixelTextFieldState) {
        updateText(state = state, text = "")
    }

    /**
     * 选中当前文本的全部内容；首尾天然是扩展字素边界。
     *
     * @param state 要全选的文本输入状态。
     */
    public fun selectAll(state: PixelTextFieldState) {
        setSelection(
            state = state,
            selectionStart = 0,
            selectionEnd = state.text.length,
        )
    }

    /**
     * 返回 [state] 当前 selection 的原始文本；折叠 selection 返回空字符串。
     *
     * 读取前会防御性地向外规范化端点，但不会修改状态或触发通知。
     *
     * @param state 提供文本和 selection 的状态。
     * @return 完整字素组成的选中文本，或空字符串。
     */
    public fun selectedText(state: PixelTextFieldState): String {
        /** 当前原文对应的稳定字素边界图。 */
        val boundaryMap = PixelGraphemeBoundaryMap(state.text)
        /** 防御性规范化后的 selection。 */
        val selection = boundaryMap.expand(state.selectionStart, state.selectionEnd)
        if (selection.isCollapsed) return ""
        return state.text.substring(selection.start, selection.end)
    }

    /**
     * 删除并返回 [state] 当前 selection；没有非空 selection 时返回 `null`。
     *
     * 删除前 selection 会向外规范化，因此不会从有效 surrogate pair 或扩展字素簇中只
     * 剪切一部分。
     *
     * @param state 要剪切的文本输入状态。
     * @return 被删除的原始文本，或在没有 selection 时返回 `null`。
     */
    public fun cutSelection(state: PixelTextFieldState): String? {
        /** 先按旧文本边界取得的完整选中内容。 */
        val selected = selectedText(state)
        if (selected.isEmpty()) return null
        replaceSelection(state, "")
        return selected
    }

    /**
     * 把 [text] 粘贴到 [state] 当前 selection，并把光标折叠到插入内容之后的最近边界。
     *
     * 空粘贴保持历史 no-op 语义。文本按原样保留，不执行 NFC；插入后会针对新文本重建
     * 字素图，因此跨拼接点形成的新组合序列或 ZWJ 序列也不会留下内部光标。
     *
     * @param state 接收粘贴内容的文本输入状态。
     * @param text 要原样插入的文本。
     */
    public fun paste(state: PixelTextFieldState, text: String) {
        if (text.isEmpty()) {
            /** 空粘贴不删除 selection，但仍防御性修正旧式内部 offset。 */
            val normalized = PixelGraphemeBoundaryMap(state.text)
                .expand(state.selectionStart, state.selectionEnd)
            applySelectionIfChanged(state, normalized)
            return
        }
        replaceSelection(state, text)
    }

    /**
     * 用 [replacement] 替换当前 selection，并在新文本上重新规范化折叠光标。
     *
     * 该 additive API 是 Tester、宿主输入和剪贴板共享的字素安全编辑原语。空
     * [replacement] 会删除非空 selection；若 selection 也为空则返回 `false`。
     *
     * @param state 接收编辑的文本输入状态。
     * @param replacement 按原始 UTF-16 内容插入且不执行 Unicode 规范化的文本。
     * @return 文本、selection 或 composition 是否实际改变。
     */
    public fun replaceSelection(
        state: PixelTextFieldState,
        replacement: String,
    ): Boolean {
        /** 必须基于编辑前原文规范化待替换范围。 */
        val oldBoundaryMap = PixelGraphemeBoundaryMap(state.text)
        /** 非空旧范围向外扩展，折叠旧光标按固定 downstream affinity 吸附。 */
        val oldSelection = oldBoundaryMap.expand(state.selectionStart, state.selectionEnd)
        return replaceRange(state, oldSelection, replacement)
    }

    /**
     * 把 [range] 替换为 [replacement]，供 selection 替换和相邻字素删除共享同一实现。
     */
    private fun replaceRange(
        state: PixelTextFieldState,
        range: PixelUtf16Range,
        replacement: String,
    ): Boolean {
        /** 私有调用方已用当前原文边界图规范化的待替换范围。 */
        val oldSelection = range
        if (oldSelection.isCollapsed && replacement.isEmpty()) {
            return applySelectionIfChanged(state, oldSelection)
        }
        /** 编辑前的原始文本，用于构造新值和判定实际变化。 */
        val oldText = state.text
        /** 替换旧范围后得到的原始新文本，不执行 NFC 或内容重写。 */
        val nextText = buildString(
            oldText.length - oldSelection.length + replacement.length,
        ) {
            append(oldText, 0, oldSelection.start)
            append(replacement)
            append(oldText, oldSelection.end, oldText.length)
        }
        /** 插入内容末尾在新文本 UTF-16 坐标中的候选光标。 */
        val requestedCaret = oldSelection.start + replacement.length
        /** 新文本必须重建边界图，因为拼接可能跨编辑点合并为一个字素。 */
        val nextBoundaryMap = PixelGraphemeBoundaryMap(nextText)
        /** 按固定最近/downstream 规则得到的新折叠光标。 */
        val nextSelection = nextBoundaryMap.expand(requestedCaret, requestedCaret)
        /** 文本、selection 或 composition 任一变化都表示编辑产生了可观察状态变化。 */
        val changed = oldText != nextText ||
            state.selectionStart != nextSelection.start ||
            state.selectionEnd != nextSelection.end ||
            state.compositionStart != -1 ||
            state.compositionEnd != -1
        if (!changed) return false
        state.text = nextText
        state.selectionStart = nextSelection.start
        state.selectionEnd = nextSelection.end
        state.compositionStart = -1
        state.compositionEnd = -1
        notifyListeners()
        return true
    }

    /**
     * 根据 UTF-16 位置选择 ASCII 单词、单个非空白字素或折叠到空白字素开头。
     *
     * 该方法保持既有 ASCII 单词定义，但“单个字符”已升级为完整扩展字素簇。传入有效
     * surrogate pair、组合字符、emoji modifier、ZWJ、VS、keycap、RI 或 CRLF 内部 offset
     * 时，不会产生内部 selection 端点。
     *
     * @param state 提供待选文本并接收 selection 的状态。
     * @param index 要命中的 UTF-16 offset；越界值会 clamp。
     */
    public fun selectWordAt(state: PixelTextFieldState, index: Int) {
        /** 保持原样参与分段的当前文本。 */
        val text = state.text
        if (text.isEmpty()) {
            setSelection(state, 0, 0)
            return
        }
        /** 当前原文专属的字素边界图。 */
        val boundaryMap = PixelGraphemeBoundaryMap(text)
        /** 命中末尾时沿用旧行为并选择前一个字素，否则定位命中字素的起点。 */
        val clampedIndex = index.coerceIn(0, text.length)
        /** 命中字素的 inclusive UTF-16 起点。 */
        val clusterStart = if (clampedIndex == text.length) {
            boundaryMap.previous(text.length)
        } else {
            boundaryMap.floor(clampedIndex)
        }
        /** 命中字素的 exclusive UTF-16 终点。 */
        val clusterEnd = boundaryMap.next(clusterStart)
        /** 完整命中字素内容，用于保持既有 ASCII/空白分类。 */
        val cluster = text.substring(clusterStart, clusterEnd)
        if (cluster.isAsciiWordCluster()) {
            /** 向前扫描连续单 code-unit ASCII word 字素后的起点。 */
            var start = clusterStart
            while (start > 0) {
                /** 前一个完整字素的起点。 */
                val previousStart = boundaryMap.previous(start)
                if (!text.substring(previousStart, start).isAsciiWordCluster()) break
                start = previousStart
            }
            /** 向后扫描连续单 code-unit ASCII word 字素后的终点。 */
            var end = clusterEnd
            while (end < text.length) {
                /** 后一个完整字素的终点。 */
                val nextEnd = boundaryMap.next(end)
                if (!text.substring(end, nextEnd).isAsciiWordCluster()) break
                end = nextEnd
            }
            setSelection(state, start, end)
            return
        }
        if (cluster.all(Char::isWhitespace)) {
            /** 末尾命中沿用旧“前一个字符”行为，其余位置按最近/downstream 规则吸附。 */
            val requestedCaret = if (clampedIndex == text.length) clusterStart else clampedIndex
            setSelection(state, requestedCaret, requestedCaret)
            return
        }
        setSelection(state, clusterStart, clusterEnd)
    }

    /**
     * 把光标/selection 向前一个扩展字素边界移动。
     *
     * 非扩展移动会把已有非空 selection 折叠到起点；折叠光标则移动到前一个完整字素
     * 之前。[extendSelection] 为 `true` 时保持终点作为锚并向前扩展起点。方法不改写文本，
     * 也不自动结束有效 composition。
     *
     * @param state 接收移动结果的文本输入状态。
     * @param extendSelection 是否扩展 selection，而不是折叠/移动光标。
     * @return selection 是否实际改变。
     */
    public fun moveCaretBackward(
        state: PixelTextFieldState,
        extendSelection: Boolean = false,
    ): Boolean {
        /** 当前文本对应的稳定字素边界图。 */
        val boundaryMap = PixelGraphemeBoundaryMap(state.text)
        /** 防御性规范化的当前 selection。 */
        val current = boundaryMap.expand(state.selectionStart, state.selectionEnd)
        /** 根据是否扩展及当前是否折叠得到的目标 selection。 */
        val target = when {
            extendSelection -> PixelUtf16Range(
                start = boundaryMap.previous(current.start),
                end = current.end,
            )
            !current.isCollapsed -> PixelUtf16Range(current.start, current.start)
            else -> {
                /** 前一个完整字素之前的目标光标。 */
                val caret = boundaryMap.previous(current.start)
                PixelUtf16Range(caret, caret)
            }
        }
        return applySelectionIfChanged(state, target)
    }

    /**
     * 把光标/selection 向后一个扩展字素边界移动。
     *
     * 非扩展移动会把已有非空 selection 折叠到终点；折叠光标则移动到下一个完整字素
     * 之后。[extendSelection] 为 `true` 时保持起点作为锚并向后扩展终点。方法不改写文本，
     * 也不自动结束有效 composition。
     *
     * @param state 接收移动结果的文本输入状态。
     * @param extendSelection 是否扩展 selection，而不是折叠/移动光标。
     * @return selection 是否实际改变。
     */
    public fun moveCaretForward(
        state: PixelTextFieldState,
        extendSelection: Boolean = false,
    ): Boolean {
        /** 当前文本对应的稳定字素边界图。 */
        val boundaryMap = PixelGraphemeBoundaryMap(state.text)
        /** 防御性规范化的当前 selection。 */
        val current = boundaryMap.expand(state.selectionStart, state.selectionEnd)
        /** 根据是否扩展及当前是否折叠得到的目标 selection。 */
        val target = when {
            extendSelection -> PixelUtf16Range(
                start = current.start,
                end = boundaryMap.next(current.end),
            )
            !current.isCollapsed -> PixelUtf16Range(current.end, current.end)
            else -> {
                /** 下一个完整字素之后的目标光标。 */
                val caret = boundaryMap.next(current.end)
                PixelUtf16Range(caret, caret)
            }
        }
        return applySelectionIfChanged(state, target)
    }

    /**
     * 删除光标之前的一个完整扩展字素，或优先删除当前非空 selection。
     *
     * 删除范围在旧文本上规范化，编辑后在新文本上重建边界图并重新吸附光标，所以不会
     * 拆分有效 surrogate pair、组合序列、emoji ZWJ、RI flag 或 CRLF。成功删除会清除
     * composition。
     *
     * @param state 接收删除结果的文本输入状态。
     * @return 文本、selection 或 composition 是否实际改变；开头空光标返回 `false`。
     */
    public fun deleteBackward(state: PixelTextFieldState): Boolean {
        /** 当前文本对应的稳定字素边界图。 */
        val boundaryMap = PixelGraphemeBoundaryMap(state.text)
        /** 删除前防御性规范化的 selection。 */
        val current = boundaryMap.expand(state.selectionStart, state.selectionEnd)
        /** 非空 selection 原样删除；空 selection 扩展到前一个完整字素。 */
        val deletion = if (!current.isCollapsed) {
            current
        } else {
            PixelUtf16Range(boundaryMap.previous(current.start), current.start)
        }
        return replaceRange(state, deletion, "")
    }

    /**
     * 删除光标之后的一个完整扩展字素，或优先删除当前非空 selection。
     *
     * 删除范围在旧文本上规范化，编辑后在新文本上重建边界图并重新吸附光标，所以不会
     * 拆分有效 surrogate pair、组合序列、emoji ZWJ、RI flag 或 CRLF。成功删除会清除
     * composition。
     *
     * @param state 接收删除结果的文本输入状态。
     * @return 文本、selection 或 composition 是否实际改变；末尾空光标返回 `false`。
     */
    public fun deleteForward(state: PixelTextFieldState): Boolean {
        /** 当前文本对应的稳定字素边界图。 */
        val boundaryMap = PixelGraphemeBoundaryMap(state.text)
        /** 删除前防御性规范化的 selection。 */
        val current = boundaryMap.expand(state.selectionStart, state.selectionEnd)
        /** 非空 selection 原样删除；空 selection 扩展到后一个完整字素。 */
        val deletion = if (!current.isCollapsed) {
            current
        } else {
            PixelUtf16Range(current.end, boundaryMap.next(current.end))
        }
        return replaceRange(state, deletion, "")
    }

    /**
     * 立即把状态标记为已聚焦，并重置光标闪烁。
     *
     * @param state 接收焦点状态的文本输入状态。
     */
    public fun focus(state: PixelTextFieldState) {
        state.isFocused = true
        state.focusRequested = false
        state.blurRequested = false
        resetCursorBlink(state)
        notifyListeners()
    }

    /**
     * 立即把状态标记为失焦、结束瞬态 IME composition，并取消待处理焦点请求。
     *
     * Composition 不跨焦点会话保留；否则旧 InputConnection 的 composing range 会在字段
     * 切换后被错误恢复到新的 IME 会话。原始文本和 selection 均保持不变。
     *
     * @param state 接收失焦状态的文本输入状态。
     */
    public fun blur(state: PixelTextFieldState) {
        state.isFocused = false
        state.compositionStart = -1
        state.compositionEnd = -1
        state.focusRequested = false
        state.blurRequested = false
        resetCursorBlink(state)
        notifyListeners()
    }

    /**
     * 请求下一帧聚焦；已经聚焦或存在待处理请求时不重复派发。
     *
     * @param state 记录待处理聚焦请求的文本输入状态。
     */
    public fun requestFocus(state: PixelTextFieldState) {
        if (state.isFocused || state.focusRequested) return
        state.focusRequested = true
        state.blurRequested = false
        notifyListeners()
    }

    /**
     * 请求下一帧失焦；尚未应用的聚焦请求会被直接取消。
     *
     * @param state 记录待处理失焦请求的文本输入状态。
     */
    public fun requestBlur(state: PixelTextFieldState) {
        if (state.blurRequested) return
        if (!state.isFocused) {
            if (!state.focusRequested) return
            state.focusRequested = false
            notifyListeners()
            return
        }
        state.blurRequested = true
        state.focusRequested = false
        notifyListeners()
    }

    /**
     * 仅在 [selection] 与当前端点不同时应用并发送一次监听通知。
     *
     * @return selection 是否实际改变。
     */
    private fun applySelectionIfChanged(
        state: PixelTextFieldState,
        selection: PixelUtf16Range,
    ): Boolean {
        /** 两端完全相同表示调用是稳定 no-op。 */
        val changed = state.selectionStart != selection.start ||
            state.selectionEnd != selection.end
        if (!changed) return false
        state.selectionStart = selection.start
        state.selectionEnd = selection.end
        notifyListeners()
        return true
    }

    /** 当前字素是否由一个既有 ASCII word base 和零个或多个 Unicode Extend 组成。 */
    private fun String.isAsciiWordCluster(): Boolean {
        if (isEmpty() || !this[0].isAsciiWord()) return false
        /** UTF-16 cursor validating every scalar after the required ASCII base. */
        var offset = 1
        while (offset < length) {
            /** base 之后的完整 Unicode code point，不能按 Char 拆 supplementary 数据。 */
            val codePoint = Character.codePointAt(this, offset)
            if (
                UnicodeGraphemeData.graphemeBreakProperty(codePoint) !=
                UnicodeGraphemeBreakProperty.EXTEND
            ) {
                return false
            }
            offset += Character.charCount(codePoint)
        }
        return true
    }

    /** 是否属于控制器为兼容旧 selectWord 行为保留的 ASCII word 集合。 */
    private fun Char.isAsciiWord(): Boolean {
        return this == '_' || this in '0'..'9' || this in 'A'..'Z' || this in 'a'..'z'
    }
}

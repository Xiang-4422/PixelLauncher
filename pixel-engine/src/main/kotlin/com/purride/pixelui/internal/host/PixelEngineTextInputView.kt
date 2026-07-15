package com.purride.pixelui.internal.host

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.text.Editable
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.TextAttribute
import android.widget.EditText
import com.purride.pixelui.PixelGraphemeBoundaryMap
import com.purride.pixelui.PixelTextEditingValue
import com.purride.pixelui.PixelUtf16Range
import com.purride.pixelui.internal.text.isWellFormedUtf16
import com.purride.pixelui.internal.text.normalizeGraphemeOffsets
import com.purride.pixelui.internal.text.offsetByCodePointsStrictly

/**
 * Engine-owned hidden editor that keeps every observable Android editing offset on a grapheme boundary.
 *
 * The public bridge continues to expose this instance as [EditText]. Keeping the concrete type
 * internal lets the SDK intercept InputConnection commands without adding Android implementation
 * types to the stable API surface.
 */
internal class PixelEngineTextInputView(
    context: Context,
) : EditText(context) {
    init {
        // 该 View 只承载 Editable 与 InputConnection；移除可见 TextView 装饰，避免进入宿主绘制列表。
        background = null
        setPadding(0, 0, 0, 0)
        isCursorVisible = false
        setWillNotDraw(true)
    }

    /** Listener that receives normalized text, selection, and composition snapshots. */
    private var editingValueListener: ((PixelTextEditingValue) -> Unit)? = null

    /** Last value delivered or installed by the host, used to suppress duplicate callbacks. */
    private var lastPublishedValue: PixelTextEditingValue? = null

    /** Host 向平台编辑器回写的嵌套深度；大于零时禁止向引擎发布 watcher 回调。 */
    private var hostWriteDepth: Int = 0

    /** Nesting depth shared by explicit InputConnection and local hardware-key batches. */
    private var batchDepth: Int = 0

    /** Whether an outer batch owes one normalized callback when it finishes. */
    private var publishPending: Boolean = false

    /** Re-entrancy guard for selection/span changes made by the normalizer itself. */
    private var normalizationDepth: Int = 0

    /** Nesting guard used while stale connections are drained without publishing their edits. */
    private var retirementDepth: Int = 0

    /** Selection anchor retained across consecutive Shift+Left/Right commands. */
    private var logicalSelectionAnchor: Int? = null

    /** Guard preventing an engine-owned selection extension from clearing its own anchor. */
    private var logicalSelectionWriteDepth: Int = 0

    /** Monotonic token that invalidates every InputConnection from an earlier editor session. */
    private var connectionGeneration: Long = 0L

    /** Currently served wrapper, retained so a restart can drain its outstanding edit batches. */
    private var activeConnection: PixelGraphemeInputConnection? = null

    /** Last controller value installed into the hidden editor, used to recognize stale rebind echoes. */
    private var lastAppliedHostValue: PixelTextEditingValue? = null

    /** Genuine controller update deferred until an active platform batch has finished. */
    private var deferredHostEditingValue: PixelTextEditingValue? = null

    /** Whether a non-echo host value appeared during the current outer platform batch. */
    private var hostOverrideObservedInBatch: Boolean = false

    /** BaseInputConnection helper that installs Android's platform-recognized composing marker. */
    private val composingSpanConnection = PixelComposingSpanConnection(this)

    /** Installs the bridge callback after Android has completed base View construction. */
    internal fun setEditingValueListener(listener: (PixelTextEditingValue) -> Unit) {
        editingValueListener = listener
    }

    /**
     * Applies an engine-owned editing value without echoing it back as a platform-originated edit.
     *
     * Android input filters remain authoritative for the hidden editor. Consequently offsets are
     * normalized against the actual post-filter text before they are installed.
     */
    internal fun applyHostEditingValue(value: PixelTextEditingValue) {
        if (batchDepth > 0) {
            if (value != lastAppliedHostValue) {
                hostOverrideObservedInBatch = true
                deferredHostEditingValue = value
            } else if (hostOverrideObservedInBatch) {
                deferredHostEditingValue = value
            }
            return
        }
        applyHostEditingValueNow(value)
    }

    /** 在平台 batch 仲裁完成后立即安装 Host 编辑值，并合并平台编辑后处理。 */
    private fun applyHostEditingValueNow(value: PixelTextEditingValue) {
        hostWriteDepth += 1
        // 合并文本、选区与组合区写入，让 TextView 只在结尾执行一次 updateAfterEdit。
        beginBatchEdit()
        try {
            /** 当前 Editable 必须原位保留，避免 setText 替换 buffer 并让 Android 关闭 InputConnection。 */
            val editable = text
            if (editable?.toString() != value.text) {
                if (editable == null) {
                    setText(value.text)
                } else {
                    editable.replace(0, editable.length, value.text)
                }
            }
            /** 按过滤后的真实 Editable 内容重新规范文本、选区与组合区。 */
            val actualValue = value.copy(text = text?.toString().orEmpty()).normalizeGraphemeOffsets()
            applyNormalizedOffsets(actualValue)
            lastAppliedHostValue = actualValue
            lastPublishedValue = actualValue
        } finally {
            try {
                endBatchEdit()
            } finally {
                // endBatchEdit 可能同步触发平台 watcher，整个结束阶段仍需保持 Host 回写抑制。
                hostWriteDepth -= 1
            }
        }
    }

    /** Retires the current IME session so late commands cannot mutate a newly focused TextField. */
    internal fun retireInputConnections() {
        retirementDepth += 1
        try {
            activeConnection?.retire()
            activeConnection = null
            connectionGeneration += 1L
            logicalSelectionAnchor = null
            publishPending = false
            deferredHostEditingValue = null
            hostOverrideObservedInBatch = false
        } finally {
            retirementDepth -= 1
        }
    }

    /** Creates the platform connection and wraps it in the API-appropriate grapheme guard. */
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        /** Concrete EditText delegate that continues to own ordinary IME protocol behavior. */
        val platformConnection = super.onCreateInputConnection(outAttrs) ?: return null
        retireInputConnections()
        /** Wrapper tied to the new generation; every older wrapper is now mutation-inert. */
        val connection = createPixelGraphemeInputConnection(
            inputView = this,
            target = platformConnection,
            generation = connectionGeneration,
        )
        activeConnection = connection
        return connection
    }

    /** 隐藏编辑器始终占用一个物理像素，不让文本、字体或行数参与 Android measure。 */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(HiddenEditorSizePx, HiddenEditorSizePx)
    }

    /** 隐藏编辑器的可见文本由 Pixel Host 绘制，平台 TextView 不生成背景、光标或字形 display list。 */
    override fun onDraw(canvas: Canvas) = Unit

    /** Publishes selection-only changes, which Android's TextWatcher intentionally does not report. */
    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (logicalSelectionWriteDepth == 0) {
            logicalSelectionAnchor = null
        }
        requestNormalizedPublish()
    }

    /** Publishes text changes while composition-only commands are published by the wrapper itself. */
    override fun onTextChanged(
        text: CharSequence?,
        start: Int,
        lengthBefore: Int,
        lengthAfter: Int,
    ) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        logicalSelectionAnchor = null
        requestNormalizedPublish()
    }

    /** Keeps hardware cursor and delete keys on the same grapheme rules as software IMEs. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        /** Whether the key was recognized and executed by the engine editing contract. */
        val handled = when (keyCode) {
            KeyEvent.KEYCODE_DEL -> deleteSelectionOrCluster(backward = true)
            KeyEvent.KEYCODE_FORWARD_DEL -> deleteSelectionOrCluster(backward = false)
            KeyEvent.KEYCODE_DPAD_LEFT -> moveLogicalCaret(
                backward = true,
                extendSelection = event.isShiftPressed,
            )
            KeyEvent.KEYCODE_DPAD_RIGHT -> moveLogicalCaret(
                backward = false,
                extendSelection = event.isShiftPressed,
            )
            else -> false
        }
        return handled || super.onKeyDown(keyCode, event)
    }

    /** Consumes matching key-up events after the corresponding grapheme-safe key-down command. */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return if (keyCode.isPixelEditingKey()) true else super.onKeyUp(keyCode, event)
    }

    /** 保存隐藏平台编辑器自身的固定几何常量。 */
    private companion object {
        /** 焦点与 InputConnection 所需的最小非零物理尺寸。 */
        const val HiddenEditorSizePx: Int = 1
    }

    /** Starts one callback-coalescing edit batch. */
    internal fun beginPixelBatch() {
        batchDepth += 1
    }

    /** Ends one edit batch and publishes exactly once when the outermost batch has changed state. */
    internal fun endPixelBatch() {
        check(batchDepth > 0) { "Pixel text-input batch depth underflow" }
        batchDepth -= 1
        if (batchDepth == 0) {
            if (retirementDepth > 0) {
                publishPending = false
                deferredHostEditingValue = null
                return
            }
            /** Host update that differs from the pre-batch value wins an actual concurrent edit. */
            val deferredHostValue = deferredHostEditingValue
            deferredHostEditingValue = null
            hostOverrideObservedInBatch = false
            if (deferredHostValue != null) {
                publishPending = false
                applyHostEditingValueNow(deferredHostValue)
            } else if (publishPending) {
                publishPending = false
                publishNormalizedValue()
            }
        }
    }

    /** Returns whether [generation] still belongs to the editor session served by this view. */
    internal fun isCurrentConnectionGeneration(generation: Long): Boolean {
        return generation == connectionGeneration
    }

    /**
     * Runs one InputConnection mutation with legal state before and after the platform delegate.
     *
     * The post-pass rebuilds the boundary map because inserted Extend, ZWJ, RI, or surrogate code
     * units may form different clusters with adjacent text.
     */
    internal fun <T> runPixelMutation(block: () -> T): T {
        beginPixelBatch()
        return try {
            normalizeEditableState()
            block()
        } finally {
            normalizeEditableState()
            publishPending = true
            endPixelBatch()
        }
    }

    /** Returns the current normalized editing snapshot without changing any text code units. */
    internal fun normalizedEditingValue(): PixelTextEditingValue = normalizeEditableState()

    /**
     * Deletes UTF-16 units around the selection after expanding both deletion edges to clusters.
     *
     * Selected text is intentionally preserved because Android's surrounding-delete contract acts
     * before selection start and after selection end.
     */
    internal fun deleteSurroundingUtf16(beforeLength: Int, afterLength: Int): Boolean {
        return runPixelMutation {
            /** Android treats negative surrounding lengths as zero-length sides. */
            val safeBeforeLength = beforeLength.coerceAtLeast(0)
            /** Android treats negative surrounding lengths as zero-length sides. */
            val safeAfterLength = afterLength.coerceAtLeast(0)
            /** Normalized selection/composition defining the protected middle range. */
            val value = normalizedEditingValue()
            /** Live platform buffer mutated only after every edge has been validated. */
            val editable = text ?: return@runPixelMutation false
            /** Boundary authority for the current pre-deletion buffer. */
            val boundaries = PixelGraphemeBoundaryMap(editable.toString())
            /** Composition and selection form the middle region that surrounding delete must retain. */
            val protectedStart = value.protectedEditingStart()
            /** Exclusive end of the middle region retained by surrounding deletion. */
            val protectedEnd = value.protectedEditingEnd()
            /** Requested prefix edge before outward grapheme expansion. */
            val rawBefore = (protectedStart.toLong() - safeBeforeLength.toLong())
                .coerceAtLeast(0L)
                .toInt()
            /** Requested suffix edge before outward grapheme expansion. */
            val rawAfter = (protectedEnd.toLong() + safeAfterLength.toLong())
                .coerceAtMost(editable.length.toLong())
                .toInt()
            deleteAroundProtectedRange(
                editable = editable,
                value = value,
                protectedStart = protectedStart,
                protectedEnd = protectedEnd,
                deleteStart = boundaries.floor(rawBefore),
                deleteEnd = boundaries.ceil(rawAfter),
            )
            true
        }
    }

    /** Deletes code points around the selection while expanding the final edges to graphemes. */
    internal fun deleteSurroundingCodePoints(beforeLength: Int, afterLength: Int): Boolean {
        return runPixelMutation {
            /** Platform code-point delete clamps a negative before count to zero. */
            val safeBeforeLength = beforeLength.coerceAtLeast(0)
            /** Platform code-point delete clamps a negative after count to zero. */
            val safeAfterLength = afterLength.coerceAtLeast(0)
            /** Normalized selection/composition defining the protected middle range. */
            val value = normalizedEditingValue()
            /** Live platform buffer inspected strictly for malformed UTF-16 traversal. */
            val editable = text ?: return@runPixelMutation false
            /** Boundary authority used only after strict code-point traversal succeeds. */
            val boundaries = PixelGraphemeBoundaryMap(editable.toString())
            /** Composition and selection form the middle region that surrounding delete must retain. */
            val protectedStart = value.protectedEditingStart()
            /** Exclusive end of the middle region retained by surrounding deletion. */
            val protectedEnd = value.protectedEditingEnd()
            /** Strictly traversed prefix edge, or a no-op when malformed UTF-16 is encountered. */
            val rawBefore = offsetByCodePointsStrictly(
                text = editable,
                offset = protectedStart,
                codePointDelta = -safeBeforeLength,
            ) ?: return@runPixelMutation true
            /** Strictly traversed suffix edge, or a no-op when malformed UTF-16 is encountered. */
            val rawAfter = offsetByCodePointsStrictly(
                text = editable,
                offset = protectedEnd,
                codePointDelta = safeAfterLength,
            ) ?: return@runPixelMutation true
            deleteAroundProtectedRange(
                editable = editable,
                value = value,
                protectedStart = protectedStart,
                protectedEnd = protectedEnd,
                deleteStart = boundaries.floor(rawBefore),
                deleteEnd = boundaries.ceil(rawAfter),
            )
            true
        }
    }

    /** Deletes the selected range or one adjacent extended grapheme cluster. */
    internal fun deleteSelectionOrCluster(backward: Boolean): Boolean {
        return runPixelMutation {
            /** Stable selection used to choose range deletion versus adjacent-cluster deletion. */
            val value = normalizedEditingValue()
            /** Live buffer receiving the final whole-cluster removal. */
            val editable = text ?: return@runPixelMutation false
            /** Boundary authority for the current live buffer. */
            val boundaries = PixelGraphemeBoundaryMap(editable.toString())
            /** Inclusive whole-cluster deletion edge selected below. */
            val deleteStart: Int
            /** Exclusive whole-cluster deletion edge selected below. */
            val deleteEnd: Int
            if (value.selectionStart != value.selectionEnd) {
                deleteStart = value.selectionStart
                deleteEnd = value.selectionEnd
            } else if (backward) {
                deleteStart = boundaries.previous(value.selectionStart)
                deleteEnd = value.selectionStart
            } else {
                deleteStart = value.selectionEnd
                deleteEnd = boundaries.next(value.selectionEnd)
            }
            if (deleteStart == deleteEnd) return@runPixelMutation false
            editable.delete(deleteStart, deleteEnd)
            setSelection(deleteStart)
            true
        }
    }

    /** Moves or extends the logical caret by exactly one grapheme cluster. */
    internal fun moveLogicalCaret(backward: Boolean, extendSelection: Boolean): Boolean {
        return runPixelMutation {
            /** Current stable selection from which the logical active edge moves. */
            val value = normalizedEditingValue()
            /** Boundary authority for one-cluster movement. */
            val boundaries = PixelGraphemeBoundaryMap(value.text)
            /** Ordered selection start produced by the requested movement. */
            val nextStart: Int
            /** Ordered selection end produced by the requested movement. */
            val nextEnd: Int
            if (!extendSelection) {
                logicalSelectionAnchor = null
                /** Collapsed destination, or the chosen edge of an existing selection. */
                val caret = if (value.selectionStart != value.selectionEnd) {
                    if (backward) value.selectionStart else value.selectionEnd
                } else if (backward) {
                    boundaries.previous(value.selectionStart)
                } else {
                    boundaries.next(value.selectionEnd)
                }
                nextStart = caret
                nextEnd = caret
            } else {
                /** Existing anchor, or the edge opposite the first requested movement direction. */
                val anchor = logicalSelectionAnchor ?: if (value.selectionStart == value.selectionEnd) {
                    value.selectionStart
                } else if (backward) {
                    value.selectionEnd
                } else {
                    value.selectionStart
                }
                logicalSelectionAnchor = anchor
                /** Active edge opposite [anchor], allowed to cross it during repeated Shift movement. */
                val activeCaret = when (anchor) {
                    value.selectionStart -> value.selectionEnd
                    value.selectionEnd -> value.selectionStart
                    else -> if (backward) value.selectionStart else value.selectionEnd
                }
                /** One-cluster movement of the active edge before ordering the stored range. */
                val movedCaret = if (backward) {
                    boundaries.previous(activeCaret)
                } else {
                    boundaries.next(activeCaret)
                }
                nextStart = minOf(anchor, movedCaret)
                nextEnd = maxOf(anchor, movedCaret)
            }
            if (selectionStart == nextStart && selectionEnd == nextEnd) {
                false
            } else {
                logicalSelectionWriteDepth += 1
                try {
                    setSelection(nextStart, nextEnd)
                } finally {
                    logicalSelectionWriteDepth -= 1
                }
                true
            }
        }
    }

    /** Requests a normalized callback now or records one for the current outer edit batch. */
    internal fun requestNormalizedPublish() {
        if (
            editingValueListener == null ||
            hostWriteDepth > 0 ||
            normalizationDepth > 0 ||
            retirementDepth > 0
        ) return
        if (batchDepth > 0) {
            publishPending = true
        } else {
            publishNormalizedValue()
        }
    }

    /** Normalizes selection/composition in-place and returns the installed immutable snapshot. */
    private fun normalizeEditableState(): PixelTextEditingValue {
        /** Current live Editable; null is represented as an empty editing value during setup. */
        val editable = text
        /** Untrusted Android snapshot before engine boundary normalization. */
        val raw = PixelTextEditingValue(
            text = editable?.toString().orEmpty(),
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
            compositionStart = editable?.let(BaseInputConnection::getComposingSpanStart) ?: -1,
            compositionEnd = editable?.let(BaseInputConnection::getComposingSpanEnd) ?: -1,
        )
        /** Immutable stable snapshot installed back onto the Android buffer. */
        val normalized = raw.normalizeGraphemeOffsets()
        normalizationDepth += 1
        try {
            applyNormalizedOffsets(normalized)
        } finally {
            normalizationDepth -= 1
        }
        return normalized
    }

    /** Installs already-normalized selection and composition offsets on the current Editable. */
    private fun applyNormalizedOffsets(value: PixelTextEditingValue) {
        /** Live buffer receiving selection and Android's private composing marker. */
        val editable = text ?: return
        if (selectionStart != value.selectionStart || selectionEnd != value.selectionEnd) {
            setSelection(value.selectionStart, value.selectionEnd)
        }
        /** Platform marker start currently installed on the live buffer. */
        val currentCompositionStart = BaseInputConnection.getComposingSpanStart(editable)
        /** Platform marker end currently installed on the live buffer. */
        val currentCompositionEnd = BaseInputConnection.getComposingSpanEnd(editable)
        if (
            currentCompositionStart != value.compositionStart ||
            currentCompositionEnd != value.compositionEnd
        ) {
            BaseInputConnection.removeComposingSpans(editable)
            if (value.compositionStart >= 0 && value.compositionEnd > value.compositionStart) {
                composingSpanConnection.setComposingRegion(
                    value.compositionStart,
                    value.compositionEnd,
                )
            }
        }
    }

    /** Delivers the exact normalized snapshot unless it is identical to the previous delivery. */
    private fun publishNormalizedValue() {
        /** Active Host callback; absence means the view has not been bound yet. */
        val listener = editingValueListener ?: return
        /** Latest stable snapshot used for duplicate suppression and delivery. */
        val normalized = normalizeEditableState()
        if (normalized == lastPublishedValue) return
        lastPublishedValue = normalized
        listener(normalized)
    }

    /** Deletes outside the protected selection/composition union and preserves relative offsets. */
    private fun deleteAroundProtectedRange(
        editable: Editable,
        value: PixelTextEditingValue,
        protectedStart: Int,
        protectedEnd: Int,
        deleteStart: Int,
        deleteEnd: Int,
    ) {
        /** UTF-16 units removed before both selection and composition. */
        val deletedBefore = protectedStart - deleteStart
        /** Selection width retained while its absolute offsets shift left. */
        val selectedLength = value.selectionEnd - value.selectionStart
        if (deleteEnd > protectedEnd) {
            editable.delete(protectedEnd, deleteEnd)
        }
        if (deleteStart < protectedStart) {
            editable.delete(deleteStart, protectedStart)
        }
        /** Original ordered selection shifted by the exact prefix deletion. */
        val shiftedSelectionStart = value.selectionStart - deletedBefore
        setSelection(shiftedSelectionStart, shiftedSelectionStart + selectedLength)
    }
}

/**
 * Creates a wrapper whose bytecode only references APIs available on the running Android version.
 *
 * @param inputView engine-owned editor whose sessions and boundaries the wrapper guards.
 * @param target platform `EditText` connection that retains ordinary IME behavior.
 * @param generation session token that rejects commands after a target switch or restart.
 */
private fun createPixelGraphemeInputConnection(
    inputView: PixelEngineTextInputView,
    target: InputConnection,
    generation: Long,
): PixelGraphemeInputConnection {
    return when {
        Build.VERSION.SDK_INT >= 34 -> PixelApi34GraphemeInputConnection(
            inputView,
            target,
            generation,
        )
        Build.VERSION.SDK_INT >= 33 -> PixelApi33GraphemeInputConnection(
            inputView,
            target,
            generation,
        )
        else -> PixelGraphemeInputConnection(inputView, target, generation)
    }
}

/** BaseInputConnection exposing the view Editable solely to install Android's private marker. */
private class PixelComposingSpanConnection(
    /** Hidden editor whose live Editable receives the platform composing span. */
    private val inputView: PixelEngineTextInputView,
) : BaseInputConnection(inputView, true) {
    /** Returns the exact editor buffer rather than BaseInputConnection's fallback buffer. */
    override fun getEditable(): Editable? = inputView.text
}

/** Ordered protected start used by both surrounding-deletion units. */
private fun PixelTextEditingValue.protectedEditingStart(): Int {
    return if (compositionStart >= 0) minOf(selectionStart, compositionStart) else selectionStart
}

/** Ordered protected end used by both surrounding-deletion units. */
private fun PixelTextEditingValue.protectedEditingEnd(): Int {
    return if (compositionEnd >= 0) maxOf(selectionEnd, compositionEnd) else selectionEnd
}

/**
 * API 24-safe InputConnection interception shared by every supported Android version.
 *
 * @param inputView engine-owned editor receiving normalized mutations.
 * @param target platform delegate preserving Android protocol behavior.
 * @param generation editor-session token authorizing mutations from this wrapper.
 */
private open class PixelGraphemeInputConnection(
    /** 公开 `PixelEngineTextInputView` 的 `inputView` 配置或运行值。
 *
 * Engine-owned editor receiving every guarded command.
 */
    protected val inputView: PixelEngineTextInputView,
    target: InputConnection,
    /** View generation that this wrapper is authorized to mutate. */
    private val generation: Long,
) : InputConnectionWrapper(target, false) {
    /** Number of explicit beginBatchEdit calls owned by this connection wrapper. */
    private var connectionBatchDepth: Int = 0

    /** Whether retirement or close has made every subsequent editing command inert. */
    private var retired: Boolean = false

    /** Whether the platform delegate has already received its single closeConnection call. */
    private var delegateClosed: Boolean = false

    /** 执行 `PixelEngineTextInputView` 的 `acceptsMutations` 公开行为；具体参数、返回和副作用见下文。
 *
 * Whether this wrapper remains both open and current for the hidden editor.
 */
    protected fun acceptsMutations(): Boolean {
        return !retired && inputView.isCurrentConnectionGeneration(generation)
    }

    /** Retires a stale wrapper and drains batches before a new TextField session can reuse the view. */
    internal fun retire() {
        if (retired) return
        retired = true
        drainOwnedBatches()
    }

    /** Mirrors only platform-accepted batch nesting so a failed begin cannot suppress callbacks. */
    override fun beginBatchEdit(): Boolean {
        if (!acceptsMutations()) return false
        /** Delegate acceptance determines whether the IME owes a matching end call. */
        val accepted = super.beginBatchEdit()
        if (accepted) {
            inputView.beginPixelBatch()
            connectionBatchDepth += 1
        }
        return accepted
    }

    /** Completes the platform batch before releasing the matching engine callback batch. */
    override fun endBatchEdit(): Boolean {
        if (connectionBatchDepth <= 0) {
            if (!acceptsMutations()) return false
            return inputView.runPixelMutation { super.endBatchEdit() }
        }
        return try {
            super.endBatchEdit()
        } finally {
            connectionBatchDepth -= 1
            inputView.requestNormalizedPublish()
            inputView.endPixelBatch()
        }
    }

    /** Releases callback batches if a misbehaving IME closes the connection while still nested. */
    override fun closeConnection() {
        if (delegateClosed) return
        retired = true
        delegateClosed = true
        try {
            super.closeConnection()
        } finally {
            drainOwnedBatches(closeDelegateBatch = false)
        }
    }

    /** Closes every accepted delegate/view batch and publishes at most one final normalized value. */
    private fun drainOwnedBatches(closeDelegateBatch: Boolean = true) {
        while (connectionBatchDepth > 0) {
            try {
                if (closeDelegateBatch) {
                    super.endBatchEdit()
                }
            } finally {
                connectionBatchDepth -= 1
                inputView.requestNormalizedPublish()
                inputView.endPixelBatch()
            }
        }
    }

    /** Rejects newly orphaned surrogates, then normalizes around the delegated insertion. */
    override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
        if (!acceptsMutations() || !isWellFormedUtf16(text)) return false
        return inputView.runPixelMutation {
            super.commitText(text, newCursorPosition)
        }
    }

    /** Rejects newly orphaned surrogates, then normalizes the resulting composition and caret. */
    override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
        if (!acceptsMutations() || !isWellFormedUtf16(text)) return false
        return inputView.runPixelMutation {
            super.setComposingText(text, newCursorPosition)
        }
    }

    /** Orders, clamps, and expands a composing range before installing the platform marker. */
    override fun setComposingRegion(start: Int, end: Int): Boolean {
        if (!acceptsMutations()) return false
        return inputView.runPixelMutation {
            /** Android accepts either endpoint order and clamps both endpoints to the buffer. */
            val range = normalizedUnorderedRange(start, end)
            if (range.isCollapsed) {
                super.finishComposingText()
            } else {
                super.setComposingRegion(range.start, range.end)
            }
        }
    }

    /** Publishes composition-only removal after the platform removes composing spans. */
    override fun finishComposingText(): Boolean {
        if (!acceptsMutations()) return false
        return inputView.runPixelMutation {
            super.finishComposingText()
        }
    }

    /** Snaps a caret or expands a selection before giving it to the platform editor. */
    override fun setSelection(start: Int, end: Int): Boolean {
        if (!acceptsMutations()) return false
        /** Android ignores an out-of-bounds selection because the IME is stale, but returns true. */
        val length = inputView.text?.length ?: 0
        if (start !in 0..length || end !in 0..length) return true
        return inputView.runPixelMutation {
            /** Endpoint order is not significant for InputConnection.setSelection. */
            val range = normalizedUnorderedRange(start, end)
            super.setSelection(range.start, range.end)
        }
    }

    /** Implements Android UTF-16 surrounding deletion without allowing partial clusters. */
    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        if (!acceptsMutations()) return false
        return inputView.deleteSurroundingUtf16(beforeLength, afterLength)
    }

    /** Implements Android code-point deletion while retaining grapheme-safe final edges. */
    override fun deleteSurroundingTextInCodePoints(
        beforeLength: Int,
        afterLength: Int,
    ): Boolean {
        if (!acceptsMutations()) return false
        return inputView.deleteSurroundingCodePoints(beforeLength, afterLength)
    }

    /** Routes IME-synthesized editing keys through the same local grapheme commands. */
    override fun sendKeyEvent(event: KeyEvent): Boolean {
        if (!acceptsMutations()) return false
        if (!event.keyCode.isPixelEditingKey()) return super.sendKeyEvent(event)
        if (event.action == KeyEvent.ACTION_UP) return true
        if (event.action != KeyEvent.ACTION_DOWN) return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DEL -> inputView.deleteSelectionOrCluster(backward = true)
            KeyEvent.KEYCODE_FORWARD_DEL -> inputView.deleteSelectionOrCluster(backward = false)
            KeyEvent.KEYCODE_DPAD_LEFT -> inputView.moveLogicalCaret(
                backward = true,
                extendSelection = event.isShiftPressed,
            )
            KeyEvent.KEYCODE_DPAD_RIGHT -> inputView.moveLogicalCaret(
                backward = false,
                extendSelection = event.isShiftPressed,
            )
            else -> false
        }
    }

    /** 执行 `PixelEngineTextInputView` 的 `normalizedUnorderedRange` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns an ordered, clamped range expanded against the view's current grapheme map.
 */
    protected fun normalizedUnorderedRange(start: Int, end: Int): PixelUtf16Range {
        /** Current UTF-16 length used to reproduce Android's endpoint clipping behavior. */
        val length = inputView.text?.length ?: 0
        /** Lower endpoint after accepting reversed caller order and clipping to the live buffer. */
        val lower = minOf(start, end).coerceIn(0, length)
        /** Upper endpoint after accepting reversed caller order and clipping to the live buffer. */
        val upper = maxOf(start, end).coerceIn(0, length)
        return PixelGraphemeBoundaryMap(inputView.text?.toString().orEmpty()).expand(lower, upper)
    }
}

/**
 * API 33 overloads that carry TextAttribute without loading those signatures on API 24.
 *
 * @param inputView engine-owned editor receiving normalized mutations.
 * @param target platform delegate providing attributed Android operations.
 * @param generation editor-session token authorizing this wrapper.
 */
@TargetApi(33)
private open class PixelApi33GraphemeInputConnection(
    inputView: PixelEngineTextInputView,
    target: InputConnection,
    generation: Long,
) : PixelGraphemeInputConnection(inputView, target, generation) {
    /** Applies the API 33 commit overload under the same surrogate and grapheme invariants. */
    override fun commitText(
        text: CharSequence,
        newCursorPosition: Int,
        textAttribute: TextAttribute?,
    ): Boolean {
        if (!acceptsMutations() || !isWellFormedUtf16(text)) return false
        return inputView.runPixelMutation {
            super.commitText(text, newCursorPosition, textAttribute)
        }
    }

    /** Applies the API 33 composing-text overload under the same editing invariants. */
    override fun setComposingText(
        text: CharSequence,
        newCursorPosition: Int,
        textAttribute: TextAttribute?,
    ): Boolean {
        if (!acceptsMutations() || !isWellFormedUtf16(text)) return false
        return inputView.runPixelMutation {
            super.setComposingText(text, newCursorPosition, textAttribute)
        }
    }

    /** Expands the API 33 attributed composing region before delegating it. */
    override fun setComposingRegion(
        start: Int,
        end: Int,
        textAttribute: TextAttribute?,
    ): Boolean {
        if (!acceptsMutations()) return false
        return inputView.runPixelMutation {
            /** Android accepts either endpoint order and clamps before grapheme expansion. */
            val range = normalizedUnorderedRange(start, end)
            if (range.isCollapsed) {
                super.finishComposingText()
            } else {
                super.setComposingRegion(range.start, range.end, textAttribute)
            }
        }
    }
}

/**
 * API 34 absolute replacement overload kept in a separately verified runtime class.
 *
 * @param inputView engine-owned editor receiving normalized replacement.
 * @param target platform delegate providing the API 34 operation.
 * @param generation editor-session token authorizing this wrapper.
 */
@TargetApi(34)
private class PixelApi34GraphemeInputConnection(
    inputView: PixelEngineTextInputView,
    target: InputConnection,
    generation: Long,
) : PixelApi33GraphemeInputConnection(inputView, target, generation) {
    /** Expands the replaced range and rejects replacement text containing orphaned surrogates. */
    override fun replaceText(
        start: Int,
        end: Int,
        text: CharSequence,
        newCursorPosition: Int,
        textAttribute: TextAttribute?,
    ): Boolean {
        require(start >= 0) { "replaceText start must be non-negative" }
        require(end >= 0) { "replaceText end must be non-negative" }
        if (!acceptsMutations() || !isWellFormedUtf16(text)) return false
        return inputView.runPixelMutation {
            /** API 34 explicitly accepts reversed endpoints and clips them to the current text. */
            val range = normalizedUnorderedRange(start, end)
            super.replaceText(
                range.start,
                range.end,
                text,
                newCursorPosition,
                textAttribute,
            )
        }
    }
}

/** Whether this Android key participates in engine-owned logical text editing. */
private fun Int.isPixelEditingKey(): Boolean {
    return this == KeyEvent.KEYCODE_DEL ||
        this == KeyEvent.KEYCODE_FORWARD_DEL ||
        this == KeyEvent.KEYCODE_DPAD_LEFT ||
        this == KeyEvent.KEYCODE_DPAD_RIGHT
}

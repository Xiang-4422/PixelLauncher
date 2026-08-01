package com.purride.pixellockscreen.credential

import java.util.Arrays

/** 像素认证界面当前允许收集的设备凭据类型。 */
internal enum class PixelCredentialMode {
    /** 九宫格图案。 */
    PATTERN,

    /** 仅包含数字的 PIN。 */
    PIN,

    /** 由系统输入法提供字符的密码。 */
    PASSWORD,
}

/**
 * 仅在当前认证会话内存活的可清零字符缓冲。
 *
 * 该类型故意实现 [CharSequence]，让 Android 直接复制字符，而无需创建无法主动清零的
 * `String`。关闭后所有存储单元都会被覆写，且任何读取都会失败。
 */
internal class EphemeralCharBuffer(
    /** 缓冲允许保存的最大字符数。 */
    private val capacity: Int = DEFAULT_CAPACITY,
) : CharSequence, AutoCloseable {
    /** 保存凭据字符的固定容量可覆写数组。 */
    private val storage: CharArray = CharArray(capacity)

    /** 当前有效字符数量。 */
    private var currentLength: Int = 0

    /** 缓冲是否已经完成清零并永久关闭。 */
    private var closed: Boolean = false

    /** 当前有效字符数量；关闭后访问会失败。 */
    override val length: Int
        get() {
            ensureOpen()
            return currentLength
        }

    /** 在容量允许时追加一个字符。 */
    fun append(character: Char): Boolean {
        ensureOpen()
        if (currentLength >= capacity) {
            return false
        }
        storage[currentLength] = character
        currentLength += 1
        return true
    }

    /** 删除最后一个字符并立即覆写原存储位置。 */
    fun deleteLast(): Boolean {
        ensureOpen()
        if (currentLength == 0) {
            return false
        }
        currentLength -= 1
        storage[currentLength] = ZERO_CHARACTER
        return true
    }

    /** 返回指定位置字符，仅供系统凭据工厂同步复制。 */
    override fun get(index: Int): Char {
        ensureOpen()
        if (index !in 0 until currentLength) {
            throw IndexOutOfBoundsException("credential_character_index")
        }
        return storage[index]
    }

    /**
     * 禁止产生包含凭据内容的子序列。
     *
     * Android 的凭据工厂只使用 `length` 与索引读取，不需要此能力。
     */
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        throw UnsupportedOperationException("credential_subsequence_forbidden")
    }

    /** 创建一个独立可清零副本，用于把所有权移交给单次校验任务。 */
    fun copy(): EphemeralCharBuffer {
        ensureOpen()
        /** 与原缓冲容量一致的临时副本。 */
        val copiedBuffer = EphemeralCharBuffer(capacity)
        repeat(currentLength) { index -> copiedBuffer.append(storage[index]) }
        return copiedBuffer
    }

    /** 覆写全部存储位置，但允许当前输入会话继续使用缓冲。 */
    fun clear() {
        ensureOpen()
        Arrays.fill(storage, ZERO_CHARACTER)
        currentLength = 0
    }

    /** 永远只返回脱敏标记，避免调试器或日志隐式输出凭据。 */
    override fun toString(): String = REDACTED_TEXT

    /** 幂等覆写全部字符并永久关闭缓冲。 */
    override fun close() {
        if (closed) {
            return
        }
        Arrays.fill(storage, ZERO_CHARACTER)
        currentLength = 0
        closed = true
    }

    /** 拒绝使用已经完成清零的缓冲。 */
    private fun ensureOpen() {
        check(!closed) { "credential_buffer_closed" }
    }

    private companion object {
        /** 默认密码长度上限，避免注入进程被无界输入占用内存。 */
        const val DEFAULT_CAPACITY: Int = 64

        /** 清零字符。 */
        const val ZERO_CHARACTER: Char = '\u0000'

        /** 所有字符串化操作的固定脱敏结果。 */
        const val REDACTED_TEXT: String = "[REDACTED_CREDENTIAL]"
    }
}

/** 保存一次九宫格拖动路径，并保证格子合法且不重复。 */
internal class SecurePatternBuffer : AutoCloseable {
    /** 按用户经过顺序保存的九宫格编号。 */
    private val cells: IntArray = IntArray(CELL_COUNT) { EMPTY_CELL }

    /** 当前有效格子数量。 */
    private var currentSize: Int = 0

    /** 缓冲是否已经清零并永久关闭。 */
    private var closed: Boolean = false

    /** 当前路径包含的格子数量。 */
    val size: Int
        get() {
            ensureOpen()
            return currentSize
        }

    /** 追加一个尚未经过的合法格子。 */
    fun append(cellId: Int): Boolean {
        ensureOpen()
        require(cellId in 0 until CELL_COUNT) { "pattern_cell_out_of_range" }
        if (contains(cellId)) {
            return false
        }
        cells[currentSize] = cellId
        currentSize += 1
        return true
    }

    /** 返回路径中指定位置的格子编号。 */
    fun cellAt(index: Int): Int {
        ensureOpen()
        if (index !in 0 until currentSize) {
            throw IndexOutOfBoundsException("pattern_cell_index")
        }
        return cells[index]
    }

    /** 创建独立副本，用于移交给单次系统校验。 */
    fun copy(): SecurePatternBuffer {
        ensureOpen()
        /** 保留当前经过顺序的新缓冲。 */
        val copiedBuffer = SecurePatternBuffer()
        repeat(currentSize) { index -> copiedBuffer.append(cells[index]) }
        return copiedBuffer
    }

    /** 清除当前路径并覆写固定数组中的全部位置。 */
    fun clear() {
        ensureOpen()
        Arrays.fill(cells, EMPTY_CELL)
        currentSize = 0
    }

    /** 永远只返回脱敏标记，避免图案路径进入日志。 */
    override fun toString(): String = REDACTED_TEXT

    /** 幂等覆写全部格子并永久关闭缓冲。 */
    override fun close() {
        if (closed) {
            return
        }
        Arrays.fill(cells, EMPTY_CELL)
        currentSize = 0
        closed = true
    }

    /** 判断当前路径是否已经包含指定格子。 */
    private fun contains(cellId: Int): Boolean {
        repeat(currentSize) { index ->
            if (cells[index] == cellId) {
                return true
            }
        }
        return false
    }

    /** 拒绝使用已经完成清零的缓冲。 */
    private fun ensureOpen() {
        check(!closed) { "pattern_buffer_closed" }
    }

    private companion object {
        /** Android 图案锁固定包含九个格子。 */
        const val CELL_COUNT: Int = 9

        /** 清零后的非格子哨兵值。 */
        const val EMPTY_CELL: Int = -1

        /** 所有字符串化操作的固定脱敏结果。 */
        const val REDACTED_TEXT: String = "[REDACTED_PATTERN]"
    }
}

/** 单次系统校验独占的临时凭据所有权。 */
internal sealed class EphemeralCredentialLease : AutoCloseable {
    /** 当前凭据类型。 */
    abstract val mode: PixelCredentialMode

    /** 单次 PIN 或密码校验的字符所有权。 */
    internal class Characters(
        /** PIN 或密码类型。 */
        override val mode: PixelCredentialMode,
        /** 已从输入会话复制出的可清零字符。 */
        private val buffer: EphemeralCharBuffer,
    ) : EphemeralCredentialLease() {
        init {
            require(mode == PixelCredentialMode.PIN || mode == PixelCredentialMode.PASSWORD) {
                "character_credential_mode"
            }
        }

        /** 在同步代码块内向系统凭据工厂暴露字符序列。 */
        fun <Result> withCharacters(block: (CharSequence) -> Result): Result = block(buffer)

        /** 覆写本次校验持有的字符。 */
        override fun close() {
            buffer.close()
        }

        /** 返回不含字符的固定摘要。 */
        override fun toString(): String = "EphemeralCredentialLease.Characters(mode=$mode, value=[REDACTED])"
    }

    /** 单次图案校验的路径所有权。 */
    internal class Pattern(
        /** 已从输入会话复制出的可清零路径。 */
        private val buffer: SecurePatternBuffer,
    ) : EphemeralCredentialLease() {
        /** 图案凭据类型。 */
        override val mode: PixelCredentialMode = PixelCredentialMode.PATTERN

        /** 当前路径长度。 */
        val size: Int
            get() = buffer.size

        /** 返回指定位置的格子，仅供系统凭据工厂同步复制。 */
        fun cellAt(index: Int): Int = buffer.cellAt(index)

        /** 覆写本次校验持有的路径。 */
        override fun close() {
            buffer.close()
        }

        /** 返回不含路径的固定摘要。 */
        override fun toString(): String = "EphemeralCredentialLease.Pattern(value=[REDACTED])"
    }
}

/**
 * 像素凭据控件与系统校验桥之间的唯一输入会话。
 *
 * 会话不会暴露原始输入状态；UI 只能读取长度。提交会复制出单次 lease，并立即清空原缓冲。
 */
internal class CredentialInputSession(
    /** 当前认证模式。 */
    initialMode: PixelCredentialMode,
) : AutoCloseable {
    /** 当前认证模式。 */
    var mode: PixelCredentialMode = initialMode
        private set

    /** PIN 或密码使用的字符缓冲。 */
    private val characters: EphemeralCharBuffer = EphemeralCharBuffer()

    /** 图案使用的路径缓冲。 */
    private val pattern: SecurePatternBuffer = SecurePatternBuffer()

    /** 会话是否已经清零并永久关闭。 */
    private var closed: Boolean = false

    /** UI 可显示的输入长度，不包含任何原始凭据。 */
    val inputLength: Int
        get() {
            ensureOpen()
            return if (mode == PixelCredentialMode.PATTERN) pattern.size else characters.length
        }

    /** 切换认证模式，并在切换前清除旧模式的全部输入。 */
    fun switchMode(newMode: PixelCredentialMode) {
        ensureOpen()
        clear()
        mode = newMode
    }

    /** 为 PIN 或密码追加字符；PIN 会拒绝非数字。 */
    fun appendCharacter(character: Char): Boolean {
        ensureOpen()
        check(mode != PixelCredentialMode.PATTERN) { "character_not_allowed_for_pattern" }
        if (mode == PixelCredentialMode.PIN && character !in '0'..'9') {
            return false
        }
        return characters.append(character)
    }

    /** 删除 PIN 或密码的最后一个字符。 */
    fun deleteLastCharacter(): Boolean {
        ensureOpen()
        check(mode != PixelCredentialMode.PATTERN) { "character_not_allowed_for_pattern" }
        return characters.deleteLast()
    }

    /** 为图案追加一个格子。 */
    fun appendPatternCell(cellId: Int): Boolean {
        ensureOpen()
        check(mode == PixelCredentialMode.PATTERN) { "pattern_not_allowed_for_character_mode" }
        return pattern.append(cellId)
    }

    /** 清除当前输入，同时保留会话和认证模式。 */
    fun clear() {
        ensureOpen()
        characters.clear()
        pattern.clear()
    }

    /**
     * 将当前输入复制为单次校验 lease，并立即覆写会话中的原输入。
     *
     * 空输入不会创建 lease。
     */
    fun submit(): EphemeralCredentialLease? {
        ensureOpen()
        if (inputLength == 0) {
            return null
        }
        /** 只允许被一个系统校验任务持有的输入副本。 */
        val lease = when (mode) {
            PixelCredentialMode.PATTERN -> EphemeralCredentialLease.Pattern(pattern.copy())
            PixelCredentialMode.PIN,
            PixelCredentialMode.PASSWORD,
            -> EphemeralCredentialLease.Characters(mode, characters.copy())
        }
        clear()
        return lease
    }

    /** 幂等清零所有输入并永久关闭会话。 */
    override fun close() {
        if (closed) {
            return
        }
        characters.close()
        pattern.close()
        closed = true
    }

    /** 拒绝使用已经结束的输入会话。 */
    private fun ensureOpen() {
        check(!closed) { "credential_input_session_closed" }
    }
}

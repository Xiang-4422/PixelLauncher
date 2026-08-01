package com.android.internal.widget

import java.util.Arrays

/** 测试中模拟 Android 内部锁屏配置对象。 */
class LockPatternUtils

/** 测试中模拟 Android 内部图案视图类型。 */
class LockPatternView {
    /** 测试中模拟 Android 不可变图案格子。 */
    class Cell private constructor(
        /** 格子所在行。 */
        val row: Int,
        /** 格子所在列。 */
        val column: Int,
    ) {
        companion object {
            /** 按行列创建测试格子。 */
            @JvmStatic
            fun of(row: Int, column: Int): Cell = Cell(row, column)
        }
    }
}

/** 测试中模拟 Android 可清零系统凭据。 */
class LockscreenCredential private constructor(
    /** 测试凭据类型。 */
    val kind: String,
    /** 测试字符副本。 */
    private val characters: CharArray?,
    /** 测试图案格子副本。 */
    private val pattern: List<LockPatternView.Cell>?,
) : AutoCloseable {
    /** 系统凭据是否已经清零关闭。 */
    var closed: Boolean = false
        private set

    /** 创建仅供断言使用的字符快照。 */
    fun characterSnapshot(): String? = characters?.concatToString()

    /** 创建仅供断言使用的格子编号快照。 */
    fun patternSnapshot(): List<Int>? = pattern?.map { cell -> cell.row * 3 + cell.column }

    /** 模拟系统凭据的主动清零。 */
    override fun close() {
        characters?.let { value -> Arrays.fill(value, '\u0000') }
        closed = true
    }

    companion object {
        /** 从通用字符序列同步复制 PIN。 */
        @JvmStatic
        fun createPin(characters: CharSequence): LockscreenCredential =
            LockscreenCredential("pin", copyCharacters(characters), null)

        /** 从通用字符序列同步复制密码。 */
        @JvmStatic
        fun createPassword(characters: CharSequence): LockscreenCredential =
            LockscreenCredential("password", copyCharacters(characters), null)

        /** 从格子列表同步复制图案。 */
        @JvmStatic
        fun createPattern(pattern: List<LockPatternView.Cell>): LockscreenCredential =
            LockscreenCredential("pattern", null, pattern.toList())

        /** 不创建 String 地复制测试字符。 */
        private fun copyCharacters(characters: CharSequence): CharArray =
            CharArray(characters.length) { index -> characters[index] }
    }
}

/** 测试中模拟 Android 的异步校验器。 */
class LockPatternChecker {
    /** 测试中模拟系统校验回调。 */
    interface OnCheckCallback {
        /** 模拟系统早匹配通知。 */
        fun onEarlyMatched()

        /** 模拟最终匹配与限流通知。 */
        fun onChecked(matched: Boolean, throttleTimeoutMs: Int)

        /** 模拟系统任务取消通知。 */
        fun onCancelled()
    }

    companion object {
        /** 下一次校验使用的测试行为。 */
        var behavior: FakeCheckBehavior = FakeCheckBehavior.REJECTED

        /** 最近一次系统工厂创建的凭据。 */
        var lastCredential: LockscreenCredential? = null

        /** 最近一次任务对象。 */
        var lastTask: FakeCredentialTask? = null

        /** 系统凭据关闭前同步读取的测试字符。 */
        var capturedCharacters: String? = null

        /** 清除不同测试之间的静态状态。 */
        fun reset() {
            behavior = FakeCheckBehavior.REJECTED
            lastCredential = null
            lastTask = null
            capturedCharacters = null
        }

        /** 按配置行为模拟 Android 同步复制后的异步校验入口。 */
        @JvmStatic
        fun checkCredential(
            lockPatternUtils: LockPatternUtils,
            credential: LockscreenCredential,
            userId: Int,
            callback: OnCheckCallback,
        ): FakeCredentialTask {
            check(userId >= 0)
            lockPatternUtils.hashCode()
            lastCredential = credential
            capturedCharacters = credential.characterSnapshot()
            /** 当前测试任务。 */
            val task = FakeCredentialTask(callback)
            lastTask = task
            when (behavior) {
                FakeCheckBehavior.MATCHED -> {
                    callback.onEarlyMatched()
                    callback.onChecked(true, 0)
                }

                FakeCheckBehavior.REJECTED -> callback.onChecked(false, 0)
                FakeCheckBehavior.THROTTLED -> callback.onChecked(false, 30_000)
                FakeCheckBehavior.WAITING -> Unit
            }
            return task
        }
    }
}

/** 测试校验器可选择的结果。 */
enum class FakeCheckBehavior {
    /** 早匹配与最终匹配都会到达。 */
    MATCHED,

    /** 普通不匹配。 */
    REJECTED,

    /** 系统要求限流。 */
    THROTTLED,

    /** 等待调用方主动取消。 */
    WAITING,
}

/** 测试中模拟 Android 返回的可取消异步任务。 */
class FakeCredentialTask(
    /** 接收取消通知的系统回调。 */
    private val callback: LockPatternChecker.OnCheckCallback,
) {
    /** 任务是否已经收到取消请求。 */
    var cancelled: Boolean = false
        private set

    /** 模拟 Android `AsyncTask.cancel(boolean)`。 */
    fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        mayInterruptIfRunning.hashCode()
        if (cancelled) {
            return false
        }
        cancelled = true
        callback.onCancelled()
        return true
    }
}

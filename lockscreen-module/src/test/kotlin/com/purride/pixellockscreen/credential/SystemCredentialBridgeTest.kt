package com.purride.pixellockscreen.credential

import com.android.internal.widget.FakeCheckBehavior
import com.android.internal.widget.LockPatternChecker
import com.android.internal.widget.LockPatternUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 验证反射桥只转交凭据，并完全服从 Android 返回的校验结果。 */
class SystemCredentialBridgeTest {
    /** 使用测试类加载器解析精确 Android 类名的系统桥。 */
    private lateinit var bridge: SystemCredentialBridge

    /** 普通测试路径中不应出现的上层回调异常。 */
    private val unexpectedCallbackFailures: MutableList<Throwable> = mutableListOf()

    /** 每个测试开始前清除模拟系统状态。 */
    @Before
    fun setUp() {
        LockPatternChecker.reset()
        bridge = SystemCredentialBridge(javaClass.classLoader!!)
    }

    /** 每个测试结束后清除模拟系统状态。 */
    @After
    fun tearDown() {
        assertTrue(unexpectedCallbackFailures.isEmpty())
        unexpectedCallbackFailures.clear()
        LockPatternChecker.reset()
    }

    /** 精确类与方法存在时合同才能进入 Ready。 */
    @Test
    fun exactFrameworkContractIsRequired() {
        assertEquals(CredentialBridgeContractResult.Ready, bridge.verifyContract(LockPatternUtils()))
        assertTrue(bridge.verifyContract(Any()) is CredentialBridgeContractResult.Unsupported)
    }

    /** PIN 由 CharSequence 同步复制，桥返回前两侧临时对象都已关闭。 */
    @Test
    fun pinIsCopiedWithoutStringContractAndClosedImmediately() {
        LockPatternChecker.behavior = FakeCheckBehavior.REJECTED
        /** 包含测试 PIN 的输入会话。 */
        val session = CredentialInputSession(PixelCredentialMode.PIN)
        "2608".forEach(session::appendCharacter)
        /** 被系统桥独占的输入 lease。 */
        val lease = session.submit() as EphemeralCredentialLease.Characters
        /** 收集脱敏系统结果。 */
        val results = mutableListOf<CredentialCheckResult>()

        bridge.checkCredential(LockPatternUtils(), 0, lease, ::recordUnexpectedFailure, results::add)

        assertEquals(listOf(CredentialCheckResult.Rejected), results)
        assertEquals("pin", LockPatternChecker.lastCredential?.kind)
        assertEquals("2608", LockPatternChecker.capturedCharacters)
        assertTrue(LockPatternChecker.lastCredential?.closed == true)
        assertThrowsClosedLease(lease)
        session.close()
    }

    /** 合同或实例不匹配时也必须关闭调用方已经移交的 lease。 */
    @Test
    fun rejectedContractStillClosesTransferredCredential() {
        /** 包含测试 PIN 的输入会话。 */
        val session = CredentialInputSession(PixelCredentialMode.PIN)
        session.appendCharacter('8')
        /** 被系统桥独占的字符 lease。 */
        val lease = session.submit() as EphemeralCredentialLease.Characters
        /** 合同失败是否按预期抛出。 */
        var failedAsExpected = false

        try {
            bridge.checkCredential(Any(), 0, lease, ::recordUnexpectedFailure) { result -> result.hashCode() }
        } catch (_: IllegalStateException) {
            failedAsExpected = true
        }

        assertTrue(failedAsExpected)
        assertThrowsClosedLease(lease)
        session.close()
    }

    /** 图案编号必须按原顺序映射为 Android 的三行三列 Cell。 */
    @Test
    fun patternCellOrderIsPreserved() {
        LockPatternChecker.behavior = FakeCheckBehavior.MATCHED
        /** 包含测试路径的输入会话。 */
        val session = CredentialInputSession(PixelCredentialMode.PATTERN)
        listOf(0, 4, 8, 6).forEach(session::appendPatternCell)
        /** 被系统桥独占的图案 lease。 */
        val lease = session.submit() as EphemeralCredentialLease.Pattern
        /** 收集脱敏系统结果。 */
        val results = mutableListOf<CredentialCheckResult>()

        bridge.checkCredential(LockPatternUtils(), 10, lease, ::recordUnexpectedFailure, results::add)

        assertEquals(listOf(CredentialCheckResult.Matched), results)
        assertEquals(listOf(0, 4, 8, 6), LockPatternChecker.lastCredential?.patternSnapshot())
        assertTrue(LockPatternChecker.lastCredential?.closed == true)
        session.close()
    }

    /** Android 返回的正限流时间必须原样交给 UI，不由模块推算。 */
    @Test
    fun throttleTimeoutComesFromFrameworkResult() {
        LockPatternChecker.behavior = FakeCheckBehavior.THROTTLED
        /** 包含测试密码的输入会话。 */
        val session = CredentialInputSession(PixelCredentialMode.PASSWORD)
        "temporary".forEach(session::appendCharacter)
        /** 收集脱敏系统结果。 */
        val results = mutableListOf<CredentialCheckResult>()

        bridge.checkCredential(
            LockPatternUtils(),
            0,
            session.submit()!!,
            ::recordUnexpectedFailure,
            results::add,
        )

        assertEquals(listOf(CredentialCheckResult.Throttled(30_000)), results)
        assertEquals("password", LockPatternChecker.lastCredential?.kind)
        session.close()
    }

    /** 主动取消只产生一个取消终态，并向系统异步任务转发请求。 */
    @Test
    fun cancellationIsForwardedExactlyOnce() {
        LockPatternChecker.behavior = FakeCheckBehavior.WAITING
        /** 包含测试 PIN 的输入会话。 */
        val session = CredentialInputSession(PixelCredentialMode.PIN)
        session.appendCharacter('1')
        /** 收集脱敏系统结果。 */
        val results = mutableListOf<CredentialCheckResult>()
        /** 尚未收到系统结果的任务句柄。 */
        val pending = bridge.checkCredential(
            LockPatternUtils(),
            0,
            session.submit()!!,
            ::recordUnexpectedFailure,
            results::add,
        )

        assertFalse(LockPatternChecker.lastTask?.cancelled == true)
        pending.cancel()
        pending.cancel()

        assertTrue(LockPatternChecker.lastTask?.cancelled == true)
        assertEquals(listOf(CredentialCheckResult.Cancelled), results)
        session.close()
    }

    /** 上层结果处理异常必须到达独立回退出口，且不得逃逸到系统回调线程。 */
    @Test
    fun callbackFailureUsesDedicatedFallbackChannel() {
        LockPatternChecker.behavior = FakeCheckBehavior.REJECTED
        /** 包含测试 PIN 的输入会话。 */
        val session = CredentialInputSession(PixelCredentialMode.PIN)
        session.appendCharacter('4')
        /** 收集上层回调失败。 */
        val failures = mutableListOf<Throwable>()

        bridge.checkCredential(
            LockPatternUtils(),
            0,
            session.submit()!!,
            failures::add,
        ) {
            error("test_result_handler_failure")
        }

        assertEquals(1, failures.size)
        assertEquals("test_result_handler_failure", failures.single().message)
        session.close()
    }

    /** 断言字符 lease 已经被桥清零关闭。 */
    private fun assertThrowsClosedLease(lease: EphemeralCredentialLease.Characters) {
        /** 关闭后读取是否抛出预期异常。 */
        var failedAsExpected = false
        try {
            lease.withCharacters { characters -> characters.length }
        } catch (_: IllegalStateException) {
            failedAsExpected = true
        }
        assertTrue(failedAsExpected)
    }

    /** 记录不应发生的回调错误，由测试清理阶段统一断言。 */
    private fun recordUnexpectedFailure(throwable: Throwable) {
        unexpectedCallbackFailures += throwable
    }
}
